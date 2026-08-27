package com.ledgerhub.presentation.hello

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests unitaires ViewModel — Skill 2 : QA Automatisé
 *
 * STRATÉGIE DE TEST :
 * - [StandardTestDispatcher] + [runTest] = contrôle total du temps virtuel.
 * - Aucun thread réel créé → tests ultra-rapides et déterministes.
 * - [advanceUntilIdle] simule l'écoulement du temps, y compris le delay(600ms).
 * - [runCurrent] exécute les tâches en attente sans avancer le temps virtuel.
 *
 * COUVERTURE :
 * ✅ État initial       → isLoading = true (avant exécution)
 * ✅ Après chargement   → isLoading = false, message non vide, pas d'erreur
 * ✅ Cycle Retry        → retour en loading puis succès après un second chargement
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HelloViewModelTest {

    // ── État initial ──────────────────────────────────────────────────────────

    @Test
    fun initialState_isLoadingTrue_messageIsEmpty_noError() = runTest {
        // Le StandardTestDispatcher est lié au testScheduler de runTest.
        // → les coroutines ne s'exécutent PAS avant advanceUntilIdle() / runCurrent()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel  = HelloViewModel(dispatcher = dispatcher)

        // À T=0, avant toute exécution de coroutine :
        // L'état par défaut de MutableStateFlow(HelloUiState()) a isLoading=true
        assertTrue(
            actual  = viewModel.uiState.value.isLoading,
            message = "L'état initial doit être isLoading=true"
        )
        assertTrue(
            actual  = viewModel.uiState.value.message.isEmpty(),
            message = "Le message doit être vide à l'initialisation"
        )
        assertNull(
            actual  = viewModel.uiState.value.error,
            message = "Pas d'erreur à l'initialisation"
        )
    }

    // ── Après chargement complet ───────────────────────────────────────────────

    @Test
    fun afterLoad_isLoadingFalse_messageIsNotEmpty_noError() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel  = HelloViewModel(dispatcher = dispatcher)

        // advanceUntilIdle() avance le temps virtuel jusqu'à ce que toutes
        // les coroutines soient terminées, y compris le delay(600L).
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(
            actual  = state.isLoading,
            message = "Après le chargement, isLoading doit être false"
        )
        assertTrue(
            actual  = state.message.isNotEmpty(),
            message = "Le message de bienvenue ne doit pas être vide"
        )
        assertNull(
            actual  = state.error,
            message = "Aucune erreur ne doit être présente après un chargement réussi"
        )
    }

    // ── Cycle Retry ──────────────────────────────────────────────────────────

    @Test
    fun retryIntent_firstResetsToLoading_thenResolvesToSuccess() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel  = HelloViewModel(dispatcher = dispatcher)

        // --- Étape 1 : Premier chargement réussi ---
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLoading, "Doit être loaded après init")

        // --- Étape 2 : Déclenche le Retry ---
        viewModel.processIntent(HelloIntent.RetryLoad)

        // runCurrent() exécute les tâches en attente jusqu'au premier point de suspension.
        // → La coroutine de loadWelcomeMessage() tourne jusqu'à delay(600L).
        // → Le StateFlow est mis à jour avec isLoading=true AVANT le delay.
        runCurrent()
        assertTrue(
            actual  = viewModel.uiState.value.isLoading,
            message = "Juste après RetryLoad, doit repasser en isLoading=true"
        )
        assertNull(
            actual  = viewModel.uiState.value.error,
            message = "L'erreur doit être effacée dès le début du retry"
        )

        // --- Étape 3 : Laisse le retry se terminer ---
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLoading, "Après retry complet, isLoading=false")
        assertTrue(viewModel.uiState.value.message.isNotEmpty(), "Message présent après retry")
    }

    // ── Nettoyage du scope ─────────────────────────────────────────────────────

    @Test
    fun onCleared_cancelsScope_noFurtherStateUpdates() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel  = HelloViewModel(dispatcher = dispatcher)

        // Annule le scope avant que la coroutine ne se termine
        viewModel.onCleared()

        // advanceUntilIdle ne doit pas provoquer d'exception malgré le scope annulé
        advanceUntilIdle()

        // L'état reste à l'état initial (isLoading=true) car la coroutine a été annulée
        assertTrue(
            actual  = viewModel.uiState.value.isLoading,
            message = "Le scope annulé ne doit pas permettre de mise à jour d'état"
        )
    }
}
