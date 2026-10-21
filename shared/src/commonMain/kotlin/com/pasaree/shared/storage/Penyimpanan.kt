package com.pasaree.shared.storage

import app.cash.sqldelight.db.SqlDriver
import com.pasaree.shared.db.PasareeDb
import com.pasaree.shared.domain.ItemKeranjang
import com.pasaree.shared.domain.KeranjangStore
import com.pasaree.shared.domain.KunciIdempotensiStore
import com.pasaree.shared.domain.KunciTersimpan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/** Membuka database dari [driver] platform; driver Android/Native membuat skema sendiri lewat `PasareeDb.Schema`. */
fun buatDatabase(driver: SqlDriver): PasareeDb = PasareeDb(driver)

/** Keranjang tersimpan di SQLDelight: tulis lokal dulu (bertahan setelah aplikasi dimatikan). */
class KeranjangSql(private val db: PasareeDb) : KeranjangStore {
    private val q get() = db.pasareeQueries
    private val _item = MutableStateFlow(baca())
    override val item: StateFlow<List<ItemKeranjang>> = _item.asStateFlow()

    private fun baca(): List<ItemKeranjang> = q.semuaKeranjang().executeAsList().map {
        ItemKeranjang(
            varianId = it.varian_id, produkId = it.produk_id, lapakSlug = it.lapak_slug, lapakNama = it.lapak_nama,
            judul = it.judul, namaVarian = it.nama_varian, hargaIdr = it.harga_idr.toInt(), qty = it.qty.toInt(),
            stokTersedia = it.stok_tersedia.toInt(),
        )
    }

    override suspend fun simpan(item: ItemKeranjang) {
        db.transaction {
            val urutan = q.semuaKeranjang().executeAsList().firstOrNull { it.varian_id == item.varianId }?.urutan
                ?: (q.maksUrutan().executeAsOne() + 1)
            q.upsertKeranjang(
                item.varianId, item.produkId, item.lapakSlug, item.lapakNama, item.judul, item.namaVarian,
                item.hargaIdr.toLong(), item.qty.toLong(), item.stokTersedia.toLong(), urutan,
            )
        }
        _item.value = baca()
    }

    override suspend fun hapus(varianId: String) {
        q.hapusKeranjang(varianId)
        _item.value = baca()
    }

    override suspend fun kosongkan() {
        q.kosongkanKeranjang()
        _item.value = baca()
    }
}

/** Kunci idempotensi bayar yang bertahan lintas proses (24 jam; kedaluwarsa ditentukan `KunciIdempotensi`). */
class KunciIdempotensiSql(private val db: PasareeDb) : KunciIdempotensiStore {
    private val q get() = db.pasareeQueries

    override suspend fun ambil(pesananId: String, metode: String): KunciTersimpan? =
        q.ambilKunci(pesananId, metode).executeAsOneOrNull()?.let {
            KunciTersimpan(it.pesanan_id, it.metode, it.kunci, Instant.fromEpochMilliseconds(it.dibuat_pada))
        }

    override suspend fun simpan(k: KunciTersimpan) { q.simpanKunci(k.pesananId, k.metode, k.kunci, k.dibuat.toEpochMilliseconds()) }

    override suspend fun hapus(pesananId: String) { q.hapusKunciPesanan(pesananId) }

    /** Pembersihan berkala kunci yang sudah lewat masa berlaku. */
    fun bersihkan(batas: Instant) = q.hapusKunciKedaluwarsa(batas.toEpochMilliseconds())
}

/** Cache katalog dengan TTL 5 menit. */
interface CacheKatalog {
    /** Isi cache bila masih segar (umur <= TTL), selain itu null. */
    suspend fun segar(kunci: String): String?

    /** Isi cache walau kedaluwarsa (untuk tampilan luring). */
    suspend fun basi(kunci: String): String?

    suspend fun simpan(kunci: String, isi: String)
    suspend fun hapusSemua()

    companion object {
        const val TTL_MS = 5L * 60 * 1000
    }
}

class CacheKatalogSql(private val db: PasareeDb, private val sekarang: () -> Instant = { Clock.System.now() }) : CacheKatalog {
    private val q get() = db.pasareeQueries
    override suspend fun segar(kunci: String): String? = q.ambilCache(kunci).executeAsOneOrNull()?.takeIf {
        sekarang().toEpochMilliseconds() - it.diambil_pada <= CacheKatalog.TTL_MS
    }?.isi

    override suspend fun basi(kunci: String): String? = q.ambilCache(kunci).executeAsOneOrNull()?.isi
    override suspend fun simpan(kunci: String, isi: String) { q.simpanCache(kunci, isi, sekarang().toEpochMilliseconds()) }
    override suspend fun hapusSemua() { q.hapusSemuaCache() }
}

class CacheKatalogMemori(private val sekarang: () -> Instant = { Clock.System.now() }) : CacheKatalog {
    private val data = mutableMapOf<String, Pair<String, Long>>()
    override suspend fun segar(kunci: String) = data[kunci]?.takeIf { sekarang().toEpochMilliseconds() - it.second <= CacheKatalog.TTL_MS }?.first
    override suspend fun basi(kunci: String) = data[kunci]?.first
    override suspend fun simpan(kunci: String, isi: String) { data[kunci] = isi to sekarang().toEpochMilliseconds() }
    override suspend fun hapusSemua() = data.clear()
}

/** Metadata sinkron (mis. waktu sinkron terakhir untuk delta `diperbarui_pada`). */
interface MetaSinkron {
    suspend fun ambil(kunci: String): String?
    suspend fun simpan(kunci: String, nilai: String)
}

class MetaSinkronSql(private val db: PasareeDb) : MetaSinkron {
    override suspend fun ambil(kunci: String) = db.pasareeQueries.ambilMeta(kunci).executeAsOneOrNull()
    override suspend fun simpan(kunci: String, nilai: String) { db.pasareeQueries.simpanMeta(kunci, nilai) }
}

class MetaSinkronMemori : MetaSinkron {
    private val data = mutableMapOf<String, String>()
    override suspend fun ambil(kunci: String) = data[kunci]
    override suspend fun simpan(kunci: String, nilai: String) { data[kunci] = nilai }
}
