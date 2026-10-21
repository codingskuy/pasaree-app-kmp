package com.pasaree.shared.network

import com.pasaree.shared.model.GalatApi
import com.pasaree.shared.model.HalamanProduk
import com.pasaree.shared.model.ProdukTayang

/** Kesalahan HTTP dari API: membawa status dan `GalatApi` baku bila ada. */
class ApiException(
    val status: Int,
    val galat: GalatApi?,
    pesan: String = galat?.pesan ?: "HTTP $status",
) : Exception(pesan) {
    val kode: String? get() = galat?.kode
}

const val LIMIT_DEFAULT = 20
const val LIMIT_MAKS = 50

interface PasareeApi {
    /** `GET /katalog/produk`; limit dijepit ke 1..50. */
    suspend fun daftarProduk(
        q: String? = null,
        kategori: String? = null,
        tag: String? = null,
        lapakSlug: String? = null,
        sort: String? = null,
        limit: Int = LIMIT_DEFAULT,
        cursor: String? = null,
    ): HalamanProduk

    /** `GET /katalog/produk/{id}`. */
    suspend fun detailProduk(id: String): ProdukTayang
}
