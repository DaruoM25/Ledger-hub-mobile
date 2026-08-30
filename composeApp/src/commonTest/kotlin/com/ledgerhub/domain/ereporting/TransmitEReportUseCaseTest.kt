package com.ledgerhub.domain.ereporting

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Dépôt en mémoire pour piloter le cas d'usage sans base. */
private class FakeEReportingRepository(
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

/**
 * Niveau 2 — cas d'usage [TransmitEReportUseCase] contre un dépôt en mémoire.
 */
class TransmitEReportUseCaseTest {

    private fun draft(id: String = "rep-1") = EReportingReport(
        id = id,
        period = "2026-03",
        type = EReportingType.PAYMENTS,
        status = EReportingStatus.DRAFT,
        ackNumber = null,
        totalHt = 100.0,
        totalVat = 20.0,
        totalTtc = 120.0,
        transactionCount = 1,
        createdAt = "2026-08-30T09:00:00Z",
    )

    @Test
    fun tcInt03_transmittingADraft_yieldsAckPrefixedNumber_andAcknowledgedStatus() = runTest {
        val repository = FakeEReportingRepository(listOf(draft()))
        val useCase = TransmitEReportUseCase(repository) { "ACK-2026-1234" }

        val result = useCase("rep-1")

        assertEquals(EReportingStatus.ACKNOWLEDGED, result.status)
        assertEquals("ACK-2026-1234", result.ackNumber)
        assertTrue(result.ackNumber!!.startsWith("ACK-2026-"))
        // La transition est bien persistée, pas seulement retournée.
        assertEquals(EReportingStatus.ACKNOWLEDGED, repository.getById("rep-1")!!.status)
        assertEquals("ACK-2026-1234", repository.getById("rep-1")!!.ackNumber)
    }

    @Test
    fun tcInt03bis_defaultGenerator_alsoProducesTheReformPrefix() = runTest {
        val repository = FakeEReportingRepository(listOf(draft()))
        val useCase = TransmitEReportUseCase(repository)

        val result = useCase("rep-1")

        assertTrue(result.ackNumber!!.startsWith("ACK-2026-"))
        assertEquals(13, result.ackNumber!!.length)
    }

    @Test
    fun tcInt04_retransmittingAnAcknowledgedReport_throwsAlreadyAcknowledgedException() = runTest {
        val acknowledged = draft().copy(
            status = EReportingStatus.ACKNOWLEDGED,
            ackNumber = "ACK-2026-0001",
        )
        val repository = FakeEReportingRepository(listOf(acknowledged))
        val useCase = TransmitEReportUseCase(repository)

        val error = assertFailsWith<AlreadyAcknowledgedException> { useCase("rep-1") }
        assertEquals("rep-1", error.reportId)
        // L'accusé d'origine n'a pas bougé.
        assertEquals("ACK-2026-0001", repository.getById("rep-1")!!.ackNumber)
    }
}
