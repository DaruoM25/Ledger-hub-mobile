package com.ledgerhub.domain.subscription

import com.ledgerhub.domain.i18n.StringKey

/**
 * Fonctionnalités protégées par le modèle Freemium de LedgerHub.
 */
enum class PremiumFeature(
    val titleKey: StringKey,
    val descriptionKey: StringKey,
    val glyph: String,
) {
    /** Création de factures au-delà de 3 factures/mois. */
    UNLIMITED_INVOICES(
        titleKey = StringKey.FEATURE_UNLIMITED_INVOICES_TITLE,
        descriptionKey = StringKey.FEATURE_UNLIMITED_INVOICES_DESC,
        glyph = "⚡",
    ),

    /** Export officiel FEC (Fichier des Écritures Comptables) conforme article A.47 A-1 du LPF. */
    FEC_EXPORT(
        titleKey = StringKey.FEATURE_FEC_EXPORT_TITLE,
        descriptionKey = StringKey.FEATURE_FEC_EXPORT_DESC,
        glyph = "📒",
    ),

    /** Mentions légales avancées et pénalités B2B (taux 3x légal + indemnité 40 €). */
    B2B_PENALTIES(
        titleKey = StringKey.FEATURE_B2B_PENALTIES_TITLE,
        descriptionKey = StringKey.FEATURE_B2B_PENALTIES_DESC,
        glyph = "⚖️",
    ),

    /** Rapprochement bancaire intelligent et exports comptables multi-formats. */
    SMART_RECONCILIATION(
        titleKey = StringKey.FEATURE_RECONCILIATION_TITLE,
        descriptionKey = StringKey.FEATURE_RECONCILIATION_DESC,
        glyph = "🔗",
    );
}
