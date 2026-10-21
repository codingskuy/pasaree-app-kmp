package com.pasaree.shared

import com.pasaree.shared.domain.CariProduk
import com.pasaree.shared.domain.CheckoutController
import com.pasaree.shared.domain.ItemKeranjang
import com.pasaree.shared.domain.KeranjangMemori
import com.pasaree.shared.domain.KeranjangRepository
import com.pasaree.shared.domain.KriteriaKatalog
import com.pasaree.shared.domain.StateCheckout
import com.pasaree.shared.model.HalamanProduk
import com.pasaree.shared.model.ItemKeranjangServer
import com.pasaree.shared.model.KeranjangServer
import com.pasaree.shared.model.LapakRingkas
import com.pasaree.shared.model.PratinjauCheckout
import com.pasaree.shared.model.ProdukRingkas
import com.pasaree.shared.network.ApiException
import com.pasaree.shared.network.JsonPasaree
import com.pasaree.shared.storage.CacheKatalog
import com.pasaree.shared.storage.CacheKatalogMemori
import com.pasaree.shared.storage.MetaSinkronMemori
import com.pasaree.shared.sync.AntreanMemori
import com.pasaree.shared.sync.JenisAksi
import com.pasaree.shared.sync.KUNCI_BERANDA
import com.pasaree.shared.sync.StatusSinkron
import com.pasaree.shared.sync.SyncWorker
import com.pasaree.shared.sync.gabungKeranjang
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val T0 = Instant.parse("2026-10-04T10:00:00Z")

private fun lokal(varian: String, qty: Int, stok: Int = 10, harga: Int = 18_000) = ItemKeranjang(
    varianId = varian, produkId = "P", lapakSlug = "a", lapakNama = "A", judul = "J", namaVarian = "V",
    hargaIdr = harga, qty = qty, stokTersedia = stok,
)

private fun produkRingkas(id: String, harga: Int = 18_000) =
    ProdukRingkas(id, "Produk $id", "bumbu-dapur", harga, 4.5, LapakRingkas("a", "A"))

class GabungKeranjangTest {
    @Test
    fun qty_maxLokalServer() {
        val hasil = gabungKeranjang(listOf(lokal("V1", 2), lokal("V2", 5)), KeranjangServer(listOf(ItemKeranjangServer("V1", 4, 18_000), ItemKeranjangServer("V2", 1, 18_000)), 0))
        assertEquals(listOf(4, 5), hasil.map { it.qty })
    }

    @Test
    fun hargaServerMenang() {
        val hasil = gabungKeranjang(listOf(lokal("V1", 1, harga = 18_000)), KeranjangServer(listOf(ItemKeranjangServer("V1", 1, 20_000)), 20_000))
        assertEquals(20_000, hasil.single().hargaIdr)
    }

    @Test
    fun stokMenang_qtyDijepit() {
        val hasil = gabungKeranjang(listOf(lokal("V1", 2, stok = 3)), KeranjangServer(listOf(ItemKeranjangServer("V1", 9, 18_000)), 0))
        assertEquals(3, hasil.single().qty)
    }

    @Test
    fun itemHanyaDiServer_ditambahkan_itemHanyaLokal_tetap() {
        val hasil = gabungKeranjang(listOf(lokal("V1", 2)), KeranjangServer(listOf(ItemKeranjangServer("V9", 3, 5_000)), 0))
        assertEquals(setOf("V1", "V9"), hasil.map { it.varianId }.toSet())
        assertEquals(2, hasil.first { it.varianId == "V1" }.qty)
        assertEquals(3, hasil.first { it.varianId == "V9" }.qty)
    }
}

class SyncWorkerTest {
    private fun worker(api: ApiPalsu, antrean: AntreanMemori, store: KeranjangMemori = KeranjangMemori(), cache: CacheKatalog = CacheKatalogMemori({ T0 })) =
        SyncWorker(api, store, antrean, cache, MetaSinkronMemori(), { T0 })

