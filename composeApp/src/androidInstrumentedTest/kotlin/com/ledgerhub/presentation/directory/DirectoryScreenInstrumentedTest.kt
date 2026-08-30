package com.ledgerhub.presentation.directory

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ledgerhub.domain.directory.DirectoryEntry
import com.ledgerhub.domain.directory.DirectoryRepository
import com.ledgerhub.domain.directory.DirectoryStatus
import com.ledgerhub.domain.directory.FrenchVatNumber
import com.ledgerhub.domain.directory.ResolveDirectoryEntryUseCase
import com.ledgerhub.domain.directory.RoutingMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Niveau 3 — exécution réelle sur émulateur Pixel 5 API 35 (`connectedDebugAndroidTest`).
 * Rendu Compose réel via [createAndroidComposeRule], assertions en [assertIsDisplayed].
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class DirectoryScreenInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private class FakeDirectoryRepository(entries: List<DirectoryEntry>) : DirectoryRepository {
        private val store = entries.associateBy { it.siren }.toMutableMap()
        override suspend fun findBySiren(siren: String) = store[siren]
        override suspend fun findBySiret(siret: String) = store.values.firstOrNull { it.siret == siret }
        override suspend fun all() = store.values.toList()
        override suspend fun cache(entry: DirectoryEntry) { store[entry.siren] = entry }
    }

    private fun renault() = DirectoryEntry(
        siren = "732829320",
        siret = "73282932000074",
        companyName = "RENAULT SAS",
        vatNumber = FrenchVatNumber.format("732829320"),
        routingMode = RoutingMode.PPF,
        pdpIdentifier = null,
        isVatSubject = true,
        status = DirectoryStatus.ACTIVE,
        lastSyncAt = "2026-08-30T09:00:00Z",
    )

    private fun viewModel(): DirectoryViewModel {
        val repo = FakeDirectoryRepository(listOf(renault()))
        return DirectoryViewModel(repo, ResolveDirectoryEntryUseCase(repo), UnconfinedTestDispatcher())
    }

    @Test
    fun luhnFeedback_isLiveOnInput_andSearchGatesOnValidity() {
        composeRule.setContent { MaterialTheme { DirectoryScreen(viewModel()) } }

        composeRule.onNodeWithTag(DirectoryTags.SCREEN).assertIsDisplayed()

        composeRule.onNodeWithTag(DirectoryTags.SEARCH_FIELD).performTextInput("12345678")
        composeRule.onNodeWithTag(DirectoryTags.SEARCH_BUTTON).performScrollTo().assertIsNotEnabled()

        composeRule.onNodeWithTag(DirectoryTags.SEARCH_FIELD).performTextInput("9") // -> 123456789, Luhn KO
        composeRule.onNodeWithContentDescription("Clé de Luhn invalide").assertIsDisplayed()
    }

    @Test
    fun searchingValidSiren_showsCompany_vat_andRoutingBadge_onDevice() {
        composeRule.setContent { MaterialTheme { DirectoryScreen(viewModel()) } }

        composeRule.onNodeWithTag(DirectoryTags.SEARCH_FIELD).performTextInput("732829320")
        composeRule.onNodeWithTag(DirectoryTags.SEARCH_BUTTON).performScrollTo().assertIsEnabled()
        composeRule.onNodeWithTag(DirectoryTags.SEARCH_BUTTON).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(DirectoryTags.RESULT_CARD).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(DirectoryTags.RESULT_COMPANY).assertIsDisplayed()
        composeRule.onNodeWithTag(DirectoryTags.RESULT_VAT).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Routage PPF").assertIsDisplayed()
    }
}
