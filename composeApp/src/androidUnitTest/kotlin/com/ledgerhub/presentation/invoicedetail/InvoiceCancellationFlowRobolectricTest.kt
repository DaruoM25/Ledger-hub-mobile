package com.ledgerhub.presentation.invoicedetail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.ledgerhub.data.creditnote.SqlDelightCreditNoteRepository
import com.ledgerhub.data.invoice.SqlDelightInvoiceRepository
import com.ledgerhub.db.LedgerHubDatabase
import com.ledgerhub.domain.creditnote.SubmitCreditNoteUseCase
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.presentation.creditnoteform.CreditNoteFormScreen
import com.ledgerhub.presentation.creditnoteform.CreditNoteFormTags
import com.ledgerhub.presentation.creditnoteform.CreditNoteFormViewModel
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test d'IHM Robolectric (Skill 2) couvrant le flux complet du "bouton magique" inversé :
 * ouverture d'une facture finalisée -> clic sur "Annuler par un avoir" -> saisie du motif ->
 * validation -> vérification directe de l'avoir et du statut Annulée en base SQLite **en
 * mémoire** (JdbcSqliteDriver, pas de fichier disque). Aucune infrastructure de navigation
 * dans l'app : les deux écrans sont composés localement au test via un simple bascule d'état,
 * comme le ferait un futur écran hôte.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalTestApi::class)
class InvoiceCancellationFlowRobolectricTest {

    private fun newDatabase(): LedgerHubDatabase {
        // Sous Robolectric, le classloader sandboxé empêche le ServiceLoader du JDBC
        // DriverManager de découvrir org.sqlite.JDBC automatiquement — chargement explicite
        // requis (inutile dans les tests JUnit "nus" comme SqlDelightInvoiceRepositoryTest).
        Class.forName("org.sqlite.JDBC")
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        LedgerHubDatabase.Schema.create(driver)
        return LedgerHubDatabase(driver)
    }

    private fun sourceInvoice() = Invoice(
        number = "F-2026-042",
        issueDate = "2026-08-01",
        issuer = Party("Vendeur SARL", "123456789", "12345678900012"),
        recipient = Party("Client SAS", "987654321", "98765432100045"),
        lines = listOf(InvoiceLine("Conseil", quantity = 2, unitPriceHt = Money(5000), vatRate = VatRate.TAUX_NORMAL)),
        status = InvoiceStatus.DEPOSITED,
    )

    @Test
    fun openFinalizedInvoice_clickCreateCreditNote_submitForm_persistsCreditNote_andCancelsInvoice() = runComposeUiTest {
        val database = newDatabase()
        val invoiceRepository = SqlDelightInvoiceRepository(database, userEmail = "qa@ledgerhub.app")
        val creditNoteRepository = SqlDelightCreditNoteRepository(database, userEmail = "qa@ledgerhub.app")
        val invoice = sourceInvoice()
        runBlocking { invoiceRepository.submitInvoice(invoice) }

        setContent {
            var showCreditNoteForm by remember { mutableStateOf(false) }
            if (!showCreditNoteForm) {
                InvoiceDetailScreen(
                    viewModel = remember { InvoiceDetailViewModel(invoice, creditNoteRepository) },
                    onCreateCreditNoteClick = { showCreditNoteForm = true },
                )
            } else {
                CreditNoteFormScreen(
                    viewModel = remember {
                        CreditNoteFormViewModel(
                            sourceInvoice = invoice,
                            submitCreditNoteUseCase = SubmitCreditNoteUseCase(creditNoteRepository),
                        )
                    }
                )
            }
        }

        // Ouverture facture finalisée -> le bouton "Annuler par un avoir" doit être visible.
        onNodeWithTag(InvoiceDetailTags.STATUS_BADGE).assertIsDisplayed()
        onNodeWithTag(InvoiceDetailTags.CREATE_CREDIT_NOTE_BUTTON).performScrollTo().assertIsDisplayed()

        // Clic -> bascule vers le formulaire d'avoir.
        onNodeWithTag(InvoiceDetailTags.CREATE_CREDIT_NOTE_BUTTON).performScrollTo().performClick()
        onNodeWithTag(CreditNoteFormTags.SCREEN).assertIsDisplayed()

        // Le motif légal est obligatoire avant validation.
        onNodeWithTag(CreditNoteFormTags.ISSUE_DATE).performScrollTo().performTextInput("2026-08-06")
        onNodeWithTag(CreditNoteFormTags.REASON).performScrollTo().performTextInput("Erreur tarifaire")
        onNodeWithTag(CreditNoteFormTags.SUBMIT_BUTTON).performScrollTo().performClick()

        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(CreditNoteFormTags.SUCCESS_MESSAGE).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag(CreditNoteFormTags.SUCCESS_MESSAGE).assertIsDisplayed()

        // Validation directe en base SQLite en mémoire — l'avoir existe, montants négatifs,
        // et la facture d'origine est bien passée au statut Annulée.
        runBlocking {
            val creditNotes = creditNoteRepository.fetchCreditNotes().getOrThrow()
            assertEquals(1, creditNotes.size)
            val creditNote = creditNotes.single()
            assertEquals("AV-2026-0001", creditNote.number)
            assertEquals(invoice.number, creditNote.invoiceId)
            assertEquals("Erreur tarifaire", creditNote.reason)
            assertTrue(creditNote.totalTtc.cents < 0)
            assertEquals(-invoice.totalTtc.cents, creditNote.totalTtc.cents)

            val updatedInvoice = invoiceRepository.fetchInvoices().getOrThrow().single { it.number == invoice.number }
            assertEquals(InvoiceStatus.CANCELLED, updatedInvoice.status)
        }
    }

    @Test
    fun draftInvoice_doesNotShowCreateCreditNoteButton() = runComposeUiTest {
        val database = newDatabase()
        val creditNoteRepository = SqlDelightCreditNoteRepository(database, userEmail = "qa@ledgerhub.app")
        val draftInvoice = sourceInvoice().copy(status = InvoiceStatus.DRAFT)

        setContent {
            InvoiceDetailScreen(
                viewModel = remember { InvoiceDetailViewModel(draftInvoice, creditNoteRepository) },
                onCreateCreditNoteClick = {},
            )
        }

        onNodeWithTag(InvoiceDetailTags.SCREEN).assertIsDisplayed()
        onNodeWithTag(InvoiceDetailTags.CREATE_CREDIT_NOTE_BUTTON).assertDoesNotExist()
    }
}
