package com.ledgerhub.domain.audit

import com.ledgerhub.domain.invoice.InvoiceStatus

/**
 * Une entrée de la Piste d'Audit Fiable : un changement de statut, daté et motivé.
 *
 * Les entrées ne sont **jamais modifiées ni supprimées** — c'est la définition même d'une piste
 * d'audit. Une correction s'exprime par une nouvelle transition, pas par la réécriture de
 * l'historique.
 *
 * @param fromStatus `null` pour la première entrée d'une facture (création), sans état antérieur.
 * @param reason motif — obligatoire sur les transitions négatives (rejet, refus), libre ailleurs.
 * @param createdAt horodatage ISO 8601 UTC, fourni par [Clock][com.ledgerhub.domain.time.Clock].
 */
data class AuditEntry(
    val id: String,
    val invoiceNumber: String? = null,
    val fromStatus: InvoiceStatus?,
    val toStatus: InvoiceStatus,
    val reason: String?,
    val createdAt: String,
    val userId: String? = null,
)

/** Historique des transitions — lecture seule par construction. */
interface AuditRepository {
    /** Entrées de [invoiceNumber], de la plus ancienne à la plus récente. */
    suspend fun entriesFor(invoiceNumber: String): Result<List<AuditEntry>>
}
