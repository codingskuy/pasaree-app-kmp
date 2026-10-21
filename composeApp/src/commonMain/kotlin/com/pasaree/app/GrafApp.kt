package com.pasaree.app

import com.pasaree.shared.domain.AmbilDetailProduk
import com.pasaree.shared.domain.AjukanRetur
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
import app.cash.sqldelight.db.SqlDriver
import com.pasaree.shared.network.PasareeApi
import com.pasaree.shared.network.buatPasareeApi
import com.pasaree.shared.storage.CacheKatalog
import com.pasaree.shared.storage.CacheKatalogMemori
import com.pasaree.shared.storage.CacheKatalogSql
import com.pasaree.shared.storage.KeranjangSql
import com.pasaree.shared.storage.KunciIdempotensiSql
import com.pasaree.shared.storage.MetaSinkron
import com.pasaree.shared.storage.MetaSinkronMemori
import com.pasaree.shared.storage.MetaSinkronSql
import com.pasaree.shared.storage.AntreanSql
import com.pasaree.shared.storage.buatDatabase
import com.pasaree.shared.sync.AntreanAksi
import com.pasaree.shared.sync.AntreanMemori
import com.pasaree.shared.sync.SyncWorker

/** Graf dependensi aplikasi (tanpa DI framework; satu instance per proses). */
class GrafApp(
    val api: PasareeApi,
    val sesi: SesiAuth,
    keranjangStore: KeranjangStore = KeranjangMemori(),
    kunciStore: KunciIdempotensiStore = KunciIdempotensiMemori(),
    antrean: AntreanAksi = AntreanMemori(),
    cache: CacheKatalog = CacheKatalogMemori(),
    meta: MetaSinkron = MetaSinkronMemori(),
) {
    val sync = SyncWorker(api, keranjangStore, antrean, cache, meta)
    val cariProduk = CariProduk(api, cache)
    val ambilDetailProduk = AmbilDetailProduk(api)
    val ambilTrip = AmbilTrip(api)
    val keranjang = KeranjangRepository(keranjangStore, api, antrean, onLuring = sync::tandaiLuring)
    val bayar = BayarPesanan(api, KunciIdempotensi(kunciStore))
    val ambilPesanan = AmbilPesanan(api)
    val konfirmasiTerima = KonfirmasiTerima(api)
    val kirimUlasan = KirimUlasan(api)
    val ajukanRetur = AjukanRetur(api)

    companion object {
        /** Graf dengan penyimpanan SQLDelight dari [driver] platform (Android: AndroidSqliteDriver, iOS: NativeSqliteDriver). */
        fun buat(driver: SqlDriver): GrafApp {
            val db = buatDatabase(driver)
            lateinit var grafApi: PasareeApi
            val sesi = SesiAuth { grafApi }
            grafApi = buatPasareeApi(penyediaToken = sesi)
            return GrafApp(
                api = grafApi,
                sesi = sesi,
                keranjangStore = KeranjangSql(db),
                kunciStore = KunciIdempotensiSql(db),
                antrean = AntreanSql(db),
                cache = CacheKatalogSql(db),
                meta = MetaSinkronSql(db),
            )
        }
    }
}
