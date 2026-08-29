package com.ledgerhub.presentation.invoiceform

import com.ledgerhub.domain.i18n.ValidationErrorKey
import com.ledgerhub.domain.invoice.VatRate

/** Identifie un champ d'une ligne pour lui associer un message d'erreur. */
enum class InvoiceLineField {
    LABEL,
    QUANTITY,
    UNIT_PRICE,
}

/**
 * État d'une ligne de facturation dans le formulaire — champs en texte brut (comme les champs
 * d'en-tête SIREN/SIRET/date) pour permettre la saisie libre et l'affichage d'une saisie
 * intermédiaire invalide sans perte de caractères, avant validation.
 */
data class InvoiceLineFormState(
    val label: String = "",
    val quantity: String = "1",
    val unitPriceHt: String = "",
    val vatRate: VatRate = VatRate.TAUX_NORMAL,
    val errors: Map<InvoiceLineField, ValidationErrorKey> = emptyMap(),
    /** Champs de la ligne déjà saisis — conditionne l'affichage des erreurs, pas leur calcul. */
    val touched: Set<InvoiceLineField> = emptySet(),
) {
    /**
     * Erreurs présentées pour cette ligne. [revealAll] est vrai dès la première tentative
     * d'émission : les erreurs des champs jamais saisis deviennent alors visibles elles aussi.
     */
    fun visibleErrors(revealAll: Boolean): Map<InvoiceLineField, ValidationErrorKey> =
        if (revealAll) errors else errors.filterKeys { it in touched }
}
