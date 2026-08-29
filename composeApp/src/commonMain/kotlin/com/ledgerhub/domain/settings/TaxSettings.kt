package com.ledgerhub.domain.settings

import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate

/**
 * Paramètres fiscaux du cabinet émetteur.
 *
 * Les **valeurs** des taux de TVA n'y figurent pas : elles sont fixées par la loi et portées par
 * [VatRate]. Seul [defaultVatRate], le taux pré-sélectionné à la saisie d'une ligne, se configure.
 */
data class TaxSettings(
    val issuerName: String,
    val issuerSiren: String,
    val issuerSiret: String,
    val issuerEmail: String = "",
    /** TVA intracommunautaire, `FRXX999999999`. Vide pour une entreprise en franchise. */
    val vatNumber: String = "",
    val defaultVatRate: VatRate = VatRate.TAUX_NORMAL,
    /** Génération Factur-X et dématérialisation actives — conformité 2026 par défaut. */
    val facturXEnabled: Boolean = true,
) {
    /** Émetteur à porter sur les factures — voir `InvoiceFormViewModel`. */
    val issuerParty: Party
        get() = Party(name = issuerName, siren = issuerSiren, siret = issuerSiret, email = issuerEmail)

    companion object {
        /**
         * Valeurs servies tant que l'utilisateur n'a rien enregistré. Reprennent l'identité de
         * démonstration qui était codée en dur dans `CabinetIdentity`, pour que le comportement
         * d'émission reste inchangé sur une base vierge.
         */
        val Default = TaxSettings(
            issuerName = "Cabinet LedgerHub",
            issuerSiren = "820329331",
            issuerSiret = "82032933100027",
            issuerEmail = "facturation@ledgerhub.app",
            vatNumber = "FR82820329331",
        )
    }
}

/** Persistance des paramètres fiscaux — une seule ligne, voir `TaxSettings.sq`. */
interface TaxSettingsRepository {
    /** Retourne les paramètres enregistrés, ou [TaxSettings.Default] si rien ne l'a encore été. */
    suspend fun loadSettings(): Result<TaxSettings>

    suspend fun saveSettings(settings: TaxSettings): Result<Unit>
}
