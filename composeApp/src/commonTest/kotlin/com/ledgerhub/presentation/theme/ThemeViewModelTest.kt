package com.ledgerhub.presentation.theme

import com.ledgerhub.domain.theme.ThemeMode
import com.ledgerhub.domain.theme.ThemePreferenceRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Niveau 2 (US-25) — persistance de la préférence de thème et émission de l'état.
 *
 * Le dépôt est un double en mémoire : ce qui est vérifié ici, c'est le **contrat** (ce qui est lu,
 * ce qui est écrit, dans quel ordre, et ce qui est émis), pas le SQL. Le SQL a son propre test —
 * `SqlDelightThemePreferenceRepositoryTest`, qui exige un pilote JDBC absent d'iosTest.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ThemeViewModelTest {

    /**
     * Dépôt en mémoire. Il compte ses écritures : « le thème a changé à l'écran » et « le thème a
     * été persisté » sont deux affirmations distinctes, et l'US exige les deux.
     */
    private class FakeThemePreferenceRepository(
        initial: ThemeMode? = null,
        private val failOnSave: Boolean = false,
        private val failOnLoad: Boolean = false,
    ) : ThemePreferenceRepository {
        var stored: ThemeMode? = initial
            private set
        val savedModes = mutableListOf<ThemeMode>()

        override suspend fun loadThemeMode(): Result<ThemeMode> =
            if (failOnLoad) Result.failure(IllegalStateException("base verrouillée"))
            else Result.success(stored ?: ThemeMode.Default)

        override suspend fun saveThemeMode(mode: ThemeMode): Result<Unit> {
            savedModes += mode
            if (failOnSave) return Result.failure(IllegalStateException("disque plein"))
            stored = mode
            return Result.success(Unit)
        }
    }

    // ── Chargement ──────────────────────────────────────────────────────────

    @Test
    fun beforeLoading_theStateIsTheDefaultAndNotMarkedLoaded() = runTest {
        val viewModel = ThemeViewModel(FakeThemePreferenceRepository(), StandardTestDispatcher(testScheduler))

        assertEquals(ThemeMode.Default, viewModel.uiState.value.mode)
        assertFalse(viewModel.uiState.value.isLoaded, "L'état ne peut pas être chargé avant Load")
    }

    @Test
    fun loading_anEmptyRepository_yieldsTheDefaultDarkTheme() = runTest {
        val viewModel = ThemeViewModel(FakeThemePreferenceRepository(), StandardTestDispatcher(testScheduler))

        viewModel.processIntent(ThemeIntent.Load)
        advanceUntilIdle()

        assertEquals(ThemeMode.DARK, viewModel.uiState.value.mode)
        assertTrue(viewModel.uiState.value.isLoaded)
    }

    @Test
    fun loading_aStoredPreference_emitsIt() = runTest {
        val repository = FakeThemePreferenceRepository(initial = ThemeMode.LIGHT)
        val viewModel = ThemeViewModel(repository, StandardTestDispatcher(testScheduler))

        viewModel.processIntent(ThemeIntent.Load)
        advanceUntilIdle()

        assertEquals(ThemeMode.LIGHT, viewModel.uiState.value.mode)
    }

    /** Une lecture en échec ne suspend pas le démarrage : l'app s'ouvre sur son thème par défaut. */
    @Test
    fun aFailingLoad_leavesTheDefaultThemeAndUnblocksTheShell() = runTest {
        val viewModel = ThemeViewModel(
            FakeThemePreferenceRepository(failOnLoad = true),
            StandardTestDispatcher(testScheduler),
        )

        viewModel.processIntent(ThemeIntent.Load)
        advanceUntilIdle()

        assertEquals(ThemeMode.Default, viewModel.uiState.value.mode)
        assertTrue(viewModel.uiState.value.isLoaded)
    }

    // ── Bascule ─────────────────────────────────────────────────────────────

    @Test
    fun toggling_emitsTheNewModeAndPersistsIt() = runTest {
        val repository = FakeThemePreferenceRepository()
        val viewModel = ThemeViewModel(repository, StandardTestDispatcher(testScheduler))
        viewModel.processIntent(ThemeIntent.Load)
        advanceUntilIdle()

        viewModel.processIntent(ThemeIntent.Toggle(systemIsDark = false))
        advanceUntilIdle()

        assertEquals(ThemeMode.LIGHT, viewModel.uiState.value.mode)
        assertEquals(listOf(ThemeMode.LIGHT), repository.savedModes)
        assertEquals(ThemeMode.LIGHT, repository.stored)
    }

    /** Le cycle complet exigé par le cahier des charges, vu depuis l'état émis et depuis la base. */
    @Test
    fun togglingTwice_returnsToDarkAndPersistsBothSteps() = runTest {
        val repository = FakeThemePreferenceRepository()
        val viewModel = ThemeViewModel(repository, StandardTestDispatcher(testScheduler))
        viewModel.processIntent(ThemeIntent.Load)
        advanceUntilIdle()

        viewModel.processIntent(ThemeIntent.Toggle(systemIsDark = false))
        advanceUntilIdle()
        viewModel.processIntent(ThemeIntent.Toggle(systemIsDark = false))
        advanceUntilIdle()

        assertEquals(ThemeMode.DARK, viewModel.uiState.value.mode)
        assertEquals(listOf(ThemeMode.LIGHT, ThemeMode.DARK), repository.savedModes)
    }

    @Test
    fun togglingFromSystem_persistsAnExplicitMode() = runTest {
        val repository = FakeThemePreferenceRepository(initial = ThemeMode.SYSTEM)
        val viewModel = ThemeViewModel(repository, StandardTestDispatcher(testScheduler))
        viewModel.processIntent(ThemeIntent.Load)
        advanceUntilIdle()

        viewModel.processIntent(ThemeIntent.Toggle(systemIsDark = true))
        advanceUntilIdle()

        assertEquals(ThemeMode.LIGHT, viewModel.uiState.value.mode)
        assertEquals(ThemeMode.LIGHT, repository.stored)
    }

    @Test
    fun selecting_aModeExplicitly_persistsIt() = runTest {
        val repository = FakeThemePreferenceRepository()
        val viewModel = ThemeViewModel(repository, StandardTestDispatcher(testScheduler))

        viewModel.processIntent(ThemeIntent.Select(ThemeMode.SYSTEM))
        advanceUntilIdle()

        assertEquals(ThemeMode.SYSTEM, viewModel.uiState.value.mode)
        assertEquals(ThemeMode.SYSTEM, repository.stored)
    }

    // ── Persistance de bout en bout ─────────────────────────────────────────

    /**
     * La preuve que demande l'US : ce qui a été basculé dans une session est **relu** par la
     * suivante. Deux ViewModels sur le même dépôt simulent le redémarrage de l'application.
     */
    @Test
    fun aToggledTheme_isReadBackByTheNextSession() = runTest {
        val repository = FakeThemePreferenceRepository()
        val first = ThemeViewModel(repository, StandardTestDispatcher(testScheduler))
        first.processIntent(ThemeIntent.Load)
        advanceUntilIdle()
        first.processIntent(ThemeIntent.Toggle(systemIsDark = false))
        advanceUntilIdle()
        first.onCleared()

        val second = ThemeViewModel(repository, StandardTestDispatcher(testScheduler))
        second.processIntent(ThemeIntent.Load)
        advanceUntilIdle()

        assertEquals(ThemeMode.LIGHT, second.uiState.value.mode)
    }

    /**
     * Arbitrage consigné : afficher d'abord, persister ensuite. Un échec d'écriture ne fait pas
     * revenir le thème en arrière sous les yeux de l'utilisateur — la préférence sera simplement
     * oubliée au prochain lancement.
     */
    @Test
    fun aFailingSave_doesNotRevertTheThemeOnScreen() = runTest {
        val repository = FakeThemePreferenceRepository(failOnSave = true)
        val viewModel = ThemeViewModel(repository, StandardTestDispatcher(testScheduler))
        viewModel.processIntent(ThemeIntent.Load)
        advanceUntilIdle()

        viewModel.processIntent(ThemeIntent.Toggle(systemIsDark = false))
        advanceUntilIdle()

        assertEquals(ThemeMode.LIGHT, viewModel.uiState.value.mode)
        assertEquals(listOf(ThemeMode.LIGHT), repository.savedModes)
        assertNull(repository.stored, "L'écriture a échoué : rien ne doit être persisté")
    }
}
