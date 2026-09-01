package com.ledgerhub.domain.reconciliation

import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.Money

/**
 * Écart entre un encaissement et la facture qu'il est censé solder (US-18).
 *
 * Convention de signe : `transaction - facture`. Un écart **positif** signifie que le client a
 * versé plus que dû (trop-perçu), un écart **négatif** qu'il reste à recouvrer. L'inverse se
 * lirait à contresens du langage comptable, où l'on parle du solde restant dû.
 *
 * Le rapprochement d'un montant qui ne tombe pas juste n'est pas interdit — c'est le cas courant
 * d'un virement amputé de frais bancaires, ou d'un paiement partiel. Il est **signalé**, pour que
 * l'écart soit justifié plutôt que découvert au bilan.
 */
object AmountDelta {

    /** Écart entre l'encaissement et le total TTC de la facture. [Money.ZERO] si le compte est juste. */
    fun between(transaction: BankTransaction, invoice: Invoice): Money =
        transaction.amount - invoice.totalTtc

    /** `true` si l'encaissement ne solde pas exactement la facture. */
    fun hasMismatch(transaction: BankTransaction, invoice: Invoice): Boolean =
        between(transaction, invoice) != Money.ZERO
}
