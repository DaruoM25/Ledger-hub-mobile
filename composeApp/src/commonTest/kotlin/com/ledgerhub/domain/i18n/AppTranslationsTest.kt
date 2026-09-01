package com.ledgerhub.domain.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Tests QA — complétude et exactitude du dictionnaire bilingue. */
class AppTranslationsTest {

    @Test
    fun everyStringKey_isTranslated_inBothLanguages() {
        // AppTranslations.get lève NoSuchElementException si une clé manque dans une langue.
        StringKey.entries.forEach { key ->
            AppLanguage.entries.forEach { lang ->
                val value = AppTranslations.get(key, lang)
                assertTrue(value.isNotBlank(), "Traduction vide pour $key / $lang")
            }
        }
    }

    @Test
    fun sentinelValues_matchTheWebDictionary() {
        assertEquals("Vue d'ensemble", AppTranslations.get(StringKey.NAV_OVERVIEW, AppLanguage.FR))
        assertEquals("Dashboard", AppTranslations.get(StringKey.NAV_OVERVIEW, AppLanguage.EN))
        assertEquals("Factures", AppTranslations.get(StringKey.NAV_INVOICES, AppLanguage.FR))
        assertEquals("Invoices", AppTranslations.get(StringKey.NAV_INVOICES, AppLanguage.EN))
        assertEquals("Chiffre d'affaires", AppTranslations.get(StringKey.KPI_REVENUE_TITLE, AppLanguage.FR))
        assertEquals("Revenue", AppTranslations.get(StringKey.KPI_REVENUE_TITLE, AppLanguage.EN))
        assertEquals(
            "Le SIRET doit comporter exactement 14 chiffres",
            AppTranslations.get(ValidationErrorKey.CLIENT_SIRET_INVALID.stringKey, AppLanguage.FR),
        )
        assertEquals(
            "SIRET must be exactly 14 digits",
            AppTranslations.get(ValidationErrorKey.CLIENT_SIRET_INVALID.stringKey, AppLanguage.EN),
        )
    }

    /**
     * Libellés réglementaires PPF 2026 (US-13). Leur formulation est imposée par le référentiel :
     * elle est donc figée par des sentinelles, et non laissée à l'appréciation d'une relecture.
     */
    @Test
    fun ppfRegulatoryStatuses_useTheOfficialWording() {
        assertEquals("Déposée", AppTranslations.get(StringKey.STATUS_DEPOSITED, AppLanguage.FR))
        assertEquals("Submitted", AppTranslations.get(StringKey.STATUS_DEPOSITED, AppLanguage.EN))
        assertEquals(
            "Approuvée par l'administration",
            AppTranslations.get(StringKey.STATUS_APPROVED, AppLanguage.FR),
        )
        assertEquals(
            "Approved by administration",
            AppTranslations.get(StringKey.STATUS_APPROVED, AppLanguage.EN),
        )
        assertEquals(
            "Rejetée par la plateforme",
            AppTranslations.get(StringKey.STATUS_REJECTED, AppLanguage.FR),
        )
        assertEquals(
            "Rejected by platform",
            AppTranslations.get(StringKey.STATUS_REJECTED, AppLanguage.EN),
        )
    }

    /**
     * Sélecteur de mode de saisie (US-15). La formulation est imposée par la spécification
     * fonctionnelle : elle est figée par sentinelle, au même titre que les statuts PPF.
     */
    @Test
    fun invoiceFormModes_useTheSpecifiedWording() {
        assertEquals("Mode Formulaire", AppTranslations.get(StringKey.FORM_MODE_CLASSIC, AppLanguage.FR))
        assertEquals("Form Mode", AppTranslations.get(StringKey.FORM_MODE_CLASSIC, AppLanguage.EN))
        assertEquals("Mode Page Blanche", AppTranslations.get(StringKey.FORM_MODE_BLANK_PAGE, AppLanguage.FR))
        assertEquals("Blank Page Mode", AppTranslations.get(StringKey.FORM_MODE_BLANK_PAGE, AppLanguage.EN))
    }

    /**
     * Mentions légales B2B (US-16). La formulation de la mention de retard est **imposée par
     * l'article L.441-10 du Code de commerce** : elle est figée caractère pour caractère, et non
     * laissée à l'appréciation d'une relecture — même traitement que les statuts PPF de l'US-13.
     */
    @Test
    fun b2bLegalMentions_useTheStatutoryWording() {
        assertEquals("Réglementation B2B", AppTranslations.get(StringKey.FORM_SECTION_B2B, AppLanguage.FR))
        assertEquals("B2B Regulations", AppTranslations.get(StringKey.FORM_SECTION_B2B, AppLanguage.EN))

        assertEquals(
            "Appliquer les pénalités de retard légales (B2B)",
            AppTranslations.get(StringKey.B2B_PENALTIES_CHECKBOX, AppLanguage.FR),
        )
        assertEquals(
            "Apply statutory late payment penalties (B2B)",
            AppTranslations.get(StringKey.B2B_PENALTIES_CHECKBOX, AppLanguage.EN),
        )

        assertEquals(
            "En cas de retard de paiement, une pénalité égale à 3 fois le taux d'intérêt légal " +
                "sera appliquée, ainsi qu'une indemnité forfaitaire de 40€ pour frais de " +
                "recouvrement conformément à l'article L.441-10 du Code de commerce.",
            AppTranslations.get(StringKey.B2B_LEGAL_MENTION, AppLanguage.FR),
        )
        assertEquals(
            "In the event of late payment, a penalty equal to 3 times the legal interest rate " +
                "will apply, along with a fixed recovery fee of €40 pursuant to Article L.441-10 " +
                "of the French Commercial Code.",
            AppTranslations.get(StringKey.B2B_LEGAL_MENTION, AppLanguage.EN),
        )

        assertEquals(
            "Merci pour votre confiance.",
            AppTranslations.get(StringKey.B2B_COURTESY_MENTION, AppLanguage.FR),
        )
        assertEquals(
            "Thank you for your trust.",
            AppTranslations.get(StringKey.B2B_COURTESY_MENTION, AppLanguage.EN),
        )
    }

