package com.ledgerhub.presentation.invoices

import androidx.compose.ui.graphics.Color
import com.ledgerhub.domain.i18n.AppLanguage
import com.ledgerhub.domain.i18n.AppTranslations
import com.ledgerhub.domain.invoice.InvoiceStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Contrat d'affichage du statut réglementaire (US-13).
 *
 * Les `when` de [InvoiceStatusUi] sont exhaustifs : le compilateur garantit qu'un statut ajouté
 * y est traité. Ce qu'il ne garantit pas — et que ces tests couvrent — c'est que chaque statut
 * reçoive une identité *distincte* : une couleur copiée-collée ou une clé de traduction
 * dupliquée compile parfaitement, et rendrait deux statuts réglementaires indiscernables.
 */
class InvoiceStatusUiTest {

    @Test
    fun everyStatus_hasItsOwnTranslationKey() {
        val keys = InvoiceStatus.entries.map { it.labelKey() }
        assertEquals(keys.size, keys.toSet().size, "Deux statuts partagent la même clé i18n : $keys")
    }

    @Test
    fun everyStatus_hasADistinctAccentColour() {
        val colours = InvoiceStatus.entries.map { it.tagColor() }
        assertEquals(colours.size, colours.toSet().size, "Deux statuts partagent la même teinte d'accent")
    }

    @Test
    fun ppfStatuses_useTheColourCodeOfTheSpecification() {
        // Dépôt = bleu, approbation = émeraude, rejet = rouge vif. Valeurs figées : un ajustement
        // de charte doit être un choix explicite, pas un effet de bord.
        assertEquals(Color(0xFF1565C0), InvoiceStatus.DEPOSITED.tagColor(), "Dépôt PPF = bleu")
        assertEquals(Color(0xFF00A86B), InvoiceStatus.APPROVED.tagColor(), "Approbation = émeraude")
        assertEquals(Color(0xFFD32F2F), InvoiceStatus.REJECTED.tagColor(), "Rejet plateforme = rouge vif")
    }

    @Test
    fun accentAndContainerColours_areNeverTheSame() {
        // Le point indicateur doit rester lisible sur le fond de la pastille.
        InvoiceStatus.entries.forEach { status ->
            assertTrue(
                status.tagColor() != status.containerColor(),
                "Point et fond confondus pour $status : l'indicateur serait invisible",
            )
            assertTrue(
                status.onContainerColor() != status.containerColor(),
                "Texte et fond confondus pour $status",
            )
        }
    }

    @Test
    fun glyph_marksOnlyTheUnfavourableOutcomes() {
        assertNotNull(InvoiceStatus.REJECTED.glyph(), "Un rejet plateforme doit porter un glyphe d'alerte")
        assertNotNull(InvoiceStatus.REFUSED.glyph(), "Un refus acheteur doit porter un glyphe d'alerte")
        listOf(
            InvoiceStatus.DRAFT,
            InvoiceStatus.DEPOSITED,
            InvoiceStatus.APPROVED,
            InvoiceStatus.PAID,
            InvoiceStatus.CANCELLED,
        ).forEach { assertNull(it.glyph(), "$it ne doit pas être signalé comme un incident") }
    }

    @Test
    fun labelKeys_resolveToTheOfficialPpfWording() {
        assertEquals(
            "Déposée",
            AppTranslations.get(InvoiceStatus.DEPOSITED.labelKey(), AppLanguage.FR),
        )
        assertEquals(
            "Approuvée par l'administration",
            AppTranslations.get(InvoiceStatus.APPROVED.labelKey(), AppLanguage.FR),
        )
        assertEquals(
            "Rejetée par la plateforme",
            AppTranslations.get(InvoiceStatus.REJECTED.labelKey(), AppLanguage.FR),
        )
    }

    @Test
    fun everyStatus_isReachableByExactlyOneFilter() {
        // Un statut qu'aucun filtre ne retient serait invisible dès qu'on quitte "Toutes".
        // TOUTES et OVERDUE sont écartés : le premier retient tout, le second ne porte pas sur le
        // statut mais sur l'échéance (US-19), et ne fausse donc pas ce décompte.
        val statusFilters = InvoiceStatusFilter.entries -
            setOf(InvoiceStatusFilter.TOUTES, InvoiceStatusFilter.OVERDUE)
        InvoiceStatus.entries.forEach { status ->
            val matching = statusFilters.filter { it.matchesStatus(status) }
            assertEquals(1, matching.size, "Filtres retenant $status : $matching")
        }
    }
}
