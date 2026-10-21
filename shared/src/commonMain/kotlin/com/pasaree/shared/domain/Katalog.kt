package com.pasaree.shared.domain

import com.pasaree.shared.model.HalamanProduk
import com.pasaree.shared.model.ProdukRingkas
import com.pasaree.shared.model.ProdukTayang
import com.pasaree.shared.network.LIMIT_DEFAULT
import com.pasaree.shared.network.LIMIT_MAKS
import com.pasaree.shared.network.PasareeApi
import com.pasaree.shared.network.JsonPasaree
import com.pasaree.shared.storage.CacheKatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Kriteria pencarian katalog. */
data class KriteriaKatalog(
    val q: String? = null,
    val kategori: String? = null,
    val tag: String? = null,
    val sort: String? = null,
)

/**
 * Pencarian katalog. Dengan [cache]: cache segar (TTL 5 menit) dipakai tanpa jaringan; saat gagal
 * (luring) cache basi tetap disajikan agar katalog tersedia luring.
 */
class CariProduk(private val api: PasareeApi, private val cache: CacheKatalog? = null) {
    suspend operator fun invoke(
        kriteria: KriteriaKatalog = KriteriaKatalog(),
        cursor: String? = null,
        limit: Int = LIMIT_DEFAULT,
    ): Result<HalamanProduk> {
        val batas = limit.coerceIn(1, LIMIT_MAKS)
        val kunci = kunciCache(kriteria, cursor, batas)
        cache?.segar(kunci)?.let { isi -> bacaCache(isi, basi = false)?.let { return Result.success(it) } }
        val hasil = hasilDomain {
            api.daftarProduk(q = kriteria.q, kategori = kriteria.kategori, tag = kriteria.tag, sort = kriteria.sort, limit = batas, cursor = cursor)
        }
        hasil.onSuccess { cache?.simpan(kunci, JsonPasaree.encodeToString(HalamanProduk.serializer(), it)) }
        if (hasil.isFailure) cache?.basi(kunci)?.let { isi -> bacaCache(isi, basi = true)?.let { return Result.success(it) } }
        return hasil
    }

    private fun bacaCache(isi: String, basi: Boolean): HalamanProduk? =
        runCatching { JsonPasaree.decodeFromString<HalamanProduk>(isi) }.getOrNull()?.copy(dariCache = true, basi = basi)

    companion object {
        /** Beranda (halaman pertama, urut terbaru) memakai kunci tetap agar dapat diperbarui oleh delta sinkron. */
        fun kunciCache(k: KriteriaKatalog, cursor: String?, limit: Int): String =
            if (k == KriteriaKatalog(sort = "terbaru") && cursor == null && limit == LIMIT_DEFAULT) "beranda"
            else "q=${k.q}|kat=${k.kategori}|tag=${k.tag}|sort=${k.sort}|cursor=$cursor|limit=$limit"
    }
}

class AmbilDetailProduk(private val api: PasareeApi) {
    suspend operator fun invoke(id: String): Result<ProdukTayang> = hasilDomain { api.detailProduk(id) }
}

data class StatePager(
    val item: List<ProdukRingkas> = emptyList(),
    val cursorLanjut: String? = null,
    val memuat: Boolean = false,
    val habis: Boolean = false,
    val galat: GalatDomain? = null,
    /** True bila server menandai harga berubah pada halaman terakhir (klien perlu segarkan). */
    val hargaBerubah: Boolean = false,
    /** True bila halaman terakhir berasal dari cache lokal (mungkin basi saat luring). */
    val dariCache: Boolean = false,
)

/** Paginasi cursor: tambah halaman berikutnya, abaikan panggilan saat sedang memuat atau sudah habis. */
class PagerProduk(
    private val cari: CariProduk,
    private val kriteria: KriteriaKatalog = KriteriaKatalog(),
    private val limit: Int = LIMIT_DEFAULT,
) {
    private val _state = MutableStateFlow(StatePager())
    val state: StateFlow<StatePager> = _state.asStateFlow()

    suspend fun muatAwal() {
        _state.value = StatePager()
        muatLagi()
    }

    suspend fun muatLagi() {
        val sekarang = _state.value
        if (sekarang.memuat || sekarang.habis) return
        _state.update { it.copy(memuat = true, galat = null) }
        cari(kriteria, sekarang.cursorLanjut, limit).fold(
            onSuccess = { halaman ->
                _state.update {
                    it.copy(
                        item = it.item + halaman.data.filter { baru -> it.item.none { lama -> lama.id == baru.id } },
                        cursorLanjut = halaman.cursorLanjut,
                        habis = halaman.cursorLanjut == null,
                        memuat = false,
                        hargaBerubah = halaman.hargaBerubah,
                        dariCache = halaman.dariCache,
                    )
                }
            },
            onFailure = { e -> _state.update { it.copy(memuat = false, galat = e as? GalatDomain ?: e.keGalatDomain()) } },
        )
    }
}
