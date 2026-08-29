package com.ledgerhub.data.audit

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.ledgerhub.data.invoice.SqlDelightInvoiceRepository
import com.ledgerhub.db.LedgerHubDatabase
import com.ledgerhub.domain.invoice.ChangeInvoiceStatusUseCase
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.domain.time.FixedClock
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Persistance de la Piste d'Audit Fiable sur base réelle (SQLDelight / `JdbcSqliteDriver` en
 * mémoire). L'enjeu central est l'**atomicité** : statut et trace vont ensemble, ou pas du tout.
 */
class AuditTrailPersistenceTest {

    private val clock = FixedClock("2026-08-29T10:00:00Z")

    private fun newDatabase(): LedgerHubDatabase {
        Class.forName("org.sqlite.JDBC")
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        LedgerHubDatabase.Schema.create(driver)
        return LedgerHubDatabase(driver)
    }

    private fun invoice(status: InvoiceStatus = InvoiceStatus.DRAFT) = Invoice(
        number = "FAC-2026-0001",
        issueDate = "2026-07-12",
        issuer = Party("Cabinet LedgerHub", "820329331", "82032933100027"),
        recipient = Party("Boulangerie Moreau SARL", "784102336", "78410233600021"),
        lines = listOf(InvoiceLine("Conseil", 1, Money(220_000), VatRate.TAUX_NORMAL)),
        status = status,
    )

    @Test
    fun anAuthorisedTransition_writesBothTheStatusAndTheTrace() = runTest {
        val database = newDatabase()
        val invoices = SqlDelightInvoiceRepository(database, "qa@ledgerhub.app", clock)
        val audit = SqlDelightAuditRepository(database)
        invoices.submitInvoice(invoice()).getOrThrow()

        ChangeInvoiceStatusUseCase(invoices)(invoice(), InvoiceStatus.DEPOSITED).getOrThrow()

        assertEquals(InvoiceStatus.DEPOSITED, invoices.fetchInvoices().getOrThrow().single().status)
        val entry = audit.entriesFor("FAC-2026-0001").getOrThrow().single()
        assertEquals(InvoiceStatus.DRAFT, entry.fromStatus)
        assertEquals(InvoiceStatus.DEPOSITED, entry.toStatus)
        assertEquals("2026-08-29T10:00:00Z", entry.createdAt)
        assertNull(entry.reason)
    }

    @Test
    fun aForbiddenTransition_writesNeitherStatusNorTrace() = runTest {
        val database = newDatabase()
        val invoices = SqlDelightInvoiceRepository(database, "qa@ledgerhub.app", clock)
        val audit = SqlDelightAuditRepository(database)
        invoices.submitInvoice(invoice(InvoiceStatus.PAID)).getOrThrow()

        val result = ChangeInvoiceStatusUseCase(invoices)(invoice(InvoiceStatus.PAID), InvoiceStatus.DRAFT)

        assertTrue(result.isFailure)
        assertEquals(InvoiceStatus.PAID, invoices.fetchInvoices().getOrThrow().single().status)
        assertTrue(audit.entriesFor("FAC-2026-0001").getOrThrow().isEmpty())
    }

    @Test
    fun aRejection_persistsItsReason() = runTest {
        val database = newDatabase()
        val invoices = SqlDelightInvoiceRepository(database, "qa@ledgerhub.app", clock)
        val audit = SqlDelightAuditRepository(database)
        invoices.submitInvoice(invoice(InvoiceStatus.DEPOSITED)).getOrThrow()

        ChangeInvoiceStatusUseCase(invoices)(
            invoice(InvoiceStatus.DEPOSITED), InvoiceStatus.REJECTED, "SIRET destinataire invalide",
        ).getOrThrow()

        val entry = audit.entriesFor("FAC-2026-0001").getOrThrow().single()
        assertEquals(InvoiceStatus.REJECTED, entry.toStatus)
        assertEquals("SIRET destinataire invalide", entry.reason)
    }

