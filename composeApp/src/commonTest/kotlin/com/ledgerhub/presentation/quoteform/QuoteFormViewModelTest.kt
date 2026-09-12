package com.ledgerhub.presentation.quoteform

import com.ledgerhub.data.quote.MockQuoteRepository
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.domain.quote.SubmitQuoteUseCase
import com.ledgerhub.presentation.components.QuickClientField
import com.ledgerhub.presentation.invoiceform.InMemoryClientRepository
import com.ledgerhub.presentation.invoiceform.SubmissionStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class QuoteFormViewModelTest {

    private val validIssuerSiren = "123456789"
    private val validIssuerSiret = "12345678900012"
    private val validRecipientSiren = "987654321"
    private val validRecipientSiret = "98765432100045"

    private fun fillHeader(viewModel: QuoteFormViewModel) {
        viewModel.processIntent(QuoteFormIntent.QuoteNumberChanged("DEV-2026-001"))
        viewModel.processIntent(QuoteFormIntent.IssueDateChanged("2026-08-03"))
        viewModel.processIntent(QuoteFormIntent.ValidityDateChanged("2026-09-03"))
        viewModel.processIntent(QuoteFormIntent.IssuerNameChanged("Vendeur SARL"))
        viewModel.processIntent(QuoteFormIntent.IssuerSirenChanged(validIssuerSiren))
        viewModel.processIntent(QuoteFormIntent.IssuerSiretChanged(validIssuerSiret))
        viewModel.processIntent(QuoteFormIntent.RecipientNameChanged("Client SAS"))
        viewModel.processIntent(QuoteFormIntent.RecipientSirenChanged(validRecipientSiren))
        viewModel.processIntent(QuoteFormIntent.RecipientSiretChanged(validRecipientSiret))
    }

    private fun fillLine(
        viewModel: QuoteFormViewModel,
        index: Int,
        label: String,
        quantity: String,
        unitPriceHt: String,
        vatRate: VatRate = VatRate.TAUX_NORMAL,
    ) {
        viewModel.processIntent(QuoteFormIntent.UpdateLine(index, label, quantity, unitPriceHt, vatRate))
    }

    private fun fillValidSingleLineForm(viewModel: QuoteFormViewModel) {
        fillHeader(viewModel)
        fillLine(viewModel, index = 0, label = "Prestation de conseil", quantity = "2", unitPriceHt = "50.00")
    }

    // ── État initial ──────────────────────────────────────────────────────────

    @Test
    fun initialState_hasOneEmptyLine_andValidationErrors_andSubmitDisabled() {
        val viewModel = QuoteFormViewModel()
        val state = viewModel.uiState.value
        assertEquals(1, state.lines.size)
        assertFalse(state.isSubmitEnabled)
        assertTrue(state.errors.isNotEmpty())
        assertEquals(SubmissionStatus.Idle, state.submissionStatus)
    }

    // ── Validation SIREN/SIRET (en-tête) ────────────────────────────────────

    @Test
    fun invalidSiren_producesFieldError() {
        val viewModel = QuoteFormViewModel()
        viewModel.processIntent(QuoteFormIntent.IssuerSirenChanged("123"))
        assertNotNull(viewModel.uiState.value.errors[QuoteFormField.ISSUER_SIREN])
    }

    @Test
    fun invalidSiret_producesFieldError() {
        val viewModel = QuoteFormViewModel()
        viewModel.processIntent(QuoteFormIntent.RecipientSiretChanged("abc"))
        assertNotNull(viewModel.uiState.value.errors[QuoteFormField.RECIPIENT_SIRET])
    }

    // ── Champ spécifique devis : date de validité ────────────────────────────

    @Test
    fun invalidValidityDate_producesFieldError() {
        val viewModel = QuoteFormViewModel()
        viewModel.processIntent(QuoteFormIntent.ValidityDateChanged("not-a-date"))
        assertNotNull(viewModel.uiState.value.errors[QuoteFormField.VALIDITY_DATE])
    }

    @Test
    fun validValidityDate_clearsFieldError() {
        val viewModel = QuoteFormViewModel()
        viewModel.processIntent(QuoteFormIntent.ValidityDateChanged("2026-09-03"))
        assertNull(viewModel.uiState.value.errors[QuoteFormField.VALIDITY_DATE])
    }

    // ── Multi-lignes : ajout / suppression ──────────────────────────────────

    @Test
    fun addLine_appendsEmptyLine() {
        val viewModel = QuoteFormViewModel()
        viewModel.processIntent(QuoteFormIntent.AddLine)
        viewModel.processIntent(QuoteFormIntent.AddLine)
        assertEquals(3, viewModel.uiState.value.lines.size)
    }

    @Test
    fun threeLines_computeExactAggregatedTotal() {
        val viewModel = QuoteFormViewModel()
        viewModel.processIntent(QuoteFormIntent.AddLine)
        viewModel.processIntent(QuoteFormIntent.AddLine)

        fillLine(viewModel, 0, "Conseil", "1", "100.00", VatRate.TAUX_NORMAL)
        fillLine(viewModel, 1, "Formation", "2", "25.00", VatRate.TAUX_NORMAL)
        fillLine(viewModel, 2, "Livre", "1", "20.00", VatRate.TAUX_REDUIT)

        val state = viewModel.uiState.value
        assertEquals(17000L, state.totalHt.cents)
        assertEquals(3110L, state.totalVat.cents)
        assertEquals(20110L, state.totalTtc.cents)
    }

    @Test
    fun removeLine_updatesTotalsInstantly() {
        val viewModel = QuoteFormViewModel()
        viewModel.processIntent(QuoteFormIntent.AddLine)
        fillLine(viewModel, 0, "Conseil", "1", "100.00")
        fillLine(viewModel, 1, "Formation", "1", "50.00")
        assertEquals(15000L, viewModel.uiState.value.totalHt.cents)

        viewModel.processIntent(QuoteFormIntent.RemoveLine(1))

        val state = viewModel.uiState.value
        assertEquals(1, state.lines.size)
        assertEquals(10000L, state.totalHt.cents)
    }

    @Test
    fun removeLine_lastRemainingLine_isBlocked() {
        val viewModel = QuoteFormViewModel()
        viewModel.processIntent(QuoteFormIntent.RemoveLine(0))
        assertEquals(1, viewModel.uiState.value.lines.size)
    }

    // ── Validation par ligne ─────────────────────────────────────────────────

    @Test
    fun lineWithBlankLabel_producesLineError_andBlocksSubmit() {
        val viewModel = QuoteFormViewModel()
        fillHeader(viewModel)
        fillLine(viewModel, 0, label = "", quantity = "1", unitPriceHt = "10.00")

        val state = viewModel.uiState.value
        assertNotNull(state.lines[0].errors[QuoteLineField.LABEL])
        assertFalse(state.isSubmitEnabled)
    }

    @Test
    fun oneInvalidLineAmongValidOnes_blocksGlobalSubmit_butOthersStillContributeToTotal() {
        val viewModel = QuoteFormViewModel()
        viewModel.processIntent(QuoteFormIntent.AddLine)
        fillHeader(viewModel)
        fillLine(viewModel, 0, "Conseil valide", "1", "100.00")
        fillLine(viewModel, 1, "", "1", "50.00") // ligne 1 invalide

        val state = viewModel.uiState.value
        assertFalse(state.isSubmitEnabled)
        assertNotNull(state.lines[1].errors[QuoteLineField.LABEL])
        assertEquals(10000L, state.totalHt.cents)
    }

    // ── Soumission invalide : aucun devis produit ────────────────────────────

    @Test
    fun submit_withInvalidForm_staysIdle_doesNotProduceQuote() {
        val viewModel = QuoteFormViewModel()
        viewModel.processIntent(QuoteFormIntent.Submit)

        val state = viewModel.uiState.value
        assertNull(state.submittedQuote)
        assertEquals(SubmissionStatus.Idle, state.submissionStatus)
    }

    // ── Cycle de vie de la soumission : Idle -> Loading -> Success ───────────

    @Test
    fun submit_withValidMultiLineForm_transitionsThroughLoadingToSuccess() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val useCase = SubmitQuoteUseCase(MockQuoteRepository(simulatedDelayMillis = 1_500L))
        val viewModel = QuoteFormViewModel(submitQuoteUseCase = useCase, dispatcher = dispatcher)
        viewModel.processIntent(QuoteFormIntent.AddLine)
        fillHeader(viewModel)
        fillLine(viewModel, 0, "Conseil", "1", "100.00", VatRate.TAUX_NORMAL)
        fillLine(viewModel, 1, "Livre", "1", "20.00", VatRate.TAUX_REDUIT)

        viewModel.processIntent(QuoteFormIntent.Submit)

        runCurrent()
        assertEquals(SubmissionStatus.Loading, viewModel.uiState.value.submissionStatus)
        assertFalse(viewModel.uiState.value.isFormEnabled)
        assertNull(viewModel.uiState.value.submittedQuote)

        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(SubmissionStatus.Success, state.submissionStatus)
        assertTrue(state.isFormEnabled)
        val quote = state.submittedQuote
        assertNotNull(quote)
        assertEquals("2026-09-03", quote.validityDate)
        assertEquals(2, quote.lines.size)
        assertEquals(12000L, quote.totalHt.cents)
    }

    // ── Cycle de vie de la soumission : Idle -> Loading -> Error ─────────────

    @Test
    fun submit_whenRepositoryFails_transitionsThroughLoadingToError() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val useCase = SubmitQuoteUseCase(
            MockQuoteRepository(simulatedDelayMillis = 500L, simulateFailure = true)
        )
        val viewModel = QuoteFormViewModel(submitQuoteUseCase = useCase, dispatcher = dispatcher)
        fillValidSingleLineForm(viewModel)

        viewModel.processIntent(QuoteFormIntent.Submit)
        runCurrent()
        assertEquals(SubmissionStatus.Loading, viewModel.uiState.value.submissionStatus)

        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertIs<SubmissionStatus.Error>(state.submissionStatus)
        assertTrue(state.isFormEnabled)
        assertNull(state.submittedQuote)
    }

    // ── Sélecteur client (US-11) — parité avec le formulaire de facture ──────

    // SIRET Luhn-valides (voir LuhnChecksum) : la création rapide contrôle la clé, pas seulement
    // la longueur — les SIRET historiques des fixtures ne la satisfont pas.
    private val boulangerie = Party("Boulangerie Moreau SARL", "784102336", "78410233600004", "compta@moreau.fr")
    private val bouchon = Party("Bouchon Lyonnais SAS", "732829320", "73282932000074", "contact@bouchon.fr")

    private fun viewModelWithDirectory(vararg clients: Party): Pair<QuoteFormViewModel, InMemoryClientRepository> {
        val repository = InMemoryClientRepository(clients.toList())
        val viewModel = QuoteFormViewModel(
            dispatcher = UnconfinedTestDispatcher(),
            clientRepository = repository,
        )
        return viewModel to repository
    }

    @Test
    fun clientPicker_offersEveryKnownClient_beforeAnyKeystroke() = runTest {
        val (viewModel, _) = viewModelWithDirectory(boulangerie, bouchon)

        assertEquals(2, viewModel.uiState.value.clientSuggestions.size)
        assertNull(viewModel.uiState.value.selectedClient)
    }

    @Test
    fun typingAPrefix_narrowsTheSuggestions_caseInsensitively() = runTest {
        val (viewModel, _) = viewModelWithDirectory(boulangerie, bouchon)

        viewModel.processIntent(QuoteFormIntent.OnClientQueryChanged("BOULANGERIE"))

        assertEquals(
            listOf("Boulangerie Moreau SARL"),
            viewModel.uiState.value.clientSuggestions.map { it.name },
        )
        assertTrue(viewModel.uiState.value.isClientDropdownExpanded)
    }

    @Test
    fun selectingAClient_fillsRecipientNameSirenAndSiret() = runTest {
        val (viewModel, _) = viewModelWithDirectory(boulangerie)

        viewModel.processIntent(QuoteFormIntent.OnClientSelected(boulangerie))

        val state = viewModel.uiState.value
        assertEquals("Boulangerie Moreau SARL", state.recipientName)
        assertEquals("Boulangerie Moreau SARL", state.clientQuery)
        // Le SIREN vient de la fiche, il n'est pas dérivé du SIRET.
        assertEquals("784102336", state.recipientSiren)
        assertEquals("78410233600004", state.recipientSiret)
        assertEquals(boulangerie, state.selectedClient)
        assertFalse(state.isClientDropdownExpanded)
        assertNull(state.errors[QuoteFormField.RECIPIENT_NAME])
        assertNull(state.errors[QuoteFormField.RECIPIENT_SIREN])
        assertNull(state.errors[QuoteFormField.RECIPIENT_SIRET])
    }

    @Test
    fun editingSiretByHand_afterASelection_breaksTheLinkToTheRecord() = runTest {
        val (viewModel, _) = viewModelWithDirectory(boulangerie)
        viewModel.processIntent(QuoteFormIntent.OnClientSelected(boulangerie))
        assertNotNull(viewModel.uiState.value.selectedClient)

        viewModel.processIntent(QuoteFormIntent.RecipientSiretChanged("78410233600099"))

        assertEquals("78410233600099", viewModel.uiState.value.recipientSiret)
        assertNull(viewModel.uiState.value.selectedClient)
    }

    @Test
    fun legacyRecipientNameChanged_behavesAsAQuery() = runTest {
        val (viewModel, _) = viewModelWithDirectory(boulangerie, bouchon)

        viewModel.processIntent(QuoteFormIntent.RecipientNameChanged("Bou"))

        val state = viewModel.uiState.value
        assertEquals("Bou", state.clientQuery)
        assertEquals("Bou", state.recipientName)
        assertEquals(2, state.clientSuggestions.size)
    }

    @Test
    fun addButton_appearsOnlyForAnUnknownNonEmptyQuery() = runTest {
        val (viewModel, _) = viewModelWithDirectory(boulangerie)

        assertFalse(viewModel.uiState.value.showAddNewClientButton, "requête vide")

        viewModel.processIntent(QuoteFormIntent.OnClientQueryChanged("Boul"))
        assertFalse(viewModel.uiState.value.showAddNewClientButton, "des suggestions subsistent")

        viewModel.processIntent(QuoteFormIntent.OnClientQueryChanged("Client Inconnu SAS"))
        assertTrue(viewModel.uiState.value.showAddNewClientButton, "aucune fiche ne correspond")
    }

    @Test
    fun withoutADirectory_theRecipientFieldStaysAFreeInput() = runTest {
        val viewModel = QuoteFormViewModel(dispatcher = UnconfinedTestDispatcher())

        viewModel.processIntent(QuoteFormIntent.RecipientNameChanged("Client Manuel SARL"))

        val state = viewModel.uiState.value
        assertEquals("Client Manuel SARL", state.recipientName)
        assertTrue(state.clientSuggestions.isEmpty())
        assertFalse(state.showAddNewClientButton)
    }

    // ── Création rapide depuis un devis ─────────────────────────────────────

    @Test
    fun openingTheQuickDialog_prefillsTheNameWithTheTypedText() = runTest {
        val (viewModel, _) = viewModelWithDirectory(boulangerie)
        viewModel.processIntent(QuoteFormIntent.OnClientQueryChanged("  Client Inconnu SAS  "))

        viewModel.processIntent(QuoteFormIntent.OnOpenQuickClientDialog)

        val state = viewModel.uiState.value
        assertTrue(state.showQuickClientDialog)
        assertEquals("Client Inconnu SAS", state.quickClientDraft?.name)
    }

    @Test
    fun savingAValidQuickClient_persistsSelectsAndClosesTheDialog() = runTest {
        val (viewModel, repository) = viewModelWithDirectory()
        viewModel.processIntent(QuoteFormIntent.OnClientQueryChanged("Nouveau Client SAS"))
        viewModel.processIntent(QuoteFormIntent.OnOpenQuickClientDialog)

        viewModel.processIntent(
            QuoteFormIntent.OnSaveQuickClient("Nouveau Client SAS", "73282932000074", "contact@nouveau.fr"),
        )

        val state = viewModel.uiState.value
        assertFalse(state.showQuickClientDialog)
        assertNull(state.quickClientDraft)
        assertEquals("Nouveau Client SAS", state.recipientName)
        assertEquals("732829320", state.recipientSiren)
        assertEquals("73282932000074", state.recipientSiret)
        assertNotNull(state.selectedClient)
        assertEquals("Nouveau Client SAS", repository.clients.single().name)
    }

    @Test
    fun savingWithASiretFailingTheLuhnKey_isRefused_andKeepsTheDialogOpen() = runTest {
        val (viewModel, repository) = viewModelWithDirectory()
        viewModel.processIntent(QuoteFormIntent.OnOpenQuickClientDialog)

        // 14 chiffres, mais clé de Luhn fausse — la seule longueur ne suffit pas.
        viewModel.processIntent(
            QuoteFormIntent.OnSaveQuickClient("Nouveau Client SAS", "78410233600022", "contact@nouveau.fr"),
        )

        val state = viewModel.uiState.value
        assertTrue(state.showQuickClientDialog, "la modale reste ouverte pour corriger")
        assertNotNull(state.quickClientDraft?.errors?.get(QuickClientField.SIRET))
        assertTrue(repository.clients.isEmpty())
    }

    @Test
    fun savingAnAlreadyKnownSiret_isRefused_withoutOverwritingTheRecord() = runTest {
        val (viewModel, repository) = viewModelWithDirectory(boulangerie)
        viewModel.processIntent(QuoteFormIntent.OnOpenQuickClientDialog)

        viewModel.processIntent(
            QuoteFormIntent.OnSaveQuickClient("Doublon SARL", boulangerie.siret, "doublon@test.fr"),
        )

        val state = viewModel.uiState.value
        assertTrue(state.showQuickClientDialog)
        assertNotNull(state.quickClientDraft?.errors?.get(QuickClientField.SIRET))
        assertEquals(1, repository.clients.size)
        assertEquals("Boulangerie Moreau SARL", repository.clients.single().name)
    }

    @Test
    fun dismissingTheQuickDialog_persistsNothing() = runTest {
        val (viewModel, repository) = viewModelWithDirectory()
        viewModel.processIntent(QuoteFormIntent.OnClientQueryChanged("Client Inconnu SAS"))
        viewModel.processIntent(QuoteFormIntent.OnOpenQuickClientDialog)

        viewModel.processIntent(QuoteFormIntent.OnDismissQuickClientDialog)

        assertFalse(viewModel.uiState.value.showQuickClientDialog)
        assertNull(viewModel.uiState.value.quickClientDraft)
        assertTrue(repository.clients.isEmpty())
    }

    // ── Validation progressive & Actions Brouillon vs Finaliser ───────────────

    @Test
    fun initialState_calculatesErrors_butShowsNoneInVisibleErrors() {
        val viewModel = QuoteFormViewModel()
        val state = viewModel.uiState.value

        // Le calcul brut contient des erreurs (formulaire vide)
        assertTrue(state.errors.isNotEmpty())
        assertFalse(state.isSubmitEnabled)

        // Mais la vue n'en présente aucune
        assertTrue(state.visibleErrors.isEmpty())
        assertTrue(state.lines.all { it.visibleErrors(false).isEmpty() })
    }

    @Test
    fun typingInField_marksItTouched_andRevealsOnlyItsError() {
        val viewModel = QuoteFormViewModel()
        viewModel.processIntent(QuoteFormIntent.QuoteNumberChanged(""))

        val state = viewModel.uiState.value
        assertEquals(1, state.visibleErrors.size)
        assertNotNull(state.visibleErrors[QuoteFormField.QUOTE_NUMBER])
        assertNull(state.visibleErrors[QuoteFormField.ISSUE_DATE])
        assertNull(state.visibleErrors[QuoteFormField.RECIPIENT_NAME])
    }

    @Test
    fun clickingFinalizeOnEmptyForm_setsSubmitAttempted_revealingAllErrors() {
        val viewModel = QuoteFormViewModel()
        viewModel.processIntent(QuoteFormIntent.FinalizeQuote)

        val state = viewModel.uiState.value
        assertTrue(state.submitAttempted)
        assertTrue(state.visibleErrors.isNotEmpty())
        assertEquals(state.errors, state.visibleErrors)
    }

    @Test
    fun saveDraft_persistsWithDraftStatus_andFinalizeWithSentStatus() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val mockRepo = MockQuoteRepository(simulatedDelayMillis = 100L)
        val viewModel = QuoteFormViewModel(submitQuoteUseCase = SubmitQuoteUseCase(mockRepo), dispatcher = dispatcher)

        fillValidSingleLineForm(viewModel)

        // Sauvegarde Brouillon
        viewModel.processIntent(QuoteFormIntent.SaveDraft)
        advanceUntilIdle()

        val draftQuote = viewModel.uiState.value.submittedQuote
        assertNotNull(draftQuote)
        assertEquals(com.ledgerhub.domain.quote.QuoteStatus.DRAFT, draftQuote.status)

        // Finalisation
        viewModel.processIntent(QuoteFormIntent.FinalizeQuote)
        advanceUntilIdle()

        val finalizedQuote = viewModel.uiState.value.submittedQuote
        assertNotNull(finalizedQuote)
        assertEquals(com.ledgerhub.domain.quote.QuoteStatus.SENT, finalizedQuote.status)
    }
}

