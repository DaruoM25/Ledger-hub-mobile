package com.ledgerhub.presentation.creditnoteform

import com.ledgerhub.data.creditnote.MockCreditNoteRepository
import com.ledgerhub.domain.creditnote.SubmitCreditNoteUseCase
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.presentation.invoiceform.SubmissionStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Tests QA (Skill 2) — vérifient en priorité l'inversion des montants de la facture source. */
@OptIn(ExperimentalCoroutinesApi::class)
class CreditNoteFormViewModelTest {

    private val issuer = Party("Vendeur SARL", "123456789", "12345678900012")
    private val recipient = Party("Client SAS", "987654321", "98765432100045")

    private fun invoice(
        status: InvoiceStatus = InvoiceStatus.VALIDATED,
        lines: List<InvoiceLine> = listOf(
            InvoiceLine("Conseil", quantity = 1, unitPriceHt = Money(10000), vatRate = VatRate.TAUX_NORMAL),
            InvoiceLine("Livre", quantity = 1, unitPriceHt = Money(2000), vatRate = VatRate.TAUX_REDUIT),
        ),
    ) = Invoice(
        number = "F-2026-042",
        issueDate = "2026-08-01",
        issuer = issuer,
        recipient = recipient,
        lines = lines,
        status = status,
    )

    // ── Inversion des montants — cœur de la règle fiscale de l'avoir ────────

    @Test
    fun initialState_prefillsInvertedAmounts_fromSourceInvoice() {
        val source = invoice()
        val viewModel = CreditNoteFormViewModel(sourceInvoice = source)

        val state = viewModel.uiState.value
        assertEquals(source.number, state.invoiceId)
        assertEquals(source.issuer.name, state.issuerName)
        assertEquals(source.recipient.name, state.recipientName)
        // HT = 120.00, TVA = 21.10, TTC = 141.10 côté facture -> négatifs côté avoir.
        assertEquals(-source.totalHt.cents, state.totalHt.cents)
        assertEquals(-source.totalVat.cents, state.totalVat.cents)
        assertEquals(-source.totalTtc.cents, state.totalTtc.cents)
        assertTrue(state.totalHt.cents < 0)
        assertTrue(state.totalVat.cents < 0)
        assertTrue(state.totalTtc.cents < 0)
    }

    @Test
    fun invertedAmounts_areNeverEditableByUserInput() {
        // Aucun intent ne permet de modifier les montants — ils restent ceux calculés à la
        // construction, quel que soit ce que l'utilisateur saisit par ailleurs.
        val source = invoice()
        val viewModel = CreditNoteFormViewModel(sourceInvoice = source)

        viewModel.processIntent(CreditNoteFormIntent.ReasonChanged("Erreur tarifaire"))

        val state = viewModel.uiState.value
        assertEquals(-source.totalTtc.cents, state.totalTtc.cents)
    }

    // ── État initial et validation ───────────────────────────────────────────

    @Test
    fun initialState_hasValidationErrors_andSubmitDisabled() {
        val viewModel = CreditNoteFormViewModel(sourceInvoice = invoice())
        val state = viewModel.uiState.value
        assertFalse(state.isSubmitEnabled)
        assertTrue(state.errors.isNotEmpty())
        assertEquals(SubmissionStatus.Idle, state.submissionStatus)
    }

    @Test
    fun blankReason_producesFieldError_andBlocksSubmit() {
        val viewModel = CreditNoteFormViewModel(sourceInvoice = invoice())
        viewModel.processIntent(CreditNoteFormIntent.IssueDateChanged("2026-08-06"))

        val state = viewModel.uiState.value
        assertNotNull(state.errors[CreditNoteFormField.REASON])
        assertFalse(state.isSubmitEnabled)
    }

    @Test
    fun filledReason_clearsFieldError() {
        val viewModel = CreditNoteFormViewModel(sourceInvoice = invoice())
        viewModel.processIntent(CreditNoteFormIntent.ReasonChanged("Erreur tarifaire"))
        assertNull(viewModel.uiState.value.errors[CreditNoteFormField.REASON])
    }

