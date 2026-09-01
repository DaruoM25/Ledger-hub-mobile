package com.ledgerhub.domain.integrations

import com.ledgerhub.domain.i18n.StringKey

/**
 * Catalogue déclaratif des modules d'intégration présentés par le Hub (US-20).
 *
 * **Enum et non liste de `data class`** : le `when` qui associe un module à son tag de test
 * (`IntegrationsHubTags.card`) devient exhaustif. Un cinquième module ne peut donc pas être ajouté
 * sans que le compilateur exige son tag — le contrat QA ne peut plus être oublié en silence.
 *
 * Chaque entrée ne porte que des **clés** de traduction, jamais de texte : le hub est bilingue au
 * même titre que le reste de l'application, et un libellé écrit en dur ici échapperait à
 * l'invariant de parité vérifié par `AppTranslationsTest`.
 *
 * [glyph] plutôt qu'une icône Material : `material-icons-extended` est absent des dépendances, et
 * tirer la bibliothèque entière pour quatre pictogrammes serait disproportionné (précédent US-19).
 *
 * [id] est la racine des tags de test. Il est **stable et indépendant du nom de l'entrée** : le
 * cahier des charges fige `integration_card_bank_sync`, que la constante s'appelle un jour
 * `BANK_SYNC` ou autrement.
 */
enum class IntegrationModule(
    val id: String,
    val titleKey: StringKey,
    val descriptionKey: StringKey,
    val glyph: String,
    val status: IntegrationStatus,
) {
    /** Paiement en ligne par carte — le module le plus avancé, ouvert en accès anticipé. */
    STRIPE_PAYMENTS(
        id = "stripe",
        titleKey = StringKey.INTEGRATION_STRIPE_TITLE,
        descriptionKey = StringKey.INTEGRATION_STRIPE_DESC,
        glyph = "💳",
        status = IntegrationStatus.BETA,
    ),

    /** Export du Fichier des Écritures Comptables, attendu par les cabinets. */
    FEC_EXPORT(
        id = "fec",
        titleKey = StringKey.INTEGRATION_FEC_TITLE,
        descriptionKey = StringKey.INTEGRATION_FEC_DESC,
        glyph = "📊",
        status = IntegrationStatus.BETA,
    ),

    /** Notifications d'équipe — annoncé, pas encore développé. */
    SLACK_NOTIFICATIONS(
        id = "slack",
        titleKey = StringKey.INTEGRATION_SLACK_TITLE,
        descriptionKey = StringKey.INTEGRATION_SLACK_DESC,
        glyph = "💬",
        status = IntegrationStatus.COMING_SOON,
    ),

    /**
     * Synchronisation bancaire par API. Le rapprochement de l'US-18 lit encore un relevé simulé
     * (`MockBankTransactionRepository`) : ce module est ce qui le remplacera.
     */
    BANK_SYNC(
        id = "bank_sync",
        titleKey = StringKey.INTEGRATION_BANK_SYNC_TITLE,
        descriptionKey = StringKey.INTEGRATION_BANK_SYNC_DESC,
        glyph = "🏦",
        status = IntegrationStatus.COMING_SOON,
    ),
}
