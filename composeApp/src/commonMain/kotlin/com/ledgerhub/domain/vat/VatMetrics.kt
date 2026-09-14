package com.ledgerhub.domain.vat

import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.VatRate

/**
 * Période fiscale d'analyse TVA.
 */
enum class VatPeriodFilter(val labelFr: String, val labelEn: String) {
    ALL("Toutes", "All"),
    CURRENT_MONTH("Mois en cours", "Current Month"),
    CURRENT_QUARTER("Trimestre en cours", "Current Quarter"),
    CURRENT_YEAR("Année en cours", "Current Year");
}

/**
 * Ligne de déclaration du formulaire fiscal 3310-CA3 (lignes 01 à 04 de la section TVA Brute).
 */
data class VatCa3Line(
    val lineCode: String,
    val vatRate: VatRate,
    val baseHt: Money,
    val taxDue: Money,
)

/**
 * Métriques globales de TVA et ventilation réglementaire 3310-CA3.
 */
data class VatMetrics(
    /** TVA collectée totale dont l'exigibilité fiscale est acquise. */
    val totalVatCollectedExigible: Money = Money.ZERO,
    /** TVA collectée sur factures émises en attente d'encaissement (prestations de services sans option débits). */
    val totalVatPendingCollection: Money = Money.ZERO,
    /** TVA déductible (sur achats et investissements - simulation / saisie ou avoirs reçus). */
    val totalVatDeductible: Money = Money.ZERO,
    /** Lignes de ventilation par taux selon le formulaire 3310-CA3. */
    val ca3Lines: List<VatCa3Line> = emptyList(),
) {
    /**
     * Solde net de TVA = TVA collectée exigible - TVA déductible.
     * Positif : TVA nette à payer à l'administration.
     * Négatif : Crédit de TVA en faveur de l'entreprise.
     */
    val netVatBalance: Money
        get() = totalVatCollectedExigible - totalVatDeductible

    /** Indique s'il s'agit d'un crédit de TVA (solde net négatif). */
    val isCredit: Boolean
        get() = netVatBalance < Money.ZERO

    /** Montant absolu de la TVA due ou du crédit. */
    val absoluteBalance: Money
        get() = if (netVatBalance < Money.ZERO) Money(-netVatBalance.cents) else netVatBalance
}
