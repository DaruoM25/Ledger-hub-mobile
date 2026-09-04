package com.ledgerhub.data.auth

import com.ledgerhub.db.LedgerHubDatabase
import com.ledgerhub.domain.auth.AuthRepository
import com.ledgerhub.domain.auth.EmailAlreadyRegisteredException
import com.ledgerhub.domain.auth.InvalidCredentialsException
import com.ledgerhub.domain.auth.PasswordHash
import com.ledgerhub.domain.auth.UserAccount
import com.ledgerhub.domain.auth.normalizeEmail
import com.ledgerhub.domain.time.Clock
import com.ledgerhub.domain.time.SystemClock
import kotlin.random.Random

/**
 * Authentification **autonome** — comptes tenus dans la base SQLDelight de l'appareil (US-26, RC1).
 *
 * L'application ne dépend d'aucun serveur : l'espace créé à l'inscription est écrit dans la même
 * base que les factures, et la connexion s'y adresse. C'est le pendant de
 * [com.ledgerhub.data.theme.SqlDelightThemePreferenceRepository] pour l'identité plutôt que pour
 * l'affichage, et cela ferme la dette relevée en recette : l'écran d'authentification ouvrait sur
 * un backend qui n'existe pas hors poste de développement.
 *
 * Le mot de passe ne traverse cette classe que salé puis haché ([PasswordHash]) — rien n'en est
 * conservé en clair, pas même le temps d'une transaction.
 *
 * @param clock injectée pour horodater la création sans dépendre de l'heure réelle en test.
 * @param random source du sel, injectable pour rendre un test reproductible.
 */
class SqlDelightAuthRepository(
    private val database: LedgerHubDatabase,
    private val clock: Clock = SystemClock,
    private val random: Random = Random.Default,
) : AuthRepository {

    private val queries get() = database.userAccountQueries

    override suspend fun login(email: String, password: String): Result<UserAccount> = runCatching {
        val stored = queries.selectByEmail(normalizeEmail(email)).executeAsOneOrNull()
            ?: throw InvalidCredentialsException()

        // Le mot de passe est vérifié même quand le compte est introuvable ? Non : la lecture
        // ci-dessus a déjà tranché. Distinguer les deux cas dans le *message* serait la fuite —
        // c'est ce que garantit InvalidCredentialsException, unique pour les deux branches.
        if (!PasswordHash.matches(password, stored.passwordSalt, stored.passwordHash)) {
            throw InvalidCredentialsException()
        }

        UserAccount(
            email = stored.email,
            companyName = stored.companyName,
            siret = stored.siret,
        )
    }

    override suspend fun register(
        account: UserAccount,
        password: String,
    ): Result<UserAccount> = runCatching {
        val email = normalizeEmail(account.email)
        val salt = PasswordHash.newSalt(random)

        // Dérivation hors transaction : les milliers d'itérations de PasswordHash tiendraient
        // sinon la base verrouillée pendant tout leur calcul, pour un travail qui ne la touche pas.
        val hash = PasswordHash.hash(password, salt)

        // Lecture et écriture dans une seule transaction : entre un `selectByEmail` isolé et
        // l'insertion, une seconde inscription pourrait s'intercaler et l'échec remonterait alors
        // comme une violation de contrainte SQLite brute, illisible à l'écran.
        database.transaction {
            val alreadyOpen = queries.selectByEmail(email).executeAsOneOrNull() != null
            if (alreadyOpen) throw EmailAlreadyRegisteredException()

            queries.insertAccount(
                email = email,
                passwordSalt = salt,
                passwordHash = hash,
                companyName = account.companyName,
                siret = account.siret,
                createdAt = clock.nowIso(),
            )
        }

        account.copy(email = email)
    }
}
