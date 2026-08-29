package com.ledgerhub.data.creditnote

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.ledgerhub.data.invoice.SqlDelightInvoiceRepository
import com.ledgerhub.db.LedgerHubDatabase
import com.ledgerhub.domain.creditnote.CreateCreditNoteUseCase
import com.ledgerhub.domain.creditnote.InvoiceAlreadyCreditedException
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Cycle d'annulation US-05 sur base réelle (SQLDelight / `JdbcSqliteDriver` en mémoire) :
 * référence croisée complète, recopie des lignes, numérotation séquentielle et refus du
 * second avoir.
 */
class CreditNoteLifecycleRepositoryTest {

    private val issuer = Party("Cabinet LedgerHub", "820329331", "82032933100027")
    private val recipient = Party("Boulangerie Moreau SARL", "784102336", "78410233600021")

    private fun newDatabase(): LedgerHubDatabase {
        Class.forName("org.sqlite.JDBC")
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        LedgerHubDatabase.Schema.create(driver)
        return LedgerHubDatabase(driver)
    }

    private fun invoice(number: String = "FAC-2026-0100") = Invoice(
        number = number,
        issueDate = "2026-06-24",
        issuer = issuer,
        recipient = recipient,
        lines = listOf(
            InvoiceLine("Conseil", quantity = 2, unitPriceHt = Money(50_000), vatRate = VatRate.TAUX_NORMAL),
            InvoiceLine("Livre", quantity = 1, unitPriceHt = Money(2_000), vatRate = VatRate.TAUX_REDUIT),
        ),
        status = InvoiceStatus.DEPOSITED,
    )

    // ── Référence croisée & recopie des lignes ───────────────────────────────────────────────

    @Test
    fun submittedCreditNote_carriesTheFullCrossReference() = runTest {
        val database = newDatabase()
        val invoices = SqlDelightInvoiceRepository(database, userEmail = "qa@ledgerhub.app")
        val creditNotes = SqlDelightCreditNoteRepository(database, userEmail = "qa@ledgerhub.app")
        val source = invoice()
        invoices.submitInvoice(source).getOrThrow()

        val number = creditNotes.nextNumberForYear(2026).getOrThrow()
        val creditNote = CreateCreditNoteUseCase()(source, number, "2026-07-01", "Erreur tarifaire").getOrThrow()
        creditNotes.submitCreditNote(creditNote).getOrThrow()

        val reloaded = creditNotes.fetchCreditNotes().getOrThrow().single()
        assertEquals("FAC-2026-0100", reloaded.invoiceId)
        // Factur-X attend le couple numéro + date, pas le seul numéro.
        assertEquals("2026-06-24", reloaded.originalInvoiceDate)
    }

    @Test
    fun submittedCreditNote_copiesEveryLineOfTheCancelledInvoice() = runTest {
        val database = newDatabase()
        val invoices = SqlDelightInvoiceRepository(database, userEmail = "qa@ledgerhub.app")
        val creditNotes = SqlDelightCreditNoteRepository(database, userEmail = "qa@ledgerhub.app")
        val source = invoice()
        invoices.submitInvoice(source).getOrThrow()
        val number = creditNotes.nextNumberForYear(2026).getOrThrow()
        creditNotes.submitCreditNote(
            CreateCreditNoteUseCase()(source, number, "2026-07-01", "Erreur tarifaire").getOrThrow(),
        ).getOrThrow()

        val reloaded = creditNotes.fetchCreditNotes().getOrThrow().single()
        assertEquals(2, reloaded.lines.size)
        assertEquals(source.lines.map { it.label }, reloaded.lines.map { it.label })
        assertEquals(source.lines.map { it.quantity }, reloaded.lines.map { it.quantity })
        assertEquals(source.lines.map { it.unitPriceHt.cents }, reloaded.lines.map { it.unitPriceHt.cents })
        assertEquals(source.lines.map { it.vatRate }, reloaded.lines.map { it.vatRate })
    }

    @Test
    fun reloadedCreditNote_exposesNegatedVatBasesPerRate() = runTest {
        val database = newDatabase()
        val invoices = SqlDelightInvoiceRepository(database, userEmail = "qa@ledgerhub.app")
        val creditNotes = SqlDelightCreditNoteRepository(database, userEmail = "qa@ledgerhub.app")
        val source = invoice()
        invoices.submitInvoice(source).getOrThrow()
        val number = creditNotes.nextNumberForYear(2026).getOrThrow()
        creditNotes.submitCreditNote(
            CreateCreditNoteUseCase()(source, number, "2026-07-01", "Erreur tarifaire").getOrThrow(),
        ).getOrThrow()

        val reloaded = creditNotes.fetchCreditNotes().getOrThrow().single()
        // 100 000 HT à 20 % et 2 000 HT à 5,5 %, portés au crédit.
        val normal = reloaded.vatBreakdown.single { it.rate == VatRate.TAUX_NORMAL }
        val reduced = reloaded.vatBreakdown.single { it.rate == VatRate.TAUX_REDUIT }
        assertEquals(-100_000, normal.baseHt.cents)
        assertEquals(-20_000, normal.vatAmount.cents)
        assertEquals(-2_000, reduced.baseHt.cents)
        assertEquals(-110, reduced.vatAmount.cents)
        assertTrue(reloaded.totalTtc.cents < 0)
    }

