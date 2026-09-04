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
}
