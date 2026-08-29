package com.ledgerhub.presentation.invoiceform

import com.ledgerhub.domain.invoice.VatRate

sealed interface InvoiceFormIntent {
    // ── Détails de la facture ──────────────────────────────────────────────────
    data class InvoiceNumberChanged(val value: String) : InvoiceFormIntent
    data class IssueDateChanged(val value: String) : InvoiceFormIntent
    data class DueDateChanged(val value: String) : InvoiceFormIntent

    // ── Informations client ───────────────────────────────────────────────────
    data class ClientNameChanged(val value: String) : InvoiceFormIntent
    data class ClientSiretChanged(val value: String) : InvoiceFormIntent
    data class ClientEmailChanged(val value: String) : InvoiceFormIntent

    /** Bascule « Générer au format légal Factur-X ». */
    data class ToggleFacturX(val enabled: Boolean) : InvoiceFormIntent

    /** Ajoute une ligne de prestation vierge en fin de liste. */
    data object AddLine : InvoiceFormIntent

    /** Supprime la ligne à [index] — sans effet s'il ne reste qu'une ligne ou si le formulaire est verrouillé. */
    data class RemoveLine(val index: Int) : InvoiceFormIntent

    /**
     * Met à jour les champs de la ligne à [index]. Les valeurs restent en texte brut : la
     * validation/parsing se fait lors de la revalidation, pas à la frontière de l'intention.
     */
    data class UpdateLine(
        val index: Int,
        val label: String,
        val quantity: String,
        val unitPriceHt: String,
        val vatRate: VatRate,
    ) : InvoiceFormIntent

    data object Submit : InvoiceFormIntent
}
