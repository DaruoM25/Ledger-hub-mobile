package com.ledgerhub

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.ledgerhub.db.LedgerHubDatabase
import com.ledgerhub.domain.i18n.AppLanguage
import com.ledgerhub.domain.i18n.AppTranslations
import com.ledgerhub.domain.i18n.StringKey
import com.ledgerhub.presentation.auth.AuthTags
import com.ledgerhub.presentation.components.LangToggleTags
import com.ledgerhub.presentation.components.ThemeToggleTags
import com.ledgerhub.presentation.dashboard.DashboardTags
import com.ledgerhub.presentation.ereporting.EReportingTags
import com.ledgerhub.presentation.export.ExportModalTags
import com.ledgerhub.presentation.directory.DirectoryTags
import com.ledgerhub.presentation.integrations.IntegrationsHubTags
import com.ledgerhub.presentation.invoiceform.InvoiceFormTags
import com.ledgerhub.presentation.placeholder.PlaceholderTags
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test

/**
 * Tests IHM Robolectric du shell de navigation responsive de [App] (fenêtre Robolectric par
 * défaut = compact → barre de navigation en bas). Base SQLDelight en mémoire, comme
 * SqlDelightInvoiceRepositoryTest.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalTestApi::class)
class AppShellRobolectricTest {

    private fun newDatabase(): LedgerHubDatabase {
        Class.forName("org.sqlite.JDBC")
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        LedgerHubDatabase.Schema.create(driver)
        return LedgerHubDatabase(driver)
    }

    @Test
    fun app_startsOnOverview_andRendersDashboard() = runComposeUiTest {
        setContent { App(database = newDatabase(), startAuthenticated = true) }

        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(DashboardTags.SCREEN).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag(DashboardTags.SCREEN).assertIsDisplayed()
    }

    @Test
    fun bottomBar_navigatesToClientsPlaceholder() = runComposeUiTest {
        setContent { App(database = newDatabase(), startAuthenticated = true) }

        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(DashboardTags.SCREEN).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithText("Clients").performClick()

        onNodeWithTag(PlaceholderTags.CLIENTS).assertIsDisplayed()
    }

    @Test
    fun bottomBar_navigatesToDgfipDirectory() = runComposeUiTest {
        setContent { App(database = newDatabase(), startAuthenticated = true) }

        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(DashboardTags.SCREEN).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithText("Annuaire DGFIP").performClick()

        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(DirectoryTags.SCREEN).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag(DirectoryTags.SCREEN).assertIsDisplayed()
    }

    @Test
    fun createInvoiceButton_opensInvoiceForm() = runComposeUiTest {
        setContent { App(database = newDatabase(), startAuthenticated = true) }

        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(DashboardTags.SCREEN).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithText("Créer une facture", substring = true).performClick()

        onNodeWithTag(InvoiceFormTags.SCREEN).assertIsDisplayed()
    }

    /**
     * Point d'entrée du hub d'intégrations (US-20) : il vit dans le shell, pas dans un onglet.
     * L'écran Robolectric par défaut est compact — c'est donc le déclencheur de l'en-tête qui est
     * éprouvé ici, celui dont la place est la plus disputée.
     */
    @Test
    fun headerTrigger_opensTheIntegrationsHub() = runComposeUiTest {
        setContent { App(database = newDatabase(), startAuthenticated = true) }

        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(DashboardTags.SCREEN).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag(IntegrationsHubTags.TRIGGER).assertIsDisplayed()
        onNodeWithTag(IntegrationsHubTags.TRIGGER).performClick()

        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(IntegrationsHubTags.CONTAINER).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag(IntegrationsHubTags.CONTAINER).assertIsDisplayed()
        onNodeWithTag("integration_card_stripe").assertIsDisplayed()
    }

    /**
     * Point d'entrée de l'export comptable (US-22), et **non-régression de l'en-tête** : ce
     * quatrième déclencheur est celui qui risquait de pousser le sélecteur de langue hors de
     * l'écran d'un téléphone. Le test constate qu'il ne l'a pas fait — c'est pour cela qu'il
     * vérifie la présence du sélecteur, et pas seulement l'ouverture de la modale.
     */
    @Test
    fun headerTrigger_opensTheExportModal_andKeepsTheLanguageSelectorVisible() = runComposeUiTest {
        setContent { App(database = newDatabase(), startAuthenticated = true) }

        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(DashboardTags.SCREEN).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag(ExportModalTags.TRIGGER).assertIsDisplayed()
        onNodeWithTag(IntegrationsHubTags.TRIGGER).assertIsDisplayed()
        onNodeWithTag(LangToggleTags.ROOT).assertIsDisplayed()

        onNodeWithTag(ExportModalTags.TRIGGER).performClick()

        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(ExportModalTags.DIALOG).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag(ExportModalTags.DIALOG).assertIsDisplayed()
        // L'appareil Robolectric par defaut ne fait que 470 dp de haut : la feuille y defile, et
        // le bouton de generation attend sous la ligne de flottaison. C'est le comportement voulu
        // — encore faut-il aller le chercher pour l'affirmer (meme idiome qu'AuthScreenRobolectricTest).
        onNodeWithTag(ExportModalTags.GENERATE_BTN).performScrollTo().assertIsDisplayed()
    }

    // ── Bascule de thème dans le shell (US-25) ───────────────────────────────

    /**
     * **Non-régression de l'en-tête.** La bascule de thème est la cinquième commande à se disputer
     * la largeur d'un téléphone (après l'export, le hub, la palette et le sélecteur de langue).
     * Ce test ne se contente donc pas de la trouver : il exige que le sélecteur de langue soit
     * TOUJOURS visible à côté d'elle. C'est l'assertion qui remplace une estimation de largeur.
     */
    @Test
    fun header_carriesTheThemeToggleWithoutPushingOutTheLanguageSelector() = runComposeUiTest {
        setContent { App(database = newDatabase(), startAuthenticated = true) }

        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(DashboardTags.SCREEN).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag(ThemeToggleTags.ROOT).assertIsDisplayed()
        onNodeWithTag(LangToggleTags.ROOT).assertIsDisplayed()
        onNodeWithTag(ExportModalTags.TRIGGER).assertIsDisplayed()
        onNodeWithTag(IntegrationsHubTags.TRIGGER).assertIsDisplayed()
    }

    /**
     * Le geste de bout en bout, dans le vrai shell et sur une base SQLDelight réelle : appuyer sur
     * le bouton de l'en-tête bascule le thème de l'application.
     *
     * Ce qui est observable ici, c'est le bouton lui-même : il annonce la destination de l'appui,
     * donc son icône et sa description **changent** quand le thème a effectivement basculé — le
     * soleil (« aller au clair ») cède la place à la lune (« revenir au sombre »). Que la palette
     * Material change réellement de valeurs est affirmé par `ThemeToggleRobolectricTest`, qui peut
     * sonder l'intérieur du thème ; ici, on prouve que le shell câble bien le geste à l'état.
     *
     * L'app démarre en sombre (`ThemeMode.Default`) : le mode par défaut est explicite, l'état
     * initial ne dépend donc pas de `isSystemInDarkTheme()` — `false` sous Robolectric.
     */
    @Test
    fun tappingTheHeaderToggle_switchesTheShellTheme() = runComposeUiTest {
        setContent { App(database = newDatabase(), startAuthenticated = true) }

        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(DashboardTags.SCREEN).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag(ThemeToggleTags.ICON_SUN, useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag(ThemeToggleTags.ROOT)
            .assertContentDescriptionContains(
                AppTranslations.get(StringKey.THEME_TOGGLE_TO_LIGHT, AppLanguage.FR),
            )

        onNodeWithTag(ThemeToggleTags.ROOT).performClick()
        waitForIdle()

        onNodeWithTag(ThemeToggleTags.ICON_MOON, useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag(ThemeToggleTags.ROOT)
            .assertContentDescriptionContains(
                AppTranslations.get(StringKey.THEME_TOGGLE_TO_DARK, AppLanguage.FR),
            )

        // Le shell n'a pas bronché : l'écran reste en place et l'en-tête garde ses deux commandes.
        onNodeWithTag(DashboardTags.SCREEN).assertIsDisplayed()
        onNodeWithTag(ThemeToggleTags.ROOT).assertIsDisplayed()
        onNodeWithTag(LangToggleTags.ROOT).assertIsDisplayed()
    }

    // ── Porte d'authentification (US-26) ─────────────────────────────────────

    /**
     * **La dette de navigation relevée par la recette, refermée.** L'écran de connexion existait
     * depuis l'US-21, entièrement testé, et n'était référencé nulle part : lancer l'application
     * ouvrait le tableau de bord sans jamais le montrer. Il garde désormais l'entrée.
     *
     * Ce test appelle `App` **sans** `startAuthenticated` — donc exactement comme `MainActivity`.
     */
    @Test
    fun app_withoutAuthentication_showsTheLoginScreenInsteadOfTheShell() = runComposeUiTest {
        setContent { App(database = newDatabase()) }
        waitForIdle()

        onNodeWithTag(AuthTags.SCREEN).assertIsDisplayed()
        onNodeWithTag(DashboardTags.SCREEN).assertDoesNotExist()
    }

    /** Le contrepoint : la porte franchie, c'est bien le shell qui est rendu. */
    @Test
    fun app_whenAlreadyAuthenticated_skipsTheLoginScreen() = runComposeUiTest {
        setContent { App(database = newDatabase(), startAuthenticated = true) }

        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(DashboardTags.SCREEN).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag(AuthTags.SCREEN).assertDoesNotExist()
    }

    // ── e-Reporting (US-26) ──────────────────────────────────────────────────

    /**
     * Second écran orphelin refermé. Le point d'entrée vit dans l'onglet Paramètres et non dans
     * un septième onglet : la barre de navigation compacte porte déjà six destinations dont les
     * libellés se coupent sur un Pixel 5.
     */
    @Test
    fun settingsTab_carriesTheEReportingEntryPoint() = runComposeUiTest {
        setContent { App(database = newDatabase(), startAuthenticated = true) }

        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(DashboardTags.SCREEN).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithText("Paramètres").performClick()

        onNodeWithTag(EREPORTING_TRIGGER_TAG).assertIsDisplayed()
    }

    @Test
    fun theEReportingEntryPoint_opensTheEReportingScreen() = runComposeUiTest {
        setContent { App(database = newDatabase(), startAuthenticated = true) }

        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(DashboardTags.SCREEN).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithText("Paramètres").performClick()
        onNodeWithTag(EREPORTING_TRIGGER_TAG).performClick()

        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(EReportingTags.SCREEN).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag(EReportingTags.SCREEN).assertIsDisplayed()
        onNodeWithTag(EReportingTags.BADGE).assertIsDisplayed()
    }
}
