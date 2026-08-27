package com.ledgerhub.presentation.quoteform

import com.ledgerhub.data.quote.MockQuoteRepository
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.domain.quote.SubmitQuoteUseCase
import com.ledgerhub.presentation.invoiceform.SubmissionStatus
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
}
