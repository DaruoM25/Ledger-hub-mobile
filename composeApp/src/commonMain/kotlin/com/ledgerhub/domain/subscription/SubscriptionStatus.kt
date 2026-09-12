package com.ledgerhub.domain.subscription

/**
 * État immuable de l'abonnement d'un utilisateur.
 */
data class SubscriptionStatus(
    val tier: SubscriptionTier = SubscriptionTier.FREE,
    val isActive: Boolean = true,
    val expiresAt: String? = null,
    val isBypassed: Boolean = false,
) {
    val isPro: Boolean get() = (tier.isPro || isBypassed) && isActive

    companion object {
        val Free = SubscriptionStatus(tier = SubscriptionTier.FREE, isActive = true)
        val ProMonthly = SubscriptionStatus(tier = SubscriptionTier.PRO_MONTHLY, isActive = true)
        val ProAnnual = SubscriptionStatus(tier = SubscriptionTier.PRO_ANNUAL, isActive = true)
        val DevpostBypass = SubscriptionStatus(tier = SubscriptionTier.PRO_ANNUAL, isActive = true, isBypassed = true)
    }
}
