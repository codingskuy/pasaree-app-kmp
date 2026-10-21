package com.pasaree.shared.sync

import com.pasaree.shared.domain.GalatDomain
import com.pasaree.shared.domain.ItemKeranjang
import com.pasaree.shared.domain.JenisGalat
import com.pasaree.shared.domain.KeranjangStore
import com.pasaree.shared.domain.KriteriaKatalog
import com.pasaree.shared.domain.hasilDomain
import com.pasaree.shared.model.HalamanProduk
import com.pasaree.shared.model.KeranjangServer
import com.pasaree.shared.model.KeranjangUbah
import com.pasaree.shared.network.JsonPasaree
import com.pasaree.shared.network.LIMIT_DEFAULT
import com.pasaree.shared.network.PasareeApi
import com.pasaree.shared.storage.CacheKatalog
import com.pasaree.shared.storage.MetaSinkron
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

enum class StatusSinkron { IDLE, SINKRON, LURING, GALAT }

/**
 * Jenis aksi yang boleh diantre offline. SENGAJA hanya mengubah keranjang:
 * aksi bayar tidak pernah masuk antrean offline (AGENTS.md, blast radius).
 */
enum class JenisAksi { UBAH_KERANJANG }

data class AksiAntre(
    val id: Long,
    val jenis: JenisAksi,
    val varianId: String,
    val qty: Int,
    val dibuat: Instant,
    val percobaan: Int = 0,
)

/** Antrean aksi offline. Aksi untuk varian yang sama menimpa yang lama (PUT keranjang idempoten, terakhir menang). */
interface AntreanAksi {
    suspend fun tambah(jenis: JenisAksi, varianId: String, qty: Int)
    suspend fun semua(): List<AksiAntre>
    suspend fun hapus(id: Long)
    suspend fun naikkanPercobaan(id: Long)
}

class AntreanMemori(private val sekarang: () -> Instant = { Clock.System.now() }) : AntreanAksi {
    private var urut = 0L
    private val data = mutableListOf<AksiAntre>()
    override suspend fun tambah(jenis: JenisAksi, varianId: String, qty: Int) {
        data.removeAll { it.varianId == varianId && it.jenis == jenis }
        data += AksiAntre(++urut, jenis, varianId, qty, sekarang())
    }
    override suspend fun semua() = data.toList()
    override suspend fun hapus(id: Long) { data.removeAll { it.id == id } }
    override suspend fun naikkanPercobaan(id: Long) {
        val i = data.indexOfFirst { it.id == id }
        if (i >= 0) data[i] = data[i].copy(percobaan = data[i].percobaan + 1)
    }
}

/**
 * Gabung keranjang lokal dengan keranjang server:
 * - qty = max(lokal, server), dijepit ke stok yang dikenal (stok server menang bila lebih kecil);
 * - harga dari server menang (`harga_idr`);
 * - item yang hanya ada di server (mis. dari perangkat lain) ditambahkan dengan metadata sementara;
 * - item yang hanya ada lokal tetap (akan didorong lewat antrean).
 */
fun gabungKeranjang(lokal: List<ItemKeranjang>, server: KeranjangServer): List<ItemKeranjang> {
    val serverPerVarian = server.item.associateBy { it.varianId }
    val hasil = lokal.map { l ->
        val s = serverPerVarian[l.varianId]
        if (s == null) l else l.copy(
            hargaIdr = s.hargaIdr,
            qty = maxOf(l.qty, s.qty).coerceAtMost(l.stokTersedia.coerceAtLeast(1)),
        )
    }
    val tambahan = server.item.filter { s -> lokal.none { it.varianId == s.varianId } }.map { s ->
        ItemKeranjang(
            varianId = s.varianId, produkId = "", lapakSlug = "tidak-diketahui", lapakNama = "Lapak lain",
            judul = "Item dari akun Anda", namaVarian = s.varianId, hargaIdr = s.hargaIdr, qty = s.qty, stokTersedia = s.qty,
        )
    }
    return hasil + tambahan
}

const val KUNCI_BERANDA = "beranda" // selaras dengan CariProduk.kunciCache untuk beranda
val KRITERIA_BERANDA = KriteriaKatalog(sort = "terbaru")
private const val META_SINKRON_TERAKHIR = "sinkron_terakhir"

/**
 * Pekerja sinkronisasi: kuras antrean aksi, rekonsiliasi keranjang (server menang untuk stok/harga,
 * max untuk qty), lalu tarik delta katalog via `diperbarui_pada`. `now()` dipicu tombol "Coba lagi".
 */
