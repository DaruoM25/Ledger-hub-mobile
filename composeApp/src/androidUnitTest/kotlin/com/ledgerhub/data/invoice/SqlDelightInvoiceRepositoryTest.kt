package com.ledgerhub.data.invoice

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.ledgerhub.db.LedgerHubDatabase
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests QA (Skill 2) de [SqlDelightInvoiceRepository] sur un pilote JdbcSqliteDriver **en
 * mémoire** — pas de fichier disque, chaque test repart d'une base neuve : suite rapide,
 * déterministe, indépendante de l'ordre d'exécution. Réservé à androidUnitTest/JVM :
 * JdbcSqliteDriver n'existe pas pour les cibles natives (iosTest).
 */
class SqlDelightInvoiceRepositoryTest {

    private fun newDatabase(): LedgerHubDatabase {
        // Chargement explicite du driver — robustesse face au classloader sandboxé de Robolectric
        // (utilisé ailleurs dans la même suite), voir SqlDelightCreditNoteRepositoryTest.
        Class.forName("org.sqlite.JDBC")
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        LedgerHubDatabase.Schema.create(driver)
        return LedgerHubDatabase(driver)
    }

    private val issuer = Party("Vendeur SARL", "123456789", "12345678900012")
    private val recipient = Party("Client SAS", "987654321", "98765432100045")

    private fun invoice(
        number: String = "F-2026-001",
        status: InvoiceStatus = InvoiceStatus.DRAFT,
        sourceQuoteId: String? = null,
        dueDate: String = "",
        lines: List<InvoiceLine> = listOf(
            InvoiceLine("Conseil", quantity = 2, unitPriceHt = Money(5000), vatRate = VatRate.TAUX_NORMAL),
        ),
    ) = Invoice(
        number = number,
        issueDate = "2026-08-06",
        issuer = issuer,
        recipient = recipient,
        lines = lines,
        status = status,
        sourceQuoteId = sourceQuoteId,
        dueDate = dueDate,
    )

    @Test
    fun submitInvoice_thenFetch_roundTripsAllFields_includingLinesAndRecipient() = runTest {
        val repository = SqlDelightInvoiceRepository(newDatabase(), userEmail = "qa@ledgerhub.app")
        val original = invoice(
            lines = listOf(
                InvoiceLine("Conseil", 1, Money(10000), VatRate.TAUX_NORMAL),
                InvoiceLine("Livre", 1, Money(2000), VatRate.TAUX_REDUIT),
            ),
            sourceQuoteId = "DEV-2026-001",
        )

        assertTrue(repository.submitInvoice(original).isSuccess)

        val fetched = repository.fetchInvoices().getOrThrow()
        assertEquals(1, fetched.size)
        val roundTripped = fetched.single()
        assertEquals(original.number, roundTripped.number)
        assertEquals(original.issueDate, roundTripped.issueDate)
        assertEquals(original.status, roundTripped.status)
        assertEquals(original.sourceQuoteId, roundTripped.sourceQuoteId)
        assertEquals(original.issuer, roundTripped.issuer)
        assertEquals(original.recipient, roundTripped.recipient)
        assertEquals(original.lines.size, roundTripped.lines.size)
        assertEquals(original.totalTtc, roundTripped.totalTtc)
    }

    /**
     * N2 (US-13) — le nouveau statut réglementaire `APPROVED` survit à un aller-retour SQLite.
     * `status` est persisté en `TEXT` via `InvoiceStatus.name` / `.valueOf` (voir
     * [SqlDelightInvoiceRepository]) : un statut ajouté à l'enum sans migration doit rester
     * lisible tel quel, ce que ce test garantit indépendamment du reste de la suite.
     */
    @Test
    fun submitInvoice_withApprovedStatus_roundTripsWithoutMigration() = runTest {
        val repository = SqlDelightInvoiceRepository(newDatabase(), userEmail = "qa@ledgerhub.app")
        val original = invoice(number = "F-2026-APPROVED", status = InvoiceStatus.APPROVED)

        repository.submitInvoice(original).getOrThrow()
        val fetched = repository.fetchInvoices().getOrThrow().single()

        assertEquals(InvoiceStatus.APPROVED, fetched.status)
    }

