package com.ledgerhub.domain.invoice

/**
 * Papier à en-tête du cabinet : ce qu'une facture imprimée doit porter **en plus** de ses
 * données fiscales, et que ni [Invoice] ni [Party] ne modélisent — adresse postale, téléphone,
 * TVA intracommunautaire, coordonnées bancaires, mentions légales obligatoires.
 *
 * Volontairement hors base : ces informations ne participent à aucun calcul, ne sont pas gelées
 * sur la facture et n'entrent pas dans le flux Factur-X. Les stocker imposerait une migration
 * SQLDelight pour de la donnée de mise en page — voir `TaxSettings` pour ce qui est réellement
 * fiscal et persisté (dont [issuerVatNumber], repris ici pour l'impression).
 *
 * **L'identité de l'émetteur n'y figure pas** : elle est gelée sur la facture
 * ([Invoice.issuer]) au moment de l'émission. Un en-tête qui pourrait la réécrire permettrait
 * de réimprimer une pièce déjà émise sous un autre nom.
 */
data class Letterhead(
    /** Adresse postale, une entrée par ligne imprimée. */
    val issuerAddressLines: List<String>,
    val issuerPhone: String,
    val issuerEmail: String,
    /** TVA intracommunautaire `FRXX999999999`. Vide pour une entreprise en franchise. */
    val issuerVatNumber: String,
    val bankName: String,
    val iban: String,
    val bic: String,
    /**
     * Taux des pénalités de retard, exprimé tel qu'il doit être imprimé. Le Code de commerce
     * (art. L441-10) impose de le mentionner, pas de le calculer : c'est une phrase, pas un
     * nombre à multiplier.
     */
    val latePenaltyRate: String,
    /** Indemnité forfaitaire de recouvrement, en euros. 40 € — art. D441-5 du Code de commerce. */
    val fixedRecoveryIndemnityEuros: Int = DEFAULT_RECOVERY_INDEMNITY_EUROS,
) {
    companion object {
        /** Indemnité forfaitaire légale pour frais de recouvrement (art. D441-5). */
        const val DEFAULT_RECOVERY_INDEMNITY_EUROS: Int = 40

        /**
         * En-tête de démonstration servi tant qu'aucun n'est configuré. Miroir de l'identité de
         * démonstration utilisée par les maquettes et les captures QA de l'US-14.
         */
        val Default = Letterhead(
            issuerAddressLines = listOf("12 rue des Comptes", "69002 Lyon", "France"),
            issuerPhone = "+33 4 72 00 18 42",
            issuerEmail = "contact@roux-expertise.fr",
            issuerVatNumber = "FR82820329331",
            bankName = "Banque Rhône-Alpes",
            iban = "FR76 3000 4000 0300 0012 3456 789",
            bic = "BNPAFRPPXXX",
            latePenaltyRate = "3 fois le taux d'intérêt légal",
        )
    }
}
