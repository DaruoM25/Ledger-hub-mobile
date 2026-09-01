package com.ledgerhub.presentation.auth

/** Étape de la vérification d'une entreprise auprès du répertoire SIRENE (US-21). */
enum class SireneVerificationStatus {
    /** Aucun SIRET complet saisi — rien à afficher. */
    IDLE,

    /** Interrogation en cours : le champ porte son indicateur de chargement. */
    VERIFYING,

    /** Entreprise trouvée : badge vert et raison sociale reportée. */
    VERIFIED,

    /** SIRET absent du répertoire. */
    NOT_FOUND,

    /** Le répertoire n'a pas répondu — incident technique, distinct d'un SIRET inconnu. */
    UNAVAILABLE,
}

/**
 * État immuable de l'écran d'authentification — connexion et inscription réunies (US-21).
 *
 * @param companyNameAutoFilled la raison sociale affichée vient du répertoire et non de la frappe
 *   de l'utilisateur. C'est ce qui autorise à l'effacer quand le SIRET redescend sous 14 chiffres :
 *   effacer un nom saisi à la main parce qu'un chiffre est corrigé serait une perte de frappe.
 */
data class AuthUiState(
    val isRegistering: Boolean = false,
    val email: String = "",
    val password: String = "",
    val siret: String = "",
    val companyName: String = "",
    val companyNameAutoFilled: Boolean = false,
    val sireneStatus: SireneVerificationStatus = SireneVerificationStatus.IDLE,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val loginSucceeded: Boolean = false,
    val registrationSucceeded: Boolean = false,
) {
    val isSubmitEnabled: Boolean get() = email.isNotBlank() && password.isNotBlank() && !isLoading

    val isVerifying: Boolean get() = sireneStatus == SireneVerificationStatus.VERIFYING

    val isSireneVerified: Boolean get() = sireneStatus == SireneVerificationStatus.VERIFIED

    /**
     * L'inscription exige une entreprise **vérifiée** : c'est tout l'objet de l'US. Un compte créé
     * sur un SIRET que le répertoire n'a pas confirmé n'aurait pas d'identité d'entreprise.
     */
    val isRegisterEnabled: Boolean
        get() = isSireneVerified && companyName.isNotBlank() && isSubmitEnabled
}
