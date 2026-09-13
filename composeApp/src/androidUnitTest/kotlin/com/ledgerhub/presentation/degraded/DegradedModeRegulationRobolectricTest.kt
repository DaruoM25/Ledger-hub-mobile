package com.ledgerhub.presentation.degraded

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.ledgerhub.domain.degraded.DegradedModeNetworkState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Suite de tests Robolectric [N3a] pour le Mode Dégradé (US-29).
 * Valide le sélecteur réseau, l'affichage de la carte de synchronisation par lot et les interactions.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalTestApi::class)
class DegradedModeRegulationRobolectricTest {

    @Test
    fun networkSelector_togglesBetweenOperationalAndOutage() = runComposeUiTest {
        var networkState = DegradedModeNetworkState.OPERATIONAL

        setContent {
            NetworkSimulationSelector(
                networkState = networkState,
                onToggle = {
                    networkState = if (networkState == DegradedModeNetworkState.OPERATIONAL) {
                        DegradedModeNetworkState.OUTAGE
                    } else {
                        DegradedModeNetworkState.OPERATIONAL
                    }
                },
            )
        }

        onNodeWithTag(DegradedModeTags.NETWORK_TOGGLE)
            .assertIsDisplayed()
            .performClick()

        assertEquals(DegradedModeNetworkState.OUTAGE, networkState)
    }

    @Test
    fun syncBatchCard_displaysPendingCountAndTriggerAction() = runComposeUiTest {
        var syncTriggered = false
        val state = SyncQueueUiState(
            pendingCount = 3L,
            isSyncing = false,
        )

        setContent {
            SyncBatchCard(
                state = state,
                onSyncBatch = { syncTriggered = true },
            )
        }

        onNodeWithTag(DegradedModeTags.SYNC_BATCH_CARD)
            .assertIsDisplayed()

        onNodeWithTag(DegradedModeTags.SYNC_BATCH_COUNT)
            .assertIsDisplayed()

        onNodeWithTag(DegradedModeTags.SYNC_BATCH_BUTTON)
            .assertIsDisplayed()
            .performClick()

        assertTrue(syncTriggered)
    }

    @Test
    fun syncBatchCard_whenSyncing_showsProgressIndicator() = runComposeUiTest {
        val state = SyncQueueUiState(
            pendingCount = 2L,
            isSyncing = true,
        )

        setContent {
            SyncBatchCard(
                state = state,
                onSyncBatch = {},
            )
        }

        onNodeWithTag(DegradedModeTags.SYNC_BATCH_PROGRESS)
            .assertIsDisplayed()
    }
}
