package com.pasaree.shared.network

import com.pasaree.shared.model.BayarRequest
import com.pasaree.shared.model.BayarResponse
import com.pasaree.shared.model.HalamanPesanan
import com.pasaree.shared.model.KonfirmasiTerimaResponse
import com.pasaree.shared.model.PesananDetail
import com.pasaree.shared.model.ReturRequest
import com.pasaree.shared.model.ReturResponse
import com.pasaree.shared.model.UlasanBuat
import com.pasaree.shared.model.CheckoutRequest
import com.pasaree.shared.model.DaftarTrip
import com.pasaree.shared.model.GalatApi
import com.pasaree.shared.model.HalamanProduk
import com.pasaree.shared.model.KeranjangServer
import com.pasaree.shared.model.KeranjangUbah
import com.pasaree.shared.model.LoginRequest
import com.pasaree.shared.model.PesananBuat
import com.pasaree.shared.model.PesananDibuat
import com.pasaree.shared.model.PratinjauCheckout
import com.pasaree.shared.model.ProdukTayang
import com.pasaree.shared.model.TokenResponse

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

    /** `PUT /keranjang` (qty 0 = hapus). Idempoten: server menimpa qty varian. */
    suspend fun ubahKeranjang(ubah: KeranjangUbah): KeranjangServer

    /** `GET /trip` (proksi Lumbung). */
    suspend fun daftarTrip(): DaftarTrip

    /** `POST /checkout/pratinjau`: total dihitung server. 409 = STOK_HABIS/HARGA_BERUBAH/TRIP_PENUH. */
    suspend fun pratinjauCheckout(req: CheckoutRequest): PratinjauCheckout

    /** `POST /pesanan`. */
    suspend fun buatPesanan(req: PesananBuat): PesananDibuat

    /** `POST /pesanan/{id}/bayar`. TIDAK pernah diulang otomatis; `idempotency_key` wajib. */
    suspend fun bayar(pesananId: String, req: BayarRequest): BayarResponse

    /** `GET /pesanan/{id}`: detail + timeline + `aksi_tersedia`. */
    suspend fun detailPesanan(id: String): PesananDetail

    /** `GET /pesanan`. */
    suspend fun daftarPesanan(status: String? = null, limit: Int = LIMIT_DEFAULT, cursor: String? = null): HalamanPesanan

    /** `POST /pesanan/{id}/konfirmasi-terima`. */
    suspend fun konfirmasiTerima(id: String): KonfirmasiTerimaResponse

    /** `POST /pesanan/{id}/retur` (kontrak PSR-BE-005, backend PR #10). */
    suspend fun ajukanRetur(pesananId: String, req: ReturRequest): ReturResponse

    /** `POST /ulasan` (201 tanpa badan). */
    suspend fun buatUlasan(u: UlasanBuat)

    /** `POST /auth/login`. */
    suspend fun login(req: LoginRequest): TokenResponse
}
