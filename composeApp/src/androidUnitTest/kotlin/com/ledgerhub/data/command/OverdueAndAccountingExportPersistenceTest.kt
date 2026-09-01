package com.ledgerhub.data.command

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.ledgerhub.data.invoice.SqlDelightInvoiceRepository
import com.ledgerhub.db.LedgerHubDatabase
import com.ledgerhub.domain.export.LedgerCsvExport
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceOverdue
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.domain.time.FixedClock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Niveau 2 (US-19) — les deux actions rapides qui touchent aux données réelles.
 *
 * Le niveau 1 verrouille les règles sur des factures fabriquées en mémoire ; celui-ci ferme la
 * boucle sur une vraie base. C'est là qu'on vérifie ce qu'un test en mémoire ne peut pas prouver :
 * que `dueDate` **survit à l'aller-retour SQLDelight** (sans quoi aucune facture ne serait jamais
 * en retard en production, le champ revenant vide), et que l'export comptable porte bien sur les
 * factures persistées.
 *
 * Pilote JdbcSqliteDriver en mémoire, réservé à androidUnitTest/JVM.
 */
class OverdueAndAccountingExportPersistenceTest {

    private val clock = FixedClock("2026-09-01T10:15:00Z")
    private val today = "2026-09-01"
    private val userEmail = "qa@ledgerhub.app"
    private val issuer = Party("Cabinet LedgerHub", "820329331", "82032933100027")

    private fun newDatabase(): LedgerHubDatabase {
        // Chargement explicite du driver — robustesse face au classloader sandboxé de Robolectric.
        Class.forName("org.sqlite.JDBC")
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        LedgerHubDatabase.Schema.create(driver)
        return LedgerHubDatabase(driver)
    }

    /** 240,00 € TTC. */
    private fun invoice(
        number: String,
        clientName: String = "Boulangerie Moreau SARL",
        dueDate: String = "2026-08-15",
    ) = Invoice(
        number = number,
        issueDate = "2026-08-01",
        issuer = issuer,
        recipient = Party(clientName, "784102336", "78410233600021"),
        lines = listOf(InvoiceLine("Conseil réglementaire", 2, Money(10_000), VatRate.TAUX_NORMAL)),
        status = InvoiceStatus.DRAFT,
        dueDate = dueDate,
    )

    private suspend fun seed(repository: SqlDelightInvoiceRepository, vararg invoices: Invoice) {
        invoices.forEach { repository.submitInvoice(it).getOrThrow() }
    }

    // ── Retard sur données persistées ───────────────────────────────────────

    /**
     * Le test qui compte : si `dueDate` ne revenait pas de la base, aucune facture ne serait jamais
     * déclarée en retard — et le défaut serait invisible, la liste étant simplement vide.
     */
    @Test
    fun theDueDate_survivesThePersistenceRoundTrip() = runTest {
        val database = newDatabase()
        val repository = SqlDelightInvoiceRepository(database, userEmail, clock)
        seed(repository, invoice("FAC-2026-0401", dueDate = "2026-08-15"))

        val reloaded = repository.fetchInvoices().getOrThrow().single()

        assertEquals("2026-08-15", reloaded.dueDate)
    }

    @Test
    fun overdueInvoices_areResolvedFromThePersistedLedger() = runTest {
        val database = newDatabase()
        val repository = SqlDelightInvoiceRepository(database, userEmail, clock)
        seed(
            repository,
            invoice("FAC-LATE-1", dueDate = "2026-07-15"),
            invoice("FAC-LATE-2", dueDate = "2026-08-20"),
            invoice("FAC-FUTURE", dueDate = "2026-12-31"),
        )
        // Seules les factures deposees sont des creances : le semis les cree en brouillon.
        listOf("FAC-LATE-1", "FAC-LATE-2", "FAC-FUTURE").forEach { number ->
            repository.changeStatus(number, InvoiceStatus.DRAFT, InvoiceStatus.DEPOSITED, null).getOrThrow()
        }

        val overdue = InvoiceOverdue.filter(repository.fetchInvoices().getOrThrow(), today)

        assertEquals(listOf("FAC-LATE-1", "FAC-LATE-2"), overdue.map { it.number })
        assertEquals(Money(48_000), InvoiceOverdue.totalTtc(overdue, today))
    }

    /** Une facture encaissée sort du retard : le statut relu doit suffire à l'exclure. */
    @Test
    fun aPaidInvoice_leavesTheOverdueList() = runTest {
        val database = newDatabase()
        val repository = SqlDelightInvoiceRepository(database, userEmail, clock)
        seed(repository, invoice("FAC-LATE-1", dueDate = "2026-07-15"))
        repository.changeStatus("FAC-LATE-1", InvoiceStatus.DRAFT, InvoiceStatus.DEPOSITED, null).getOrThrow()
        assertEquals(1, InvoiceOverdue.filter(repository.fetchInvoices().getOrThrow(), today).size)

        repository.changeStatus("FAC-LATE-1", InvoiceStatus.DEPOSITED, InvoiceStatus.PAID, null).getOrThrow()

        assertTrue(InvoiceOverdue.filter(repository.fetchInvoices().getOrThrow(), today).isEmpty())
    }

    // ── Export comptable sur données persistées ─────────────────────────────

    @Test
    fun theAccountingExport_coversEveryPersistedInvoice() = runTest {
        val database = newDatabase()
        val repository = SqlDelightInvoiceRepository(database, userEmail, clock)
        seed(repository, invoice("FAC-2026-0401"), invoice("FAC-2026-0402"))

        val csv = LedgerCsvExport.generate(
            repository.fetchInvoices().getOrThrow().sortedBy { it.number },
        )

        val rows = csv.split("\r\n")
        assertEquals(3, rows.size, "Un en-tête et une ligne par facture")
        assertTrue(rows[1].startsWith("FAC-2026-0401;"))
        assertTrue(rows[2].startsWith("FAC-2026-0402;"))
    }

    /**
     * L'échappement doit tenir sur des données réellement relues : un nom de client à
     * point-virgule décalerait sinon toutes les colonnes du fichier remis au comptable.
     */
    @Test
    fun aClientNameWithASeparator_staysEscapedAfterPersistence() = runTest {
        val database = newDatabase()
        val repository = SqlDelightInvoiceRepository(database, userEmail, clock)
        seed(repository, invoice("FAC-2026-0401", clientName = "Moreau; Fils"))

        val csv = LedgerCsvExport.generate(repository.fetchInvoices().getOrThrow())

        assertTrue(csv.contains("\"Moreau; Fils\""), "Séparateur non échappé après relecture : $csv")
    }

    @Test
    fun theExportedAmounts_matchThePersistedTotals() = runTest {
        val database = newDatabase()
        val repository = SqlDelightInvoiceRepository(database, userEmail, clock)
        seed(repository, invoice("FAC-2026-0401"))

        val row = LedgerCsvExport.generate(repository.fetchInvoices().getOrThrow()).split("\r\n")[1]

        assertTrue(row.contains("200,00"), "Total HT attendu : $row")
        assertTrue(row.contains("40,00"), "Total TVA attendu : $row")
        assertTrue(row.contains("240,00"), "Total TTC attendu : $row")
    }
}
