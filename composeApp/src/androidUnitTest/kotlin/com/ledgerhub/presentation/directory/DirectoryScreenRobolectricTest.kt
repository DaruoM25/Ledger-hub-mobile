package com.ledgerhub.presentation.directory

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.ledgerhub.domain.directory.ResolveDirectoryEntryUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test

/**
 * Niveau 3 — rendu Robolectric de [DirectoryScreen] : feedback Luhn temps réel, activation du
 * bouton de recherche, affichage de la fiche résolue et du macaron de routage.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class DirectoryScreenRobolectricTest {

    private fun viewModel(vararg entries: com.ledgerhub.domain.directory.DirectoryEntry): DirectoryViewModel {
        val repo = FakeDirectoryRepository(entries.toList())
        return DirectoryViewModel(repo, ResolveDirectoryEntryUseCase(repo), UnconfinedTestDispatcher())
    }

    @Test
    fun typingInvalidIdentifier_showsLuhnKo_andKeepsSearchDisabled() = runComposeUiTest {
        setContent { DirectoryScreen(viewModel()) }

        onNodeWithTag(DirectoryTags.SEARCH_FIELD).performScrollTo().performTextInput("123456789")

        onNodeWithContentDescription("Clé de Luhn invalide").assertIsDisplayed()
        onNodeWithTag(DirectoryTags.SEARCH_BUTTON).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun typingValidSiren_enablesSearch() = runComposeUiTest {
        setContent { DirectoryScreen(viewModel()) }

        onNodeWithTag(DirectoryTags.SEARCH_FIELD).performScrollTo().performTextInput("732829320")

        onNodeWithTag(DirectoryTags.LUHN_INDICATOR).assertIsDisplayed()
        onNodeWithTag(DirectoryTags.SEARCH_BUTTON).performScrollTo().assertIsEnabled()
    }

    @Test
    fun searchingKnownSiren_showsResolvedCard_withVatAndRoutingBadge() = runComposeUiTest {
        setContent { DirectoryScreen(viewModel(renaultEntry())) }

        onNodeWithTag(DirectoryTags.SEARCH_FIELD).performScrollTo().performTextInput("732829320")
        onNodeWithTag(DirectoryTags.SEARCH_BUTTON).performScrollTo().performClick()

        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(DirectoryTags.RESULT_CARD).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag(DirectoryTags.RESULT_CARD).performScrollTo().assertIsDisplayed()
        onNodeWithTag(DirectoryTags.RESULT_COMPANY).assertIsDisplayed()
        onNodeWithTag(DirectoryTags.RESULT_VAT).performScrollTo().assertIsDisplayed()
        onNodeWithContentDescription("Routage PPF").assertIsDisplayed()
    }

    @Test
    fun searchingPdpEntry_showsPdpBadge_andIdentifier() = runComposeUiTest {
        setContent { DirectoryScreen(viewModel(danonePdpEntry())) }

        onNodeWithTag(DirectoryTags.SEARCH_FIELD).performScrollTo().performTextInput("552081317")
        onNodeWithTag(DirectoryTags.SEARCH_BUTTON).performScrollTo().performClick()

        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(DirectoryTags.RESULT_CARD).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithContentDescription("Routage PDP").assertIsDisplayed()
        onNodeWithTag(DirectoryTags.PDP_IDENTIFIER).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun searchingUnknownValidIdentifier_showsNotFound() = runComposeUiTest {
        setContent { DirectoryScreen(viewModel()) }

        onNodeWithTag(DirectoryTags.SEARCH_FIELD).performScrollTo().performTextInput("732829320")
        onNodeWithTag(DirectoryTags.SEARCH_BUTTON).performScrollTo().performClick()

        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(DirectoryTags.NOT_FOUND).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag(DirectoryTags.NOT_FOUND).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun mobDir01_searchingValidSiret_resolvesAndDisplaysResultCard_withSirenSiretAndVat() = runComposeUiTest {
        setContent { DirectoryScreen(viewModel(orangeEntry())) }

        val orangeSiret = "38012986648625"
        onNodeWithTag(DirectoryTags.SEARCH_FIELD).performScrollTo().performTextInput(orangeSiret)

        onNodeWithContentDescription("Clé de Luhn valide — SIRET").assertIsDisplayed()
        onNodeWithTag(DirectoryTags.SEARCH_BUTTON).performScrollTo().assertIsEnabled()
        onNodeWithTag(DirectoryTags.SEARCH_BUTTON).performScrollTo().performClick()

        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(DirectoryTags.RESULT_CARD).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag(DirectoryTags.RESULT_CARD).performScrollTo().assertIsDisplayed()
        onNodeWithTag(DirectoryTags.RESULT_COMPANY).assertIsDisplayed()
        onNodeWithTag(DirectoryTags.RESULT_VAT).performScrollTo().assertIsDisplayed()
        onNodeWithContentDescription("Routage PPF").assertIsDisplayed()
    }
}
