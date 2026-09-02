package com.ledgerhub

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.ledgerhub.db.LedgerHubDatabase
import com.ledgerhub.presentation.components.LangToggleTags
import com.ledgerhub.presentation.dashboard.DashboardTags
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
        setContent { App(database = newDatabase()) }

        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(DashboardTags.SCREEN).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag(DashboardTags.SCREEN).assertIsDisplayed()
    }

    @Test
    fun bottomBar_navigatesToClientsPlaceholder() = runComposeUiTest {
        setContent { App(database = newDatabase()) }

        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(DashboardTags.SCREEN).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithText("Clients").performClick()

        onNodeWithTag(PlaceholderTags.CLIENTS).assertIsDisplayed()
    }

    @Test
    fun bottomBar_navigatesToDgfipDirectory() = runComposeUiTest {
        setContent { App(database = newDatabase()) }

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
        setContent { App(database = newDatabase()) }

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
        setContent { App(database = newDatabase()) }

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
        setContent { App(database = newDatabase()) }

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
}
