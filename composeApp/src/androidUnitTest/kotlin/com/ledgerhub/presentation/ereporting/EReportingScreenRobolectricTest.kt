package com.ledgerhub.presentation.ereporting

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.ledgerhub.domain.ereporting.EReportingReport
import com.ledgerhub.domain.ereporting.EReportingRepository
import com.ledgerhub.domain.ereporting.EReportingStatus
import com.ledgerhub.domain.ereporting.EReportingType
import com.ledgerhub.domain.ereporting.TransmitEReportUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test

/**
 * Niveau 3 — rendu Robolectric de [EReportingScreen] et scénario de transmission complet
 * (clic « Transmettre PPF » → disparition du bouton → Snackbar portant le numéro d'accusé).
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class EReportingScreenRobolectricTest {

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
    fun tcUi02_screen_rendersTheDgfipHeader() = runComposeUiTest {
        setContent { MaterialTheme { EReportingScreen(viewModelWith("ACK-2026-5555")) } }

        onNodeWithTag(EReportingTags.BADGE).assertIsDisplayed()
        onNodeWithText(EREPORTING_HEADER).assertIsDisplayed()
        onNodeWithTag(EReportingTags.transmitButton("r1")).assertIsDisplayed()
    }

    @Test
    fun tcUi03_transmitClick_hidesButton_andShowsSnackbarWithAckNumber() = runComposeUiTest {
        setContent { MaterialTheme { EReportingScreen(viewModelWith("ACK-2026-7777")) } }

        onNodeWithTag(EReportingTags.transmitButton("r1")).assertIsDisplayed().performClick()

        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText("ACK-2026-7777", substring = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Le numéro d'accusé apparaît à deux endroits après transmission (Snackbar + carte) :
        // on vérifie le conteneur Snackbar et la ligne d'accusé de la carte séparément.
        onNodeWithTag(EReportingTags.SNACKBAR).assertIsDisplayed()
        onAllNodesWithTag(EReportingTags.transmitButton("r1")).assertCountEquals(0)
        onNodeWithTag(EReportingTags.ack("r1")).assertIsDisplayed()
    }
}
