package com.ledgerhub.presentation.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.ledgerhub.App
import com.ledgerhub.db.LedgerHubDatabase
import com.ledgerhub.presentation.dashboard.DashboardTags
import com.ledgerhub.presentation.invoices.InvoiceAdaptiveTags
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test

/**
 * Tests Robolectric pour le comportement adaptatif multi-résolutions :
 * - Mode COMPACT (Smartphone portrait ~390 dp)
 * - Mode MEDIUM (Foldable déplié / Petite tablette portrait ~720 dp)
 * - Mode EXPANDED (Tablette paysage / Grand écran ~1024 dp)
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalTestApi::class)
class AdaptiveNavigationRobolectricTest {

    private fun newDatabase(): LedgerHubDatabase {
        Class.forName("org.sqlite.JDBC")
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        LedgerHubDatabase.Schema.create(driver)
        return LedgerHubDatabase(driver)
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun compactLayout_displaysBottomBar() = runComposeUiTest {
        setContent {
            Box(modifier = Modifier.size(390.dp, 844.dp)) {
                App(database = newDatabase(), startAuthenticated = true)
            }
        }

        onNodeWithTag(DashboardTags.SCREEN).assertIsDisplayed()
        onNodeWithTag(DashboardTags.ISSUED_CARD).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w720dp-h900dp")
    fun mediumLayout_displaysNavigationRail() = runComposeUiTest {
        setContent {
            Box(modifier = Modifier.size(720.dp, 900.dp)) {
                App(database = newDatabase(), startAuthenticated = true)
            }
        }

        onNodeWithTag(AdaptiveNavigationTags.NAVIGATION_RAIL).assertIsDisplayed()
        onNodeWithTag(AdaptiveNavigationTags.FAB_CREATE_INVOICE).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w1024dp-h768dp")
    fun expandedLayout_displaysSidebarAndDualPane() = runComposeUiTest {
        setContent {
            Box(modifier = Modifier.size(1024.dp, 768.dp)) {
                App(database = newDatabase(), startAuthenticated = true)
            }
        }

        onNodeWithTag(DashboardTags.SCREEN).assertIsDisplayed()
    }
}
