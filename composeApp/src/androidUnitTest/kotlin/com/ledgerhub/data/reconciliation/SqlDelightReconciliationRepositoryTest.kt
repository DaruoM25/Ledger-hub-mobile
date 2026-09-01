package com.ledgerhub.data.reconciliation

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.ledgerhub.data.audit.SqlDelightAuditRepository
import com.ledgerhub.data.invoice.SqlDelightInvoiceRepository
import com.ledgerhub.db.LedgerHubDatabase
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.domain.reconciliation.BankTransaction
import com.ledgerhub.domain.reconciliation.ReconcilePaymentUseCase
import com.ledgerhub.domain.time.FixedClock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Niveau 2 (US-18) — le lettrage écrit réellement, et il écrit **tout ensemble**.
 *
 * Le niveau 1 verrouille les règles sur des doubles ; celui-ci ferme la boucle sur une vraie base :
 * ligne de lettrage, statut de la facture et trace d'audit sortent d'une seule transaction. C'est
 * l'invariant que le double ne peut pas prouver — un dépôt qui écrirait ces trois faits en trois
 * temps satisferait tous les tests de niveau 1.
 *
 * [FixedClock] rend les horodatages déterministes. Pilote JdbcSqliteDriver en mémoire, réservé à
 * androidUnitTest/JVM.
 */
class SqlDelightReconciliationRepositoryTest {

    private val clock = FixedClock("2026-09-01T10:15:00Z")
    private val userEmail = "qa@ledgerhub.app"
    private val issuer = Party("Cabinet LedgerHub", "820329331", "82032933100027")
    private val recipient = Party("Boulangerie Moreau SARL", "784102336", "78410233600021")

    private fun newDatabase(): LedgerHubDatabase {
        // Chargement explicite du driver — robustesse face au classloader sandboxé de Robolectric.
        Class.forName("org.sqlite.JDBC")
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        LedgerHubDatabase.Schema.create(driver)
        return LedgerHubDatabase(driver)
    }

    /** 240,00 € TTC. */
    private fun invoice(number: String = "FAC-2026-0301", status: InvoiceStatus = InvoiceStatus.DRAFT) =
        Invoice(
            number = number,
            issueDate = "2026-08-31",
            issuer = issuer,
            recipient = recipient,
            lines = listOf(InvoiceLine("Conseil réglementaire", 2, Money(10_000), VatRate.TAUX_NORMAL)),
            status = status,
        )

    private fun transaction(id: String = "TX-2026-0091", cents: Long = 24_000) = BankTransaction(
        id = id,
        label = "VIR SEPA BOULANGERIE MOREAU",
        amount = Money(cents),
        valueDateIso = "2026-09-01",
        counterparty = "Boulangerie Moreau SARL",
    )

    /**
     * Amène une facture jusqu'à `DEPOSITED` par le vrai chemin : le lettrage doit être éprouvé sur
     * une facture réellement déposée, pas sur une ligne forgée directement en base.
     */
    private suspend fun seedDepositedInvoice(
        database: LedgerHubDatabase,
        number: String = "FAC-2026-0301",
    ): Invoice {
        val invoiceRepository = SqlDelightInvoiceRepository(database, userEmail, clock)
        val draft = invoice(number)
        invoiceRepository.submitInvoice(draft).getOrThrow()
        invoiceRepository.changeStatus(number, InvoiceStatus.DRAFT, InvoiceStatus.DEPOSITED, null).getOrThrow()
        // L'horloge avance : `AuditLog.selectByInvoiceNumber` trie par createdAt puis par id, et
        // deux transitions horodatees a la meme seconde se departageraient sur un UUID aleatoire —
        // l'ordre de l'historique varierait alors d'une execution a l'autre.
        clock.advanceBy(60)
        return draft.copy(status = InvoiceStatus.DEPOSITED)
    }

    // ── Relevé bancaire ─────────────────────────────────────────────────────

