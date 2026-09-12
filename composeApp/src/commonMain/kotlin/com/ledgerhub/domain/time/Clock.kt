package com.ledgerhub.domain.time

import kotlinx.datetime.Clock as KotlinxClock

/**
 * Source de temps du domaine.
 *
 * Injectée plutôt que statique : une piste d'audit ne se teste pas sérieusement contre l'heure
 * réelle, et un horodatage fourni par l'appelant serait falsifiable — donc incompatible avec la
 * *fiabilité* attendue de la PAF. Le double [FixedClock] rend les tests déterministes sans
 * ouvrir la moindre porte côté production.
 */
interface Clock {
    /** Instant courant en ISO 8601 UTC, ex. `2026-08-29T14:33:07.512Z`. */
    fun nowIso(): String

    /** Horodatage courant en millisecondes depuis l'époque Unix. */
    fun nowEpochMillis(): Long = kotlinx.datetime.Instant.parse(nowIso()).toEpochMilliseconds()
}

/** Horloge réelle — `kotlinx-datetime`, adoptée en US-07 (la v1 s'en passait). */
object SystemClock : Clock {
    override fun nowIso(): String = KotlinxClock.System.now().toString()
    override fun nowEpochMillis(): Long = KotlinxClock.System.now().toEpochMilliseconds()
}

/**
 * Horloge figée pour les tests. [advanceBy] permet d'ordonner plusieurs traces d'audit sans
 * dépendre de la vitesse d'exécution.
 */
class FixedClock(private var instant: kotlinx.datetime.Instant) : Clock {
    constructor(iso: String) : this(kotlinx.datetime.Instant.parse(iso))

    override fun nowIso(): String = instant.toString()
    override fun nowEpochMillis(): Long = instant.toEpochMilliseconds()

    fun advanceBy(seconds: Long) {
        instant = instant.plus(kotlin.time.Duration.parse("${seconds}s"))
    }
}
