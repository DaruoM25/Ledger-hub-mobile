package com.ledgerhub.data.auth

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.ledgerhub.db.LedgerHubDatabase
import com.ledgerhub.domain.auth.UserAccount
import com.ledgerhub.domain.time.FixedClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * N2 — Test d'intégration SQLite local pour la déconnexion (RC1 Security Hardening).
 *
 * Valide l'atomicité de la purge des identifiants locaux et l'émission immédiate de null
 * sur le flux réactif [SqlDelightAuthRepository.observeCurrentAccount].
 */
class SqlDelightAuthRepositoryLogoutTest {

    private val clock = FixedClock("2026-09-08T10:00:00Z")

    private fun newDatabase(): LedgerHubDatabase {
        Class.forName("org.sqlite.JDBC")
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        LedgerHubDatabase.Schema.create(driver)
        return LedgerHubDatabase(driver)
    }

    private fun testAccount(email: String = "expert@cabinet.fr") = UserAccount(
        email = email,
        companyName = "Cabinet Expertise SAS",
        siret = "90123456700013",
    )

    @Test
    fun logout_atomicallyPurgesUserAccount_andEmitsNullOnCurrentAccountFlow() = runTest {
        val database = newDatabase()
        val repository = SqlDelightAuthRepository(database, clock)

        // 1. Insertion / Inscription d'un compte local
        val account = testAccount()
        val registerResult = repository.register(account, "SecurePass2026!")
        assertTrue(registerResult.isSuccess)

        // Vérification de la présence du compte actif
        val currentBefore = repository.getCurrentAccount()
        assertNotNull(currentBefore)
        assertEquals(account.email, currentBefore.email)
        assertEquals(account.email, repository.observeCurrentAccount().first()?.email)

        // 2. Déclenchement de la déconnexion
        val logoutResult = repository.logout()
        assertTrue(logoutResult.isSuccess)

        // 3. Assertions strictes post-déconnexion
        assertNull(repository.getCurrentAccount(), "getCurrentAccount() doit retourner null après déconnexion")
        assertNull(repository.observeCurrentAccount().first(), "observeCurrentAccount() doit émettre null après déconnexion")

        // 4. Vérification de la purge en base SQLite
        val storedCount = database.userAccountQueries.countAccounts().executeAsOne()
        assertEquals(0L, storedCount, "La table UserAccount doit être vide après déconnexion locale")
    }
}