    /**
     * Libellés du Rapprochement Bancaire (US-18). Formulation imposée par le cahier des charges,
     * donc figée ici plutôt que laissée à une relecture : « Associer (Lettrage) » et « Écart de
     * montant » sont le vocabulaire métier attendu par l'utilisateur comptable, et leur
     * reformulation en cours de route casserait la parité avec le Web.
     */
    @Test
    fun bankReconciliationLabels_useTheAgreedWording() {
        assertEquals("Rapprochement Bancaire", AppTranslations.get(StringKey.RECONCILIATION_TITLE, AppLanguage.FR))
        assertEquals("Bank Reconciliation", AppTranslations.get(StringKey.RECONCILIATION_TITLE, AppLanguage.EN))

        assertEquals(
            "Transactions bancaires récentes",
            AppTranslations.get(StringKey.RECONCILIATION_TRANSACTIONS_COLUMN, AppLanguage.FR),
        )
        assertEquals(
            "Recent Bank Transactions",
            AppTranslations.get(StringKey.RECONCILIATION_TRANSACTIONS_COLUMN, AppLanguage.EN),
        )

        assertEquals(
            "Factures en attente de paiement",
            AppTranslations.get(StringKey.RECONCILIATION_INVOICES_COLUMN, AppLanguage.FR),
        )
        assertEquals(
            "Pending Invoices",
            AppTranslations.get(StringKey.RECONCILIATION_INVOICES_COLUMN, AppLanguage.EN),
        )

        assertEquals(
            "Associer (Lettrage)",
            AppTranslations.get(StringKey.RECONCILIATION_ACTION_MATCH, AppLanguage.FR),
        )
        assertEquals(
            "Link (Reconciliation)",
            AppTranslations.get(StringKey.RECONCILIATION_ACTION_MATCH, AppLanguage.EN),
        )

        assertEquals(
            "Rapprochée",
            AppTranslations.get(StringKey.RECONCILIATION_BADGE_RECONCILED, AppLanguage.FR),
        )
        assertEquals(
            "Reconciled",
            AppTranslations.get(StringKey.RECONCILIATION_BADGE_RECONCILED, AppLanguage.EN),
        )

        assertEquals(
            "Écart de montant",
            AppTranslations.get(StringKey.RECONCILIATION_BADGE_AMOUNT_MISMATCH, AppLanguage.FR),
        )
        assertEquals(
            "Amount Mismatch",
            AppTranslations.get(StringKey.RECONCILIATION_BADGE_AMOUNT_MISMATCH, AppLanguage.EN),
        )
    }

    /**
     * Libellés de la palette de commandes (US-19). Le placeholder et les trois actions rapides sont
     * imposés par le cahier des charges : ils sont figés ici, une reformulation en cours de route
     * casserait la parité avec le Web.
     */
    @Test
    fun commandPaletteLabels_useTheAgreedWording() {
        assertEquals(
            "Que voulez-vous faire ?",
            AppTranslations.get(StringKey.COMMAND_PALETTE_PLACEHOLDER, AppLanguage.FR),
        )
        assertEquals(
            "What would you like to do?",
            AppTranslations.get(StringKey.COMMAND_PALETTE_PLACEHOLDER, AppLanguage.EN),
        )

        assertEquals(
            "Créer une facture pour un nouveau client",
            AppTranslations.get(StringKey.COMMAND_ACTION_CREATE_INVOICE, AppLanguage.FR),
        )
        assertEquals(
            "Create an invoice for a new client",
            AppTranslations.get(StringKey.COMMAND_ACTION_CREATE_INVOICE, AppLanguage.EN),
        )

        assertEquals(
            "Relancer les factures en retard",
            AppTranslations.get(StringKey.COMMAND_ACTION_REMIND_OVERDUE, AppLanguage.FR),
        )
        assertEquals(
            "Remind overdue invoices",
            AppTranslations.get(StringKey.COMMAND_ACTION_REMIND_OVERDUE, AppLanguage.EN),
        )

        assertEquals(
            "Générer l'export comptable",
            AppTranslations.get(StringKey.COMMAND_ACTION_EXPORT_ACCOUNTING, AppLanguage.FR),
        )
        assertEquals(
            "Generate the accounting export",
            AppTranslations.get(StringKey.COMMAND_ACTION_EXPORT_ACCOUNTING, AppLanguage.EN),
        )
    }

