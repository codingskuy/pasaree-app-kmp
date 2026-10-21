package com.pasaree.shared

import com.pasaree.shared.domain.AmbilDetailProduk
import com.pasaree.shared.domain.CariProduk
import com.pasaree.shared.domain.JenisGalat
import com.pasaree.shared.domain.KriteriaKatalog
import com.pasaree.shared.domain.PagerProduk
import com.pasaree.shared.model.ProdukTayang
import com.pasaree.shared.network.ApiException
import com.pasaree.shared.network.JsonPasaree
import com.pasaree.shared.network.KtorPasareeApi
import com.pasaree.shared.network.buatKlienHttp
import com.pasaree.shared.util.TujuanTautan
import com.pasaree.shared.util.formatIdr
import com.pasaree.shared.util.uraiTautan
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val JSON_TYPE = headersOf(HttpHeaders.ContentType, "application/json")

private fun halaman(vararg id: String, cursor: String?, hargaBerubah: Boolean = false): String {
    val item = id.joinToString(",") {
        """{"id":"$it","judul":"Produk $it","kategori_kode":"bumbu-dapur","harga_mulai_idr":18000,
            "rating_rata":4.8,"lapak":{"slug":"mbok-d","nama_lapak":"Mbok D"},"media_utama":null}"""
    }
    val c = if (cursor == null) "null" else "\"$cursor\""
    return """{"data":[$item],"cursor_lanjut":$c,"harga_berubah":$hargaBerubah}"""
}

private const val DETAIL_JSON = """
{"id":"P1","judul":"Sambal Bawang","kategori_kode":"bumbu-dapur","tag":["pedas"],"deskripsi":"Enak",
 "lapak":{"slug":"mbok-d","nama_lapak":"Mbok D"},
 "varian":[{"id":"V1","nama_varian":"150 g","sku":"LPK-0001-0001","harga_idr":18000,"stok":5,"satuan":"g","berat_kirim_gram":150},
           {"id":"V2","nama_varian":"300 g","sku":"LPK-0001-0002","harga_idr":34000,"stok":0,"satuan":"g","berat_kirim_gram":300}],
 "media":[],"rating_rata":4.8,"total_ulasan":3,
 "stok_per_varian":{"V1":5,"V2":0},
 "trip_tersedia":[{"trip_id":"TRP-1","jadwal_berangkat":"2026-10-04T10:00:00Z","ongkir_idr":8000}],
 "kolom_baru_server":"diabaikan"}
"""

class KatalogTest {

    @Test
    fun formatIdr_memakaiTitikRibuan() {
        assertEquals("Rp0", formatIdr(0))
        assertEquals("Rp500", formatIdr(500))
        assertEquals("Rp18.000", formatIdr(18_000))
        assertEquals("Rp56.000", formatIdr(56_000))
        assertEquals("Rp1.250.000", formatIdr(1_250_000))
        assertEquals("-Rp2.000", formatIdr(-2_000))
    }

    @Test
    fun deepLink_skemaApp_dan_https() {
        assertEquals(TujuanTautan.Produk("01J0ABC"), uraiTautan("chefgenie://pasaree/produk/01J0ABC"))
        assertEquals(TujuanTautan.Produk("01J0ABC"), uraiTautan("https://pasaree.chefgenie.id/p/01J0ABC?utm=x"))
        assertEquals(TujuanTautan.Produk("P1"), uraiTautan("chefgenie://pasaree/produk/P1/"))
    }

    @Test
    fun deepLink_menolakTautanAsing() {
        assertNull(uraiTautan(null))
        assertNull(uraiTautan("chefgenie://lumbung/produk/P1"))
        assertNull(uraiTautan("https://evil.example/p/P1"))
        assertNull(uraiTautan("chefgenie://pasaree/produk/"))
        assertNull(uraiTautan("chefgenie://pasaree/produk/a/b"))
        assertNull(uraiTautan("chefgenie://pasaree/produk/../x"))
    }

    @Test
    fun detail_stokNol_tidakBisaMasukKeranjang_dan_hargaApaAdanya() {
        val p = JsonPasaree.decodeFromString<ProdukTayang>(DETAIL_JSON)
        assertTrue(p.bisaMasukKeranjang("V1"))
        assertFalse(p.bisaMasukKeranjang("V2"), "stok 0 tidak boleh masuk keranjang")
        assertFalse(p.bisaMasukKeranjang("TIDAK_ADA"))
        assertEquals(5, p.stokVarian("V1"))
        assertEquals(18_000, p.hargaMulaiIdr, "harga tampil = harga bayar varian, tanpa mark-up")
        assertEquals(1, p.tripTersedia.size)
    }

