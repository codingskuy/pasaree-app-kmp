package com.pasaree.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LapakRingkas(
    val slug: String,
    @SerialName("nama_lapak") val namaLapak: String,
)

@Serializable
data class ProdukRingkas(
    val id: String,
    val judul: String,
    @SerialName("kategori_kode") val kategoriKode: String,
    @SerialName("harga_mulai_idr") val hargaMulaiIdr: Int,
    @SerialName("rating_rata") val ratingRata: Double,
    val lapak: LapakRingkas,
    @SerialName("media_utama") val mediaUtama: String? = null,
)

@Serializable
data class HalamanProduk(
    val data: List<ProdukRingkas>,
    @SerialName("cursor_lanjut") val cursorLanjut: String? = null,
    @SerialName("harga_berubah") val hargaBerubah: Boolean = false,
)

@Serializable
data class VarianProduk(
    val id: String,
    @SerialName("produk_id") val produkId: String? = null,
    @SerialName("nama_varian") val namaVarian: String,
    val sku: String,
    @SerialName("harga_idr") val hargaIdr: Int,
    val stok: Int,
    val satuan: String,
    @SerialName("berat_kirim_gram") val beratKirimGram: Int,
)

@Serializable
data class MediaProduk(
    val url: String,
    val jenis: String,
    val utama: Boolean,
)

@Serializable
data class TripLumbung(
    @SerialName("trip_id") val tripId: String,
    @SerialName("jadwal_berangkat") val jadwalBerangkat: String,
    val rute: String? = null,
    @SerialName("titik_serah") val titikSerah: String? = null,
    @SerialName("sisa_kapasitas") val sisaKapasitas: Int? = null,
    @SerialName("ongkir_idr") val ongkirIdr: Int,
)

@Serializable
data class ProdukTayang(
    val id: String,
    val judul: String,
    @SerialName("kategori_kode") val kategoriKode: String,
    val tag: List<String> = emptyList(),
    val deskripsi: String,
    val lapak: LapakRingkas,
    val varian: List<VarianProduk>,
    val media: List<MediaProduk> = emptyList(),
    @SerialName("rating_rata") val ratingRata: Double,
    @SerialName("total_ulasan") val totalUlasan: Int,
    @SerialName("stok_per_varian") val stokPerVarian: Map<String, Int>,
    @SerialName("trip_tersedia") val tripTersedia: List<TripLumbung>,
    val basi: Boolean = false,
) {
    /** Stok terbaru untuk sebuah varian: `stok_per_varian` menang atas `varian[].stok`. */
    fun stokVarian(varianId: String): Int =
        stokPerVarian[varianId] ?: varian.firstOrNull { it.id == varianId }?.stok ?: 0

    /** Produk stok 0 tidak boleh masuk keranjang (PSR-KMP-002). */
    fun bisaMasukKeranjang(varianId: String): Boolean =
        varian.any { it.id == varianId } && stokVarian(varianId) > 0

    /** Harga tampil = harga bayar varian (tanpa mark-up). Null bila tidak ada varian. */
    val hargaMulaiIdr: Int? get() = varian.minOfOrNull { it.hargaIdr }
}
