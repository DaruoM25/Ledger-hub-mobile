package com.ledgerhub.domain.creditnote

import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party

/**
 * Avoir d'annulation intégrale d'une facture finalisée — voir [Invoice.isCancellableByCreditNote].
 * Toujours lié à [invoiceId] (piste d'audit fiscale) et porteur d'un [reason] obligatoire
 * (motif légal de l'annulation). Les montants sont toujours négatifs : ils annulent
 * comptablement ceux de la facture d'origine plutôt que de la modifier ou de la supprimer.
 */
data class CreditNote(
    val number: String,
    val issueDate: String,
    val invoiceId: String,
    val reason: String,
    val issuer: Party,
    val recipient: Party,
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
}
