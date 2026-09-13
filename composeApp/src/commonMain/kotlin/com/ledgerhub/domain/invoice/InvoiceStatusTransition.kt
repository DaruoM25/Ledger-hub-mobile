package com.ledgerhub.domain.invoice

import com.ledgerhub.domain.invoice.InvoiceStatus.APPROVED
import com.ledgerhub.domain.invoice.InvoiceStatus.CANCELLED
import com.ledgerhub.domain.invoice.InvoiceStatus.DEPOSITED
import com.ledgerhub.domain.invoice.InvoiceStatus.DRAFT
import com.ledgerhub.domain.invoice.InvoiceStatus.PAID
import com.ledgerhub.domain.invoice.InvoiceStatus.PENDING_REGULARIZATION
import com.ledgerhub.domain.invoice.InvoiceStatus.REFUSED
import com.ledgerhub.domain.invoice.InvoiceStatus.REJECTED

/**
 * Machine d'états du cycle de vie réglementaire DGFIP 2026.
 *
 * La table est **déclarative et exhaustive** : chaque statut y figure, y compris les terminaux
 * avec un ensemble vide. Une cascade de `if` laisserait des chemins implicites — ici, ce qui
 * n'est pas écrit est interdit, et le test parcourt les 49 cases.
 *
 * Deux règles méritent d'être explicitées :
 * - **[REJECTED] → [DRAFT]** est le seul retour en arrière. Un rejet de plateforme signifie que
 *   la facture n'est jamais entrée dans le circuit légal ; la corriger et la redéposer est la
 *   procédure attendue. À l'inverse [REFUSED], qui a circulé, ne se corrige que par un avoir.
 * - **[APPROVED]** est l'issue favorable du dépôt, exclusive de [REJECTED]. Elle ne réintroduit
 *   aucun retour en arrière : une facture approuvée a circulé, seul un avoir la corrige.
 * - **[PENDING_REGULARIZATION]** (US-29) : émise en mode dégradé lors d'une indisponibilité PPF.
 *   Elle transite vers [DEPOSITED] lors de la télétransmission groupée ou [CANCELLED] par avoir.
 * - **[CANCELLED]** n'est jamais atteint par une action d'interface : seule l'émission d'un avoir
 *   y conduit, dans la transaction atomique de
 *   [SqlDelightCreditNoteRepository][com.ledgerhub.data.creditnote.SqlDelightCreditNoteRepository].
 *   Il figure néanmoins dans la table, car la transition doit être validée comme les autres.
 */
object InvoiceStatusTransition {

    private val TRANSITIONS: Map<InvoiceStatus, Set<InvoiceStatus>> = mapOf(
        DRAFT to setOf(DEPOSITED, PENDING_REGULARIZATION),
        PENDING_REGULARIZATION to setOf(DEPOSITED, CANCELLED),
        DEPOSITED to setOf(APPROVED, PAID, REJECTED, REFUSED, CANCELLED),
        APPROVED to setOf(PAID, REFUSED, CANCELLED),
        PAID to setOf(CANCELLED),
        REJECTED to setOf(DRAFT),
        REFUSED to setOf(CANCELLED),
        CANCELLED to emptySet(),
    )

    /**
     * Statuts atteignables depuis [from]. Les transitions déclenchées par l'émission d'un avoir
     * ([CANCELLED]) en font partie : le filtrage de ce qui est proposé à l'écran relève de la
     * présentation, pas de la règle métier.
     */
    fun allowedFrom(from: InvoiceStatus): Set<InvoiceStatus> = TRANSITIONS.getValue(from)

    /** `true` si le passage de [from] à [to] est autorisé. Une transition vers soi-même ne l'est pas. */
    fun isAllowed(from: InvoiceStatus, to: InvoiceStatus): Boolean = to in allowedFrom(from)

    /**
     * Transitions proposables à l'utilisateur depuis [from] : les autorisées, moins [CANCELLED]
     * qui n'appartient qu'au parcours d'avoir (arbitrage PO de l'US-07).
     */
    fun userActionableFrom(from: InvoiceStatus): Set<InvoiceStatus> = allowedFrom(from) - CANCELLED

    /**
     * Valide la transition ou explique le refus.
     * Le message nomme les deux statuts : un rejet muet serait indébogable côté support.
     */
    fun validate(from: InvoiceStatus, to: InvoiceStatus): ValidationResult =
        if (isAllowed(from, to)) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid("Transition interdite : une facture $from ne peut pas passer à $to")
        }
}

/** Transition refusée par la machine d'états — jamais atteinte si l'appelant valide en amont. */
class InvalidStatusTransitionException(
    val from: InvoiceStatus,
    val to: InvoiceStatus,
) : Exception("Transition interdite : une facture $from ne peut pas passer à $to")

/** Alias de conformité légale US-28 pour les transitions d'état non autorisées. */
typealias IllegalInvoiceTransitionException = InvalidStatusTransitionException
