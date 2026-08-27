package com.ledgerhub.presentation.hello

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test

/**
 * Tests d'interface — Skill 2 : QA Automatisé
 *
 * STRATÉGIE DE TEST :
 * - On teste [HelloScreenContent] (composable stateless), PAS [HelloScreen].
 *   → Avantage : zéro ViewModel, zéro coroutine, zéro dépendance async dans les tests UI.
 *   → Les états sont injectés directement → déterminisme garanti.
 * - Les assertions utilisent [HelloTags] (constantes partagées avec le code prod).
 *   → Les textes peuvent changer (traduction, copywriting) sans casser les tests.
 *   → Les testTags sémantiques sont contractuels.
 *
 * COUVERTURE :
 * ✅ État Chargement  → spinner visible, message absent
 * ✅ État Succès      → message visible, spinner absent
 * ✅ État Erreur      → texte d'erreur + bouton Retry visibles
 * ✅ Bouton Retry     → activé et interactif en état Erreur
 */
@OptIn(ExperimentalTestApi::class)
class HelloScreenTest {

    // ── État Chargement ───────────────────────────────────────────────────────

    @Test
    fun whenLoading_loaderIsDisplayed_andMessageDoesNotExist() = runComposeUiTest {
        setContent {
            HelloScreenContent(
                uiState = HelloUiState(isLoading = true, message = "")
            )
        }

        // Le spinner doit être visible
        onNodeWithTag(HelloTags.LOADER).assertIsDisplayed()
        // Le message et l'erreur ne doivent pas exister dans l'arbre de composition
        onNodeWithTag(HelloTags.MESSAGE).assertDoesNotExist()
        onNodeWithTag(HelloTags.ERROR_TEXT).assertDoesNotExist()
        onNodeWithTag(HelloTags.RETRY_BUTTON).assertDoesNotExist()
    }

    // ── État Succès ───────────────────────────────────────────────────────────

    @Test
    fun whenSuccess_messageIsDisplayed_andLoaderDoesNotExist() = runComposeUiTest {
        val expectedMessage = "Bienvenue sur LedgerHub — Factur-X 2026 Ready!"

        setContent {
            HelloScreenContent(
                uiState = HelloUiState(
                    isLoading = false,
                    message   = expectedMessage
                )
            )
        }

        // Le message de bienvenue doit être visible
        onNodeWithTag(HelloTags.MESSAGE).assertIsDisplayed()
        // Le spinner et les éléments d'erreur ne doivent pas exister
        onNodeWithTag(HelloTags.LOADER).assertDoesNotExist()
        onNodeWithTag(HelloTags.ERROR_TEXT).assertDoesNotExist()
        onNodeWithTag(HelloTags.RETRY_BUTTON).assertDoesNotExist()
    }

    // ── État Erreur ───────────────────────────────────────────────────────────

    @Test
    fun whenError_errorTextAndRetryButtonAreDisplayed_andLoaderDoesNotExist() = runComposeUiTest {
        setContent {
            HelloScreenContent(
                uiState = HelloUiState(
                    isLoading = false,
                    error     = "Serveur Kubernetes indisponible (503)"
                )
            )
        }

        // L'erreur et le bouton Retry doivent être visibles
        onNodeWithTag(HelloTags.ERROR_TEXT).assertIsDisplayed()
        onNodeWithTag(HelloTags.RETRY_BUTTON).assertIsDisplayed()
        // Le bouton Retry doit être activé (pas grisé)
        onNodeWithTag(HelloTags.RETRY_BUTTON).assertIsEnabled()
        // Le spinner et le message de succès ne doivent pas exister
        onNodeWithTag(HelloTags.LOADER).assertDoesNotExist()
        onNodeWithTag(HelloTags.MESSAGE).assertDoesNotExist()
    }

    // ── Transition Chargement → Succès ────────────────────────────────────────

    @Test
    fun stateTransitionFromLoadingToSuccess_displaysMessageCorrectly() = runComposeUiTest {
        // setContent ne peut être appelé qu'une seule fois par test (toutes plateformes) —
        // la transition d'état se simule donc via un state mutable recomposé, pas un second setContent.
        var uiState by mutableStateOf(HelloUiState(isLoading = true))

        setContent { HelloScreenContent(uiState = uiState) }
        onNodeWithTag(HelloTags.LOADER).assertIsDisplayed()

        // Transition vers succès
        uiState = HelloUiState(isLoading = false, message = "Bienvenue !")

        onNodeWithTag(HelloTags.MESSAGE).assertIsDisplayed()
        onNodeWithTag(HelloTags.LOADER).assertDoesNotExist()
    }
}
