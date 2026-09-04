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
