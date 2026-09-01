package com.ledgerhub.domain.audit

import com.ledgerhub.domain.i18n.AppLanguage
import com.ledgerhub.domain.i18n.AppTranslations
import com.ledgerhub.domain.i18n.StringKey
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Niveau 1 (US-17) — résolution de la timeline de traçabilité et empreinte d'intégrité.
 *
 * Toute la logique de l'US-17 est une fonction pure ([buildAuditTimeline]) : elle se teste sans
 * composition, sans base et sans émulateur. C'est ce qui rend ce niveau substantiel plutôt que
 * cosmétique — les niveaux 3 n'auront plus qu'à vérifier le rendu.
 */
class AuditTimelineTest {

    private val issuer = Party("Cabinet LedgerHub", "820329331", "82032933100027")
    private val recipient = Party("Boulangerie Moreau SARL", "784102336", "78410233600021")

    private fun invoice(
        number: String = "FAC-2026-0217",
        status: InvoiceStatus = InvoiceStatus.DRAFT,
        issueDate: String = "2026-08-31",
    ) = Invoice(
        number = number,
        issueDate = issueDate,
        issuer = issuer,
        recipient = recipient,
        lines = listOf(InvoiceLine("Conseil", 2, Money(10_000), VatRate.TAUX_NORMAL)),
        status = status,
    )

    private fun entry(
        from: InvoiceStatus?,
        to: InvoiceStatus,
        at: String,
        id: String = "audit-$to-$at",
    ) = AuditEntry(
        id = id,
        invoiceNumber = "FAC-2026-0217",
        fromStatus = from,
        toStatus = to,
        reason = null,
        createdAt = at,
    )

    private fun List<AuditMilestone>.state(id: AuditMilestoneId) = single { it.id == id }.state
    private fun List<AuditMilestone>.milestone(id: AuditMilestoneId) = single { it.id == id }

    // ── Structure ────────────────────────────────────────────────────────────

    @Test
    fun theTimeline_alwaysExposesTheFourRegulatoryMilestones_inOrder() {
        val timeline = buildAuditTimeline(invoice())

        assertEquals(
            listOf(
                AuditMilestoneId.CREATED,
                AuditMilestoneId.SEALED,
                AuditMilestoneId.PPF,
                AuditMilestoneId.STATUS,
            ),
            timeline.map { it.id },
        )
    }

    // ── Résolution des états par statut ─────────────────────────────────────

    @Test
    fun draftInvoice_isCreatedAndSealed_butNeitherSubmittedNorReported() {
        val timeline = buildAuditTimeline(invoice(status = InvoiceStatus.DRAFT))

        assertEquals(AuditMilestoneState.DONE, timeline.state(AuditMilestoneId.CREATED))
        assertEquals(AuditMilestoneState.DONE, timeline.state(AuditMilestoneId.SEALED))
        assertEquals(AuditMilestoneState.PENDING, timeline.state(AuditMilestoneId.PPF))
        assertEquals(AuditMilestoneState.PENDING, timeline.state(AuditMilestoneId.STATUS))
        // Rien à répercuter tant que le portail ne s'est pas prononcé.
        assertNull(timeline.milestone(AuditMilestoneId.STATUS).reportedStatus)
    }

    @Test
    fun depositedInvoice_reachedThePpf_butAwaitsTheAdministration() {
        val timeline = buildAuditTimeline(
            invoice(status = InvoiceStatus.DEPOSITED),
            listOf(entry(InvoiceStatus.DRAFT, InvoiceStatus.DEPOSITED, "2026-08-31T09:00:00Z")),
        )

        assertEquals(AuditMilestoneState.DONE, timeline.state(AuditMilestoneId.PPF))
        assertEquals(AuditMilestoneState.PENDING, timeline.state(AuditMilestoneId.STATUS))
        assertNull(timeline.milestone(AuditMilestoneId.STATUS).reportedStatus)
    }

