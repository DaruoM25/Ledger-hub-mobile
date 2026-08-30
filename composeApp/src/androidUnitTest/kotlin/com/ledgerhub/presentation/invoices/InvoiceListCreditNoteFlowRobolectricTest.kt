package com.ledgerhub.presentation.invoices

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.runComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.ledgerhub.App
import com.ledgerhub.db.LedgerHubDatabase
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.presentation.creditnoteform.CreditNoteFormTags
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * US-10 — entrée « Créer un avoir » depuis la liste des factures.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalTestApi::class)
class InvoiceListCreditNoteFlowRobolectricTest {

    private fun newDatabase(): LedgerHubDatabase {
        Class.forName("org.sqlite.JDBC")
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        LedgerHubDatabase.Schema.create(driver)
        return LedgerHubDatabase(driver)
    }

    private val finalizedInvoice = Invoice(
        number = "FAC-2026-0137",
        issueDate = "2026-07-12",
        issuer = Party("Cabinet LedgerHub", "820329331", "82032933100027"),
        recipient = Party("Boulangerie Moreau SARL", "784102336", "78410233600021"),
        lines = listOf(InvoiceLine("Prestation de conseil", quantity = 1, unitPriceHt = Money(220_000), vatRate = VatRate.TAUX_NORMAL)),
        status = InvoiceStatus.DEPOSITED,
    )

    // ── Composant sans état : le bouton n'apparaît que sur une facture finalisable ──

    @Test
    fun creditNoteAction_isShownForAFinalizedInvoice_andInvokesTheCallback() = runComposeUiTest {
        var captured: Invoice? = null
        setContent {
            InvoiceListView(
                uiState = InvoiceListUiState(
                    isLoading = false,
                    invoices = listOf(finalizedInvoice),
                ),
                onIntent = {},
                onInvoiceClick = {},
                onCreateCreditNote = { captured = it },
            )
        }

        onNodeWithTag(InvoiceListTags.creditNoteAction("FAC-2026-0137"))
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        assertEquals("FAC-2026-0137", captured?.number)
    }

    @Test
    fun creditNoteAction_isHidden_forADraftInvoice() = runComposeUiTest {
        setContent {
            InvoiceListView(
                uiState = InvoiceListUiState(
                    isLoading = false,
                    invoices = listOf(finalizedInvoice.copy(status = InvoiceStatus.DRAFT)),
                ),
                onIntent = {},
                onInvoiceClick = {},
            )
        }

        onAllNodesWithTag(InvoiceListTags.creditNoteAction("FAC-2026-0137")).assertCountEquals(0)
    }

    // ── Intégration : depuis App, l'action ouvre l'écran d'avoir violet ──

    @Test
    fun fromApp_tappingCreateCreditNote_opensTheCreditNoteScreen() = runComposeUiTest {
        setContent { App(database = newDatabase()) }

        onNodeWithText("Factures").performClick()

        // Le semis des factures de démonstration est asynchrone : on attend que l'action
        // d'avoir de la facture déposée FAC-2026-0137 soit composée dans la liste.
        val actionTag = InvoiceListTags.creditNoteAction("FAC-2026-0137")
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(InvoiceListTags.LIST).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag(InvoiceListTags.LIST).performScrollToNode(hasTestTag(actionTag))
        onNodeWithTag(actionTag).performClick()

        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(CreditNoteFormTags.SCREEN).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag(CreditNoteFormTags.SCREEN).assertIsDisplayed()
        onNodeWithTag(CreditNoteFormTags.DRAFT_BADGE).performScrollTo().assertIsDisplayed()
        onNodeWithText("Annule la facture FAC-2026-0137 du 2026-07-12").assertIsDisplayed()
    }
}
