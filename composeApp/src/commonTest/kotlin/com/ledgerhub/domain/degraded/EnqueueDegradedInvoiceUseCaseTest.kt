package com.ledgerhub.domain.degraded

import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceRepository
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.domain.time.Clock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EnqueueDegradedInvoiceUseCaseTest {

    private class FakeInvoiceRepository : InvoiceRepository {
        val invoices = mutableMapOf<String, Invoice>()

        override suspend fun submitInvoice(invoice: Invoice): Result<Unit> {
            invoices[invoice.number] = invoice
            return Result.success(Unit)
        }

        override suspend fun fetchInvoices(): Result<List<Invoice>> {
            return Result.success(invoices.values.toList())
        }
    }

    private class FakeSyncQueueRepository : SyncQueueRepository {
        val entries = mutableMapOf<String, SyncQueueEntry>()

        override suspend fun enqueue(entry: SyncQueueEntry): Result<Unit> {
            entries[entry.invoiceId] = entry
            return Result.success(Unit)
        }

        override suspend fun getPendingEntries(): Result<List<SyncQueueEntry>> {
            return Result.success(entries.values.filter { it.syncStatus == SyncStatus.PENDING })
        }

        override suspend fun getAllEntries(): Result<List<SyncQueueEntry>> {
            return Result.success(entries.values.toList())
        }

        override suspend fun getEntryByInvoiceId(invoiceId: String): Result<SyncQueueEntry?> {
            return Result.success(entries[invoiceId])
        }

        override suspend fun updateStatus(
            id: String,
            status: SyncStatus,
            lastError: String?,
            syncedAt: String?,
        ): Result<Unit> {
            val entry = entries.values.firstOrNull { it.id == id }
                ?: return Result.failure(NoSuchElementException("Entry not found"))
            entries[entry.invoiceId] = entry.copy(
                syncStatus = status,
                lastError = lastError,
                syncedAt = syncedAt,
            )
            return Result.success(Unit)
        }

        override suspend fun countPending(): Result<Long> {
            return Result.success(entries.values.count { it.syncStatus == SyncStatus.PENDING }.toLong())
        }

        override suspend fun remove(invoiceId: String): Result<Unit> {
            entries.remove(invoiceId)
            return Result.success(Unit)
        }
    }

    private class FakeClock(private val fixedIso: String = "2026-09-12T18:00:00Z") : Clock {
        override fun nowIso(): String = fixedIso
        override fun nowEpochMillis(): Long = 1789236000000L
    }

    private fun sampleInvoice(number: String = "FAC-2026-DEG-001") = Invoice(
        number = number,
        issueDate = "2026-09-12",
        issuer = Party("Cabinet LedgerHub", "820329331", "82032933100027"),
        recipient = Party("Boulangerie Test", "784102336", "78410233600021"),
        lines = listOf(InvoiceLine("Prestation secours", 1, Money(100_000L), VatRate.TAUX_NORMAL)),
        status = InvoiceStatus.DRAFT,
    )

    @Test
    fun enqueueDegradedInvoice_forcesPendingRegularizationAndCreatesPendingQueueEntry() = runTest {
        val invoiceRepo = FakeInvoiceRepository()
        val syncRepo = FakeSyncQueueRepository()
        val clock = FakeClock("2026-09-12T10:00:00Z")
        val useCase = EnqueueDegradedInvoiceUseCase(invoiceRepo, syncRepo, clock)

        val invoice = sampleInvoice("FAC-2026-0099")
        val result = useCase(invoice, DegradedChannel.PDF_SIMPLE)

        assertTrue(result.isSuccess)
        val entry = result.getOrThrow()

        // 1. Statut facture forcé à PENDING_REGULARIZATION
        val savedInvoice = invoiceRepo.invoices["FAC-2026-0099"]
        assertEquals(InvoiceStatus.PENDING_REGULARIZATION, savedInvoice?.status)

        // 2. Queue entry créée en statut PENDING
        assertEquals("FAC-2026-0099", entry.invoiceId)
        assertEquals(SyncStatus.PENDING, entry.syncStatus)
        assertEquals(DegradedChannel.PDF_SIMPLE, entry.originalChannel)
        assertEquals("2026-09-12T10:00:00Z", entry.createdAt)

        // 3. Persistée dans le sync repository
        val storedEntry = syncRepo.getEntryByInvoiceId("FAC-2026-0099").getOrNull()
        assertEquals(entry, storedEntry)
    }

    @Test
    fun enqueueDegradedInvoice_strictDeduplication_raisesDuplicateDegradedInvoiceException() = runTest {
        val invoiceRepo = FakeInvoiceRepository()
        val syncRepo = FakeSyncQueueRepository()
        val useCase = EnqueueDegradedInvoiceUseCase(invoiceRepo, syncRepo)

        val invoice = sampleInvoice("FAC-2026-0099")

        // Premier enfilement : OK
        val firstResult = useCase(invoice)
        assertTrue(firstResult.isSuccess)

        // Deuxième enfilement avec le même numéro de facture : ECHEC
        val secondResult = useCase(invoice)
        assertTrue(secondResult.isFailure)
        val exception = assertIs<DuplicateDegradedInvoiceException>(secondResult.exceptionOrNull())
        assertEquals("FAC-2026-0099", exception.invoiceNumber)
    }
}
