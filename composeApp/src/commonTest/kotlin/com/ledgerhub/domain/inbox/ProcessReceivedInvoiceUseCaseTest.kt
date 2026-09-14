package com.ledgerhub.domain.inbox

import com.ledgerhub.domain.invoice.Money
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProcessReceivedInvoiceUseCaseTest {

    private class FakeInboxRepository : InboxRepository {
        val invoices = mutableListOf<ReceivedInvoice>()

        override suspend fun saveReceivedInvoice(invoice: ReceivedInvoice): Result<Unit> {
            invoices.removeAll { it.id == invoice.id }
            invoices.add(invoice)
            return Result.success(Unit)
        }

        override suspend fun fetchReceivedInvoices(): Result<List<ReceivedInvoice>> = Result.success(invoices.toList())

        override suspend fun getReceivedInvoiceById(id: String): Result<ReceivedInvoice?> {
            return Result.success(invoices.find { it.id == id })
        }

        override suspend fun findByFileHash(fileHash: String): Result<ReceivedInvoice?> {
            return Result.success(invoices.find { it.fileHash == fileHash })
        }

        override suspend fun findDuplicateTriptych(
            supplierSiren: String,
            invoiceNumber: String,
            totalTtcCents: Long,
            excludeId: String,
        ): Result<ReceivedInvoice?> {
            return Result.success(
                invoices.find {
                    it.supplierSiren == supplierSiren &&
                        it.invoiceNumber == invoiceNumber &&
                        it.totalTtc.cents == totalTtcCents &&
                        it.id != excludeId
                }
            )
        }

        override suspend fun updateStatus(
            id: String,
            status: ReceivedInvoiceStatus,
            duplicateReason: String?,
        ): Result<Unit> {
            val idx = invoices.indexOfFirst { it.id == id }
            if (idx != -1) {
                val current = invoices[idx]
                invoices[idx] = current.copy(status = status, duplicateReason = duplicateReason)
            }
            return Result.success(Unit)
        }
    }

    @Test
    fun processFirstInvoice_isSavedAsReceived() = runTest {
        val repo = FakeInboxRepository()
        val useCase = ProcessReceivedInvoiceUseCase(repo)

        val result = useCase(
            id = "REC-001",
            supplierName = "Acme Corp",
            supplierSiren = "123456782",
            supplierSiret = "12345678200015",
            invoiceNumber = "FAC-2026-001",
            issueDate = "2026-09-14",
            dueDate = "2026-10-14",
            totalHt = Money(100_000),
            totalVat = Money(20_000),
            totalTtc = Money(120_000),
            rawPayload = "FacturX-XML",
            receivedAt = "2026-09-14T10:00:00",
        )

        assertTrue(result is ProcessReceivedInvoiceResult.Success)
        assertEquals(ReceivedInvoiceStatus.RECEIVED, result.invoice.status)
        assertEquals(1, repo.invoices.size)
    }

    @Test
    fun processDuplicateFileHash_triggersDuplicateAlertAndBlocksApproval() = runTest {
        val repo = FakeInboxRepository()
        val useCase = ProcessReceivedInvoiceUseCase(repo)

        // 1ère facture
        useCase(
            id = "REC-001",
            supplierName = "Acme Corp",
            supplierSiren = "123456782",
            supplierSiret = "12345678200015",
            invoiceNumber = "FAC-2026-001",
            issueDate = "2026-09-14",
            dueDate = "2026-10-14",
            totalHt = Money(100_000),
            totalVat = Money(20_000),
            totalTtc = Money(120_000),
            rawPayload = "FacturX-XML-CONTENT",
            receivedAt = "2026-09-14T10:00:00",
        )

        // 2ème facture identique importée sous un autre ID
        val duplicateResult = useCase(
            id = "REC-002",
            supplierName = "Acme Corp",
            supplierSiren = "123456782",
            supplierSiret = "12345678200015",
            invoiceNumber = "FAC-2026-001",
            issueDate = "2026-09-14",
            dueDate = "2026-10-14",
            totalHt = Money(100_000),
            totalVat = Money(20_000),
            totalTtc = Money(120_000),
            rawPayload = "FacturX-XML-CONTENT",
            receivedAt = "2026-09-14T10:05:00",
        )

        assertTrue(duplicateResult is ProcessReceivedInvoiceResult.DuplicateDetected)
        assertEquals(ReceivedInvoiceStatus.DUPLICATE_ALERT, duplicateResult.invoice.status)
        assertNotNull(duplicateResult.invoice.duplicateReason)

        // Tentative d'approbation pour paiement doit échouer
        val approveResult = useCase.approveInvoice("REC-002")
        assertFalse(approveResult.isSuccess)
        assertTrue(approveResult.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun processDuplicateTriptych_evenWithDifferentPayload_triggersDuplicateAlert() = runTest {
        val repo = FakeInboxRepository()
        val useCase = ProcessReceivedInvoiceUseCase(repo)

        useCase(
            id = "REC-001",
            supplierName = "Acme Corp",
            supplierSiren = "123456782",
            supplierSiret = "12345678200015",
            invoiceNumber = "FAC-2026-001",
            issueDate = "2026-09-14",
            dueDate = "2026-10-14",
            totalHt = Money(100_000),
            totalVat = Money(20_000),
            totalTtc = Money(120_000),
            rawPayload = "Payload A",
            receivedAt = "2026-09-14T10:00:00",
        )

        // Même fournisseur + même numéro + même montant TTC, mais payload OCR légèrement altéré
        val triptychDuplicateResult = useCase(
            id = "REC-003",
            supplierName = "Acme Corp",
            supplierSiren = "123456782",
            supplierSiret = "12345678200015",
            invoiceNumber = "FAC-2026-001",
            issueDate = "2026-09-14",
            dueDate = "2026-10-14",
            totalHt = Money(100_000),
            totalVat = Money(20_000),
            totalTtc = Money(120_000),
            rawPayload = "Payload B (Re-scan OCR)",
            receivedAt = "2026-09-14T11:00:00",
        )

        assertTrue(triptychDuplicateResult is ProcessReceivedInvoiceResult.DuplicateDetected)
        assertEquals(ReceivedInvoiceStatus.DUPLICATE_ALERT, triptychDuplicateResult.invoice.status)
    }

    @Test
    fun legitimateInvoice_canBeApprovedAndRejected() = runTest {
        val repo = FakeInboxRepository()
        val useCase = ProcessReceivedInvoiceUseCase(repo)

        useCase(
            id = "REC-001",
            supplierName = "Fournisseur Légal",
            supplierSiren = "999888777",
            supplierSiret = "99988877700012",
            invoiceNumber = "FAC-OK-01",
            issueDate = "2026-09-14",
            dueDate = "2026-10-14",
            totalHt = Money(50_000),
            totalVat = Money(10_000),
            totalTtc = Money(60_000),
            rawPayload = "OK",
            receivedAt = "2026-09-14T10:00:00",
        )

        val approveResult = useCase.approveInvoice("REC-001")
        assertTrue(approveResult.isSuccess)
        assertEquals(ReceivedInvoiceStatus.APPROVED, repo.invoices.first().status)

        val rejectResult = useCase.rejectInvoice("REC-001", "Erreur de montant")
        assertTrue(rejectResult.isSuccess)
        assertEquals(ReceivedInvoiceStatus.REJECTED, repo.invoices.first().status)
        assertEquals("Erreur de montant", repo.invoices.first().duplicateReason)
    }
}
