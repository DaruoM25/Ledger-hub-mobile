package com.ledgerhub.domain.export

import com.ledgerhub.domain.facturx.FacturXGenerator
import com.ledgerhub.domain.facturx.toFacturXDocument
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.settings.TaxSettings

/**
 * Fabrique des trois documents d'export comptable (US-22).
 *
 * Le filtrage sur la période et le tri sont faits **ici, une fois** : les trois formats couvrent
 * exactement les mêmes pièces dans le même ordre, sans quoi deux exports d'une même période
 * remis au même cabinet ne se recouperaient pas.
 */
object AccountingArchiveBuilder {

    // ── Plan comptable minimal utilisé par le journal des ventes ──────────────
    // Comptes du PCG suffisants pour une facture de prestation. Une ventilation plus fine
    // (produits par nature, TVA par taux) suppose un paramétrage comptable qui n'existe pas
    // encore dans l'application : ce serait promettre une exactitude qu'aucune donnée ne porte.
    private const val ACCOUNT_CUSTOMERS = "411000"
    private const val ACCOUNT_CUSTOMERS_LABEL = "Clients"
    private const val ACCOUNT_SALES = "706000"
    private const val ACCOUNT_SALES_LABEL = "Prestations de services"
    private const val ACCOUNT_VAT = "445710"
    private const val ACCOUNT_VAT_LABEL = "TVA collectee"

    private const val JOURNAL_CODE = "VE"
    private const val JOURNAL_LABEL = "Ventes"

    private const val TAB = "\t"
    private const val LINE_BREAK = "\r\n"

    /**
     * Colonnes du FEC, dans l'ordre imposé par l'article A.47 A-1 du LPF. Ni l'ordre ni
     * l'orthographe ne sont négociables : un contrôle fiscal rejette le fichier sur son en-tête
     * avant même d'en lire une ligne. `Montantdevise` s'écrit bien ainsi dans le texte officiel.
     */
    private val FEC_HEADER = listOf(
        "JournalCode", "JournalLib", "EcritureNum", "EcritureDate",
        "CompteNum", "CompteLib", "CompAuxNum", "CompAuxLib",
        "PieceRef", "PieceDate", "EcritureLib", "Debit", "Credit",
        "EcritureLet", "DateLet", "ValidDate", "Montantdevise", "Idevise",
    )

    /** SIREN de repli quand aucune facture ne renseigne l'émetteur — le FEC exige un préfixe. */
    private const val UNKNOWN_SIREN = "000000000"

    /**
     * @param invoices toutes les factures connues ; le filtrage sur [period] est fait ici.
     * @param settings paramètres fiscaux de l'émetteur — seul le numéro de TVA intracommunautaire
     *   est consommé, par le générateur Factur-X.
     */
    fun build(
        format: ExportFormat,
        period: ExportPeriod,
        invoices: List<Invoice>,
        settings: TaxSettings = TaxSettings.Default,
    ): AccountingArchive {
        val covered = invoices
            .filter { period.contains(it.issueDate) }
            .sortedWith(compareBy({ it.issueDate }, { it.number }))

        val content = when (format) {
            ExportFormat.FEC_OFFICIAL -> buildFec(covered)
            ExportFormat.FACTURX_ARCHIVE -> buildFacturXArchive(covered, period, settings)
            ExportFormat.EXCEL_SUMMARY -> LedgerCsvExport.generate(covered)
        }
        return AccountingArchive(
            fileName = fileName(format, period, covered),
            mimeType = format.mimeType,
            content = content,
            format = format,
            documentCount = covered.size,
        )
    }

    /**
     * Nom du fichier proposé à l'utilisateur.
     *
     * Le FEC suit la nomenclature officielle `<SIREN>FEC<AAAAMMJJ>.txt`, où la date est celle de
     * clôture de l'exercice couvert : c'est sous ce nom que l'administration l'attend. Les deux
     * autres portent les bornes de la période — deux exports successifs de périodes différentes
     * ne doivent pas se recouvrir dans le dossier de téléchargement.
     */
    fun fileName(format: ExportFormat, period: ExportPeriod, invoices: List<Invoice>): String =
        when (format) {
            ExportFormat.FEC_OFFICIAL -> {
                val siren = invoices.firstOrNull()?.issuer?.siren?.takeIf { it.isNotBlank() }
                    ?: UNKNOWN_SIREN
                siren + "FEC" + period.to.compactDate() + "." + format.fileExtension
            }

            ExportFormat.FACTURX_ARCHIVE ->
                "archive-facturx-${period.from}_${period.to}.${format.fileExtension}"

            ExportFormat.EXCEL_SUMMARY ->
                "synthese-comptable-${period.from}_${period.to}.${format.fileExtension}"
        }

    // ── FEC ───────────────────────────────────────────────────────────────────

