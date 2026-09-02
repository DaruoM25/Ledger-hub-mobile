package com.ledgerhub.presentation.export

import com.ledgerhub.domain.export.ExportFormat

/** Intentions de la modale d'export comptable (UDF/MVI) — US-22. */
sealed interface ExportIntent {

    /** Frappe dans le champ « Du ». */
    data class DateFromChanged(val value: String) : ExportIntent

    /** Frappe dans le champ « Au ». */
    data class DateToChanged(val value: String) : ExportIntent

    /** Sélection d'une des trois cartes de format. */
    data class FormatSelected(val format: ExportFormat) : ExportIntent

    /** Bouton « Générer l'archive ». */
    data object GenerateRequested : ExportIntent

    /** Bouton « Télécharger l'archive (.zip) » — remise du document à la plateforme. */
    data object DownloadRequested : ExportIntent

    /**
     * Fermeture de la modale. Distincte d'un simple retrait de l'écran : une génération en vol
     * doit être annulée, sans quoi elle publierait son archive dans une modale déjà refermée.
     */
    data object Dismissed : ExportIntent
}
