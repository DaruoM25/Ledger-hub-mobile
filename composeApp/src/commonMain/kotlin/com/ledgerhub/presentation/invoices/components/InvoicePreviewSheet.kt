package com.ledgerhub.presentation.invoices.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ledgerhub.domain.i18n.StringKey
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.Letterhead
import com.ledgerhub.presentation.i18n.LocalAppLanguage
import com.ledgerhub.presentation.i18n.formatIsoDate
import com.ledgerhub.presentation.i18n.tr
import com.ledgerhub.presentation.invoices.format

/** Tags de test — contrat partagé entre l'aperçu A4 (commonMain) et les tests. */
object InvoicePreviewTags {
    const val DIALOG = "invoice_preview_dialog"
    const val SHEET = "invoice_preview_sheet"
    const val CLOSE_BUTTON = "invoice_preview_close"

    const val ISSUER_BLOCK = "invoice_preview_issuer"
    const val ISSUER_NAME = "invoice_preview_issuer_name"
    const val ISSUER_SIRET = "invoice_preview_issuer_siret"
    const val ISSUER_VAT_NUMBER = "invoice_preview_issuer_vat_number"

    const val CLIENT_BLOCK = "invoice_preview_client"
    const val CLIENT_NAME = "invoice_preview_client_name"
    const val CLIENT_SIREN = "invoice_preview_client_siren"

    const val METADATA_BLOCK = "invoice_preview_metadata"
    const val INVOICE_NUMBER = "invoice_preview_number"
    const val ISSUE_DATE = "invoice_preview_issue_date"
    const val DUE_DATE = "invoice_preview_due_date"

    const val LINES_TABLE = "invoice_preview_lines_table"
    const val TOTALS_BLOCK = "invoice_preview_totals"
    const val TOTAL_HT = "invoice_preview_total_ht"
    const val TOTAL_VAT = "invoice_preview_total_vat"
    const val TOTAL_TTC = "invoice_preview_total_ttc"

    const val LEGAL_BLOCK = "invoice_preview_legal"
    const val LEGAL_ASSOCIATION = "invoice_preview_legal_association"
    const val LEGAL_LATE_PENALTY = "invoice_preview_legal_late_penalty"
    const val LEGAL_INDEMNITY = "invoice_preview_legal_indemnity"
    const val BANK_DETAILS = "invoice_preview_bank_details"

    fun lineRow(index: Int) = "invoice_preview_line_$index"
    fun lineTotalHt(index: Int) = "invoice_preview_line_${index}_total_ht"
}

// Palette de l'aperçu : volontairement figée et hors thème. Une feuille imprimée est blanche
// quel que soit le thème de l'application — la basculer en sombre donnerait un aperçu qui ne
// ressemble pas au document réellement émis.
private val SheetPaper = Color.White
private val SheetDesk = Color(0xFF3A3A42)
private val SheetInk = Color(0xFF1A1A1A)
private val SheetMutedInk = Color(0xFF6B6B6B)
private val SheetRule = Color(0xFFDDDDDD)
private val SheetTotalsBackground = Color(0xFFF4F5F7)

/** Largeur maximale de la feuille : au-delà, une A4 étirée cesse de se lire comme une page. */
private val SheetMaxWidth = 640.dp

/**
 * Aperçu A4 d'une facture émise, présenté en boîte de dialogue plein écran.
 *
 * Le contenu est délégué à [InvoicePreviewSheet], qui reste utilisable seul : une capture
 * d'écran de test porte sur la feuille, pas sur la fenêtre de dialogue qui la transporte.
 */
@Composable
fun InvoicePreviewDialog(
    invoice: Invoice,
    onDismiss: () -> Unit,
    letterhead: Letterhead = Letterhead.Default,
    recipientAddressLines: List<String> = emptyList(),
) {
    Dialog(
        onDismissRequest = onDismiss,
        // La feuille impose sa propre largeur : la contrainte de dialogue par défaut la
        // rétrécirait au point de casser le tableau des prestations.
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            color = SheetDesk,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .semantics { testTag = InvoicePreviewTags.DIALOG },
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.semantics { testTag = InvoicePreviewTags.CLOSE_BUTTON },
                    ) {
                        Text(tr(StringKey.PREVIEW_CLOSE), color = Color.White)
                    }
                }
                InvoicePreviewSheet(
                    invoice = invoice,
                    letterhead = letterhead,
                    recipientAddressLines = recipientAddressLines,
                )
            }
        }
    }
}