    @Test
    fun stokPerVarian_menangAtasStokVarian() {
        val p = JsonPasaree.decodeFromString<ProdukTayang>(DETAIL_JSON).copy(stokPerVarian = mapOf("V1" to 0))
        assertFalse(p.bisaMasukKeranjang("V1"))
    }

    @Test
    fun daftarProduk_limitDefault20_cursor_dan_header() = runTest {
        val diminta = mutableListOf<HttpRequestData>()
        val api = KtorPasareeApi(buatKlienHttp(
            baseUrl = "http://uji/v1",
            engine = MockEngine { req ->
                diminta += req
                respond(halaman("A", cursor = "C1"), HttpStatusCode.OK, JSON_TYPE)
            },
        ))
        val hasil = CariProduk(api)(KriteriaKatalog(q = "sambal"))
        val h = hasil.getOrThrow()
        assertEquals("C1", h.cursorLanjut)
        val url = diminta.single().url
        assertEquals("20", url.parameters["limit"])
        assertEquals("sambal", url.parameters["q"])
        assertEquals("/v1/katalog/produk", url.encodedPath)
        assertEquals("kmp-android", diminta.single().headers["X-Client"])
        assertEquals("0.1.0", diminta.single().headers["X-App-Version"])
    }

    @Test
    fun daftarProduk_limitDijepitMaks50() = runTest {
        val diminta = mutableListOf<HttpRequestData>()
        val api = KtorPasareeApi(buatKlienHttp(baseUrl = "http://uji/v1", engine = MockEngine { req ->
            diminta += req
            respond(halaman(cursor = null), HttpStatusCode.OK, JSON_TYPE)
        }))
        CariProduk(api)(limit = 500).getOrThrow()
        CariProduk(api)(limit = 0).getOrThrow()
        assertEquals(listOf("50", "1"), diminta.map { it.url.parameters["limit"] })
    }

    @Test
    fun pager_parsingCursor_menambahHalaman_dan_berhentiSaatHabis() = runTest {
        val kursor = mutableListOf<String?>()
        val api = KtorPasareeApi(buatKlienHttp(baseUrl = "http://uji/v1", engine = MockEngine { req ->
            val c = req.url.parameters["cursor"]
            kursor += c
            when (c) {
                null -> respond(halaman("A", "B", cursor = "C1", hargaBerubah = true), HttpStatusCode.OK, JSON_TYPE)
                "C1" -> respond(halaman("B", "C", cursor = null), HttpStatusCode.OK, JSON_TYPE)
                else -> respond("{}", HttpStatusCode.BadRequest, JSON_TYPE)
            }
        }))
        val pager = PagerProduk(CariProduk(api))
        pager.muatAwal()
        assertEquals(listOf("A", "B"), pager.state.value.item.map { it.id })
        assertEquals("C1", pager.state.value.cursorLanjut)
        assertTrue(pager.state.value.hargaBerubah)
        pager.muatLagi()
        assertEquals(listOf("A", "B", "C"), pager.state.value.item.map { it.id }, "duplikat B dibuang")
        assertTrue(pager.state.value.habis)
        pager.muatLagi() // tidak ada panggilan lagi
        assertEquals(listOf(null, "C1"), kursor)
    }

    @Test
    fun detail_404_dipetakanKeGalatDomain() = runTest {
        val api = KtorPasareeApi(buatKlienHttp(baseUrl = "http://uji/v1", engine = MockEngine {
            respond("""{"kode":"PESANAN_TIDAK_DITEMUKAN","pesan":"Tidak ada"}""", HttpStatusCode.NotFound, JSON_TYPE)
        }))
        val e = AmbilDetailProduk(api)("X").exceptionOrNull()
        assertIs<com.pasaree.shared.domain.GalatDomain>(e)
        assertEquals(JenisGalat.TIDAK_DITEMUKAN, e.jenis)
        assertEquals("PESANAN_TIDAK_DITEMUKAN", e.kode)
    }

    @Test
    fun get_diulang2x_saat5xx() = runTest {
        var panggilan = 0
        val api = KtorPasareeApi(buatKlienHttp(baseUrl = "http://uji/v1", jedaRetryMs = 1, engine = MockEngine {
            panggilan++
            if (panggilan < 3) respond("{}", HttpStatusCode.InternalServerError, JSON_TYPE)
            else respond(halaman("A", cursor = null), HttpStatusCode.OK, JSON_TYPE)
        }))
        CariProduk(api)().getOrThrow()
        assertEquals(3, panggilan, "1 percobaan + 2 retry untuk GET idempoten")
    }

    @Test
    fun apiException_membawaKode() {
        val e = ApiException(409, com.pasaree.shared.model.GalatApi("STOK_HABIS", "habis"))
        assertEquals("STOK_HABIS", e.kode)
    }
}
