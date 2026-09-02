package com.ledgerhub.domain.export

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Niveau 1 (US-22) — catalogue des formats d'export.
 *
 * L'ordre et le nombre sont imposés par le cahier des charges ; l'extension et le type MIME
 * décident, eux, de l'application qui s'ouvrira chez le destinataire. Trois formats qui
 * partageraient un nom de fichier se recouvriraient dans le dossier de téléchargement.
 */
class ExportFormatTest {

    @Test
    fun theCatalog_holdsExactlyTheThreeRequiredFormats() {
        assertEquals(
            listOf(
                ExportFormat.FEC_OFFICIAL,
                ExportFormat.FACTURX_ARCHIVE,
                ExportFormat.EXCEL_SUMMARY,
            ),
            ExportFormat.ordered(),
        )
    }

    /** Le FEC est la pièce opposable : c'est elle que la modale propose d'emblée. */
    @Test
    fun theDefaultFormat_isTheOfficialFec() {
        assertEquals(ExportFormat.FEC_OFFICIAL, ExportFormat.Default)
    }

    @Test
    fun everyFormat_carriesItsOwnExtensionAndMimeType() {
        assertEquals("txt", ExportFormat.FEC_OFFICIAL.fileExtension)
        assertEquals("text/plain", ExportFormat.FEC_OFFICIAL.mimeType)
        assertEquals("xml", ExportFormat.FACTURX_ARCHIVE.fileExtension)
        assertEquals("application/xml", ExportFormat.FACTURX_ARCHIVE.mimeType)
        assertEquals("csv", ExportFormat.EXCEL_SUMMARY.fileExtension)
        assertEquals("text/csv", ExportFormat.EXCEL_SUMMARY.mimeType)
    }

    @Test
    fun everyFormat_hasADistinctIdentityAndItsOwnLabels() {
        val ids = ExportFormat.entries.map { it.id }
        val titles = ExportFormat.entries.map { it.titleKey }
        val descriptions = ExportFormat.entries.map { it.descriptionKey }

        assertEquals(ids.size, ids.toSet().size, "Identifiants dupliqués : $ids")
        assertEquals(titles.size, titles.toSet().size, "Clés de titre dupliquées : $titles")
        assertEquals(
            descriptions.size,
            descriptions.toSet().size,
            "Clés de description dupliquées : $descriptions",
        )
        assertTrue(
            ExportFormat.entries.all { it.glyph.isNotBlank() },
            "Un format sans glyphe se lirait comme une carte inachevée",
        )
    }
}
