package com.ledgerhub.domain.invoice

/** Cas d'usage unique : soumettre une facture via le [InvoiceRepository] injecté. */
class SubmitInvoiceUseCase(private val repository: InvoiceRepository) {
    suspend operator fun invoke(invoice: Invoice): Result<Unit> = repository.submitInvoice(invoice)
}
