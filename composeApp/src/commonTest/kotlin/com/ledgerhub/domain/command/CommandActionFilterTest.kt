package com.ledgerhub.domain.command

import com.ledgerhub.domain.i18n.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Niveau 1 (US-19) — filtrage de la palette de commandes.
 *
 * C'est le comportement qui décide de l'utilité de la palette : une recherche qui exige le mot
 * exact ne sert à rien, puisqu'il faudrait déjà savoir ce qu'on cherche. Ces tests figent donc
 * autant la tolérance de la recherche que ses résultats.
 */
class CommandActionFilterTest {

    private fun filter(query: String, language: AppLanguage = AppLanguage.FR) =
        CommandActionFilter.filter(query, language)

    // ── Requête vide ────────────────────────────────────────────────────────

    /** Une palette qui s'ouvre montre ce qu'elle sait faire, elle n'attend pas qu'on devine. */
    @Test
    fun anEmptyQuery_offersEveryAction() {
        assertEquals(CommandAction.entries.toList(), filter(""))
        assertEquals(CommandAction.entries.toList(), filter("   "))
    }

    // ── Correspondance par libellé ──────────────────────────────────────────

    @Test
    fun aPartialWord_alreadyMatches() {
        // « fact » doit suffire : personne ne tape le libellé entier d'une commande.
        assertTrue(CommandAction.CREATE_INVOICE in filter("fact"))
    }

    @Test
    fun theSearchIsCaseInsensitive() {
        assertEquals(filter("facture"), filter("FACTURE"))
        assertEquals(filter("facture"), filter("FaCtUrE"))
    }

    /**
     * Sur un clavier logiciel, personne ne pose les accents. « echeance » doit trouver ce que
     * « échéance » trouve — sans quoi la recherche échoue précisément quand on va vite.
     */
    @Test
    fun theSearchIgnoresDiacritics() {
        assertEquals(filter("échéance"), filter("echeance"))
        assertTrue(CommandAction.REMIND_OVERDUE in filter("echeance"))
        assertTrue(CommandAction.EXPORT_ACCOUNTING in filter("comptabilite"))
    }

    @Test
    fun surroundingSpaces_areIgnored() {
        assertEquals(filter("retard"), filter("  retard  "))
    }

    // ── Correspondance par mots-clés ────────────────────────────────────────

    /**
     * Les mots-clés couvrent le vocabulaire que l'utilisateur emploie sans qu'il figure dans le
     * libellé : « impayé » ne s'y trouve pas, c'est pourtant le terme le plus naturel.
     */
    @Test
    fun keywordsAbsentFromTheLabel_stillMatch() {
        assertTrue(CommandAction.REMIND_OVERDUE in filter("impaye"))
        assertTrue(CommandAction.EXPORT_ACCOUNTING in filter("csv"))
        assertTrue(CommandAction.CREATE_INVOICE in filter("emettre"))
    }

    // ── Absence de résultat ─────────────────────────────────────────────────

    @Test
    fun anUnknownQuery_matchesNothing() {
        assertTrue(filter("zzzzz").isEmpty())
    }

    // ── Bilinguisme ─────────────────────────────────────────────────────────

    /** En anglais, la recherche porte sur le dictionnaire anglais — libellés comme mots-clés. */
    @Test
    fun inEnglish_theSearchUsesTheEnglishVocabulary() {
        assertTrue(CommandAction.REMIND_OVERDUE in filter("overdue", AppLanguage.EN))
        assertTrue(CommandAction.EXPORT_ACCOUNTING in filter("ledger", AppLanguage.EN))
        assertTrue(CommandAction.CREATE_INVOICE in filter("invoice", AppLanguage.EN))
    }

    /**
     * Le terme d'une langue ne doit pas ressortir dans l'autre : « impaye » n'a aucun sens pour un
     * utilisateur anglophone, et le proposer trahirait un dictionnaire mal cloisonné.
     */
    @Test
    fun aFrenchOnlyKeyword_doesNotLeakIntoEnglish() {
        assertTrue(filter("impaye", AppLanguage.EN).isEmpty())
    }

    // ── Ordre ───────────────────────────────────────────────────────────────

    /** L'ordre de déclaration est celui de l'écran : il ne doit pas dépendre du filtrage. */
    @Test
    fun resultsKeepTheDeclarationOrder() {
        val results = filter("facture")

        assertEquals(results.sortedBy { it.ordinal }, results)
    }
}
