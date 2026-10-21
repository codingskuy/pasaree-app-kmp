package com.pasaree.shared

import com.pasaree.shared.domain.CheckoutController
import com.pasaree.shared.domain.GalatDomain
import com.pasaree.shared.domain.JenisGalat
import com.pasaree.shared.domain.KeranjangMemori
import com.pasaree.shared.domain.KeranjangRepository
import com.pasaree.shared.domain.StateCheckout
import com.pasaree.shared.domain.infoKomisiLapak
import com.pasaree.shared.domain.perLapak
import com.pasaree.shared.model.GalatApi
import com.pasaree.shared.model.KodeGalat
import com.pasaree.shared.model.LapakRingkas
import com.pasaree.shared.model.PesananDibuat
import com.pasaree.shared.model.PratinjauCheckout
import com.pasaree.shared.model.ProdukTayang
import com.pasaree.shared.model.TripLumbung
import com.pasaree.shared.model.VarianProduk
import com.pasaree.shared.network.ApiException
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

private fun produk(id: String, lapak: String, vararg varian: Pair<String, Int>, harga: Int = 18_000) = ProdukTayang(
    id = id, judul = "Produk $id", kategoriKode = "bumbu-dapur", deskripsi = "d",
    lapak = LapakRingkas(lapak, "Lapak $lapak"),
    varian = varian.map { (vid, stok) -> VarianProduk(vid, id, "Var $vid", "LPK-0001-0001", harga, stok, "g", 100) },
    ratingRata = 4.5, totalUlasan = 1,
    stokPerVarian = varian.toMap(),
    tripTersedia = listOf(TripLumbung("TRP-1", "2026-10-04T10:00:00Z", ongkirIdr = 8_000)),
)

private fun pratinjau(subtotal: Int, ongkir: Int = 8_000, total: Int = subtotal + ongkir + 2_000, kunci: String = "2099-01-01T00:00:00Z") =
    PratinjauCheckout(subtotal, ongkir, 2_000, total, kunci)

private fun konflik(kode: String) = ApiException(409, GalatApi(kode, "Berubah"))

class KeranjangTest {

    @Test
    fun stokNol_tidakMasukKeranjang_dan_tidakMenyentuhJaringan() = runTest {
        val api = ApiPalsu()
        val repo = KeranjangRepository(KeranjangMemori(), api)
        val hasil = repo.tambah(produk("P1", "a", "V1" to 0), "V1")
        val e = hasil.exceptionOrNull()
        assertIs<GalatDomain>(e)
        assertEquals(KodeGalat.STOK_HABIS, e.kode)
        assertTrue(repo.item.value.isEmpty())
        assertTrue(api.panggilan.isEmpty())
    }

    @Test
    fun tambah_tulisLokalDulu_laluPutKeranjang_denganQtyTotal() = runTest {
        val api = ApiPalsu().apply { onUbahKeranjang = { keranjangServer(36_000, Triple(it.varianId, it.qty, 18_000)) } }
        val repo = KeranjangRepository(KeranjangMemori(), api)
        repo.tambah(produk("P1", "a", "V1" to 5), "V1").getOrThrow()
        repo.tambah(produk("P1", "a", "V1" to 5), "V1").getOrThrow()
        assertEquals(listOf("ubahKeranjang:V1:1", "ubahKeranjang:V1:2"), api.panggilan)
        assertEquals(2, repo.item.value.single().qty)
        assertEquals(36_000, repo.ringkasan.value.subtotalIdr, "subtotal berasal dari server")
    }

    @Test
    fun qtyDijepitKeStok() = runTest {
        val repo = KeranjangRepository(KeranjangMemori(), ApiPalsu())
        val p = produk("P1", "a", "V1" to 3)
        repo.tambah(p, "V1", qty = 10).getOrThrow()
        assertEquals(3, repo.item.value.single().qty)
        repo.ubahQty("V1", 99).getOrThrow()
        assertEquals(3, repo.item.value.single().qty)
    }

    @Test
    fun qtyNol_menghapusItem_dan_mengirimQtyNol() = runTest {
        val api = ApiPalsu()
        val repo = KeranjangRepository(KeranjangMemori(), api)
        repo.tambah(produk("P1", "a", "V1" to 5), "V1").getOrThrow()
        repo.ubahQty("V1", 0).getOrThrow()
        assertTrue(repo.item.value.isEmpty())
        assertEquals("ubahKeranjang:V1:0", api.panggilan.last())
    }

    @Test
    fun gagalJaringan_itemLokalTetapAda() = runTest {
        val api = ApiPalsu().apply { onUbahKeranjang = { throw RuntimeException("luring") } }
        val repo = KeranjangRepository(KeranjangMemori(), api)
        val hasil = repo.tambah(produk("P1", "a", "V1" to 5), "V1")
        assertEquals(JenisGalat.JARINGAN, (hasil.exceptionOrNull() as GalatDomain).jenis)
        assertEquals(1, repo.item.value.size, "tulis lokal dulu; sinkron diulang kemudian")
    }

    @Test
    fun keranjang_dikelompokkanPerLapak_menjadiSubPesanan() = runTest {
        val repo = KeranjangRepository(KeranjangMemori(), ApiPalsu())
        repo.tambah(produk("P1", "mbok-d", "V1" to 5), "V1").getOrThrow()
        repo.tambah(produk("P2", "bu-ani", "V2" to 5), "V2").getOrThrow()
        repo.tambah(produk("P3", "mbok-d", "V3" to 5), "V3").getOrThrow()
        val kelompok = repo.item.value.perLapak()
        assertEquals(listOf("mbok-d", "bu-ani"), kelompok.map { it.lapakSlug })
        assertEquals(listOf("V1", "V3"), kelompok.first().item.map { it.varianId })
    }

