package com.pasaree.shared.domain

import com.pasaree.shared.model.AksiPesanan
import com.pasaree.shared.model.HalamanPesanan
import com.pasaree.shared.model.KodeGalat
import com.pasaree.shared.model.PesananDetail
import com.pasaree.shared.model.ReturRequest
import com.pasaree.shared.model.StatusPesanan
import com.pasaree.shared.model.UlasanBuat
import com.pasaree.shared.network.PasareeApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

/** Interval polling minimum (kontrak: maksimal tiap 15 detik). */
const val INTERVAL_POLL_MS = 15_000L

class AmbilPesanan(private val api: PasareeApi) {
    suspend operator fun invoke(id: String): Result<PesananDetail> = hasilDomain { api.detailPesanan(id) }
    suspend fun daftar(status: String? = null, cursor: String? = null, limit: Int = 20): Result<HalamanPesanan> =
        hasilDomain { api.daftarPesanan(status, limit.coerceIn(1, 50), cursor) }
}

class KonfirmasiTerima(private val api: PasareeApi) {
    suspend operator fun invoke(id: String): Result<String> = hasilDomain { api.konfirmasiTerima(id).status }
}

/** Ajukan retur sub-pesanan; alasan 5-280 karakter (kontrak). Hanya dipanggil bila server menyediakan aksi `ajukan_retur`. */
class AjukanRetur(private val api: PasareeApi) {
    suspend operator fun invoke(pesananId: String, subId: String, alasan: String): Result<String> {
        val a = alasan.trim()
        if (a.length !in 5..280) return Result.failure(GalatDomain(JenisGalat.VALIDASI, KodeGalat.VALIDASI_GAGAL, "Alasan retur 5 sampai 280 karakter."))
        return hasilDomain { api.ajukanRetur(pesananId, ReturRequest(subId, a)).status }
    }
}

/**
 * Polling status pesanan: ambil segera, lalu setiap [intervalMs] (dijepit minimal 15 dtk),
 * berhenti di status akhir. Galat jaringan tidak menghentikan polling.
 */
fun pantauPesanan(api: PasareeApi, id: String, intervalMs: Long = INTERVAL_POLL_MS): Flow<Result<PesananDetail>> = flow {
    val jeda = intervalMs.coerceAtLeast(INTERVAL_POLL_MS)
    while (true) {
        val hasil = hasilDomain { api.detailPesanan(id) }
        emit(hasil)
        if (hasil.getOrNull()?.status in StatusPesanan.AKHIR) return@flow
        delay(jeda)
    }
}

/** Sisa waktu bayar (batas 30 menit dari server); tidak pernah negatif. */
fun sisaBatasBayar(batasIso: String, sekarang: Instant = Clock.System.now()): Duration =
    runCatching { (Instant.parse(batasIso) - sekarang).coerceAtLeast(Duration.ZERO) }.getOrDefault(Duration.ZERO)

/** Batas bayar 30 menit sejak [dibuatIso] (asumsi bila server tidak mengirim `batas_bayar_hingga`). */
fun batasBayarDefault(dibuatIso: String): String? =
    runCatching { (Instant.parse(dibuatIso) + 30.minutes).toString() }.getOrNull()

/** Hitung mundur "mm:ss" terhadap jam sekarang (dipakai UI agar tidak bergantung pada kotlinx-datetime). */
fun hitungMundurSekarang(batasIso: String): String = formatHitungMundur(sisaBatasBayar(batasIso))

/** Format hitung mundur "mm:ss". */
fun formatHitungMundur(d: Duration): String {
    val total = d.inWholeSeconds.coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return "${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
}

/** Aksi hanya tampil bila server mengembalikannya di `aksi_tersedia`. */
data class AksiTampil(val konfirmasiTerima: Boolean, val ajukanRetur: Boolean)

fun aksiTampil(d: PesananDetail) = AksiTampil(
    konfirmasiTerima = AksiPesanan.KONFIRMASI_TERIMA in d.aksiTersedia,
    ajukanRetur = AksiPesanan.AJUKAN_RETUR in d.aksiTersedia,
)

/** Tahap timeline normal: sudah dicapai / belum, berdasarkan titik timeline dari server. */
data class TahapTimeline(val status: String, val tercapai: Boolean, val waktu: String?)

fun susunTimeline(d: PesananDetail): List<TahapTimeline> {
    val waktu = d.timeline.associate { it.status to it.waktu }
    return StatusPesanan.JALUR_NORMAL.map { TahapTimeline(it, it in waktu || it == d.status, waktu[it]) }
}

val BATAS_ULASAN = 7.days

/** Ulasan: pesanan SELESAI, <=7 hari setelah selesai. (1 ulasan per produk per pesanan ditegakkan server: 409.) */
fun bisaMengulas(d: PesananDetail, sekarang: Instant = Clock.System.now()): Boolean {
    if (d.status != StatusPesanan.SELESAI) return false
    val selesai = d.timeline.lastOrNull { it.status == StatusPesanan.SELESAI }?.waktu ?: return false
    val t = runCatching { Instant.parse(selesai) }.getOrNull() ?: return false
    return sekarang - t <= BATAS_ULASAN
}

class KirimUlasan(private val api: PasareeApi) {
    private val sudah = mutableSetOf<String>()

    suspend operator fun invoke(u: UlasanBuat): Result<Unit> {
        if (u.bintang !in 1..5) return Result.failure(GalatDomain(JenisGalat.VALIDASI, KodeGalat.VALIDASI_GAGAL, "Bintang harus 1 sampai 5."))
        val kunci = "${u.pesananId}|${u.produkId}"
        if (kunci in sudah) return Result.failure(GalatDomain(JenisGalat.KONFLIK, null, "Produk ini sudah Anda ulas untuk pesanan ini."))
        return hasilDomain { api.buatUlasan(u) }.onSuccess { sudah += kunci }
            // 409 dari server berarti ulasan sudah ada: tandai agar tidak dicoba lagi.
            .onFailure { if ((it as? GalatDomain)?.jenis == JenisGalat.KONFLIK) sudah += kunci }
    }
}
