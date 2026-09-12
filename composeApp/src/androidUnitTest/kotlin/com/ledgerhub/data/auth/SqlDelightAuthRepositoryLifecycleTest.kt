package com.ledgerhub.data.auth

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.ledgerhub.db.LedgerHubDatabase
import com.ledgerhub.domain.auth.InvalidCredentialsException
import com.ledgerhub.domain.auth.UserAccount
import com.ledgerhub.domain.time.FixedClock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests d'intégration SQLite du cycle de vie utilisateur (US-26).
 *
 * Valide la réinitialisation anti-énumération et la suppression de compte RGPD Art. 17
 * avec maintien de l'intégrité décennale des pièces comptables (LPF Art. L.102 B).
 */
class SqlDelightAuthRepositoryLifecycleTest {

    private val clock = FixedClock("2026-09-07T10:00:00Z")

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
    fun requestPasswordReset_returnsSuccess_evenIfAccountDoesNotExist() = runTest {
        val database = newDatabase()
        val repository = SqlDelightAuthRepository(database, clock)

        // Compte inexistant : anti-énumération
        val result = repository.requestPasswordReset("inconnu@cabinet.fr")
        assertTrue(result.isSuccess)

        // Compte existant : succès également
        repository.register(testAccount(), "secret123").getOrThrow()
        val resultExisting = repository.requestPasswordReset("expert@cabinet.fr")
        assertTrue(resultExisting.isSuccess)
    }

    @Test
    fun deleteAccount_purgesUserAccount_andPreservesAccountingRecords() = runTest {
        val database = newDatabase()
        val repository = SqlDelightAuthRepository(database, clock)

        val account = testAccount()
        repository.register(account, "secret123").getOrThrow()

        // Insertion d'un client et d'une facture pour simuler une pièce comptable légalement conservée (LPF L.102 B)
        database.customerQueries.insertIfAbsent(
            siret = "12345678900012",
            siren = "123456789",
            name = "Client SARL",
            email = "compta@client.fr",
        )
        database.invoiceQueries.insertOrReplace(
            number = "FAC-2026-0001",
            issueDate = "2026-09-01",
            status = "ISSUED",
            sourceQuoteId = null,
            userEmail = account.email,
            issuerName = account.companyName,
            issuerSiren = account.siret.take(9),
            issuerSiret = account.siret,
            recipientSiret = "12345678900012",
            recipientName = "Client SARL",
            recipientEmail = "compta@client.fr",
            dueDate = "2026-09-30",
            facturX = 1L,
            applyB2bPenalties = 1L,
            clientSiren = "123456789",
            natureOperation = "PRESTATION_SERVICES",
            optionTvaDebit = 0L,
            isEReporting = 0L,
            deliveryStreet = "",
            deliveryZip = "",
            deliveryCity = "",
            deliveryCountry = "",
            refusalReason = null,
        )

        // Vérification présence initiale
        assertTrue(repository.login(account.email, "secret123").isSuccess)
        val invoicesBefore = database.invoiceQueries.selectAll().executeAsList()
        assertEquals(1, invoicesBefore.size)

        // Exécution de la suppression de compte (Droit à l'effacement RGPD Art. 17)
        val deleteResult = repository.deleteAccount(account.email)
        assertTrue(deleteResult.isSuccess)

        // 1. Le compte utilisateur doit être purgé (connexion impossible)
        val loginAfter = repository.login(account.email, "secret123")
        assertTrue(loginAfter.isFailure)
        assertIs<InvalidCredentialsException>(loginAfter.exceptionOrNull())

        // 2. Conformité fiscale LPF Art. L.102 B : la facture comptable reste intacte en base locale !
        val invoicesAfter = database.invoiceQueries.selectAll().executeAsList()
        assertEquals(1, invoicesAfter.size)
        assertEquals("FAC-2026-0001", invoicesAfter.first().number)

        // 3. Persistance locale de l'événement unique ACCOUNT_DELETED dans AuditLog lié à userId
        val userAuditLogs = database.auditLogQueries.selectByUserId(account.email).executeAsList()
        assertEquals(1, userAuditLogs.size)
        val entry = userAuditLogs.first()
        assertEquals("ACCOUNT_DELETED", entry.toStatus)
        assertEquals(null, entry.invoiceNumber)
        assertEquals(account.email, entry.userId)
    }
}
