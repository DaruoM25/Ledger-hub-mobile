package com.ledgerhub.presentation.invoices

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.ledgerhub.data.invoice.SqlDelightInvoiceRepository
import com.ledgerhub.db.LedgerHubDatabase
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.domain.invoice.canDelete
import com.ledgerhub.presentation.invoices.components.InvoiceCard
import com.ledgerhub.presentation.invoices.components.InvoiceCardTags
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Immutabilité fiscale d'une facture **persistée** : une facture émise ne doit exposer aucun
 * chemin de mutation, ni côté domaine ni côté interface.
 *
 * Le test part de la base réelle (SQLDelight sur `JdbcSqliteDriver` en mémoire) plutôt que d'un
 * objet construit à la volée : c'est le statut relu depuis la base qui doit verrouiller l'écran,
 * pas seulement celui posé en mémoire à l'émission.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalTestApi::class)
class InvoiceImmutabilityUiTest {

    private fun newRepository(): SqlDelightInvoiceRepository {
        // Chargement explicite du driver — classloader sandboxé de Robolectric.
        Class.forName("org.sqlite.JDBC")
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        LedgerHubDatabase.Schema.create(driver)
        return SqlDelightInvoiceRepository(LedgerHubDatabase(driver), userEmail = "qa@ledgerhub.app")
    }

    private fun invoice(number: String, status: InvoiceStatus) = Invoice(
        number = number,
        issueDate = "2026-06-24",
        issuer = Party("Cabinet LedgerHub", "820329331", "82032933100027"),
        recipient = Party("Boulangerie Moreau SARL", "784102336", "78410233600021"),
        lines = listOf(
            InvoiceLine("Prestation de conseil", quantity = 1, unitPriceHt = Money(100_000), vatRate = VatRate.TAUX_NORMAL),
        ),
        status = status,
        dueDate = "2026-07-24",
    )

    /** Relit la facture depuis la base — c'est ce statut-là qui fait foi. */
    private suspend fun persistAndReload(status: InvoiceStatus): Invoice {
        val repository = newRepository()
        repository.submitInvoice(invoice("FAC-2026-0500", status)).getOrThrow()
        return repository.fetchInvoices().getOrThrow().single()
    }

    // ── Domaine : le statut relu verrouille la facture ───────────────────────────────────────

    @Test
    fun persistedValidatedInvoice_exposesIsEditableFalse() = runTest {
        val reloaded = persistAndReload(InvoiceStatus.DEPOSITED)

        assertEquals(InvoiceStatus.DEPOSITED, reloaded.status)
        assertFalse(reloaded.isEditable)
        assertFalse(canDelete(reloaded))
        // Seule voie de sortie légale : l'avoir.
        assertTrue(reloaded.isCancellableByCreditNote)
    }

    @Test
    fun persistedPaidInvoice_exposesIsEditableFalse() = runTest {
        val reloaded = persistAndReload(InvoiceStatus.PAID)

        assertFalse(reloaded.isEditable)
        assertFalse(canDelete(reloaded))
    }

    @Test
    fun persistedDraftInvoice_remainsEditable() = runTest {
        val reloaded = persistAndReload(InvoiceStatus.DRAFT)

        assertTrue(reloaded.isEditable)
        assertTrue(canDelete(reloaded))
        assertFalse(reloaded.isCancellableByCreditNote)
    }

    // ── Interface : aucun point d'entrée mutable sur une facture verrouillée ─────────────────

    @Test
    fun detailScreen_ofAValidatedInvoice_offersNoUsableEditAction() = runTest {
        val reloaded = persistAndReload(InvoiceStatus.DEPOSITED)

        var editRequested = false

        runComposeUiTest {
            setContent {
                InvoiceDetailView(
                    uiState = InvoiceDetailUiState(isLoading = false, invoice = reloaded),
                    onEditClick = { editRequested = true },
                )
            }

            onNodeWithTag(InvoiceDetailScreenTags.EDIT_BUTTON).assertIsNotEnabled()
            // Compose conserve l'action OnClick sur un nœud désactivé et se contente de le marquer
            // [Disabled] : on vérifie donc que le clic ne remonte effectivement rien à l'appelant.
            onNodeWithTag(InvoiceDetailScreenTags.EDIT_BUTTON).performClick()
            assertFalse(editRequested, "Un clic sur une facture émise ne doit ouvrir aucune édition")
            // L'écran de détail défile : la mention peut être hors du viewport de test.
            onNodeWithTag(InvoiceDetailScreenTags.LOCKED_HINT, useUnmergedTree = true)
                .performScrollTo()
                .assertIsDisplayed()
        }
    }

    @Test
    fun detailScreen_ofAPaidInvoice_offersNoUsableEditAction() = runTest {
        val reloaded = persistAndReload(InvoiceStatus.PAID)
        var editRequested = false

        runComposeUiTest {
            setContent {
                InvoiceDetailView(
                    uiState = InvoiceDetailUiState(isLoading = false, invoice = reloaded),
                    onEditClick = { editRequested = true },
                )
            }

            onNodeWithTag(InvoiceDetailScreenTags.EDIT_BUTTON).assertIsNotEnabled()
            onNodeWithTag(InvoiceDetailScreenTags.EDIT_BUTTON).performClick()
            assertFalse(editRequested, "Un clic sur une facture payée ne doit ouvrir aucune édition")
        }
    }

    @Test
    fun detailScreen_ofADraftInvoice_stillOffersTheEditAction() = runTest {
        val reloaded = persistAndReload(InvoiceStatus.DRAFT)

        runComposeUiTest {
            setContent {
                InvoiceDetailView(uiState = InvoiceDetailUiState(isLoading = false, invoice = reloaded))
            }

            onNodeWithTag(InvoiceDetailScreenTags.EDIT_BUTTON).assertIsEnabled()
        }
    }

    @Test
    fun listCard_marksALockedInvoiceWithAPadlock_andLeavesDraftsUnmarked() = runTest {
        val validated = persistAndReload(InvoiceStatus.DEPOSITED)
        val draft = persistAndReload(InvoiceStatus.DRAFT)

        runComposeUiTest {
            setContent { InvoiceCard(invoice = validated, onClick = {}) }
            onNodeWithTag(InvoiceCardTags.lockTag(validated.number), useUnmergedTree = true).assertIsDisplayed()
        }

        runComposeUiTest {
            setContent { InvoiceCard(invoice = draft, onClick = {}) }
            onNodeWithTag(InvoiceCardTags.lockTag(draft.number), useUnmergedTree = true).assertDoesNotExist()
        }
    }
}
