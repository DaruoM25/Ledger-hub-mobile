package com.ledgerhub.presentation.reconciliation

import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceRepository
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.domain.reconciliation.BankTransaction
import com.ledgerhub.domain.reconciliation.FakeBankTransactionRepository
import com.ledgerhub.domain.reconciliation.FakeReconciliationRepository
import com.ledgerhub.domain.reconciliation.ReconcilePaymentUseCase
import com.ledgerhub.domain.time.FixedClock
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Niveau 1 (US-18) — sélection croisée et lettrage vus depuis l'état de l'écran.
 *
 * Le ViewModel ne recalcule pas les règles du domaine : ce niveau vérifie ce qui lui appartient en
 * propre — quelles factures il présente, comment la sélection se comporte, et à quel moment
 * l'action de lettrage devient disponible.
 */
class ReconciliationViewModelTest {

    private val clock = FixedClock("2026-09-01T10:15:00Z")
    private val issuer = Party("Cabinet LedgerHub", "820329331", "82032933100027")
    private val recipient = Party("Boulangerie Moreau SARL", "784102336", "78410233600021")

    /** 240,00 € TTC. */
    private fun invoice(number: String, status: InvoiceStatus) = Invoice(
        number = number,
        issueDate = "2026-08-31",
        issuer = issuer,
        recipient = recipient,
        lines = listOf(InvoiceLine("Conseil réglementaire", 2, Money(10_000), VatRate.TAUX_NORMAL)),
        status = status,
    )

    private fun transaction(id: String, cents: Long = 24_000) = BankTransaction(
        id = id,
        label = "VIR SEPA $id",
        amount = Money(cents),
        valueDateIso = "2026-09-01",
    )

    private class StubInvoiceRepository(private val invoices: List<Invoice>) : InvoiceRepository {
        override suspend fun submitInvoice(invoice: Invoice): Result<Unit> = Result.success(Unit)
        override suspend fun fetchInvoices(): Result<List<Invoice>> = Result.success(invoices)
    }

    private fun viewModel(
        invoices: List<Invoice> = listOf(invoice("FAC-2026-0301", InvoiceStatus.DEPOSITED)),
        transactions: List<BankTransaction> = listOf(transaction("TX-2026-0091")),
        reconciliation: FakeReconciliationRepository = FakeReconciliationRepository(),
        dispatcher: TestDispatcher,
    ) = ReconciliationViewModel(
        invoiceRepository = StubInvoiceRepository(invoices),
        bankTransactionRepository = FakeBankTransactionRepository(transactions),
        reconciliationRepository = reconciliation,
        reconcilePayment = ReconcilePaymentUseCase(reconciliation, clock),
        dispatcher = dispatcher,
    )

    // ── Chargement et filtrage réglementaire ────────────────────────────────

    /**
     * Le filtre n'est pas cosmétique : `DRAFT → PAID` étant interdit, présenter un brouillon
     * offrirait une impasse — l'utilisateur le sélectionnerait pour se voir refuser le lettrage.
     */
    @Test
    fun onlyInvoicesThatCanBePaid_areOffered() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val subject = viewModel(
            invoices = listOf(
                invoice("FAC-DRAFT", InvoiceStatus.DRAFT),
                invoice("FAC-DEPOSITED", InvoiceStatus.DEPOSITED),
                invoice("FAC-APPROVED", InvoiceStatus.APPROVED),
                invoice("FAC-PAID", InvoiceStatus.PAID),
                invoice("FAC-CANCELLED", InvoiceStatus.CANCELLED),
            ),
            dispatcher = dispatcher,
        )
        advanceUntilIdle()