/**
 * La feuille elle-même : page blanche verticale posée sur un bureau sombre, ombrée et
 * défilante.
 *
 * La cinquième colonne porte le **Total HT** de chaque ligne (quantité x prix unitaire HT),
 * standard des factures B2B sous la réforme PPF 2026 : la TVA n'apparaît qu'une fois, au pied
 * de page, là où elle est ventilée par taux. C'est aussi la seule colonne additive — la somme
 * des HT de ligne égale exactement [Invoice.totalHt], alors qu'une colonne TTC s'écarterait de
 * quelques centimes du total, `computeVatBreakdown` calculant la TVA sur la base HT agrégée de
 * chaque taux plutôt qu'en sommant des arrondis ligne à ligne.
 *
 * Les montants du pied de page proviennent malgré tout de [Invoice.totalHt] / [Invoice.totalVat] /
 * [Invoice.totalTtc], et non d'une somme de colonne : c'est l'agrégat qui fait foi pour
 * l'administration fiscale.
 *
 * @param recipientAddressLines adresse postale du client, une entrée par ligne. `Party` ne
 *   modélise pas d'adresse et une facture émise ne la gèle pas : l'appelant la fournit s'il en
 *   dispose, et le bloc client se limite au nom et au SIREN sinon.
 */
@Composable
fun InvoicePreviewSheet(
    invoice: Invoice,
    letterhead: Letterhead = Letterhead.Default,
    recipientAddressLines: List<String> = emptyList(),
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(SheetDesk)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Surface(
            color = SheetPaper,
            contentColor = SheetInk,
            shadowElevation = 8.dp,
            shape = RoundedCornerShape(2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = SheetMaxWidth)
                .padding(horizontal = 12.dp)
                .semantics { testTag = InvoicePreviewTags.SHEET },
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                SheetHeader(
                    invoice = invoice,
                    letterhead = letterhead,
                    recipientAddressLines = recipientAddressLines,
                )
                SheetMetadata(invoice)
                HorizontalDivider(color = SheetRule)
                SheetLinesTable(invoice.lines)
                SheetTotals(invoice)
                HorizontalDivider(color = SheetRule)
                SheetLegalFooter(letterhead)
            }
        }
    }
}

@Composable
private fun SheetHeader(
    invoice: Invoice,
    letterhead: Letterhead,
    recipientAddressLines: List<String>,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Gauche : émetteur. Le nom vient de la facture (identité gelée à l'émission), les
        // coordonnées de l'en-tête — voir Letterhead.
        Column(
            modifier = Modifier
                .weight(1f)
                .semantics { testTag = InvoicePreviewTags.ISSUER_BLOCK },
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = invoice.issuer.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = SheetInk,
                modifier = Modifier.semantics { testTag = InvoicePreviewTags.ISSUER_NAME },
            )
            letterhead.issuerAddressLines.forEach { line -> SheetFineText(line) }
            if (letterhead.issuerPhone.isNotBlank()) SheetFineText(letterhead.issuerPhone)
            if (letterhead.issuerEmail.isNotBlank()) SheetFineText(letterhead.issuerEmail)

            val siret = "${tr(StringKey.PREVIEW_SIRET)} ${invoice.issuer.siret}"
            SheetFineText(siret, tag = InvoicePreviewTags.ISSUER_SIRET, description = siret)
            if (letterhead.issuerVatNumber.isNotBlank()) {
                val vat = "${tr(StringKey.PREVIEW_VAT_NUMBER)} ${letterhead.issuerVatNumber}"
                SheetFineText(vat, tag = InvoicePreviewTags.ISSUER_VAT_NUMBER, description = vat)
            }
        }

        // Droite : encart client, encadré comme sur un courrier à fenêtre.
        Surface(
            color = SheetTotalsBackground,
            contentColor = SheetInk,
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier
                .weight(1f)
                .semantics { testTag = InvoicePreviewTags.CLIENT_BLOCK },
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = tr(StringKey.PREVIEW_BILL_TO),
                    style = MaterialTheme.typography.labelSmall,
                    color = SheetMutedInk,
                )
                Text(
                    text = invoice.recipient.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = SheetInk,
                    modifier = Modifier.semantics { testTag = InvoicePreviewTags.CLIENT_NAME },
                )
                recipientAddressLines.forEach { line -> SheetFineText(line) }

                val siren = "${tr(StringKey.PREVIEW_SIREN)} ${invoice.recipient.siren}"
                SheetFineText(siren, tag = InvoicePreviewTags.CLIENT_SIREN, description = siren)
            }
        }
    }
}