    @Test
    fun approvedInvoice_completesEveryMilestone() {
        val timeline = buildAuditTimeline(
            invoice(status = InvoiceStatus.APPROVED),
            listOf(
                entry(InvoiceStatus.DRAFT, InvoiceStatus.DEPOSITED, "2026-08-31T09:00:00Z"),
                entry(InvoiceStatus.DEPOSITED, InvoiceStatus.APPROVED, "2026-09-01T10:30:00Z"),
            ),
        )

        assertTrue(timeline.all { it.state == AuditMilestoneState.DONE })
        assertEquals(InvoiceStatus.APPROVED, timeline.milestone(AuditMilestoneId.STATUS).reportedStatus)
    }

    @Test
    fun rejectedInvoice_marksTheReportedStatusAsRejected() {
        val timeline = buildAuditTimeline(
            invoice(status = InvoiceStatus.REJECTED),
            listOf(
                entry(InvoiceStatus.DRAFT, InvoiceStatus.DEPOSITED, "2026-08-31T09:00:00Z"),
                entry(InvoiceStatus.DEPOSITED, InvoiceStatus.REJECTED, "2026-09-01T11:00:00Z"),
            ),
        )

        assertEquals(AuditMilestoneState.REJECTED, timeline.state(AuditMilestoneId.STATUS))
        assertEquals(InvoiceStatus.REJECTED, timeline.milestone(AuditMilestoneId.STATUS).reportedStatus)
        // Le rejet ne défait pas la transmission : la facture a bien été déposée.
        assertEquals(AuditMilestoneState.DONE, timeline.state(AuditMilestoneId.PPF))
    }

    /**
     * Le refus acheteur porte sur une facture qui a circulé, le rejet plateforme sur une facture
     * jamais entrée dans le circuit — deux causes distinctes, une même lecture pour l'utilisateur :
     * l'étape s'est mal terminée.
     */
    @Test
    fun refusedInvoice_alsoReadsAsAnUnfavourableOutcome() {
        val timeline = buildAuditTimeline(invoice(status = InvoiceStatus.REFUSED))

        assertEquals(AuditMilestoneState.REJECTED, timeline.state(AuditMilestoneId.STATUS))
        assertEquals(InvoiceStatus.REFUSED, timeline.milestone(AuditMilestoneId.STATUS).reportedStatus)
    }

    /** L'annulation par avoir est un aboutissement comptable, pas un incident. */
    @Test
    fun cancelledInvoice_readsAsCompleted_notAsAnIncident() {
        val timeline = buildAuditTimeline(invoice(status = InvoiceStatus.CANCELLED))

        assertEquals(AuditMilestoneState.DONE, timeline.state(AuditMilestoneId.STATUS))
        assertEquals(InvoiceStatus.CANCELLED, timeline.milestone(AuditMilestoneId.STATUS).reportedStatus)
    }

    /**
     * **L'invariant central de l'US-17.** Une facture rejetée peut légalement redevenir brouillon
     * pour correction et redépôt. Son jalon PPF doit rester franchi : elle *a* été transmise, et
     * l'effacer reviendrait à réécrire l'historique — ce qu'une piste d'audit interdit.
     *
     * C'est exactement ce qu'un `when (invoice.status)` naïf casserait.
     */
    @Test
    fun invoiceReturnedToDraftAfterRejection_keepsItsSubmissionMilestone() {
        val timeline = buildAuditTimeline(
            invoice(status = InvoiceStatus.DRAFT),
            listOf(
                entry(InvoiceStatus.DRAFT, InvoiceStatus.DEPOSITED, "2026-08-31T09:00:00Z"),
                entry(InvoiceStatus.DEPOSITED, InvoiceStatus.REJECTED, "2026-09-01T11:00:00Z"),
                entry(InvoiceStatus.REJECTED, InvoiceStatus.DRAFT, "2026-09-02T08:15:00Z"),
            ),
        )

        assertEquals(AuditMilestoneState.DONE, timeline.state(AuditMilestoneId.PPF))
        // Le statut courant est bien redevenu brouillon : l'administration n'a plus rien répercuté.
        assertEquals(AuditMilestoneState.PENDING, timeline.state(AuditMilestoneId.STATUS))
    }

    // ── Horodatages : la PAF fait foi ───────────────────────────────────────

