package com.ledgerhub.domain.auth

/**
 * Compte ouvert sur cet appareil (US-26) — l'identité d'un espace LedgerHub, sans son secret.
 *
 * Le mot de passe n'y figure pas, délibérément : un compte se *lit* en base (voir
 * [AuthRepository.login]) et le condensat n'a aucune raison de circuler dans la couche présentation.
 * Le secret ne traverse le domaine que le temps d'une vérification, en paramètre, jamais en champ.
 *
 * [siret] et [companyName] sont ceux que le répertoire SIRENE a confirmés à l'inscription : c'est
 * ce qui distingue un espace LedgerHub d'un simple couple identifiant/mot de passe.
 */
data class UserAccount(
    val email: String,
    val companyName: String,
    val siret: String,
)

/**
 * Forme canonique d'une adresse — minuscules, sans espaces de bordure.
 *
 * Appliquée à l'inscription **et** à la connexion : sans elle, « Vous@Cabinet.fr » ouvrirait un
 * second compte, et l'utilisateur qui capitalise la première lettre au clavier mobile — ce que fait
 * l'autocorrection par défaut — ne retrouverait plus le sien.
 */
fun normalizeEmail(raw: String): String = raw.trim().lowercase()
