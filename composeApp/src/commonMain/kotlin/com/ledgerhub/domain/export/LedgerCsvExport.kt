package com.ledgerhub.domain.export

import com.ledgerhub.domain.invoice.Invoice

/**
 * Export comptable du grand livre au format CSV (US-19).
 *
 * Destiné à être repris par un logiciel de comptabilité ou un tableur : une ligne par facture, les
 * montants en unités monétaires avec deux décimales, aucune mise en forme.
 *
 * ## Choix de format, et pourquoi ils ne sont pas anodins
 *
 * - **Séparateur point-virgule.** C'est ce qu'attend Excel en locale française, où la virgule est
 *   le séparateur décimal. Un CSV à virgules s'y ouvre en une seule colonne — techniquement
 *   correct, inexploitable en pratique.
 * - **Montants à la virgule décimale**, pour la même raison. La conversion depuis les centimes est
 *   faite ici, une fois : le domaine raisonne en centiers entiers, le comptable lit des euros.
 * - **Dates ISO `AAAA-MM-JJ`**, laissées telles quelles : c'est le seul format qui se trie
 *   correctement et ne s'interprète pas différemment d'une locale à l'autre.
 * - **Statut en clair, non traduit** : la valeur du référentiel DGFIP (`DEPOSITED`, `PAID`…) est
 *   ce qu'un rapprochement comptable doit pouvoir recouper, pas son libellé d'écran.
 *
 * Les champs sont échappés selon la RFC 4180 — un nom de client contenant un point-virgule ou un
 * guillemet décalerait sinon toutes les colonnes suivantes.
 */
object LedgerCsvExport {

    const val FILE_NAME: String = "grand-livre.csv"
    const val MIME_TYPE: String = "text/csv"

    private const val SEPARATOR = ";"
    private const val LINE_BREAK = "\r\n"

    private val HEADER = listOf(
        "Numero",
        "Date emission",
        "Date echeance",
        "Client",
        "SIRET client",
        "Total HT",
        "Total TVA",
        "Total TTC",
        "Statut",
    )

    /**
     * @param invoices factures à exporter, dans l'ordre voulu par l'appelant. Une liste vide
     *   produit malgré tout l'en-tête : un fichier vide laisserait croire à un échec d'export.
     */
    fun generate(invoices: List<Invoice>): String {
        val rows = invoices.map { invoice ->
            listOf(
                invoice.number,
                invoice.issueDate,
                invoice.dueDate,
                invoice.recipient.name,
                invoice.recipient.siret,
                invoice.totalHt.cents.toDecimalString(),
                invoice.totalVat.cents.toDecimalString(),
                invoice.totalTtc.cents.toDecimalString(),
                invoice.status.name,
            )
        }
        return (listOf(HEADER) + rows).joinToString(LINE_BREAK) { row ->
            row.joinToString(SEPARATOR) { it.escapeCsv() }
        }
    }

    /**
     * Centimes → montant décimal, sans arithmétique flottante : un export comptable ne peut pas se
     * permettre l'arrondi approximatif d'un `Double`.
     */
    private fun Long.toDecimalString(): String {
        val sign = if (this < 0) "-" else ""
        val absolute = if (this < 0) -this else this
        return "$sign${absolute / 100},${(absolute % 100).toString().padStart(2, '0')}"
    }

    /** Échappement RFC 4180 : guillemets doublés, champ encadré s'il contient un caractère parlant. */
    private fun String.escapeCsv(): String =
        if (contains(SEPARATOR) || contains('"') || contains('\n') || contains('\r')) {
            "\"" + replace("\"", "\"\"") + "\""
        } else {
            this
        }
}
