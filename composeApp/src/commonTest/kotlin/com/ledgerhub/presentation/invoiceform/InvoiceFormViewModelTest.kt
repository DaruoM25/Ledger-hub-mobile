package com.ledgerhub.presentation.invoiceform

import com.ledgerhub.data.invoice.MockInvoiceRepository
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.NatureOperation
import com.ledgerhub.domain.invoice.SubmitInvoiceUseCase
import com.ledgerhub.domain.invoice.TransactionMode
import com.ledgerhub.domain.invoice.VatRate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
class InvoiceFormViewModelTest {

    private val validClientSiret = "98765432100045"

    /** Renseigne l'en-tête (client + détails facture) avec des valeurs valides. */
    private fun fillHeader(viewModel: InvoiceFormViewModel) {
        viewModel.processIntent(InvoiceFormIntent.InvoiceNumberChanged("F-2026-001"))
        viewModel.processIntent(InvoiceFormIntent.IssueDateChanged("2026-08-03"))
        viewModel.processIntent(InvoiceFormIntent.DueDateChanged("2026-09-03"))
        viewModel.processIntent(InvoiceFormIntent.ClientNameChanged("Client SAS"))
        viewModel.processIntent(InvoiceFormIntent.ClientSiretChanged(validClientSiret))
        viewModel.processIntent(InvoiceFormIntent.ClientEmailChanged("compta@client-sas.fr"))
    }

    private fun fillLine(
        viewModel: InvoiceFormViewModel,
        index: Int,
        label: String,
        quantity: String,
        unitPriceHt: String,
        vatRate: VatRate = VatRate.TAUX_NORMAL,
    ) {
        viewModel.processIntent(InvoiceFormIntent.UpdateLine(index, label, quantity, unitPriceHt, vatRate))
    }

    /** Formulaire complet valide, avec la ligne 0 déjà présente par défaut. */
    private fun fillValidSingleLineForm(viewModel: InvoiceFormViewModel) {
        fillHeader(viewModel)
        fillLine(viewModel, index = 0, label = "Prestation de conseil", quantity = "2", unitPriceHt = "50.00")
    }

    // ── État initial ──────────────────────────────────────────────────────────

    @Test
    fun initialState_hasOneEmptyLine_andValidationErrors_andSubmitDisabled() {
        val viewModel = InvoiceFormViewModel()
        val state = viewModel.uiState.value
        assertEquals(1, state.lines.size)
        assertFalse(state.isSubmitEnabled)
        assertTrue(state.errors.isNotEmpty())
        assertTrue(state.generateFacturX) // Factur-X activé par défaut
        assertEquals(SubmissionStatus.Idle, state.submissionStatus)
    }

    // ── Validation client (en-tête) ──────────────────────────────────────────

    @Test
    fun invalidSiret_producesFieldError() {
        val viewModel = InvoiceFormViewModel()
        viewModel.processIntent(InvoiceFormIntent.ClientSiretChanged("123")) // pas 14 chiffres
        assertNotNull(viewModel.uiState.value.errors[InvoiceFormField.CLIENT_SIRET])
    }

    @Test
    fun validSiret_exactly14Digits_clearsError() {
        val viewModel = InvoiceFormViewModel()
        viewModel.processIntent(InvoiceFormIntent.ClientSiretChanged("1234567890123"))  // 13 -> erreur
        assertNotNull(viewModel.uiState.value.errors[InvoiceFormField.CLIENT_SIRET])
        viewModel.processIntent(InvoiceFormIntent.ClientSiretChanged("12345678901234")) // 14 -> ok
        assertNull(viewModel.uiState.value.errors[InvoiceFormField.CLIENT_SIRET])
    }

    @Test
    fun invalidEmail_producesFieldError() {
        val viewModel = InvoiceFormViewModel()
        viewModel.processIntent(InvoiceFormIntent.ClientEmailChanged("pas-un-email"))
        assertNotNull(viewModel.uiState.value.errors[InvoiceFormField.CLIENT_EMAIL])
        viewModel.processIntent(InvoiceFormIntent.ClientEmailChanged("valide@client.fr"))
        assertNull(viewModel.uiState.value.errors[InvoiceFormField.CLIENT_EMAIL])
    }

