package com.ledgerhub.domain.degraded

/**
 * État de simulation réseau pour le mode dégradé DGFiP 2026 (US-29).
 */
enum class DegradedModeNetworkState {
    /** Connectivité PPF/PDP normale. */
    OPERATIONAL,

    /** Incident technique PPF/PDP simulé — déclenche le mode dégradé de secours. */
    OUTAGE;

    val isOutage: Boolean get() = this == OUTAGE
}
