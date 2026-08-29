package com.ledgerhub.domain.creditnote

import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatBreakdown
import com.ledgerhub.domain.invoice.computeVatBreakdown

/**
 * Avoir d'annulation intégrale d'une facture finalisée — voir
 * [Invoice.isCancellableByCreditNote][com.ledgerhub.domain.invoice.Invoice.isCancellableByCreditNote].
 *
 * **Référence croisée** : [invoiceId] et [originalInvoiceDate] désignent ensemble la facture
 * annulée. Factur-X 2026 attend le couple, pas le seul numéro.
 *
 * **Autoportance** : [lines] est la recopie des lignes de la facture d'origine. Un avoir est une
 * pièce fiscale à part entière et doit rester lisible sans sa facture parente — même principe
 * que la copie gelée du destinataire portée par la facture depuis D-03.
 *
 * **Sens comptable** : les prix des lignes restent positifs (ce sont ceux facturés) ; ce sont les
 * totaux et les assiettes de TVA qui sont portés au crédit, donc négatifs. Le [reason] est
 * obligatoire : c'est le motif légal de l'annulation.
 */
data class CreditNote(
    val number: String,
    val issueDate: String,
    val invoiceId: String,
    val originalInvoiceDate: String,
    val reason: String,
    val issuer: Party,
    val recipient: Party,
    val lines: List<InvoiceLine>,
    val totalHt: Money,
    val totalVat: Money,
    val totalTtc: Money,
) {
    init {
        require(reason.isNotBlank()) { "Un avoir doit obligatoirement indiquer un motif d'annulation" }
        require(totalHt.cents <= 0) { "Le montant HT d'un avoir doit être négatif ou nul" }
        require(totalVat.cents <= 0) { "Le montant de TVA d'un avoir doit être négatif ou nul" }
        require(totalTtc.cents <= 0) { "Le montant TTC d'un avoir doit être négatif ou nul" }
    }

    /**
     * Assiettes de TVA de l'avoir, par taux — recalculées depuis [lines] par le même moteur que
     * la facture, puis inversées. Aucune duplication de l'arithmétique fiscale.
     */
    val vatBreakdown: List<VatBreakdown>
        get() = computeVatBreakdown(lines).map { breakdown ->
            VatBreakdown(
                rate = breakdown.rate,
                baseHt = Money(-breakdown.baseHt.cents),
                vatAmount = Money(-breakdown.vatAmount.cents),
            )
        }
}
