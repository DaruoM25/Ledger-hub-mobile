package com.ledgerhub.domain.command

import com.ledgerhub.domain.i18n.AppLanguage
import com.ledgerhub.domain.i18n.AppTranslations

/**
 * Filtrage temps réel des actions de la palette (US-19).
 *
 * Fonction **pure** : aucune composition, aucun état. Le comportement de recherche d'une palette
 * de commandes est ce qui décide de son utilité, et il se teste ligne à ligne — pas à travers un
 * arbre sémantique.
 *
 * ## Ce que « correspondre » veut dire
 *
 * La correspondance porte sur le libellé traduit **et** sur les mots-clés, en sous-chaîne : taper
 * « fact » doit déjà proposer la création de facture, sans quoi il faut connaître le mot entier
 * pour le trouver.
 *
 * Elle est insensible à la casse **et aux accents** — on tape « echeance » sans accent, à plus
 * forte raison sur un clavier logiciel pressé. `java.text.Normalizer` étant absent de commonMain,
 * les diacritiques du français sont repliées à la main : la table est courte et figée, là où une
 * dépendance de normalisation Unicode complète serait hors de proportion.
 *
 * Une requête vide rend **toutes** les actions : la palette qui s'ouvre doit montrer ce qu'elle
 * sait faire, pas un vide à remplir.
 */
object CommandActionFilter {

    fun filter(query: String, language: AppLanguage): List<CommandAction> {
        val needle = query.normalizeForSearch()
        if (needle.isEmpty()) return CommandAction.entries.toList()

        return CommandAction.entries.filter { action ->
            val label = AppTranslations.get(action.labelKey, language).normalizeForSearch()
            label.contains(needle) ||
                action.keywords(language).any { it.normalizeForSearch().contains(needle) }
        }
    }

    /** Minuscules, sans accents, sans espaces superflus. */
    private fun String.normalizeForSearch(): String =
        trim().lowercase().map { DIACRITICS[it] ?: it }.joinToString("")

    /**
     * Repli des diacritiques du français. Volontairement limité à ce que la langue de l'interface
     * peut produire : une table Unicode complète n'apporterait rien à un dictionnaire FR/EN.
     */
    private val DIACRITICS: Map<Char, Char> = mapOf(
        'à' to 'a', 'â' to 'a', 'ä' to 'a',
        'é' to 'e', 'è' to 'e', 'ê' to 'e', 'ë' to 'e',
        'î' to 'i', 'ï' to 'i',
        'ô' to 'o', 'ö' to 'o',
        'ù' to 'u', 'û' to 'u', 'ü' to 'u',
        'ç' to 'c',
        'ÿ' to 'y',
    )
}