    /**
     * Journal des ventes au format FEC : une écriture équilibrée par facture — le client au
     * débit pour le TTC, la vente et la TVA au crédit.
     *
     * La ligne de TVA est omise quand la taxe est nulle (exonération, autoliquidation) : une
     * ligne à `0,00` dans un fichier d'écritures n'est pas neutre, elle affirme une TVA collectée
     * nulle sur un compte qui n'aurait pas dû être mouvementé.
     *
     * Une période sans facture produit malgré tout l'en-tête : un fichier vide se lirait comme un
     * échec d'export, pas comme un exercice sans vente (même parti pris que [LedgerCsvExport]).
     */
    private fun buildFec(invoices: List<Invoice>): String {
        val rows = mutableListOf<List<String>>()
        invoices.forEachIndexed { index, invoice ->
            val entryNumber = (index + 1).toString().padStart(5, '0')
            val date = invoice.issueDate.compactDate()
            val label = "Facture " + invoice.number + " - " + invoice.recipient.name

            rows += fecRow(
                entryNumber = entryNumber, date = date, account = ACCOUNT_CUSTOMERS,
                accountLabel = ACCOUNT_CUSTOMERS_LABEL,
                auxNumber = invoice.recipient.siret, auxLabel = invoice.recipient.name,
                pieceRef = invoice.number, label = label,
                debit = invoice.totalTtc.cents, credit = 0L,
            )
            rows += fecRow(
                entryNumber = entryNumber, date = date, account = ACCOUNT_SALES,
                accountLabel = ACCOUNT_SALES_LABEL,
                auxNumber = "", auxLabel = "",
                pieceRef = invoice.number, label = label,
                debit = 0L, credit = invoice.totalHt.cents,
            )
            if (invoice.totalVat.cents != 0L) {
                rows += fecRow(
                    entryNumber = entryNumber, date = date, account = ACCOUNT_VAT,
                    accountLabel = ACCOUNT_VAT_LABEL,
                    auxNumber = "", auxLabel = "",
                    pieceRef = invoice.number, label = label,
                    debit = 0L, credit = invoice.totalVat.cents,
                )
            }
        }
        return (listOf(FEC_HEADER) + rows).joinToString(LINE_BREAK) { row ->
            row.joinToString(TAB) { it.sanitizeFec() }
        }
    }

    @Suppress("LongParameterList")
    private fun fecRow(
        entryNumber: String,
        date: String,
        account: String,
        accountLabel: String,
        auxNumber: String,
        auxLabel: String,
        pieceRef: String,
        label: String,
        debit: Long,
        credit: Long,
    ): List<String> = listOf(
        JOURNAL_CODE, JOURNAL_LABEL, entryNumber, date,
        account, accountLabel, auxNumber, auxLabel,
        pieceRef, date, label, debit.toDecimalString(), credit.toDecimalString(),
        // EcritureLet / DateLet : le lettrage n'est pas tenu par l'application. ValidDate reprend
        // la date d'écriture — la pièce est validée à son émission, pas à son export.
        "", "", date, "", "",
    )

    // ── Archive Factur-X ──────────────────────────────────────────────────────

    /**
     * Recueil des pièces Factur-X de la période dans un document pivot unique.
     *
     * Chaque pièce est produite par le générateur déjà en place — l'archive ne réimplémente aucun
     * XML métier — puis incorporée **sans sa déclaration** : un document ne peut en porter qu'une,
     * en tête. `fileName` conserve le chemin qu'aurait l'entrée dans une archive ZIP, de sorte que
     * le passage à un vrai conteneur ne changerait pas la structure remise au cabinet.
     */
    private fun buildFacturXArchive(
        invoices: List<Invoice>,
        period: ExportPeriod,
        settings: TaxSettings,
    ): String {
        val builder = StringBuilder()
        builder.append(XML_DECLARATION).append('\n')
        builder.append(
            "<FacturXArchive periodFrom=\"" + period.from.escapeXml() +
                "\" periodTo=\"" + period.to.escapeXml() +
                "\" documentCount=\"" + invoices.size + "\">",
        ).append('\n')
        invoices.forEach { invoice ->
            val xml = FacturXGenerator.generate(invoice.toFacturXDocument(settings))
            builder.append(
                "  <Document number=\"" + invoice.number.escapeXml() +
                    "\" issueDate=\"" + invoice.issueDate.escapeXml() +
                    "\" fileName=\"" + invoice.number.escapeXml() + "/factur-x.xml\">",
            ).append('\n')
            builder.append(xml.withoutXmlDeclaration()).append('\n')
            builder.append("  </Document>").append('\n')
        }
        builder.append("</FacturXArchive>")
        return builder.toString()
    }

    /** Écrite en une constante : la séquence `?>` dans une chaîne littérale trouble les outils. */
    private const val XML_DECLARATION = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"

    // ── Utilitaires de format ─────────────────────────────────────────────────

    /** `AAAA-MM-JJ` vers `AAAAMMJJ`, la seule forme de date acceptée dans un FEC. */
    private fun String.compactDate(): String = replace("-", "")

    /**
     * Centimes vers montant décimal, sans arithmétique flottante — mêmes raisons que
     * [LedgerCsvExport] : un fichier d'écritures ne supporte pas l'arrondi approximatif d'un
     * `Double`. Virgule décimale, comme l'attend l'administration.
     */
    private fun Long.toDecimalString(): String {
        val sign = if (this < 0) "-" else ""
        val absolute = if (this < 0) -this else this
        return sign + (absolute / 100) + "," + (absolute % 100).toString().padStart(2, '0')
    }

    /**
     * Le FEC est un fichier **tabulé** : une tabulation ou un saut de ligne dans une raison
     * sociale décalerait toutes les colonnes suivantes, et il n'existe aucun échappement dans le
     * format. Les remplacer par une espace est la seule issue.
     */
    private fun String.sanitizeFec(): String =
        replace('\t', ' ').replace('\r', ' ').replace('\n', ' ')

    private fun String.escapeXml(): String = this
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun String.withoutXmlDeclaration(): String =
        substringAfter("?>").trimStart('\n', '\r')
}