    @Test
    fun fetchInvoices_onEmptyDatabase_returnsEmptyList() = runTest {
        val repository = SqlDelightInvoiceRepository(newDatabase(), userEmail = "qa@ledgerhub.app")
        assertEquals(emptyList(), repository.fetchInvoices().getOrThrow())
    }

    @Test
    fun fetchInvoices_filtersByUserEmail_doesNotLeakOtherUsersInvoices() = runTest {
        val database = newDatabase()
        val repositoryA = SqlDelightInvoiceRepository(database, userEmail = "a@ledgerhub.app")
        val repositoryB = SqlDelightInvoiceRepository(database, userEmail = "b@ledgerhub.app")

        repositoryA.submitInvoice(invoice(number = "F-A-001"))
        repositoryB.submitInvoice(invoice(number = "F-B-001"))

        val forA = repositoryA.fetchInvoices().getOrThrow()
        assertEquals(1, forA.size)
        assertEquals("F-A-001", forA.single().number)

        val forB = repositoryB.fetchInvoices().getOrThrow()
        assertEquals(1, forB.size)
        assertEquals("F-B-001", forB.single().number)
    }

    @Test
    fun resubmittingSameInvoiceNumber_replacesLines_insteadOfAccumulatingThem() = runTest {
        val repository = SqlDelightInvoiceRepository(newDatabase(), userEmail = "qa@ledgerhub.app")
        repository.submitInvoice(invoice(lines = listOf(InvoiceLine("Conseil", 1, Money(10000), VatRate.TAUX_NORMAL))))

        repository.submitInvoice(
            invoice(
                status = InvoiceStatus.DEPOSITED,
                lines = listOf(
                    InvoiceLine("Conseil", 1, Money(10000), VatRate.TAUX_NORMAL),
                    InvoiceLine("Formation", 1, Money(5000), VatRate.TAUX_NORMAL),
                ),
            )
        )

        val fetched = repository.fetchInvoices().getOrThrow()
        assertEquals(1, fetched.size) // même numéro -> remplacement, pas doublon
        assertEquals(InvoiceStatus.DEPOSITED, fetched.single().status)
        assertEquals(2, fetched.single().lines.size)
    }

    @Test
    fun invoiceWithoutSourceQuoteId_roundTripsAsNull() = runTest {
        val repository = SqlDelightInvoiceRepository(newDatabase(), userEmail = "qa@ledgerhub.app")
        repository.submitInvoice(invoice(sourceQuoteId = null))

        assertNull(repository.fetchInvoices().getOrThrow().single().sourceQuoteId)
    }

    @Test
    fun invoicePreviewRoundTrip_preservesItemLinesAmountsAndVatBreakdown() = runTest {
        val repository = SqlDelightInvoiceRepository(newDatabase(), userEmail = "qa@ledgerhub.app")
        val original = invoice(
            number = "F-2026-PREVIEW",
            status = InvoiceStatus.DEPOSITED,
            dueDate = "2026-09-15",
            lines = listOf(
                InvoiceLine("Prestation de conseil", quantity = 3, unitPriceHt = Money(125_050), vatRate = VatRate.TAUX_NORMAL),
                InvoiceLine("Documentation fiscale", quantity = 1, unitPriceHt = Money(4_500), vatRate = VatRate.TAUX_REDUIT),
            ),
        )

        repository.submitInvoice(original).getOrThrow()

        val roundTripped = repository.fetchInvoices().getOrThrow().single()
        assertEquals(original.number, roundTripped.number)
        assertEquals(original.dueDate, roundTripped.dueDate)
        assertEquals(original.status, roundTripped.status)
        assertEquals(original.lines.map { it.label }, roundTripped.lines.map { it.label })
        assertEquals(original.lines.map { it.quantity }, roundTripped.lines.map { it.quantity })
        assertEquals(original.lines.map { it.unitPriceHt.cents }, roundTripped.lines.map { it.unitPriceHt.cents })
        assertEquals(original.lines.map { it.vatRate }, roundTripped.lines.map { it.vatRate })
        assertEquals(original.totalHt, roundTripped.totalHt)
        assertEquals(original.totalVat, roundTripped.totalVat)
        assertEquals(original.totalTtc, roundTripped.totalTtc)
    }

