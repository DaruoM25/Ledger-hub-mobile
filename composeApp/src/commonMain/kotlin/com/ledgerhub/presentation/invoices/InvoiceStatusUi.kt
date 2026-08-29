package com.ledgerhub.presentation.invoices

import androidx.compose.ui.graphics.Color
import com.ledgerhub.domain.i18n.StringKey
import com.ledgerhub.domain.invoice.InvoiceStatus

/**
 * Helpers d'affichage du statut réglementaire d'une facture, partagés par la liste et le détail.
 * Noms distincts de `com.ledgerhub.presentation.invoicedetail.label` / `.badgeColor` (module
 * Avoir) pour éviter toute collision d'import côté écrans transverses.
 */

/** Libellé français d'un statut. Conservé pour compat ; l'UI passe par [labelKey]. */
fun InvoiceStatus.displayLabel(): String = when (this) {
    InvoiceStatus.DRAFT -> "Brouillon"
    InvoiceStatus.DEPOSITED -> "Déposée"
    InvoiceStatus.PAID -> "Encaissée"
    InvoiceStatus.REJECTED -> "Rejetée"
    InvoiceStatus.REFUSED -> "Refusée"
    InvoiceStatus.CANCELLED -> "Annulée"
}

/** Clé de traduction du statut — résolue via `LocalAppLanguage` côté écran. */
fun InvoiceStatus.labelKey(): StringKey = when (this) {
    InvoiceStatus.DRAFT -> StringKey.STATUS_DRAFT
    InvoiceStatus.DEPOSITED -> StringKey.STATUS_DEPOSITED
    InvoiceStatus.PAID -> StringKey.STATUS_PAID
    InvoiceStatus.REJECTED -> StringKey.STATUS_REJECTED
    InvoiceStatus.REFUSED -> StringKey.STATUS_REFUSED
    InvoiceStatus.CANCELLED -> StringKey.STATUS_CANCELLED
}

/** Clé de traduction d'un filtre de la liste (libellés au pluriel). */
fun InvoiceStatusFilter.labelKey(): StringKey = when (this) {
    InvoiceStatusFilter.TOUTES -> StringKey.FILTER_ALL
    InvoiceStatusFilter.DRAFT -> StringKey.FILTER_DRAFT
    InvoiceStatusFilter.DEPOSITED -> StringKey.FILTER_DEPOSITED
    InvoiceStatusFilter.PAID -> StringKey.FILTER_PAID
    InvoiceStatusFilter.REJECTED -> StringKey.FILTER_REJECTED
    InvoiceStatusFilter.REFUSED -> StringKey.FILTER_REFUSED
    InvoiceStatusFilter.CANCELLED -> StringKey.FILTER_CANCELLED
}

/**
 * Couleur de la pastille de statut. Les deux issues défavorables — rejet plateforme et refus
 * acheteur — partagent une teinte d'alerte distincte de l'annulation, qui est un état comptable
 * abouti et non un incident.
 */
fun InvoiceStatus.tagColor(): Color = when (this) {
    InvoiceStatus.DRAFT -> Color(0xFF9E9E9E)
    InvoiceStatus.DEPOSITED -> Color(0xFF3F51B5)
    InvoiceStatus.PAID -> Color(0xFF4CAF50)
    InvoiceStatus.REJECTED -> Color(0xFFEF6C00)
    InvoiceStatus.REFUSED -> Color(0xFFD84315)
    InvoiceStatus.CANCELLED -> Color(0xFFF44336)
}
