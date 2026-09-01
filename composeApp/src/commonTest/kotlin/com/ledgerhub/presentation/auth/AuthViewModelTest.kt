package com.ledgerhub.presentation.auth

import com.ledgerhub.data.sirene.MockSireneLookupService
import com.ledgerhub.domain.auth.AuthRepository
import com.ledgerhub.domain.sirene.SireneCompany
import com.ledgerhub.domain.sirene.SireneLookupResult
import com.ledgerhub.domain.sirene.SireneLookupService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Faux repository — pas d'accès réseau dans les tests, contrôle direct du succès/échec.
 * [delayMillis] simule un appel réseau non instantané — nécessaire pour observer l'état
 * `Loading` intermédiaire sous [StandardTestDispatcher] (voir MockCreditNoteRepository pour le
 * même principe) : sans délai, la coroutine se termine entièrement dans le même `runCurrent()`.
 */
private class FakeAuthRepository(
    private val result: Result<Unit>,
    private val delayMillis: Long = 0L,
) : AuthRepository {
    var lastEmail: String? = null
    var lastPassword: String? = null

    override suspend fun login(email: String, password: String): Result<Unit> {
        delay(delayMillis)
        lastEmail = email
        lastPassword = password
        return result
    }
}

/**
 * Faux répertoire SIRENE — compte ses appels, ce qui est le seul moyen de prouver qu'une saisie
 * incomplète **n'en déclenche aucun**. Une assertion sur l'état ne le dirait pas : `IDLE` est aussi
 * l'état d'une interrogation lancée puis revenue bredouille.
 */
private class FakeSireneLookupService(
    private val result: SireneLookupResult = SireneLookupResult.Verified(
        SireneCompany("90123456700013", "Youssoufi DevOps & Cloud EURL", "EURL"),
    ),
    private val delayMillis: Long = 1_000L,
    private val failure: Throwable? = null,
) : SireneLookupService {
    var callCount = 0
        private set
    var lastSiret: String? = null
        private set

    override suspend fun lookup(siret: String): SireneLookupResult {
        callCount++
        lastSiret = siret
        delay(delayMillis)
        failure?.let { throw it }
        return result
    }
}

