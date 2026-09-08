package com.ledgerhub.domain.auth

/**
 * Échecs d'authentification que la couche présentation doit **distinguer** (US-26).
 *
 * Des exceptions plutôt qu'un `sealed` de résultats : les deux repositories rendent déjà un
 * `Result`, et un échec métier y voyage comme un échec technique — le ViewModel n'a qu'un seul
 * chemin d'erreur à écrire. Le message porté ici est celui que l'écran affiche (voir
 * `AuthViewModel`), le reste du domaine restant en français comme les autres ViewModels.
 */

/**
 * Adresse inconnue **ou** mot de passe faux — un seul message pour les deux.
 *
 * Ce n'est pas une approximation : répondre « ce compte n'existe pas » révélerait quelles adresses
 * ont un espace sur l'appareil. La distinction n'existe donc nulle part au-dessus du dépôt.
 */
class InvalidCredentialsException : Exception("Identifiants invalides")

/** Inscription sur une adresse déjà ouverte — la clé primaire de `UserAccount` l'interdit. */
class EmailAlreadyRegisteredException :
    Exception("Un compte existe déjà pour cette adresse — connectez-vous.")

/** Adresse email syntaxiquement invalide selon les règles du domaine. */
class InvalidEmailException : Exception("Format d'adresse email invalide.")

/** Requête non authentifiée (HTTP 401). */
class UnauthorizedException(message: String = "Non autorisé (401)") : Exception(message)

/** Données de requête invalides ou rejetées par le backend (HTTP 422). */
class ValidationException(message: String = "Données invalides (422)") : Exception(message)

/** Erreur interne du serveur distant (HTTP 500..599). */
class ServerException(message: String = "Erreur interne du serveur (500)") : Exception(message)

/** Erreur réseau ou connectivité I/O. */
class NetworkException(message: String, cause: Throwable? = null) : Exception(message, cause)