    @Test
    fun theTrailIsReturnedInChronologicalOrder() = runTest {
        val database = newDatabase()
        val invoices = SqlDelightInvoiceRepository(database, "qa@ledgerhub.app", clock)
        val audit = SqlDelightAuditRepository(database)
        val useCase = ChangeInvoiceStatusUseCase(invoices)
        invoices.submitInvoice(invoice()).getOrThrow()

        useCase(invoice(InvoiceStatus.DRAFT), InvoiceStatus.DEPOSITED).getOrThrow()
        clock.advanceBy(60)
        useCase(invoice(InvoiceStatus.DEPOSITED), InvoiceStatus.REJECTED, "Rejet plateforme").getOrThrow()
        clock.advanceBy(60)
        useCase(invoice(InvoiceStatus.REJECTED), InvoiceStatus.DRAFT).getOrThrow()

        val trail = audit.entriesFor("FAC-2026-0001").getOrThrow()
        assertEquals(3, trail.size)
        assertEquals(
            listOf(InvoiceStatus.DEPOSITED, InvoiceStatus.REJECTED, InvoiceStatus.DRAFT),
            trail.map { it.toStatus },
        )
        assertEquals(listOf("2026-08-29T10:00:00Z", "2026-08-29T10:01:00Z", "2026-08-29T10:02:00Z"), trail.map { it.createdAt })
        // Chaque entrée est unique : les identifiants ne doivent jamais collisionner.
        assertEquals(3, trail.map { it.id }.toSet().size)
    }

    @Test
    fun theTrailIsScopedToItsInvoice() = runTest {
        val database = newDatabase()
        val invoices = SqlDelightInvoiceRepository(database, "qa@ledgerhub.app", clock)
        val audit = SqlDelightAuditRepository(database)
        invoices.submitInvoice(invoice()).getOrThrow()
        val other = invoice().copy(number = "FAC-2026-0002")
        invoices.submitInvoice(other).getOrThrow()

        ChangeInvoiceStatusUseCase(invoices)(invoice(), InvoiceStatus.DEPOSITED).getOrThrow()

        assertEquals(1, audit.entriesFor("FAC-2026-0001").getOrThrow().size)
        assertTrue(audit.entriesFor("FAC-2026-0002").getOrThrow().isEmpty())
    }

    @Test
    fun anInvoiceNeverTransitioned_hasAnEmptyTrail() = runTest {
        val database = newDatabase()
        val invoices = SqlDelightInvoiceRepository(database, "qa@ledgerhub.app", clock)
        invoices.submitInvoice(invoice()).getOrThrow()

        assertTrue(SqlDelightAuditRepository(database).entriesFor("FAC-2026-0001").getOrThrow().isEmpty())
    }
}

/**
 * Migration `2.sqm` — la partie que `verifyMigrations` ne couvre pas : il valide le **schéma**,
 * pas la **donnée**. Or c'est le remappage des lignes qui protège les installations existantes.
 */
class DgfipStatusMigrationTest {

