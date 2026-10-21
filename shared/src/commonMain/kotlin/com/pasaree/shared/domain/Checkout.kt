package com.pasaree.shared.domain

import com.pasaree.shared.model.CheckoutRequest
import com.pasaree.shared.model.KodeGalat
import com.pasaree.shared.model.PesananBuat
import com.pasaree.shared.model.PesananDibuat
import com.pasaree.shared.model.PratinjauCheckout
import com.pasaree.shared.network.PasareeApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

sealed interface StateCheckout {
    data object Belum : StateCheckout
    data object Memuat : StateCheckout

    /**
     * Pratinjau dari server siap. [alasanUlang] terisi bila pratinjau ini ditampilkan ulang
     * karena 409 (`STOK_HABIS`, `HARGA_BERUBAH`, `TRIP_PENUH`) beserta [pesanUlang] dari server.
     */
    data class Siap(
        val pratinjau: PratinjauCheckout,
        val alasanUlang: String? = null,
        val pesanUlang: String? = null,
    ) : StateCheckout

    data class Gagal(val galat: GalatDomain) : StateCheckout
    data class Dibuat(val pesanan: PesananDibuat) : StateCheckout
}

private val KODE_PRATINJAU_ULANG = setOf(KodeGalat.STOK_HABIS, KodeGalat.HARGA_BERUBAH, KodeGalat.TRIP_PENUH)

/**
 * Alur checkout: pratinjau SELALU dipanggil sebelum membuat pesanan, dan semua angka
 * ditampilkan apa adanya dari server (klien tidak menghitung total).
 */
class CheckoutController(
    private val api: PasareeApi,
    private val tripId: String,
    private val titikId: String,
    private val voucher: String? = null,
    private val sekarang: () -> Instant = { Clock.System.now() },
) {
    private val _state = MutableStateFlow<StateCheckout>(StateCheckout.Belum)
    val state: StateFlow<StateCheckout> = _state.asStateFlow()

    private val permintaan get() = CheckoutRequest(tripId, titikId, voucher)

    suspend fun muatPratinjau() {
        _state.value = StateCheckout.Memuat
        hasilDomain { api.pratinjauCheckout(permintaan) }.fold(
            onSuccess = { _state.value = StateCheckout.Siap(it) },
            onFailure = { tanganiGagalPratinjau(it.keGalatDomain()) },
        )
    }

    /**
     * Membuat pesanan hanya dari pratinjau yang masih berlaku. Bila 409, pratinjau dimuat ulang
     * otomatis dan keadaan menjadi [StateCheckout.Siap] dengan angka terbaru + alasan.
     */
    suspend fun buatPesanan(catatanPerLapak: Map<String, String> = emptyMap()): Result<PesananDibuat> {
        val siap = _state.value as? StateCheckout.Siap
            ?: return Result.failure(GalatDomain(JenisGalat.VALIDASI, KodeGalat.VALIDASI_GAGAL, "Pratinjau checkout wajib dimuat dulu."))
        if (kunciKedaluwarsa(siap.pratinjau)) {
            muatPratinjau()
            return Result.failure(GalatDomain(JenisGalat.KONFLIK, KodeGalat.STOK_HABIS, "Kunci stok berakhir; pratinjau dimuat ulang."))
        }
        val hasil = hasilDomain { api.buatPesanan(PesananBuat(tripId, titikId, voucher, catatanPerLapak)) }
        hasil.fold(
            onSuccess = { _state.value = StateCheckout.Dibuat(it) },
            onFailure = { e ->
                val g = e.keGalatDomain()
                if (g.jenis == JenisGalat.KONFLIK && g.kode in KODE_PRATINJAU_ULANG) tanganiGagalPratinjau(g) else _state.value = StateCheckout.Gagal(g)
            },
        )
        return hasil
    }

    private fun kunciKedaluwarsa(p: PratinjauCheckout): Boolean =
        runCatching { Instant.parse(p.kunciStokHingga) <= sekarang() }.getOrDefault(false)

    /** 409 stok/harga/trip: tampilkan ulang pratinjau dengan angka terbaru (muat ulang dari server). */
    private suspend fun tanganiGagalPratinjau(g: GalatDomain) {
        if (g.jenis == JenisGalat.KONFLIK && g.kode in KODE_PRATINJAU_ULANG) {
            hasilDomain { api.pratinjauCheckout(permintaan) }.fold(
                onSuccess = { _state.value = StateCheckout.Siap(it, alasanUlang = g.kode, pesanUlang = g.message) },
                onFailure = { _state.value = StateCheckout.Gagal(it.keGalatDomain()) },
            )
        } else {
            _state.value = StateCheckout.Gagal(g)
        }
    }
}
