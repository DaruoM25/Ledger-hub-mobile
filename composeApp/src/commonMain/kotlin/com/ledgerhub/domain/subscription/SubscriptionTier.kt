package com.ledgerhub.domain.subscription

/**
 * Niveaux d'abonnement disponibles dans LedgerHub Mobile.
 */
enum class SubscriptionTier {
    /** Formule Gratuite : limitée à 3 factures/mois, export Excel simple. */
    FREE,

    /** Formule Pro Mensuelle (9,99 € / mois sans engagement). */
    PRO_MONTHLY,

    /** Formule Pro Annuelle (99,99 € / an avec 2 mois offerts). */
    PRO_ANNUAL;

    val isPro: Boolean get() = this == PRO_MONTHLY || this == PRO_ANNUAL
}
