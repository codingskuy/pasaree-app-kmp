package com.pasaree.shared.storage

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.pasaree.shared.db.PasareeDb

/** Driver SQLite Android; membuat/migrasi skema lewat `PasareeDb.Schema`. */
fun buatDriverAndroid(context: Context): SqlDriver =
    AndroidSqliteDriver(PasareeDb.Schema, context.applicationContext, "pasaree.db")
