package com.ledgerhub.presentation.quoteform

import com.ledgerhub.domain.invoice.VatRate

sealed interface QuoteFormIntent {
    data class QuoteNumberChanged(val value: String) : QuoteFormIntent
    data class IssueDateChanged(val value: String) : QuoteFormIntent
    data class ValidityDateChanged(val value: String) : QuoteFormIntent
    data class IssuerNameChanged(val value: String) : QuoteFormIntent
    data class IssuerSirenChanged(val value: String) : QuoteFormIntent
    data class IssuerSiretChanged(val value: String) : QuoteFormIntent
    data class RecipientNameChanged(val value: String) : QuoteFormIntent
    data class RecipientSirenChanged(val value: String) : QuoteFormIntent
    data class RecipientSiretChanged(val value: String) : QuoteFormIntent

    /** Ajoute une ligne de devis vierge en fin de liste. */
    data object AddLine : QuoteFormIntent

    /** Supprime la ligne à [index] — sans effet s'il ne reste qu'une ligne ou si le formulaire est verrouillé. */
    data class RemoveLine(val index: Int) : QuoteFormIntent

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
    ) : QuoteFormIntent

    data object Submit : QuoteFormIntent
}
