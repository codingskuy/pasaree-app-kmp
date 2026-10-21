package com.pasaree.shared.domain

import com.pasaree.shared.model.HalamanProduk
import com.pasaree.shared.model.ProdukRingkas
import com.pasaree.shared.model.ProdukTayang
import com.pasaree.shared.network.LIMIT_DEFAULT
import com.pasaree.shared.network.LIMIT_MAKS
import com.pasaree.shared.network.PasareeApi
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

class CariProduk(private val api: PasareeApi) {
    suspend operator fun invoke(
        kriteria: KriteriaKatalog = KriteriaKatalog(),
        cursor: String? = null,
        limit: Int = LIMIT_DEFAULT,
    ): Result<HalamanProduk> = hasilDomain {
        api.daftarProduk(
            q = kriteria.q,
            kategori = kriteria.kategori,
            tag = kriteria.tag,
            sort = kriteria.sort,
            limit = limit.coerceIn(1, LIMIT_MAKS),
            cursor = cursor,
        )
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
                    )
                }
            },
            onFailure = { e -> _state.update { it.copy(memuat = false, galat = e as? GalatDomain ?: e.keGalatDomain()) } },
        )
    }
}
