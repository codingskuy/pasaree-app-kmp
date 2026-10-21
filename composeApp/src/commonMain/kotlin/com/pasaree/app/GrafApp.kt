package com.pasaree.app

import com.pasaree.shared.domain.AmbilDetailProduk
import com.pasaree.shared.domain.AmbilPesanan
import com.pasaree.shared.domain.BayarPesanan
import com.pasaree.shared.domain.KirimUlasan
import com.pasaree.shared.domain.KonfirmasiTerima
import com.pasaree.shared.domain.KunciIdempotensi
import com.pasaree.shared.domain.KunciIdempotensiMemori
import com.pasaree.shared.domain.KunciIdempotensiStore
import com.pasaree.shared.domain.AmbilTrip
import com.pasaree.shared.domain.CariProduk
import com.pasaree.shared.domain.KeranjangMemori
import com.pasaree.shared.domain.KeranjangRepository
import com.pasaree.shared.domain.KeranjangStore
import com.pasaree.shared.domain.SesiAuth
import com.pasaree.shared.network.PasareeApi
import com.pasaree.shared.network.buatPasareeApi

/** Graf dependensi aplikasi (tanpa DI framework; satu instance per proses). */
class GrafApp(
    val api: PasareeApi,
    val sesi: SesiAuth,
    keranjangStore: KeranjangStore = KeranjangMemori(),
    kunciStore: KunciIdempotensiStore = KunciIdempotensiMemori(),
) {
    val cariProduk = CariProduk(api)
    val ambilDetailProduk = AmbilDetailProduk(api)
    val ambilTrip = AmbilTrip(api)
    val keranjang = KeranjangRepository(keranjangStore, api)
    val bayar = BayarPesanan(api, KunciIdempotensi(kunciStore))
    val ambilPesanan = AmbilPesanan(api)
    val konfirmasiTerima = KonfirmasiTerima(api)
    val kirimUlasan = KirimUlasan(api)

    companion object {
        /** Instance proses; bertahan saat Activity dibuat ulang (rotasi). */
        val bawaan: GrafApp by lazy {
            lateinit var grafApi: PasareeApi
            val sesi = SesiAuth { grafApi }
            grafApi = buatPasareeApi(penyediaToken = sesi)
            GrafApp(grafApi, sesi)
        }
    }
}
