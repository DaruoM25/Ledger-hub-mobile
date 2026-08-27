package com.ledgerhub.presentation.invoiceform

import com.ledgerhub.domain.invoice.VatRate

sealed interface InvoiceFormIntent {
    data class InvoiceNumberChanged(val value: String) : InvoiceFormIntent
    data class IssueDateChanged(val value: String) : InvoiceFormIntent
    data class IssuerNameChanged(val value: String) : InvoiceFormIntent
    data class IssuerSirenChanged(val value: String) : InvoiceFormIntent
    data class IssuerSiretChanged(val value: String) : InvoiceFormIntent
    data class RecipientNameChanged(val value: String) : InvoiceFormIntent
    data class RecipientSirenChanged(val value: String) : InvoiceFormIntent
    data class RecipientSiretChanged(val value: String) : InvoiceFormIntent

    /** Ajoute une ligne de facturation vierge en fin de liste. */
    data object AddLine : InvoiceFormIntent

    /** Supprime la ligne à [index] — sans effet s'il ne reste qu'une ligne ou si le formulaire est verrouillé. */
    data class RemoveLine(val index: Int) : InvoiceFormIntent

    /**
     * Met à jour les champs de la ligne à [index]. Les valeurs restent en texte brut (comme les
     * autres champs du formulaire) pour permettre une saisie intermédiaire invalide sans perte
     * de caractères — la validation/parsing se fait lors de la revalidation, pas à la frontière
     * de l'intention.
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
