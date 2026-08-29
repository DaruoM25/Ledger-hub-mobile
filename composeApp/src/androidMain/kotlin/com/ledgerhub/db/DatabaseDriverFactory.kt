package com.ledgerhub.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

actual class DatabaseDriverFactory(private val context: Context) {

    actual fun createDriver(): SqlDriver = AndroidSqliteDriver(
        schema = LedgerHubDatabase.Schema,
        context = context,
        name = DATABASE_NAME,
        callback = object : AndroidSqliteDriver.Callback(LedgerHubDatabase.Schema) {
            /**
             * SQLite désactive les clés étrangères par défaut, à chaque connexion. Sans ce
             * réglage, les `FOREIGN KEY` et les `ON DELETE CASCADE` déclarés dans les `.sq`
             * n'étaient que documentaires : supprimer une facture laissait ses lignes et ses
             * entrées d'audit orphelines, au lieu de les emporter.
             *
             * `onConfigure` et non `onOpen` : le réglage doit précéder toute transaction, et
             * SQLite refuse de le modifier à l'intérieur de l'une d'elles.
             */
            override fun onConfigure(db: SupportSQLiteDatabase) {
                super.onConfigure(db)
                db.setForeignKeyConstraintsEnabled(true)
            }
        },
    )

    private companion object {
        const val DATABASE_NAME = "ledgerhub.db"
    }
}
