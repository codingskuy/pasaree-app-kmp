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
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess

class KtorPasareeApi(private val klien: HttpClient) : PasareeApi {

    override suspend fun daftarProduk(
        q: String?,
        kategori: String?,
        tag: String?,
        lapakSlug: String?,
        sort: String?,
        limit: Int,
        cursor: String?,
    ): HalamanProduk = klien.get("katalog/produk") {
        q?.takeIf { it.isNotBlank() }?.let { parameter("q", it) }
        kategori?.let { parameter("kategori", it) }
        tag?.let { parameter("tag", it) }
        lapakSlug?.let { parameter("lapak_slug", it) }
        sort?.let { parameter("sort", it) }
        parameter("limit", limit.coerceIn(1, LIMIT_MAKS))
        cursor?.let { parameter("cursor", it) }
    }.ambil()

    override suspend fun detailProduk(id: String): ProdukTayang =
        klien.get("katalog/produk/$id").ambil()

    // PUT bersifat idempoten (server menimpa qty), tetapi plugin retry hanya mengulang GET: pengulangan
    // keranjang ditangani antrean aksi di PSR-KMP-005, bukan pengulangan buta di lapisan HTTP.
    override suspend fun ubahKeranjang(ubah: KeranjangUbah): KeranjangServer =
        klien.put("keranjang") { contentType(ContentType.Application.Json); setBody(ubah) }.ambil()

    override suspend fun daftarTrip(): DaftarTrip = klien.get("trip").ambil()

    override suspend fun pratinjauCheckout(req: CheckoutRequest): PratinjauCheckout =
        klien.post("checkout/pratinjau") { contentType(ContentType.Application.Json); setBody(req) }.ambil()

    override suspend fun buatPesanan(req: PesananBuat): PesananDibuat =
        klien.post("pesanan") { contentType(ContentType.Application.Json); setBody(req) }.ambil()

    // Bayar memakai POST: plugin retry hanya GET, jadi tidak pernah diulang otomatis.
    override suspend fun bayar(pesananId: String, req: BayarRequest): BayarResponse =
        klien.post("pesanan/$pesananId/bayar") { contentType(ContentType.Application.Json); setBody(req) }.ambil()

    override suspend fun detailPesanan(id: String): PesananDetail = klien.get("pesanan/$id").ambil()

    override suspend fun daftarPesanan(status: String?, limit: Int, cursor: String?): HalamanPesanan =
        klien.get("pesanan") {
            status?.let { parameter("status", it) }
            parameter("limit", limit.coerceIn(1, LIMIT_MAKS))
            cursor?.let { parameter("cursor", it) }
        }.ambil()

    override suspend fun konfirmasiTerima(id: String): KonfirmasiTerimaResponse =
        klien.post("pesanan/$id/konfirmasi-terima").ambil()

    override suspend fun ajukanRetur(pesananId: String, req: ReturRequest): ReturResponse =
        klien.post("pesanan/$pesananId/retur") { contentType(ContentType.Application.Json); setBody(req) }.ambil()

    override suspend fun buatUlasan(u: UlasanBuat) {
        val r = klien.post("ulasan") { contentType(ContentType.Application.Json); setBody(u) }
        if (!r.status.isSuccess()) throw r.galatDariRespons()
    }

    override suspend fun login(req: LoginRequest): TokenResponse =
        klien.post("auth/login") { contentType(ContentType.Application.Json); setBody(req) }.ambil()
}

/** Membaca badan sukses atau melempar [ApiException] dengan `GalatApi` baku. */
internal suspend inline fun <reified T> HttpResponse.ambil(): T {
    if (status.isSuccess()) return body()
    throw galatDariRespons()
}

internal suspend fun HttpResponse.galatDariRespons(): ApiException {
    val galat = runCatching { body<GalatApi>() }.getOrNull()
    return ApiException(status.value, galat)
}

/** Pabrik API untuk lapisan UI agar `composeApp` tidak bergantung langsung pada Ktor. */
fun buatPasareeApi(
    baseUrl: String = BASE_URL_PRODUKSI,
    penyediaToken: PenyediaToken = PenyediaToken { null },
): PasareeApi = KtorPasareeApi(buatKlienHttp(baseUrl = baseUrl, penyediaToken = penyediaToken))