    @Test
    fun luring_aksiTetapDiAntrean_statusLURING_laluCobaLagiBerhasil() = runTest {
        val antrean = AntreanMemori { T0 }
        antrean.tambah(JenisAksi.UBAH_KERANJANG, "V1", 2)
        var online = false
        val api = ApiPalsu().apply {
            onUbahKeranjang = { if (!online) throw RuntimeException("luring") else KeranjangServer(listOf(ItemKeranjangServer(it.varianId, it.qty, 18_000)), 36_000) }
        }
        val w = worker(api, antrean)
        assertEquals(StatusSinkron.LURING, w.now())
        assertEquals(1, antrean.semua().size)
        assertEquals(1, antrean.semua().single().percobaan)
        online = true
        assertEquals(StatusSinkron.IDLE, w.now())
        assertTrue(antrean.semua().isEmpty())
    }

    @Test
    fun antreanSatuEntriPerVarian_terakhirMenang() = runTest {
        val antrean = AntreanMemori { T0 }
        antrean.tambah(JenisAksi.UBAH_KERANJANG, "V1", 1)
        antrean.tambah(JenisAksi.UBAH_KERANJANG, "V1", 4)
        assertEquals(listOf(4), antrean.semua().map { it.qty })
    }

    @Test
    fun konflikServer_aksiDibuang_agarAntreanTidakMenyumbat() = runTest {
        val antrean = AntreanMemori { T0 }
        antrean.tambah(JenisAksi.UBAH_KERANJANG, "V1", 2)
        antrean.tambah(JenisAksi.UBAH_KERANJANG, "V2", 1)
        val api = ApiPalsu().apply {
            onUbahKeranjang = { if (it.varianId == "V1") throw ApiException(409, null) else KeranjangServer(emptyList(), 0) }
        }
        assertEquals(StatusSinkron.IDLE, worker(api, antrean).now())
        assertTrue(antrean.semua().isEmpty())
    }

    @Test
    fun rekonsiliasi_qtyMax_dan_hargaServerMenang_setelahSinkron() = runTest {
        val store = KeranjangMemori().also { it.simpan(lokal("V1", 2)) }
        val antrean = AntreanMemori { T0 }.also { it.tambah(JenisAksi.UBAH_KERANJANG, "V1", 2) }
        val api = ApiPalsu().apply { onUbahKeranjang = { KeranjangServer(listOf(ItemKeranjangServer("V1", 5, 21_000)), 105_000) } }
        worker(api, antrean, store).now()
        val i = store.item.value.single()
        assertEquals(5, i.qty)
        assertEquals(21_000, i.hargaIdr)
    }

    @Test
    fun antrean_tidakPunyaJenisBayar() {
        assertEquals(listOf("UBAH_KERANJANG"), JenisAksi.entries.map { it.name }, "bayar tidak pernah masuk antrean offline")
    }

    @Test
    fun deltaKatalog_hargaBerubah_membuangCacheLama_dan_menyimpanBerandaBaru() = runTest {
        val cache = CacheKatalogMemori({ T0 })
        cache.simpan("kunci-lain", "{}")
        val api = ApiPalsu().apply { onDaftarProduk = { HalamanProduk(listOf(produkRingkas("A", 20_000)), null, hargaBerubah = true) } }
        assertEquals(StatusSinkron.IDLE, worker(api, AntreanMemori { T0 }, cache = cache).now())
        assertNull(cache.basi("kunci-lain"))
        val beranda = JsonPasaree.decodeFromString<HalamanProduk>(assertNotNull(cache.basi(KUNCI_BERANDA)))
        assertEquals(20_000, beranda.data.single().hargaMulaiIdr)
    }

    @Test
    fun deltaKatalog_menggabungDenganCacheLama_serverMenang() = runTest {
        val cache = CacheKatalogMemori({ T0 })
        cache.simpan(KUNCI_BERANDA, JsonPasaree.encodeToString(HalamanProduk.serializer(), HalamanProduk(listOf(produkRingkas("A", 18_000), produkRingkas("B")), "C1")))
        val api = ApiPalsu().apply { onDaftarProduk = { HalamanProduk(listOf(produkRingkas("A", 19_000), produkRingkas("N"))) } }
        worker(api, AntreanMemori { T0 }, cache = cache).now()
        val h = JsonPasaree.decodeFromString<HalamanProduk>(cache.basi(KUNCI_BERANDA)!!)
        assertEquals(listOf("A", "N", "B"), h.data.map { it.id })
        assertEquals(19_000, h.data.first().hargaMulaiIdr)
        assertEquals("C1", h.cursorLanjut)
    }

