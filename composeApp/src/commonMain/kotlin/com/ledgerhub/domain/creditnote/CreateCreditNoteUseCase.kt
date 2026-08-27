package com.ledgerhub.domain.creditnote

import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.Money

/**
 * Construit l'avoir d'annulation d'une facture finalisée : mêmes émetteur/destinataire,
 * montants intégralement inversés (voir [CreditNote] — règle fiscale : montants négatifs).
 * Symétrique à [com.ledgerhub.domain.quote.ConvertQuoteToInvoiceUseCase].
 */
class CreateCreditNoteUseCase {

    operator fun invoke(invoice: Invoice, number: String, issueDate: String, reason: String): Result<CreditNote> {
        if (!invoice.isCancellableByCreditNote) {
            return Result.failure(
                IllegalStateException("Seule une facture finalisée (validée, envoyée ou payée) peut être annulée par un avoir")
            )
        }
        if (reason.isBlank()) {
            return Result.failure(IllegalArgumentException("Un motif d'annulation est requis"))
        }

        return runCatching {
            CreditNote(
                number = number,
                issueDate = issueDate,
                invoiceId = invoice.number,
                reason = reason,
                issuer = invoice.issuer,
                recipient = invoice.recipient,
                totalHt = Money(-invoice.totalHt.cents),
                totalVat = Money(-invoice.totalVat.cents),
                totalTtc = Money(-invoice.totalTtc.cents),
            )
        }
    }
}
