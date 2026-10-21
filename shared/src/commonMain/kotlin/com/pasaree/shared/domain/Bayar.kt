package com.pasaree.shared.domain

import com.pasaree.shared.model.BayarRequest
import com.pasaree.shared.model.BayarResponse
import com.pasaree.shared.model.MetodeBayar
import com.pasaree.shared.network.PasareeApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Satu kunci idempotensi per upaya bayar (pesanan + metode). */
data class KunciTersimpan(val pesananId: String, val metode: String, val kunci: String, val dibuat: Instant)

/** Penyimpanan kunci idempotensi; implementasi persisten (SQLDelight) ada di PSR-KMP-005. */
interface KunciIdempotensiStore {
    suspend fun ambil(pesananId: String, metode: String): KunciTersimpan?
    suspend fun simpan(k: KunciTersimpan)
    suspend fun hapus(pesananId: String)
}

class KunciIdempotensiMemori : KunciIdempotensiStore {
    private val data = MutableStateFlow<Map<String, KunciTersimpan>>(emptyMap())
    private fun id(p: String, m: String) = "$p|$m"
    override suspend fun ambil(pesananId: String, metode: String) = data.value[id(pesananId, metode)]
    override suspend fun simpan(k: KunciTersimpan) { data.value = data.value + (id(k.pesananId, k.metode) to k) }
    override suspend fun hapus(pesananId: String) { data.value = data.value.filterValues { it.pesananId != pesananId } }
}

/** Kunci disimpan 24 jam; dipakai ulang untuk retry aman, kedaluwarsa menghasilkan kunci baru. */
class KunciIdempotensi(
    private val store: KunciIdempotensiStore,
    private val sekarang: () -> Instant = { Clock.System.now() },
    @OptIn(ExperimentalUuidApi::class) private val buatUuid: () -> String = { Uuid.random().toString() },
) {
    suspend fun ambilAtauBuat(pesananId: String, metode: String): String {
        val ada = store.ambil(pesananId, metode)
        val sekarangIni = sekarang()
        if (ada != null && sekarangIni - ada.dibuat < MASA_BERLAKU) return ada.kunci
        val baru = buatUuid()
        store.simpan(KunciTersimpan(pesananId, metode, baru, sekarangIni))
        return baru
    }

    suspend fun lupakan(pesananId: String) = store.hapus(pesananId)

    companion object {
        val MASA_BERLAKU = 24.hours
    }
}

/**
 * Bayar pesanan. SELALU mengirim `idempotency_key`; tidak ada retry otomatis (klien HTTP hanya
 * mengulang GET). Percobaan ulang oleh pengguna memakai kunci yang sama sehingga aman dari tagihan ganda.
 * Aksi bayar tidak pernah masuk antrean offline.
 */
class BayarPesanan(
    private val api: PasareeApi,
    private val kunci: KunciIdempotensi,
) {
    suspend operator fun invoke(pesananId: String, metode: MetodeBayar): Result<BayarResponse> {
        val key = kunci.ambilAtauBuat(pesananId, metode.kode)
        return hasilDomain { api.bayar(pesananId, BayarRequest(metode.kode, key)) }.also { hasil ->
            // Validasi ditolak server (422): kunci tidak berguna lagi, upaya berikutnya memakai kunci baru.
            val g = hasil.exceptionOrNull() as? GalatDomain
            if (g?.jenis == JenisGalat.VALIDASI) kunci.lupakan(pesananId)
        }
    }
}
