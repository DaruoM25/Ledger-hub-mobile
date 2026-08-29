package com.ledgerhub.domain.facturx

import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatBreakdown
import com.ledgerhub.domain.invoice.VatRate

/**
 * Code de type de pièce (UNTDID 1001) porté par `ram:TypeCode`.
 * Seules ces deux valeurs sont émises par l'application.
 */
enum class FacturXDocumentType(val code: String) {
    /** Facture commerciale. */
    INVOICE("380"),

    /** Avoir — impose le chaînage `ram:InvoiceReferencedDocument` vers la facture annulée. */
    CREDIT_NOTE("381"),
}

/**
 * Catégorie de TVA (UNTDID 5305). `S` pour un taux applicable, `E` pour une exonération.
 * Les taux réduits français restent en catégorie standard : seule l'exonération change de code.
 */
enum class FacturXTaxCategory(val code: String) {
    STANDARD("S"),
    EXEMPT("E"),
}

/** Référence croisée vers la facture annulée — numéro **et** date, comme l'exige EN 16931. */
data class FacturXReference(
    val documentNumber: String,
    val issueDate: String,
)

/** Une assiette de TVA du document, avec son taux et le montant correspondant. */
data class FacturXTax(
    val rate: VatRate,
    val basisAmount: Money,
    val calculatedAmount: Money,
) {
    val category: FacturXTaxCategory
        get() = if (rate == VatRate.EXONERE) FacturXTaxCategory.EXEMPT else FacturXTaxCategory.STANDARD
}

/**
 * Modèle pivot, neutre vis-à-vis de la facture et de l'avoir.
 *
 * Le générateur ne connaît que ce type : `Invoice` et `CreditNote` s'y projettent via
 * [FacturXMapper]. Une seule arborescence XML à écrire et à tester, quel que soit le type de pièce.
 *
 * @param lines lignes du document, prix **positifs** dans les deux cas — c'est le sens comptable
 *   de la pièce, porté par [FacturXDocumentType], qui distingue une facture d'un avoir. Les
 *   montants de [taxes] et de la sommation, eux, sont négatifs sur un avoir.
 */
data class FacturXDocument(
    val type: FacturXDocumentType,
    val documentNumber: String,
    val issueDate: String,
    val seller: Party,
    val buyer: Party,
    val sellerVatNumber: String,
    val lines: List<InvoiceLine>,
    val taxes: List<FacturXTax>,
    val totalHt: Money,
    val totalVat: Money,
    val totalTtc: Money,
    /** Renseignée uniquement pour un avoir — voir [FacturXDocumentType.CREDIT_NOTE]. */
    val referencedDocument: FacturXReference? = null,
) {
    init {
        require(lines.isNotEmpty()) { "Un document Factur-X doit comporter au moins une ligne" }
        require(type != FacturXDocumentType.CREDIT_NOTE || referencedDocument != null) {
            "Un avoir (381) doit référencer la facture qu'il annule"
        }
    }

    /**
     * Montant de la ligne, signé comme le document. Sur un avoir, la sommation est négative :
     * chaque ligne doit l'être aussi, sans quoi `LineTotalAmount` global et somme des lignes
     * divergeraient.
     */
    fun lineTotal(line: InvoiceLine): Money =
        if (type == FacturXDocumentType.CREDIT_NOTE) Money(-line.totalHt.cents) else line.totalHt

    companion object {
        /** Construit les assiettes à partir d'une ventilation du domaine, sans recalcul propre. */
        fun taxesFrom(breakdown: List<VatBreakdown>): List<FacturXTax> = breakdown.map {
            FacturXTax(rate = it.rate, basisAmount = it.baseHt, calculatedAmount = it.vatAmount)
        }
    }
}
