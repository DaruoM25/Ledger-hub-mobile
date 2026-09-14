package com.ledgerhub.domain.vat

import com.ledgerhub.domain.creditnote.CreditNote
import com.ledgerhub.domain.creditnote.CreditNoteRepository
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceRepository
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.NatureOperation
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CalculateVatMetricsUseCaseTest {

    private val fakeIssuer = Party(name = "Mon Entreprise", siren = "123456782", siret = "12345678200015")
    private val fakeClient = Party(name = "Client Test", siren = "987654321", siret = "98765432100010")

    private fun buildInvoice(
        number: String,
        status: InvoiceStatus,
        natureOperation: NatureOperation,
        optionTvaDebit: Boolean = false,
        lines: List<InvoiceLine>,
        issueDate: String = "2026-09-14",
    ): Invoice = Invoice(
        number = number,
        issueDate = issueDate,
        issuer = fakeIssuer,
        recipient = fakeClient,
        status = status,
        natureOperation = natureOperation,
        optionTvaDebit = optionTvaDebit,
        lines = lines,
    )

    private class FakeInvoiceRepository(private val invoices: List<Invoice>) : InvoiceRepository {
        override suspend fun submitInvoice(invoice: Invoice): Result<Unit> = Result.success(Unit)
        override suspend fun fetchInvoices(): Result<List<Invoice>> = Result.success(invoices)
    }

    private class FakeCreditNoteRepository(private val creditNotes: List<CreditNote>) : CreditNoteRepository {
        override suspend fun submitCreditNote(creditNote: CreditNote): Result<Unit> = Result.success(Unit)
        override suspend fun fetchCreditNotes(): Result<List<CreditNote>> = Result.success(creditNotes)
        override suspend fun findByInvoiceNumber(invoiceNumber: String): Result<CreditNote?> =
            Result.success(creditNotes.firstOrNull { it.invoiceId == invoiceNumber })
        override suspend fun nextNumberForYear(year: Int): Result<String> = Result.success("AV-$year-0001")
    }

    @Test
    fun serviceInvoice_deposited_isPendingCollection_notExigible() = runTest {
        // Prestation de services déposée mais non encore encaissée (1000€ HT @ 20% -> 200€ TVA)
        val invoice = buildInvoice(
            number = "FAC-2026-0001",
            status = InvoiceStatus.DEPOSITED,
            natureOperation = NatureOperation.PRESTATION_SERVICES,
            optionTvaDebit = false,
            lines = listOf(
                InvoiceLine(label = "Conseil", quantity = 1, unitPriceHt = Money(100_000), vatRate = VatRate.TAUX_NORMAL),
            ),
        )

        val useCase = CalculateVatMetricsUseCase(FakeInvoiceRepository(listOf(invoice)))
        val metrics = useCase().getOrThrow()

        assertEquals(Money.ZERO, metrics.totalVatCollectedExigible)
        assertEquals(Money(20_000), metrics.totalVatPendingCollection)
        assertEquals(Money.ZERO, metrics.netVatBalance)
    }

    @Test
    fun serviceInvoice_paid_becomesExigible() = runTest {
        // Prestation de services payée (1000€ HT @ 20% -> 200€ TVA)
        val invoice = buildInvoice(
            number = "FAC-2026-0002",
            status = InvoiceStatus.PAID,
            natureOperation = NatureOperation.PRESTATION_SERVICES,
            optionTvaDebit = false,
            lines = listOf(
                InvoiceLine(label = "Audit", quantity = 1, unitPriceHt = Money(100_000), vatRate = VatRate.TAUX_NORMAL),
            ),
        )

        val useCase = CalculateVatMetricsUseCase(FakeInvoiceRepository(listOf(invoice)))
        val metrics = useCase().getOrThrow()

        assertEquals(Money(20_000), metrics.totalVatCollectedExigible)
        assertEquals(Money.ZERO, metrics.totalVatPendingCollection)
        assertEquals(Money(20_000), metrics.netVatBalance)
        assertFalse(metrics.isCredit)
    }

    @Test
    fun goodsInvoice_deposited_isImmediatelyExigible() = runTest {
        // Vente de matériel déposée (natureOperation = LIVRAISON_BIENS) (500€ HT @ 20% -> 100€ TVA)
        val invoice = buildInvoice(
            number = "FAC-2026-0003",
            status = InvoiceStatus.DEPOSITED,
            natureOperation = NatureOperation.LIVRAISON_BIENS,
            optionTvaDebit = false,
            lines = listOf(
                InvoiceLine(label = "Serveur", quantity = 1, unitPriceHt = Money(50_000), vatRate = VatRate.TAUX_NORMAL),
            ),
        )

        val useCase = CalculateVatMetricsUseCase(FakeInvoiceRepository(listOf(invoice)))
        val metrics = useCase().getOrThrow()

        assertEquals(Money(10_000), metrics.totalVatCollectedExigible)
        assertEquals(Money.ZERO, metrics.totalVatPendingCollection)
        assertEquals(Money(10_000), metrics.netVatBalance)
    }

    @Test
    fun serviceInvoice_withDebitOption_isImmediatelyExigible() = runTest {
        // Prestation de services avec Option TVA sur les Débits activée (optionTvaDebit = true)
        val invoice = buildInvoice(
            number = "FAC-2026-0004",
            status = InvoiceStatus.DEPOSITED,
            natureOperation = NatureOperation.PRESTATION_SERVICES,
            optionTvaDebit = true,
            lines = listOf(
                InvoiceLine(label = "Abonnement SaaS", quantity = 1, unitPriceHt = Money(20_000), vatRate = VatRate.TAUX_NORMAL),
            ),
        )

        val useCase = CalculateVatMetricsUseCase(FakeInvoiceRepository(listOf(invoice)))
        val metrics = useCase().getOrThrow()

        assertEquals(Money(4_000), metrics.totalVatCollectedExigible)
        assertEquals(Money.ZERO, metrics.totalVatPendingCollection)
    }

    @Test
    fun multiRate_breakdown_matchesCa3OfficialLines() = runTest {
        // Facture avec taux 20%, 10%, 5.5%, 2.1%
        val invoice = buildInvoice(
            number = "FAC-2026-0005",
            status = InvoiceStatus.PAID,
            natureOperation = NatureOperation.LIVRAISON_BIENS,
            lines = listOf(
                InvoiceLine(label = "L20", quantity = 1, unitPriceHt = Money(100_000), vatRate = VatRate.TAUX_NORMAL), // 200€
                InvoiceLine(label = "L10", quantity = 1, unitPriceHt = Money(50_000), vatRate = VatRate.TAUX_INTERMEDIAIRE), // 50€
                InvoiceLine(label = "L55", quantity = 1, unitPriceHt = Money(20_000), vatRate = VatRate.TAUX_REDUIT), // 11€
                InvoiceLine(label = "L21", quantity = 1, unitPriceHt = Money(10_000), vatRate = VatRate.TAUX_PARTICULIER), // 2.10€ -> 210c
            ),
        )

        val useCase = CalculateVatMetricsUseCase(FakeInvoiceRepository(listOf(invoice)))
        val metrics = useCase().getOrThrow()

        assertEquals(4, metrics.ca3Lines.size)
        val l01 = metrics.ca3Lines.first { it.lineCode == "01" }
        assertEquals(Money(100_000), l01.baseHt)
        assertEquals(Money(20_000), l01.taxDue)

        val l02 = metrics.ca3Lines.first { it.lineCode == "02" }
        assertEquals(Money(50_000), l02.baseHt)
        assertEquals(Money(5_000), l02.taxDue)

        val l03 = metrics.ca3Lines.first { it.lineCode == "03" }
        assertEquals(Money(20_000), l03.baseHt)
        assertEquals(Money(1_100), l03.taxDue)

        val l04 = metrics.ca3Lines.first { it.lineCode == "04" }
        assertEquals(Money(10_000), l04.baseHt)
        assertEquals(Money(210), l04.taxDue)
    }

    @Test
    fun deductibleVat_higherThanCollected_yieldsVatCredit() = runTest {
        val invoice = buildInvoice(
            number = "FAC-2026-0006",
            status = InvoiceStatus.PAID,
            natureOperation = NatureOperation.LIVRAISON_BIENS,
            lines = listOf(
                InvoiceLine(label = "Produit", quantity = 1, unitPriceHt = Money(100_000), vatRate = VatRate.TAUX_NORMAL), // 200€
            ),
        )

        val useCase = CalculateVatMetricsUseCase(FakeInvoiceRepository(listOf(invoice)))
        // TVA Déductible simulée de 300€
        val metrics = useCase(simulatedDeductibleVat = Money(30_000)).getOrThrow()

        assertEquals(Money(20_000), metrics.totalVatCollectedExigible)
        assertEquals(Money(30_000), metrics.totalVatDeductible)
        assertEquals(Money(-10_000), metrics.netVatBalance)
        assertTrue(metrics.isCredit)
        assertEquals(Money(10_000), metrics.absoluteBalance)
    }
}
