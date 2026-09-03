package com.ledgerhub.domain.theme

/**
 * Apparence **effective** de l'interface — ce que l'écran affiche réellement.
 *
 * Distincte de [ThemeMode] à dessein : `SYSTEM` est une *préférence*, jamais une apparence. Aucun
 * `ColorScheme` ne peut être choisi à partir de `SYSTEM` seul, et ce type rend cette impossibilité
 * structurelle plutôt que documentaire.
 */
enum class ResolvedTheme { DARK, LIGHT }

/**
 * Préférence de thème de l'utilisateur — miroir strict du sélecteur clair/sombre validé sur le Web
 * (US-25). Persistée telle quelle (voir `ThemePreferenceRepository`) : c'est bien le choix
 * `SYSTEM` qui se mémorise, pas l'apparence qu'il donnait le jour où il a été fait.
 *
 * Toute la règle de bascule vit ici, dans le domaine, et non dans le composable : c'est le seul
 * moyen de la couvrir par un test pur (même raisonnement que
 * [CommandPaletteShortcut][com.ledgerhub.domain.command.CommandPaletteShortcut], dont la règle de
 * reconnaissance clavier est hors de portée de Robolectric comme de l'émulateur).
 *
 * L'obscurité du système est **injectée** (`systemIsDark`) et jamais lue ici : le domaine ignore
 * Compose, et un test doit pouvoir jouer les deux environnements sans émulateur.
 */
enum class ThemeMode {
    DARK,
    LIGHT,
    SYSTEM;

    /** Apparence effective de ce mode dans un environnement donné. */
    fun resolve(systemIsDark: Boolean): ResolvedTheme = when (this) {
        DARK -> ResolvedTheme.DARK
        LIGHT -> ResolvedTheme.LIGHT
        SYSTEM -> if (systemIsDark) ResolvedTheme.DARK else ResolvedTheme.LIGHT
    }

    /**
     * Mode obtenu en appuyant sur le bouton de bascule.
     *
     * Depuis [SYSTEM], on ne retombe pas sur une valeur arbitraire : on prend l'**inverse de
     * l'apparence effective**. Sans quoi un appui sur le bouton, en journée avec un système clair,
     * rendrait `LIGHT` — c'est-à-dire ne changerait rien à l'écran. Un bouton de bascule qui ne
     * bascule pas est un défaut, pas un cas limite.
     */
    fun toggled(systemIsDark: Boolean = false): ThemeMode =
        when (resolve(systemIsDark)) {
            ResolvedTheme.DARK -> LIGHT
            ResolvedTheme.LIGHT -> DARK
        }

    companion object {
        /**
         * Thème par défaut — le sombre premium, seule apparence que l'app ait connue jusqu'à
         * l'US-25. Une base vierge ou une valeur illisible y retombe : la bascule est une
         * nouveauté, pas une remise en cause de l'identité visuelle.
         */
        val Default: ThemeMode = DARK

        /**
         * Relecture d'une valeur persistée. Une constante renommée entre deux versions retombe sur
         * [Default] plutôt que de faire échouer le chargement des préférences — même règle que le
         * repli de `VatRate` dans `SqlDelightTaxSettingsRepository`.
         */
        fun fromStorage(raw: String?): ThemeMode =
            entries.firstOrNull { it.name == raw } ?: Default
    }
}
