package com.ledgerhub.presentation.subscription

import com.ledgerhub.domain.subscription.PremiumFeature
import com.ledgerhub.domain.subscription.SubscriptionStatus
import com.ledgerhub.domain.subscription.SubscriptionTier

/**
 * Forfait sélectionnable dans le Paywall.
 */
data class SubscriptionPlan(
    val tier: SubscriptionTier,
    val title: String,
    val priceFormatted: String,
    val periodFormatted: String,
    val badge: String? = null,
    val isRecommended: Boolean = false,
)

/**
 * État immuable de l'écran Paywall.
 */
data class PaywallUiState(
    val currentStatus: SubscriptionStatus = SubscriptionStatus.Free,
    val selectedTier: SubscriptionTier = SubscriptionTier.PRO_ANNUAL,
    val reasonFeature: PremiumFeature? = null,
    val isLoading: Boolean = false,
    val isPurchasing: Boolean = false,
    val isRestoring: Boolean = false,
    val isApplyingPromo: Boolean = false,
    val promoCode: String = "",
    val promoSuccessMessage: String? = null,
    val promoErrorMessage: String? = null,
    val errorMessage: String? = null,
    val isDismissed: Boolean = false,
) {
    val isPro: Boolean get() = currentStatus.isPro

    val plans: List<SubscriptionPlan> = listOf(
        SubscriptionPlan(
            tier = SubscriptionTier.PRO_MONTHLY,
            title = "Mensuel",
            priceFormatted = "9,99 €",
            periodFormatted = "/ mois",
            badge = null,
            isRecommended = false,
        ),
        SubscriptionPlan(
            tier = SubscriptionTier.PRO_ANNUAL,
            title = "Annuel",
            priceFormatted = "99,99 €",
            periodFormatted = "/ an",
            badge = "2 mois offerts (-20%)",
            isRecommended = true,
        ),
    )

    val features: List<PremiumFeature> = PremiumFeature.entries
}
