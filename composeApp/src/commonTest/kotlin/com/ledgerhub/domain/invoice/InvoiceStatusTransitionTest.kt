package com.ledgerhub.domain.invoice

import com.ledgerhub.domain.invoice.InvoiceStatus.APPROVED
import com.ledgerhub.domain.invoice.InvoiceStatus.CANCELLED
import com.ledgerhub.domain.invoice.InvoiceStatus.DEPOSITED
import com.ledgerhub.domain.invoice.InvoiceStatus.DRAFT
import com.ledgerhub.domain.invoice.InvoiceStatus.PAID
import com.ledgerhub.domain.invoice.InvoiceStatus.PENDING_REGULARIZATION
import com.ledgerhub.domain.invoice.InvoiceStatus.REFUSED
import com.ledgerhub.domain.invoice.InvoiceStatus.REJECTED
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Machine d'états DGFIP 2026 — **matrice exhaustive 8 × 8**, pas un échantillon.
 *
 * Les 64 cases sont énumérées explicitement : un statut ajouté au référentiel sans mise à jour
 * de la table ferait échouer [everyPairIsCovered], et non passer un test silencieusement.
 */
class InvoiceStatusTransitionTest {

    /**
     * Vérité de référence, écrite à la main d'après l'arbitrage produit — délibérément **pas**
     * dérivée de l'implémentation, sans quoi le test ne vérifierait que sa propre copie.
     */
    private val expected: Map<Pair<InvoiceStatus, InvoiceStatus>, Boolean> = mapOf(
        // Depuis DRAFT : dépôt nominal ou mode dégradé.
        (DRAFT to DRAFT) to false,
        (DRAFT to PENDING_REGULARIZATION) to true,
        (DRAFT to DEPOSITED) to true,
        (DRAFT to APPROVED) to false,
        (DRAFT to PAID) to false,
        (DRAFT to REJECTED) to false,
        (DRAFT to REFUSED) to false,
        (DRAFT to CANCELLED) to false,

        // Depuis PENDING_REGULARIZATION (US-29) : régularisation télétransmise ou annulation par avoir.
        (PENDING_REGULARIZATION to DRAFT) to false,
        (PENDING_REGULARIZATION to PENDING_REGULARIZATION) to false,
        (PENDING_REGULARIZATION to DEPOSITED) to true,
        (PENDING_REGULARIZATION to APPROVED) to false,
        (PENDING_REGULARIZATION to PAID) to false,
        (PENDING_REGULARIZATION to REJECTED) to false,
        (PENDING_REGULARIZATION to REFUSED) to false,
        (PENDING_REGULARIZATION to CANCELLED) to true,

        // Depuis DEPOSITED : les cinq issues d'une facture déposée, approbation PPF comprise.
        (DEPOSITED to DRAFT) to false,
        (DEPOSITED to PENDING_REGULARIZATION) to false,
        (DEPOSITED to DEPOSITED) to false,
        (DEPOSITED to APPROVED) to true,
        (DEPOSITED to PAID) to true,
        (DEPOSITED to REJECTED) to true,
        (DEPOSITED to REFUSED) to true,
        (DEPOSITED to CANCELLED) to true,

        // Depuis APPROVED : validée par l'administration, elle a circulé — plus de rejet possible.
        (APPROVED to DRAFT) to false,
        (APPROVED to PENDING_REGULARIZATION) to false,
        (APPROVED to DEPOSITED) to false,
        (APPROVED to APPROVED) to false,
        (APPROVED to PAID) to true,
        (APPROVED to REJECTED) to false,
        (APPROVED to REFUSED) to true,
        (APPROVED to CANCELLED) to true,

        // Depuis PAID : encaissée, seul l'avoir peut encore intervenir.
        (PAID to DRAFT) to false,
        (PAID to PENDING_REGULARIZATION) to false,
        (PAID to DEPOSITED) to false,
        (PAID to APPROVED) to false,
        (PAID to PAID) to false,
        (PAID to REJECTED) to false,
        (PAID to REFUSED) to false,
        (PAID to CANCELLED) to true,

        // Depuis REJECTED : jamais entrée dans le circuit légal, la correction est la procédure.
        (REJECTED to DRAFT) to true,
        (REJECTED to PENDING_REGULARIZATION) to false,
        (REJECTED to DEPOSITED) to false,
        (REJECTED to APPROVED) to false,
        (REJECTED to PAID) to false,
        (REJECTED to REJECTED) to false,
        (REJECTED to REFUSED) to false,
        (REJECTED to CANCELLED) to false,

        // Depuis REFUSED : la facture a circulé, seul un avoir la corrige.
        (REFUSED to DRAFT) to false,
        (REFUSED to PENDING_REGULARIZATION) to false,
        (REFUSED to DEPOSITED) to false,
        (REFUSED to APPROVED) to false,
        (REFUSED to PAID) to false,
        (REFUSED to REJECTED) to false,
        (REFUSED to REFUSED) to false,
        (REFUSED to CANCELLED) to true,

        // Depuis CANCELLED : terminal.
        (CANCELLED to DRAFT) to false,
        (CANCELLED to PENDING_REGULARIZATION) to false,
        (CANCELLED to DEPOSITED) to false,
        (CANCELLED to APPROVED) to false,
        (CANCELLED to PAID) to false,
        (CANCELLED to REJECTED) to false,
        (CANCELLED to REFUSED) to false,
        (CANCELLED to CANCELLED) to false,
    )