    @Test
    fun timestamps_comeFromTheRealAuditTrail() {
        val timeline = buildAuditTimeline(
            invoice(status = InvoiceStatus.APPROVED, issueDate = "2026-08-31"),
            listOf(
                entry(null, InvoiceStatus.DRAFT, "2026-08-30T07:45:00Z"),
                entry(InvoiceStatus.DRAFT, InvoiceStatus.DEPOSITED, "2026-08-31T09:00:00Z"),
                entry(InvoiceStatus.DEPOSITED, InvoiceStatus.APPROVED, "2026-09-01T10:30:00Z"),
            ),
        )

        assertEquals("2026-08-30T07:45:00Z", timeline.milestone(AuditMilestoneId.CREATED).timestampIso)
        assertEquals("2026-08-30T07:45:00Z", timeline.milestone(AuditMilestoneId.SEALED).timestampIso)
        assertEquals("2026-08-31T09:00:00Z", timeline.milestone(AuditMilestoneId.PPF).timestampIso)
        assertEquals("2026-09-01T10:30:00Z", timeline.milestone(AuditMilestoneId.STATUS).timestampIso)
    }

    /** Parc antérieur à la PAF (US-07) : aucune trace, la date d'émission prend le relais. */
    @Test
    fun withoutAnyAuditEntry_theIssueDateStandsIn() {
        val timeline = buildAuditTimeline(invoice(issueDate = "2026-08-31"))

        assertEquals("2026-08-31", timeline.milestone(AuditMilestoneId.CREATED).timestampIso)
        assertEquals("2026-08-31", timeline.milestone(AuditMilestoneId.SEALED).timestampIso)
    }

    /**
     * Facture héritée déposée sans trace de dépôt : l'étape est franchie mais non datée. Mieux
     * vaut une date absente qu'une date inventée dans un panneau d'horodatage réglementaire.
     */
    @Test
    fun legacyDepositedInvoice_hasAReachedButUndatedSubmission() {
        val timeline = buildAuditTimeline(invoice(status = InvoiceStatus.DEPOSITED))

        assertEquals(AuditMilestoneState.DONE, timeline.state(AuditMilestoneId.PPF))
        assertNull(timeline.milestone(AuditMilestoneId.PPF).timestampIso)
    }

    @Test
    fun pendingMilestones_carryNoTimestamp() {
        val timeline = buildAuditTimeline(invoice(status = InvoiceStatus.DRAFT))

        assertNull(timeline.milestone(AuditMilestoneId.PPF).timestampIso)
        assertNull(timeline.milestone(AuditMilestoneId.STATUS).timestampIso)
    }

    @Test
    fun anInvoiceWithoutIssueDate_yieldsNoTimestampRatherThanAnEmptyString() {
        val timeline = buildAuditTimeline(invoice(issueDate = ""))

        assertNull(timeline.milestone(AuditMilestoneId.CREATED).timestampIso)
    }

    // ── Empreinte d'intégrité ───────────────────────────────────────────────

    @Test
    fun onlyTheSealedMilestone_carriesTheFingerprint() {
        val timeline = buildAuditTimeline(invoice())

        assertNotNull(timeline.milestone(AuditMilestoneId.SEALED).fingerprint)
        listOf(AuditMilestoneId.CREATED, AuditMilestoneId.PPF, AuditMilestoneId.STATUS).forEach {
            assertNull(timeline.milestone(it).fingerprint)
        }
    }

    @Test
    fun theFingerprint_isDeterministic_andSixtyFourHexCharacters() {
        val first = invoice().fingerprintSha256()
        val second = invoice().fingerprintSha256()

        assertEquals(first, second)
        assertEquals(64, first.length)
        assertTrue(first.all { it in "0123456789abcdef" }, "Empreinte non hexadécimale : $first")
    }

    @Test
    fun twoDifferentInvoices_yieldDifferentFingerprints() {
        assertNotEquals(
            invoice(number = "FAC-2026-0001").fingerprintSha256(),
            invoice(number = "FAC-2026-0002").fingerprintSha256(),
        )
    }

