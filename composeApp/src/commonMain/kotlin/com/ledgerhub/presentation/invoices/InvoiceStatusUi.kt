package com.ledgerhub.presentation.invoices

import androidx.compose.ui.graphics.Color
import com.ledgerhub.domain.i18n.StringKey
import com.ledgerhub.domain.invoice.InvoiceStatus

/**
 * Helpers d'affichage du statut fiscal d'une facture, partagés par la liste et le détail
 * (US-02). Noms distincts de `com.ledgerhub.presentation.invoicedetail.label` /
 * `.badgeColor` (module Avoir) pour éviter toute collision d'import côté écrans transverses.
 */

/** Libellé français affichable d'un statut de facture. Conservé pour compat ; l'UI passe désormais par [labelKey]. */
fun InvoiceStatus.displayLabel(): String = when (this) {
    InvoiceStatus.DRAFT -> "Brouillon"
    InvoiceStatus.VALIDATED -> "Validée"
    InvoiceStatus.SENT -> "Envoyée"
    InvoiceStatus.PAID -> "Payée"
    InvoiceStatus.CANCELLED -> "Annulée"
}

/** Clé de traduction du statut — résolue via `LocalAppLanguage` côté écran. */
fun InvoiceStatus.labelKey(): StringKey = when (this) {
    InvoiceStatus.DRAFT -> StringKey.STATUS_DRAFT
    InvoiceStatus.VALIDATED -> StringKey.STATUS_VALIDATED
    InvoiceStatus.SENT -> StringKey.STATUS_SENT
    InvoiceStatus.PAID -> StringKey.STATUS_PAID
    InvoiceStatus.CANCELLED -> StringKey.STATUS_CANCELLED
}

/** Clé de traduction d'un filtre de la liste (libellés au pluriel). */
fun InvoiceStatusFilter.labelKey(): StringKey = when (this) {
    InvoiceStatusFilter.TOUTES -> StringKey.FILTER_ALL
    InvoiceStatusFilter.DRAFT -> StringKey.FILTER_DRAFT
    InvoiceStatusFilter.VALIDATED -> StringKey.FILTER_VALIDATED
    InvoiceStatusFilter.SENT -> StringKey.FILTER_SENT
    InvoiceStatusFilter.PAID -> StringKey.FILTER_PAID
    InvoiceStatusFilter.CANCELLED -> StringKey.FILTER_CANCELLED
}

/** Couleur de la pastille de statut — palette alignée sur l'écran de détail existant. */
fun InvoiceStatus.tagColor(): Color = when (this) {
    InvoiceStatus.DRAFT -> Color(0xFF9E9E9E)
    InvoiceStatus.VALIDATED -> Color(0xFF2196F3)
    InvoiceStatus.SENT -> Color(0xFF3F51B5)
    InvoiceStatus.PAID -> Color(0xFF4CAF50)
    InvoiceStatus.CANCELLED -> Color(0xFFF44336)
}
