package com.ledgerhub.domain.i18n

/**
 * Langue active de l'interface — miroir strict du sélecteur bilingue validé sur le Web (Prompt 2).
 * Deux langues seulement en v1 : français (défaut) et anglais.
 *
 * @property code   code ISO 639-1 (`fr` / `en`) — clé de sélection dans [AppTranslations].
 * @property flag   emoji drapeau affiché par le sélecteur de langue.
 * @property shortLabel libellé court du sélecteur (`FR` / `EN`).
 */
enum class AppLanguage(val code: String, val flag: String, val shortLabel: String) {
    FR("fr", "🇫🇷", "FR"),
    EN("en", "🇬🇧", "EN");

    /** Langue opposée — utilisé par le bouton de bascule (toggle binaire). */
    fun toggled(): AppLanguage = if (this == FR) EN else FR
}
