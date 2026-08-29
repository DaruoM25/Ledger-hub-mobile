package com.ledgerhub.presentation.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import com.ledgerhub.domain.i18n.AppLanguage
import com.ledgerhub.domain.i18n.AppTranslations
import com.ledgerhub.domain.i18n.StringKey

/**
 * Langue active propagée à tout l'arbre Compose depuis la racine (`App.kt`) via
 * `CompositionLocalProvider(LocalAppLanguage provides activeLanguage)`. `compositionLocalOf`
 * (et non `staticCompositionLocalOf`) : seuls les composables qui **lisent** la langue recomposent
 * lors d'une bascule — exactement le comportement voulu par le sélecteur FR/EN.
 *
 * Défaut : [AppLanguage.FR] (langue par défaut de l'app, comme sur le Web).
 */
val LocalAppLanguage = compositionLocalOf { AppLanguage.FR }

/** Raccourci de résolution d'une chaîne UI dans la langue active. */
@Composable
fun tr(key: StringKey): String = AppTranslations.get(key, LocalAppLanguage.current)
