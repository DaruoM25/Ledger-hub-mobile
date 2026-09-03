package com.ledgerhub.presentation.theme

import com.ledgerhub.presentation.components.ThemeToggleTags
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Niveau 1 (US-25) — tags du bouton de bascule, **figés en dur**.
 *
 * `theme_toggle_btn` est imposé par le cahier des charges : c'est le nom sur lequel s'appuient les
 * niveaux 3, le script QA et toute automatisation ultérieure. Comparer la constante à elle-même ne
 * prouverait rien ; c'est bien la forme littérale qui est affirmée ici, de sorte qu'un renommage
 * « inoffensif » du symbole Kotlin ne puisse pas passer inaperçu.
 */
class ThemeToggleTagsTest {

    @Test
    fun theRootTag_matchesTheSpecification() {
        assertEquals("theme_toggle_btn", ThemeToggleTags.ROOT)
    }

    @Test
    fun theIconTags_areStable() {
        assertEquals("theme_toggle_sun", ThemeToggleTags.ICON_SUN)
        assertEquals("theme_toggle_moon", ThemeToggleTags.ICON_MOON)
    }
}