    @Test
    fun blankDueDate_producesFieldError() {
        val viewModel = InvoiceFormViewModel()
        assertNotNull(viewModel.uiState.value.errors[InvoiceFormField.DUE_DATE])
        viewModel.processIntent(InvoiceFormIntent.DueDateChanged("2026-09-30"))
        assertNull(viewModel.uiState.value.errors[InvoiceFormField.DUE_DATE])
    }

    // ── Multi-lignes : ajout ─────────────────────────────────────────────────

    @Test
    fun addLine_appendsEmptyLine() {
        val viewModel = InvoiceFormViewModel()
        viewModel.processIntent(InvoiceFormIntent.AddLine)
        viewModel.processIntent(InvoiceFormIntent.AddLine)
        assertEquals(3, viewModel.uiState.value.lines.size)
    }

    @Test
    fun threeLines_computeExactAggregatedTotal() {
        val viewModel = InvoiceFormViewModel()
        viewModel.processIntent(InvoiceFormIntent.AddLine)
        viewModel.processIntent(InvoiceFormIntent.AddLine)

        fillLine(viewModel, 0, "Conseil", "1", "100.00", VatRate.TAUX_NORMAL)      // 100.00 HT, TVA 20%
        fillLine(viewModel, 1, "Formation", "2", "25.00", VatRate.TAUX_NORMAL)     // 50.00 HT, TVA 20%
        fillLine(viewModel, 2, "Livre", "1", "20.00", VatRate.TAUX_REDUIT)         // 20.00 HT, TVA 5.5%

        val state = viewModel.uiState.value
        // HT = 170.00 ; TVA = (150 * 20%) + (20 * 5.5%) = 30.00 + 1.10 = 31.10 ; TTC = 201.10
        assertEquals(17000L, state.totalHt.cents)
        assertEquals(3110L, state.totalVat.cents)
        assertEquals(20110L, state.totalTtc.cents)
    }

    // ── Multi-lignes : suppression ───────────────────────────────────────────

    @Test
    fun removeLine_updatesTotalsInstantly() {
        val viewModel = InvoiceFormViewModel()
        viewModel.processIntent(InvoiceFormIntent.AddLine)
        fillLine(viewModel, 0, "Conseil", "1", "100.00")
        fillLine(viewModel, 1, "Formation", "1", "50.00")
        assertEquals(15000L, viewModel.uiState.value.totalHt.cents)

        viewModel.processIntent(InvoiceFormIntent.RemoveLine(1))

        val state = viewModel.uiState.value
        assertEquals(1, state.lines.size)
        assertEquals(10000L, state.totalHt.cents)
    }

    @Test
    fun removeLine_lastRemainingLine_isBlocked() {
        val viewModel = InvoiceFormViewModel()
        viewModel.processIntent(InvoiceFormIntent.RemoveLine(0))
        assertEquals(1, viewModel.uiState.value.lines.size)
    }

    // ── Validation par ligne ─────────────────────────────────────────────────

    @Test
    fun lineWithBlankLabel_producesLineError_andBlocksSubmit() {
        val viewModel = InvoiceFormViewModel()
        fillHeader(viewModel)
        fillLine(viewModel, 0, label = "", quantity = "1", unitPriceHt = "10.00")

        val state = viewModel.uiState.value
        assertNotNull(state.lines[0].errors[InvoiceLineField.LABEL])
        assertFalse(state.isSubmitEnabled)
    }

    @Test
    fun lineWithNegativeQuantity_producesLineError() {
        val viewModel = InvoiceFormViewModel()
        fillLine(viewModel, 0, label = "Prestation", quantity = "-1", unitPriceHt = "10.00")

        assertNotNull(viewModel.uiState.value.lines[0].errors[InvoiceLineField.QUANTITY])
    }

