package com.ledgerhub.domain.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Niveau 1 (US-25) — machine à états du thème, en Kotlin pur.
 *
 * Aucune dépendance à Compose : c'est précisément pourquoi la règle de bascule vit dans le domaine.
 * L'apparence du système est un paramètre, jamais une lecture d'environnement — les deux
 * configurations se jouent donc ici, sans émulateur et sans Robolectric.
 */
class ThemeModeTest {

    // ── Résolution ──────────────────────────────────────────────────────────

    @Test
    fun explicitModes_ignoreTheSystemAppearance() {
        assertEquals(ResolvedTheme.DARK, ThemeMode.DARK.resolve(systemIsDark = false))
        assertEquals(ResolvedTheme.DARK, ThemeMode.DARK.resolve(systemIsDark = true))
        assertEquals(ResolvedTheme.LIGHT, ThemeMode.LIGHT.resolve(systemIsDark = false))
        assertEquals(ResolvedTheme.LIGHT, ThemeMode.LIGHT.resolve(systemIsDark = true))
    }

    @Test
    fun systemMode_followsTheSystemAppearance() {
        assertEquals(ResolvedTheme.DARK, ThemeMode.SYSTEM.resolve(systemIsDark = true))
        assertEquals(ResolvedTheme.LIGHT, ThemeMode.SYSTEM.resolve(systemIsDark = false))
    }

    // ── Cycles de bascule ───────────────────────────────────────────────────

    /** Le cycle exigé par le cahier des charges : DARK → LIGHT → DARK. */
    @Test
    fun toggling_cyclesBetweenDarkAndLight() {
        val once = ThemeMode.DARK.toggled()
        val twice = once.toggled()

        assertEquals(ThemeMode.LIGHT, once)
        assertEquals(ThemeMode.DARK, twice)
    }

    /** Quatre appuis ramènent au point de départ, quel que soit le mode initial explicite. */
    @Test
    fun togglingFourTimes_returnsToTheInitialMode() {
        listOf(ThemeMode.DARK, ThemeMode.LIGHT).forEach { start ->
            val after = start.toggled().toggled().toggled().toggled()
            assertEquals(start, after, "Le cycle de bascule n'est pas stable depuis $start")
        }
    }

    /**
     * Depuis `SYSTEM`, l'appui prend l'inverse de l'apparence **effective**. Retomber sur une
     * valeur fixe ferait, la moitié du temps, un bouton de bascule qui ne change rien à l'écran.
     */
    @Test
    fun togglingFromSystem_alwaysChangesTheAppearance() {
        listOf(true, false).forEach { systemIsDark ->
            val before = ThemeMode.SYSTEM.resolve(systemIsDark)
            val after = ThemeMode.SYSTEM.toggled(systemIsDark).resolve(systemIsDark)

            assertNotEquals(
                before,
                after,
                "Depuis SYSTEM avec systemIsDark=$systemIsDark, l'appui n'a rien changé à l'écran",
            )
        }
    }

    @Test
    fun togglingFromSystem_yieldsAnExplicitMode() {
        assertEquals(ThemeMode.LIGHT, ThemeMode.SYSTEM.toggled(systemIsDark = true))
        assertEquals(ThemeMode.DARK, ThemeMode.SYSTEM.toggled(systemIsDark = false))
    }

    // ── Persistance ─────────────────────────────────────────────────────────

    @Test
    fun defaultMode_isTheHistoricalDarkTheme() {
        assertEquals(ThemeMode.DARK, ThemeMode.Default)
    }

    @Test
    fun everyMode_survivesARoundTripThroughStorage() {
        ThemeMode.entries.forEach { mode ->
            assertEquals(mode, ThemeMode.fromStorage(mode.name))
        }
    }

    /** Une constante renommée entre deux versions ne doit pas empêcher l'app de démarrer. */
    @Test
    fun unknownOrAbsentStoredValues_fallBackToTheDefault() {
        assertEquals(ThemeMode.Default, ThemeMode.fromStorage(null))
        assertEquals(ThemeMode.Default, ThemeMode.fromStorage(""))
        assertEquals(ThemeMode.Default, ThemeMode.fromStorage("SEPIA"))
        assertEquals(ThemeMode.Default, ThemeMode.fromStorage("dark"))
    }

    /**
     * Un quatrième mode devra déclarer sa résolution et sa bascule pour que ce test passe — la
     * liste est figée ici, et non déduite de l'enum, faute de quoi il l'accepterait en silence.
     */
    @Test
    fun theSetOfModes_isFrozen() {
        assertEquals(
            listOf(ThemeMode.DARK, ThemeMode.LIGHT, ThemeMode.SYSTEM),
            ThemeMode.entries.toList(),
        )
        assertEquals(listOf(ResolvedTheme.DARK, ResolvedTheme.LIGHT), ResolvedTheme.entries.toList())
    }
}
