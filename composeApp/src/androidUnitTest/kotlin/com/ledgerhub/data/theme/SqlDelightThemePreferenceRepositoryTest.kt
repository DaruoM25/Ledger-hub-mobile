package com.ledgerhub.data.theme

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.ledgerhub.db.LedgerHubDatabase
import com.ledgerhub.domain.theme.ThemeMode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Niveau 2 (US-25) — persistance réelle de la préférence de thème sur `JdbcSqliteDriver`
 * **en mémoire** : base neuve à chaque test, suite rapide et indépendante de l'ordre d'exécution.
 *
 * Complète `ThemeViewModelTest` (commonTest), qui vérifie le contrat sur un dépôt double. Ici,
 * c'est le SQL qui est éprouvé — la table mono-ligne, l'upsert, et le repli sur une base vierge.
 * Ce niveau ne peut pas vivre en `commonTest` : le pilote JDBC est indisponible côté iosTest.
 */
class SqlDelightThemePreferenceRepositoryTest {

    private fun newDatabase(): LedgerHubDatabase {
        // Chargement explicite du driver — classloader sandboxé de Robolectric ailleurs dans la suite.
        Class.forName("org.sqlite.JDBC")
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        LedgerHubDatabase.Schema.create(driver)
        return LedgerHubDatabase(driver)
    }

    /** Sur une base vierge, l'app doit démarrer — donc rendre un thème, jamais une absence. */
    @Test
    fun anEmptyDatabase_yieldsTheDefaultDarkTheme() = runTest {
        val repository = SqlDelightThemePreferenceRepository(newDatabase())

        val loaded = repository.loadThemeMode()

        assertTrue(loaded.isSuccess, "Une base vierge n'est pas une erreur de chargement")
        assertEquals(ThemeMode.Default, loaded.getOrNull())
    }

    @Test
    fun everyMode_roundTripsThroughTheDatabase() = runTest {
        ThemeMode.entries.forEach { mode ->
            val repository = SqlDelightThemePreferenceRepository(newDatabase())

            assertTrue(repository.saveThemeMode(mode).isSuccess)

            assertEquals(mode, repository.loadThemeMode().getOrNull(), "Aller-retour rompu pour $mode")
        }
    }

    /**
     * La table est mono-ligne par construction (CHECK sur la clé primaire) : deux écritures
     * successives remplacent la préférence, elles ne l'empilent pas.
     */
    @Test
    fun successiveSaves_replaceThePreferenceInASingleRow() = runTest {
        val database = newDatabase()
        val repository = SqlDelightThemePreferenceRepository(database)

        repository.saveThemeMode(ThemeMode.LIGHT)
        repository.saveThemeMode(ThemeMode.SYSTEM)
        repository.saveThemeMode(ThemeMode.DARK)

        assertEquals(ThemeMode.DARK, repository.loadThemeMode().getOrNull())
        assertEquals(
            1,
            database.uiPreferencesQueries.selectThemeMode().executeAsList().size,
            "UiPreferences doit rester mono-ligne",
        )
    }

    /**
     * Le thème survit à la reconstruction du dépôt sur la **même** base — c'est ce qui distingue
     * une préférence persistée d'un état en mémoire, et ce que l'US exige.
     */
    @Test
    fun aSavedTheme_isReadBackByANewRepositoryInstance() = runTest {
        val database = newDatabase()
        SqlDelightThemePreferenceRepository(database).saveThemeMode(ThemeMode.LIGHT)

        val reopened = SqlDelightThemePreferenceRepository(database).loadThemeMode()

        assertEquals(ThemeMode.LIGHT, reopened.getOrNull())
    }

    /**
     * Une valeur illisible en base — constante renommée entre deux versions — ne doit pas empêcher
     * l'app de démarrer. Elle est écrite ici par la requête brute : aucune API du domaine ne permet
     * de la produire, et c'est précisément le point.
     */
    @Test
    fun anUnknownStoredValue_fallsBackToTheDefault() = runTest {
        val database = newDatabase()
        database.uiPreferencesQueries.upsertThemeMode("SEPIA")

        val loaded = SqlDelightThemePreferenceRepository(database).loadThemeMode()

        assertTrue(loaded.isSuccess, "Une valeur inconnue n'est pas une erreur de chargement")
        assertEquals(ThemeMode.Default, loaded.getOrNull())
    }
}
