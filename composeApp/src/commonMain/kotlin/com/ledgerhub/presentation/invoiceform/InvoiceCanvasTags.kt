package com.ledgerhub.presentation.invoiceform

/**
 * Contrat de tags du **mode canvas** (US-23) — les huit chaînes imposées par le cahier des
 * charges, et la seule source de vérité pour elles.
 *
 * ## Pourquoi cet objet existe à côté de `InvoicePaperCanvasTags`
 *
 * Le mode canvas n'est pas une fonctionnalité nouvelle : c'est le « Mode Page Blanche » livré par
 * l'US-15, dont l'écran, le ViewModel, l'i18n et les quatre suites de tests sont en place. Ce que
 * l'US-23 apporte, c'est **un contrat de tags normalisé** et deux nœuds qui n'en portaient aucun
 * (l'encart client et le tableau des lignes), plus la distinction entre le bureau et la feuille.
 *
 * Un nœud Compose ne porte qu'un seul `testTag` : les deux jeux ne pouvaient donc pas coexister
 * sur les mêmes nœuds. Les valeurs canoniques vivent ici, et
 * [InvoicePaperCanvasTags] délègue — les suites US-15 et US-16 continuent de désigner les mêmes
 * nœuds par les mêmes constantes, sans être réécrites.
 *
 * Ces huit valeurs sont **figées par le cahier des charges** : les modifier casserait les suites
 * QA. `InvoiceCanvasTagsTest` (N1) les verrouille, cohérence des alias comprise.
 */
object InvoiceCanvasTags {

    /** Segment « Mode Page Blanche » du sélecteur de mode — l'entrée du canvas. */
    const val MODE_BUTTON = "invoice_mode_canvas_btn"

    /**
     * Le **bureau** : la zone grise défilante qui porte la feuille.
     *
     * Distinct de [PAGE], et la distinction n'est pas cosmétique : c'est le conteneur qui défile,
     * et c'est la feuille que la capture QA officielle cadre.
     */
    const val CONTAINER = "invoice_canvas_container"

    /** La **feuille A4** elle-même : surface blanche, ombre portée, marges d'impression. */
    const val PAGE = "invoice_canvas_page"

    /** Encart client, en haut à droite de la feuille — éditable en place. */
    const val CLIENT_CARD = "invoice_canvas_client_card"

    /** Tableau des lignes de prestation, en-tête de colonnes compris. */
    const val ITEMS_TABLE = "invoice_canvas_items_table"

    const val TOTAL_HT = "invoice_canvas_total_ht"

    /** `tva` et non `vat` : la forme littérale est celle qu'impose le cahier des charges. */
    const val TOTAL_VAT = "invoice_canvas_total_tva"

    const val TOTAL_TTC = "invoice_canvas_total_ttc"

    /** Les huit tags imposés, dans l'ordre du cahier des charges — sert aux contrôles d'unicité. */
    fun specified(): List<String> = listOf(
        MODE_BUTTON,
        CONTAINER,
        PAGE,
        CLIENT_CARD,
        ITEMS_TABLE,
        TOTAL_HT,
        TOTAL_VAT,
        TOTAL_TTC,
    )
}
