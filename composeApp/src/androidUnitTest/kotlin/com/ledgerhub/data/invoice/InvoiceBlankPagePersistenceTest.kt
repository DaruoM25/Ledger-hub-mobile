package com.ledgerhub.data.invoice

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.ledgerhub.db.LedgerHubDatabase
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
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
import kotlin.test.assertTrue

/**
 * Niveau 2 (US-15) — la saisie effectuée **en mode Page Blanche** atteint bien SQLite.
 *
 * La feuille blanche n'émet aucune intention qui lui soit propre : ce test rejoue exactement la
 * séquence d'intentions produite par ses cellules ([InvoiceFormIntent.UpdateLine],
 * [InvoiceFormIntent.ClientNameChanged], [InvoiceFormIntent.ClientSiretChanged]…) sur un
 * [SqlDelightInvoiceRepository] réel, puis relit la base. C'est la preuve de bout en bout que
 * les deux modes de saisie écrivent la même facture — la promesse centrale de l'US-15.
 *
 * Pilote JdbcSqliteDriver **en mémoire**, comme [SqlDelightInvoiceRepositoryTest] : base neuve à
 * chaque test, aucun fichier disque, aucune dépendance à l'ordre d'exécution. Réservé à
 * androidUnitTest/JVM (pas de JdbcSqliteDriver sur cibles natives).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InvoiceBlankPagePersistenceTest {

    private val userEmail = "qa@ledgerhub.app"
    private val clientSiret = "98765432100045"

    private fun newDatabase(): LedgerHubDatabase {
        // Chargement explicite du driver — robustesse face au classloader sandboxé de Robolectric
        // (utilisé ailleurs dans la même suite).
        Class.forName("org.sqlite.JDBC")
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        LedgerHubDatabase.Schema.create(driver)
        return LedgerHubDatabase(driver)
    }

    private fun newViewModel(
        repository: SqlDelightInvoiceRepository,
        dispatcher: TestDispatcher,
    ) = InvoiceFormViewModel(
        submitInvoiceUseCase = SubmitInvoiceUseCase(repository),
        dispatcher = dispatcher,
    )

    /**
     * Saisie de l'en-tête telle qu'elle se fait **sur la feuille** : la raison sociale et le SIRET
     * sont tapés dans l'encart « Facturé à », les métadonnées dans le bandeau du haut.
     */
    private fun InvoiceFormViewModel.fillHeaderOnPaper(number: String = "F-2026-NOTION-001") {
        processIntent(InvoiceFormIntent.InvoiceNumberChanged(number))
        processIntent(InvoiceFormIntent.IssueDateChanged("2026-08-31"))
        processIntent(InvoiceFormIntent.DueDateChanged("2026-09-30"))
        processIntent(InvoiceFormIntent.ClientNameChanged("Boulangerie Moreau SARL"))
        processIntent(InvoiceFormIntent.ClientSiretChanged(clientSiret))
        processIntent(InvoiceFormIntent.ClientEmailChanged("compta@moreau.fr"))
    }

    /**
     * Cas nominal : trois lignes saisies directement dans le tableau de la feuille, dont une au
     * taux réduit, puis « Enregistrer le brouillon ». Tout doit se retrouver en base à l'identique.
     */
    @Test
    fun linesTypedOnTheBlankPage_arePersistedVerbatim_onSaveDraft() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = SqlDelightInvoiceRepository(newDatabase(), userEmail)
        val viewModel = newViewModel(repository, dispatcher)

        viewModel.fillHeaderOnPaper()
        viewModel.processIntent(
            InvoiceFormIntent.UpdateLine(0, "Conseil réglementaire", "2", "100.00", VatRate.TAUX_NORMAL),
        )
        viewModel.processIntent(InvoiceFormIntent.AddLine)
        viewModel.processIntent(
            InvoiceFormIntent.UpdateLine(1, "Ouvrage documentaire", "3", "19.90", VatRate.TAUX_REDUIT),
        )
        viewModel.processIntent(InvoiceFormIntent.AddLine)
        viewModel.processIntent(
            InvoiceFormIntent.UpdateLine(2, "Formation équipe", "1", "450.00", VatRate.TAUX_NORMAL),
        )

        val displayedTotals = viewModel.uiState.value
        viewModel.processIntent(InvoiceFormIntent.SaveDraft)
        advanceUntilIdle()

        val persisted = repository.fetchInvoices().getOrThrow().single()

        assertEquals("F-2026-NOTION-001", persisted.number)
        assertEquals(InvoiceStatus.DRAFT, persisted.status)
        assertEquals("Boulangerie Moreau SARL", persisted.recipient.name)
        assertEquals(clientSiret, persisted.recipient.siret)

        // Les trois lignes, dans l'ordre de saisie, avec leur taux respectif.
        assertEquals(3, persisted.lines.size)
        assertEquals(listOf("Conseil réglementaire", "Ouvrage documentaire", "Formation équipe"), persisted.lines.map { it.label })
        assertEquals(listOf(2, 3, 1), persisted.lines.map { it.quantity })
        assertEquals(listOf(Money(10_000), Money(1_990), Money(45_000)), persisted.lines.map { it.unitPriceHt })
        assertEquals(
            listOf(VatRate.TAUX_NORMAL, VatRate.TAUX_REDUIT, VatRate.TAUX_NORMAL),
            persisted.lines.map { it.vatRate },
        )

        // Les totaux relus depuis SQLite valent ceux qu'affichait le bas de la feuille : la
        // facture émise est bien celle que l'utilisateur avait sous les yeux.
        assertEquals(displayedTotals.totalHt, persisted.totalHt)
        assertEquals(displayedTotals.totalVat, persisted.totalVat)
        assertEquals(displayedTotals.totalTtc, persisted.totalTtc)
    }

    /**
     * Modifier **une seule cellule** de prix ne doit toucher que sa ligne : les autres lignes
     * sont réécrites à l'identique, sans doublon en table `InvoiceLine`.
     */
    @Test
    fun editingASingleCell_rewritesOnlyThatLine_withoutDuplicatingTheOthers() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = SqlDelightInvoiceRepository(newDatabase(), userEmail)
        val viewModel = newViewModel(repository, dispatcher)

        viewModel.fillHeaderOnPaper(number = "F-2026-NOTION-002")
        viewModel.processIntent(InvoiceFormIntent.UpdateLine(0, "Conseil", "1", "100.00", VatRate.TAUX_NORMAL))
        viewModel.processIntent(InvoiceFormIntent.AddLine)
        viewModel.processIntent(InvoiceFormIntent.UpdateLine(1, "Audit", "1", "200.00", VatRate.TAUX_NORMAL))

        // Correction en place du prix de la seconde ligne, comme on retoucherait un chiffre sur
        // la feuille : 200,00 € -> 250,00 €.
        viewModel.processIntent(InvoiceFormIntent.UpdateLine(1, "Audit", "1", "250.00", VatRate.TAUX_NORMAL))

        viewModel.processIntent(InvoiceFormIntent.SaveDraft)
        advanceUntilIdle()

        val persisted = repository.fetchInvoices().getOrThrow().single()
        assertEquals(2, persisted.lines.size)
        assertEquals(Money(10_000), persisted.lines[0].unitPriceHt)
        assertEquals(Money(25_000), persisted.lines[1].unitPriceHt)
        assertEquals(Money(35_000), persisted.totalHt)
    }

    /**
     * Émission depuis la feuille blanche : la facture bascule en [InvoiceStatus.DEPOSITED] et
     * devient immuable au sens fiscal (US-13). Le mode de saisie ne change rien à ce cycle de vie.
     */
    @Test
    fun validateAndIssueFromTheBlankPage_persistsADepositedInvoice() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = SqlDelightInvoiceRepository(newDatabase(), userEmail)
        val viewModel = newViewModel(repository, dispatcher)

        viewModel.fillHeaderOnPaper(number = "F-2026-NOTION-003")
        viewModel.processIntent(InvoiceFormIntent.UpdateLine(0, "Prestation", "1", "500.00", VatRate.TAUX_NORMAL))

        viewModel.processIntent(InvoiceFormIntent.ValidateAndIssue)
        advanceUntilIdle()

        val persisted = repository.fetchInvoices().getOrThrow().single()
        assertEquals(InvoiceStatus.DEPOSITED, persisted.status)
        assertEquals(Money(60_000), persisted.totalTtc)
        assertTrue(!persisted.isEditable, "Une facture déposée ne doit plus être modifiable")
    }

    /**
     * Une feuille incomplète n'écrit rien. La règle est celle du formulaire classique — mais elle
     * mérite d'être re-vérifiée ici : sur une page blanche, l'absence de libellés de champs rend
     * l'oubli d'une mention obligatoire plus facile, et c'est la base qui doit tenir bon.
     */
    @Test
    fun incompleteBlankPage_writesNothingToTheDatabase() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = SqlDelightInvoiceRepository(newDatabase(), userEmail)
        val viewModel = newViewModel(repository, dispatcher)

        // SIRET client volontairement absent — mention obligatoire manquante.
        viewModel.processIntent(InvoiceFormIntent.InvoiceNumberChanged("F-2026-NOTION-004"))
        viewModel.processIntent(InvoiceFormIntent.IssueDateChanged("2026-08-31"))
        viewModel.processIntent(InvoiceFormIntent.DueDateChanged("2026-09-30"))
        viewModel.processIntent(InvoiceFormIntent.ClientNameChanged("Client sans SIRET"))
        viewModel.processIntent(InvoiceFormIntent.UpdateLine(0, "Prestation", "1", "100.00", VatRate.TAUX_NORMAL))

        viewModel.processIntent(InvoiceFormIntent.SaveDraft)
        advanceUntilIdle()

        assertEquals(emptyList(), repository.fetchInvoices().getOrThrow())
        assertTrue(
            viewModel.uiState.value.errors.isNotEmpty(),
            "La tentative doit révéler les mentions manquantes plutôt que d'écrire une facture invalide",
        )
    }
}
