package com.ledgerhub.presentation.settings

import com.ledgerhub.domain.auth.AuthRepository
import com.ledgerhub.domain.auth.UserAccount
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.domain.settings.TaxSettings
import com.ledgerhub.domain.settings.TaxSettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeTaxSettingsRepository(var stored: TaxSettings? = null) : TaxSettingsRepository {
    var saveCount = 0
    override suspend fun loadSettings(): Result<TaxSettings> =
        Result.success(stored ?: TaxSettings.Default)

    override suspend fun saveSettings(settings: TaxSettings): Result<Unit> {
        stored = settings
        saveCount++
        return Result.success(Unit)
    }
}

private class FakeAuthRepository : AuthRepository {
    var logoutCalled = false

    override suspend fun login(email: String, password: String): Result<UserAccount> =
        Result.success(UserAccount(email, "", ""))

    override suspend fun register(account: UserAccount, password: String): Result<UserAccount> =
        Result.success(account)

    override suspend fun requestPasswordReset(email: String): Result<Unit> = Result.success(Unit)

    override suspend fun deleteAccount(email: String): Result<Unit> = Result.success(Unit)

    override suspend fun logout(): Result<Unit> {
        logoutCalled = true
        return Result.success(Unit)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TaxSettingsViewModelTest {

    private fun viewModel(
        repository: TaxSettingsRepository,
        scheduler: kotlinx.coroutines.test.TestCoroutineScheduler,
        authRepository: AuthRepository? = null,
    ) = TaxSettingsViewModel(
        repository = repository,
        deleteAccountUseCase = null,
        authRepository = authRepository,
        currentUserEmail = "",
        dispatcher = StandardTestDispatcher(scheduler),
    )

    // ── Chargement ───────────────────────────────────────────────────────────────────────────

    @Test
    fun load_onAVirginBase_fallsBackToTheDefaultSettings() = runTest {
        val vm = viewModel(FakeTaxSettingsRepository(), testScheduler)

        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals(TaxSettings.Default.issuerName, state.issuerName)
        assertEquals(TaxSettings.Default.issuerSiret, state.issuerSiret)
        assertTrue(state.facturXEnabled) // conformité 2026 active par défaut
    }

    @Test
    fun load_restoresPersistedSettings() = runTest {
        val stored = TaxSettings(
            issuerName = "Atelier Dupont",
            issuerSiren = "111111111",
            issuerSiret = "11111111100011",
            vatNumber = "FR11111111111",
            defaultVatRate = VatRate.TAUX_REDUIT,
            facturXEnabled = false,
        )
        val vm = viewModel(FakeTaxSettingsRepository(stored), testScheduler)

        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("Atelier Dupont", state.issuerName)
        assertEquals(VatRate.TAUX_REDUIT, state.defaultVatRate)
        assertFalse(state.facturXEnabled)
    }

    // ── Taux de TVA : référence en lecture seule ─────────────────────────────────────────────

    @Test
    fun availableVatRates_exposeTheStatutoryRates_andAreNotConfigurable() = runTest {
        val vm = viewModel(FakeTaxSettingsRepository(), testScheduler)
        advanceUntilIdle()

        // Les valeurs viennent du domaine, jamais des paramètres : elles sont fixées par la loi.
        assertEquals(VatRate.entries, vm.uiState.value.availableVatRates)
        assertEquals(2000, VatRate.TAUX_NORMAL.basisPoints)
        assertEquals(550, VatRate.TAUX_REDUIT.basisPoints)
    }

    @Test
    fun selectingADefaultRate_isPersisted() = runTest {
        val repository = FakeTaxSettingsRepository()
        val vm = viewModel(repository, testScheduler)
        advanceUntilIdle()

        vm.processIntent(TaxSettingsIntent.DefaultVatRateSelected(VatRate.TAUX_INTERMEDIAIRE))
        vm.processIntent(TaxSettingsIntent.Save)
        advanceUntilIdle()

        assertEquals(VatRate.TAUX_INTERMEDIAIRE, repository.stored?.defaultVatRate)
    }

    // ── Validation ───────────────────────────────────────────────────────────────────────────

    @Test
    fun loadedSettings_presentNoErrorBeforeAnyEdit() = runTest {
        val vm = viewModel(FakeTaxSettingsRepository(), testScheduler)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.visibleErrors.isEmpty())
    }

    @Test
    fun issuerSiretShorterThan14Digits_blocksSaving() = runTest {
        val repository = FakeTaxSettingsRepository()
        val vm = viewModel(repository, testScheduler)
        advanceUntilIdle()

        vm.processIntent(TaxSettingsIntent.IssuerSiretChanged("123"))
        vm.processIntent(TaxSettingsIntent.Save)
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.visibleErrors[TaxSettingsField.ISSUER_SIRET])
        assertEquals(0, repository.saveCount)
    }

