package com.ledgerhub.domain.reconciliation

import com.ledgerhub.domain.invoice.Money

/**
 * Écriture bancaire telle que la banque la restitue (US-18).
 *
 * Reflet d'un relevé, jamais une donnée que l'application produit : une transaction n'est ni
 * créée, ni modifiée, ni supprimée depuis l'interface. Le rapprochement ne la touche pas — il
 * inscrit un lien à côté (voir [ReconciliationMatch]).
 *
 * @param amount montant **signé**, en centimes : positif pour un encaissement, négatif pour un
 *   décaissement. Le signe porté par le montant plutôt que par un enum « sens » permet de calculer
 *   l'écart avec une facture par simple soustraction, sans conversion préalable.
 * @param valueDateIso date de valeur (AAAA-MM-JJ), celle qui fait foi en rapprochement — la date
 *   d'opération peut en différer de plusieurs jours.
 */
data class BankTransaction(
    val id: String,
    val label: String,
    val amount: Money,
    val valueDateIso: String,
    val counterparty: String = "",
) {
    /** Un encaissement : seul sens qui puisse solder une facture client. */
    val isCredit: Boolean get() = amount > Money.ZERO
}
