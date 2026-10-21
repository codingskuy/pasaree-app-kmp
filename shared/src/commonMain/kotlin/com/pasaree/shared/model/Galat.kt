package com.pasaree.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Respons error baku `{kode, pesan, detail}` dari kontrak (schema `Error`). */
@Serializable
data class GalatApi(
    val kode: String,
    val pesan: String,
    val detail: JsonObject? = null,
)

object KodeGalat {
    const val STOK_HABIS = "STOK_HABIS"
    const val HARGA_BERUBAH = "HARGA_BERUBAH"
    const val TRIP_PENUH = "TRIP_PENUH"
    const val AUTH_KEDALUWARSA = "AUTH_KEDALUWARSA"
    const val VALIDASI_GAGAL = "VALIDASI_GAGAL"
    const val PESANAN_TIDAK_DITEMUKAN = "PESANAN_TIDAK_DITEMUKAN"
    const val BATAS_LAJU = "BATAS_LAJU"
}
