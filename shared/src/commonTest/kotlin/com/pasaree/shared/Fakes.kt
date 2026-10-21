package com.pasaree.shared

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

    val panggilan = mutableListOf<String>()

    override suspend fun daftarProduk(q: String?, kategori: String?, tag: String?, lapakSlug: String?, sort: String?, limit: Int, cursor: String?): HalamanProduk =
        HalamanProduk(emptyList())

    override suspend fun detailProduk(id: String): ProdukTayang = error("tidak dipakai")

    override suspend fun ubahKeranjang(ubah: KeranjangUbah): KeranjangServer {
        panggilan += "ubahKeranjang:${ubah.varianId}:${ubah.qty}"
        return onUbahKeranjang(ubah)
    }

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
