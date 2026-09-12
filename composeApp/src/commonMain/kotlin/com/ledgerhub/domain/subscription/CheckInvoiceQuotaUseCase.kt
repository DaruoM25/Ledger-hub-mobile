package com.ledgerhub.domain.subscription

import com.ledgerhub.domain.invoice.InvoiceRepository
import com.ledgerhub.domain.time.Clock
import com.ledgerhub.domain.time.SystemClock

/**
 * Résultat du contrôle de quota mensuel d'émission de factures.
 */
data class QuotaCheckResult(
    val isPro: Boolean,
    val invoicesCreatedThisMonth: Int,
    val maxFreeMonthlyInvoices: Int = CheckInvoiceQuotaUseCase.MAX_FREE_MONTHLY_INVOICES,
    val isQuotaReached: Boolean,
    val remainingFreeInvoices: Int,
)

/**
 * Cas d'usage : vérifie si l'utilisateur peut émettre une nouvelle facture ou a atteint le quota Free.
 */
class CheckInvoiceQuotaUseCase(
    private val invoiceRepository: InvoiceRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val clock: Clock = SystemClock,
) {
    companion object {
        const val MAX_FREE_MONTHLY_INVOICES = 3
    }

    suspend operator fun invoke(): QuotaCheckResult {
        val subscription = subscriptionRepository.getSubscription()
        if (subscription.isPro) {
            return QuotaCheckResult(
                isPro = true,
                invoicesCreatedThisMonth = 0,
                isQuotaReached = false,
                remainingFreeInvoices = Int.MAX_VALUE,
            )
        }

        val allInvoices = invoiceRepository.fetchInvoices().getOrDefault(emptyList())
        val currentMonthPrefix = clock.nowIso().take(7) // "YYYY-MM"

        val countThisMonth = allInvoices.count { invoice ->
            invoice.issueDate.startsWith(currentMonthPrefix)
        }

        val remaining = (MAX_FREE_MONTHLY_INVOICES - countThisMonth).coerceAtLeast(0)
        val reached = countThisMonth >= MAX_FREE_MONTHLY_INVOICES

        return QuotaCheckResult(
            isPro = false,
            invoicesCreatedThisMonth = countThisMonth,
            isQuotaReached = reached,
            remainingFreeInvoices = remaining,
        )
    }
}
