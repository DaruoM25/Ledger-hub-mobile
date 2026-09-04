package com.ledgerhub.data.auth

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.ledgerhub.db.LedgerHubDatabase
import com.ledgerhub.domain.auth.EmailAlreadyRegisteredException
import com.ledgerhub.domain.auth.InvalidCredentialsException
import com.ledgerhub.domain.auth.UserAccount
import com.ledgerhub.domain.time.FixedClock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Niveau 2 (US-26) — l'authentification autonome, sur une **vraie** base.
 *
 * C'est ici que se joue la promesse de la RC1 : un compte créé sans serveur, retrouvé sans serveur.
 * Le niveau 1 vérifie l'empreinte (`PasswordHashTest`), le ViewModel vérifie l'enchaînement des
 * états ; ce niveau ferme la boucle sur le stockage — ce qui est réellement écrit dans
 * `UserAccount`, et ce que la connexion en refait.
 *
 * Pilote JdbcSqliteDriver en mémoire, réservé à androidUnitTest/JVM.
 */
class SqlDelightAuthRepositoryTest {

    private val clock = FixedClock("2026-09-04T09:15:00Z")

    private fun newDatabase(): LedgerHubDatabase {
        // Chargement explicite du driver — robustesse face au classloader sandboxé de Robolectric.
        Class.forName("org.sqlite.JDBC")
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        LedgerHubDatabase.Schema.create(driver)
        return LedgerHubDatabase(driver)
    }

    private fun account(email: String = "vous@cabinet.fr") = UserAccount(
        email = email,
        companyName = "Youssoufi DevOps & Cloud EURL",
        siret = "90123456700013",
    )

    // ── Le cycle complet ────────────────────────────────────────────────────

    @Test
    fun anAccountCreatedOnce_opensTheDoorAfterwards() = runTest {
        val database = newDatabase()
        val repository = SqlDelightAuthRepository(database, clock)

        assertTrue(repository.register(account(), "motdepasse").isSuccess)

        val logged = repository.login("vous@cabinet.fr", "motdepasse").getOrThrow()
        assertEquals("vous@cabinet.fr", logged.email)
        assertEquals("Youssoufi DevOps & Cloud EURL", logged.companyName)
        assertEquals("90123456700013", logged.siret)
    }

    /**
     * Le compte survit à l'objet qui l'a écrit : c'est la base qui le porte, pas un état en
     * mémoire. Sans cette assertion, un dépôt qui garderait simplement le dernier compte dans un
     * champ passerait le test précédent.
     */
    @Test
    fun theAccount_survivesTheRepositoryInstance() = runTest {
        val database = newDatabase()
        SqlDelightAuthRepository(database, clock).register(account(), "motdepasse").getOrThrow()

        val fromAnotherInstance = SqlDelightAuthRepository(database, clock)
            .login("vous@cabinet.fr", "motdepasse")

        assertTrue(fromAnotherInstance.isSuccess)
    }

    // ── Ce qui est réellement écrit ─────────────────────────────────────────

    /** La promesse de [com.ledgerhub.domain.auth.PasswordHash], vérifiée là où elle compte. */
    @Test
    fun thePassword_isNeverStoredInClear() = runTest {
        val database = newDatabase()
        SqlDelightAuthRepository(database, clock).register(account(), "motdepasse").getOrThrow()

        val stored = database.userAccountQueries.selectByEmail("vous@cabinet.fr").executeAsOne()
        assertNotEquals("motdepasse", stored.passwordHash)
        assertFalse(stored.passwordHash.contains("motdepasse"))
        assertFalse(stored.passwordSalt.contains("motdepasse"))
        assertEquals(64, stored.passwordHash.length)
        assertEquals("2026-09-04T09:15:00Z", stored.createdAt)
    }

