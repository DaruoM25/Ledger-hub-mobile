package com.ledgerhub.presentation.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.ledgerhub.domain.i18n.AppLanguage
import com.ledgerhub.domain.i18n.AppTranslations
import com.ledgerhub.domain.i18n.StringKey
import com.ledgerhub.domain.theme.ThemeMode
import com.ledgerhub.presentation.components.ThemeToggle
import com.ledgerhub.presentation.components.ThemeToggleTags
import com.ledgerhub.presentation.i18n.LocalAppLanguage
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Niveau 3a (US-25) — rendu du bouton de bascule, clic, et **palette Material 3 effectivement
 * appliquée**.
 *
 * ## Ce qui est réellement affirmé
 *
 * Pas seulement que l'état a changé — cela, le niveau 2 le prouve déjà sans interface. Ici, une
 * sonde placée sous [LedgerHubTheme] relève `MaterialTheme.colorScheme` **après** le clic : ce qui
 * est vérifié, c'est que l'arbre a bien été recomposé avec l'autre `ColorScheme`, et que ce
 * `ColorScheme` porte les teintes de [LedgerHubPalette.Light].
 *
 * ## Déterminisme
 *
 * Sous Robolectric, `isSystemInDarkTheme()` vaut `false`. Tous les cas ci-dessous partent donc d'un
 * mode **explicite** (`DARK` ou `LIGHT`) : `SYSTEM` dépendrait de l'environnement, et c'est
 * `ThemeModeTest` (niveau 1) qui le couvre, avec l'apparence système injectée.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalTestApi::class)
class ThemeToggleRobolectricTest {

    /** Ce que la sonde relève de l'arbre — le thème tel qu'un écran le lirait réellement. */
    private class ThemeProbe {
        var background: Color = Color.Unspecified
        var onBackground: Color = Color.Unspecified
        var surface: Color = Color.Unspecified
        var statusPaidBg: Color = Color.Unspecified
    }

    private fun tr(key: StringKey, language: AppLanguage = AppLanguage.FR) =
        AppTranslations.get(key, language)

    // ── Rendu ───────────────────────────────────────────────────────────────

    @Test
    fun theToggle_isDisplayedWithASensibleTouchTarget() = runComposeUiTest {
        setContent {
            LedgerHubTheme(mode = ThemeMode.DARK) {
                ThemeToggle(mode = ThemeMode.DARK, resolved = LedgerHubTheme.resolved, onToggle = {})
            }
        }

        onNodeWithTag(ThemeToggleTags.ROOT).assertIsDisplayed()
        onNodeWithTag(ThemeToggleTags.ROOT).assertHeightIsAtLeast(48.dp)
        onNodeWithTag(ThemeToggleTags.ROOT).assertWidthIsAtLeast(48.dp)
    }

    /**
     * **Non-compression de la cible tactile.** Le bouton est placé dans une ligne délibérément trop
     * étroite, à côté d'un voisin encombrant — la situation exacte de l'en-tête compact, où il a
     * été livré comprimé à 14,5 dp de large sur Pixel 5.
     *
     * Cette propriété-là, Robolectric la mesure fidèlement : elle ne dépend pas des métriques de
     * police (simulées, donc optimistes ici) mais des contraintes de mise en page, que
     * `requiredSizeIn` fait ignorer au parent. La largeur réelle de l'en-tête garni, elle, reste du
     * ressort du niveau 3b.
     */
    @Test
    fun theToggle_keepsItsTouchTargetInsideACrampedRow() = runComposeUiTest {
        setContent {
            LedgerHubTheme(mode = ThemeMode.DARK) {
                Row(modifier = Modifier.width(120.dp)) {
                    Box(modifier = Modifier.width(100.dp).height(40.dp))
                    ThemeToggle(mode = ThemeMode.DARK, resolved = LedgerHubTheme.resolved, onToggle = {})
                }
            }
        }

        onNodeWithTag(ThemeToggleTags.ROOT).assertWidthIsAtLeast(48.dp)
        onNodeWithTag(ThemeToggleTags.ROOT).assertHeightIsAtLeast(48.dp)
    }

