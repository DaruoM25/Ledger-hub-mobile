package com.ledgerhub.domain.reconciliation

import com.ledgerhub.domain.invoice.InvalidStatusTransitionException
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.domain.time.FixedClock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Niveau 1 (US-18) — règles du lettrage.
 *
 * Ce cas d'usage est le seul point d'entrée du rapprochement : ce qu'il refuse est définitivement
 * refusé. Les trois refus sont donc testés autant que le chemin nominal — un garde-fou dont on ne
 * vérifie que le succès n'est pas un garde-fou.
 *
 * [FixedClock] rend l'horodatage déterministe : un lettrage horodaté à l'heure réelle ne serait
 * pas comparable d'une exécution à l'autre.
 */
class ReconcilePaymentUseCaseTest {

    private val clock = FixedClock("2026-09-01T10:15:00Z")
    private val issuer = Party("Cabinet LedgerHub", "820329331", "82032933100027")
    private val recipient = Party("Boulangerie Moreau SARL", "784102336", "78410233600021")

    /** 240,00 € TTC. */
    private fun invoice(
        status: InvoiceStatus = InvoiceStatus.DEPOSITED,
        number: String = "FAC-2026-0301",
    ) = Invoice(
        number = number,
        issueDate = "2026-08-31",
        issuer = issuer,
        recipient = recipient,
        lines = listOf(InvoiceLine("Conseil réglementaire", 2, Money(10_000), VatRate.TAUX_NORMAL)),
        status = status,
    )

    private fun transaction(cents: Long = 24_000, id: String = "TX-2026-0091") = BankTransaction(
        id = id,
        label = "VIR SEPA BOULANGERIE MOREAU",
        amount = Money(cents),
        valueDateIso = "2026-09-01",
    )

    private fun useCase(repository: FakeReconciliationRepository) =
        ReconcilePaymentUseCase(repository, clock)

    // ── Chemin nominal ──────────────────────────────────────────────────────

    @Test
    fun aDepositedInvoice_isReconciledAndTimestamped() = runTest {
        val repository = FakeReconciliationRepository()

        val result = useCase(repository)(transaction(), invoice())

        val match = result.getOrThrow()
        assertEquals("TX-2026-0091", match.transactionId)
        assertEquals("FAC-2026-0301", match.invoiceNumber)
        assertEquals("2026-09-01T10:15:00Z", match.matchedAtIso)
        assertEquals(0L, match.deltaCents)
        assertEquals(listOf(match), repository.matches)
    }

    /** Le statut de départ est transmis au dépôt : sans lui, la trace d'audit serait incomplète. */
    @Test
    fun theOriginStatus_isHandedToTheRepository_forTheAuditTrail() = runTest {
        val repository = FakeReconciliationRepository()

        useCase(repository)(transaction(), invoice(InvoiceStatus.APPROVED)).getOrThrow()

        assertEquals(listOf("APPROVED"), repository.recordedFromStatuses)
    }

    /** L'écart est figé au lettrage, pas recalculé plus tard sur une facture qui aurait bougé. */
    @Test
    fun aPartialPayment_isAllowed_andFreezesItsDelta() = runTest {
        val repository = FakeReconciliationRepository()

        val match = useCase(repository)(transaction(cents = 23_850), invoice()).getOrThrow()

        assertEquals(-150L, match.deltaCents)
        assertTrue(match.hasAmountMismatch)
    }

    // ── Refus ───────────────────────────────────────────────────────────────

    /**
     * `DRAFT → PAID` est interdit par la machine d'états : un brouillon n'est pas entré dans le
     * circuit légal et ne peut donc pas être soldé.
     */
    @Test
    fun aDraftInvoice_cannotBeReconciled() = runTest {
        val repository = FakeReconciliationRepository()

        val result = useCase(repository)(transaction(), invoice(InvoiceStatus.DRAFT))

        assertIs<InvalidStatusTransitionException>(result.exceptionOrNull())
        assertTrue(repository.matches.isEmpty(), "Rien ne doit être écrit sur une transition refusée")
    }

    @Test
    fun anAlreadyPaidInvoice_cannotBeReconciledAgain() = runTest {
        val repository = FakeReconciliationRepository()

        val result = useCase(repository)(transaction(), invoice(InvoiceStatus.PAID))

        assertIs<InvalidStatusTransitionException>(result.exceptionOrNull())
    }

    @Test
    fun aDebitTransaction_cannotSettleAnInvoice() = runTest {
        val repository = FakeReconciliationRepository()

        val result = useCase(repository)(transaction(cents = -24_000), invoice())

        assertIs<NotACreditTransactionException>(result.exceptionOrNull())
        assertTrue(repository.matches.isEmpty())
    }

    @Test
    fun aTransactionAlreadyMatched_isRefused() = runTest {
        val repository = FakeReconciliationRepository()
        useCase(repository)(transaction(), invoice()).getOrThrow()

        val result = useCase(repository)(transaction(), invoice(number = "FAC-2026-0302"))

        assertIs<AlreadyReconciledException>(result.exceptionOrNull())
        assertEquals(1, repository.matches.size, "Une écriture bancaire ne solde qu'une facture")
    }

    @Test
    fun anInvoiceAlreadyMatched_isRefused() = runTest {
        val repository = FakeReconciliationRepository()
        useCase(repository)(transaction(), invoice()).getOrThrow()

        val result = useCase(repository)(transaction(id = "TX-2026-0092"), invoice())

        assertIs<AlreadyReconciledException>(result.exceptionOrNull())
        assertEquals(1, repository.matches.size, "Une facture n'est soldée que par une écriture")
    }

    /** Une panne de lecture ne doit pas se traduire par un lettrage écrit à l'aveugle. */
    @Test
    fun aRepositoryFailure_surfacesAsAFailedResult() = runTest {
        val repository = FakeReconciliationRepository(failWith = IllegalStateException("base indisponible"))

        val result = useCase(repository)(transaction(), invoice())

        assertTrue(result.isFailure)
        assertTrue(repository.matches.isEmpty())
    }
}
