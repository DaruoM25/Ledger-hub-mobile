package com.ledgerhub.presentation.integrations

import com.ledgerhub.domain.integrations.IntegrationModule
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Niveau 1 (US-20) — verrouillage du contrat de tags.
 *
 * Ces chaînes sont **imposées par le cahier des charges** et consommées par les niveaux 3a et 3b.
 * Les figer ici les sort du domaine de la relecture : un renommage de constante ou une
 * interpolation « améliorée » casse ce test avant de casser la QA.
 */
class IntegrationsHubTagsTest {

    @Test
    fun theContainerTag_matchesTheSpecification() {
        assertEquals("integrations_hub_container", IntegrationsHubTags.CONTAINER)
    }

    @Test
    fun theFourCardTags_matchTheSpecification() {
        assertEquals(
            "integration_card_stripe",
            IntegrationsHubTags.card(IntegrationModule.STRIPE_PAYMENTS),
        )
        assertEquals(
            "integration_card_slack",
            IntegrationsHubTags.card(IntegrationModule.SLACK_NOTIFICATIONS),
        )
        assertEquals("integration_card_fec", IntegrationsHubTags.card(IntegrationModule.FEC_EXPORT))
        assertEquals(
            "integration_card_bank_sync",
            IntegrationsHubTags.card(IntegrationModule.BANK_SYNC),
        )
    }

    /** Deux modules qui partageraient un tag rendraient les assertions des niveaux 3 ambiguës. */
    @Test
    fun everyModule_hasADistinctCardAndBadgeTag() {
        val cardTags = IntegrationModule.entries.map { IntegrationsHubTags.card(it) }
        val badgeTags = IntegrationModule.entries.map { IntegrationsHubTags.badge(it) }

        assertEquals(cardTags.size, cardTags.toSet().size, "Tags de carte dupliqués : $cardTags")
        assertEquals(badgeTags.size, badgeTags.toSet().size, "Tags de badge dupliqués : $badgeTags")
        assertEquals(
            emptySet(),
            cardTags.toSet().intersect(badgeTags.toSet()),
            "Un tag de badge collisionne avec un tag de carte",
        )
    }
}
