package com.pasaree.shared.domain

import com.pasaree.shared.network.ApiException
import kotlinx.coroutines.CancellationException

enum class JenisGalat { JARINGAN, AUTENTIKASI, VALIDASI, TIDAK_DITEMUKAN, KONFLIK, SERVER, LAINNYA }

/** Galat domain: dipetakan dari HTTP/jaringan, tanpa tipe Ktor di UI. */
class GalatDomain(
    val jenis: JenisGalat,
    val kode: String?,
    pesan: String,
    cause: Throwable? = null,
) : Exception(pesan, cause)

internal fun Throwable.keGalatDomain(): GalatDomain = when (this) {
    is GalatDomain -> this
    is ApiException -> GalatDomain(
        jenis = when (status) {
            401 -> JenisGalat.AUTENTIKASI
            404 -> JenisGalat.TIDAK_DITEMUKAN
            409 -> JenisGalat.KONFLIK
            400, 422 -> JenisGalat.VALIDASI
            in 500..599 -> JenisGalat.SERVER
            else -> JenisGalat.LAINNYA
        },
        kode = kode,
        pesan = message ?: "Terjadi kesalahan",
        cause = this,
    )
    else -> GalatDomain(JenisGalat.JARINGAN, null, message ?: "Jaringan bermasalah", this)
}

/** Menjalankan [blok] dan memetakan kegagalan ke [GalatDomain]; pembatalan korutin tidak ditelan. */
internal suspend fun <T> hasilDomain(blok: suspend () -> T): Result<T> =
    try {
        Result.success(blok())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e.keGalatDomain())
    }
