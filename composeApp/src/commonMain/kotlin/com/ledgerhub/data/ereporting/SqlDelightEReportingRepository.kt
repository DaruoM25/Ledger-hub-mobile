package com.ledgerhub.data.ereporting

import com.ledgerhub.db.EReporting as EReportingRow
import com.ledgerhub.db.LedgerHubDatabase
import com.ledgerhub.domain.ereporting.EReportingReport
import com.ledgerhub.domain.ereporting.EReportingRepository
import com.ledgerhub.domain.ereporting.EReportingStatus
import com.ledgerhub.domain.ereporting.EReportingType
import com.ledgerhub.domain.time.Clock
import com.ledgerhub.domain.time.SystemClock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Persistance réelle des déclarations e-Reporting via SQLDelight (voir `EReporting.sq`).
 *
 * @param clock horloge injectée : la date de création est produite ici, jamais fournie par
 *   l'appelant, pour rester alignée avec le reste des horodatages du domaine.
 */
@OptIn(ExperimentalUuidApi::class)
class SqlDelightEReportingRepository(
    private val database: LedgerHubDatabase,
    private val clock: Clock = SystemClock,
) : EReportingRepository {

    override suspend fun getAll(): List<EReportingReport> =
        database.eReportingQueries.selectAll().executeAsList().map { it.toDomain() }

    override suspend fun getById(id: String): EReportingReport? =
        database.eReportingQueries.selectById(id).executeAsOneOrNull()?.toDomain()

    override suspend fun create(report: EReportingReport): EReportingReport {
        val stored = report.copy(
            id = report.id.ifBlank { Uuid.random().toString() },
            createdAt = report.createdAt.ifBlank { clock.nowIso() },
        )
        database.eReportingQueries.insertReport(
            id = stored.id,
            period = stored.period,
            type = stored.type.name,
            status = stored.status.name,
            ackNumber = stored.ackNumber,
            totalHt = stored.totalHt,
            totalVat = stored.totalVat,
            totalTtc = stored.totalTtc,
            transactionCount = stored.transactionCount.toLong(),
            createdAt = stored.createdAt,
        )
        return stored
    }

    override suspend fun markAsAcknowledged(id: String, ackNumber: String) {
        database.eReportingQueries.updateStatusAndAck(
            status = EReportingStatus.ACKNOWLEDGED.name,
            ackNumber = ackNumber,
            id = id,
        )
    }

    private fun EReportingRow.toDomain(): EReportingReport = EReportingReport(
        id = id,
        period = period,
        // Un libellé devenu inconnu (référentiel qui évoluerait) ne doit pas rendre la liste
        // illisible : on retombe sur une valeur par défaut plutôt que de lever.
        type = EReportingType.entries.firstOrNull { it.name == type } ?: EReportingType.B2C,
        status = EReportingStatus.entries.firstOrNull { it.name == status } ?: EReportingStatus.DRAFT,
        ackNumber = ackNumber,
        totalHt = totalHt,
        totalVat = totalVat,
        totalTtc = totalTtc,
        transactionCount = transactionCount.toInt(),
        createdAt = createdAt,
    )
}
