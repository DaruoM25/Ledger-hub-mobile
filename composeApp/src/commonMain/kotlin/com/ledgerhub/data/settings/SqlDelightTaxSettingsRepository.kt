package com.ledgerhub.data.settings

import com.ledgerhub.db.LedgerHubDatabase
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.domain.settings.TaxSettings
import com.ledgerhub.domain.settings.TaxSettingsRepository

/**
 * Persistance des paramètres fiscaux — table mono-ligne `TaxSettings` (voir le CHECK sur la
 * clé primaire). Sur une base vierge, [loadSettings] rend [TaxSettings.Default] plutôt que `null` :
 * l'émission de factures reste ainsi fonctionnelle avant tout passage par l'écran Paramètres.
 */
class SqlDelightTaxSettingsRepository(
    private val database: LedgerHubDatabase,
) : TaxSettingsRepository {

    override suspend fun loadSettings(): Result<TaxSettings> = runCatching {
        val row = database.taxSettingsQueries.select().executeAsOneOrNull()
            ?: return@runCatching TaxSettings.Default
        TaxSettings(
            issuerName = row.issuerName,
            issuerSiren = row.issuerSiren,
            issuerSiret = row.issuerSiret,
            issuerEmail = row.issuerEmail,
            vatNumber = row.vatNumber,
            // Un taux inconnu (constante renommée entre deux versions) retombe sur le taux normal
            // plutôt que de faire échouer tout le chargement des paramètres.
            defaultVatRate = VatRate.entries.firstOrNull { it.name == row.defaultVatRate }
                ?: VatRate.TAUX_NORMAL,
            facturXEnabled = row.facturXEnabled == 1L,
        )
    }

    override suspend fun saveSettings(settings: TaxSettings): Result<Unit> = runCatching {
        database.taxSettingsQueries.upsert(
            issuerName = settings.issuerName,
            issuerSiren = settings.issuerSiren,
            issuerSiret = settings.issuerSiret,
            issuerEmail = settings.issuerEmail,
            vatNumber = settings.vatNumber,
            defaultVatRate = settings.defaultVatRate.name,
            facturXEnabled = if (settings.facturXEnabled) 1L else 0L,
        )
    }
}
