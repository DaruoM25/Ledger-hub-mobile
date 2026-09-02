package com.ledgerhub.presentation.export

import com.ledgerhub.domain.export.ExportFormat
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Niveau 1 (US-22) — verrouillage du contrat de tags.
 *
 * Ces neuf chaînes sont **imposées par le cahier des charges** et consommées par les niveaux 3a
 * et 3b. Les figer ici les sort du domaine de la relecture : un renommage de constante ou une
 * interpolation « améliorée » casse ce test avant de casser la QA (même parti pris que
 * `IntegrationsHubTagsTest`).
 */
class ExportModalTagsTest {

    @Test
    fun theStructuralTags_matchTheSpecification() {
        assertEquals("export_modal_dialog", ExportModalTags.DIALOG)
        assertEquals("export_date_from", ExportModalTags.DATE_FROM)
        assertEquals("export_date_to", ExportModalTags.DATE_TO)
        assertEquals("export_generate_btn", ExportModalTags.GENERATE_BTN)
        assertEquals("export_progress_bar", ExportModalTags.PROGRESS_BAR)
        assertEquals("export_download_btn", ExportModalTags.DOWNLOAD_BTN)
    }

    @Test
    fun theThreeFormatTags_matchTheSpecification() {
        assertEquals("export_format_fec", ExportModalTags.format(ExportFormat.FEC_OFFICIAL))
        assertEquals("export_format_facturx", ExportModalTags.format(ExportFormat.FACTURX_ARCHIVE))
        assertEquals("export_format_excel", ExportModalTags.format(ExportFormat.EXCEL_SUMMARY))
    }

    /** Deux formats qui partageraient un tag rendraient les assertions des niveaux 3 ambiguës. */
    @Test
    fun everyFormat_hasADistinctCardTag() {
        val tags = ExportFormat.entries.map { ExportModalTags.format(it) }

        assertEquals(tags.size, tags.toSet().size, "Tags de carte dupliqués : $tags")
    }

    /** Un tag interne qui collisionnerait avec un tag imposé viserait deux nœuds à la fois. */
    @Test
    fun theInternalTags_doNotCollideWithTheSpecifiedOnes() {
        val specified = setOf(
            ExportModalTags.DIALOG,
            ExportModalTags.DATE_FROM,
            ExportModalTags.DATE_TO,
            ExportModalTags.GENERATE_BTN,
            ExportModalTags.PROGRESS_BAR,
            ExportModalTags.DOWNLOAD_BTN,
        ) + ExportFormat.entries.map { ExportModalTags.format(it) }
        val internal = listOf(
            ExportModalTags.TRIGGER,
            ExportModalTags.SCRIM,
            ExportModalTags.SUCCESS,
            ExportModalTags.PERIOD_ERROR,
        )

        assertEquals(9, specified.size, "Le cahier des charges US-22 impose exactement 9 tags")
        assertEquals(internal.size, internal.toSet().size, "Tags internes dupliqués : $internal")
        assertEquals(
            emptySet(),
            specified.intersect(internal.toSet()),
            "Un tag interne collisionne avec un tag imposé",
        )
    }
}
