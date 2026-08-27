package com.ledgerhub.presentation.hello

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test

/**
 * Équivalent Android de [HelloScreenTest] (commonTest, qui sert iosTest sans modification).
 *
 * `runComposeUiTest` a besoin d'un runtime Android réel (Build.FINGERPRINT, Looper) —
 * absent des tests JVM locaux "nus". @RunWith(RobolectricTestRunner) ne peut pas être
 * ajouté à la classe partagée sans casser la compilation iosTest (androidx.test/Robolectric
 * n'existent pas sur ce classpath) — d'où cette duplication ciblée, scoped à androidUnitTest.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalTestApi::class)
class HelloScreenRobolectricTest {

    @Test
    fun whenLoading_loaderIsDisplayed_andMessageDoesNotExist() = runComposeUiTest {
        setContent {
            HelloScreenContent(uiState = HelloUiState(isLoading = true, message = ""))
        }

        onNodeWithTag(HelloTags.LOADER).assertIsDisplayed()
        onNodeWithTag(HelloTags.MESSAGE).assertDoesNotExist()
        onNodeWithTag(HelloTags.ERROR_TEXT).assertDoesNotExist()
        onNodeWithTag(HelloTags.RETRY_BUTTON).assertDoesNotExist()
    }

    @Test
    fun whenSuccess_messageIsDisplayed_andLoaderDoesNotExist() = runComposeUiTest {
        val expectedMessage = "Bienvenue sur LedgerHub — Factur-X 2026 Ready!"

        setContent {
            HelloScreenContent(
                uiState = HelloUiState(isLoading = false, message = expectedMessage)
            )
        }

        onNodeWithTag(HelloTags.MESSAGE).assertIsDisplayed()
        onNodeWithTag(HelloTags.LOADER).assertDoesNotExist()
        onNodeWithTag(HelloTags.ERROR_TEXT).assertDoesNotExist()
        onNodeWithTag(HelloTags.RETRY_BUTTON).assertDoesNotExist()
    }

    @Test
    fun whenError_errorTextAndRetryButtonAreDisplayed_andLoaderDoesNotExist() = runComposeUiTest {
        setContent {
            HelloScreenContent(
                uiState = HelloUiState(
                    isLoading = false,
                    error = "Serveur Kubernetes indisponible (503)"
                )
            )
        }

        onNodeWithTag(HelloTags.ERROR_TEXT).assertIsDisplayed()
        onNodeWithTag(HelloTags.RETRY_BUTTON).assertIsDisplayed()
        onNodeWithTag(HelloTags.RETRY_BUTTON).assertIsEnabled()
        onNodeWithTag(HelloTags.LOADER).assertDoesNotExist()
        onNodeWithTag(HelloTags.MESSAGE).assertDoesNotExist()
    }

    @Test
    fun stateTransitionFromLoadingToSuccess_displaysMessageCorrectly() = runComposeUiTest {
        var uiState by mutableStateOf(HelloUiState(isLoading = true))

        setContent { HelloScreenContent(uiState = uiState) }
        onNodeWithTag(HelloTags.LOADER).assertIsDisplayed()

        uiState = HelloUiState(isLoading = false, message = "Bienvenue !")

        onNodeWithTag(HelloTags.MESSAGE).assertIsDisplayed()
        onNodeWithTag(HelloTags.LOADER).assertDoesNotExist()
    }
}
