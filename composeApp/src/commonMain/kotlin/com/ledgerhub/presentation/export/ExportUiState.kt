package com.ledgerhub.presentation.export

import com.ledgerhub.domain.export.AccountingArchive
import com.ledgerhub.domain.export.ExportFormat
import com.ledgerhub.domain.export.ExportPeriod
import com.ledgerhub.domain.export.ExportPeriodError

/**
 * Étape de la génération d'une archive comptable (US-22).
 *
 * Trois états, et un seul chemin entre eux : `IDLE` → `GENERATING` → `READY`. Un quatrième état
 * d'erreur serait inutile — une période invalide n'entre jamais en génération, elle reste à
 * `IDLE` avec son motif de rejet affiché sous le champ fautif.
 */
enum class ExportStage {
    /** Au repos : la période et le format se règlent, rien n'est encore produit. */
    IDLE,

    /** Compression simulée en cours — la barre de progression avance. */
    GENERATING,

    /** Archive disponible : elle n'attend plus que d'être remise à la plateforme. */
    READY,
}

/**
 * État immuable de la modale d'export comptable (US-22).
 *
 * [periodError] est **dérivé** et non stocké : deux sources de vérité sur la validité d'une même
 * période finiraient par se contredire, typiquement après une frappe qui met l'une à jour et pas
 * l'autre.
 *
 * @param progress avancement dans `[0f, 1f]`. Déterminé, jamais indéterminé : c'est ce qui rend la
 *   barre observable par les tests, une animation infinie empêchant `waitForIdle()` de rendre la
 *   main sous Robolectric.
 */
data class ExportUiState(
    val period: ExportPeriod = ExportPeriod(from = "", to = ""),
    val selectedFormat: ExportFormat = ExportFormat.Default,
    val stage: ExportStage = ExportStage.IDLE,
    val progress: Float = 0f,
    val archive: AccountingArchive? = null,
    val isPro: Boolean = true,
) {
    val periodError: ExportPeriodError? get() = period.validate()

    val isGenerating: Boolean get() = stage == ExportStage.GENERATING

    val isReady: Boolean get() = stage == ExportStage.READY

    val isFecLocked: Boolean get() = selectedFormat == ExportFormat.FEC_OFFICIAL && !isPro

    /** Une génération ne se lance ni sur une période invalide, ni par-dessus une autre en cours, ni sur un format Pro verrouillé. */
    val isGenerateEnabled: Boolean get() = stage == ExportStage.IDLE && periodError == null && !isFecLocked
}
