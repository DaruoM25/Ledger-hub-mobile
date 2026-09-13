package com.ledgerhub.domain.degraded

import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceRepository
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.time.Clock
import com.ledgerhub.domain.time.SystemClock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Exception levée en cas de tentative d'insertion en double d'une facture dans la file
 * de synchronisation en mode dégradé (sécurité fiscale anti-doublon).
 */
class DuplicateDegradedInvoiceException(val invoiceNumber: String) :
    IllegalStateException("La facture $invoiceNumber est déjà présente dans la file de régularisation ou déjà synchronisée.")

/**
 * Use-case d'enfilement d'une facture sous mode dégradé (US-29).
 *
 * Règles fiscales DGFiP 2026 :
 * 1. La facture est enregistrée avec le statut obligatoire [InvoiceStatus.PENDING_REGULARIZATION].
 * 2. Un contrôle strict d'unicité vérifie qu'elle n'est pas déjà présente dans la file d'attente.
 * 3. L'entrée de file est horodatée et créée à l'état `PENDING`.
 */
class EnqueueDegradedInvoiceUseCase(
    private val invoiceRepository: InvoiceRepository,
    private val syncQueueRepository: SyncQueueRepository,
    private val clock: Clock = SystemClock,
) {
    @OptIn(ExperimentalUuidApi::class)
    suspend operator fun invoke(
        invoice: Invoice,
        channel: DegradedChannel = DegradedChannel.PDF_SIMPLE,
    ): Result<SyncQueueEntry> = runCatching {
        // Déduplication stricte : interdiction de ré-enfiler une facture déjà dans la file
        val existingEntry = syncQueueRepository.getEntryByInvoiceId(invoice.number).getOrNull()
        if (existingEntry != null) {
            throw DuplicateDegradedInvoiceException(invoice.number)
        }

        // Forcer le statut réglementaire PENDING_REGULARIZATION
        val degradedInvoice = invoice.copy(
            status = InvoiceStatus.PENDING_REGULARIZATION,
        )

        // Sauvegarde de la facture
        invoiceRepository.submitInvoice(degradedInvoice).getOrThrow()

        // Création de l'entrée de file
        val entry = SyncQueueEntry(
            id = Uuid.random().toString(),
            invoiceId = degradedInvoice.number,
            syncStatus = SyncStatus.PENDING,
            retryCount = 0L,
            lastError = null,
            originalChannel = channel,
            createdAt = clock.nowIso(),
            syncedAt = null,
        )

        syncQueueRepository.enqueue(entry).getOrThrow()
        entry
    }
}
