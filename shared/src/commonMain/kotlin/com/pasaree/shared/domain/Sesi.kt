package com.pasaree.shared.domain

import com.pasaree.shared.model.LoginRequest
import com.pasaree.shared.network.PasareeApi
import com.pasaree.shared.network.PenyediaToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sesi masuk minimal (di luar AC PSR-KMP-002..005, dibutuhkan agar keranjang/checkout dapat dicoba).
 * Token hanya di memori: penyimpanan aman (Keystore/Keychain) dan refresh otomatis belum dikerjakan.
 */
class SesiAuth(private val api: () -> PasareeApi) : PenyediaToken {
    private val _masuk = MutableStateFlow(false)
    val masuk: StateFlow<Boolean> = _masuk.asStateFlow()
    private var token: String? = null

    override suspend fun tokenAkses(): String? = token

    suspend fun login(email: String, kataSandi: String): Result<Unit> {
        if (email.isBlank() || kataSandi.length < 8) {
            return Result.failure(GalatDomain(JenisGalat.VALIDASI, "VALIDASI_GAGAL", "Email dan kata sandi (min. 8 karakter) wajib diisi."))
        }
        return hasilDomain { api().login(LoginRequest(email.trim(), kataSandi)) }.map {
            token = it.accessToken
            _masuk.value = true
        }
    }

    fun keluar() {
        token = null
        _masuk.value = false
    }
}