@Composable
private fun SheetMetadata(invoice: Invoice) {
    val language = LocalAppLanguage.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { testTag = InvoicePreviewTags.METADATA_BLOCK },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val number = "${tr(StringKey.PREVIEW_INVOICE_NUMBER)} ${invoice.number}"
        Text(
            text = number,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = SheetInk,
            modifier = Modifier.semantics {
                testTag = InvoicePreviewTags.INVOICE_NUMBER
                contentDescription = number
            },
        )
        val issuedOn = "${tr(StringKey.DETAIL_ISSUE_DATE)} : ${formatIsoDate(invoice.issueDate, language)}"
        SheetFineText(issuedOn, tag = InvoicePreviewTags.ISSUE_DATE, description = issuedOn)

        // Échéance optionnelle : le parc hérité en contient sans (voir Invoice.dueDate). Une
        // ligne "Date d'échéance : " vide serait pire que son absence.
        if (invoice.dueDate.isNotBlank()) {
            val dueOn = "${tr(StringKey.PREVIEW_DUE_DATE)} : ${formatIsoDate(invoice.dueDate, language)}"
            SheetFineText(dueOn, tag = InvoicePreviewTags.DUE_DATE, description = dueOn)
        }
    }
}

@Composable
private fun SheetLinesTable(lines: List<InvoiceLine>) {
    val language = LocalAppLanguage.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { testTag = InvoicePreviewTags.LINES_TABLE },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            SheetHeaderCell(tr(StringKey.PREVIEW_COL_DESCRIPTION), weight = 3f)
            SheetHeaderCell(tr(StringKey.PREVIEW_COL_QUANTITY), weight = 0.8f, textAlign = TextAlign.End)
            SheetHeaderCell(tr(StringKey.PREVIEW_COL_UNIT_PRICE_HT), weight = 1.8f, textAlign = TextAlign.End)
            SheetHeaderCell(tr(StringKey.PREVIEW_COL_VAT_RATE), weight = 1.2f, textAlign = TextAlign.End)
            SheetHeaderCell(tr(StringKey.PREVIEW_COL_TOTAL_HT), weight = 1.8f, textAlign = TextAlign.End)
        }
        HorizontalDivider(color = SheetRule)

        lines.forEachIndexed { index, line ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { testTag = InvoicePreviewTags.lineRow(index) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SheetBodyCell(line.label, weight = 3f)
                SheetBodyCell(line.quantity.toString(), weight = 0.8f, textAlign = TextAlign.End)
                SheetBodyCell(line.unitPriceHt.format(language), weight = 1.8f, textAlign = TextAlign.End)
                SheetBodyCell(line.vatRate.label, weight = 1.2f, textAlign = TextAlign.End)
                SheetBodyCell(
                    text = line.totalHt.format(language),
                    weight = 1.8f,
                    textAlign = TextAlign.End,
                    tag = InvoicePreviewTags.lineTotalHt(index),
                )
            }
            HorizontalDivider(color = SheetRule)
        }
    }
}

