package com.pasaree.shared.storage

import com.pasaree.shared.db.PasareeDb
import com.pasaree.shared.sync.AksiAntre
import com.pasaree.shared.sync.AntreanAksi
import com.pasaree.shared.sync.JenisAksi
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/** Antrean aksi offline persisten (SQLDelight); urutan FIFO, satu entri per varian. */
class AntreanSql(private val db: PasareeDb, private val sekarang: () -> Instant = { Clock.System.now() }) : AntreanAksi {
    private val q get() = db.pasareeQueries

    override suspend fun tambah(jenis: JenisAksi, varianId: String, qty: Int) {
        db.transaction {
            q.semuaAntrean().executeAsList().filter { it.varian_id == varianId && it.jenis == jenis.name }.forEach { q.hapusAntrean(it.id) }
            q.tambahAntrean(jenis.name, varianId, qty.toLong(), sekarang().toEpochMilliseconds())
        }
    }

    override suspend fun semua(): List<AksiAntre> = q.semuaAntrean().executeAsList().map {
        AksiAntre(it.id, JenisAksi.valueOf(it.jenis), it.varian_id, it.qty.toInt(), Instant.fromEpochMilliseconds(it.dibuat_pada), it.percobaan.toInt())
    }

    override suspend fun hapus(id: Long) { q.hapusAntrean(id) }
    override suspend fun naikkanPercobaan(id: Long) { q.naikkanPercobaan(id) }
}
