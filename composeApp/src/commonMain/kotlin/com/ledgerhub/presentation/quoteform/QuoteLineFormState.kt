package com.ledgerhub.presentation.quoteform

import com.ledgerhub.domain.invoice.VatRate

/** Identifie un champ d'une ligne pour lui associer un message d'erreur. */
enum class QuoteLineField {
    LABEL,
    QUANTITY,
    UNIT_PRICE,
}

/**
 * État d'une ligne de devis dans le formulaire — champs en texte brut (comme les champs
 * d'en-tête SIREN/SIRET/date) pour permettre la saisie libre et l'affichage d'une saisie
 * intermédiaire invalide sans perte de caractères, avant validation.
 */
data class QuoteLineFormState(
    val label: String = "",
    val quantity: String = "1",
    val unitPriceHt: String = "",
    val vatRate: VatRate = VatRate.TAUX_NORMAL,
    val errors: Map<QuoteLineField, String> = emptyMap(),
)
