package com.pasaree.app

import com.pasaree.shared.domain.AmbilDetailProduk
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
) {
    val cariProduk = CariProduk(api)
    val ambilDetailProduk = AmbilDetailProduk(api)
    val ambilTrip = AmbilTrip(api)
    val keranjang = KeranjangRepository(keranjangStore, api)

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