    // ── Immutabilité du destinataire (anomalie D-03, recette du 29/08/2026) ──────────────────

    @Test
    fun emittingUnderKnownSiret_withDifferentName_leavesEarlierInvoicesUntouched() = runTest {
        val repository = SqlDelightInvoiceRepository(newDatabase(), userEmail = "qa@ledgerhub.app")
        repository.submitInvoice(invoice(number = "F-2026-001"))

        // Même SIRET, raison sociale et contact différents — cas qui réécrivait l'historique.
        repository.submitInvoice(
            invoice(number = "F-2026-002").copy(
                recipient = Party("Renommée SAS", "987654321", "98765432100045", "nouveau@renommee.fr"),
            ),
        )

        val byNumber = repository.fetchInvoices().getOrThrow().associateBy { it.number }
        assertEquals("Client SAS", byNumber.getValue("F-2026-001").recipient.name)
        assertEquals("Renommée SAS", byNumber.getValue("F-2026-002").recipient.name)
    }

    @Test
    fun recipientEmail_isFrozenOnTheInvoice_notReadBackFromTheCustomerRecord() = runTest {
        val repository = SqlDelightInvoiceRepository(newDatabase(), userEmail = "qa@ledgerhub.app")
        repository.submitInvoice(
            invoice(number = "F-2026-001").copy(
                recipient = Party("Client SAS", "987654321", "98765432100045", "contact@client.fr"),
            ),
        )
        repository.submitInvoice(
            invoice(number = "F-2026-002").copy(
                recipient = Party("Client SAS", "987654321", "98765432100045", "compta@client.fr"),
            ),
        )

        val byNumber = repository.fetchInvoices().getOrThrow().associateBy { it.number }
        assertEquals("contact@client.fr", byNumber.getValue("F-2026-001").recipient.email)
        assertEquals("compta@client.fr", byNumber.getValue("F-2026-002").recipient.email)
    }

    @Test
    fun customerRecord_isNeverOverwrittenByAnEmission() = runTest {
        val database = newDatabase()
        val repository = SqlDelightInvoiceRepository(database, userEmail = "qa@ledgerhub.app")
        repository.submitInvoice(invoice(number = "F-2026-001"))
        repository.submitInvoice(
            invoice(number = "F-2026-002").copy(
                recipient = Party("Renommée SAS", "987654321", "98765432100045", "nouveau@renommee.fr"),
            ),
        )

        val customer = database.customerQueries.selectBySiret("98765432100045").executeAsOne()
        assertEquals("Client SAS", customer.name)
    }

    @Test
    fun legacyInvoiceWithoutFrozenRecipient_fallsBackToTheCustomerRecord() = runTest {
        val database = newDatabase()
        // Écriture directe façon « facture héritée » : colonnes de gel laissées vides.
        database.customerQueries.insertIfAbsent(
            siret = "98765432100045",
            siren = "987654321",
            name = "Client Historique SARL",
            email = "legacy@client.fr",
        )
        database.invoiceQueries.insertOrReplace(
            number = "F-2025-900",
            issueDate = "2025-11-02",
            status = InvoiceStatus.PAID.name,
            sourceQuoteId = null,
            userEmail = "qa@ledgerhub.app",
            issuerName = issuer.name,
            issuerSiren = issuer.siren,
            issuerSiret = issuer.siret,
            recipientSiret = "98765432100045",
            recipientName = "",
            recipientEmail = "",
            dueDate = "2025-12-02",
            facturX = 1L,
        )
        database.invoiceLineQueries.insert(
            invoiceNumber = "F-2025-900",
            label = "Conseil",
            quantity = 1L,
            unitPriceHtCents = 10000L,
            vatRate = VatRate.TAUX_NORMAL.name,
        )

        val fetched = SqlDelightInvoiceRepository(database, userEmail = "qa@ledgerhub.app")
            .fetchInvoices().getOrThrow().single()
        assertEquals("Client Historique SARL", fetched.recipient.name)
        assertEquals("legacy@client.fr", fetched.recipient.email)
    }
}
