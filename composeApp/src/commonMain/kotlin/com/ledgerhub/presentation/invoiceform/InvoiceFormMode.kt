package com.ledgerhub.presentation.invoiceform

import com.ledgerhub.domain.i18n.StringKey

/**
 * Mode de saisie de la facture (US-15) — deux représentations d'un **même**
 * [InvoiceFormUiState] :
 *
 * - [CLASSIC] : le formulaire sectionné historique ([InvoiceFormContent]).
 * - [BLANK_PAGE] : la feuille A4 éditable en place ([InvoicePaperCanvas]), à la manière d'une
 *   page Notion — on écrit directement sur le document tel qu'il sera émis.
 *
 * Le mode est un état **de vue**, délibérément absent de [InvoiceFormUiState] : basculer ne
 * touche ni la saisie, ni les erreurs, ni les totaux. C'est l'invariant que verrouille
 * `InvoiceFormModeTest`.
 */
enum class InvoiceFormMode(val labelKey: StringKey) {
    CLASSIC(StringKey.FORM_MODE_CLASSIC),
    BLANK_PAGE(StringKey.FORM_MODE_BLANK_PAGE),
}
