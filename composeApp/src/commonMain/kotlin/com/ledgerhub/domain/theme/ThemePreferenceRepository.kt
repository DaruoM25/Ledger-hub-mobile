package com.ledgerhub.domain.theme

/**
 * Persistance de la préférence de thème (US-25).
 *
 * Contrat calqué sur
 * [TaxSettingsRepository][com.ledgerhub.domain.settings.TaxSettingsRepository] : `Result<…>` pour
 * qu'un échec de stockage reste une valeur et non une exception qui traverse l'UI, et **jamais de
 * `null`** — une base vierge rend [ThemeMode.Default], si bien que l'application démarre toujours
 * avec un thème, y compris au tout premier lancement.
 */
interface ThemePreferenceRepository {

    suspend fun loadThemeMode(): Result<ThemeMode>

    suspend fun saveThemeMode(mode: ThemeMode): Result<Unit>
}