    @Test
    fun oneInvalidLineAmongValidOnes_blocksGlobalSubmit_butOthersStillContributeToTotal() {
        val viewModel = InvoiceFormViewModel()
        viewModel.processIntent(InvoiceFormIntent.AddLine)
        fillHeader(viewModel)
        fillLine(viewModel, 0, "Conseil valide", "1", "100.00")
        fillLine(viewModel, 1, "", "1", "50.00") // ligne 1 invalide (libellé vide)

        val state = viewModel.uiState.value
        assertFalse(state.isSubmitEnabled)
        assertNotNull(state.lines[1].errors[InvoiceLineField.LABEL])
        assertEquals(10000L, state.totalHt.cents) // la ligne valide contribue seule
    }

    // ── Soumission invalide : aucun appel au repository ──────────────────────

    @Test
    fun submit_withInvalidForm_staysIdle_doesNotProduceInvoice() {
        val viewModel = InvoiceFormViewModel()
        viewModel.processIntent(InvoiceFormIntent.ValidateAndIssue)

        val state = viewModel.uiState.value
        assertNull(state.submittedInvoice)
        assertEquals(SubmissionStatus.Idle, state.submissionStatus)
    }

    @Test
    fun submit_withOneInvalidLineAmongValidForm_isBlocked() {
        val viewModel = InvoiceFormViewModel()
        viewModel.processIntent(InvoiceFormIntent.AddLine)
        fillHeader(viewModel)
        fillLine(viewModel, 0, "Conseil valide", "1", "100.00")
        fillLine(viewModel, 1, "", "1", "50.00") // invalide

        viewModel.processIntent(InvoiceFormIntent.ValidateAndIssue)

        assertNull(viewModel.uiState.value.submittedInvoice)
        assertEquals(SubmissionStatus.Idle, viewModel.uiState.value.submissionStatus)
    }

    // ── Cycle de soumission : Idle -> Loading -> Success (multi-lignes) ───────

    @Test
    fun submit_withValidMultiLineForm_transitionsThroughLoadingToSuccess() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val useCase = SubmitInvoiceUseCase(MockInvoiceRepository(simulatedDelayMillis = 1_500L))
        val viewModel = InvoiceFormViewModel(submitInvoiceUseCase = useCase, dispatcher = dispatcher)
        viewModel.processIntent(InvoiceFormIntent.AddLine)
        fillHeader(viewModel)
        fillLine(viewModel, 0, "Conseil", "1", "100.00", VatRate.TAUX_NORMAL)
        fillLine(viewModel, 1, "Livre", "1", "20.00", VatRate.TAUX_REDUIT)

        viewModel.processIntent(InvoiceFormIntent.ValidateAndIssue)

