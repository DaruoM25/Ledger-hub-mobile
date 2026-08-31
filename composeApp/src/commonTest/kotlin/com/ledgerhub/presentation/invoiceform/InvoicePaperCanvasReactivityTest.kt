package com.ledgerhub.presentation.invoiceform

import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.parseAmountToCents
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.presentation.components.filterAmount
import com.ledgerhub.presentation.components.filterQuantity
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Niveau 1 (US-15) — réactivité des totaux à la frappe en mode Page Blanche.
 *
 * La feuille blanche n'a **aucun état propre** : chaque cellule émet les mêmes intentions que le
 * formulaire classique et relit le [InvoiceFormUiState] recalculé par le ViewModel. Ces tests
 * pilotent donc directement le ViewModel, sans composition — ce qui est testé ici est la boucle
 * « frappe → revalidation → totaux », pas le rendu (couvert par N3a/N3b).
 */
class InvoicePaperCanvasReactivityTest {

    /** Frappe une cellule Prix unitaire HT de la ligne 0, en conservant les autres champs. */
    private fun InvoiceFormViewModel.typeUnitPrice(text: String, label: String = "Prestation") {
        val line = uiState.value.lines[0]
        processIntent(
            InvoiceFormIntent.UpdateLine(
                index = 0,
                label = label,
                quantity = line.quantity,
                unitPriceHt = text,
                vatRate = line.vatRate,
            ),
        )
    }

    /**
     * Le cœur de l'US-15 : le total suit la frappe **caractère par caractère**, y compris quand
     * la saisie traverse un état intermédiaire invalide (`"12."`), où il retombe à zéro plutôt
     * que de rester figé sur une valeur périmée qui mentirait à l'utilisateur.
     */
    @Test
    fun typingUnitPriceCharacterByCharacter_updatesTotalsAtEveryKeystroke() {
        val viewModel = InvoiceFormViewModel()

        val expectedHtCents = mapOf(
            "" to 0L,
            "1" to 100L,
            "12" to 1_200L,
            "12." to 0L,
            "12.5" to 1_250L,
            "12.50" to 1_250L,
        )

        expectedHtCents.forEach { (typed, expected) ->
            viewModel.typeUnitPrice(typed)
            assertEquals(
                Money(expected),
                viewModel.uiState.value.totalHt,
                "Total HT incorrect après la frappe \"$typed\"",
            )
        }
    }

    /** Quantité, prix et TVA : le trio se répercute d'un coup sur les trois totaux du bas de feuille. */
    @Test
    fun editingQuantityAndUnitPrice_recomputesHtVatAndTtc() {
        val viewModel = InvoiceFormViewModel()

        viewModel.processIntent(
            InvoiceFormIntent.UpdateLine(0, "Prestation", "3", "25.00", VatRate.TAUX_NORMAL),
        )

        val state = viewModel.uiState.value
        // 3 x 25,00 € = 75,00 € HT ; TVA 20 % = 15,00 € ; TTC = 90,00 €.
        assertEquals(Money(7_500), state.totalHt)
        assertEquals(Money(1_500), state.totalVat)
        assertEquals(Money(9_000), state.totalTtc)
    }

    /**
     * La 5e colonne de la feuille affiche un total de ligne **HT** recalculé localement, quand le
     * bloc récapitulatif lit les `Money` du ViewModel. Deux chemins de calcul, donc un risque de
     * dérive : ce test verrouille leur équivalence sur un cas multi-taux.
     */
    @Test
    fun sumOfLineTotalsHt_alwaysMatchesTheFooterTotalHt() {
        val viewModel = InvoiceFormViewModel()

        viewModel.processIntent(
            InvoiceFormIntent.UpdateLine(0, "Conseil", "2", "100.00", VatRate.TAUX_NORMAL),
        )
        viewModel.processIntent(InvoiceFormIntent.AddLine)
        viewModel.processIntent(
            InvoiceFormIntent.UpdateLine(1, "Ouvrage", "3", "19.90", VatRate.TAUX_REDUIT),
        )

        val state = viewModel.uiState.value
        // Reproduction du calcul « en direct » de la 5e colonne : arithmetique entiere au
        // centime, exactement comme `liveTotalHtCents()` dans la feuille.
        val sumOfLineTotalsHt = state.lines.sumOf { line ->
            line.quantity.toLong() * parseAmountToCents(line.unitPriceHt)!!
        }

        assertEquals(sumOfLineTotalsHt, state.totalHt.cents)
        // 200,00 € à 20 % + 59,70 € à 5,5 % = 40,00 € + 3,28 € (arrondi sur base agrégée).
        assertEquals(Money(25_970), state.totalHt)
        assertEquals(Money(4_328), state.totalVat)
        assertEquals(Money(30_298), state.totalTtc)
    }

    /**
     * Une ligne incomplète ne contribue à rien — même règle que le formulaire classique. Sans
     * cela, la feuille afficherait un total pour une ligne que la soumission refuserait.
     */
    @Test
    fun incompleteLine_contributesNothingToTheTotals() {
        val viewModel = InvoiceFormViewModel()

        // Prix saisi mais libellé encore vide : la ligne reste invalide.
        viewModel.processIntent(InvoiceFormIntent.UpdateLine(0, "", "1", "100.00", VatRate.TAUX_NORMAL))
        assertEquals(Money.ZERO, viewModel.uiState.value.totalHt)

        // Le libellé complète la ligne : le total apparaît immédiatement.
        viewModel.processIntent(InvoiceFormIntent.UpdateLine(0, "Prestation", "1", "100.00", VatRate.TAUX_NORMAL))
        assertEquals(Money(10_000), viewModel.uiState.value.totalHt)
    }

    /**
     * Les cellules de la feuille filtrent la frappe avec les mêmes règles que le formulaire
     * classique (voir `InputFilters`) : ce qui atteint le ViewModel est déjà normalisé, et la
     * virgule du clavier français y est acceptée telle quelle.
     */
    @Test
    fun cellInputFilters_normalizeKeystrokesBeforeTheyReachTheViewModel() {
        assertEquals("12,50", filterAmount("12,50abc"))
        assertEquals("1250", filterAmount("1250"))
        // Un second séparateur est refusé — la saisie reste un montant lisible.
        assertEquals("12.50", filterAmount("12.5.0"))
        assertEquals("3", filterQuantity("3x"))

        val viewModel = InvoiceFormViewModel()
        viewModel.typeUnitPrice(filterAmount("12,50 €"))

        assertEquals(Money(1_250), viewModel.uiState.value.totalHt)
    }

}
