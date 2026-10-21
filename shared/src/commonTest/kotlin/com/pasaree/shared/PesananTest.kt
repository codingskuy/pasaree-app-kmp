package com.pasaree.shared

import com.pasaree.shared.domain.BATAS_ULASAN
import com.pasaree.shared.domain.BayarPesanan
import com.pasaree.shared.domain.GalatDomain
import com.pasaree.shared.domain.JenisGalat
import com.pasaree.shared.domain.KirimUlasan
import com.pasaree.shared.domain.KunciIdempotensi
import com.pasaree.shared.domain.KunciIdempotensiMemori
import com.pasaree.shared.domain.aksiTampil
import com.pasaree.shared.domain.bisaMengulas
import com.pasaree.shared.domain.formatHitungMundur
import com.pasaree.shared.domain.pantauPesanan
import com.pasaree.shared.domain.sisaBatasBayar
import com.pasaree.shared.domain.susunTimeline
import com.pasaree.shared.model.BayarResponse
import com.pasaree.shared.model.MetodeBayar
import com.pasaree.shared.model.PesananDetail
import com.pasaree.shared.model.StatusPesanan
import com.pasaree.shared.model.TitikTimeline
import com.pasaree.shared.model.UlasanBuat
import com.pasaree.shared.network.ApiException
import com.pasaree.shared.network.KtorPasareeApi
import com.pasaree.shared.network.buatKlienHttp
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val T0 = Instant.parse("2026-10-04T10:00:00Z")

private fun detail(
    status: String = StatusPesanan.DIPESAN,
    aksi: List<String> = emptyList(),
    timeline: List<TitikTimeline> = emptyList(),
) = PesananDetail(id = "PSR-1", totalIdr = 66_000, status = status, dibuatPada = "2026-10-04T10:00:00Z", aksiTersedia = aksi, timeline = timeline)

class BayarIdempotenTest {

    @Test
    fun kunciDipakaiUlang_dalam24Jam_dan_baruSetelahnya() = runTest {
        var sekarang = T0
        var n = 0
        val k = KunciIdempotensi(KunciIdempotensiMemori(), { sekarang }, { "uuid-${++n}" })
        val a = k.ambilAtauBuat("PSR-1", "QRIS")
        sekarang = T0 + 23.hours
        assertEquals(a, k.ambilAtauBuat("PSR-1", "QRIS"), "retry dalam 24 jam memakai kunci yang sama")
        sekarang = T0 + 24.hours + 1.seconds
        assertNotEquals(a, k.ambilAtauBuat("PSR-1", "QRIS"), "kedaluwarsa setelah 24 jam")
    }

    @Test
    fun metodeBerbeda_kunciBerbeda() = runTest {
        var n = 0
        val k = KunciIdempotensi(KunciIdempotensiMemori(), { T0 }, { "uuid-${++n}" })
        assertNotEquals(k.ambilAtauBuat("PSR-1", "QRIS"), k.ambilAtauBuat("PSR-1", "VA_BCA"))
    }

    @Test
    fun bayar_selaluMengirimKunci_dan_retryPenggunaMemakaiKunciSama() = runTest {
        val kunciTerkirim = mutableListOf<String>()
        val api = ApiPalsu().apply {
            onBayar = { _, req ->
                kunciTerkirim += req.idempotencyKey
                if (kunciTerkirim.size == 1) throw RuntimeException("putus") else BayarResponse("DIBAYAR", "ESC-1")
            }
        }
        val bayar = BayarPesanan(api, KunciIdempotensi(KunciIdempotensiMemori(), { T0 }, { "uuid-tetap" }))
        assertTrue(bayar("PSR-1", MetodeBayar.QRIS).isFailure)
        assertEquals("ESC-1", bayar("PSR-1", MetodeBayar.QRIS).getOrThrow().escrowId)
        assertEquals(listOf("uuid-tetap", "uuid-tetap"), kunciTerkirim)
    }

