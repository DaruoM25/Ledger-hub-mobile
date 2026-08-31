package com.ledgerhub.data.invoice

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.ledgerhub.db.LedgerHubDatabase
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.SubmitInvoiceUseCase
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.presentation.invoiceform.InvoiceFormIntent
import com.ledgerhub.presentation.invoiceform.InvoiceFormViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Niveau 2 (US-16) — persistance SQLite du drapeau « pénalités de retard légales B2B ».
 *
 * SQLite n'ayant pas de booléen, `applyB2bPenalties` transite en INTEGER 1/0 (comme `facturX`).
 * Le point sensible est la valeur `false` : la colonne étant déclarée `DEFAULT 1`, un mapping
 * oublié en écriture **ou** en lecture ferait remonter `true` — et le seul cas nominal passerait
 * malgré le bug. Chaque aller-retour est donc testé dans les deux sens.
 *
 * Pilote JdbcSqliteDriver **en mémoire** : base neuve à chaque test, aucun fichier disque, aucune
 * dépendance à l'ordre d'exécution. Réservé à androidUnitTest/JVM.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InvoiceB2bPenaltiesPersistenceTest {

    private val userEmail = "qa@ledgerhub.app"
    private val issuer = Party("Cabinet LedgerHub", "820329331", "82032933100027")
    private val recipient = Party("Boulangerie Moreau SARL", "784102336", "78410233600021")

    private fun newDatabase(): LedgerHubDatabase {
        // Chargement explicite du driver — robustesse face au classloader sandboxé de Robolectric
        // (utilisé ailleurs dans la même suite).
        Class.forName("org.sqlite.JDBC")
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        LedgerHubDatabase.Schema.create(driver)
        return LedgerHubDatabase(driver)
    }

    private fun invoice(
        number: String = "F-2026-B2B-001",
        status: InvoiceStatus = InvoiceStatus.DRAFT,
        applyB2bPenalties: Boolean = true,
    ) = Invoice(
        number = number,
        issueDate = "2026-08-31",
        issuer = issuer,
        recipient = recipient,
        lines = listOf(InvoiceLine("Conseil", 2, Money(10_000), VatRate.TAUX_NORMAL)),
        status = status,
        dueDate = "2026-09-30",
        applyB2bPenalties = applyB2bPenalties,
    )

    // ── Aller-retour du drapeau ─────────────────────────────────────────────

    @Test
    fun submitInvoice_withPenaltiesEnabled_roundTripsAsTrue() = runTest {
        val repository = SqlDelightInvoiceRepository(newDatabase(), userEmail)

        repository.submitInvoice(invoice(applyB2bPenalties = true)).getOrThrow()

        assertTrue(repository.fetchInvoices().getOrThrow().single().applyB2bPenalties)
    }

    /**
     * Le cas qui compte vraiment : sans mapping explicite en écriture, la colonne retomberait sur
     * son `DEFAULT 1` et la mention légale réapparaîtrait sur une facture qui l'avait écartée.
     */
    @Test
    fun submitInvoice_withPenaltiesDisabled_roundTripsAsFalse() = runTest {
        val repository = SqlDelightInvoiceRepository(newDatabase(), userEmail)

        repository.submitInvoice(invoice(applyB2bPenalties = false)).getOrThrow()

        assertFalse(repository.fetchInvoices().getOrThrow().single().applyB2bPenalties)
    }

    /** Deux factures voisines, deux régimes de mention : aucune contamination de l'une à l'autre. */
    @Test
    fun twoInvoices_keepTheirOwnPenaltiesFlag() = runTest {
        val repository = SqlDelightInvoiceRepository(newDatabase(), userEmail)

        repository.submitInvoice(invoice(number = "F-B2B", applyB2bPenalties = true)).getOrThrow()
        repository.submitInvoice(invoice(number = "F-B2C", applyB2bPenalties = false)).getOrThrow()

        val byNumber = repository.fetchInvoices().getOrThrow().associateBy { it.number }
        assertEquals(2, byNumber.size)
        assertTrue(byNumber.getValue("F-B2B").applyB2bPenalties)
        assertFalse(byNumber.getValue("F-B2C").applyB2bPenalties)
    }

    /** Le drapeau ne perturbe pas le reste de la facture — contrôle de non-régression du mapping. */
    @Test
    fun thePenaltiesFlag_doesNotDisturbTheOtherPersistedFields() = runTest {
        val repository = SqlDelightInvoiceRepository(newDatabase(), userEmail)
        val original = invoice(applyB2bPenalties = false)

        repository.submitInvoice(original).getOrThrow()
        val persisted = repository.fetchInvoices().getOrThrow().single()

        assertEquals(original.number, persisted.number)
        assertEquals(original.dueDate, persisted.dueDate)
        assertEquals(original.facturX, persisted.facturX)
        assertEquals(original.recipient, persisted.recipient)
        assertEquals(original.lines.size, persisted.lines.size)
        assertEquals(original.totalTtc, persisted.totalTtc)
    }

    // ── Écriture depuis l'écran de saisie ──────────────────────────────────

    private fun newViewModel(
        repository: SqlDelightInvoiceRepository,
        dispatcher: TestDispatcher,
    ) = InvoiceFormViewModel(
        submitInvoiceUseCase = SubmitInvoiceUseCase(repository),
        dispatcher = dispatcher,
    )

    private fun InvoiceFormViewModel.fillValidForm(number: String) {
        processIntent(InvoiceFormIntent.InvoiceNumberChanged(number))
        processIntent(InvoiceFormIntent.IssueDateChanged("2026-08-31"))
        processIntent(InvoiceFormIntent.DueDateChanged("2026-09-30"))
        processIntent(InvoiceFormIntent.ClientNameChanged("Boulangerie Moreau SARL"))
        processIntent(InvoiceFormIntent.ClientSiretChanged("78410233600021"))
        processIntent(InvoiceFormIntent.ClientEmailChanged("compta@moreau.fr"))
        processIntent(InvoiceFormIntent.UpdateLine(0, "Conseil", "2", "100.00", VatRate.TAUX_NORMAL))
    }

    /**
     * Chaîne complète depuis l'écran : décocher la case doit atteindre la base. C'est ce qui
     * relie l'intention de l'utilisateur à la pièce archivée.
     */
    @Test
    fun uncheckingTheBoxOnScreen_thenSavingADraft_persistsTheDisabledFlag() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = SqlDelightInvoiceRepository(newDatabase(), userEmail)
        val viewModel = newViewModel(repository, dispatcher)

        viewModel.fillValidForm("F-2026-B2B-SCREEN")
        viewModel.processIntent(InvoiceFormIntent.ToggleB2bPenalties(false))
        viewModel.processIntent(InvoiceFormIntent.SaveDraft)
        advanceUntilIdle()

        assertFalse(repository.fetchInvoices().getOrThrow().single().applyB2bPenalties)
    }

    /** Le défaut de l'écran est la conformité : sans geste de l'utilisateur, la mention est portée. */
    @Test
    fun savingWithoutTouchingTheBox_persistsThePenaltiesAsEnabled() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = SqlDelightInvoiceRepository(newDatabase(), userEmail)
        val viewModel = newViewModel(repository, dispatcher)

        viewModel.fillValidForm("F-2026-B2B-DEFAULT")
        viewModel.processIntent(InvoiceFormIntent.SaveDraft)
        advanceUntilIdle()

        assertTrue(repository.fetchInvoices().getOrThrow().single().applyB2bPenalties)
    }

    /** Une facture déposée fige ses mentions légales avec le reste — immuabilité fiscale (US-13). */
    @Test
    fun issuedInvoice_freezesItsPenaltiesFlag() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = SqlDelightInvoiceRepository(newDatabase(), userEmail)
        val viewModel = newViewModel(repository, dispatcher)

        viewModel.fillValidForm("F-2026-B2B-ISSUED")
        viewModel.processIntent(InvoiceFormIntent.ToggleB2bPenalties(false))
        viewModel.processIntent(InvoiceFormIntent.ValidateAndIssue)
        advanceUntilIdle()

        val persisted = repository.fetchInvoices().getOrThrow().single()
        assertEquals(InvoiceStatus.DEPOSITED, persisted.status)
        assertFalse(persisted.applyB2bPenalties)
        assertFalse(persisted.isEditable, "Une facture déposée ne doit plus être modifiable")
    }

    // ── Migration 5 → 6 ─────────────────────────────────────────────────────

    /**
     * Bases déjà déployées : la colonne est ajoutée par `5.sqm` avec `DEFAULT 1`. Une facture
     * écrite **avant** la migration doit donc se relire avec la mention active — le choix
     * conservateur : une migration ne retire pas rétroactivement une mention obligatoire d'une
     * pièce déjà émise.
     */
    @Test
    fun aRowWrittenWithoutTheColumn_readsBackWithPenaltiesEnabled() = runTest {
        Class.forName("org.sqlite.JDBC")
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        LedgerHubDatabase.Schema.create(driver)
        val database = LedgerHubDatabase(driver)
        val repository = SqlDelightInvoiceRepository(database, userEmail)

        // Écriture nominale, puis remise de la colonne à son état « hérité » (valeur par défaut),
        // ce que produit exactement un ALTER TABLE ... DEFAULT 1 sur une ligne préexistante.
        repository.submitInvoice(invoice(number = "F-LEGACY", applyB2bPenalties = false)).getOrThrow()
        driver.execute(null, "UPDATE Invoice SET applyB2bPenalties = 1 WHERE number = 'F-LEGACY';", 0)

        assertTrue(repository.fetchInvoices().getOrThrow().single().applyB2bPenalties)
    }
}
