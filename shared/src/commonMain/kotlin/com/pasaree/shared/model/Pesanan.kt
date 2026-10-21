package com.pasaree.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object StatusPesanan {
    const val DRAF_KERANJANG = "DRAF_KERANJANG"
    const val DIPESAN = "DIPESAN"
    const val DIBAYAR = "DIBAYAR"
    const val DIKONFIRMASI_LAPAK = "DIKONFIRMASI_LAPAK"
    const val DISERAHKAN_KE_TRIP = "DISERAHKAN_KE_TRIP"
    const val DITERIMA_PEMBELI = "DITERIMA_PEMBELI"
    const val SELESAI = "SELESAI"
    const val DIBATALKAN = "DIBATALKAN"
    const val DIKEMBALIKAN = "DIKEMBALIKAN"

    /** Urutan jalur normal untuk timeline DIPESAN sampai SELESAI. */
    val JALUR_NORMAL = listOf(DIPESAN, DIBAYAR, DIKONFIRMASI_LAPAK, DISERAHKAN_KE_TRIP, DITERIMA_PEMBELI, SELESAI)

    /** Status akhir: polling berhenti. */
    val AKHIR = setOf(SELESAI, DIBATALKAN, DIKEMBALIKAN)
}

object AksiPesanan {
    const val KONFIRMASI_TERIMA = "konfirmasi_terima"
    const val AJUKAN_RETUR = "ajukan_retur"
}

enum class MetodeBayar(val kode: String, val label: String) {
    VA_BCA("VA_BCA", "Virtual Account BCA"),
    QRIS("QRIS", "QRIS"),
    EWALLET_DANA("EWALLET_DANA", "DANA"),
}

@Serializable
data class BayarRequest(
    val metode: String,
    @SerialName("idempotency_key") val idempotencyKey: String,
)

@Serializable
data class BayarResponse(
    val status: String,
    @SerialName("escrow_id") val escrowId: String,
)

@Serializable
data class SubPesanan(
    /** Dari kontrak PSR-BE-005 (`sub_id`); dipakai pada `POST /pesanan/{id}/retur`. */
    @SerialName("sub_id") val subId: String? = null,
    val lapak: LapakRingkas,
    val status: String,
    @SerialName("subtotal_idr") val subtotalIdr: Int,
)

@Serializable
data class TitikTimeline(
    val status: String,
    val waktu: String,
)

/** ASUMSI: openapi `PesananDetail` belum memuat item; `item` opsional agar klien siap bila ditambahkan. */
@Serializable
data class ItemPesanan(
    @SerialName("produk_id") val produkId: String,
    val judul: String,
)

@Serializable
data class PesananDetail(
    val id: String,
    @SerialName("total_idr") val totalIdr: Int,
    val status: String,
    @SerialName("dibuat_pada") val dibuatPada: String,
    @SerialName("sub_pesanan") val subPesanan: List<SubPesanan> = emptyList(),
    val timeline: List<TitikTimeline> = emptyList(),
    @SerialName("bukti_serah_trip") val buktiSerahTrip: String? = null,
    @SerialName("escrow_id") val escrowId: String? = null,
    @SerialName("aksi_tersedia") val aksiTersedia: List<String> = emptyList(),
    val item: List<ItemPesanan> = emptyList(),
    /** ASUMSI: batas bayar 30 menit; kontrak hanya menyebut `batas_bayar_hingga` di respons POST /pesanan. */
    @SerialName("batas_bayar_hingga") val batasBayarHingga: String? = null,
)

@Serializable
data class PesananRingkas(
    val id: String,
    @SerialName("total_idr") val totalIdr: Int,
    val status: String,
    @SerialName("dibuat_pada") val dibuatPada: String,
)

@Serializable
data class HalamanPesanan(
    val data: List<PesananRingkas>,
    @SerialName("cursor_lanjut") val cursorLanjut: String? = null,
)

@Serializable
data class ReturRequest(
    @SerialName("sub_id") val subId: String,
    val alasan: String,
)

@Serializable
data class ReturResponse(val status: String)

@Serializable
data class KonfirmasiTerimaResponse(val status: String)

@Serializable
data class UlasanBuat(
    @SerialName("pesanan_id") val pesananId: String,
    @SerialName("produk_id") val produkId: String,
    val bintang: Int,
    val teks: String? = null,
    @SerialName("foto_url") val fotoUrl: List<String> = emptyList(),
)
