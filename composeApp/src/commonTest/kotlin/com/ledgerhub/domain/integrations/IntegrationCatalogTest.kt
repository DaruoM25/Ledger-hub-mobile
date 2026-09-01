package com.ledgerhub.domain.integrations

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Niveau 1 (US-20) — catalogue des modules d'intégration et règles de lecture.
 *
 * Fonctions pures, donc éprouvables sans composition : ce niveau fige **ce que le hub propose et
 * dans quel ordre**, deux décisions produit qu'aucune capture d'écran ne prouve.
 */
class IntegrationCatalogTest {

    // ── Composition du catalogue ────────────────────────────────────────────

    @Test
    fun theCatalog_holdsExactlyTheFourRequiredModules() {
        assertEquals(4, IntegrationCatalog.all().size)
        assertEquals(
            listOf(
                IntegrationModule.STRIPE_PAYMENTS,
                IntegrationModule.FEC_EXPORT,
                IntegrationModule.SLACK_NOTIFICATIONS,
                IntegrationModule.BANK_SYNC,
            ).toSet(),
            IntegrationCatalog.all().toSet(),
        )
    }

    /** Les identifiants sont la racine des tags de test : deux modules homonymes les confondraient. */
    @Test
    fun everyModule_hasAUniqueIdentifier() {
        val ids = IntegrationModule.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "Identifiants dupliqués : $ids")
    }

    @Test
    fun everyModule_carriesAGlyphAndDistinctTranslationKeys() {
        IntegrationModule.entries.forEach { module ->
            assertTrue(module.glyph.isNotBlank(), "Glyphe manquant pour $module")
            assertTrue(
                module.titleKey != module.descriptionKey,
                "$module réutilise la même clé pour son titre et sa description",
            )
        }
        val titleKeys = IntegrationModule.entries.map { it.titleKey }
        assertEquals(titleKeys.size, titleKeys.toSet().size, "Deux modules partagent un titre")
    }

    // ── Statuts ─────────────────────────────────────────────────────────────

    @Test
    fun stripeAndFec_areInBeta_slackAndBankSync_areAnnounced() {
        assertEquals(IntegrationStatus.BETA, IntegrationModule.STRIPE_PAYMENTS.status)
        assertEquals(IntegrationStatus.BETA, IntegrationModule.FEC_EXPORT.status)
        assertEquals(IntegrationStatus.COMING_SOON, IntegrationModule.SLACK_NOTIFICATIONS.status)
        assertEquals(IntegrationStatus.COMING_SOON, IntegrationModule.BANK_SYNC.status)
    }

    /**
     * Aucun statut « disponible » n'existe : le hub est une vitrine, et un module qui s'annoncerait
     * ouvert alors qu'aucun connecteur n'est branché serait un mensonge à l'utilisateur.
     */
    @Test
    fun noModule_claimsToBeAvailable() {
        assertEquals(
            setOf(IntegrationStatus.BETA, IntegrationStatus.COMING_SOON),
            IntegrationStatus.entries.toSet(),
        )
        assertTrue(IntegrationModule.entries.all { it.status in IntegrationStatus.entries })
    }

    @Test
    fun everyStatus_carriesItsBadgeKey() {
        assertEquals(
            IntegrationStatus.entries.size,
            IntegrationStatus.entries.map { it.badgeKey }.toSet().size,
            "Deux statuts partagent la même clé de badge",
        )
    }

    // ── Filtrage ────────────────────────────────────────────────────────────

    @Test
    fun filteringByStatus_returnsTheExpectedModules() {
        assertEquals(
            listOf(IntegrationModule.STRIPE_PAYMENTS, IntegrationModule.FEC_EXPORT),
            IntegrationCatalog.withStatus(IntegrationStatus.BETA),
        )
        assertEquals(
            listOf(IntegrationModule.SLACK_NOTIFICATIONS, IntegrationModule.BANK_SYNC),
            IntegrationCatalog.withStatus(IntegrationStatus.COMING_SOON),
        )
    }

    /** Garde-fou de complétude : un statut ajouté sans module le ferait tomber. */
    @Test
    fun theStatusFilters_partitionTheWholeCatalog() {
        val partitioned = IntegrationStatus.entries.flatMap { IntegrationCatalog.withStatus(it) }
        assertEquals(IntegrationCatalog.all().size, partitioned.size)
        assertEquals(IntegrationCatalog.all().toSet(), partitioned.toSet())
    }

    // ── Ordre d'affichage ───────────────────────────────────────────────────

    @Test
    fun theDisplayOrder_putsBetaModulesFirst() {
        val ordered = IntegrationCatalog.ordered()
        val firstComingSoon = ordered.indexOfFirst { it.status == IntegrationStatus.COMING_SOON }
        val lastBeta = ordered.indexOfLast { it.status == IntegrationStatus.BETA }

        assertTrue(
            lastBeta < firstComingSoon,
            "Un module « bientôt disponible » passe devant un module en bêta : $ordered",
        )
    }

    @Test
    fun theDisplayOrder_losesNoModule() {
        assertEquals(IntegrationCatalog.all().toSet(), IntegrationCatalog.ordered().toSet())
        assertEquals(IntegrationCatalog.all().size, IntegrationCatalog.ordered().size)
    }
}
