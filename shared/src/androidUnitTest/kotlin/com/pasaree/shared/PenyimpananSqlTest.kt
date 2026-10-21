package com.pasaree.shared

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.pasaree.shared.db.PasareeDb
import com.pasaree.shared.domain.ItemKeranjang
import com.pasaree.shared.domain.KunciIdempotensi
import com.pasaree.shared.storage.AntreanSql
import com.pasaree.shared.storage.CacheKatalog
import com.pasaree.shared.storage.CacheKatalogSql
import com.pasaree.shared.storage.KeranjangSql
import com.pasaree.shared.storage.KunciIdempotensiSql
import com.pasaree.shared.storage.MetaSinkronSql
import com.pasaree.shared.storage.buatDatabase
import com.pasaree.shared.sync.JenisAksi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val T0 = Instant.parse("2026-10-04T10:00:00Z")

private fun item(varian: String, qty: Int) = ItemKeranjang(varian, "P1", "mbok-d", "Mbok D", "Sambal", "150 g", 18_000, qty, 10)

/** Uji SQLDelight di host JVM memakai driver SQLite JDBC (database berkas sementara agar bisa "dibuka ulang"). */
class PenyimpananSqlTest {
    private fun driverBaru(): Pair<SqlDriver, java.io.File> {
        val berkas = java.io.File.createTempFile("pasaree-uji", ".db").also { it.deleteOnExit() }
        val driver = JdbcSqliteDriver("jdbc:sqlite:${berkas.absolutePath}")
        PasareeDb.Schema.create(driver)
        return driver to berkas
    }

    private fun bukaUlang(berkas: java.io.File) = buatDatabase(JdbcSqliteDriver("jdbc:sqlite:${berkas.absolutePath}"))

    @Test
    fun keranjang_bertahanSetelahDibukaUlang_dan_urutanTetap() = runTest {
        val (driver, berkas) = driverBaru()
        val repo = KeranjangSql(buatDatabase(driver))
        repo.simpan(item("V1", 1))
        repo.simpan(item("V2", 2))
        repo.simpan(item("V1", 3)) // ubah qty tidak memindahkan urutan
        driver.close()

        val setelah = KeranjangSql(bukaUlang(berkas))
        assertEquals(listOf("V1" to 3, "V2" to 2), setelah.item.value.map { it.varianId to it.qty })
        setelah.hapus("V1")
        assertEquals(listOf("V2"), KeranjangSql(bukaUlang(berkas)).item.value.map { it.varianId })
    }

    @Test
    fun antrean_fifo_satuEntriPerVarian_bertahan() = runTest {
        val (driver, berkas) = driverBaru()
        val antrean = AntreanSql(buatDatabase(driver)) { T0 }
        antrean.tambah(JenisAksi.UBAH_KERANJANG, "V1", 1)
        antrean.tambah(JenisAksi.UBAH_KERANJANG, "V2", 2)
        antrean.tambah(JenisAksi.UBAH_KERANJANG, "V1", 5)
        driver.close()

        val baru = AntreanSql(bukaUlang(berkas)) { T0 }
        assertEquals(listOf("V2" to 2, "V1" to 5), baru.semua().map { it.varianId to it.qty })
        baru.naikkanPercobaan(baru.semua().first().id)
        assertEquals(1, baru.semua().first().percobaan)
        baru.hapus(baru.semua().first().id)
        assertEquals(listOf("V1"), baru.semua().map { it.varianId })
    }

    @Test
    fun cacheKatalog_ttl5Menit() = runTest {
        val (driver, _) = driverBaru()
        var t = T0
        val cache = CacheKatalogSql(buatDatabase(driver)) { t }
        cache.simpan("beranda", "{\"data\":[]}")
        t = T0 + 5.minutes
        assertEquals("{\"data\":[]}", cache.segar("beranda"))
        t = T0 + 5.minutes + 1.seconds
        assertNull(cache.segar("beranda"))
        assertEquals("{\"data\":[]}", cache.basi("beranda"))
        cache.hapusSemua()
        assertNull(cache.basi("beranda"))
    }

    @Test
    fun kunciIdempotensi_bertahanLintasProses_24Jam() = runTest {
        val (driver, berkas) = driverBaru()
        var sekarang = T0
        val k1 = KunciIdempotensi(KunciIdempotensiSql(buatDatabase(driver)), { sekarang }, { "uuid-a" })
        val a = k1.ambilAtauBuat("PSR-1", "QRIS")
        driver.close()

        sekarang = T0 + 23.hours
        val k2 = KunciIdempotensi(KunciIdempotensiSql(bukaUlang(berkas)), { sekarang }, { "uuid-b" })
        assertEquals(a, k2.ambilAtauBuat("PSR-1", "QRIS"), "retry setelah proses dimatikan memakai kunci yang sama")
        sekarang = T0 + 25.hours
        assertNotEquals(a, k2.ambilAtauBuat("PSR-1", "QRIS"))
    }

    @Test
    fun metaSinkron_simpanDanAmbil() = runTest {
        val (driver, _) = driverBaru()
        val meta = MetaSinkronSql(buatDatabase(driver))
        assertNull(meta.ambil("sinkron_terakhir"))
        meta.simpan("sinkron_terakhir", T0.toString())
        assertEquals(T0.toString(), meta.ambil("sinkron_terakhir"))
    }
}
