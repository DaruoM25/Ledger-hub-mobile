package com.ledgerhub.db

import app.cash.sqldelight.db.SqlDriver

/**
 * Fabrique du pilote SQLite concret — Android (AndroidSqliteDriver) et iOS (NativeSqliteDriver)
 * ont des constructeurs différents (le premier a besoin d'un Context Android), donc chaque
 * `actual` déclare son propre constructeur ; commonMain ne construit jamais cette classe
 * lui-même, il reçoit une instance déjà construite depuis MainActivity / MainViewController.
 */
expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}
