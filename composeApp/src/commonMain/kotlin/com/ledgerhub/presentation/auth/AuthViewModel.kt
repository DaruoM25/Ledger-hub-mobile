package com.ledgerhub.presentation.auth

import com.ledgerhub.data.sirene.MockSireneLookupService
import com.ledgerhub.domain.auth.AuthRepository
import com.ledgerhub.domain.auth.EmailValidator
import com.ledgerhub.domain.auth.PasswordValidator
import com.ledgerhub.domain.auth.UserAccount
import com.ledgerhub.domain.sirene.SireneLookupResult
import com.ledgerhub.domain.sirene.SireneLookupService
import com.ledgerhub.domain.sirene.SiretInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel de l'écran d'authentification — connexion et inscription sur le compte **local** tenu
 * par [AuthRepository] (US-26), l'inscription restant pilotée par le SIRET via
 * [SireneLookupService] (US-21).
 *
 * La vérification SIRENE est déclenchée **ici**, à la frappe, dès que la saisie porte 14 chiffres :
 * l'utilisateur n'a aucun bouton à chercher, ce qui est tout l'intérêt d'une inscription par SIRET.
 * La règle vit dans le domaine ([SiretInput]) et la décision dans le ViewModel, donc l'une comme
 * l'autre s'éprouvent sans composition.
 *
 * @param dispatcher injecté pour des tests sans dépendance au thread réel (cf. `DirectoryViewModel`).
 */
