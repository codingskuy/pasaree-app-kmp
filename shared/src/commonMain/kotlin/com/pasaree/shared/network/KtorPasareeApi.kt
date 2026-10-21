package com.pasaree.shared.network

import com.pasaree.shared.model.GalatApi
import com.pasaree.shared.model.HalamanProduk
import com.pasaree.shared.model.ProdukTayang
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
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