    @Test
    fun appLanguage_toggle_isBinaryAndSymmetric() {
        assertEquals(AppLanguage.EN, AppLanguage.FR.toggled())
        assertEquals(AppLanguage.FR, AppLanguage.EN.toggled())
    }

    /**
     * Hub d'intégrations (US-20). Les libellés des badges et les intitulés des quatre modules sont
     * **imposés par le cahier des charges** : ils sont figés caractère pour caractère, au même
     * titre que les statuts PPF de l'US-13. Une reformulation de confort casse ce test.
     */
    @Test
    fun integrationsHub_usesTheSpecifiedWording() {
        assertEquals(
            "Bêta - Accès anticipé",
            AppTranslations.get(StringKey.INTEGRATION_BADGE_BETA, AppLanguage.FR),
        )
        assertEquals(
            "Beta - Early access",
            AppTranslations.get(StringKey.INTEGRATION_BADGE_BETA, AppLanguage.EN),
        )
        assertEquals(
            "Bientôt disponible",
            AppTranslations.get(StringKey.INTEGRATION_BADGE_COMING_SOON, AppLanguage.FR),
        )
        assertEquals(
            "Coming soon",
            AppTranslations.get(StringKey.INTEGRATION_BADGE_COMING_SOON, AppLanguage.EN),
        )

        assertEquals(
            "Paiement en ligne par CB (Stripe)",
            AppTranslations.get(StringKey.INTEGRATION_STRIPE_TITLE, AppLanguage.FR),
        )
        assertEquals(
            "Notifications Slack",
            AppTranslations.get(StringKey.INTEGRATION_SLACK_TITLE, AppLanguage.FR),
        )
        assertEquals(
            "Export FEC Expert-Comptable",
            AppTranslations.get(StringKey.INTEGRATION_FEC_TITLE, AppLanguage.FR),
        )
        assertEquals(
            "Synchronisation Bancaire API",
            AppTranslations.get(StringKey.INTEGRATION_BANK_SYNC_TITLE, AppLanguage.FR),
        )

        assertEquals(
            "Hub d'intégrations",
            AppTranslations.get(StringKey.INTEGRATIONS_TITLE, AppLanguage.FR),
        )
        assertEquals(
            "Integrations Hub",
            AppTranslations.get(StringKey.INTEGRATIONS_TITLE, AppLanguage.EN),
        )
    }

    /**
     * Écran d'authentification (US-21). Le libellé du badge de vérification est **imposé par le
     * cahier des charges, coche comprise** : il est figé caractère pour caractère, au même titre
     * que les statuts PPF de l'US-13. Une reformulation de confort casse ce test.
     */
    @Test
    fun sireneRegistration_usesTheSpecifiedWording() {
        assertEquals(
            "✓ Entreprise vérifiée via l'API SIRENE",
            AppTranslations.get(StringKey.AUTH_SIRENE_VERIFIED_BADGE, AppLanguage.FR),
        )
        assertEquals(
            "✓ Company verified via the SIRENE API",
            AppTranslations.get(StringKey.AUTH_SIRENE_VERIFIED_BADGE, AppLanguage.EN),
        )

        assertEquals("Inscription", AppTranslations.get(StringKey.AUTH_TAB_REGISTER, AppLanguage.FR))
        assertEquals("Sign up", AppTranslations.get(StringKey.AUTH_TAB_REGISTER, AppLanguage.EN))
        assertEquals(
            "SIRET de l'entreprise",
            AppTranslations.get(StringKey.AUTH_SIRET_LABEL, AppLanguage.FR),
        )
        assertEquals(
            "Company SIRET",
            AppTranslations.get(StringKey.AUTH_SIRET_LABEL, AppLanguage.EN),
        )
        assertEquals(
            "Raison sociale",
            AppTranslations.get(StringKey.AUTH_COMPANY_NAME_LABEL, AppLanguage.FR),
        )
        assertEquals(
            "Company name",
            AppTranslations.get(StringKey.AUTH_COMPANY_NAME_LABEL, AppLanguage.EN),
        )
    }

    /**
     * L'écran de connexion était en français en dur jusqu'à l'US-21 (exclusion documentée en tête
     * de `StringKey`). Ces sentinelles constatent qu'il ne l'est plus.
     */
    @Test
    fun theLoginScreen_isNowBilingual() {
        assertEquals("Connexion à LedgerHub", AppTranslations.get(StringKey.AUTH_LOGIN_TITLE, AppLanguage.FR))
        assertEquals("Sign in to LedgerHub", AppTranslations.get(StringKey.AUTH_LOGIN_TITLE, AppLanguage.EN))
        assertEquals("Mot de passe", AppTranslations.get(StringKey.AUTH_PASSWORD_LABEL, AppLanguage.FR))
        assertEquals("Password", AppTranslations.get(StringKey.AUTH_PASSWORD_LABEL, AppLanguage.EN))
    }
}