    /** Deux espaces ouverts avec le même mot de passe ne partagent pas leur empreinte. */
    @Test
    fun twoAccountsWithTheSamePassword_carryDifferentDigests() = runTest {
        val database = newDatabase()
        val repository = SqlDelightAuthRepository(database, clock)
        repository.register(account("premier@cabinet.fr"), "motdepasse").getOrThrow()
        repository.register(account("second@cabinet.fr"), "motdepasse").getOrThrow()

        val first = database.userAccountQueries.selectByEmail("premier@cabinet.fr").executeAsOne()
        val second = database.userAccountQueries.selectByEmail("second@cabinet.fr").executeAsOne()

        assertNotEquals(first.passwordSalt, second.passwordSalt)
        assertNotEquals(first.passwordHash, second.passwordHash)
    }

    // ── Normalisation de l'adresse ──────────────────────────────────────────

    /**
     * L'autocorrection des claviers mobiles capitalise la première lettre : sans normalisation,
     * l'utilisateur ne retrouverait pas le compte qu'il vient d'ouvrir.
     */
    @Test
    fun theEmail_isCaseAndSpaceInsensitive() = runTest {
        val database = newDatabase()
        val repository = SqlDelightAuthRepository(database, clock)
        repository.register(account("  Vous@Cabinet.FR "), "motdepasse").getOrThrow()

        assertTrue(repository.login("vous@cabinet.fr", "motdepasse").isSuccess)
        assertTrue(repository.login("VOUS@CABINET.FR", "motdepasse").isSuccess)
        assertEquals(1L, database.userAccountQueries.countAccounts().executeAsOne())
    }

    @Test
    fun theSameEmailInAnotherCase_isTheSameAccount() = runTest {
        val database = newDatabase()
        val repository = SqlDelightAuthRepository(database, clock)
        repository.register(account("vous@cabinet.fr"), "motdepasse").getOrThrow()

        val second = repository.register(account("Vous@Cabinet.fr"), "autrechose")

        assertIs<EmailAlreadyRegisteredException>(second.exceptionOrNull())
        assertEquals(1L, database.userAccountQueries.countAccounts().executeAsOne())
    }

    // ── Issues défavorables ─────────────────────────────────────────────────

    @Test
    fun aWrongPassword_isRefused() = runTest {
        val database = newDatabase()
        val repository = SqlDelightAuthRepository(database, clock)
        repository.register(account(), "motdepasse").getOrThrow()

        assertIs<InvalidCredentialsException>(
            repository.login("vous@cabinet.fr", "MotDePasse").exceptionOrNull(),
        )
    }

    /** Adresse inconnue et mot de passe faux rendent le même message — voir le contrat du dépôt. */
    @Test
    fun anUnknownEmail_isRefusedWithTheSameMessageAsAWrongPassword() = runTest {
        val database = newDatabase()
        val repository = SqlDelightAuthRepository(database, clock)
        repository.register(account(), "motdepasse").getOrThrow()

        val unknown = repository.login("inconnu@cabinet.fr", "motdepasse").exceptionOrNull()
        val wrongPassword = repository.login("vous@cabinet.fr", "faux").exceptionOrNull()

        assertIs<InvalidCredentialsException>(unknown)
        assertEquals(wrongPassword?.message, unknown.message)
    }

    /** Base vierge : aucune connexion ne passe tant qu'aucune inscription n'a eu lieu. */
    @Test
    fun onAFreshDatabase_noLoginSucceeds() = runTest {
        val database = newDatabase()
        val repository = SqlDelightAuthRepository(database, clock)

        assertEquals(0L, database.userAccountQueries.countAccounts().executeAsOne())
        assertIs<InvalidCredentialsException>(
            repository.login("vous@cabinet.fr", "motdepasse").exceptionOrNull(),
        )
    }

    /** L'inscription refusée ne laisse pas de trace : la transaction a été annulée. */
    @Test
    fun aRefusedRegistration_leavesTheExistingAccountUntouched() = runTest {
        val database = newDatabase()
        val repository = SqlDelightAuthRepository(database, clock)
        repository.register(account(), "motdepasse").getOrThrow()
        val before = database.userAccountQueries.selectByEmail("vous@cabinet.fr").executeAsOne()

        repository.register(account(), "autrechose")

        val after = database.userAccountQueries.selectByEmail("vous@cabinet.fr").executeAsOne()
        assertEquals(before.passwordHash, after.passwordHash)
        assertTrue(repository.login("vous@cabinet.fr", "motdepasse").isSuccess)
    }
}
