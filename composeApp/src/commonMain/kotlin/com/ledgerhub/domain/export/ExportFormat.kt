package com.ledgerhub.domain.export

import com.ledgerhub.domain.i18n.StringKey

/**
 * Formats de sortie de l'export comptable (US-22).
 *
 * Trois formats, et trois destinataires distincts : le FEC part au vérificateur, l'archive
 * Factur-X part au cabinet, la synthèse part dans un tableur. Ce n'est pas la même pièce à trois
 * habillages — chacun a son extension et son type MIME, portés ici et non déduits à l'affichage.
 *
 * L'ordre de déclaration **est** l'ordre d'affichage : le FEC vient en tête parce que c'est la
 * pièce opposable, pas parce qu'il est le premier alphabétiquement (voir [ordered]).
 */
enum class ExportFormat(
    val id: String,
    val titleKey: StringKey,
    val descriptionKey: StringKey,
    val glyph: String,
    val fileExtension: String,
    val mimeType: String,
) {
    /** Fichier des écritures comptables — article A.47 A-1 du LPF, fichier plat tabulé. */
    FEC_OFFICIAL(
        id = "fec",
        titleKey = StringKey.EXPORT_FORMAT_FEC_TITLE,
        descriptionKey = StringKey.EXPORT_FORMAT_FEC_DESC,
        glyph = "📒",
        fileExtension = "txt",
        mimeType = "text/plain",
    ),

    /** Recueil des pièces Factur-X de la période, dans un document pivot unique. */
    FACTURX_ARCHIVE(
        id = "facturx",
        titleKey = StringKey.EXPORT_FORMAT_FACTURX_TITLE,
        descriptionKey = StringKey.EXPORT_FORMAT_FACTURX_DESC,
        glyph = "🗂️",
        fileExtension = "xml",
        mimeType = "application/xml",
    ),

    /** Synthèse tabulaire — CSV point-virgule, le seul format qu'Excel ouvre en locale française. */
    EXCEL_SUMMARY(
        id = "excel",
        titleKey = StringKey.EXPORT_FORMAT_EXCEL_TITLE,
        descriptionKey = StringKey.EXPORT_FORMAT_EXCEL_DESC,
        glyph = "📊",
        fileExtension = "csv",
        mimeType = "text/csv",
    );

    companion object {
        /** Format proposé à l'ouverture de la modale — la pièce opposable, par défaut. */
        val Default: ExportFormat = FEC_OFFICIAL

        /** Catalogue ordonné — décision de domaine, pas tri d'affichage (cf. `IntegrationCatalog`). */
        fun ordered(): List<ExportFormat> = entries.toList()
    }
}
