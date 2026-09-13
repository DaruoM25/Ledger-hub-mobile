package com.ledgerhub.domain.audit

import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceStatus

/**
 * Jalons de la timeline de traçabilité réglementaire (US-17).
 *
 * Quatre étapes fixes, dans cet ordre : ce que la réforme PPF 2026 demande de pouvoir montrer
 * d'une facture — sa création, son scellement, sa transmission au portail, et le retour de
 * l'administration.
 */
enum class AuditMilestoneId {
    /** Document créé en mode brouillon. */
    CREATED,

    /** Scellé et chiffré sur le coffre-fort — porte l'empreinte d'intégrité. */
    SEALED,

    /** Transmis au Portail Public de Facturation. */
    PPF,

    /** Statut répercuté par l'administration. */
    STATUS,
}

/** État d'un jalon, qui commande sa pastille et son badge à l'écran. */
enum class AuditMilestoneState {
    /** Étape franchie. */
    DONE,

    /** Étape pas encore atteinte — pastille grise. */
    PENDING,

    /** Issue défavorable : rejet plateforme ou refus acheteur. */
    REJECTED,
}

/**
 * Un jalon résolu, prêt à être rendu. Ne porte **aucune** chaîne traduite ni couleur : le
 * domaine décide de l'état et de la date, la présentation décide de leur apparence.
 *
 * @param timestampIso horodatage ISO 8601 de l'étape, ou `null` si elle n'est pas franchie.
 * @param fingerprint empreinte SHA-256, portée par le seul jalon [AuditMilestoneId.SEALED].
 * @param reportedStatus statut répercuté, porté par le seul jalon [AuditMilestoneId.STATUS] —
 *   c'est lui qui détermine le libellé et la couleur du badge côté écran.
 * @param reason motif renvoyé par l'administration avec sa décision (« SIRET destinataire
 *   invalide »), porté par le seul jalon [AuditMilestoneId.STATUS]. Sans lui, un rejet
 *   n'apprendrait à l'utilisateur que le fait du rejet, pas ce qu'il doit corriger pour redéposer.
 */
data class AuditMilestone(
    val id: AuditMilestoneId,
    val state: AuditMilestoneState,
    val timestampIso: String? = null,
    val fingerprint: String? = null,
    val reportedStatus: InvoiceStatus? = null,
    val reason: String? = null,
)

/** Statuts qui prouvent que la facture a atteint le portail public de facturation. */
private val PPF_REACHED = setOf(InvoiceStatus.DEPOSITED, InvoiceStatus.APPROVED, InvoiceStatus.PAID)

/**
 * Construit la timeline d'une facture à partir de sa **Piste d'Audit Fiable réelle**.
 *
 * Les horodatages proviennent des [AuditEntry] persistées (voir `AuditLog.sq`, table en écriture
 * seule) et non d'un calcul d'affichage : un panneau intitulé « Horodatage réglementaire » qui
 * inventerait ses dates n'aurait aucune valeur probante. [Invoice.issueDate] ne sert que de repli,
 * pour le parc de factures antérieur à la PAF (US-07), qui n'a aucune trace.
 *
 * ## L'état se déduit du chemin parcouru, pas du statut courant
 *
 * Une facture rejetée peut légalement redevenir brouillon pour correction et redépôt (voir
 * `InvoiceStatusTransition`). Un `when (invoice.status)` naïf ferait alors repasser le jalon PPF
 * en « en attente » — alors que la facture *a bel et bien* été transmise, et que l'effacer
 * reviendrait à réécrire l'historique, ce qu'une piste d'audit interdit par définition.
 * La traversée est donc lue dans les entrées.
 *
 * @param entries entrées de la PAF, de la plus ancienne à la plus récente
 *   (ordre garanti par `AuditLog.sq:selectByInvoiceNumber`). Peut être vide.
 */
fun buildAuditTimeline(
    invoice: Invoice,
    entries: List<AuditEntry> = emptyList(),
): List<AuditMilestone> {
    // Statuts par lesquels la facture est passée : ceux inscrits dans la PAF, plus le statut
    // courant — indispensable pour une facture héritée, dépourvue de traces.
    val traversed: Set<InvoiceStatus> = entries.map { it.toStatus }.toSet() + invoice.status

    // La création est la première trace ; à défaut, la date d'émission portée par la pièce.
    val createdAt = entries.firstOrNull { it.fromStatus == null }?.createdAt
        ?: entries.firstOrNull()?.createdAt
        ?: invoice.issueDate.ifBlank { null }

    val reachedPpf = traversed.any { it in PPF_REACHED }
    val depositedAt = entries.firstOrNull { it.toStatus == InvoiceStatus.DEPOSITED }?.createdAt

    return listOf(
        // La pièce existe : ce jalon est franchi par construction.
        AuditMilestone(
            id = AuditMilestoneId.CREATED,
            state = AuditMilestoneState.DONE,
            timestampIso = createdAt,
        ),
        // Scellement : la facture est enregistrée, donc son empreinte est calculable. Elle est
        // recalculée à chaque affichage plutôt que stockée — une empreinte figée en base ne
        // prouverait plus que la pièce n'a pas bougé depuis.
        AuditMilestone(
            id = AuditMilestoneId.SEALED,
            state = AuditMilestoneState.DONE,
            timestampIso = createdAt,
            fingerprint = invoice.fingerprintSha256(),
        ),
        AuditMilestone(
            id = AuditMilestoneId.PPF,
            state = if (reachedPpf) AuditMilestoneState.DONE else AuditMilestoneState.PENDING,
            // Une facture héritée peut être déposée sans trace de dépôt : l'étape est alors
            // franchie mais non datée, ce que l'écran rend par une absence de date, pas par un 0.
            timestampIso = depositedAt.takeIf { reachedPpf },
        ),
        AuditMilestone(
            id = AuditMilestoneId.STATUS,
            state = invoice.status.reportedState(),
            timestampIso = entries.lastOrNull()?.createdAt.takeIf { invoice.status.isReported() },
            reportedStatus = invoice.status.takeIf { it.isReported() },
            reason = entries.lastOrNull()?.reason?.takeIf { invoice.status.isReported() },
        ),
    )
}

/**
 * L'administration s'est-elle prononcée ? Un brouillon, non ; une facture seulement déposée non
 * plus — elle attend encore le retour du portail.
 */
private fun InvoiceStatus.isReported(): Boolean = when (this) {
    InvoiceStatus.DRAFT, InvoiceStatus.PENDING_REGULARIZATION, InvoiceStatus.DEPOSITED -> false
    InvoiceStatus.APPROVED, InvoiceStatus.PAID,
    InvoiceStatus.REJECTED, InvoiceStatus.REFUSED,
    InvoiceStatus.CANCELLED,
    -> true
}

private fun InvoiceStatus.reportedState(): AuditMilestoneState = when (this) {
    // Les deux issues défavorables. Le refus acheteur porte sur une facture qui a circulé, le
    // rejet plateforme sur une facture jamais entrée dans le circuit : deux causes, une même
    // lecture pour l'utilisateur — l'étape s'est mal terminée.
    InvoiceStatus.REJECTED, InvoiceStatus.REFUSED -> AuditMilestoneState.REJECTED
    // L'annulation par avoir est un aboutissement comptable, pas un incident.
    InvoiceStatus.APPROVED, InvoiceStatus.PAID, InvoiceStatus.CANCELLED -> AuditMilestoneState.DONE
    InvoiceStatus.DRAFT, InvoiceStatus.PENDING_REGULARIZATION, InvoiceStatus.DEPOSITED -> AuditMilestoneState.PENDING
}
