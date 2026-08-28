package com.ledgerhub.presentation.invoices

import androidx.compose.ui.graphics.Color
import com.ledgerhub.domain.invoice.InvoiceStatus

/**
 * Helpers d'affichage du statut fiscal d'une facture, partagés par la liste et le détail
 * (US-02). Noms distincts de `com.ledgerhub.presentation.invoicedetail.label` /
 * `.badgeColor` (module Avoir) pour éviter toute collision d'import côté écrans transverses.
 */

/** Libellé français affichable d'un statut de facture. */
fun InvoiceStatus.displayLabel(): String = when (this) {
    InvoiceStatus.DRAFT -> "Brouillon"
    InvoiceStatus.VALIDATED -> "Validée"
    InvoiceStatus.SENT -> "Envoyée"
    InvoiceStatus.PAID -> "Payée"
    InvoiceStatus.CANCELLED -> "Annulée"
}

/** Couleur de la pastille de statut — palette alignée sur l'écran de détail existant. */
fun InvoiceStatus.tagColor(): Color = when (this) {
    InvoiceStatus.DRAFT -> Color(0xFF9E9E9E)
    InvoiceStatus.VALIDATED -> Color(0xFF2196F3)
    InvoiceStatus.SENT -> Color(0xFF3F51B5)
    InvoiceStatus.PAID -> Color(0xFF4CAF50)
    InvoiceStatus.CANCELLED -> Color(0xFFF44336)
}
