package com.ledgerhub.presentation.integrations

import com.ledgerhub.domain.integrations.IntegrationModule

/** Intentions du hub d'intégrations (UDF/MVI) — US-20. */
sealed interface IntegrationsHubIntent {
    /**
     * L'utilisateur touche une carte. Aucun module n'étant branché, le geste ne mène nulle part :
     * il publie un bandeau qui dit **pourquoi**. Une carte qui n'a aucune réaction au toucher se
     * lit comme une interface cassée, pas comme un module verrouillé.
     */
    data class ModuleSelected(val module: IntegrationModule) : IntegrationsHubIntent

    /** Le bandeau est acquitté — l'écran revient à son état de repos. */
    data object NoticeDismissed : IntegrationsHubIntent
}
