package com.ledgerhub.domain.degraded

import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.InvoiceStatusRepository
import com.ledgerhub.domain.time.Clock
import com.ledgerhub.domain.time.SystemClock

/**
 * Rapport d'exécution du lot de régularisation en mode dégradé (US-29).
 */
data class SyncBatchResult(
    val totalProcessed: Int,
    val successCount: Int,
    val failureCount: Int,
    val syncedInvoiceNumbers: List<String>,
)

/**
 * Cas d'usage : traitement par lot de la file d'attente de régularisation (US-29).
 *
 * Lorsque la connectivité est rétablie avec le PPF, chaque facture en attente est
 * télétransmise et son statut passe de [InvoiceStatus.PENDING_REGULARIZATION] à [InvoiceStatus.DEPOSITED].
 */
class ProcessSyncQueueBatchUseCase(
    private val syncQueueRepository: SyncQueueRepository,
    private val invoiceStatusRepository: InvoiceStatusRepository,
    private val clock: Clock = SystemClock,
) {
    suspend operator fun invoke(): Result<SyncBatchResult> = runCatching {
        val pendingEntries = syncQueueRepository.getPendingEntries().getOrThrow()
        val nowIso = clock.nowIso()

        var successes = 0
        var failures = 0
        val syncedNumbers = mutableListOf<String>()

        for (entry in pendingEntries) {
            val statusResult = invoiceStatusRepository.changeStatus(
                invoiceNumber = entry.invoiceId,
                from = InvoiceStatus.PENDING_REGULARIZATION,
                to = InvoiceStatus.DEPOSITED,
                reason = "Régularisation télétransmise suite au rétablissement du Portail Public de Facturation",
            )

            if (statusResult.isSuccess) {
                syncQueueRepository.updateStatus(
                    id = entry.id,
                    status = SyncStatus.SYNCED,
                    syncedAt = nowIso,
                ).getOrThrow()
                successes++
                syncedNumbers.add(entry.invoiceId)
            } else {
                failures++
                syncQueueRepository.updateStatus(
                    id = entry.id,
                    status = SyncStatus.FAILED,
                    lastError = statusResult.exceptionOrNull()?.message ?: "Erreur de régularisation",
                ).getOrThrow()
            }
        }

        SyncBatchResult(
            totalProcessed = pendingEntries.size,
            successCount = successes,
            failureCount = failures,
            syncedInvoiceNumbers = syncedNumbers,
        )
    }
}
