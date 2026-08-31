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
    InvoiceStatus.APPROVED -> "Approuvée par l'administration"
    InvoiceStatus.PAID -> "Encaissée"
    InvoiceStatus.REJECTED -> "Rejetée par la plateforme"
    InvoiceStatus.REFUSED -> "Refusée"
    InvoiceStatus.CANCELLED -> "Annulée"
}

/** Clé de traduction du statut — résolue via `LocalAppLanguage` côté écran. */
fun InvoiceStatus.labelKey(): StringKey = when (this) {
    InvoiceStatus.DRAFT -> StringKey.STATUS_DRAFT
    InvoiceStatus.DEPOSITED -> StringKey.STATUS_DEPOSITED
    InvoiceStatus.APPROVED -> StringKey.STATUS_APPROVED
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
    InvoiceStatusFilter.APPROVED -> StringKey.FILTER_APPROVED
    InvoiceStatusFilter.PAID -> StringKey.FILTER_PAID
    InvoiceStatusFilter.REJECTED -> StringKey.FILTER_REJECTED
    InvoiceStatusFilter.REFUSED -> StringKey.FILTER_REFUSED
    InvoiceStatusFilter.CANCELLED -> StringKey.FILTER_CANCELLED
}

/**
 * Couleur d'accent d'un statut — celle du point indicateur de la pastille, et la seule teinte
 * pleinement saturée du badge. Les deux issues défavorables — rejet plateforme et refus
 * acheteur — partagent une teinte d'alerte distincte de l'annulation, qui est un état comptable
 * abouti et non un incident.
 */
fun InvoiceStatus.tagColor(): Color = when (this) {
    InvoiceStatus.DRAFT -> Color(0xFF9E9E9E)
    InvoiceStatus.DEPOSITED -> Color(0xFF1565C0)
    InvoiceStatus.APPROVED -> Color(0xFF00A86B)
    InvoiceStatus.PAID -> Color(0xFF4CAF50)
    InvoiceStatus.REJECTED -> Color(0xFFD32F2F)
    InvoiceStatus.REFUSED -> Color(0xFFD84315)
    InvoiceStatus.CANCELLED -> Color(0xFFF44336)
}

/**
 * Fond de la pastille. Un aplat saturé sur un libellé désormais long ("Approuvée par
 * l'administration") pèserait visuellement plus que le montant de la facture ; le badge passe
 * donc en fond conteneur clair, l'accent restant porté par le point indicateur.
 */
fun InvoiceStatus.containerColor(): Color = when (this) {
    InvoiceStatus.DRAFT -> Color(0xFFF0F0F0)
    InvoiceStatus.DEPOSITED -> Color(0xFFE3F0FC)
    InvoiceStatus.APPROVED -> Color(0xFFE0F5EC)
    InvoiceStatus.PAID -> Color(0xFFE8F5E9)
    InvoiceStatus.REJECTED -> Color(0xFFFDE7E7)
    InvoiceStatus.REFUSED -> Color(0xFFFBE9E2)
    InvoiceStatus.CANCELLED -> Color(0xFFFDE7E7)
}

/** Couleur du libellé sur [containerColor] — assombrie pour tenir le contraste AA en texte petit. */
fun InvoiceStatus.onContainerColor(): Color = when (this) {
    InvoiceStatus.DRAFT -> Color(0xFF424242)
    InvoiceStatus.DEPOSITED -> Color(0xFF0D47A1)
    InvoiceStatus.APPROVED -> Color(0xFF00674B)
    InvoiceStatus.PAID -> Color(0xFF1B5E20)
    InvoiceStatus.REJECTED -> Color(0xFFB3261E)
    InvoiceStatus.REFUSED -> Color(0xFF8F2C0C)
    InvoiceStatus.CANCELLED -> Color(0xFFB3261E)
}

/**
 * Glyphe précédant le libellé, ou `null`. Réservé aux issues défavorables : porter la couleur
 * seule les distinguerait mal du reste pour un utilisateur daltonien, alors que le libellé
 * complet reste par ailleurs exposé en `contentDescription`.
 */
fun InvoiceStatus.glyph(): String? = when (this) {
    InvoiceStatus.REJECTED, InvoiceStatus.REFUSED -> "⚠"
    else -> null
}
