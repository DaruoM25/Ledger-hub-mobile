package com.ledgerhub.data.vault

import com.ledgerhub.db.DigitalArchive as DigitalArchiveRow
import com.ledgerhub.db.LedgerHubDatabase
import com.ledgerhub.db.PisteAuditLog as PisteAuditLogRow
import com.ledgerhub.domain.audit.sha256Hex
import com.ledgerhub.domain.vault.DigitalArchive
import com.ledgerhub.domain.vault.PisteAuditEntry
import com.ledgerhub.domain.vault.SealStatus
import com.ledgerhub.domain.vault.VaultDocumentType
import com.ledgerhub.domain.vault.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SqlDelightVaultRepository(
    private val database: LedgerHubDatabase,
) : VaultRepository {

    override suspend fun saveArchive(archive: DigitalArchive): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            database.digitalArchiveQueries.insertOrReplace(
                id = archive.id,
                invoiceNumber = archive.invoiceNumber,
                documentType = archive.documentType.name,
                payloadHash = archive.payloadHash,
                archiveSize = archive.archiveSize,
                sealedAt = archive.sealedAt,
                sealedBy = archive.sealedBy,
                status = archive.status.name,
            )
            Unit
        }
    }

    override suspend fun fetchArchives(): Result<List<DigitalArchive>> = withContext(Dispatchers.Default) {
        runCatching {
            database.digitalArchiveQueries.selectAll().executeAsList().map { it.toDomain() }
        }
    }

    override suspend fun getArchiveByInvoiceNumber(invoiceNumber: String): Result<DigitalArchive?> = withContext(Dispatchers.Default) {
        runCatching {
            database.digitalArchiveQueries.selectByInvoiceNumber(invoiceNumber).executeAsOneOrNull()?.toDomain()
        }
    }

    override suspend fun countSealedDocuments(): Result<Long> = withContext(Dispatchers.Default) {
        runCatching {
            database.digitalArchiveQueries.countSealedDocuments().executeAsOne()
        }
    }

    override suspend fun appendAuditEntry(
        id: String,
        invoiceNumber: String?,
        action: String,
        details: String,
        timestamp: String,
    ): Result<PisteAuditEntry> = withContext(Dispatchers.Default) {
        runCatching {
            val latest = database.pisteAuditLogQueries.selectLatestEntry().executeAsOneOrNull()
            val previousChecksum = latest?.checksum

            // Chaînage cryptographique : sha256(id|invoiceNumber|action|timestamp|previousChecksum)
            val source = listOf(
                id,
                invoiceNumber.orEmpty(),
                action,
                timestamp,
                previousChecksum.orEmpty(),
            ).joinToString("|")

            val checksum = sha256Hex(source)

            database.pisteAuditLogQueries.insert(
                id = id,
                invoiceNumber = invoiceNumber,
                action = action,
                details = details,
                timestamp = timestamp,
                previousChecksum = previousChecksum,
                checksum = checksum,
            )

            PisteAuditEntry(
                id = id,
                invoiceNumber = invoiceNumber,
                action = action,
                details = details,
                timestamp = timestamp,
                previousChecksum = previousChecksum,
                checksum = checksum,
            )
        }
    }

    override suspend fun fetchAuditTrail(): Result<List<PisteAuditEntry>> = withContext(Dispatchers.Default) {
        runCatching {
            database.pisteAuditLogQueries.selectAllChronological().executeAsList().map { it.toDomain() }
        }
    }

    override suspend fun getLatestAuditEntry(): Result<PisteAuditEntry?> = withContext(Dispatchers.Default) {
        runCatching {
            database.pisteAuditLogQueries.selectLatestEntry().executeAsOneOrNull()?.toDomain()
        }
    }

    private fun DigitalArchiveRow.toDomain(): DigitalArchive = DigitalArchive(
        id = id,
        invoiceNumber = invoiceNumber,
        documentType = VaultDocumentType.entries.firstOrNull { it.name == documentType } ?: VaultDocumentType.INVOICE_FACTURX,
        payloadHash = payloadHash,
        archiveSize = archiveSize,
        sealedAt = sealedAt,
        sealedBy = sealedBy,
        status = SealStatus.entries.firstOrNull { it.name == status } ?: SealStatus.SEALED,
    )

    private fun PisteAuditLogRow.toDomain(): PisteAuditEntry = PisteAuditEntry(
        id = id,
        invoiceNumber = invoiceNumber,
        action = action,
        details = details,
        timestamp = timestamp,
        previousChecksum = previousChecksum,
        checksum = checksum,
    )
}
