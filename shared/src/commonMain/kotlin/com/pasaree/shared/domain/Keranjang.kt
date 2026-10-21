package com.pasaree.shared.domain

import com.pasaree.shared.model.KeranjangUbah
import com.pasaree.shared.model.KodeGalat
import com.pasaree.shared.model.ProdukTayang
import com.pasaree.shared.network.PasareeApi
import com.pasaree.shared.sync.AntreanAksi
import com.pasaree.shared.sync.JenisAksi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Item keranjang lokal. Respons server tidak memuat lapak/judul, sehingga metadata tampilan
 * disimpan klien saat item ditambahkan (asumsi; lihat PR PSR-KMP-003).
 * `hargaIdr` hanya untuk tampilan harga satuan; TOTAL tidak pernah dihitung dari sini.
 */
data class ItemKeranjang(
    val varianId: String,
    val produkId: String,
    val lapakSlug: String,
    val lapakNama: String,
    val judul: String,
    val namaVarian: String,
    val hargaIdr: Int,
    val qty: Int,
    val stokTersedia: Int,
)

/** Sub-pesanan calon: item dikelompokkan per lapak. */
data class KelompokLapak(
    val lapakSlug: String,
    val lapakNama: String,
    val item: List<ItemKeranjang>,
)

fun List<ItemKeranjang>.perLapak(): List<KelompokLapak> =
    groupBy { it.lapakSlug }.map { (slug, daftar) -> KelompokLapak(slug, daftar.first().lapakNama, daftar) }

class AmbilTrip(private val api: PasareeApi) {
    suspend operator fun invoke(): Result<com.pasaree.shared.model.DaftarTrip> = hasilDomain { api.daftarTrip() }
}

/** Penyimpanan lokal keranjang (SQLDelight di PSR-KMP-005; memori untuk uji dan awal). */
interface KeranjangStore {
    val item: StateFlow<List<ItemKeranjang>>
    suspend fun simpan(item: ItemKeranjang)
    suspend fun hapus(varianId: String)
    suspend fun kosongkan()
}

class KeranjangMemori : KeranjangStore {
    private val _item = MutableStateFlow<List<ItemKeranjang>>(emptyList())
    override val item: StateFlow<List<ItemKeranjang>> = _item.asStateFlow()

    override suspend fun simpan(item: ItemKeranjang) {
        _item.update { daftar ->
            if (daftar.any { it.varianId == item.varianId }) daftar.map { if (it.varianId == item.varianId) item else it }
            else daftar + item
        }
    }

    override suspend fun hapus(varianId: String) = _item.update { d -> d.filterNot { it.varianId == varianId } }
    override suspend fun kosongkan() { _item.value = emptyList() }
}

/** Keadaan sinkron keranjang dengan server terakhir (angka server, bukan hitungan klien). */
data class RingkasanServer(
    val subtotalIdr: Int? = null,
    val peringatanStok: List<String> = emptyList(),
)

/**
 * Keranjang: tulis lokal dulu, lalu `PUT /keranjang` (qty 0 = hapus).
 * Produk/varian stok 0 ditolak sebelum menyentuh jaringan.
 */
class KeranjangRepository(
    private val store: KeranjangStore,
    private val api: PasareeApi,
    /** Antrean aksi offline; null = tanpa antrean (galat jaringan dikembalikan ke pemanggil). */
    private val antrean: AntreanAksi? = null,
    /** Dipanggil saat penulisan ke server gagal karena jaringan (banner luring). */
    private val onLuring: () -> Unit = {},
) {
    val item: StateFlow<List<ItemKeranjang>> get() = store.item
    private val _ringkasan = MutableStateFlow(RingkasanServer())
    val ringkasan: StateFlow<RingkasanServer> = _ringkasan.asStateFlow()

    suspend fun tambah(produk: ProdukTayang, varianId: String, qty: Int = 1): Result<Unit> {
        if (qty < 1) return Result.failure(validasi("Jumlah minimal 1."))
        if (!produk.bisaMasukKeranjang(varianId)) {
            return Result.failure(GalatDomain(JenisGalat.VALIDASI, KodeGalat.STOK_HABIS, "Stok varian habis."))
        }
        val varian = produk.varian.first { it.id == varianId }
        val stok = produk.stokVarian(varianId)
        val lama = store.item.value.firstOrNull { it.varianId == varianId }?.qty ?: 0
        val baru = (lama + qty).coerceAtMost(stok)
        store.simpan(
            ItemKeranjang(
                varianId = varianId,
                produkId = produk.id,
                lapakSlug = produk.lapak.slug,
                lapakNama = produk.lapak.namaLapak,
                judul = produk.judul,
                namaVarian = varian.namaVarian,
                hargaIdr = varian.hargaIdr,
                qty = baru,
                stokTersedia = stok,
            ),
        )
        return sinkron(varianId, baru)
    }

    /** Ubah qty; 0 menghapus item. Qty dijepit ke stok yang diketahui. */
    suspend fun ubahQty(varianId: String, qty: Int): Result<Unit> {
        val ada = store.item.value.firstOrNull { it.varianId == varianId }
            ?: return Result.failure(validasi("Item tidak ada di keranjang."))
        if (qty <= 0) {
            store.hapus(varianId)
            return sinkron(varianId, 0)
        }
        val baru = qty.coerceAtMost(ada.stokTersedia)
        store.simpan(ada.copy(qty = baru))
        return sinkron(varianId, baru)
    }

    /**
     * Sinkron ke server. Bila luring dan ada [antrean], aksi diantre (aksi bayar TIDAK pernah diantre) dan
     * penulisan lokal dianggap berhasil; SyncWorker mengirimnya kemudian.
     */
    private suspend fun sinkron(varianId: String, qty: Int): Result<Unit> {
        val hasil = hasilDomain { api.ubahKeranjang(KeranjangUbah(varianId, qty)) }
        hasil.onSuccess { server -> _ringkasan.value = RingkasanServer(server.subtotalIdr, server.peringatanStok) }
        val g = hasil.exceptionOrNull() as? GalatDomain
        if (g != null && g.jenis == JenisGalat.JARINGAN && antrean != null) {
            antrean.tambah(JenisAksi.UBAH_KERANJANG, varianId, qty)
            onLuring()
            return Result.success(Unit)
        }
        return hasil.map { }
    }

    private fun validasi(pesan: String) = GalatDomain(JenisGalat.VALIDASI, KodeGalat.VALIDASI_GAGAL, pesan)
}

/**
 * Info komisi untuk lapak (hanya informasi, TIDAK menambah total bayar pembeli).
 * Asumsi: tarif dasar 8% dari subtotal lapak, `floor()`. Tarif sebenarnya
 * (paket-hampers 10%, lapak baru 5%) belum dikirim server; lihat PR.
 */
data class InfoKomisi(val komisiIdr: Int, val diterimaLapakIdr: Int, val persen: Int) {
    val teks: String get() = "Lapak menerima ${com.pasaree.shared.util.formatIdr(diterimaLapakIdr)} setelah komisi $persen%"
}

fun infoKomisiLapak(subtotalLapakIdr: Int, persen: Int = 8): InfoKomisi {
    val komisi = (subtotalLapakIdr.toLong() * persen / 100).toInt() // floor untuk nilai non-negatif
    return InfoKomisi(komisi, subtotalLapakIdr - komisi, persen)
}