        runCurrent()
        assertEquals(SubmissionStatus.Loading, viewModel.uiState.value.submissionStatus)
        assertFalse(viewModel.uiState.value.isFormEnabled)
        assertFalse(viewModel.uiState.value.isSubmitEnabled)
        assertNull(viewModel.uiState.value.submittedInvoice)

        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(SubmissionStatus.Success, state.submissionStatus)
        assertTrue(state.isFormEnabled)
        val invoice = state.submittedInvoice
        assertNotNull(invoice)
        assertEquals(2, invoice.lines.size)
        assertEquals(12000L, invoice.totalHt.cents) // 100.00 + 20.00
        // L'émetteur est l'identité fixe du cabinet ; le client saisi devient le destinataire.
        assertEquals(CabinetIdentity.party, invoice.issuer)
        assertEquals("Client SAS", invoice.recipient.name)
        assertEquals(validClientSiret, invoice.recipient.siret)
        assertEquals("compta@client-sas.fr", invoice.recipient.email)
        assertEquals("2026-09-03", invoice.dueDate)
        assertTrue(invoice.facturX)
    }

    @Test
    fun toggleFacturXOff_isCarriedToSubmittedInvoice() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val useCase = SubmitInvoiceUseCase(MockInvoiceRepository(simulatedDelayMillis = 0L))
        val viewModel = InvoiceFormViewModel(submitInvoiceUseCase = useCase, dispatcher = dispatcher)
        fillValidSingleLineForm(viewModel)
        viewModel.processIntent(InvoiceFormIntent.ToggleFacturX(false))

        viewModel.processIntent(InvoiceFormIntent.ValidateAndIssue)
        advanceUntilIdle()

        val invoice = viewModel.uiState.value.submittedInvoice
        assertNotNull(invoice)
        assertFalse(invoice.facturX)
    }

    // ── Cycle de soumission : Idle -> Loading -> Error ───────────────────────

    @Test
    fun submit_whenRepositoryFails_transitionsThroughLoadingToError() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val useCase = SubmitInvoiceUseCase(
            MockInvoiceRepository(simulatedDelayMillis = 500L, simulateFailure = true)
        )
        val viewModel = InvoiceFormViewModel(submitInvoiceUseCase = useCase, dispatcher = dispatcher)
        fillValidSingleLineForm(viewModel)

        viewModel.processIntent(InvoiceFormIntent.ValidateAndIssue)
        runCurrent()
        assertEquals(SubmissionStatus.Loading, viewModel.uiState.value.submissionStatus)

        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertIs<SubmissionStatus.Error>(state.submissionStatus)
        assertTrue(state.isFormEnabled)
        assertNull(state.submittedInvoice)
    }

    // ── État de soumission : isSubmitting (Prompt 3) ─────────────────────────────────────────

    @Test
    fun freshForm_isNotSubmitting() {
        assertFalse(InvoiceFormViewModel().uiState.value.isSubmitting)
    }

    @Test
    fun validateAndIssue_flipsIsSubmittingDuringWrite_thenBackOnSuccess() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val useCase = SubmitInvoiceUseCase(MockInvoiceRepository(simulatedDelayMillis = 1_000L))
        val viewModel = InvoiceFormViewModel(submitInvoiceUseCase = useCase, dispatcher = dispatcher)
        fillValidSingleLineForm(viewModel)
        assertFalse(viewModel.uiState.value.isSubmitting)

        viewModel.processIntent(InvoiceFormIntent.ValidateAndIssue)
        runCurrent()
        assertTrue(viewModel.uiState.value.isSubmitting)
        assertFalse(viewModel.uiState.value.isFormEnabled)

        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isSubmitting)
        assertEquals(SubmissionStatus.Success, viewModel.uiState.value.submissionStatus)
    }

    @Test
    fun saveDraft_flipsIsSubmittingDuringWrite_thenBackOnSuccess() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val useCase = SubmitInvoiceUseCase(MockInvoiceRepository(simulatedDelayMillis = 1_000L))
        val viewModel = InvoiceFormViewModel(submitInvoiceUseCase = useCase, dispatcher = dispatcher)
        fillValidSingleLineForm(viewModel)

        viewModel.processIntent(InvoiceFormIntent.SaveDraft)
        runCurrent()
        assertTrue(viewModel.uiState.value.isSubmitting)

        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun isSubmitting_returnsToFalse_whenTheWriteFails() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val useCase = SubmitInvoiceUseCase(
            MockInvoiceRepository(simulatedDelayMillis = 500L, simulateFailure = true)
        )
        val viewModel = InvoiceFormViewModel(submitInvoiceUseCase = useCase, dispatcher = dispatcher)
        fillValidSingleLineForm(viewModel)

        viewModel.processIntent(InvoiceFormIntent.ValidateAndIssue)
        runCurrent()
        assertTrue(viewModel.uiState.value.isSubmitting)

        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isSubmitting)
        assertIs<SubmissionStatus.Error>(viewModel.uiState.value.submissionStatus)
    }

    @Test
    fun rejectedByValidation_neverEntersSubmittingState() {
        val viewModel = InvoiceFormViewModel()

        viewModel.processIntent(InvoiceFormIntent.ValidateAndIssue)

        assertFalse(viewModel.uiState.value.isSubmitting)
        assertEquals(SubmissionStatus.Idle, viewModel.uiState.value.submissionStatus)
    }

    @Test
    fun secondActionDuringAWrite_isIgnored() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val useCase = SubmitInvoiceUseCase(MockInvoiceRepository(simulatedDelayMillis = 1_000L))
        val viewModel = InvoiceFormViewModel(submitInvoiceUseCase = useCase, dispatcher = dispatcher)
        fillValidSingleLineForm(viewModel)

        viewModel.processIntent(InvoiceFormIntent.ValidateAndIssue)
        runCurrent()
        // Double appui : l'écriture en cours ne doit pas être doublée ni requalifiée en brouillon.
        viewModel.processIntent(InvoiceFormIntent.SaveDraft)
        advanceUntilIdle()

        assertEquals(InvoiceStatus.DEPOSITED, viewModel.uiState.value.submittedInvoice?.status)
    }

    // ── Statut cible par action ──────────────────────────────────────────────────────────────

    @Test
    fun saveDraft_persistsAsDraft() = runTest {
        val viewModel = InvoiceFormViewModel(
            submitInvoiceUseCase = SubmitInvoiceUseCase(MockInvoiceRepository(simulatedDelayMillis = 0L)),
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        fillValidSingleLineForm(viewModel)

        viewModel.processIntent(InvoiceFormIntent.SaveDraft)
        advanceUntilIdle()

        val invoice = assertNotNull(viewModel.uiState.value.submittedInvoice)
        assertEquals(InvoiceStatus.DRAFT, invoice.status)
        assertTrue(invoice.isEditable)
    }

    @Test
    fun validateAndIssue_persistsAsValidated_andLocksTheInvoice() = runTest {
        val viewModel = InvoiceFormViewModel(
            submitInvoiceUseCase = SubmitInvoiceUseCase(MockInvoiceRepository(simulatedDelayMillis = 0L)),
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        fillValidSingleLineForm(viewModel)

        viewModel.processIntent(InvoiceFormIntent.ValidateAndIssue)
        advanceUntilIdle()

        val invoice = assertNotNull(viewModel.uiState.value.submittedInvoice)
        assertEquals(InvoiceStatus.DEPOSITED, invoice.status)
        assertFalse(invoice.isEditable) // immutabilité fiscale
        assertTrue(invoice.isCancellableByCreditNote)
    }

    // ── Révélation progressive des erreurs (anomalie D-02, recette du 29/08/2026) ────────────

    @Test
    fun freshForm_computesErrorsButPresentsNone() {
        val state = InvoiceFormViewModel().uiState.value

        // La validation tourne bien : le formulaire vierge reste non soumettable…
        assertTrue(state.errors.isNotEmpty())
        assertFalse(state.isSubmitEnabled)
        // …mais rien n'est encore présenté à l'utilisateur, qui n'a rien saisi.
        assertTrue(state.visibleErrors.isEmpty())
        assertTrue(state.lines.single().visibleErrors(revealAll = false).isEmpty())
    }

    @Test
    fun editingOneField_revealsOnlyThatFieldsError() {
        val viewModel = InvoiceFormViewModel()

        viewModel.processIntent(InvoiceFormIntent.ClientSiretChanged("123"))

        val visible = viewModel.uiState.value.visibleErrors
        assertEquals(setOf(InvoiceFormField.CLIENT_SIRET), visible.keys)
    }

    @Test
    fun correctingATouchedField_clearsItsVisibleError() {
        val viewModel = InvoiceFormViewModel()

        viewModel.processIntent(InvoiceFormIntent.ClientSiretChanged("123"))
        viewModel.processIntent(InvoiceFormIntent.ClientSiretChanged(validClientSiret))

        assertFalse(viewModel.uiState.value.visibleErrors.containsKey(InvoiceFormField.CLIENT_SIRET))
    }

    @Test
    fun submitAttemptOnEmptyForm_revealsEveryError() {
        val viewModel = InvoiceFormViewModel()

        viewModel.processIntent(InvoiceFormIntent.ValidateAndIssue)

        val state = viewModel.uiState.value
        assertTrue(state.submitAttempted)
        assertEquals(state.errors, state.visibleErrors)
        assertEquals(
            state.lines.single().errors,
            state.lines.single().visibleErrors(revealAll = state.submitAttempted),
        )
    }

    @Test
    fun editingALine_revealsOnlyTheChangedLineField() {
        val viewModel = InvoiceFormViewModel()

        // L'utilisateur saisit un libellé puis l'efface : ce champ devient « touché » et invalide.
        // Le prix unitaire, lui, n'a jamais été saisi.
        fillLine(viewModel, index = 0, label = "Conseil", quantity = "1", unitPriceHt = "")
        fillLine(viewModel, index = 0, label = "", quantity = "1", unitPriceHt = "")

        val line = viewModel.uiState.value.lines.single()
        assertEquals(
            setOf(InvoiceLineField.LABEL),
            line.visibleErrors(revealAll = false).keys,
        )
        assertTrue(line.errors.containsKey(InvoiceLineField.UNIT_PRICE)) // calculée, mais pas présentée
    }

    // ── Réforme 2026 (US-27) ───────────────────────────────────────────────────

    @Test
    fun b2bMode_blankSiren_producesFieldError() {
        val viewModel = InvoiceFormViewModel()
        viewModel.processIntent(InvoiceFormIntent.TransactionModeChanged(TransactionMode.E_INVOICING))
        viewModel.processIntent(InvoiceFormIntent.ClientSirenChanged(""))

        assertNotNull(viewModel.uiState.value.errors[InvoiceFormField.CLIENT_SIREN])
        assertFalse(viewModel.uiState.value.isSubmitEnabled)
    }

    @Test
    fun eReportingMode_blankSiren_clearsFieldError() {
        val viewModel = InvoiceFormViewModel()
        // En B2B, SIREN vide = erreur
        viewModel.processIntent(InvoiceFormIntent.TransactionModeChanged(TransactionMode.E_INVOICING))
        viewModel.processIntent(InvoiceFormIntent.ClientSirenChanged(""))
        assertNotNull(viewModel.uiState.value.errors[InvoiceFormField.CLIENT_SIREN])

        // En e-Reporting, SIREN facultatif = pas d'erreur
        viewModel.processIntent(InvoiceFormIntent.TransactionModeChanged(TransactionMode.E_REPORTING))
        assertNull(viewModel.uiState.value.errors[InvoiceFormField.CLIENT_SIREN])
    }

    @Test
    fun transactionModeSwitch_updatesIsEReporting() {
        val viewModel = InvoiceFormViewModel()
        assertEquals(TransactionMode.E_INVOICING, viewModel.uiState.value.transactionMode)
        assertFalse(viewModel.uiState.value.isEReporting)

        viewModel.processIntent(InvoiceFormIntent.TransactionModeChanged(TransactionMode.E_REPORTING))
        assertEquals(TransactionMode.E_REPORTING, viewModel.uiState.value.transactionMode)
        assertTrue(viewModel.uiState.value.isEReporting)
    }

    @Test
    fun us27_fiscalFieldsAndDeliveryAddress_carriedToSubmittedInvoice() = runTest {
        val viewModel = InvoiceFormViewModel(
            submitInvoiceUseCase = SubmitInvoiceUseCase(MockInvoiceRepository(simulatedDelayMillis = 0L)),
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        fillValidSingleLineForm(viewModel)
        viewModel.processIntent(InvoiceFormIntent.TransactionModeChanged(TransactionMode.E_REPORTING))
        viewModel.processIntent(InvoiceFormIntent.NatureOperationChanged(NatureOperation.MIXTE))
        viewModel.processIntent(InvoiceFormIntent.ToggleOptionTvaDebit(true))
        viewModel.processIntent(InvoiceFormIntent.ToggleDifferentDeliveryAddress(true))
        viewModel.processIntent(InvoiceFormIntent.DeliveryStreetChanged("10 rue de la Paix"))
        viewModel.processIntent(InvoiceFormIntent.DeliveryZipChanged("75002"))
        viewModel.processIntent(InvoiceFormIntent.DeliveryCityChanged("Paris"))
        viewModel.processIntent(InvoiceFormIntent.DeliveryCountryChanged("FRANCE"))

        viewModel.processIntent(InvoiceFormIntent.ValidateAndIssue)
        runCurrent()
        advanceUntilIdle()

        val submitted = assertNotNull(viewModel.uiState.value.submittedInvoice)
        assertTrue(submitted.isEReporting)
        assertEquals(NatureOperation.MIXTE, submitted.natureOperation)
        assertTrue(submitted.optionTvaDebit)
        assertEquals("10 rue de la Paix", submitted.deliveryAddress.street)
        assertEquals("75002", submitted.deliveryAddress.zip)
        assertEquals("Paris", submitted.deliveryAddress.city)
        assertEquals("FRANCE", submitted.deliveryAddress.country)
    }
}
