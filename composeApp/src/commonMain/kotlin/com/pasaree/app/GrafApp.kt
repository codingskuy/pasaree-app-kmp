package com.pasaree.app

import com.pasaree.shared.domain.AmbilDetailProduk
import com.pasaree.shared.domain.CariProduk
import com.pasaree.shared.network.PasareeApi
import com.pasaree.shared.network.buatPasareeApi

/** Graf dependensi aplikasi (tanpa DI framework; satu instance per proses). */
class GrafApp(val api: PasareeApi) {
    val cariProduk = CariProduk(api)
    val ambilDetailProduk = AmbilDetailProduk(api)

    companion object {
        /** Instance proses; bertahan saat Activity dibuat ulang (rotasi). */
        val bawaan: GrafApp by lazy { GrafApp(buatPasareeApi()) }
    }
}