    @Test
    fun malformedVatNumber_blocksSaving_butAnEmptyOneIsAccepted() = runTest {
        val repository = FakeTaxSettingsRepository()
        val vm = viewModel(repository, testScheduler)
        advanceUntilIdle()

        vm.processIntent(TaxSettingsIntent.VatNumberChanged("XX12"))
        assertNotNull(vm.uiState.value.visibleErrors[TaxSettingsField.VAT_NUMBER])

        // Une entreprise en franchise de TVA n'a pas de numéro : le champ est optionnel.
        vm.processIntent(TaxSettingsIntent.VatNumberChanged(""))
        assertNull(vm.uiState.value.errors[TaxSettingsField.VAT_NUMBER])

        vm.processIntent(TaxSettingsIntent.Save)
        advanceUntilIdle()
        assertEquals(1, repository.saveCount)
    }

    // ── Enregistrement ───────────────────────────────────────────────────────────────────────

    @Test
    fun saving_derivesTheSirenFromTheSiret_andConfirmsWithTheExpectedMessage() = runTest {
        val repository = FakeTaxSettingsRepository()
        val vm = viewModel(repository, testScheduler)
        advanceUntilIdle()

        vm.processIntent(TaxSettingsIntent.IssuerNameChanged("Atelier Dupont"))
        vm.processIntent(TaxSettingsIntent.IssuerSiretChanged("11111111100011"))
        vm.processIntent(TaxSettingsIntent.VatNumberChanged("FR11111111111"))
        vm.processIntent(TaxSettingsIntent.Save)
        advanceUntilIdle()

        val saved = assertNotNull(repository.stored)
        assertEquals("Atelier Dupont", saved.issuerName)
        assertEquals("111111111", saved.issuerSiren) // règle INSEE
        assertEquals(
            "Paramètres fiscaux mis à jour avec succès",
            vm.uiState.value.savedMessage,
        )
    }

    @Test
    fun toggledFacturX_isPersisted() = runTest {
        val repository = FakeTaxSettingsRepository()
        val vm = viewModel(repository, testScheduler)
        advanceUntilIdle()

        vm.processIntent(TaxSettingsIntent.FacturXToggled(false))
        vm.processIntent(TaxSettingsIntent.Save)
        advanceUntilIdle()

        assertFalse(assertNotNull(repository.stored).facturXEnabled)
    }

    @Test
    fun feedbackShown_clearsTheConfirmation_soItIsNotReplayed() = runTest {
        val vm = viewModel(FakeTaxSettingsRepository(), testScheduler)
        advanceUntilIdle()
        vm.processIntent(TaxSettingsIntent.Save)
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.savedMessage)

        vm.processIntent(TaxSettingsIntent.FeedbackShown)

        assertNull(vm.uiState.value.savedMessage)
    }

    @Test
    fun savedIssuer_isTheOneCarriedOnIssuedInvoices() = runTest {
        val repository = FakeTaxSettingsRepository()
        val vm = viewModel(repository, testScheduler)
        advanceUntilIdle()

        vm.processIntent(TaxSettingsIntent.IssuerNameChanged("Atelier Dupont"))
        vm.processIntent(TaxSettingsIntent.IssuerSiretChanged("11111111100011"))
        vm.processIntent(TaxSettingsIntent.Save)
        advanceUntilIdle()

        val issuer = assertNotNull(repository.stored).issuerParty
        assertEquals("Atelier Dupont", issuer.name)
        assertEquals("11111111100011", issuer.siret)
        assertEquals("111111111", issuer.siren)
    }

    // ── Déconnexion (Fix RC1) ─────────────────────────────────────────────────────────────────

    @Test
    fun logout_invokesAuthRepository_andSetsLoggedOutFlag() = runTest {
        val authRepo = FakeAuthRepository()
        val vm = viewModel(FakeTaxSettingsRepository(), testScheduler, authRepo)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.loggedOut)
        assertFalse(vm.uiState.value.isLoggingOut)

        vm.processIntent(TaxSettingsIntent.Logout)
        advanceUntilIdle()

        assertTrue(authRepo.logoutCalled)
        assertFalse(vm.uiState.value.isLoggingOut)
        assertTrue(vm.uiState.value.loggedOut)
    }
}