    @Test
    fun deltaKatalog_mengirimDiperbaruiSejak_setelahSinkronPertama() = runTest {
        val sejak = mutableListOf<String?>()
        val api = ApiPalsu().apply { onDaftarProduk = { sejak += it; HalamanProduk(emptyList()) } }
        val w = worker(api, AntreanMemori { T0 })
        w.now(); w.now()
        assertEquals(listOf(null, T0.toString()), sejak)
    }
}

class SinkronAkunTest {
    @Test
    fun getKeranjang_menggabungKeranjangAkunDariPerangkatLain() = runTest {
        val store = KeranjangMemori().also { it.simpan(lokal("V1", 1)) }
        val api = ApiPalsu().apply { onAmbilKeranjang = { KeranjangServer(listOf(ItemKeranjangServer("V1", 3, 19_000), ItemKeranjangServer("V7", 2, 5_000)), 0) } }
        SyncWorker(api, store, AntreanMemori { T0 }, CacheKatalogMemori({ T0 }), MetaSinkronMemori(), { T0 }).now()
        assertEquals(setOf("V1", "V7"), store.item.value.map { it.varianId }.toSet())
        assertEquals(3, store.item.value.first { it.varianId == "V1" }.qty)
        assertEquals(19_000, store.item.value.first { it.varianId == "V1" }.hargaIdr)
    }

    @Test
    fun getKeranjang_401_dilewati_bukanGalat() = runTest {
        val api = ApiPalsu().apply { onAmbilKeranjang = { throw ApiException(401, null) } }
        val status = SyncWorker(api, KeranjangMemori(), AntreanMemori { T0 }, CacheKatalogMemori({ T0 }), MetaSinkronMemori(), { T0 }).now()
        assertEquals(StatusSinkron.IDLE, status)
    }

    @Test
    fun checkout_menolakPratinjau_bilaKeranjangBelumTersinkron() = runTest {
        val antrean = AntreanMemori { T0 }.also { it.tambah(JenisAksi.UBAH_KERANJANG, "V1", 2) }
        val api = ApiPalsu().apply {
            onUbahKeranjang = { throw RuntimeException("luring") }
            onPratinjau = { PratinjauCheckout(1, 1, 2_000, 2_002, "2099-01-01T00:00:00Z") }
        }
        val w = SyncWorker(api, KeranjangMemori(), antrean, CacheKatalogMemori({ T0 }), MetaSinkronMemori(), { T0 })
        val c = CheckoutController(api, "TRP-1", "T", sekarang = { T0 }, praSinkron = { w.pastikanKeranjangTersinkron() })
        c.muatPratinjau()
        assertTrue(c.state.value is StateCheckout.Gagal)
        assertFalse(api.panggilan.contains("pratinjau"), "pratinjau tidak dipanggil sebelum keranjang tersinkron")
    }

    @Test
    fun checkout_lanjut_bilaAntreanKosongSetelahKuras() = runTest {
        val antrean = AntreanMemori { T0 }.also { it.tambah(JenisAksi.UBAH_KERANJANG, "V1", 2) }
        val api = ApiPalsu().apply { onPratinjau = { PratinjauCheckout(1, 1, 2_000, 2_002, "2099-01-01T00:00:00Z") } }
        val w = SyncWorker(api, KeranjangMemori(), antrean, CacheKatalogMemori({ T0 }), MetaSinkronMemori(), { T0 })
        val c = CheckoutController(api, "TRP-1", "T", sekarang = { T0 }, praSinkron = { w.pastikanKeranjangTersinkron() })
        c.muatPratinjau()
        assertTrue(c.state.value is StateCheckout.Siap)
        assertEquals(listOf("ubahKeranjang:V1:2", "pratinjau"), api.panggilan)
    }
}

