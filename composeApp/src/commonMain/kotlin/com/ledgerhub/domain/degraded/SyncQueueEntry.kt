package com.ledgerhub.domain.degraded

/**
 * Statut d'une entrée dans la file d'attente de synchronisation en mode dégradé (US-29).
 */
enum class SyncStatus(val rawValue: String) {
    PENDING("pending_sync"),
    SYNCED("synced"),
    FAILED("failed");

    companion object {
        fun fromRaw(raw: String): SyncStatus = entries.firstOrNull { it.rawValue == raw } ?: PENDING
    }
}

/**
 * Canal d'émission original de secours en mode dégradé.
 */
enum class DegradedChannel(val rawValue: String) {
    PDF_SIMPLE("pdf_simple"),
    PAPER("paper");

    companion object {
        fun fromRaw(raw: String): DegradedChannel = entries.firstOrNull { it.rawValue == raw } ?: PDF_SIMPLE
    }
}

/**
 * Entrée de la file d'attente de synchronisation (`SyncQueue`).
 */
data class SyncQueueEntry(
    val id: String,
    val invoiceId: String,
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val retryCount: Long = 0L,
    val lastError: String? = null,
    val originalChannel: DegradedChannel = DegradedChannel.PDF_SIMPLE,
    val createdAt: String,
    val syncedAt: String? = null,
)
