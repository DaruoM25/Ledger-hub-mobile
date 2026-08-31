package com.ledgerhub.presentation.invoiceform

import com.ledgerhub.domain.invoice.VatRate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Niveau 1 (US-16) — pénalités de retard légales B2B : bascule d'état et report sur la facture.
 *
 * `applyB2bPenalties` n'est pas un champ de saisie mais une **mention légale** : ces tests
 * verrouillent donc autant ce que la bascule fait (commuter le pied de page, suivre jusqu'à
 * l'`Invoice` construite) que ce qu'elle ne doit surtout pas faire (toucher à la validation ou
 * aux montants).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InvoiceB2bPenaltiesTest {

    private val validClientSiret = "98765432100045"

    private fun fillValidForm(viewModel: InvoiceFormViewModel) {
        viewModel.processIntent(InvoiceFormIntent.InvoiceNumberChanged("F-2026-B2B-001"))
        viewModel.processIntent(InvoiceFormIntent.IssueDateChanged("2026-08-31"))
        viewModel.processIntent(InvoiceFormIntent.DueDateChanged("2026-09-30"))
        viewModel.processIntent(InvoiceFormIntent.ClientNameChanged("Client SAS"))
        viewModel.processIntent(InvoiceFormIntent.ClientSiretChanged(validClientSiret))
        viewModel.processIntent(InvoiceFormIntent.ClientEmailChanged("compta@client-sas.fr"))
        viewModel.processIntent(
            InvoiceFormIntent.UpdateLine(0, "Prestation de conseil", "2", "50.00", VatRate.TAUX_NORMAL),
        )
    }

    // ── État et bascule ──────────────────────────────────────────────────────

    /**
     * Conformité par défaut, comme Factur-X : la mention de l'article L.441-10 est obligatoire
     * entre professionnels, et un défaut à `false` produirait silencieusement des factures
     * non conformes pour l'utilisateur qui ne pense pas à cocher la case.
     */
    @Test
    fun initialState_appliesB2bPenalties() {
        assertTrue(InvoiceFormViewModel().uiState.value.applyB2bPenalties)
    }

    @Test
    fun togglingPenalties_flipsTheStateInBothDirections() {
        val viewModel = InvoiceFormViewModel()

        viewModel.processIntent(InvoiceFormIntent.ToggleB2bPenalties(false))
        assertFalse(viewModel.uiState.value.applyB2bPenalties)

        viewModel.processIntent(InvoiceFormIntent.ToggleB2bPenalties(true))
        assertTrue(viewModel.uiState.value.applyB2bPenalties)
    }

    /**
     * L'intention porte une valeur absolue, jamais un « inverse l'état » : la rejouer doit être
     * sans effet. C'est ce qui rend sûres deux vues branchées sur le même état (formulaire et
     * feuille blanche), où un double événement est toujours possible.
     */
    @Test
    fun togglingPenalties_isIdempotent() {
        val viewModel = InvoiceFormViewModel()

        viewModel.processIntent(InvoiceFormIntent.ToggleB2bPenalties(false))
        viewModel.processIntent(InvoiceFormIntent.ToggleB2bPenalties(false))

        assertFalse(viewModel.uiState.value.applyB2bPenalties)
    }

    /**
     * Une mention légale n'est pas un champ validé : la bascule ne doit produire aucune erreur,
     * ne marquer aucun champ comme saisi, et ne déplacer aucun montant.
     */
    @Test
    fun togglingPenalties_touchesNeitherValidationNorTotals() {
        val viewModel = InvoiceFormViewModel()
        fillValidForm(viewModel)
        val before = viewModel.uiState.value

        viewModel.processIntent(InvoiceFormIntent.ToggleB2bPenalties(false))
        val after = viewModel.uiState.value

        assertEquals(before.errors, after.errors)
        assertEquals(before.touchedFields, after.touchedFields)
        assertEquals(before.totalHt, after.totalHt)
        assertEquals(before.totalVat, after.totalVat)
        assertEquals(before.totalTtc, after.totalTtc)
        assertEquals(before.isSubmitEnabled, after.isSubmitEnabled)
        // Seul le drapeau a bougé.
        assertEquals(before.copy(applyB2bPenalties = false), after)
    }

    /** Les deux bascules réglementaires sont indépendantes : décocher l'une ne touche pas l'autre. */
    @Test
    fun b2bPenaltiesAndFacturX_areIndependentFlags() {
        val viewModel = InvoiceFormViewModel()

        viewModel.processIntent(InvoiceFormIntent.ToggleB2bPenalties(false))
        assertTrue(viewModel.uiState.value.generateFacturX)

        viewModel.processIntent(InvoiceFormIntent.ToggleFacturX(false))
        assertFalse(viewModel.uiState.value.applyB2bPenalties)
        assertFalse(viewModel.uiState.value.generateFacturX)
    }

    // ── Report sur la facture émise ─────────────────────────────────────────

    /**
     * Le drapeau doit atteindre l'`Invoice` construite, sans quoi il resterait un simple effet
     * d'affichage : la mention apparaîtrait à l'écran mais ne serait ni persistée, ni imprimée.
     */
    @Test
    fun savingADraft_carriesThePenaltiesFlagOntoTheInvoice() = runTest {
        val viewModel = InvoiceFormViewModel(dispatcher = StandardTestDispatcher(testScheduler))
        fillValidForm(viewModel)

        viewModel.processIntent(InvoiceFormIntent.SaveDraft)
        advanceUntilIdle()

        val submitted = viewModel.uiState.value.submittedInvoice
        assertNotNull(submitted, "La facture aurait dû être enregistrée")
        assertTrue(submitted.applyB2bPenalties)
    }

    @Test
    fun savingADraft_withPenaltiesDisabled_carriesTheDisabledFlag() = runTest {
        val viewModel = InvoiceFormViewModel(dispatcher = StandardTestDispatcher(testScheduler))
        fillValidForm(viewModel)
        viewModel.processIntent(InvoiceFormIntent.ToggleB2bPenalties(false))

        viewModel.processIntent(InvoiceFormIntent.SaveDraft)
        advanceUntilIdle()

        val submitted = viewModel.uiState.value.submittedInvoice
        assertNotNull(submitted, "La facture aurait dû être enregistrée")
        // Le cas `false` est le vrai test : le défaut du domaine étant `true`, un report oublié
        // passerait inaperçu sur le seul cas nominal.
        assertFalse(submitted.applyB2bPenalties)
    }
}