class SyncWorker(
    private val api: PasareeApi,
    private val keranjang: KeranjangStore,
    private val antrean: AntreanAksi,
    private val cache: CacheKatalog,
    private val meta: MetaSinkron,
    private val sekarang: () -> Instant = { Clock.System.now() },
) {
    private val _status = MutableStateFlow(StatusSinkron.IDLE)
    val status: StateFlow<StatusSinkron> = _status.asStateFlow()
    private val kunci = Mutex()

    /** Dipanggil saat penulisan terakhir gagal karena jaringan: tampilkan banner luring. */
    fun tandaiLuring() { _status.value = StatusSinkron.LURING }

    /** Hasil menguras antrean: apakah terputus karena luring atau galat server/otentikasi. */
    private data class HasilKuras(val luring: Boolean, val galat: Boolean)

    private suspend fun kurasAntrean(): HasilKuras {
        var luring = false
        var galat = false
        for (aksi in antrean.semua()) {
            val hasil = hasilDomain { api.ubahKeranjang(KeranjangUbah(aksi.varianId, aksi.qty)) }
            val g = hasil.exceptionOrNull() as? GalatDomain
            when {
                g == null -> {
                    antrean.hapus(aksi.id)
                    hasil.getOrNull()?.let { rekonsiliasi(it) }
                }
                g.jenis == JenisGalat.JARINGAN -> { antrean.naikkanPercobaan(aksi.id); luring = true; break }
                g.jenis == JenisGalat.SERVER || g.jenis == JenisGalat.AUTENTIKASI -> { antrean.naikkanPercobaan(aksi.id); galat = true; break }
                // Konflik/validasi/tidak ditemukan: server menang, aksi dibuang agar tidak menyumbat antrean.
                else -> antrean.hapus(aksi.id)
            }
        }
        return HasilKuras(luring, galat)
    }

    /**
     * Checkout membuat pesanan dari KERANJANG SERVER (PSR-BE-005): pastikan semua aksi lokal sudah terkirim
     * sebelum pratinjau. Gagal bila antrean masih berisi (luring/galat).
     */
    suspend fun pastikanKeranjangTersinkron(): Result<Unit> = kunci.withLock {
        kurasAntrean()
        if (antrean.semua().isEmpty()) Result.success(Unit)
        else Result.failure(GalatDomain(JenisGalat.JARINGAN, null, "Keranjang belum tersinkron ke akun. Sambungkan internet lalu coba lagi."))
    }

    suspend fun now(): StatusSinkron = kunci.withLock {
        _status.value = StatusSinkron.SINKRON
        val kuras = kurasAntrean()
        var luring = kuras.luring
        var galat = kuras.galat
        if (!luring && !galat) {
            // Tarik keranjang akun (GET /keranjang): qty max(lokal, server), harga/stok server menang. 401 = belum masuk, dilewati.
            val k = hasilDomain { api.ambilKeranjang() }
            k.getOrNull()?.let { rekonsiliasi(it) }
            (k.exceptionOrNull() as? GalatDomain)?.let { g ->
                when (g.jenis) {
                    JenisGalat.JARINGAN -> luring = true
                    JenisGalat.AUTENTIKASI -> {}
                    else -> galat = true
                }
            }
        }
        if (!luring && !galat) {
            val d = hasilDomain { tarikDelta() }.exceptionOrNull() as? GalatDomain
            when (d?.jenis) {
                null -> {}
                JenisGalat.JARINGAN -> luring = true
                else -> galat = true
            }
        }
        _status.value = when {
            luring -> StatusSinkron.LURING
            galat -> StatusSinkron.GALAT
            else -> StatusSinkron.IDLE
        }
        _status.value
    }

    private suspend fun rekonsiliasi(server: KeranjangServer) {
        gabungKeranjang(keranjang.item.value, server).forEach { keranjang.simpan(it) }
    }

    /**
     * Tarik delta katalog sejak sinkron terakhir. ASUMSI: parameter `diperbarui_sejak` belum ada di openapi.yaml;
     * bila server mengabaikannya, hasilnya halaman penuh dan penggabungan tetap benar (server menang).
     */
    private suspend fun tarikDelta() {
        val sejak = meta.ambil(META_SINKRON_TERAKHIR)
        val halaman = api.daftarProduk(sort = "terbaru", limit = LIMIT_DEFAULT, diperbaruiSejak = sejak)
        if (halaman.hargaBerubah) cache.hapusSemua() // harga berubah: server menang, buang cache lama
        val lama = cache.basi(KUNCI_BERANDA)?.let { runCatching { JsonPasaree.decodeFromString<HalamanProduk>(it) }.getOrNull() }
        val gabung = if (lama == null) halaman else {
            val baruId = halaman.data.map { it.id }.toSet()
            halaman.copy(data = halaman.data + lama.data.filterNot { it.id in baruId }, cursorLanjut = lama.cursorLanjut ?: halaman.cursorLanjut)
        }
        cache.simpan(KUNCI_BERANDA, JsonPasaree.encodeToString(HalamanProduk.serializer(), gabung))
        meta.simpan(META_SINKRON_TERAKHIR, sekarang().toString())
    }
}
