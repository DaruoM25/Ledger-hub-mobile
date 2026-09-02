package com.ledgerhub.presentation.compliance

import com.ledgerhub.domain.compliance.ComplianceCheck
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Niveau 1 (US-24) — verrouillage du contrat de tags du panneau de conformité.
 *
 * Ces huit chaînes sont **imposées par le cahier des charges** et consommées par les niveaux 3a et
 * 3b. Les figer ici les sort du domaine de la relecture : un renommage de constante ou une
 * interpolation « améliorée » casse ce test avant de casser la QA.
 */
class CompliancePanelTagsTest {

    @Test
    fun theStructuralTags_matchTheSpecification() {
        assertEquals("compliance_panel", CompliancePanelTags.PANEL)
        assertEquals("compliance_scan_btn", CompliancePanelTags.SCAN_BUTTON)
        assertEquals("compliance_checklist", CompliancePanelTags.CHECKLIST)
        assertEquals("compliance_alert", CompliancePanelTags.ALERT)
    }

    @Test
    fun theFourCheckTags_matchTheSpecification() {
        assertEquals("compliance_check_siret", CompliancePanelTags.check(ComplianceCheck.SIRET))
        assertEquals("compliance_check_vat", CompliancePanelTags.check(ComplianceCheck.VAT))
        assertEquals("compliance_check_legal", CompliancePanelTags.check(ComplianceCheck.LEGAL_MENTIONS))
        assertEquals(
            "compliance_check_facturx",
            CompliancePanelTags.check(ComplianceCheck.FACTURX_STRUCTURE),
        )
    }

    @Test
    fun theSpecifiedList_holdsExactlyEightDistinctTags() {
        val specified = CompliancePanelTags.specified()

        assertEquals(8, specified.size, "Le cahier des charges US-24 impose exactement 8 tags")
        assertEquals(specified.size, specified.toSet().size, "Tags dupliqués : $specified")
    }

    /** Deux contrôles qui partageraient un tag rendraient les assertions des niveaux 3 ambiguës. */
    @Test
    fun everyCheck_hasADistinctTag() {
        val tags = ComplianceCheck.entries.map { CompliancePanelTags.check(it) }

        assertEquals(tags.size, tags.toSet().size, "Tags de contrôle dupliqués : $tags")
    }

    /** Tous les tags du panneau lui appartiennent : aucun ne peut viser un nœud d'un autre écran. */
    @Test
    fun everyTag_isNamespacedToThePanel() {
        CompliancePanelTags.specified().forEach { tag ->
            assertEquals(
                true,
                tag.startsWith("compliance_"),
                "Tag hors du préfixe du panneau : $tag",
            )
        }
    }
}