    @Test
    fun aStatement_isPersistedAndReadBack() = runTest {
        val database = newDatabase()
        val repository = SqlDelightReconciliationRepository(database, clock)

        repository.saveTransactions(listOf(transaction(), transaction("TX-2026-0092", 11_850))).getOrThrow()

        val stored = repository.fetchTransactions().getOrThrow()
        assertEquals(2, stored.size)
        assertEquals(Money(24_000), stored.first { it.id == "TX-2026-0091" }.amount)
    }

    /** Le semis peut rejouer : une même écriture relue ne doit pas se dédoubler. */
    @Test
    fun savingTheSameStatementTwice_isIdempotent() = runTest {
        val database = newDatabase()
        val repository = SqlDelightReconciliationRepository(database, clock)

        repository.saveTransactions(listOf(transaction())).getOrThrow()
        repository.saveTransactions(listOf(transaction())).getOrThrow()

        assertEquals(1L, repository.countTransactions().getOrThrow())
    }

    /** Le signe est porté par le montant : un décaissement doit ressortir négatif de la base. */
    @Test
    fun aDebit_keepsItsNegativeSign_throughPersistence() = runTest {
        val database = newDatabase()
        val repository = SqlDelightReconciliationRepository(database, clock)

        repository.saveTransactions(listOf(transaction("TX-DEBIT", cents = -32_400))).getOrThrow()

        val stored = repository.fetchTransactions().getOrThrow().single()
        assertEquals(Money(-32_400), stored.amount)
        assertTrue(!stored.isCredit)
    }

    // ── Lettrage ────────────────────────────────────────────────────────────

    @Test
    fun reconciling_writesTheMatch_theStatus_andTheAuditEntry() = runTest {
        val database = newDatabase()
        val repository = SqlDelightReconciliationRepository(database, clock)
        val deposited = seedDepositedInvoice(database)
        repository.saveTransactions(listOf(transaction())).getOrThrow()

        ReconcilePaymentUseCase(repository, clock)(transaction(), deposited).getOrThrow()

        // 1. Le lettrage est inscrit.
        val match = repository.fetchMatches().getOrThrow().single()
        assertEquals("TX-2026-0091", match.transactionId)
        assertEquals("FAC-2026-0301", match.invoiceNumber)
        assertEquals(0L, match.deltaCents)

        // 2. La facture est passée à PAID.
        val reloaded = SqlDelightInvoiceRepository(database, userEmail, clock)
            .fetchInvoices().getOrThrow().single { it.number == "FAC-2026-0301" }
        assertEquals(InvoiceStatus.PAID, reloaded.status)

        // 3. La Piste d'Audit Fiable porte la transition, avec le motif qui la rattache au relevé.
        //    L'entree est designee par son statut d'arrivee, pas par sa position : une assertion
        //    sur `last()` dependrait de l'ordre de tri et masquerait ce qu'elle verifie vraiment.
        val trail = SqlDelightAuditRepository(database).entriesFor("FAC-2026-0301").getOrThrow()
        val paidEntry = trail.single { it.toStatus == InvoiceStatus.PAID }
        assertEquals(InvoiceStatus.DEPOSITED, paidEntry.fromStatus)
        assertEquals("2026-09-01T10:16:00Z", paidEntry.createdAt)
        assertNotNull(paidEntry.reason)
        assertTrue(
            paidEntry.reason!!.contains("TX-2026-0091"),
            "Le motif doit nommer l'écriture bancaire : c'est ce qui justifie la transition en contrôle",
        )
    }

    /** L'écart est figé en base : il devra être justifié tel qu'il a été constaté ce jour-là. */
    @Test
    fun aPartialPayment_persistsItsDelta() = runTest {
        val database = newDatabase()
        val repository = SqlDelightReconciliationRepository(database, clock)
        val deposited = seedDepositedInvoice(database)
        val short = transaction(cents = 23_850)
        repository.saveTransactions(listOf(short)).getOrThrow()

        ReconcilePaymentUseCase(repository, clock)(short, deposited).getOrThrow()

        assertEquals(-150L, repository.fetchMatches().getOrThrow().single().deltaCents)
    }

