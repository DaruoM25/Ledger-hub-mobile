package com.ledgerhub.domain.subscription

/**
 * Cas d'usage : détermine si l'utilisateur courant peut accéder à une fonctionnalité protégée.
 */
class CanAccessFeatureUseCase(
    private val subscriptionRepository: SubscriptionRepository,
    private val checkInvoiceQuotaUseCase: CheckInvoiceQuotaUseCase? = null,
) {
    suspend operator fun invoke(feature: PremiumFeature): Boolean {
        val subscription = subscriptionRepository.getSubscription()
        if (subscription.isPro) return true

        return when (feature) {
            PremiumFeature.UNLIMITED_INVOICES -> {
                val quota = checkInvoiceQuotaUseCase?.invoke()
                quota?.isQuotaReached == false
            }
            PremiumFeature.FEC_EXPORT -> false
            PremiumFeature.B2B_PENALTIES -> false
            PremiumFeature.SMART_RECONCILIATION -> false
        }
    }
}