class OfflineKeranjangTest {
    @Test
    fun gagalJaringan_denganAntrean_diterimaLokal_dan_diantre_dan_banner() = runTest {
        val antrean = AntreanMemori { T0 }
        var luring = 0
        val api = ApiPalsu().apply { onUbahKeranjang = { throw RuntimeException("luring") } }
        val repo = KeranjangRepository(KeranjangMemori(), api, antrean, onLuring = { luring++ })
        val p = com.pasaree.shared.model.ProdukTayang(
            id = "P1", judul = "J", kategoriKode = "bumbu-dapur", deskripsi = "d", lapak = LapakRingkas("a", "A"),
            varian = listOf(com.pasaree.shared.model.VarianProduk("V1", "P1", "v", "LPK-0001-0001", 18_000, 5, "g", 100)),
            ratingRata = 4.0, totalUlasan = 0, stokPerVarian = mapOf("V1" to 5), tripTersedia = emptyList(),
        )
        assertTrue(repo.tambah(p, "V1").isSuccess, "tulis lokal dulu: tidak gagal walau luring")
        assertEquals(1, repo.item.value.size)
        assertEquals(listOf(1), antrean.semua().map { it.qty })
        assertEquals(1, luring)
    }

    @Test
    fun totalServerMenangAtasPerkiraanKlien() = runTest {
        // Perkiraan klien dari item lokal (2 x 18.000 = 36.000) berbeda dari pratinjau server (voucher/harga baru):
        // checkout menampilkan angka server dan tidak pernah memakai hitungan klien.
        val itemLokal = listOf(lokal("V1", 2))
        val perkiraanKlien = itemLokal.sumOf { it.hargaIdr * it.qty }
        val api = ApiPalsu().apply { onPratinjau = { PratinjauCheckout(30_000, 8_000, 2_000, 40_000, "2099-01-01T00:00:00Z") } }
        val c = CheckoutController(api, "TRP-1", "T", sekarang = { T0 })
        c.muatPratinjau()
        val siap = c.state.value as StateCheckout.Siap
        assertEquals(36_000, perkiraanKlien)
        assertEquals(40_000, siap.pratinjau.totalIdr)
        assertFalse(siap.pratinjau.totalIdr == perkiraanKlien + 8_000 + 2_000)
    }
}

class CacheKatalogTest {
    private val halaman = HalamanProduk(listOf(produkRingkas("A")), "C1")

    @Test
    fun ttl5Menit_segarLaluBasi() = runTest {
        var t = T0
        val cache = CacheKatalogMemori({ t })
        cache.simpan("k", "isi")
        t = T0 + 5.minutes
        assertEquals("isi", cache.segar("k"))
        t = T0 + 5.minutes + 1.seconds
        assertNull(cache.segar("k"))
        assertEquals("isi", cache.basi("k"), "tetap tersedia untuk tampilan luring")
    }

    @Test
    fun cariProduk_memakaiCacheSegar_tanpaJaringan() = runTest {
        var panggilan = 0
        val api = ApiPalsu().apply { onDaftarProduk = { panggilan++; halaman } }
        val cari = CariProduk(api, CacheKatalogMemori({ T0 }))
        cari(KriteriaKatalog(q = "x")).getOrThrow()
        val kedua = cari(KriteriaKatalog(q = "x")).getOrThrow()
        assertEquals(1, panggilan)
        assertTrue(kedua.dariCache)
    }

    @Test
    fun cariProduk_luring_menyajikanCacheBasi() = runTest {
        var t = T0
        var online = true
        val api = ApiPalsu().apply { onDaftarProduk = { if (online) halaman else throw RuntimeException("luring") } }
        val cari = CariProduk(api, CacheKatalogMemori({ t }))
        cari().getOrThrow()
        t = T0 + 10.minutes
        online = false
        val h = cari().getOrThrow()
        assertTrue(h.dariCache && h.basi)
        assertEquals("A", h.data.single().id)
    }

    @Test
    fun cariProduk_luring_tanpaCache_gagal() = runTest {
        val api = ApiPalsu().apply { onDaftarProduk = { throw RuntimeException("luring") } }
        assertTrue(CariProduk(api, CacheKatalogMemori({ T0 }))().isFailure)
    }
}
