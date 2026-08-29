package com.ledgerhub.domain.invoice

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Capture l'appel au dépôt sans base : permet d'affirmer qu'un refus n'écrit *rien*. */
private class RecordingStatusRepository : InvoiceStatusRepository {
    var callCount = 0
    var lastFrom: InvoiceStatus? = null
    var lastTo: InvoiceStatus? = null
    var lastReason: String? = null

    override suspend fun changeStatus(
        invoiceNumber: String,
        from: InvoiceStatus,
        to: InvoiceStatus,
        reason: String?,
    ): Result<Unit> {
        callCount++
        lastFrom = from
        lastTo = to
        lastReason = reason
        return Result.success(Unit)
    }
}

/**
 * Le use case est le point d'entrée unique des transitions : il valide **avant** tout accès au
 * dépôt. Une transition interdite ne doit pas atteindre la base, même pour y être rejetée.
 */
class ChangeInvoiceStatusUseCaseTest {

    private fun invoice(status: InvoiceStatus) = Invoice(
        number = "FAC-2026-0001",
        issueDate = "2026-07-12",
        issuer = Party("Cabinet", "820329331", "82032933100027"),
        recipient = Party("Client", "784102336", "78410233600021"),
        lines = listOf(InvoiceLine("Conseil", 1, Money(100_000), VatRate.TAUX_NORMAL)),
        status = status,
    )

    @Test
    fun anAuthorisedTransition_reachesTheRepository() = runTest {
        val repository = RecordingStatusRepository()

        val result = ChangeInvoiceStatusUseCase(repository)(invoice(InvoiceStatus.DRAFT), InvoiceStatus.DEPOSITED)

        assertTrue(result.isSuccess)
        assertEquals(1, repository.callCount)
        assertEquals(InvoiceStatus.DRAFT, repository.lastFrom)
        assertEquals(InvoiceStatus.DEPOSITED, repository.lastTo)
    }

    @Test
    fun aForbiddenTransition_neverTouchesTheRepository() = runTest {
        val repository = RecordingStatusRepository()

        val result = ChangeInvoiceStatusUseCase(repository)(invoice(InvoiceStatus.PAID), InvoiceStatus.DRAFT)

        assertIs<InvalidStatusTransitionException>(result.exceptionOrNull())
        assertEquals(0, repository.callCount, "Aucune écriture ne doit être tentée")
    }

    @Test
    fun aTerminalInvoice_refusesEveryTransition() = runTest {
        val repository = RecordingStatusRepository()
        val useCase = ChangeInvoiceStatusUseCase(repository)

        InvoiceStatus.entries.forEach { target ->
            assertTrue(
                useCase(invoice(InvoiceStatus.CANCELLED), target, "motif").isFailure,
                "CANCELLED -> $target doit être refusé",
            )
        }
        assertEquals(0, repository.callCount)
    }

    // ── Motif obligatoire sur les transitions négatives ──────────────────────────────────────

    @Test
    fun rejection_withoutReason_isRefused() = runTest {
        val repository = RecordingStatusRepository()

        val result = ChangeInvoiceStatusUseCase(repository)(
            invoice(InvoiceStatus.DEPOSITED), InvoiceStatus.REJECTED, reason = null,
        )

        assertIs<IllegalArgumentException>(result.exceptionOrNull())
        assertEquals(0, repository.callCount)
    }

    @Test
    fun refusal_withBlankReason_isRefused() = runTest {
        val repository = RecordingStatusRepository()

        val result = ChangeInvoiceStatusUseCase(repository)(
            invoice(InvoiceStatus.DEPOSITED), InvoiceStatus.REFUSED, reason = "   ",
        )

        assertIs<IllegalArgumentException>(result.exceptionOrNull())
        assertEquals(0, repository.callCount)
    }

    @Test
    fun rejection_withReason_isAcceptedAndTheReasonIsTrimmed() = runTest {
        val repository = RecordingStatusRepository()

        val result = ChangeInvoiceStatusUseCase(repository)(
            invoice(InvoiceStatus.DEPOSITED), InvoiceStatus.REJECTED, reason = "  SIRET invalide  ",
        )

        assertTrue(result.isSuccess)
        assertEquals("SIRET invalide", repository.lastReason)
    }

    @Test
    fun aPositiveTransition_needsNoReason() = runTest {
        val repository = RecordingStatusRepository()

        val result = ChangeInvoiceStatusUseCase(repository)(
            invoice(InvoiceStatus.DEPOSITED), InvoiceStatus.PAID,
        )

        assertTrue(result.isSuccess)
        assertNull(repository.lastReason)
    }

    @Test
    fun correctingARejectedInvoice_reopensItAsDraft() = runTest {
        val repository = RecordingStatusRepository()

        val result = ChangeInvoiceStatusUseCase(repository)(
            invoice(InvoiceStatus.REJECTED), InvoiceStatus.DRAFT,
        )

        assertTrue(result.isSuccess)
        assertEquals(InvoiceStatus.DRAFT, repository.lastTo)
    }
}

/** Règles fiscales dérivées du nouveau référentiel. */
class InvoiceLifecycleRulesTest {

    private fun invoice(status: InvoiceStatus) = Invoice(
        number = "FAC-2026-0001",
        issueDate = "2026-07-12",
        issuer = Party("Cabinet", "820329331", "82032933100027"),
        recipient = Party("Client", "784102336", "78410233600021"),
        lines = listOf(InvoiceLine("Conseil", 1, Money(100_000), VatRate.TAUX_NORMAL)),
        status = status,
    )

    @Test
    fun editable_onDraftAndRejectedOnly() {
        // Une facture rejetée n'est jamais entrée dans le circuit légal : elle redevient corrigeable.
        assertTrue(invoice(InvoiceStatus.DRAFT).isEditable)
        assertTrue(invoice(InvoiceStatus.REJECTED).isEditable)
        listOf(InvoiceStatus.DEPOSITED, InvoiceStatus.PAID, InvoiceStatus.REFUSED, InvoiceStatus.CANCELLED)
            .forEach { assertTrue(!invoice(it).isEditable, "$it ne doit pas être modifiable") }
    }

    @Test
    fun deletable_onDraftOnly() {
        // Plus strict que isEditable : supprimer une facture rejetée effacerait sa trace d'audit.
        assertTrue(canDelete(invoice(InvoiceStatus.DRAFT)))
        assertTrue(!canDelete(invoice(InvoiceStatus.REJECTED)))
    }

    @Test
    fun cancellableByCreditNote_onDepositedPaidAndRefused() {
        listOf(InvoiceStatus.DEPOSITED, InvoiceStatus.PAID, InvoiceStatus.REFUSED)
            .forEach { assertTrue(invoice(it).isCancellableByCreditNote, "$it doit être annulable") }
        listOf(InvoiceStatus.DRAFT, InvoiceStatus.REJECTED, InvoiceStatus.CANCELLED)
            .forEach { assertTrue(!invoice(it).isCancellableByCreditNote, "$it ne doit pas l'être") }
    }
}
