package com.ledgerhub.presentation.auth

/** Intentions de l'écran d'authentification (UDF/MVI) — connexion et inscription (US-21). */
sealed interface AuthIntent {

    /** Bascule entre l'onglet Connexion et l'onglet Inscription. */
    data class ModeChanged(val isRegistering: Boolean) : AuthIntent

    data class EmailChanged(val value: String) : AuthIntent
    data class PasswordChanged(val value: String) : AuthIntent

    /**
     * Frappe dans le champ SIRET. C'est le **ViewModel** qui décide s'il y a lieu d'interroger le
     * répertoire, pas l'écran : la règle des 14 chiffres est du domaine, et la placer dans la
     * composition la rendrait inéprouvable sans arbre sémantique.
     */
    data class SiretChanged(val value: String) : AuthIntent

    /** Correction manuelle de la raison sociale — elle cesse alors d'être auto-complétée. */
    data class CompanyNameChanged(val value: String) : AuthIntent

    /** Soumission du formulaire affiché : connexion ou inscription selon le mode. */
    data object Submit : AuthIntent
}
