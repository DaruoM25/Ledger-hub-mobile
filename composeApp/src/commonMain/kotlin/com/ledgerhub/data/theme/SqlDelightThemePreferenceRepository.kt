package com.ledgerhub.data.theme

import com.ledgerhub.db.LedgerHubDatabase
import com.ledgerhub.domain.theme.ThemeMode
import com.ledgerhub.domain.theme.ThemePreferenceRepository

/**
 * Persistance de la préférence de thème — table mono-ligne `UiPreferences` (voir le CHECK sur la
 * clé primaire).
 *
 * Sur une base vierge, [loadThemeMode] rend [ThemeMode.Default] plutôt que `null` : l'application
 * démarre avec son thème sombre historique avant tout passage par le bouton de bascule. Une valeur
 * illisible (constante renommée entre deux versions) y retombe également — c'est
 * [ThemeMode.fromStorage] qui porte cette règle, pas ce dépôt.
 */
class SqlDelightThemePreferenceRepository(
    private val database: LedgerHubDatabase,
) : ThemePreferenceRepository {

    override suspend fun loadThemeMode(): Result<ThemeMode> = runCatching {
        val stored = database.uiPreferencesQueries.selectThemeMode().executeAsOneOrNull()
        ThemeMode.fromStorage(stored)
    }

    override suspend fun saveThemeMode(mode: ThemeMode): Result<Unit> = runCatching {
        database.uiPreferencesQueries.upsertThemeMode(mode.name)
    }
}
