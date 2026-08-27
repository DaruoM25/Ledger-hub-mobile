package com.ledgerhub.data.creditnote

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.ledgerhub.data.invoice.SqlDelightInvoiceRepository
import com.ledgerhub.db.LedgerHubDatabase
import com.ledgerhub.domain.creditnote.CreateCreditNoteUseCase
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests QA (Skill 2) de [SqlDelightCreditNoteRepository] — pilote JdbcSqliteDriver en mémoire,
 * base neuve par test. Couvre notamment la cascade fiscale : la création d'un avoir doit faire
 * passer la facture d'origine au statut Annulée, dans la même transaction.
 */
class SqlDelightCreditNoteRepositoryTest {

    private fun newDatabase(): LedgerHubDatabase {
        // Le classloader sandboxé de Robolectric (utilisé ailleurs dans la même suite de tests,
        // voir InvoiceCancellationFlowRobolectricTest) peut faire perdre au ServiceLoader du JDBC
        // DriverManager la trace de org.sqlite.JDBC entre deux classes de test du même run —
        // chargement explicite pour rendre ce test robuste à l'ordre d'exécution.
        Class.forName("org.sqlite.JDBC")
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        LedgerHubDatabase.Schema.create(driver)
        return LedgerHubDatabase(driver)
    }

    private val issuer = Party("Vendeur SARL", "123456789", "12345678900012")
    private val recipient = Party("Client SAS", "987654321", "98765432100045")

    private fun invoice(status: InvoiceStatus = InvoiceStatus.VALIDATED) = Invoice(
        number = "F-2026-042",
        issueDate = "2026-08-01",
        issuer = issuer,
        recipient = recipient,
        lines = listOf(
            InvoiceLine("Conseil", quantity = 1, unitPriceHt = Money(10000), vatRate = VatRate.TAUX_NORMAL),
            InvoiceLine("Livre", quantity = 1, unitPriceHt = Money(2000), vatRate = VatRate.TAUX_REDUIT),
        ),
        status = status,
    )

    @Test
    fun submitCreditNote_thenFetch_roundTripsAllFields_withNegativeAmounts() = runTest {
        val database = newDatabase()
        val invoiceRepository = SqlDelightInvoiceRepository(database, userEmail = "qa@ledgerhub.app")
        val creditNoteRepository = SqlDelightCreditNoteRepository(database, userEmail = "qa@ledgerhub.app")
        val sourceInvoice = invoice()
        invoiceRepository.submitInvoice(sourceInvoice)

        val creditNote = CreateCreditNoteUseCase()(sourceInvoice, "AV-2026-001", "2026-08-06", "Erreur tarifaire").getOrThrow()
        assertTrue(creditNoteRepository.submitCreditNote(creditNote).isSuccess)

        val fetched = creditNoteRepository.fetchCreditNotes().getOrThrow()
        assertEquals(1, fetched.size)
        val roundTripped = fetched.single()
        assertEquals(creditNote.number, roundTripped.number)
        assertEquals(sourceInvoice.number, roundTripped.invoiceId)
        assertEquals("Erreur tarifaire", roundTripped.reason)
        assertEquals(sourceInvoice.recipient, roundTripped.recipient)
        assertEquals(-sourceInvoice.totalHt.cents, roundTripped.totalHt.cents)
        assertEquals(-sourceInvoice.totalVat.cents, roundTripped.totalVat.cents)
        assertEquals(-sourceInvoice.totalTtc.cents, roundTripped.totalTtc.cents)
        assertTrue(roundTripped.totalTtc.cents < 0)
    }

    @Test
    fun submitCreditNote_cascadesInvoiceStatus_toCancelled() = runTest {
        val database = newDatabase()
        val invoiceRepository = SqlDelightInvoiceRepository(database, userEmail = "qa@ledgerhub.app")
        val creditNoteRepository = SqlDelightCreditNoteRepository(database, userEmail = "qa@ledgerhub.app")
        val sourceInvoice = invoice(status = InvoiceStatus.SENT)
        invoiceRepository.submitInvoice(sourceInvoice)

        val creditNote = CreateCreditNoteUseCase()(sourceInvoice, "AV-2026-001", "2026-08-06", "Erreur tarifaire").getOrThrow()
        creditNoteRepository.submitCreditNote(creditNote)

        val updatedInvoice = invoiceRepository.fetchInvoices().getOrThrow().single { it.number == sourceInvoice.number }
        assertEquals(InvoiceStatus.CANCELLED, updatedInvoice.status)
    }

    @Test
    fun fetchCreditNotes_filtersByUserEmail_doesNotLeakOtherUsersCreditNotes() = runTest {
        val database = newDatabase()
        val invoiceRepositoryA = SqlDelightInvoiceRepository(database, userEmail = "a@ledgerhub.app")
        val creditNoteRepositoryA = SqlDelightCreditNoteRepository(database, userEmail = "a@ledgerhub.app")
        val creditNoteRepositoryB = SqlDelightCreditNoteRepository(database, userEmail = "b@ledgerhub.app")
        val sourceInvoice = invoice()
        invoiceRepositoryA.submitInvoice(sourceInvoice)
        val creditNote = CreateCreditNoteUseCase()(sourceInvoice, "AV-A-001", "2026-08-06", "Erreur").getOrThrow()
        creditNoteRepositoryA.submitCreditNote(creditNote)

        assertEquals(1, creditNoteRepositoryA.fetchCreditNotes().getOrThrow().size)
        assertEquals(0, creditNoteRepositoryB.fetchCreditNotes().getOrThrow().size)
    }
}
