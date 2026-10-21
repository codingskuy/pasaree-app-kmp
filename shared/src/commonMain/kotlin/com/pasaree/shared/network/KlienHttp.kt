package com.pasaree.shared.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** Platform klien untuk header `X-Client` (kmp-android | kmp-ios). */
expect fun klienPlatform(): String

const val BASE_URL_PRODUKSI = "https://api.pasaree.chefgenie.id/v1"
const val TIMEOUT_MS = 15_000L
const val MAKS_RETRY_GET = 2

val JsonPasaree: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

/** Penyedia token akses (JWT audiens `pasaree`); null bila belum masuk. */
fun interface PenyediaToken {
    suspend fun tokenAkses(): String?
}

/**
 * Klien Ktor baku: timeout 15 dtk, retry 2x HANYA untuk GET (idempoten),
 * header `X-Client` / `X-App-Version` / `Authorization`.
 * [engine] diisi mesin uji (MockEngine); null memakai mesin platform.
 */
fun buatKlienHttp(
    baseUrl: String = BASE_URL_PRODUKSI,
    versiApp: String = "0.1.0",
    penyediaToken: PenyediaToken = PenyediaToken { null },
    jedaRetryMs: Long = 500,
    engine: HttpClientEngine? = null,
): HttpClient {
    val konfigurasi: HttpClientConfig<*>.() -> Unit = {
        install(ContentNegotiation) { json(JsonPasaree) }
        install(HttpTimeout) {
            requestTimeoutMillis = TIMEOUT_MS
            connectTimeoutMillis = TIMEOUT_MS
            socketTimeoutMillis = TIMEOUT_MS
        }
        install(HttpRequestRetry) {
            maxRetries = MAKS_RETRY_GET
            retryIf { request, response -> request.method == HttpMethod.Get && response.status.value in 500..599 }
            retryOnExceptionIf { request, _ -> request.method == HttpMethod.Get }
            constantDelay(millis = jedaRetryMs, randomizationMs = 0)
        }
        defaultRequest {
            url(baseUrl.trimEnd('/') + "/")
            headers.append("X-Client", klienPlatform())
            headers.append("X-App-Version", versiApp)
        }
    }
    val klien = if (engine != null) HttpClient(engine, konfigurasi) else HttpClient(konfigurasi)
    klien.sendPipeline.intercept(io.ktor.client.request.HttpSendPipeline.State) {
        val token = penyediaToken.tokenAkses()
        if (token != null && !context.headers.contains(HttpHeaders.Authorization)) {
            context.headers.append(HttpHeaders.Authorization, "Bearer $token")
        }
    }
    return klien
}
