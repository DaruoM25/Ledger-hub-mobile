package com.ledgerhub.domain.invoice

/** Taux de TVA français en vigueur. Valeurs en points de base — voir [vatFor]. */
enum class VatRate(val basisPoints: Int, val label: String) {
    TAUX_NORMAL(2000, "20 %"),
    TAUX_INTERMEDIAIRE(1000, "10 %"),
    TAUX_REDUIT(550, "5,5 %"),
    TAUX_PARTICULIER(210, "2,1 %"),
    EXONERE(0, "Exonéré"),
}
