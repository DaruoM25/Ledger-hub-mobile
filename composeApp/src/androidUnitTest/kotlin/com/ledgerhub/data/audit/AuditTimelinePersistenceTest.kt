package com.ledgerhub.data.audit

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.ledgerhub.data.invoice.SqlDelightInvoiceRepository
import com.ledgerhub.db.LedgerHubDatabase
import com.ledgerhub.domain.audit.AuditMilestoneId
import com.ledgerhub.domain.audit.AuditMilestoneState
import com.ledgerhub.domain.audit.buildAuditTimeline
import com.ledgerhub.domain.audit.fingerprintSha256
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.domain.time.FixedClock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Niveau 2 (US-17) — la timeline se construit sur des horodatages **réellement persistés**.
 *
 * Le niveau 1 vérifie la logique de résolution sur des entrées fabriquées ; celui-ci ferme la
 * boucle : transitions écrites par le vrai chemin (`changeStatus`, qui inscrit statut et trace
 * dans une seule transaction), relues par `SqlDelightAuditRepository`, puis passées à
 * `buildAuditTimeline`. C'est la preuve que le panneau « Horodatage réglementaire » affiche la
 * Piste d'Audit Fiable et non une reconstitution.
 *
 * [FixedClock] rend les horodatages déterministes — une PAF ne se teste pas contre l'heure réelle.
 * Pilote JdbcSqliteDriver en mémoire, réservé à androidUnitTest/JVM.
 */
class AuditTimelinePersistenceTest {

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

    private fun invoice(number: String = "FAC-2026-0217") = Invoice(
        number = number,
        issueDate = "2026-08-31",
        issuer = issuer,
        recipient = recipient,
        lines = listOf(InvoiceLine("Conseil", 2, Money(10_000), VatRate.TAUX_NORMAL)),
        status = InvoiceStatus.DRAFT,
        dueDate = "2026-09-30",
    )

    // ── Horodatages réels ───────────────────────────────────────────────────

    @Test
    fun aFullLifecycle_yieldsATimelineDatedFromThePersistedAuditTrail() = runTest {
        val database = newDatabase()
        val clock = FixedClock("2026-08-31T09:00:00Z")
        val invoices = SqlDelightInvoiceRepository(database, userEmail, clock)
        val audit = SqlDelightAuditRepository(database)

        invoices.submitInvoice(invoice()).getOrThrow()
        invoices.changeStatus("FAC-2026-0217", InvoiceStatus.DRAFT, InvoiceStatus.DEPOSITED, null).getOrThrow()
        clock.advanceBy(seconds = 3_600)
        invoices.changeStatus("FAC-2026-0217", InvoiceStatus.DEPOSITED, InvoiceStatus.APPROVED, null).getOrThrow()

        val persisted = invoices.fetchInvoices().getOrThrow().single()
        val entries = audit.entriesFor("FAC-2026-0217").getOrThrow()
        val timeline = buildAuditTimeline(persisted, entries)

        assertEquals(InvoiceStatus.APPROVED, persisted.status)
        assertTrue(timeline.all { it.state == AuditMilestoneState.DONE })

        // Les dates viennent de la base, pas d'un calcul d'affichage.
        assertEquals(
            "2026-08-31T09:00:00Z",
            timeline.single { it.id == AuditMilestoneId.PPF }.timestampIso,
        )
        assertEquals(
            "2026-08-31T10:00:00Z",
            timeline.single { it.id == AuditMilestoneId.STATUS }.timestampIso,
        )
        assertEquals(
            InvoiceStatus.APPROVED,
            timeline.single { it.id == AuditMilestoneId.STATUS }.reportedStatus,
        )
    }

    /** Le rejet se relit comme une issue défavorable, sans effacer la transmission déjà tracée. */
    @Test
    fun aRejectedLifecycle_readsBackAsAnUnfavourableOutcome() = runTest {
        val database = newDatabase()
        val clock = FixedClock("2026-08-31T09:00:00Z")
        val invoices = SqlDelightInvoiceRepository(database, userEmail, clock)
        val audit = SqlDelightAuditRepository(database)

        invoices.submitInvoice(invoice()).getOrThrow()
        invoices.changeStatus("FAC-2026-0217", InvoiceStatus.DRAFT, InvoiceStatus.DEPOSITED, null).getOrThrow()
        clock.advanceBy(seconds = 7_200)
        invoices.changeStatus(
            "FAC-2026-0217",
            InvoiceStatus.DEPOSITED,
            InvoiceStatus.REJECTED,
            reason = "SIRET destinataire inconnu du référentiel",
        ).getOrThrow()

        val persisted = invoices.fetchInvoices().getOrThrow().single()
        val timeline = buildAuditTimeline(persisted, audit.entriesFor("FAC-2026-0217").getOrThrow())

        assertEquals(
            AuditMilestoneState.DONE,
            timeline.single { it.id == AuditMilestoneId.PPF }.state,
        )
        assertEquals(
            AuditMilestoneState.REJECTED,
            timeline.single { it.id == AuditMilestoneId.STATUS }.state,
        )
    }