/** Tests QA du cycle de vie de la connexion (héritage) et de l'inscription par SIRET (US-21). */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val validSiret = MockSireneLookupService.DEMO_SIRET
    private val expectedCompany = MockSireneLookupService.DEFAULT_COMPANY_NAME

    private fun viewModel(
        sirene: SireneLookupService,
        dispatcher: TestDispatcher,
    ) = AuthViewModel(
        authRepository = FakeAuthRepository(Result.success(Unit)),
        sireneLookupService = sirene,
        dispatcher = dispatcher,
    )

    // ── Connexion (comportement hérité) ─────────────────────────────────────

    @Test
    fun initialState_hasSubmitDisabled() {
        val viewModel = AuthViewModel(authRepository = FakeAuthRepository(Result.success(Unit)))
        assertFalse(viewModel.uiState.value.isSubmitEnabled)
        assertFalse(viewModel.uiState.value.isRegistering)
    }

    @Test
    fun blankPassword_keepsSubmitDisabled() {
        val viewModel = AuthViewModel(authRepository = FakeAuthRepository(Result.success(Unit)))
        viewModel.processIntent(AuthIntent.EmailChanged("vous@cabinet.fr"))
        assertFalse(viewModel.uiState.value.isSubmitEnabled)
    }

    @Test
    fun filledEmailAndPassword_enablesSubmit() {
        val viewModel = AuthViewModel(authRepository = FakeAuthRepository(Result.success(Unit)))
        viewModel.processIntent(AuthIntent.EmailChanged("vous@cabinet.fr"))
        viewModel.processIntent(AuthIntent.PasswordChanged("motdepasse"))
        assertTrue(viewModel.uiState.value.isSubmitEnabled)
    }

    @Test
    fun submit_withValidCredentials_transitionsThroughLoadingToSuccess() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = FakeAuthRepository(Result.success(Unit), delayMillis = 500L)
        val viewModel = AuthViewModel(authRepository = repository, dispatcher = dispatcher)
        viewModel.processIntent(AuthIntent.EmailChanged("vous@cabinet.fr"))
        viewModel.processIntent(AuthIntent.PasswordChanged("motdepasse"))

        viewModel.processIntent(AuthIntent.Submit)
        runCurrent()
        assertTrue(viewModel.uiState.value.isLoading)

        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.loginSucceeded)
        assertEquals("vous@cabinet.fr", repository.lastEmail)
        assertEquals("motdepasse", repository.lastPassword)
    }

    @Test
    fun submit_whenRepositoryFails_surfacesErrorMessage() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = FakeAuthRepository(Result.failure(IllegalStateException("Identifiants invalides")))
        val viewModel = AuthViewModel(authRepository = repository, dispatcher = dispatcher)
        viewModel.processIntent(AuthIntent.EmailChanged("vous@cabinet.fr"))
        viewModel.processIntent(AuthIntent.PasswordChanged("motdepasse"))

        viewModel.processIntent(AuthIntent.Submit)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.loginSucceeded)
        assertEquals("Identifiants invalides", state.errorMessage)
    }

    @Test
    fun submit_withBlankFields_isNoOp() = runTest {
        val repository = FakeAuthRepository(Result.success(Unit))
        val viewModel = AuthViewModel(authRepository = repository)

        viewModel.processIntent(AuthIntent.Submit)

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(null, repository.lastEmail)
    }

    // ── Vérification SIRENE : déclenchement ─────────────────────────────────

    @Test
    fun anIncompleteSiret_triggersNoLookup() = runTest {
        val sirene = FakeSireneLookupService()
        val viewModel = viewModel(sirene, StandardTestDispatcher(testScheduler))

        viewModel.processIntent(AuthIntent.SiretChanged("9012345670001"))
        advanceUntilIdle()

        assertEquals(0, sirene.callCount)
        assertEquals(SireneVerificationStatus.IDLE, viewModel.uiState.value.sireneStatus)
    }

    @Test
    fun theFourteenthDigit_startsTheVerification_thenFillsTheCompanyName() = runTest {
        val sirene = FakeSireneLookupService()
        val viewModel = viewModel(sirene, StandardTestDispatcher(testScheduler))

        viewModel.processIntent(AuthIntent.SiretChanged(validSiret))
        runCurrent()

        // Pendant la seconde de vérification : le champ porte son indicateur, pas encore de badge.
        assertTrue(viewModel.uiState.value.isVerifying)
        assertFalse(viewModel.uiState.value.isSireneVerified)

        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(SireneVerificationStatus.VERIFIED, state.sireneStatus)
        assertEquals(expectedCompany, state.companyName)
        assertTrue(state.companyNameAutoFilled)
        assertEquals(1, sirene.callCount)
        assertEquals(validSiret, sirene.lastSiret)
    }

    /** Un SIRET collé avec des espaces est un SIRET : la normalisation est du domaine. */
    @Test
    fun aSiretPastedWithSpaces_isNormalizedBeforeLookup() = runTest {
        val sirene = FakeSireneLookupService()
        val viewModel = viewModel(sirene, StandardTestDispatcher(testScheduler))

        viewModel.processIntent(AuthIntent.SiretChanged("901 234 567 00013"))
        advanceUntilIdle()

        assertEquals(validSiret, sirene.lastSiret)
        assertEquals(SireneVerificationStatus.VERIFIED, viewModel.uiState.value.sireneStatus)
    }

    /** Le filtre de saisie absorbe les touches au-delà du 14e chiffre : rien ne doit être rejoué. */
    @Test
    fun retypingTheSameCompleteSiret_doesNotRelaunchTheLookup() = runTest {
        val sirene = FakeSireneLookupService()
        val viewModel = viewModel(sirene, StandardTestDispatcher(testScheduler))

        viewModel.processIntent(AuthIntent.SiretChanged(validSiret))
        advanceUntilIdle()
        viewModel.processIntent(AuthIntent.SiretChanged(validSiret))
        advanceUntilIdle()

        assertEquals(1, sirene.callCount)
    }

    // ── Réinitialisation ────────────────────────────────────────────────────

    @Test
    fun droppingBelowFourteenDigits_clearsTheBadgeAndTheAutoFilledName() = runTest {
        val sirene = FakeSireneLookupService()
        val viewModel = viewModel(sirene, StandardTestDispatcher(testScheduler))
        viewModel.processIntent(AuthIntent.SiretChanged(validSiret))
        advanceUntilIdle()

        viewModel.processIntent(AuthIntent.SiretChanged(validSiret.dropLast(1)))

        val state = viewModel.uiState.value
        assertEquals(SireneVerificationStatus.IDLE, state.sireneStatus)
        assertFalse(state.isSireneVerified)
        assertEquals("", state.companyName)
        assertFalse(state.companyNameAutoFilled)
    }

    /** Une raison sociale saisie à la main survit à la correction du SIRET — c'est de la frappe. */
    @Test
    fun droppingBelowFourteenDigits_keepsAManuallyTypedName() = runTest {
        val sirene = FakeSireneLookupService()
        val viewModel = viewModel(sirene, StandardTestDispatcher(testScheduler))
        viewModel.processIntent(AuthIntent.SiretChanged(validSiret))
        advanceUntilIdle()
        viewModel.processIntent(AuthIntent.CompanyNameChanged("Mon Enseigne Commerciale"))

        viewModel.processIntent(AuthIntent.SiretChanged(validSiret.dropLast(1)))

        val state = viewModel.uiState.value
        assertEquals(SireneVerificationStatus.IDLE, state.sireneStatus)
        assertEquals("Mon Enseigne Commerciale", state.companyName)
    }

    /**
     * Saisie corrigée avant la fin de la vérification : seule la dernière réponse compte. Sans
     * annulation de la précédente, le badge afficherait l'entreprise du SIRET abandonné.
     */
    @Test
    fun aCorrectedSiret_cancelsTheInFlightLookup() = runTest {
        val sirene = FakeSireneLookupService()
        val viewModel = viewModel(sirene, StandardTestDispatcher(testScheduler))

        viewModel.processIntent(AuthIntent.SiretChanged(validSiret))
        advanceTimeBy(500)
        viewModel.processIntent(AuthIntent.SiretChanged(validSiret.dropLast(1)))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(SireneVerificationStatus.IDLE, state.sireneStatus)
        assertEquals("", state.companyName)
    }

    // ── Issues défavorables ─────────────────────────────────────────────────

    @Test
    fun anUnknownSiret_reportsNotFound_withoutBadge() = runTest {
        val sirene = FakeSireneLookupService(result = SireneLookupResult.NotFound)
        val viewModel = viewModel(sirene, StandardTestDispatcher(testScheduler))

        viewModel.processIntent(AuthIntent.SiretChanged(validSiret))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(SireneVerificationStatus.NOT_FOUND, state.sireneStatus)
        assertFalse(state.isSireneVerified)
        assertEquals("", state.companyName)
    }

    /** Répertoire injoignable : distinct d'un SIRET inconnu, l'utilisateur doit pouvoir réessayer. */
    @Test
    fun anUnreachableRegister_reportsUnavailable() = runTest {
        val sirene = FakeSireneLookupService(failure = IllegalStateException("timeout"))
        val viewModel = viewModel(sirene, StandardTestDispatcher(testScheduler))

        viewModel.processIntent(AuthIntent.SiretChanged(validSiret))
        advanceUntilIdle()

        assertEquals(SireneVerificationStatus.UNAVAILABLE, viewModel.uiState.value.sireneStatus)
    }

    // ── Inscription ─────────────────────────────────────────────────────────

    @Test
    fun registration_staysDisabled_untilTheCompanyIsVerified() = runTest {
        val sirene = FakeSireneLookupService()
        val viewModel = viewModel(sirene, StandardTestDispatcher(testScheduler))
        viewModel.processIntent(AuthIntent.ModeChanged(true))
        viewModel.processIntent(AuthIntent.EmailChanged("vous@cabinet.fr"))
        viewModel.processIntent(AuthIntent.PasswordChanged("motdepasse"))

        assertFalse(viewModel.uiState.value.isRegisterEnabled)

        viewModel.processIntent(AuthIntent.SiretChanged(validSiret))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isRegisterEnabled)
    }

    @Test
    fun submittingAnUnverifiedRegistration_isNoOp() = runTest {
        val sirene = FakeSireneLookupService()
        val viewModel = viewModel(sirene, StandardTestDispatcher(testScheduler))
        viewModel.processIntent(AuthIntent.ModeChanged(true))
        viewModel.processIntent(AuthIntent.EmailChanged("vous@cabinet.fr"))
        viewModel.processIntent(AuthIntent.PasswordChanged("motdepasse"))

        viewModel.processIntent(AuthIntent.Submit)

        assertFalse(viewModel.uiState.value.registrationSucceeded)
    }

    @Test
    fun submittingAVerifiedRegistration_succeeds() = runTest {
        val sirene = FakeSireneLookupService()
        val viewModel = viewModel(sirene, StandardTestDispatcher(testScheduler))
        viewModel.processIntent(AuthIntent.ModeChanged(true))
        viewModel.processIntent(AuthIntent.EmailChanged("vous@cabinet.fr"))
        viewModel.processIntent(AuthIntent.PasswordChanged("motdepasse"))
        viewModel.processIntent(AuthIntent.SiretChanged(validSiret))
        advanceUntilIdle()

        viewModel.processIntent(AuthIntent.Submit)

        assertTrue(viewModel.uiState.value.registrationSucceeded)
        assertEquals(expectedCompany, viewModel.uiState.value.companyName)
    }

    @Test
    fun switchingTabs_doesNotLoseTheVerifiedCompany() = runTest {
        val sirene = FakeSireneLookupService()
        val viewModel = viewModel(sirene, StandardTestDispatcher(testScheduler))
        viewModel.processIntent(AuthIntent.ModeChanged(true))
        viewModel.processIntent(AuthIntent.SiretChanged(validSiret))
        advanceUntilIdle()

        viewModel.processIntent(AuthIntent.ModeChanged(false))
        viewModel.processIntent(AuthIntent.ModeChanged(true))

        val state = viewModel.uiState.value
        assertTrue(state.isRegistering)
        assertEquals(SireneVerificationStatus.VERIFIED, state.sireneStatus)
        assertEquals(expectedCompany, state.companyName)
    }
}