class AuthViewModel(
    private val authRepository: AuthRepository,
    private val sireneLookupService: SireneLookupService = MockSireneLookupService(),
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    /**
     * Vérification en cours. Conservée pour être **annulée** à la frappe suivante : sans cela, une
     * saisie corrigée rapidement laisserait la réponse périmée écraser l'état, et le badge
     * afficherait l'entreprise du SIRET précédent.
     */
    private var lookupJob: Job? = null

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun processIntent(intent: AuthIntent) {
        when (intent) {
            is AuthIntent.ModeChanged -> _uiState.update { current ->
                val pwdErr = if (intent.isRegistering && current.password.isNotBlank() && !PasswordValidator.isValid(current.password)) {
                    PasswordValidator.ERROR_MESSAGE
                } else null
                current.copy(
                    isRegistering = intent.isRegistering,
                    passwordError = pwdErr,
                    passwordConfirmationError = null,
                    errorMessage = null,
                )
            }

            is AuthIntent.EmailChanged -> _uiState.update { current ->
                val emailErr = if (intent.value.isNotBlank() && !EmailValidator.isValid(intent.value)) {
                    EMAIL_INVALID_MESSAGE
                } else null
                current.copy(
                    email = intent.value,
                    emailError = emailErr,
                    errorMessage = null,
                )
            }

            is AuthIntent.PasswordChanged -> _uiState.update { current ->
                val pwdErr = if (current.isRegistering && intent.value.isNotBlank() && !PasswordValidator.isValid(intent.value)) {
                    PasswordValidator.ERROR_MESSAGE
                } else null
                val confirmErr = if (current.isRegistering && current.passwordConfirmation.isNotBlank() && current.passwordConfirmation != intent.value) {
                    PASSWORDS_MISMATCH_MESSAGE
                } else null
                current.copy(
                    password = intent.value,
                    passwordError = pwdErr,
                    passwordConfirmationError = confirmErr,
                    errorMessage = null,
                )
            }

            is AuthIntent.PasswordConfirmationChanged -> _uiState.update { current ->
                val confirmErr = if (intent.value.isNotBlank() && intent.value != current.password) {
                    PASSWORDS_MISMATCH_MESSAGE
                } else null
                current.copy(
                    passwordConfirmation = intent.value,
                    passwordConfirmationError = confirmErr,
                    errorMessage = null,
                )
            }

            is AuthIntent.SiretChanged -> onSiretChanged(intent.value)

            is AuthIntent.CompanyNameChanged -> _uiState.update {
                // La saisie manuelle reprend la main : le nom cesse d'être « auto-complété », donc
                // il survivra à une correction du SIRET.
                it.copy(companyName = intent.value, companyNameAutoFilled = false)
            }

            AuthIntent.Submit -> submit()
        }
    }

    private fun onSiretChanged(value: String) {
        val current = _uiState.value
        val digits = SiretInput.sanitize(value)
        val previousDigits = SiretInput.sanitize(current.siret)
        val isComplete = digits.length == SiretInput.LENGTH

        // Même identifiant qu'à la frappe précédente, vérification déjà lancée ou aboutie : on ne
        // relance rien. Sans ce garde-fou, une frappe au-delà du 14e chiffre — que le filtre de
        // saisie absorbe sans changer la valeur — rejouerait l'interrogation à chaque touche.
        if (isComplete && digits == previousDigits && (current.isVerifying || current.isSireneVerified)) {
            _uiState.update { it.copy(siret = value) }
            return
        }

        lookupJob?.cancel()
        _uiState.update { state ->
            state.copy(
                siret = value,
                sireneStatus = if (isComplete) {
                    SireneVerificationStatus.VERIFYING
                } else {
                    SireneVerificationStatus.IDLE
                },
                // Seule une raison sociale venue du répertoire est retirée : voir
                // [AuthUiState.companyNameAutoFilled].
                companyName = if (!isComplete && state.companyNameAutoFilled) "" else state.companyName,
                companyNameAutoFilled = state.companyNameAutoFilled && isComplete,
                errorMessage = null,
            )
        }
        if (!isComplete) return

        lookupJob = scope.launch {
            // `runCatching` seul ne convient pas : il capture **aussi** la CancellationException
            // d'une vérification annulée par la frappe suivante, et l'écran annoncerait alors un
            // répertoire injoignable là où l'utilisateur a simplement corrigé son SIRET.
            // L'annulation doit remonter pour que la coroutine s'éteigne sans rien publier.
            val result = try {
                Result.success(sireneLookupService.lookup(digits))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                Result.failure(error)
            }
            _uiState.update { state ->
                result.fold(
                    onSuccess = { lookup ->
                        when (lookup) {
                            is SireneLookupResult.Verified -> state.copy(
                                sireneStatus = SireneVerificationStatus.VERIFIED,
                                companyName = lookup.company.companyName,
                                companyNameAutoFilled = true,
                            )

                            SireneLookupResult.NotFound -> state.copy(
                                sireneStatus = SireneVerificationStatus.NOT_FOUND,
                            )
                        }
                    },
                    // Le répertoire injoignable n'est pas un SIRET inconnu : l'utilisateur doit
                    // pouvoir réessayer sans croire son numéro faux.
                    onFailure = { state.copy(sireneStatus = SireneVerificationStatus.UNAVAILABLE) },
                )
            }
        }
    }

    private fun submit() {
        val state = _uiState.value
        if (state.isRegistering) {
            register(state)
        } else {
            login(state)
        }
    }

    /**
     * Inscription : le compte est **écrit en base** (US-26), avec l'entreprise que le répertoire
     * SIRENE vient de confirmer. C'est ce qui rend l'application autonome — l'espace créé ici est
     * celui que [login] retrouvera au prochain démarrage, sans serveur.
     *
     * L'échec le plus courant est une adresse déjà ouverte : le dépôt le dit
     * ([com.ledgerhub.domain.auth.EmailAlreadyRegisteredException]) et le message part à l'écran
     * comme celui d'une connexion refusée.
     */
    private fun register(state: AuthUiState) {
        val emailErr = if (!EmailValidator.isValid(state.email)) EMAIL_INVALID_MESSAGE else null
        val pwdErr = if (!PasswordValidator.isValid(state.password)) PasswordValidator.ERROR_MESSAGE else null
        val confirmErr = if (state.passwordConfirmation.isNotBlank() && state.passwordConfirmation != state.password) {
            PASSWORDS_MISMATCH_MESSAGE
        } else null

        if (emailErr != null || pwdErr != null || confirmErr != null) {
            _uiState.update {
                it.copy(
                    emailError = emailErr ?: it.emailError,
                    passwordError = pwdErr ?: it.passwordError,
                    passwordConfirmationError = confirmErr ?: it.passwordConfirmationError,
                )
            }
            return
        }

        if (!state.isRegisterEnabled) return

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        scope.launch {
            val account = UserAccount(
                email = state.email,
                companyName = state.companyName,
                siret = SiretInput.sanitize(state.siret),
            )
            val result = authRepository.register(account, state.password)
            _uiState.update { current ->
                result.fold(
                    onSuccess = { current.copy(isLoading = false, registrationSucceeded = true) },
                    onFailure = { throwable ->
                        current.copy(
                            isLoading = false,
                            errorMessage = throwable.message ?: "Création du compte impossible",
                        )
                    },
                )
            }
        }
    }

    /**
     * Connexion : les identifiants sont confrontés au compte local. Adresse inconnue et mot de
     * passe faux rendent le **même** message — c'est le dépôt qui porte cette règle, pas cet écran.
     */
    private fun login(state: AuthUiState) {
        val emailErr = if (!EmailValidator.isValid(state.email)) EMAIL_INVALID_MESSAGE else null
        if (emailErr != null) {
            _uiState.update { it.copy(emailError = emailErr) }
            return
        }

        if (!state.isSubmitEnabled) return

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        scope.launch {
            val result = authRepository.login(state.email, state.password)
            _uiState.update { current ->
                result.fold(
                    onSuccess = { current.copy(isLoading = false, loginSucceeded = true) },
                    onFailure = { throwable ->
                        current.copy(
                            isLoading = false,
                            errorMessage = throwable.message ?: "Erreur inconnue lors de la connexion",
                        )
                    },
                )
            }
        }
    }

    /**
     * À appeler depuis le cycle de vie de la plateforme.
     * Android : depuis onDestroy() ou rememberViewModel().
     * iOS     : depuis le deinit de la UIViewController.
     */
    fun onCleared() = scope.cancel()

    companion object {
        const val EMAIL_INVALID_MESSAGE = "Format d'adresse e-mail invalide"
        const val PASSWORDS_MISMATCH_MESSAGE = "Les mots de passe ne correspondent pas"
    }
}