@Composable
private fun SheetTotals(invoice: Invoice) {
    val language = LocalAppLanguage.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            color = SheetTotalsBackground,
            contentColor = SheetInk,
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.semantics { testTag = InvoicePreviewTags.TOTALS_BLOCK },
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.End,
            ) {
                TotalLine(
                    label = tr(StringKey.PREVIEW_TOTAL_HT),
                    value = invoice.totalHt.format(language),
                    tag = InvoicePreviewTags.TOTAL_HT,
                )
                TotalLine(
                    label = tr(StringKey.PREVIEW_TOTAL_VAT),
                    value = invoice.totalVat.format(language),
                    tag = InvoicePreviewTags.TOTAL_VAT,
                )
                HorizontalDivider(color = SheetRule)
                TotalLine(
                    label = tr(StringKey.PREVIEW_TOTAL_TTC),
                    value = invoice.totalTtc.format(language),
                    tag = InvoicePreviewTags.TOTAL_TTC,
                    emphasize = true,
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.TotalLine(
    label: String,
    value: String,
    tag: String,
    emphasize: Boolean = false,
) {
    val text = "$label : $value"
    Text(
        text = text,
        style = if (emphasize) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
        fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Normal,
        color = SheetInk,
        modifier = Modifier.semantics {
            testTag = tag
            contentDescription = text
        },
    )
}

/**
 * Pied de page réglementaire. Corps réduit à 9sp : ces mentions sont obligatoires mais ne
 * doivent pas concurrencer le montant à payer, exactement comme sur une facture imprimée.
 */
@Composable
private fun SheetLegalFooter(letterhead: Letterhead) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { testTag = InvoicePreviewTags.LEGAL_BLOCK },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LegalText(
            text = tr(StringKey.PREVIEW_LEGAL_ASSOCIATION),
            tag = InvoicePreviewTags.LEGAL_ASSOCIATION,
        )
        LegalText(
            text = "${tr(StringKey.PREVIEW_LEGAL_LATE_PENALTY)} ${letterhead.latePenaltyRate}.",
            tag = InvoicePreviewTags.LEGAL_LATE_PENALTY,
        )
        LegalText(
            text = "${tr(StringKey.PREVIEW_LEGAL_FIXED_INDEMNITY)} " +
                "${letterhead.fixedRecoveryIndemnityEuros} €.",
            tag = InvoicePreviewTags.LEGAL_INDEMNITY,
        )
        LegalText(
            text = "${tr(StringKey.PREVIEW_BANK_DETAILS)} — ${letterhead.bankName} · " +
                "${tr(StringKey.PREVIEW_IBAN)} ${letterhead.iban} · " +
                "${tr(StringKey.PREVIEW_BIC)} ${letterhead.bic}",
            tag = InvoicePreviewTags.BANK_DETAILS,
        )
    }
}

@Composable
private fun LegalText(text: String, tag: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontSize = 9.sp,
        lineHeight = 12.sp,
        color = SheetMutedInk,
        modifier = Modifier.semantics {
            testTag = tag
            contentDescription = text
        },
    )
}

@Composable
private fun RowScope.SheetHeaderCell(
    text: String,
    weight: Float,
    textAlign: TextAlign = TextAlign.Start,
) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = SheetMutedInk,
        textAlign = textAlign,
    )
}

@Composable
private fun RowScope.SheetBodyCell(
    text: String,
    weight: Float,
    textAlign: TextAlign = TextAlign.Start,
    tag: String? = null,
) {
    Text(
        text = text,
        modifier = Modifier
            .weight(weight)
            .padding(vertical = 6.dp)
            .then(
                if (tag == null) {
                    Modifier
                } else {
                    Modifier.semantics {
                        testTag = tag
                        contentDescription = text
                    }
                },
            ),
        style = MaterialTheme.typography.bodySmall,
        color = SheetInk,
        textAlign = textAlign,
    )
}

/** Ligne de coordonnées en petits caractères de l'en-tête. */
@Composable
private fun SheetFineText(text: String, tag: String? = null, description: String? = null) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = SheetMutedInk,
        modifier = if (tag == null) {
            Modifier
        } else {
            Modifier.semantics {
                testTag = tag
                if (description != null) contentDescription = description
            }
        },
    )
}