    /** Le statut fait partie de la pièce : une facture déposée n'est plus la même qu'un brouillon. */
    @Test
    fun changingTheStatus_changesTheFingerprint() {
        assertNotEquals(
            invoice(status = InvoiceStatus.DRAFT).fingerprintSha256(),
            invoice(status = InvoiceStatus.DEPOSITED).fingerprintSha256(),
        )
    }

    @Test
    fun theCanonicalSource_followsTheAgreedFormat() {
        assertEquals(
            "FAC-2026-0217|2026-08-31|82032933100027|78410233600021|24000|DRAFT",
            invoice().canonicalFingerprintSource(),
        )
    }

    /**
     * Vecteurs officiels du NIST (FIPS 180-4). Ils verrouillent l'implémentation Kotlin pure —
     * sans eux, une erreur d'un bit produirait des empreintes stables mais fausses, donc
     * inutilisables comme preuve d'intégrité et indétectables par les autres tests.
     */
    @Test
    fun sha256_matchesTheOfficialNistVectors() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            sha256Hex(""),
        )
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256Hex("abc"),
        )
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            sha256Hex("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"),
        )
    }

    /**
     * Message de 112 octets : il s'etend sur deux blocs et force le bourrage a en ouvrir un
     * troisieme. C'est le cas limite ou se logent les erreurs d'implementation — le vecteur NIST
     * de 56 octets ci-dessus, lui, tient sur un seul bloc.
     */
    @Test
    fun sha256_handlesMultiBlockMessagesAndThePaddingBoundary() {
        val twoBlockMessage = "abcdefghbcdefghicdefghijdefghijkefghijklfghijklmghijklmnhijklmno" +
            "ijklmnopjklmnopqklmnopqrlmnopqrsmnopqrstnopqrstu"

        assertEquals(112, twoBlockMessage.length)
        assertEquals(
            "cf5b16a778af8380036ce59e7b0492370b249b11e8f07a51afac45037afee9d1",
            sha256Hex(twoBlockMessage),
        )
    }

    /** Accents et symboles : l'encodage UTF-8 explicite garantit la même empreinte partout. */
    @Test
    fun sha256_encodesNonAsciiConsistently() {
        val accented = sha256Hex("Boulangerie Moreau — 40 €")

        assertEquals(64, accented.length)
        assertEquals(accented, sha256Hex("Boulangerie Moreau — 40 €"))
        assertNotEquals(accented, sha256Hex("Boulangerie Moreau - 40 EUR"))
    }

    @Test
    fun theFingerprint_isAbbreviatedForDisplay_withoutLosingTheFullValue() {
        val full = invoice().fingerprintSha256()

        assertEquals(full.take(16) + "…", full.abbreviateFingerprint())
        assertEquals("abc", "abc".abbreviateFingerprint())
    }

    // ── Internationalisation ────────────────────────────────────────────────

    @Test
    fun everyUs17Key_isTranslatedAndDistinct_inBothLanguages() {
        val keys = listOf(
            StringKey.AUDIT_PANEL_TITLE,
            StringKey.AUDIT_STEP_CREATED,
            StringKey.AUDIT_STEP_SEALED,
            StringKey.AUDIT_STEP_PPF,
            StringKey.AUDIT_STEP_STATUS,
            StringKey.AUDIT_SHA256_LABEL,
            StringKey.AUDIT_STEP_PENDING,
            StringKey.AUDIT_STEP_DONE,
        )

        keys.forEach { key ->
            AppLanguage.entries.forEach { language ->
                assertTrue(
                    AppTranslations.get(key, language).isNotBlank(),
                    "Traduction manquante ou vide pour $key / $language",
                )
            }
        }

        // Les quatre jalons doivent rester distinguables dans chaque langue.
        AppLanguage.entries.forEach { language ->
            val steps = listOf(
                StringKey.AUDIT_STEP_CREATED,
                StringKey.AUDIT_STEP_SEALED,
                StringKey.AUDIT_STEP_PPF,
                StringKey.AUDIT_STEP_STATUS,
            ).map { AppTranslations.get(it, language) }
            assertEquals(steps.size, steps.toSet().size, "Libellés de jalon dupliqués en $language")
        }
    }
}
