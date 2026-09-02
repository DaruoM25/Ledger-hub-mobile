package com.ledgerhub.presentation.invoiceform

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Niveau 1 (US-23) — verrouillage du contrat de tags du mode canvas.
 *
 * Ces huit chaînes sont **imposées par le cahier des charges** et consommées par les niveaux 3a et
 * 3b. Les figer ici les sort du domaine de la relecture : un renommage de constante ou une
 * interpolation « améliorée » casse ce test avant de casser la QA (même parti pris que
 * `ExportModalTagsTest` pour l'US-22).
 *
 * S'y ajoute une vérification que l'US-22 n'avait pas à faire : la **cohérence des alias**. Le
 * canvas était déjà tagué par l'US-15, et un nœud Compose ne porte qu'un `testTag` — les valeurs
 * canoniques ont donc été déplacées ici, `InvoicePaperCanvasTags` y déléguant. Si cette délégation
 * se défaisait, les suites US-15 et US-16 viseraient des nœuds qui n'existent plus, et elles
 * échoueraient loin de la cause.
 */
class InvoiceCanvasTagsTest {

    @Test
    fun theEightSpecifiedTags_matchTheSpecification() {
        assertEquals("invoice_mode_canvas_btn", InvoiceCanvasTags.MODE_BUTTON)
        assertEquals("invoice_canvas_container", InvoiceCanvasTags.CONTAINER)
        assertEquals("invoice_canvas_page", InvoiceCanvasTags.PAGE)
        assertEquals("invoice_canvas_client_card", InvoiceCanvasTags.CLIENT_CARD)
        assertEquals("invoice_canvas_items_table", InvoiceCanvasTags.ITEMS_TABLE)
        assertEquals("invoice_canvas_total_ht", InvoiceCanvasTags.TOTAL_HT)
        assertEquals("invoice_canvas_total_tva", InvoiceCanvasTags.TOTAL_VAT)
        assertEquals("invoice_canvas_total_ttc", InvoiceCanvasTags.TOTAL_TTC)
    }

    @Test
    fun theSpecifiedList_holdsExactlyThoseEight_withoutDuplicates() {
        val specified = InvoiceCanvasTags.specified()

        assertEquals(8, specified.size, "Le cahier des charges US-23 impose exactement 8 tags")
        assertEquals(specified.size, specified.toSet().size, "Tags dupliqués : $specified")
    }

    /**
     * Le bureau et la feuille sont **deux nœuds distincts** : c'est la seule raison d'être de la
     * distinction, et un tag partagé la ferait disparaître sans que rien ne le signale.
     */
    @Test
    fun theDeskAndThePage_areTaggedApart() {
        assertEquals(
            2,
            setOf(InvoiceCanvasTags.CONTAINER, InvoiceCanvasTags.PAGE).size,
            "Le conteneur et la feuille doivent porter deux tags distincts",
        )
    }

    // ── Cohérence des alias hérités de l'US-15 ──────────────────────────────

    @Test
    fun theHistoricalCanvasTags_delegateToTheCanonicalOnes() {
        assertEquals(InvoiceCanvasTags.CONTAINER, InvoicePaperCanvasTags.CANVAS)
        assertEquals(InvoiceCanvasTags.TOTAL_HT, InvoicePaperCanvasTags.TOTAL_HT)
        assertEquals(InvoiceCanvasTags.TOTAL_VAT, InvoicePaperCanvasTags.TOTAL_VAT)
        assertEquals(InvoiceCanvasTags.TOTAL_TTC, InvoicePaperCanvasTags.TOTAL_TTC)
    }

    /**
     * Les tags US-15 sans équivalent US-23 gardent leurs valeurs : rien ne justifiait de les
     * changer, et les changer aurait cassé un contrat QA pour rien.
     */
    @Test
    fun theTagsWithoutASpecifiedCounterpart_keepTheirOriginalValues() {
        assertEquals("invoice_paper_cabinet_name", InvoicePaperCanvasTags.CABINET_NAME)
        assertEquals("invoice_paper_cabinet_siret", InvoicePaperCanvasTags.CABINET_SIRET)
        assertEquals("invoice_paper_client_name", InvoicePaperCanvasTags.CLIENT_NAME)
        assertEquals("invoice_paper_client_siret", InvoicePaperCanvasTags.CLIENT_SIRET)
        assertEquals("invoice_paper_line_0_label", InvoicePaperCanvasTags.lineLabelTag(0))
        assertEquals("invoice_paper_line_2_total_ht", InvoicePaperCanvasTags.lineTotalTag(2))
    }

    // ── Sélecteur de mode ───────────────────────────────────────────────────

    @Test
    fun theCanvasSegment_carriesTheSpecifiedButtonTag() {
        assertEquals(
            InvoiceCanvasTags.MODE_BUTTON,
            InvoiceFormTags.modeSegmentTag(InvoiceFormMode.BLANK_PAGE),
        )
    }

    /** Le segment « Mode Formulaire » n'était pas visé par l'US-23 : sa valeur est inchangée. */
    @Test
    fun theFormSegment_keepsItsHistoricalTag() {
        assertEquals(
            "invoice_form_mode_segment_CLASSIC",
            InvoiceFormTags.modeSegmentTag(InvoiceFormMode.CLASSIC),
        )
    }

    /** Deux modes qui partageraient un tag rendraient les assertions des niveaux 3 ambiguës. */
    @Test
    fun everyMode_hasADistinctSegmentTag() {
        val tags = InvoiceFormMode.entries.map { InvoiceFormTags.modeSegmentTag(it) }

        assertEquals(tags.size, tags.toSet().size, "Tags de segment dupliqués : $tags")
    }

    /** Un tag du canvas qui collisionnerait avec un tag du formulaire viserait deux nœuds. */
    @Test
    fun theCanvasTags_doNotCollideWithTheFormOnes() {
        val formTags = setOf(
            InvoiceFormTags.SCREEN,
            InvoiceFormTags.MODE_SELECTOR,
            InvoiceFormTags.TOTAL_HT,
            InvoiceFormTags.TOTAL_VAT,
            InvoiceFormTags.TOTAL_TTC,
            InvoiceFormTags.B2B_PENALTIES_CHECKBOX,
            InvoiceFormTags.LEGAL_FOOTER,
        )

        assertEquals(
            emptySet(),
            InvoiceCanvasTags.specified().toSet().intersect(formTags),
            "Un tag du canvas collisionne avec un tag du formulaire classique",
        )
    }
}
