package com.ledgerhub.presentation.integrations

import com.ledgerhub.domain.integrations.IntegrationCatalog
import com.ledgerhub.domain.integrations.IntegrationModule
import com.ledgerhub.domain.integrations.IntegrationStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * Niveau 2 (US-20) — état UI du hub d'intégrations.
 *
 * Placé en `commonTest` comme `CommandPaletteViewModelTest` : le ViewModel est du Kotlin pur, il
 * n'a aucune raison de n'être éprouvé que sur la JVM Android. Il reste exécuté par
 * `testDebugUnitTest`, le lanceur du niveau 2, **et** par la cible iOS.
 */
class IntegrationsHubViewModelTest {

    private fun viewModel() = IntegrationsHubViewModel()

    // ── État initial ────────────────────────────────────────────────────────

    @Test
    fun theInitialState_exposesTheOrderedCatalog_withNoNotice() {
        val state = viewModel().uiState.value

        assertEquals(IntegrationCatalog.ordered(), state.modules)
        assertEquals(4, state.modules.size)
        assertNull(state.noticeModule)
    }

    @Test
    fun theInitialState_listsBetaModulesFirst() {
        val modules = viewModel().uiState.value.modules

        assertEquals(IntegrationStatus.BETA, modules[0].status)
        assertEquals(IntegrationStatus.BETA, modules[1].status)
        assertEquals(IntegrationStatus.COMING_SOON, modules[2].status)
        assertEquals(IntegrationStatus.COMING_SOON, modules[3].status)
    }

    // ── Sélection d'un module ───────────────────────────────────────────────

    @Test
    fun selectingAModule_publishesItsLockedNotice() {
        val viewModel = viewModel()

        viewModel.processIntent(
            IntegrationsHubIntent.ModuleSelected(IntegrationModule.STRIPE_PAYMENTS),
        )

        assertEquals(IntegrationModule.STRIPE_PAYMENTS, viewModel.uiState.value.noticeModule)
    }

    /** C'est toujours le dernier geste qui est expliqué : le bandeau se remplace, il ne s'empile pas. */
    @Test
    fun selectingASecondModule_replacesTheNotice() {
        val viewModel = viewModel()

        viewModel.processIntent(
            IntegrationsHubIntent.ModuleSelected(IntegrationModule.STRIPE_PAYMENTS),
        )
        viewModel.processIntent(IntegrationsHubIntent.ModuleSelected(IntegrationModule.BANK_SYNC))

        assertEquals(IntegrationModule.BANK_SYNC, viewModel.uiState.value.noticeModule)
    }

    @Test
    fun everyModule_canPublishItsNotice() {
        IntegrationModule.entries.forEach { module ->
            val viewModel = viewModel()
            viewModel.processIntent(IntegrationsHubIntent.ModuleSelected(module))
            assertEquals(module, viewModel.uiState.value.noticeModule)
        }
    }

    // ── Acquittement ────────────────────────────────────────────────────────

    @Test
    fun dismissingTheNotice_returnsToTheRestingState() {
        val viewModel = viewModel()
        viewModel.processIntent(IntegrationsHubIntent.ModuleSelected(IntegrationModule.FEC_EXPORT))

        viewModel.processIntent(IntegrationsHubIntent.NoticeDismissed)

        assertNull(viewModel.uiState.value.noticeModule)
    }

    /** Acquitter sans bandeau affiché ne doit pas lever ni salir l'état. */
    @Test
    fun dismissingWithoutNotice_isANoOp() {
        val viewModel = viewModel()

        viewModel.processIntent(IntegrationsHubIntent.NoticeDismissed)

        assertNull(viewModel.uiState.value.noticeModule)
        assertEquals(IntegrationCatalog.ordered(), viewModel.uiState.value.modules)
    }

    // ── Invariants ──────────────────────────────────────────────────────────

    /** Aucune intention ne peut ouvrir un module : le hub est une vitrine, pas un interrupteur. */
    @Test
    fun noIntent_unlocksAModule_orAltersTheCatalog() {
        val viewModel = viewModel()

        IntegrationModule.entries.forEach { module ->
            viewModel.processIntent(IntegrationsHubIntent.ModuleSelected(module))
            viewModel.processIntent(IntegrationsHubIntent.NoticeDismissed)
        }

        val state = viewModel.uiState.value
        assertEquals(IntegrationCatalog.ordered(), state.modules)
        assertFalse(state.hasUnlockedModule)
        assertEquals(
            IntegrationCatalog.ordered().map { it.status },
            state.modules.map { it.status },
            "Les statuts du catalogue ont bougé sous l'effet d'une intention",
        )
    }
}
