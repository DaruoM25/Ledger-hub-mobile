package com.ledgerhub.domain.quote

import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceStatus

/**
 * "Le bouton magique" — convertit un devis Accepté en brouillon de facture.
 * Copie intégralement les lignes ([QuoteLine] -> [InvoiceLine]) et conserve [Quote.number]
 * dans [Invoice.sourceQuoteId] pour la piste d'audit fiscale entre devis et facture.
 */
class ConvertQuoteToInvoiceUseCase {

    operator fun invoke(quote: Quote, invoiceNumber: String, issueDate: String): Result<Invoice> {
        if (quote.status != QuoteStatus.ACCEPTED) {
            return Result.failure(
                IllegalStateException("Seul un devis au statut Accepté peut être converti en facture")
            )
        }

        val invoice = Invoice(
            number = invoiceNumber,
            issueDate = issueDate,
            issuer = quote.issuer,
            recipient = quote.recipient,
            lines = quote.lines.map { it.toInvoiceLine() },
            status = InvoiceStatus.DRAFT,
            sourceQuoteId = quote.number,
        )
        return Result.success(invoice)
    }

    private fun QuoteLine.toInvoiceLine(): InvoiceLine =
        InvoiceLine(label = label, quantity = quantity, unitPriceHt = unitPriceHt, vatRate = vatRate)
}
