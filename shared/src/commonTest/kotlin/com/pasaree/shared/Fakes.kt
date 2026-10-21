package com.pasaree.shared

import com.pasaree.shared.model.BayarRequest
import com.pasaree.shared.model.BayarResponse
import com.pasaree.shared.model.HalamanPesanan
import com.pasaree.shared.model.KonfirmasiTerimaResponse
import com.pasaree.shared.model.PesananDetail
import com.pasaree.shared.model.ReturRequest
import com.pasaree.shared.model.ReturResponse
import com.pasaree.shared.model.UlasanBuat
import com.pasaree.shared.model.CheckoutRequest
import com.pasaree.shared.model.DaftarTrip
import com.pasaree.shared.model.HalamanProduk
import com.pasaree.shared.model.ItemKeranjangServer
import com.pasaree.shared.model.KeranjangServer
import com.pasaree.shared.model.KeranjangUbah
import com.pasaree.shared.model.LoginRequest
import com.pasaree.shared.model.TokenResponse
import com.pasaree.shared.model.PesananBuat
import com.pasaree.shared.model.PesananDibuat
import com.pasaree.shared.model.PratinjauCheckout
import com.pasaree.shared.model.ProdukTayang
import com.pasaree.shared.network.PasareeApi

/** API palsu untuk uji domain; setiap lambda dapat diganti per uji. */
class ApiPalsu : PasareeApi {
    var onUbahKeranjang: suspend (KeranjangUbah) -> KeranjangServer = { KeranjangServer(emptyList(), 0) }
    var onPratinjau: suspend (CheckoutRequest) -> PratinjauCheckout = { error("pratinjau tidak disiapkan") }
    var onBuatPesanan: suspend (PesananBuat) -> PesananDibuat = { error("buatPesanan tidak disiapkan") }

    var onBayar: suspend (String, BayarRequest) -> BayarResponse = { _, _ -> BayarResponse("DIBAYAR", "ESC-1") }
    var onDetailPesanan: suspend (String) -> PesananDetail = { error("detailPesanan tidak disiapkan") }
    var onUlasan: suspend (UlasanBuat) -> Unit = {}
    var onKonfirmasi: suspend (String) -> KonfirmasiTerimaResponse = { KonfirmasiTerimaResponse("SELESAI") }

    var onRetur: suspend (String, ReturRequest) -> ReturResponse = { _, _ -> ReturResponse("DIKEMBALIKAN") }

    val panggilan = mutableListOf<String>()

    override suspend fun daftarProduk(q: String?, kategori: String?, tag: String?, lapakSlug: String?, sort: String?, limit: Int, cursor: String?): HalamanProduk =
        HalamanProduk(emptyList())

    override suspend fun detailProduk(id: String): ProdukTayang = error("tidak dipakai")

    override suspend fun ubahKeranjang(ubah: KeranjangUbah): KeranjangServer {
        panggilan += "ubahKeranjang:${ubah.varianId}:${ubah.qty}"
        return onUbahKeranjang(ubah)
    }

    override suspend fun bayar(pesananId: String, req: BayarRequest): BayarResponse {
        panggilan += "bayar:$pesananId:${req.idempotencyKey}"
        return onBayar(pesananId, req)
    }

    override suspend fun detailPesanan(id: String): PesananDetail = onDetailPesanan(id)
    override suspend fun daftarPesanan(status: String?, limit: Int, cursor: String?): HalamanPesanan = HalamanPesanan(emptyList())
    override suspend fun konfirmasiTerima(id: String): KonfirmasiTerimaResponse = onKonfirmasi(id)
    override suspend fun ajukanRetur(pesananId: String, req: ReturRequest): ReturResponse {
        panggilan += "retur:$pesananId:${req.subId}"
        return onRetur(pesananId, req)
    }
    override suspend fun buatUlasan(u: UlasanBuat) = onUlasan(u)

    override suspend fun login(req: LoginRequest): TokenResponse = TokenResponse("tok", "ref", 900)

    override suspend fun daftarTrip(): DaftarTrip = DaftarTrip(emptyList())

    override suspend fun pratinjauCheckout(req: CheckoutRequest): PratinjauCheckout {
        panggilan += "pratinjau"
        return onPratinjau(req)
    }

    override suspend fun buatPesanan(req: PesananBuat): PesananDibuat {
        panggilan += "buatPesanan"
        return onBuatPesanan(req)
    }
}

fun keranjangServer(subtotal: Int, vararg item: Triple<String, Int, Int>) =
    KeranjangServer(item.map { ItemKeranjangServer(it.first, it.second, it.third) }, subtotal)