    @Test
    fun invalidIssueDate_producesFieldError() {
        val viewModel = CreditNoteFormViewModel(sourceInvoice = invoice())
        viewModel.processIntent(CreditNoteFormIntent.IssueDateChanged("not-a-date"))
        assertNotNull(viewModel.uiState.value.errors[CreditNoteFormField.ISSUE_DATE])
    }

    // ── Soumission invalide : aucun avoir produit ────────────────────────────

    @Test
    fun submit_withInvalidForm_staysIdle_doesNotProduceCreditNote() {
        val viewModel = CreditNoteFormViewModel(sourceInvoice = invoice())
        viewModel.processIntent(CreditNoteFormIntent.Submit)

        val state = viewModel.uiState.value
        assertNull(state.submittedCreditNote)
        assertEquals(SubmissionStatus.Idle, state.submissionStatus)
    }

    // ── Cycle de vie de la soumission : Idle -> Loading -> Success ───────────

    @Test
    fun submit_withValidForm_transitionsThroughLoadingToSuccess_withNegativeAmounts() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val source = invoice()
        val useCase = SubmitCreditNoteUseCase(MockCreditNoteRepository(simulatedDelayMillis = 1_500L))
        val viewModel = CreditNoteFormViewModel(sourceInvoice = source, submitCreditNoteUseCase = useCase, dispatcher = dispatcher)
        // Le numéro d'avoir est attribué par la séquence dans l'init du ViewModel : on laisse
        // cette coroutine s'exécuter avant toute saisie, sinon le formulaire reste invalide.
        advanceUntilIdle()

        viewModel.processIntent(CreditNoteFormIntent.IssueDateChanged("2026-08-06"))
        viewModel.processIntent(CreditNoteFormIntent.ReasonChanged("Erreur tarifaire"))
        viewModel.processIntent(CreditNoteFormIntent.Submit)

