package com.pasaree.shared.storage

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.pasaree.shared.db.PasareeDb

/** Driver SQLite iOS; dipanggil dari titik masuk iOS saat membuat `GrafApp`. */
fun buatDriverIos(): SqlDriver = NativeSqliteDriver(PasareeDb.Schema, "pasaree.db")
