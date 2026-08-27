package com.ledgerhub.domain.quote

import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatBreakdown

data class Quote(
    val number: String,
    val issueDate: String,
    /** Date jusqu'à laquelle le devis engage l'émetteur — remplace l'échéance de paiement des factures. */
    val validityDate: String,
    val issuer: Party,
    val recipient: Party,
    val lines: List<QuoteLine>,
    val status: QuoteStatus = QuoteStatus.DRAFT,
) {
    init {
        require(lines.isNotEmpty()) { "Un devis doit contenir au moins une ligne" }
    }

    val vatBreakdown: List<VatBreakdown> get() = computeQuoteVatBreakdown(lines)
    val totalHt: Money get() = totalHtOf(lines)
    val totalVat: Money get() = totalVatOf(lines)
    val totalTtc: Money get() = totalTtcOf(lines)

    /** Seul un devis Brouillon peut encore être modifié. */
    val isEditable: Boolean get() = status == QuoteStatus.DRAFT

    /** Le bouton de conversion en facture n'est disponible qu'une fois le devis Accepté. */
    val isConvertibleToInvoice: Boolean get() = status == QuoteStatus.ACCEPTED
}

/** Règle métier explicite — pas de suppression hors statut Brouillon. */
fun canDelete(quote: Quote): Boolean = quote.status == QuoteStatus.DRAFT