    @Test
    fun infoKomisi_8persen_floor_dan_hanyaInformasi() {
        val i = infoKomisiLapak(56_000)
        assertEquals(4_480, i.komisiIdr)
        assertEquals(51_520, i.diterimaLapakIdr)
        assertEquals("Lapak menerima Rp51.520 setelah komisi 8%", i.teks)
        assertEquals(1, infoKomisiLapak(19).komisiIdr, "floor(19*8/100)=1")
    }
}

class CheckoutTest {
    private val sekarang = Instant.parse("2026-10-04T10:00:00Z")

    @Test
    fun totalSelaluDariServer_bukanHitunganKlien() = runTest {
        // Server sengaja mengembalikan total berbeda dari subtotal+ongkir+layanan (mis. voucher): klien tidak boleh mengoreksi.
        val api = ApiPalsu().apply { onPratinjau = { pratinjau(subtotal = 56_000, total = 60_000) } }
        val c = CheckoutController(api, "TRP-1", "TITIK-05", sekarang = { sekarang })
        c.muatPratinjau()
        val siap = c.state.value as StateCheckout.Siap
        assertEquals(60_000, siap.pratinjau.totalIdr)
        assertEquals(2_000, siap.pratinjau.biayaLayananIdr)
    }

    @Test
    fun buatPesanan_tanpaPratinjau_ditolak_tanpaJaringan() = runTest {
        val api = ApiPalsu()
        val c = CheckoutController(api, "TRP-1", "TITIK-05", sekarang = { sekarang })
        val hasil = c.buatPesanan()
        assertTrue(hasil.isFailure)
        assertTrue(api.panggilan.isEmpty(), "pesanan tidak boleh dibuat tanpa pratinjau")
    }

    @Test
    fun urutan_pratinjau_lalu_pesanan() = runTest {
        val api = ApiPalsu().apply {
            onPratinjau = { pratinjau(56_000) }
            onBuatPesanan = { PesananDibuat("PSR-1", "2026-10-04T10:30:00Z", 66_000) }
        }
        val c = CheckoutController(api, "TRP-1", "TITIK-05", sekarang = { sekarang })
        c.muatPratinjau()
        val dibuat = c.buatPesanan(mapOf("lapak-1" to "tanpa cabai")).getOrThrow()
        assertEquals("PSR-1", dibuat.pesananId)
        assertEquals(listOf("pratinjau", "buatPesanan"), api.panggilan)
        assertIs<StateCheckout.Dibuat>(c.state.value)
    }

    @Test
    fun konflik409_stokHabis_memaksaPratinjauUlang_denganAngkaTerbaru() = runTest {
        var versi = 0
        val api = ApiPalsu().apply {
            onPratinjau = { if (versi++ == 0) pratinjau(56_000) else pratinjau(38_000) }
            onBuatPesanan = { throw konflik(KodeGalat.STOK_HABIS) }
        }
        val c = CheckoutController(api, "TRP-1", "TITIK-05", sekarang = { sekarang })
        c.muatPratinjau()
        val hasil = c.buatPesanan()
        assertTrue(hasil.isFailure)
        val siap = c.state.value as StateCheckout.Siap
        assertEquals(KodeGalat.STOK_HABIS, siap.alasanUlang)
        assertEquals(38_000, siap.pratinjau.subtotalIdr, "angka terbaru dari pratinjau ulang")
        assertEquals(listOf("pratinjau", "buatPesanan", "pratinjau"), api.panggilan)
    }

    @Test
    fun konflik409_hargaBerubah_saatPratinjau_jugaDitampilkanUlang() = runTest {
        var n = 0
        val api = ApiPalsu().apply {
            onPratinjau = { if (n++ == 0) throw konflik(KodeGalat.HARGA_BERUBAH) else pratinjau(40_000) }
        }
        val c = CheckoutController(api, "TRP-1", "TITIK-05", sekarang = { sekarang })
        c.muatPratinjau()
        val siap = c.state.value as StateCheckout.Siap
        assertEquals(KodeGalat.HARGA_BERUBAH, siap.alasanUlang)
        assertEquals(40_000, siap.pratinjau.subtotalIdr)
    }

    @Test
    fun kunciStokKedaluwarsa_memaksaPratinjauUlang_sebelumPesanan() = runTest {
        var n = 0
        val api = ApiPalsu().apply {
            onPratinjau = { if (n++ == 0) pratinjau(56_000, kunci = "2026-10-04T09:59:00Z") else pratinjau(56_000) }
        }
        val c = CheckoutController(api, "TRP-1", "TITIK-05", sekarang = { sekarang })
        c.muatPratinjau()
        assertTrue(c.buatPesanan().isFailure)
        assertEquals(listOf("pratinjau", "pratinjau"), api.panggilan, "pesanan tidak dikirim dengan kunci kedaluwarsa")
        assertIs<StateCheckout.Siap>(c.state.value)
    }

    @Test
    fun galatLain_menjadiGagal_tanpaPratinjauUlang() = runTest {
        val api = ApiPalsu().apply {
            onPratinjau = { pratinjau(56_000) }
            onBuatPesanan = { throw ApiException(500, null) }
        }
        val c = CheckoutController(api, "TRP-1", "TITIK-05", sekarang = { sekarang })
        c.muatPratinjau()
        c.buatPesanan()
        assertIs<StateCheckout.Gagal>(c.state.value)
        assertEquals(listOf("pratinjau", "buatPesanan"), api.panggilan)
    }
}
