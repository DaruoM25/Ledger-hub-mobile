package com.ledgerhub.domain.reconciliation

import com.ledgerhub.domain.invoice.Money

/**
 * Lettrage : le lien entre une écriture bancaire et la facture qu'elle solde (US-18).
 *
 * C'est **la** source de vérité de l'état « rapprochée ». Ni la transaction ni la facture ne
 * portent de drapeau : deux marqueurs pour un même fait finissent toujours par diverger, et il
 * faudrait alors décider lequel croit-on. Ici la question ne se pose pas — une ligne existe, ou
 * elle n'existe pas.
 *
 * @param deltaCents écart entre l'encaissement et le total TTC de la facture, figé **au moment du
 *   lettrage**. Recalculé à l'affichage, il changerait si la facture évoluait ; or c'est bien
 *   l'écart constaté ce jour-là qui devra être justifié en contrôle.
 * @param matchedAtIso horodatage ISO 8601 UTC, produit par [com.ledgerhub.domain.time.Clock].
 */
data class ReconciliationMatch(
    val transactionId: String,
    val invoiceNumber: String,
    val matchedAtIso: String,
    val deltaCents: Long,
) {
    val delta: Money get() = Money(deltaCents)

    /** Le lettrage ne solde pas exactement la facture — à justifier, sans être interdit. */
    val hasAmountMismatch: Boolean get() = deltaCents != 0L
}
