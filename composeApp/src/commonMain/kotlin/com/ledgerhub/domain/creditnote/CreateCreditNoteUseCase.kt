package com.ledgerhub.domain.creditnote

import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.Money

/**
 * Construit l'avoir d'annulation d'une facture finalisée : mêmes émetteur et destinataire,
 * lignes recopiées, montants intégralement inversés (règle fiscale — voir [CreditNote]).
 * Symétrique à [com.ledgerhub.domain.quote.ConvertQuoteToInvoiceUseCase].
 *
 * La construction est pure : le blocage d'un second avoir dépend de l'état persisté et relève
 * donc du repository ([InvoiceAlreadyCreditedException]), pas de ce use case.
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
        if (!CreditNoteNumbering.isValid(number)) {
            return Result.failure(
                IllegalArgumentException("Le numéro d'avoir doit respecter le format AV-AAAA-NNNN")
            )
        }

        return runCatching {
            CreditNote(
                number = number,
                issueDate = issueDate,
                invoiceId = invoice.number,
                originalInvoiceDate = invoice.issueDate,
                reason = reason,
                issuer = invoice.issuer,
                recipient = invoice.recipient,
                // Recopie intégrale : l'avoir reste lisible sans sa facture parente.
                lines = invoice.lines,
                totalHt = Money(-invoice.totalHt.cents),
                totalVat = Money(-invoice.totalVat.cents),
                totalTtc = Money(-invoice.totalTtc.cents),
            )
        }
    }
}
