package com.ledgerhub.domain.auth

/**
 * Abstraction de l'authentification — la couche présentation ne connaît que ce contrat.
 *
 * Deux opérations et rien d'autre : ouvrir un espace, y entrer. L'implémentation retenue en RC1
 * est [com.ledgerhub.data.auth.SqlDelightAuthRepository], qui les tient dans la base locale sans
 * aucun serveur — mais ce contrat ne le dit pas, et c'est ce qui permettra de lui substituer un
 * dépôt distant sans toucher ni au ViewModel ni à l'écran.
 */
interface AuthRepository {

    /**
     * @return l'échec [InvalidCredentialsException] si l'adresse est inconnue **ou** le mot de
     *   passe faux — les deux cas sont indiscernables au-dessus du dépôt, à dessein.
     */
    suspend fun login(email: String, password: String): Result<UserAccount>

    /**
     * Ouvre l'espace [account] et y attache [password].
     *
     * @return l'échec [EmailAlreadyRegisteredException] si l'adresse porte déjà un compte.
     */
    suspend fun register(account: UserAccount, password: String): Result<UserAccount>

    /**
     * Initie une demande de réinitialisation de mot de passe pour [email].
     *
     * Pour prévenir les attaques par énumération d'utilisateurs, l'opération renvoie un succès
     * même si l'adresse n'est associée à aucun compte connu.
     */
    suspend fun requestPasswordReset(email: String): Result<Unit>

    /**
     * Supprime définitivement le compte utilisateur associé à [email] (RGPD Art. 17).
     *
     * Purge les identifiants d'accès (`UserAccount`) tout en maintenant l'intégrité
     * des pièces comptables décennales (LPF Art. L.102 B).
     */
    suspend fun deleteAccount(email: String): Result<Unit>
}

