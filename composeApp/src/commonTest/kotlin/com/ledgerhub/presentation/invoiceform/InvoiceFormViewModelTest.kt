package com.ledgerhub.presentation.invoiceform

import com.ledgerhub.data.invoice.MockInvoiceRepository
import com.ledgerhub.domain.invoice.SubmitInvoiceUseCase
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

    private val validIssuerSiren = "123456789"
    private val validIssuerSiret = "12345678900012"
    private val validRecipientSiren = "987654321"
    private val validRecipientSiret = "98765432100045"

    private fun fillHeader(viewModel: InvoiceFormViewModel) {
        viewModel.processIntent(InvoiceFormIntent.InvoiceNumberChanged("F-2026-001"))
        viewModel.processIntent(InvoiceFormIntent.IssueDateChanged("2026-08-03"))
        viewModel.processIntent(InvoiceFormIntent.IssuerNameChanged("Vendeur SARL"))
        viewModel.processIntent(InvoiceFormIntent.IssuerSirenChanged(validIssuerSiren))
        viewModel.processIntent(InvoiceFormIntent.IssuerSiretChanged(validIssuerSiret))
        viewModel.processIntent(InvoiceFormIntent.RecipientNameChanged("Client SAS"))
        viewModel.processIntent(InvoiceFormIntent.RecipientSirenChanged(validRecipientSiren))
        viewModel.processIntent(InvoiceFormIntent.RecipientSiretChanged(validRecipientSiret))
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
        assertEquals(SubmissionStatus.Idle, state.submissionStatus)
    }

    // ── Validation SIREN/SIRET (en-tête) ────────────────────────────────────

    @Test
    fun invalidSiren_producesFieldError() {
        val viewModel = InvoiceFormViewModel()
        viewModel.processIntent(InvoiceFormIntent.IssuerSirenChanged("123"))
        assertNotNull(viewModel.uiState.value.errors[InvoiceFormField.ISSUER_SIREN])
    }

    @Test
    fun invalidSiret_producesFieldError() {
        val viewModel = InvoiceFormViewModel()
        viewModel.processIntent(InvoiceFormIntent.RecipientSiretChanged("abc"))
        assertNotNull(viewModel.uiState.value.errors[InvoiceFormField.RECIPIENT_SIRET])
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
        // HT = 100 + 50 + 20 = 170.00 ; TVA = (150 * 20%) + (20 * 5.5%) = 30.00 + 1.10 = 31.10 ; TTC = 201.10
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
        // La ligne valide contribue seule au total affiché (la ligne invalide est neutre, pas fausse).
        assertEquals(10000L, state.totalHt.cents)
    }

    // ── Soumission invalide : ne déclenche aucun appel au repository ──────────

    @Test
    fun submit_withInvalidForm_staysIdle_doesNotProduceInvoice() {
        val viewModel = InvoiceFormViewModel()
        viewModel.processIntent(InvoiceFormIntent.Submit)

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

        viewModel.processIntent(InvoiceFormIntent.Submit)

        assertNull(viewModel.uiState.value.submittedInvoice)
        assertEquals(SubmissionStatus.Idle, viewModel.uiState.value.submissionStatus)
    }

    // ── Cycle de vie de la soumission : Idle -> Loading -> Success (multi-lignes) ──

    @Test
    fun submit_withValidMultiLineForm_transitionsThroughLoadingToSuccess() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val useCase = SubmitInvoiceUseCase(MockInvoiceRepository(simulatedDelayMillis = 1_500L))
        val viewModel = InvoiceFormViewModel(submitInvoiceUseCase = useCase, dispatcher = dispatcher)
        viewModel.processIntent(InvoiceFormIntent.AddLine)
        fillHeader(viewModel)
        fillLine(viewModel, 0, "Conseil", "1", "100.00", VatRate.TAUX_NORMAL)
        fillLine(viewModel, 1, "Livre", "1", "20.00", VatRate.TAUX_REDUIT)

        viewModel.processIntent(InvoiceFormIntent.Submit)

        // Juste après Submit (avant la fin du délai réseau simulé) : Loading, formulaire verrouillé.
        runCurrent()
        assertEquals(SubmissionStatus.Loading, viewModel.uiState.value.submissionStatus)
        assertFalse(viewModel.uiState.value.isFormEnabled)
        assertFalse(viewModel.uiState.value.isSubmitEnabled)
        assertNull(viewModel.uiState.value.submittedInvoice)

        // Une fois le délai simulé écoulé : Success, formulaire déverrouillé, facture à 2 lignes produite.
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(SubmissionStatus.Success, state.submissionStatus)
        assertTrue(state.isFormEnabled)
        val invoice = state.submittedInvoice
        assertNotNull(invoice)
        assertEquals(2, invoice.lines.size)
        assertEquals(12000L, invoice.totalHt.cents) // 100.00 + 20.00
    }

    // ── Cycle de vie de la soumission : Idle -> Loading -> Error ──────────────

    @Test
    fun submit_whenRepositoryFails_transitionsThroughLoadingToError() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val useCase = SubmitInvoiceUseCase(
            MockInvoiceRepository(simulatedDelayMillis = 500L, simulateFailure = true)
        )
        val viewModel = InvoiceFormViewModel(submitInvoiceUseCase = useCase, dispatcher = dispatcher)
        fillValidSingleLineForm(viewModel)

        viewModel.processIntent(InvoiceFormIntent.Submit)
        runCurrent()
        assertEquals(SubmissionStatus.Loading, viewModel.uiState.value.submissionStatus)

        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertIs<SubmissionStatus.Error>(state.submissionStatus)
        assertTrue(state.isFormEnabled)
        assertNull(state.submittedInvoice)
    }
}