    @Test
    fun validasiDitolak_kunciDilupakan_upayaBerikutnyaKunciBaru() = runTest {
        var n = 0
        val kunciTerkirim = mutableListOf<String>()
        val api = ApiPalsu().apply { onBayar = { _, req -> kunciTerkirim += req.idempotencyKey; throw ApiException(422, null) } }
        val bayar = BayarPesanan(api, KunciIdempotensi(KunciIdempotensiMemori(), { T0 }, { "uuid-${++n}" }))
        bayar("PSR-1", MetodeBayar.QRIS)
        bayar("PSR-1", MetodeBayar.QRIS)
        assertEquals(listOf("uuid-1", "uuid-2"), kunciTerkirim)
    }

    @Test
    fun postBayar_tidakDiulangOtomatis_olehKlienHttp() = runTest {
        val diminta = mutableListOf<HttpRequestData>()
        val klien = buatKlienHttp(baseUrl = "http://uji/v1", jedaRetryMs = 1, engine = MockEngine { req ->
            diminta += req
            respond("{}", HttpStatusCode.InternalServerError, headersOf(HttpHeaders.ContentType, "application/json"))
        })
        val bayar = BayarPesanan(KtorPasareeApi(klien), KunciIdempotensi(KunciIdempotensiMemori(), { T0 }, { "11111111-1111-4111-8111-111111111111" }))
        assertTrue(bayar("PSR-1", MetodeBayar.VA_BCA).isFailure)
        assertEquals(1, diminta.size, "POST bayar tidak auto-retry")
        assertEquals(HttpMethod.Post, diminta.single().method)
        val badan = diminta.single().body.toByteArray().decodeToString()
        assertTrue("\"idempotency_key\":\"11111111-1111-4111-8111-111111111111\"" in badan, badan)
        assertTrue("\"metode\":\"VA_BCA\"" in badan, badan)
    }
}

class StatusPesananTest {

    @Test
    fun polling_tidakLebihCepatDari15Detik_danBerhentiDiStatusAkhir() = runTest {
        val waktuPanggil = mutableListOf<Long>()
        var n = 0
        val api = ApiPalsu().apply {
            onDetailPesanan = {
                waktuPanggil += testScheduler.currentTime
                n++
                detail(status = if (n < 4) StatusPesanan.DIBAYAR else StatusPesanan.SELESAI)
            }
        }
        // interval diminta 1 dtk, tetapi dijepit ke minimal 15 dtk
        val hasil = pantauPesanan(api, "PSR-1", intervalMs = 1_000).toList()
        assertEquals(4, hasil.size)
        assertEquals(listOf(0L, 15_000L, 30_000L, 45_000L), waktuPanggil)
        assertEquals(StatusPesanan.SELESAI, hasil.last().getOrThrow().status)
    }

    @Test
    fun polling_galatTidakMenghentikan() = runTest {
        var n = 0
        val api = ApiPalsu().apply {
            onDetailPesanan = { if (n++ == 0) throw RuntimeException("luring") else detail(StatusPesanan.DIBATALKAN) }
        }
        val hasil = pantauPesanan(api, "PSR-1").toList()
        assertTrue(hasil[0].isFailure)
        assertEquals(StatusPesanan.DIBATALKAN, hasil[1].getOrThrow().status)
    }

    @Test
    fun hitungMundurBatasBayar30Menit() {
        val batas = "2026-10-04T10:30:00Z"
        assertEquals("30:00", formatHitungMundur(sisaBatasBayar(batas, T0)))
        assertEquals("00:01", formatHitungMundur(sisaBatasBayar(batas, T0 + 29.minutes + 59.seconds)))
        assertEquals("00:00", formatHitungMundur(sisaBatasBayar(batas, T0 + 31.minutes)), "tidak negatif")
        assertEquals("00:00", formatHitungMundur(sisaBatasBayar("rusak", T0)))
    }

