package com.ledgerhub.domain.subscription

import com.ledgerhub.data.subscription.MockSubscriptionRepository
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceRepository
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.domain.time.Clock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CheckInvoiceQuotaUseCaseTest {

    private class FixedClock(val dateIso: String) : Clock {
        override fun nowIso(): String = dateIso
    }

    private class FakeInvoiceRepository(val invoices: List<Invoice>) : InvoiceRepository {
        override suspend fun submitInvoice(invoice: Invoice): Result<Unit> = Result.success(Unit)
        override suspend fun fetchInvoices(): Result<List<Invoice>> = Result.success(invoices)
    }

    private val fixedClock = FixedClock("2026-09-15T10:00:00Z")

    private fun sampleInvoice(number: String, date: String) = Invoice(
        number = number,
        issueDate = date,
        dueDate = date,
        issuer = Party("Cabinet", "123456782", "12345678200010", "contact@cabinet.fr"),
        recipient = Party("Client", "987654321", "98765432100015", "client@pro.fr"),
        lines = listOf(InvoiceLine("Prestation", 1, Money(10000), VatRate.TAUX_NORMAL)),
        status = InvoiceStatus.DRAFT,
    )

    @Test
    fun proUser_alwaysHasUnlimitedQuota() = runTest {
        val invoiceRepo = FakeInvoiceRepository(
            listOf(
                sampleInvoice("F-001", "2026-09-01"),
                sampleInvoice("F-002", "2026-09-02"),
                sampleInvoice("F-003", "2026-09-03"),
                sampleInvoice("F-004", "2026-09-04"),
            )
        )
        val subscriptionRepo = MockSubscriptionRepository(SubscriptionStatus.ProMonthly)
        val useCase = CheckInvoiceQuotaUseCase(invoiceRepo, subscriptionRepo, fixedClock)

        val result = useCase()
        assertTrue(result.isPro)
        assertFalse(result.isQuotaReached)
        assertEquals(Int.MAX_VALUE, result.remainingFreeInvoices)
    }

    @Test
    fun freeUser_withLessThan3InvoicesThisMonth_hasQuotaRemaining() = runTest {
        val invoiceRepo = FakeInvoiceRepository(
            listOf(
                sampleInvoice("F-001", "2026-09-01"),
                sampleInvoice("F-002", "2026-09-02"),
                sampleInvoice("F-OLD", "2026-08-15"), // Mois précédent -> non comptabilisée
            )
        )
        val subscriptionRepo = MockSubscriptionRepository(SubscriptionStatus.Free)
        val useCase = CheckInvoiceQuotaUseCase(invoiceRepo, subscriptionRepo, fixedClock)

        val result = useCase()
        assertFalse(result.isPro)
        assertEquals(2, result.invoicesCreatedThisMonth)
        assertEquals(1, result.remainingFreeInvoices)
        assertFalse(result.isQuotaReached)
    }

    @Test
    fun freeUser_with3OrMoreInvoicesThisMonth_reachesQuota() = runTest {
        val invoiceRepo = FakeInvoiceRepository(
            listOf(
                sampleInvoice("F-001", "2026-09-01"),
                sampleInvoice("F-002", "2026-09-05"),
                sampleInvoice("F-003", "2026-09-10"),
            )
        )
        val subscriptionRepo = MockSubscriptionRepository(SubscriptionStatus.Free)
        val useCase = CheckInvoiceQuotaUseCase(invoiceRepo, subscriptionRepo, fixedClock)

        val result = useCase()
        assertFalse(result.isPro)
        assertEquals(3, result.invoicesCreatedThisMonth)
        assertEquals(0, result.remainingFreeInvoices)
        assertTrue(result.isQuotaReached)
    }
}
