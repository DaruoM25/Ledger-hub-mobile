package com.ledgerhub

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.ledgerhub.db.LedgerHubDatabase
import com.ledgerhub.presentation.dashboard.DashboardTags
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
    fun createInvoiceButton_opensInvoiceForm() = runComposeUiTest {
        setContent { App(database = newDatabase()) }

        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(DashboardTags.SCREEN).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithText("Créer une facture", substring = true).performClick()

        onNodeWithTag(InvoiceFormTags.SCREEN).assertIsDisplayed()
    }
}