    /**
     * Ouvre une base réellement **en version 2**, celle d'avant l'US-07.
     *
     * `Schema.create()` produirait la version courante (3) et `Schema.migrate(0, 2)` ne crée
     * aucune table — SQLDelight n'expose pas les CREATE d'une version passée. On repart donc du
     * schéma de référence `2.db`, figé au commit précédent : c'est une vraie base SQLite, donc la
     * copie la plus fidèle possible d'une installation existante.
     */
    private fun driverAtVersion2(): JdbcSqliteDriver {
        Class.forName("org.sqlite.JDBC")
        val resource = requireNotNull(javaClass.classLoader?.getResourceAsStream("migrations/schema-v2.db")) {
            "Schéma de référence v2 introuvable dans les ressources de test"
        }
        val file = File.createTempFile("ledgerhub-v2-", ".db").apply { deleteOnExit() }
        resource.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
        return JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}")
    }

    private fun insertLegacyInvoice(driver: JdbcSqliteDriver, number: String, status: String) {
        driver.execute(
            identifier = null,
            sql = """
                INSERT INTO Invoice(number, issueDate, status, sourceQuoteId, userEmail,
                    issuerName, issuerSiren, issuerSiret, recipientSiret, recipientName,
                    recipientEmail, dueDate, facturX)
                VALUES ('$number', '2026-07-12', '$status', NULL, 'qa@ledgerhub.app',
                    'Cabinet', '820329331', '82032933100027', '78410233600021',
                    'Client', 'client@test.fr', '2026-08-12', 1)
            """.trimIndent(),
            parameters = 0,
        )
    }

    private fun queryStatuses(driver: JdbcSqliteDriver): Map<String, String> {
        val statuses = mutableMapOf<String, String>()
        driver.executeQuery(
            identifier = null,
            sql = "SELECT number, status FROM Invoice ORDER BY number",
            parameters = 0,
            mapper = { cursor ->
                while (cursor.next().value) {
                    statuses[cursor.getString(0)!!] = cursor.getString(1)!!
                }
                QueryResult.Unit
            },
        )
        return statuses
    }

    private fun queryAudit(driver: JdbcSqliteDriver): List<Triple<String, String?, String>> {
        val rows = mutableListOf<Triple<String, String?, String>>()
        driver.executeQuery(
            identifier = null,
            sql = "SELECT invoiceNumber, fromStatus, toStatus FROM AuditLog ORDER BY invoiceNumber",
            parameters = 0,
            mapper = { cursor ->
                while (cursor.next().value) {
                    rows += Triple(cursor.getString(0)!!, cursor.getString(1), cursor.getString(2)!!)
                }
                QueryResult.Unit
            },
        )
        return rows
    }

    @Test
    fun legacyValidatedAndSentRows_areRemappedToDeposited() {
        val driver = driverAtVersion2()
        insertLegacyInvoice(driver, "FAC-LEGACY-VALIDATED", "VALIDATED")
        insertLegacyInvoice(driver, "FAC-LEGACY-SENT", "SENT")
        insertLegacyInvoice(driver, "FAC-LEGACY-PAID", "PAID")
        insertLegacyInvoice(driver, "FAC-LEGACY-DRAFT", "DRAFT")

        LedgerHubDatabase.Schema.migrate(driver, 2, 3)

        val statuses = queryStatuses(driver)
        // Sans ce remappage, InvoiceStatus.valueOf() lèverait à la lecture de ces deux lignes.
        assertEquals("DEPOSITED", statuses["FAC-LEGACY-VALIDATED"])
        assertEquals("DEPOSITED", statuses["FAC-LEGACY-SENT"])
        // Les statuts communs aux deux référentiels sont laissés intacts.
        assertEquals("PAID", statuses["FAC-LEGACY-PAID"])
        assertEquals("DRAFT", statuses["FAC-LEGACY-DRAFT"])
    }

    @Test
    fun theRemapIsItselfTracedInTheAuditTrail() {
        val driver = driverAtVersion2()
        insertLegacyInvoice(driver, "FAC-LEGACY-VALIDATED", "VALIDATED")
        insertLegacyInvoice(driver, "FAC-LEGACY-SENT", "SENT")
        insertLegacyInvoice(driver, "FAC-LEGACY-DRAFT", "DRAFT")

        LedgerHubDatabase.Schema.migrate(driver, 2, 3)

        val audit = queryAudit(driver)
        // Une piste d'audit qui débuterait par un changement de statut invisible serait
        // incohérente dès sa première ligne : le remappage se trace lui-même.
        assertEquals(2, audit.size, "Seules les lignes remappées sont tracées")
        val byInvoice = audit.associateBy { it.first }
        assertEquals("VALIDATED", assertNotNull(byInvoice["FAC-LEGACY-VALIDATED"]).second)
        assertEquals("SENT", assertNotNull(byInvoice["FAC-LEGACY-SENT"]).second)
        assertTrue(audit.all { it.third == "DEPOSITED" })
    }

    @Test
    fun migrationOnADatabaseWithoutLegacyRows_writesNothing() {
        val driver = driverAtVersion2()
        insertLegacyInvoice(driver, "FAC-DRAFT", "DRAFT")

        LedgerHubDatabase.Schema.migrate(driver, 2, 3)

        assertTrue(queryAudit(driver).isEmpty())
        assertEquals("DRAFT", queryStatuses(driver)["FAC-DRAFT"])
    }

    @Test
    fun theAuditLogTableExistsAfterMigration() {
        val driver = driverAtVersion2()

        LedgerHubDatabase.Schema.migrate(driver, 2, 3)

        // La requête échouerait si la table n'avait pas été créée.
        assertTrue(queryAudit(driver).isEmpty())
    }
}
