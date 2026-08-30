package com.ledgerhub.presentation.ereporting

import com.ledgerhub.domain.ereporting.EReportingReport
import com.ledgerhub.domain.ereporting.EReportingRepository
import com.ledgerhub.domain.ereporting.EReportingStatus

/**
 * Dépôt e-Reporting en mémoire, partagé par les tests de présentation (ViewModel + Robolectric).
 */
internal class FakeEReportingRepository(
    initial: List<EReportingReport> = emptyList(),
) : EReportingRepository {

    private val store = initial.associateBy { it.id }.toMutableMap()

    override suspend fun getAll(): List<EReportingReport> = store.values.toList()

    override suspend fun getById(id: String): EReportingReport? = store[id]

    override suspend fun create(report: EReportingReport): EReportingReport {
        store[report.id] = report
        return report
    }

    override suspend fun markAsAcknowledged(id: String, ackNumber: String) {
        val current = store.getValue(id)
        store[id] = current.copy(status = EReportingStatus.ACKNOWLEDGED, ackNumber = ackNumber)
    }
}