    @Test
    fun everyPairIsCovered() {
        // Garde-fou : un neuvième statut rendrait la matrice incomplète et le signalerait ici.
        val statuses = InvoiceStatus.entries
        assertEquals(8, statuses.size, "Le référentiel compte huit statuts avec le mode dégradé")
        assertEquals(64, expected.size, "La matrice doit énumérer les 64 combinaisons")
        statuses.forEach { from ->
            statuses.forEach { to ->
                assertTrue(
                    expected.containsKey(from to to),
                    "Combinaison non couverte par la matrice de référence : $from -> $to",
                )
            }
        }
    }

    @Test
    fun theWholeMatrixMatchesTheStateMachine() {
        expected.forEach { (pair, allowed) ->
            val (from, to) = pair
            assertEquals(
                allowed,
                InvoiceStatusTransition.isAllowed(from, to),
                "Transition $from -> $to : attendu ${if (allowed) "autorisée" else "interdite"}",
            )
        }
    }

    // ── Lectures dérivées ────────────────────────────────────────────────────────────────────

    @Test
    fun allowedFrom_listsExactlyTheAuthorisedTargets() {
        assertEquals(setOf(DEPOSITED, PENDING_REGULARIZATION), InvoiceStatusTransition.allowedFrom(DRAFT))
        assertEquals(setOf(DEPOSITED, CANCELLED), InvoiceStatusTransition.allowedFrom(PENDING_REGULARIZATION))
        assertEquals(
            setOf(APPROVED, PAID, REJECTED, REFUSED, CANCELLED),
            InvoiceStatusTransition.allowedFrom(DEPOSITED),
        )
        assertEquals(setOf(PAID, REFUSED, CANCELLED), InvoiceStatusTransition.allowedFrom(APPROVED))
        assertEquals(setOf(CANCELLED), InvoiceStatusTransition.allowedFrom(PAID))
        assertEquals(setOf(DRAFT), InvoiceStatusTransition.allowedFrom(REJECTED))
        assertEquals(setOf(CANCELLED), InvoiceStatusTransition.allowedFrom(REFUSED))
        assertEquals(emptySet(), InvoiceStatusTransition.allowedFrom(CANCELLED))
    }

    /**
     * Parcours nominal de la réforme PPF : dépôt sur le portail, approbation par
     * l'administration, puis encaissement. Le rejet est exclusif de ce chemin.
     */
    @Test
    fun ppfHappyPath_depositThenApprovalThenPayment_isWalkable() {
        assertTrue(InvoiceStatusTransition.isAllowed(DRAFT, DEPOSITED))
        assertTrue(InvoiceStatusTransition.isAllowed(DEPOSITED, APPROVED))
        assertTrue(InvoiceStatusTransition.isAllowed(APPROVED, PAID))

        // Une facture approuvée est entrée dans le circuit légal : la plateforme ne peut plus
        // la rejeter, et elle ne repasse pas en brouillon.
        assertFalse(InvoiceStatusTransition.isAllowed(APPROVED, REJECTED))
        assertFalse(InvoiceStatusTransition.isAllowed(APPROVED, DRAFT))
    }

    @Test
    fun cancelledIsTheOnlyTerminalStatus() {
        assertTrue(CANCELLED.isTerminal)
        InvoiceStatus.entries.filter { it != CANCELLED }.forEach {
            assertFalse(it.isTerminal, "$it ne doit pas être terminal")
        }
    }

    @Test
    fun noStatusCanTransitionToItself() {
        InvoiceStatus.entries.forEach {
            assertFalse(InvoiceStatusTransition.isAllowed(it, it), "$it -> $it doit être interdit")
        }
    }

    @Test
    fun userActionableTransitions_neverOfferCancellation() {
        // CANCELLED n'appartient qu'au parcours d'avoir (arbitrage PO) : aucun bouton direct.
        InvoiceStatus.entries.forEach { from ->
            assertFalse(
                CANCELLED in InvoiceStatusTransition.userActionableFrom(from),
                "Aucune action utilisateur ne doit mener à CANCELLED depuis $from",
            )
        }
        assertEquals(
            setOf(APPROVED, PAID, REJECTED, REFUSED),
            InvoiceStatusTransition.userActionableFrom(DEPOSITED),
        )
        assertEquals(setOf(PAID, REFUSED), InvoiceStatusTransition.userActionableFrom(APPROVED))
        assertEquals(emptySet(), InvoiceStatusTransition.userActionableFrom(PAID))
    }

    @Test
    fun validate_explainsTheRefusalByNamingBothStatuses() {
        assertIs<ValidationResult.Valid>(InvoiceStatusTransition.validate(DRAFT, DEPOSITED))

        val refusal = assertIs<ValidationResult.Invalid>(InvoiceStatusTransition.validate(PAID, DRAFT))
        assertTrue(refusal.reason.contains("PAID"), refusal.reason)
        assertTrue(refusal.reason.contains("DRAFT"), refusal.reason)
    }

    // ── Règles fiscales dérivées de la machine ───────────────────────────────────────────────

    @Test
    fun cancellableByCreditNote_matchesTheTransitionTable() {
        // La propriété du domaine ne doit pas réénumérer les statuts : elle dérive de la table.
        InvoiceStatus.entries.forEach { status ->
            assertEquals(
                InvoiceStatusTransition.isAllowed(status, CANCELLED),
                status in setOf(DEPOSITED, APPROVED, PAID, REFUSED, PENDING_REGULARIZATION),
                "Cohérence avoir/machine d'états pour $status",
            )
        }
    }
}