    /**
     * Atomicité — le cœur du test. L'insertion du lettrage viole la contrainte d'unicité sur la
     * facture ; la transaction doit être annulée **en entier**, sans laisser la facture passée à
     * `PAID` ni une trace d'audit orpheline.
     */
    @Test
    fun aFailedMatch_rollsBackTheStatusAndTheAuditEntry() = runTest {
        val database = newDatabase()
        val repository = SqlDelightReconciliationRepository(database, clock)
        val first = seedDepositedInvoice(database)
        val second = seedDepositedInvoice(database, number = "FAC-2026-0302")
        repository.saveTransactions(
            listOf(transaction(), transaction("TX-2026-0092")),
        ).getOrThrow()

        ReconcilePaymentUseCase(repository, clock)(transaction(), first).getOrThrow()
        val trailBefore = SqlDelightAuditRepository(database).entriesFor("FAC-2026-0302").getOrThrow()

        // Second lettrage écrit DIRECTEMENT par le dépôt, en court-circuitant le cas d'usage :
        // c'est la contrainte de base qui doit tenir, pas seulement la validation applicative.
        val duplicate = com.ledgerhub.domain.reconciliation.ReconciliationMatch(
            transactionId = "TX-2026-0092",
            invoiceNumber = first.number,
            matchedAtIso = clock.nowIso(),
            deltaCents = 0L,
        )
        val result = repository.reconcile(duplicate, fromStatus = InvoiceStatus.DEPOSITED.name)

        assertTrue(result.isFailure, "L'index UNIQUE sur invoiceNumber doit refuser le doublon")
        assertEquals(1, repository.fetchMatches().getOrThrow().size)
        val untouched = SqlDelightInvoiceRepository(database, userEmail, clock)
            .fetchInvoices().getOrThrow().single { it.number == second.number }
        assertEquals(InvoiceStatus.DEPOSITED, untouched.status, "La seconde facture ne doit pas avoir bougé")
        assertEquals(
            trailBefore.size,
            SqlDelightAuditRepository(database).entriesFor("FAC-2026-0302").getOrThrow().size,
            "Aucune trace d'audit ne doit subsister d'une transaction annulée",
        )
    }

    @Test
    fun matches_areReadBackInChronologicalOrder() = runTest {
        val database = newDatabase()
        val repository = SqlDelightReconciliationRepository(database, clock)
        val first = seedDepositedInvoice(database)
        val second = seedDepositedInvoice(database, number = "FAC-2026-0302")
        repository.saveTransactions(listOf(transaction(), transaction("TX-2026-0092"))).getOrThrow()

        val useCase = ReconcilePaymentUseCase(repository, clock)
        useCase(transaction(), first).getOrThrow()
        clock.advanceBy(60)
        useCase(transaction("TX-2026-0092"), second).getOrThrow()

        assertEquals(
            listOf("TX-2026-0091", "TX-2026-0092"),
            repository.fetchMatches().getOrThrow().map { it.transactionId },
        )
    }

    /** Suppression d'une facture : le lettrage part avec elle, aucun orphelin ne subsiste. */
    @Test
    fun deletingAnInvoice_cascadesToItsMatch() = runTest {
        val database = newDatabase()
        val repository = SqlDelightReconciliationRepository(database, clock)
        val deposited = seedDepositedInvoice(database)
        repository.saveTransactions(listOf(transaction())).getOrThrow()
        ReconcilePaymentUseCase(repository, clock)(transaction(), deposited).getOrThrow()

        database.reconciliationMatchQueries.deleteByInvoiceNumber(deposited.number)

        assertTrue(repository.fetchMatches().getOrThrow().isEmpty())
    }
}
