package com.ledgerhub.presentation.ereporting

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ledgerhub.domain.ereporting.EReportingReport
import com.ledgerhub.domain.ereporting.EReportingRepository
import com.ledgerhub.domain.ereporting.EReportingStatus
import com.ledgerhub.domain.ereporting.EReportingType
import com.ledgerhub.domain.ereporting.TransmitEReportUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class EReportingScreenInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun draft(id: String = "r1") = EReportingReport(
        id = id,
        period = "2026-02",
        type = EReportingType.B2C,
        status = EReportingStatus.DRAFT,
        ackNumber = null,
        totalHt = 100.0,
        totalVat = 20.0,
        totalTtc = 120.0,
        transactionCount = 4,
        createdAt = "2026-08-30T09:00:00Z",
    )

    private fun viewModelWith(ackNumber: String): EReportingViewModel {
        val repository = FakeEReportingRepository(listOf(draft()))
        return EReportingViewModel(
            repository,
            TransmitEReportUseCase(repository) { ackNumber },
            UnconfinedTestDispatcher(),
        )
    }

    @Test
    fun screen_transmitsPpf_andShowsAckOnRealDevice() {
        val ackNumber = "ACK-2026-7777"
        val viewModel = viewModelWith(ackNumber)

        composeRule.setContent {
            MaterialTheme {
                EReportingScreen(viewModel)
            }
        }

        composeRule.onNodeWithText(EREPORTING_HEADER, substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag(EReportingTags.transmitButton("r1")).assertIsDisplayed().performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("ACK-2026-7777", substring = true).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag(EReportingTags.SNACKBAR).assertIsDisplayed()
        composeRule.onNodeWithTag(EReportingTags.ack("r1")).assertIsDisplayed()
        composeRule.onAllNodesWithText("ACK-2026-7777", substring = true).assertCountEquals(2)
    }

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
}
