package com.ledgerhub.domain.creditnote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Niveau 1 — motif légal de l'avoir ([CreditNoteReason]). Pur, sans I/O ni framework.
 */
class CreditNoteReasonTest {

    @Test
    fun presets_exposeTheThreeStandardLabels_inOrder() {
        assertEquals(
            listOf("Erreur de facturation", "Remise commerciale", "Retour de marchandise"),
            CreditNoteReason.PRESETS.map { it.label },
        )
    }

    @Test
    fun resolveReason_withAPreset_returnsItsLabel_ignoringFreeText() {
        assertEquals(
            "Remise commerciale",
            CreditNoteReason.resolveReason(CreditNoteReason.COMMERCIAL_DISCOUNT, "").getOrThrow(),
        )
        assertEquals(
            "Erreur de facturation",
            CreditNoteReason.resolveReason(CreditNoteReason.BILLING_ERROR, "texte ignoré").getOrThrow(),
        )
    }

    @Test
    fun resolveReason_withOtherAndBlankText_fails() {
        assertTrue(CreditNoteReason.resolveReason(CreditNoteReason.OTHER, "").isFailure)
        assertTrue(CreditNoteReason.resolveReason(CreditNoteReason.OTHER, "   ").isFailure)
    }

    @Test
    fun resolveReason_withOtherAndText_returnsTrimmedText() {
        assertEquals(
            "Retour partiel de 2 palettes",
            CreditNoteReason.resolveReason(CreditNoteReason.OTHER, "  Retour partiel de 2 palettes  ").getOrThrow(),
        )
    }

    @Test
    fun fromStoredReason_mapsAKnownLabelBackToItsPreset() {
        assertEquals(CreditNoteReason.GOODS_RETURN, CreditNoteReason.fromStoredReason("Retour de marchandise"))
        assertEquals(CreditNoteReason.BILLING_ERROR, CreditNoteReason.fromStoredReason("  Erreur de facturation  "))
    }

    @Test
    fun fromStoredReason_fallsBackToOther_forAFreeMotive() {
        assertEquals(CreditNoteReason.OTHER, CreditNoteReason.fromStoredReason("Geste commercial exceptionnel"))
        assertEquals(CreditNoteReason.OTHER, CreditNoteReason.fromStoredReason("Autre motif"))
    }

    @Test
    fun resolveReason_thenFromStoredReason_roundTrips_forEveryPreset() {
        CreditNoteReason.PRESETS.forEach { preset ->
            val stored = CreditNoteReason.resolveReason(preset, "").getOrThrow()
            assertEquals(preset, CreditNoteReason.fromStoredReason(stored))
        }
    }
}