    /**
     * L'icône annonce la **destination** de l'appui, pas l'état courant : soleil en thème sombre,
     * lune en thème clair. C'est la convention retenue pour un bouton d'action.
     */
    @Test
    fun theIcon_announcesWhereTheTapLeads() = runComposeUiTest {
        setContent {
            LedgerHubTheme(mode = ThemeMode.DARK) {
                ThemeToggle(mode = ThemeMode.DARK, resolved = LedgerHubTheme.resolved, onToggle = {})
            }
        }

        onNodeWithTag(ThemeToggleTags.ICON_SUN, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun inLightTheme_theIconIsTheMoon() = runComposeUiTest {
        setContent {
            LedgerHubTheme(mode = ThemeMode.LIGHT) {
                ThemeToggle(mode = ThemeMode.LIGHT, resolved = LedgerHubTheme.resolved, onToggle = {})
            }
        }

        onNodeWithTag(ThemeToggleTags.ICON_MOON, useUnmergedTree = true).assertIsDisplayed()
    }

    // ── Accessibilité bilingue ──────────────────────────────────────────────

    /**
     * Le bouton n'a pas de libellé : sa description est tout ce que TalkBack énonce. Elle doit
     * donc suivre la langue active, au même titre que le reste de l'en-tête.
     */
    @Test
    fun theContentDescription_followsTheActiveLanguage() = runComposeUiTest {
        var language by mutableStateOf(AppLanguage.FR)
        setContent {
            CompositionLocalProvider(LocalAppLanguage provides language) {
                LedgerHubTheme(mode = ThemeMode.DARK) {
                    ThemeToggle(mode = ThemeMode.DARK, resolved = LedgerHubTheme.resolved, onToggle = {})
                }
            }
        }

        onNodeWithTag(ThemeToggleTags.ROOT, useUnmergedTree = false)
            .assertContentDescriptionContains(tr(StringKey.THEME_TOGGLE_TO_LIGHT))

        language = AppLanguage.EN
        waitForIdle()

        onNodeWithTag(ThemeToggleTags.ROOT, useUnmergedTree = false)
            .assertContentDescriptionContains(tr(StringKey.THEME_TOGGLE_TO_LIGHT, AppLanguage.EN))
    }

    // ── Bascule de la palette Material 3 active ─────────────────────────────

    /**
     * Le cœur de l'US : un clic repeint le thème. La sonde lit `MaterialTheme.colorScheme` sous
     * [LedgerHubTheme] — c'est-à-dire ce que voit n'importe quel écran de l'application.
     */
    @Test
    fun clicking_swapsTheActiveMaterialPalette() = runComposeUiTest {
        val probe = ThemeProbe()
        var mode by mutableStateOf(ThemeMode.DARK)
        setContent {
            LedgerHubTheme(mode = mode) {
                probe.background = MaterialTheme.colorScheme.background
                probe.onBackground = MaterialTheme.colorScheme.onBackground
                probe.surface = MaterialTheme.colorScheme.surface
                probe.statusPaidBg = LedgerHubTheme.palette.StatusPaidBg
                ThemeToggle(mode = mode, resolved = LedgerHubTheme.resolved, onToggle = { mode = it })
            }
        }

        assertEquals(LedgerHubPalette.Dark.Background, probe.background)
        assertEquals(LedgerHubPalette.Dark.PrimaryText, probe.onBackground)
        assertEquals(LedgerHubPalette.Dark.StatusPaidBg, probe.statusPaidBg)

        onNodeWithTag(ThemeToggleTags.ROOT).performClick()
        waitForIdle()

        assertEquals(ThemeMode.LIGHT, mode)
        assertEquals(LedgerHubPalette.Light.Background, probe.background)
        assertEquals(LedgerHubPalette.Light.PrimaryText, probe.onBackground)
        assertEquals(LedgerHubPalette.Light.Surface, probe.surface)
        // Les tokens propres au produit basculent avec les rôles Material — c'est ce qui empêche
        // qu'un badge de statut reste sombre au milieu d'un écran clair.
        assertEquals(LedgerHubPalette.Light.StatusPaidBg, probe.statusPaidBg)
    }

    /** Le cycle exigé par le cahier des charges, vu depuis la palette effective : DARK → LIGHT → DARK. */
    @Test
    fun clickingTwice_returnsToTheDarkPalette() = runComposeUiTest {
        val probe = ThemeProbe()
        var mode by mutableStateOf(ThemeMode.DARK)
        setContent {
            LedgerHubTheme(mode = mode) {
                probe.background = MaterialTheme.colorScheme.background
                ThemeToggle(mode = mode, resolved = LedgerHubTheme.resolved, onToggle = { mode = it })
            }
        }

        onNodeWithTag(ThemeToggleTags.ROOT).performClick()
        waitForIdle()
        assertEquals(LedgerHubPalette.Light.Background, probe.background)

        onNodeWithTag(ThemeToggleTags.ROOT).performClick()
        waitForIdle()

        assertEquals(ThemeMode.DARK, mode)
        assertEquals(LedgerHubPalette.Dark.Background, probe.background)
    }

    /**
     * Les deux déclinaisons doivent être **distinctes** sur chaque token. Une palette claire
     * recopiée par mégarde sur la sombre passerait tous les tests ci-dessus.
     */
    @Test
    fun theTwoPalettes_differOnEveryVisibleToken() {
        val dark = LedgerHubPalette.Dark
        val light = LedgerHubPalette.Light

        val identical = listOf(
            "Background" to (dark.Background == light.Background),
            "Surface" to (dark.Surface == light.Surface),
            "PrimaryText" to (dark.PrimaryText == light.PrimaryText),
            "SecondaryText" to (dark.SecondaryText == light.SecondaryText),
            "Border" to (dark.Border == light.Border),
            "StatusPaidBg" to (dark.StatusPaidBg == light.StatusPaidBg),
            "StatusPendingBg" to (dark.StatusPendingBg == light.StatusPendingBg),
            "StatusDraftBg" to (dark.StatusDraftBg == light.StatusDraftBg),
            "QuotePendingBg" to (dark.QuotePendingBg == light.QuotePendingBg),
        ).filter { it.second }.map { it.first }

        assertEquals(emptyList(), identical, "Tokens non déclinés entre sombre et clair")
        // L'accent, lui, est volontairement identique : couleur de marque, pas d'ambiance.
        assertEquals(dark.Accent, light.Accent)
    }
}
