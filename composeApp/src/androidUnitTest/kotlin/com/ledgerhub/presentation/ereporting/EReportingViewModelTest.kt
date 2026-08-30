package com.ledgerhub.presentation.ereporting

import com.ledgerhub.domain.ereporting.EReportingReport
import com.ledgerhub.domain.ereporting.EReportingRepository
import com.ledgerhub.domain.ereporting.EReportingStatus
import com.ledgerhub.domain.ereporting.EReportingType
import com.ledgerhub.domain.ereporting.TransmitEReportUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Niveau 3 — test unitaire de [EReportingViewModel] : transitions de [EReportingUiState].
 *
 * Le ViewModel prend son dispatcher par injection : un [UnconfinedTestDispatcher] suffit à rendre
 * les coroutines déterministes, sans `Dispatchers.setMain`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EReportingViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private fun report(id: String, status: EReportingStatus = EReportingStatus.DRAFT) = EReportingReport(
        id = id,
        period = "2026-02",
        type = EReportingType.B2C,
        status = status,
        ackNumber = if (status == EReportingStatus.ACKNOWLEDGED) "ACK-2026-0001" else null,
        totalHt = 10.0,
        totalVat = 2.0,
        totalTtc = 12.0,
        transactionCount = 1,
        createdAt = "2026-08-30T09:00:00Z",
    )

    @Test
    fun tcUi01a_load_populatesReports_andClearsLoading() = runTest {
        val repository = FakeEReportingRepository(listOf(report("r1")))
        val viewModel = EReportingViewModel(
            repository,
            TransmitEReportUseCase(repository) { "ACK-2026-9999" },
            dispatcher,
        )

        val state = viewModel.uiState.value
        assertEquals(1, state.reports.size)
        assertTrue(!state.isLoading)
        assertNull(state.errorMessage)
        assertNull(state.transmittingId)
    }

    @Test
    fun tcUi01b_transmitDraft_movesToAcknowledged_andEmitsAckMessage() = runTest {
        val repository = FakeEReportingRepository(listOf(report("r1")))
        val viewModel = EReportingViewModel(
            repository,
            TransmitEReportUseCase(repository) { "ACK-2026-4242" },
            dispatcher,
        )

        viewModel.processIntent(EReportingIntent.Transmit("r1"))

        val state = viewModel.uiState.value
        assertEquals(EReportingStatus.ACKNOWLEDGED, state.reports.single().status)
        assertEquals("ACK-2026-4242", state.reports.single().ackNumber)
        assertNull(state.transmittingId)
        assertTrue(state.ackMessage!!.contains("ACK-2026-4242"))
        assertNull(state.errorMessage)
    }

    @Test
    fun tcUi01c_transmitAlreadyAcknowledged_setsErrorMessage_withoutAck() = runTest {
        val repository = FakeEReportingRepository(listOf(report("r1", EReportingStatus.ACKNOWLEDGED)))
        val viewModel = EReportingViewModel(repository, TransmitEReportUseCase(repository), dispatcher)

        viewModel.processIntent(EReportingIntent.Transmit("r1"))

        val state = viewModel.uiState.value
        assertNull(state.transmittingId)
        assertTrue(state.errorMessage!!.contains("409"))
        assertNull(state.ackMessage)
    }

    @Test
    fun tcUi01d_messageShown_clearsBothMessages() = runTest {
        val repository = FakeEReportingRepository(listOf(report("r1")))
        val viewModel = EReportingViewModel(
            repository,
            TransmitEReportUseCase(repository) { "ACK-2026-4242" },
            dispatcher,
        )
        viewModel.processIntent(EReportingIntent.Transmit("r1"))

        viewModel.processIntent(EReportingIntent.MessageShown)

        val state = viewModel.uiState.value
        assertNull(state.ackMessage)
        assertNull(state.errorMessage)
    }
}
