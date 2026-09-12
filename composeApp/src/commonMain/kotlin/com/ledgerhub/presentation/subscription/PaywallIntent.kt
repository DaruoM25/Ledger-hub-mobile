package com.ledgerhub.presentation.subscription

import com.ledgerhub.domain.subscription.SubscriptionTier

/**
 * Intentions utilisateur sur l'écran Paywall (Pattern UDF).
 */
sealed interface PaywallIntent {
    data class SelectTier(val tier: SubscriptionTier) : PaywallIntent
    data object PurchaseSelected : PaywallIntent
    data object RestorePurchases : PaywallIntent
    data class PromoCodeChanged(val code: String) : PaywallIntent
    data object ApplyPromoCode : PaywallIntent
    data object Dismiss : PaywallIntent
    data object ClearError : PaywallIntent
}
