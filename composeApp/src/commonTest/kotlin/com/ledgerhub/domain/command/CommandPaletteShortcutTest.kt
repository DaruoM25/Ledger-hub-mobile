package com.ledgerhub.domain.command

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Niveau 1 (US-19) — reconnaissance du raccourci clavier.
 *
 * Aucun niveau supérieur ne peut couvrir cette règle : ni Robolectric ni l'émulateur Pixel 5 n'ont
 * de clavier matériel. C'est précisément pourquoi elle a été extraite de la composition. Les
 * combinaisons **voisines** sont testées autant que les bonnes : un raccourci trop permissif
 * volerait des touches aux champs de saisie de l'application.
 */
class CommandPaletteShortcutTest {

    private fun triggered(
        keyLabel: String,
        ctrl: Boolean = false,
        meta: Boolean = false,
    ) = CommandPaletteShortcut.isTriggeredBy(keyLabel, ctrl, meta)

    // ── Combinaisons attendues ──────────────────────────────────────────────

    @Test
    fun ctrlK_opensThePalette() {
        assertTrue(triggered("Key: K", ctrl = true))
    }

    /** Cmd+K couvre le clavier Apple sans exiger de l'utilisateur qu'il connaisse la convention. */
    @Test
    fun metaK_opensThePalette() {
        assertTrue(triggered("Key: K", meta = true))
    }

    @Test
    fun bothModifiersTogether_stillOpenThePalette() {
        assertTrue(triggered("Key: K", ctrl = true, meta = true))
    }

    // ── Combinaisons refusées ───────────────────────────────────────────────

    /**
     * `K` seul doit rester saisissable : sans cette exclusion, taper « facture » dans n'importe
     * quel formulaire rouvrirait la palette au premier « k » rencontré.
     */
    @Test
    fun theLetterAlone_doesNothing() {
        assertFalse(triggered("Key: K"))
    }

    @Test
    fun anotherLetterWithTheModifier_doesNothing() {
        assertFalse(triggered("Key: J", ctrl = true))
        assertFalse(triggered("Key: L", meta = true))
    }

    // ── Robustesse au format d'étiquette ────────────────────────────────────

    /**
     * L'étiquette d'une touche n'a pas de format normalisé : Android rend « Key: K », d'autres
     * cibles rendent « K ». La règle ne doit pas dépendre de ce détail de plateforme.
     */
    @Test
    fun theKeyLabelFormat_doesNotMatter() {
        assertTrue(triggered("K", ctrl = true))
        assertTrue(triggered("Key: K", ctrl = true))
        assertTrue(triggered("  Key: k  ", ctrl = true))
    }

    @Test
    fun lowercaseLabels_areAccepted() {
        assertTrue(triggered("Key: k", meta = true))
    }

    @Test
    fun anEmptyLabel_doesNothing() {
        assertFalse(triggered("", ctrl = true))
        assertFalse(triggered("   ", ctrl = true))
    }
}