    // ── Numérotation séquentielle ────────────────────────────────────────────────────────────

    @Test
    fun nextNumberForYear_startsAtOne_thenIncrements() = runTest {
        val database = newDatabase()
        val invoices = SqlDelightInvoiceRepository(database, userEmail = "qa@ledgerhub.app")
        val creditNotes = SqlDelightCreditNoteRepository(database, userEmail = "qa@ledgerhub.app")

        assertEquals("AV-2026-0001", creditNotes.nextNumberForYear(2026).getOrThrow())

        val first = invoice("FAC-2026-0100")
        invoices.submitInvoice(first).getOrThrow()
        creditNotes.submitCreditNote(
            CreateCreditNoteUseCase()(first, "AV-2026-0001", "2026-07-01", "Motif").getOrThrow(),
        ).getOrThrow()

        assertEquals("AV-2026-0002", creditNotes.nextNumberForYear(2026).getOrThrow())
        // Chaque exercice repart de 1 : le motif LIKE isole l'année.
        assertEquals("AV-2027-0001", creditNotes.nextNumberForYear(2027).getOrThrow())
    }

    // ── Refus du second avoir ────────────────────────────────────────────────────────────────

    @Test
    fun secondCreditNoteOnTheSameInvoice_isRefused_andNamesTheExistingOne() = runTest {
        val database = newDatabase()
        val invoices = SqlDelightInvoiceRepository(database, userEmail = "qa@ledgerhub.app")
        val creditNotes = SqlDelightCreditNoteRepository(database, userEmail = "qa@ledgerhub.app")
        val source = invoice()
        invoices.submitInvoice(source).getOrThrow()
        creditNotes.submitCreditNote(
            CreateCreditNoteUseCase()(source, "AV-2026-0001", "2026-07-01", "Premier motif").getOrThrow(),
        ).getOrThrow()

        val result = creditNotes.submitCreditNote(
            CreateCreditNoteUseCase()(source, "AV-2026-0002", "2026-07-02", "Second motif").getOrThrow(),
        )

        val failure = assertIs<InvoiceAlreadyCreditedException>(result.exceptionOrNull())
        assertEquals("AV-2026-0001", failure.creditNoteNumber)
        assertTrue(
            failure.message!!.contains("AV-2026-0001"),
            "Le message doit nommer l'avoir existant : ${failure.message}",
        )
        // Aucun second avoir n'a été écrit.
        assertEquals(1, creditNotes.fetchCreditNotes().getOrThrow().size)
    }

    @Test
    fun findByInvoiceNumber_returnsTheCreditNote_orNull() = runTest {
        val database = newDatabase()
        val invoices = SqlDelightInvoiceRepository(database, userEmail = "qa@ledgerhub.app")
        val creditNotes = SqlDelightCreditNoteRepository(database, userEmail = "qa@ledgerhub.app")
        val source = invoice()
        invoices.submitInvoice(source).getOrThrow()

        assertNull(creditNotes.findByInvoiceNumber("FAC-2026-0100").getOrThrow())

        creditNotes.submitCreditNote(
            CreateCreditNoteUseCase()(source, "AV-2026-0001", "2026-07-01", "Motif").getOrThrow(),
        ).getOrThrow()

        val found = assertNotNull(creditNotes.findByInvoiceNumber("FAC-2026-0100").getOrThrow())
        assertEquals("AV-2026-0001", found.number)
    }

    // ── Inaltérabilité de la facture d'origine ───────────────────────────────────────────────

    @Test
    fun issuingACreditNote_freezesTheOriginalInvoice() = runTest {
        val database = newDatabase()
        val invoices = SqlDelightInvoiceRepository(database, userEmail = "qa@ledgerhub.app")
        val creditNotes = SqlDelightCreditNoteRepository(database, userEmail = "qa@ledgerhub.app")
        val source = invoice()
        invoices.submitInvoice(source).getOrThrow()

        creditNotes.submitCreditNote(
            CreateCreditNoteUseCase()(source, "AV-2026-0001", "2026-07-01", "Motif").getOrThrow(),
        ).getOrThrow()

        val reloaded = invoices.fetchInvoices().getOrThrow().single()
        assertEquals(InvoiceStatus.CANCELLED, reloaded.status)
        assertTrue(!reloaded.isEditable, "Une facture annulée reste figée en lecture seule")
        assertTrue(!reloaded.isCancellableByCreditNote, "Elle ne peut plus être annulée une seconde fois")
    }
}