        runCurrent()
        assertEquals(SubmissionStatus.Loading, viewModel.uiState.value.submissionStatus)
        assertFalse(viewModel.uiState.value.isFormEnabled)
        assertNull(viewModel.uiState.value.submittedCreditNote)

        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(SubmissionStatus.Success, state.submissionStatus)
        val creditNote = state.submittedCreditNote
        assertNotNull(creditNote)
        assertEquals(source.number, creditNote.invoiceId)
        assertEquals("Erreur tarifaire", creditNote.reason)
        assertEquals(-source.totalHt.cents, creditNote.totalHt.cents)
        assertEquals(-source.totalVat.cents, creditNote.totalVat.cents)
        assertEquals(-source.totalTtc.cents, creditNote.totalTtc.cents)
    }

    // ── Cycle de vie de la soumission : Idle -> Loading -> Error ─────────────

    @Test
    fun submit_whenRepositoryFails_transitionsThroughLoadingToError() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val useCase = SubmitCreditNoteUseCase(
            MockCreditNoteRepository(simulatedDelayMillis = 500L, simulateFailure = true)
        )
        val viewModel = CreditNoteFormViewModel(sourceInvoice = invoice(), submitCreditNoteUseCase = useCase, dispatcher = dispatcher)
        // Le numéro d'avoir est attribué par la séquence dans l'init du ViewModel : on laisse
        // cette coroutine s'exécuter avant toute saisie, sinon le formulaire reste invalide.
        advanceUntilIdle()
        viewModel.processIntent(CreditNoteFormIntent.IssueDateChanged("2026-08-06"))
        viewModel.processIntent(CreditNoteFormIntent.ReasonChanged("Erreur tarifaire"))

        viewModel.processIntent(CreditNoteFormIntent.Submit)
        runCurrent()
        assertEquals(SubmissionStatus.Loading, viewModel.uiState.value.submissionStatus)

        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertTrue(state.submissionStatus is SubmissionStatus.Error)
        assertTrue(state.isFormEnabled)
        assertNull(state.submittedCreditNote)
    }

    // ── QA manuelle (campagne 1) : le bouton ne doit plus être réactivable après succès ──

    @Test
    fun submit_afterSuccess_disablesSubmitButton_preventingResubmission() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val useCase = SubmitCreditNoteUseCase(MockCreditNoteRepository(simulatedDelayMillis = 0L))
        val viewModel = CreditNoteFormViewModel(sourceInvoice = invoice(), submitCreditNoteUseCase = useCase, dispatcher = dispatcher)
        // Le numéro d'avoir est attribué par la séquence dans l'init du ViewModel : on laisse
        // cette coroutine s'exécuter avant toute saisie, sinon le formulaire reste invalide.
        advanceUntilIdle()
        viewModel.processIntent(CreditNoteFormIntent.IssueDateChanged("2026-08-06"))
        viewModel.processIntent(CreditNoteFormIntent.ReasonChanged("Erreur tarifaire"))
        viewModel.processIntent(CreditNoteFormIntent.Submit)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(SubmissionStatus.Success, state.submissionStatus)
        assertFalse(state.isSubmitEnabled)
    }

    // ── Règle métier : facture non finalisable -> soumission refusée par le use case ──

    @Test
    fun submit_forDraftSourceInvoice_producesError_viaCreateCreditNoteUseCase() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = CreditNoteFormViewModel(sourceInvoice = invoice(status = InvoiceStatus.DRAFT), dispatcher = dispatcher)
        // Le numéro d'avoir est attribué par la séquence dans l'init du ViewModel : on laisse
        // cette coroutine s'exécuter avant toute saisie, sinon le formulaire reste invalide.
        advanceUntilIdle()
        viewModel.processIntent(CreditNoteFormIntent.IssueDateChanged("2026-08-06"))
        viewModel.processIntent(CreditNoteFormIntent.ReasonChanged("Erreur tarifaire"))

        viewModel.processIntent(CreditNoteFormIntent.Submit)
        runCurrent()

        val state = viewModel.uiState.value
        assertTrue(state.submissionStatus is SubmissionStatus.Error)
        assertNull(state.submittedCreditNote)
    }

    // ── Numérotation et refus du second avoir (US-05) ────────────────────────

    @Test
    fun init_assignsTheSequentialNumber_withoutAnyUserInput() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = MockCreditNoteRepository(simulatedDelayMillis = 0L)
        val viewModel = CreditNoteFormViewModel(
            sourceInvoice = invoice(),
            creditNoteRepository = repository,
            dispatcher = dispatcher,
        )

        advanceUntilIdle()

        // Exercice déduit de la facture annulée (commonMain n'a pas d'horloge — voir le ViewModel).
        assertEquals("AV-2026-0001", viewModel.uiState.value.creditNoteNumber)
        assertNull(viewModel.uiState.value.errors[CreditNoteFormField.CREDIT_NOTE_NUMBER])
    }

    @Test
    fun init_blocksTheForm_whenTheInvoiceAlreadyCarriesACreditNote() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = MockCreditNoteRepository(simulatedDelayMillis = 0L)
        val source = invoice()
        // Un premier avoir existe déjà pour cette facture.
        repository.submitCreditNote(
            com.ledgerhub.domain.creditnote.CreateCreditNoteUseCase()(
                source, "AV-2026-0007", "2026-08-06", "Premier motif",
            ).getOrThrow(),
        )

        val viewModel = CreditNoteFormViewModel(
            sourceInvoice = source,
            creditNoteRepository = repository,
            dispatcher = dispatcher,
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("AV-2026-0007", state.blockedByExistingCreditNote)
        assertFalse(state.isFormEnabled, "Le formulaire est neutralisé d'emblée")
        assertFalse(state.isSubmitEnabled)
    }

    @Test
    fun submit_onAnAlreadyCreditedInvoice_persistsNothing() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = MockCreditNoteRepository(simulatedDelayMillis = 0L)
        val source = invoice()
        repository.submitCreditNote(
            com.ledgerhub.domain.creditnote.CreateCreditNoteUseCase()(
                source, "AV-2026-0007", "2026-08-06", "Premier motif",
            ).getOrThrow(),
        )
        val viewModel = CreditNoteFormViewModel(
            sourceInvoice = source,
            creditNoteRepository = repository,
            dispatcher = dispatcher,
        )
        advanceUntilIdle()

        viewModel.processIntent(CreditNoteFormIntent.IssueDateChanged("2026-08-07"))
        viewModel.processIntent(CreditNoteFormIntent.ReasonChanged("Second motif"))
        viewModel.processIntent(CreditNoteFormIntent.Submit)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.submittedCreditNote)
        assertEquals(1, repository.fetchCreditNotes().getOrThrow().size)
    }
}
