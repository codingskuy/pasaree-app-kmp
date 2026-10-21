package com.pasaree.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class KeranjangUbah(
    @SerialName("varian_id") val varianId: String,
    val qty: Int,
)

@Serializable
data class ItemKeranjangServer(
    @SerialName("varian_id") val varianId: String,
    val qty: Int,
    @SerialName("harga_idr") val hargaIdr: Int,
)

/** Respons `PUT /keranjang`: keranjang terhitung server. `subtotal_idr` berasal dari server. */
@Serializable
data class KeranjangServer(
    val item: List<ItemKeranjangServer>,
    @SerialName("subtotal_idr") val subtotalIdr: Int,
    @SerialName("peringatan_stok") val peringatanStok: List<String> = emptyList(),
)

@Serializable
data class CheckoutRequest(
    @SerialName("trip_id") val tripId: String,
    @SerialName("titik_id") val titikId: String,
    val voucher: String? = null,
)

/** Pratinjau checkout: SEMUA angka dari server; klien tidak menjumlah ulang. */
@Serializable
data class PratinjauCheckout(
    @SerialName("subtotal_idr") val subtotalIdr: Int,
    @SerialName("ongkir_idr") val ongkirIdr: Int,
    @SerialName("biaya_layanan_idr") val biayaLayananIdr: Int,
    @SerialName("total_idr") val totalIdr: Int,
    @SerialName("kunci_stok_hingga") val kunciStokHingga: String,
    val peringatan: List<String> = emptyList(),
)

@Serializable
data class PesananBuat(
    @SerialName("trip_id") val tripId: String,
    @SerialName("titik_id") val titikId: String,
    val voucher: String? = null,
    @SerialName("catatan_per_lapak") val catatanPerLapak: Map<String, String> = emptyMap(),
)

@Serializable
data class PesananDibuat(
    @SerialName("pesanan_id") val pesananId: String,
    @SerialName("batas_bayar_hingga") val batasBayarHingga: String,
    @SerialName("total_idr") val totalIdr: Int,
)

@Serializable
data class DaftarTrip(
    val data: List<TripLumbung>,
    val basi: Boolean = false,
)

@Serializable
data class LoginRequest(
    val email: String,
    @SerialName("kata_sandi") val kataSandi: String,
)

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Int,
)