    /**
     * Retour en brouillon après rejet — parcours légal de correction. Le jalon de transmission
     * doit survivre à l'aller-retour en base : l'invariant central de l'US-17, vérifié cette fois
     * sur des traces réellement écrites.
     */
    @Test
    fun returningToDraftAfterRejection_keepsTheSubmissionMilestoneInThePersistedTrail() = runTest {
        val database = newDatabase()
        val clock = FixedClock("2026-08-31T09:00:00Z")
        val invoices = SqlDelightInvoiceRepository(database, userEmail, clock)
        val audit = SqlDelightAuditRepository(database)

        invoices.submitInvoice(invoice()).getOrThrow()
        invoices.changeStatus("FAC-2026-0217", InvoiceStatus.DRAFT, InvoiceStatus.DEPOSITED, null).getOrThrow()
        clock.advanceBy(seconds = 3_600)
        invoices.changeStatus("FAC-2026-0217", InvoiceStatus.DEPOSITED, InvoiceStatus.REJECTED, "motif").getOrThrow()
        clock.advanceBy(seconds = 3_600)
        invoices.changeStatus("FAC-2026-0217", InvoiceStatus.REJECTED, InvoiceStatus.DRAFT, null).getOrThrow()

        val persisted = invoices.fetchInvoices().getOrThrow().single()
        val timeline = buildAuditTimeline(persisted, audit.entriesFor("FAC-2026-0217").getOrThrow())

        assertEquals(InvoiceStatus.DRAFT, persisted.status)
        assertEquals(
            AuditMilestoneState.DONE,
            timeline.single { it.id == AuditMilestoneId.PPF }.state,
        )
        assertEquals(
            "2026-08-31T09:00:00Z",
            timeline.single { it.id == AuditMilestoneId.PPF }.timestampIso,
        )
    }

    /** Facture jamais transmise : la timeline le dit, sans inventer de date de dépôt. */
    @Test
    fun aDraftInvoice_hasNoSubmissionTimestampInTheDatabase() = runTest {
        val database = newDatabase()
        val invoices = SqlDelightInvoiceRepository(database, userEmail, FixedClock("2026-08-31T09:00:00Z"))
        val audit = SqlDelightAuditRepository(database)

        invoices.submitInvoice(invoice()).getOrThrow()

        val persisted = invoices.fetchInvoices().getOrThrow().single()
        val timeline = buildAuditTimeline(persisted, audit.entriesFor("FAC-2026-0217").getOrThrow())

        assertEquals(AuditMilestoneState.PENDING, timeline.single { it.id == AuditMilestoneId.PPF }.state)
        assertNull(timeline.single { it.id == AuditMilestoneId.PPF }.timestampIso)
        assertNull(timeline.single { it.id == AuditMilestoneId.STATUS }.reportedStatus)
    }

    /**
     * Parc antérieur à la PAF (US-07) : la facture existe, aucune trace ne l'accompagne. La
     * timeline doit rester exploitable — c'est le cas de repli sur la date d'émission.
     */
    @Test
    fun anInvoiceWithoutAnyAuditEntry_stillYieldsAUsableTimeline() = runTest {
        val database = newDatabase()
        val invoices = SqlDelightInvoiceRepository(database, userEmail, FixedClock("2026-08-31T09:00:00Z"))

        invoices.submitInvoice(invoice()).getOrThrow()
        val persisted = invoices.fetchInvoices().getOrThrow().single()

        // Entrées volontairement vides : on simule une facture antérieure à la piste d'audit.
        val timeline = buildAuditTimeline(persisted, entries = emptyList())

        assertEquals(4, timeline.size)
        assertEquals("2026-08-31", timeline.single { it.id == AuditMilestoneId.CREATED }.timestampIso)
    }

    // ── Empreinte d'intégrité ───────────────────────────────────────────────

    /**
     * L'empreinte est recalculée depuis la facture relue : si un champ engageant avait été altéré
     * par l'aller-retour SQLite, elle ne correspondrait plus — ce qui est précisément le service
     * qu'elle rend.
     */
    @Test
    fun theFingerprint_survivesTheSqliteRoundTrip() = runTest {
        val database = newDatabase()
        val invoices = SqlDelightInvoiceRepository(database, userEmail, FixedClock("2026-08-31T09:00:00Z"))
        val original = invoice()

        invoices.submitInvoice(original).getOrThrow()
        val persisted = invoices.fetchInvoices().getOrThrow().single()

        assertEquals(original.fingerprintSha256(), persisted.fingerprintSha256())
    }

    /** Un changement de statut change la pièce au sens du circuit légal, donc son empreinte. */
    @Test
    fun changingTheStatusInDatabase_changesTheFingerprint() = runTest {
        val database = newDatabase()
        val invoices = SqlDelightInvoiceRepository(database, userEmail, FixedClock("2026-08-31T09:00:00Z"))

        invoices.submitInvoice(invoice()).getOrThrow()
        val asDraft = invoices.fetchInvoices().getOrThrow().single().fingerprintSha256()

        invoices.changeStatus("FAC-2026-0217", InvoiceStatus.DRAFT, InvoiceStatus.DEPOSITED, null).getOrThrow()
        val asDeposited = invoices.fetchInvoices().getOrThrow().single().fingerprintSha256()

        assertNotEquals(asDraft, asDeposited)
    }

    /** Deux factures distinctes en base ne peuvent pas partager la même empreinte. */
    @Test
    fun twoPersistedInvoices_haveDistinctFingerprints() = runTest {
        val database = newDatabase()
        val invoices = SqlDelightInvoiceRepository(database, userEmail, FixedClock("2026-08-31T09:00:00Z"))

        invoices.submitInvoice(invoice(number = "FAC-2026-0001")).getOrThrow()
        invoices.submitInvoice(invoice(number = "FAC-2026-0002")).getOrThrow()

        val fingerprints = invoices.fetchInvoices().getOrThrow().map { it.fingerprintSha256() }
        assertEquals(2, fingerprints.size)
        assertEquals(2, fingerprints.toSet().size)
    }
}
