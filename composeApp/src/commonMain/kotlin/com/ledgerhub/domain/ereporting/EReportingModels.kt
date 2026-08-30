package com.ledgerhub.domain.ereporting

/**
 * Nature du flux déclaré au titre de l'e-Reporting DGFIP 2026.
 *
 * - [B2C] : ventes et prestations à des particuliers, hors champ de la facturation électronique.
 * - [INTERNATIONAL] : opérations avec un assujetti hors de France (UE ou pays tiers).
 * - [PAYMENTS] : données d'encaissement des prestations de services relevant de la TVA sur les débits.
 */
enum class EReportingType {
    B2C,
    INTERNATIONAL,
    PAYMENTS,
}

/**
 * Cycle de vie d'une déclaration e-Reporting.
 *
 * Binaire et irréversible : [DRAFT] tant que la déclaration se construit localement,
 * [ACKNOWLEDGED] une fois l'accusé de réception du PPF enregistré. Toute correction ultérieure
 * passe par une nouvelle déclaration.
 */
enum class EReportingStatus {
    DRAFT,
    ACKNOWLEDGED,
}

/**
 * Une ligne de transaction agrégée dans une déclaration.
 *
 * @param label libellé de la catégorie de transactions (ex. « Ventes comptoir »).
 * @param amountHt montant hors taxes, en euros. L'arrondi au centime est de la responsabilité
 *   de [EReportingCalculator], jamais de l'appelant.
 * @param vatRateBasisPoints taux de TVA en points de base (2000 = 20,00 %, 550 = 5,50 %) pour
 *   rester en arithmétique entière lors du calcul de la taxe.
 */
data class EReportingTransactionLine(
    val label: String,
    val amountHt: Double,
    val vatRateBasisPoints: Int,
)

/**
 * Totaux consolidés d'une déclaration, exprimés en euros et déjà arrondis au centime.
 */
data class EReportingTotals(
    val totalHt: Double,
    val totalVat: Double,
    val totalTtc: Double,
    val transactionCount: Int,
) {
    companion object {
        /** Panier vide : tous les montants à 0,00 €, aucune transaction. */
        val EMPTY = EReportingTotals(totalHt = 0.0, totalVat = 0.0, totalTtc = 0.0, transactionCount = 0)
    }
}

/**
 * Une déclaration e-Reporting persistée (voir `EReporting.sq`).
 *
 * @param ackNumber `null` en [EReportingStatus.DRAFT] ; numéro d'accusé `ACK-2026-XXXX` une fois
 *   la déclaration acquittée par le PPF.
 * @param createdAt horodatage ISO 8601 UTC de création.
 */
data class EReportingReport(
    val id: String,
    val period: String,
    val type: EReportingType,
    val status: EReportingStatus,
    val ackNumber: String?,
    val totalHt: Double,
    val totalVat: Double,
    val totalTtc: Double,
    val transactionCount: Int,
    val createdAt: String,
)

/**
 * Levée lorsqu'une déclaration déjà acquittée est re-transmise.
 *
 * Parité fonctionnelle avec un HTTP 409 Conflict côté PPF : l'accusé existant fait autorité, une
 * seconde transmission ne doit produire ni nouvel accusé ni écrasement de l'ancien.
 */
class AlreadyAcknowledgedException(
    val reportId: String,
) : Exception("La déclaration e-Reporting $reportId a déjà été acquittée par le PPF")