    @Test
    fun aksi_hanyaTampilBilaDikembalikanServer() {
        assertEquals(false, aksiTampil(detail()).konfirmasiTerima)
        assertEquals(false, aksiTampil(detail()).ajukanRetur)
        val a = aksiTampil(detail(aksi = listOf("konfirmasi_terima")))
        assertTrue(a.konfirmasiTerima)
        assertFalse(a.ajukanRetur)
        assertTrue(aksiTampil(detail(aksi = listOf("konfirmasi_terima", "ajukan_retur"))).ajukanRetur)
    }

    @Test
    fun timeline_dariDipesanSampaiSelesai() {
        val d = detail(
            status = StatusPesanan.DIBAYAR,
            timeline = listOf(TitikTimeline("DIPESAN", "2026-10-04T10:00:00Z"), TitikTimeline("DIBAYAR", "2026-10-04T10:05:00Z")),
        )
        val t = susunTimeline(d)
        assertEquals(StatusPesanan.JALUR_NORMAL, t.map { it.status })
        assertEquals(listOf(true, true, false, false, false, false), t.map { it.tercapai })
        assertEquals("2026-10-04T10:05:00Z", t[1].waktu)
    }
}

class ReturTest {
    @Test
    fun alasanHarus5sampai280_dan_dikirimDenganSubId() = runTest {
        val api = ApiPalsu()
        val retur = com.pasaree.shared.domain.AjukanRetur(api)
        assertTrue(retur("PSR-1", "SUB-1", "abc").isFailure)
        assertTrue(retur("PSR-1", "SUB-1", "x".repeat(281)).isFailure)
        assertTrue(api.panggilan.isEmpty(), "alasan tidak valid tidak dikirim")
        assertEquals("DIKEMBALIKAN", retur("PSR-1", "SUB-1", "kemasan bocor").getOrThrow())
        assertEquals(listOf("retur:PSR-1:SUB-1"), api.panggilan)
    }
}

class UlasanTest {
    private val selesai = detail(StatusPesanan.SELESAI, timeline = listOf(TitikTimeline("SELESAI", "2026-10-04T10:00:00Z")))

    @Test
    fun ulasan_hanyaSetelahSelesai_danMaks7Hari() {
        assertFalse(bisaMengulas(detail(StatusPesanan.DIBAYAR), T0))
        assertTrue(bisaMengulas(selesai, T0 + BATAS_ULASAN))
        assertFalse(bisaMengulas(selesai, T0 + BATAS_ULASAN + 1.seconds))
        assertFalse(bisaMengulas(detail(StatusPesanan.SELESAI), T0), "tanpa titik SELESAI di timeline: tidak bisa dipastikan")
    }

    @Test
    fun bintang1sampai5_dan_satuUlasanPerProdukPerPesanan() = runTest {
        var kirim = 0
        val api = ApiPalsu().apply { onUlasan = { kirim++ } }
        val ulas = KirimUlasan(api)
        assertTrue(ulas(UlasanBuat("PSR-1", "P1", 0)).isFailure)
        assertTrue(ulas(UlasanBuat("PSR-1", "P1", 6)).isFailure)
        assertEquals(0, kirim)
        assertTrue(ulas(UlasanBuat("PSR-1", "P1", 5, "enak")).isSuccess)
        val kedua = ulas(UlasanBuat("PSR-1", "P1", 4)).exceptionOrNull() as GalatDomain
        assertEquals(JenisGalat.KONFLIK, kedua.jenis)
        assertEquals(1, kirim, "ulasan kedua untuk produk yang sama tidak dikirim")
        assertTrue(ulas(UlasanBuat("PSR-1", "P2", 3)).isSuccess)
    }

    @Test
    fun server409_menandaiSudahDiulas() = runTest {
        var kirim = 0
        val api = ApiPalsu().apply { onUlasan = { kirim++; throw ApiException(409, null) } }
        val ulas = KirimUlasan(api)
        assertTrue(ulas(UlasanBuat("PSR-1", "P1", 5)).isFailure)
        assertTrue(ulas(UlasanBuat("PSR-1", "P1", 5)).isFailure)
        assertEquals(1, kirim)
    }
}
