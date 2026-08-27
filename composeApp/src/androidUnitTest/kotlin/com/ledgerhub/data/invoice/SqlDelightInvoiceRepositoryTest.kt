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
                status = InvoiceStatus.VALIDATED,
                lines = listOf(
                    InvoiceLine("Conseil", 1, Money(10000), VatRate.TAUX_NORMAL),
                    InvoiceLine("Formation", 1, Money(5000), VatRate.TAUX_NORMAL),
                ),
            )
        )

        val fetched = repository.fetchInvoices().getOrThrow()
        assertEquals(1, fetched.size) // même numéro -> remplacement, pas doublon
        assertEquals(InvoiceStatus.VALIDATED, fetched.single().status)
        assertEquals(2, fetched.single().lines.size)
    }

    @Test
    fun invoiceWithoutSourceQuoteId_roundTripsAsNull() = runTest {
        val repository = SqlDelightInvoiceRepository(newDatabase(), userEmail = "qa@ledgerhub.app")
        repository.submitInvoice(invoice(sourceQuoteId = null))

        assertNull(repository.fetchInvoices().getOrThrow().single().sourceQuoteId)
    }
}
