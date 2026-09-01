package com.ledgerhub.presentation.integrations

import com.ledgerhub.domain.integrations.IntegrationCatalog
import com.ledgerhub.domain.integrations.IntegrationModule

/**
 * État immuable du hub d'intégrations (US-20).
 *
 * [modules] vient du catalogue **déjà ordonné** : l'ordre est une décision de domaine
 * (`IntegrationCatalog.ordered`), pas un tri improvisé à l'affichage.
 *
 * @param noticeModule module dont le bandeau « verrouillé » est affiché, `null` au repos.
 */
data class IntegrationsHubUiState(
    val modules: List<IntegrationModule> = IntegrationCatalog.ordered(),
    val noticeModule: IntegrationModule? = null,
) {
    /** Aucun module n'est ouvert dans cette version : la propriété fige l'invariant, et le teste. */
    val hasUnlockedModule: Boolean get() = false
}