        assertEquals(
            listOf("FAC-DEPOSITED", "FAC-APPROVED"),
            subject.uiState.value.invoices.map { it.number },
        )
    }

    @Test
    fun theLoadedState_carriesTransactionsAndMatches() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val subject = viewModel(dispatcher = dispatcher)
        advanceUntilIdle()

        val state = subject.uiState.value
        assertFalse(state.isLoading)
        assertEquals(listOf("TX-2026-0091"), state.transactions.map { it.id })
    }

    // ── Sélection croisée ───────────────────────────────────────────────────

    @Test
    fun theActionAppears_onlyOnceBothSidesAreSelected() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val subject = viewModel(dispatcher = dispatcher)
        advanceUntilIdle()

        assertFalse(subject.uiState.value.canReconcile, "Rien n'est sélectionné")

        subject.processIntent(ReconciliationIntent.SelectTransaction("TX-2026-0091"))
        assertFalse(subject.uiState.value.canReconcile, "Un seul côté sélectionné")

        subject.processIntent(ReconciliationIntent.SelectInvoice("FAC-2026-0301"))
        assertTrue(subject.uiState.value.canReconcile)
    }

    /** Bascule : re-toucher la carte déjà choisie la relâche, sans passer par un bouton d'annulation. */
    @Test
    fun selectingTheSameCardTwice_clearsIt() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val subject = viewModel(dispatcher = dispatcher)
        advanceUntilIdle()

        subject.processIntent(ReconciliationIntent.SelectTransaction("TX-2026-0091"))
        subject.processIntent(ReconciliationIntent.SelectTransaction("TX-2026-0091"))

        assertNull(subject.uiState.value.selectedTransactionId)
    }

    @Test
    fun clearSelection_releasesBothSides() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val subject = viewModel(dispatcher = dispatcher)
        advanceUntilIdle()

        subject.processIntent(ReconciliationIntent.SelectTransaction("TX-2026-0091"))
        subject.processIntent(ReconciliationIntent.SelectInvoice("FAC-2026-0301"))
        subject.processIntent(ReconciliationIntent.ClearSelection)

        val state = subject.uiState.value
        assertNull(state.selectedTransactionId)
        assertNull(state.selectedInvoiceNumber)
        assertFalse(state.canReconcile)
    }

    /** L'écart du couple en cours pilote le badge : il doit suivre la sélection, pas la précéder. */
    @Test
    fun thePendingDelta_followsTheCurrentPair() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val subject = viewModel(
            transactions = listOf(transaction("TX-SHORT", cents = 23_850)),
            dispatcher = dispatcher,
        )
        advanceUntilIdle()

        assertNull(subject.uiState.value.pendingDelta, "Sans couple, aucun écart n'a de sens")

        subject.processIntent(ReconciliationIntent.SelectTransaction("TX-SHORT"))
        subject.processIntent(ReconciliationIntent.SelectInvoice("FAC-2026-0301"))

        assertEquals(Money(-150), subject.uiState.value.pendingDelta)
        assertTrue(subject.uiState.value.hasPendingMismatch)
    }

    // ── Lettrage ────────────────────────────────────────────────────────────

    /**
     * Après lettrage, la facture est passée à `PAID` : elle quitte donc la liste des factures à
     * solder, et la transaction porte le badge « Rapprochée ».
     */
    @Test
    fun performingAMatch_writesIt_andRefreshesTheLists() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val reconciliation = FakeReconciliationRepository()
        val subject = viewModel(reconciliation = reconciliation, dispatcher = dispatcher)
        advanceUntilIdle()

        subject.processIntent(ReconciliationIntent.SelectTransaction("TX-2026-0091"))
        subject.processIntent(ReconciliationIntent.SelectInvoice("FAC-2026-0301"))
        subject.processIntent(ReconciliationIntent.PerformMatch)
        advanceUntilIdle()

        assertEquals(1, reconciliation.matches.size)
        val state = subject.uiState.value
        assertNull(state.selectedTransactionId, "La sélection est relâchée après lettrage")
        assertNull(state.selectedInvoiceNumber)
        assertTrue(state.isTransactionReconciled("TX-2026-0091"))
        assertNotNull(state.matchFor("TX-2026-0091"))
    }

    @Test
    fun performingAMatch_withoutBothSides_doesNothing() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val reconciliation = FakeReconciliationRepository()
        val subject = viewModel(reconciliation = reconciliation, dispatcher = dispatcher)
        advanceUntilIdle()

        subject.processIntent(ReconciliationIntent.SelectTransaction("TX-2026-0091"))
        subject.processIntent(ReconciliationIntent.PerformMatch)
        advanceUntilIdle()

        assertTrue(reconciliation.matches.isEmpty())
    }

    /** Un refus du domaine doit remonter à l'écran, pas disparaître dans un `catch` silencieux. */
    @Test
    fun aRefusedMatch_surfacesItsMessage() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val reconciliation = FakeReconciliationRepository()
        val subject = viewModel(
            transactions = listOf(transaction("TX-DEBIT", cents = -24_000)),
            reconciliation = reconciliation,
            dispatcher = dispatcher,
        )
        advanceUntilIdle()

        subject.processIntent(ReconciliationIntent.SelectTransaction("TX-DEBIT"))
        subject.processIntent(ReconciliationIntent.SelectInvoice("FAC-2026-0301"))
        subject.processIntent(ReconciliationIntent.PerformMatch)
        advanceUntilIdle()

        assertNotNull(subject.uiState.value.errorMessage)
        assertTrue(reconciliation.matches.isEmpty())

        subject.processIntent(ReconciliationIntent.MessageShown)
        assertNull(subject.uiState.value.errorMessage)
    }

    // ── Agencement compact ──────────────────────────────────────────────────

    /**
     * En onglets, la sélection de l'onglet masqué doit survivre au changement d'onglet : c'est ce
     * qui permet d'associer deux éléments qu'on ne peut pas voir en même temps.
     */
    @Test
    fun switchingTab_keepsTheSelectionOfTheHiddenSide() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val subject = viewModel(dispatcher = dispatcher)
        advanceUntilIdle()

        subject.processIntent(ReconciliationIntent.SelectTransaction("TX-2026-0091"))
        subject.showTab(ReconciliationTab.INVOICES)
        subject.processIntent(ReconciliationIntent.SelectInvoice("FAC-2026-0301"))

        val state = subject.uiState.value
        assertEquals(ReconciliationTab.INVOICES, state.compactTab)
        assertEquals("TX-2026-0091", state.selectedTransactionId)
        assertTrue(state.canReconcile)
    }
}
