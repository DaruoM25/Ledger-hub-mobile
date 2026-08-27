package com.ledgerhub.presentation.invoicedetail

import com.ledgerhub.domain.invoice.Invoice

/** État immuable de l'écran de détail d'une facture. */
data class InvoiceDetailUiState(
    val invoice: Invoice,
    /** Un avoir existe déjà pour cette facture — empêche une double annulation. */
    val hasCreditNote: Boolean = false,
) {
    /** Le bouton "Annuler par un avoir" n'apparaît que sur une facture finalisée sans avoir existant. */
    val canCreateCreditNote: Boolean get() = invoice.isCancellableByCreditNote && !hasCreditNote
}
