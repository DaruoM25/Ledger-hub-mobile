package com.ledgerhub.presentation.invoiceform

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ledgerhub.domain.i18n.StringKey
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.domain.invoice.computeVatBreakdown
import com.ledgerhub.domain.invoice.parseAmountToCents
import com.ledgerhub.presentation.components.filterAmount
import com.ledgerhub.presentation.components.filterQuantity
import com.ledgerhub.presentation.components.filterSiret
import com.ledgerhub.presentation.i18n.LocalAppLanguage
import com.ledgerhub.presentation.i18n.tr
import com.ledgerhub.presentation.invoices.formatMoney

/** Tags de test — contrat partagé entre l'aperçu WYSIWYG (UI) et les tests. */
object InvoicePaperCanvasTags {
    const val CANVAS = "invoice_paper_canvas"
    const val CABINET_NAME = "invoice_paper_cabinet_name"
    const val CABINET_SIRET = "invoice_paper_cabinet_siret"
    const val CLIENT_NAME = "invoice_paper_client_name"
    const val CLIENT_SIRET = "invoice_paper_client_siret"
    const val TOTAL_HT = "invoice_paper_total_ht"
    const val TOTAL_VAT = "invoice_paper_total_vat"
    const val TOTAL_TTC = "invoice_paper_total_ttc"

    fun lineLabelTag(index: Int) = "invoice_paper_line_${index}_label"
    fun lineQuantityTag(index: Int) = "invoice_paper_line_${index}_quantity"
    fun lineUnitPriceTag(index: Int) = "invoice_paper_line_${index}_unit_price"
    fun lineTotalTag(index: Int) = "invoice_paper_line_${index}_total_ht"
    fun vatBreakdownTag(rate: VatRate) = "invoice_paper_vat_breakdown_${rate.name}"
}

/** Couleur d'accent affichée sur le contour d'un champ transparent lorsqu'il a le focus. */
private val PaperFieldFocusOutline = Color(0xFF1E88E5)

/** Aplat tres leger revele au focus — signale la cellule active sans casser l'illusion papier. */
private val PaperFieldFocusFill = Color(0x141E88E5)
private val PaperDeskBackground = Color(0xFFE3E3E8)
private val PaperDividerColor = Color(0xFFE0E0E0)
private val PaperMutedText = Color(0xFF757575)

/**
 * Aperçu WYSIWYG de la facture — reproduit une feuille A4 posée sur un bureau gris, avec des
 * champs éditables "en place" (au lieu du formulaire classique de [InvoiceFormContent]).
 * Chaque champ reste relié aux mêmes [InvoiceFormIntent] que le formulaire : la feuille n'est
 * qu'une seconde représentation du même [InvoiceFormUiState], jamais une source de vérité propre.
 */
@Composable
fun InvoicePaperCanvas(
    uiState: InvoiceFormUiState,
    onIntent: (InvoiceFormIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fieldsEnabled = uiState.isFormEnabled

    Surface(
        color = PaperDeskBackground,
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .semantics { testTag = InvoicePaperCanvasTags.CANVAS },
    ) {
        Surface(
            color = Color.White,
            shadowElevation = 6.dp,
            shape = RoundedCornerShape(2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                PaperHeader(uiState = uiState, enabled = fieldsEnabled, onIntent = onIntent)
                HorizontalDivider(color = PaperDividerColor)
                PaperLinesTable(uiState = uiState, enabled = fieldsEnabled, onIntent = onIntent)
                HorizontalDivider(color = PaperDividerColor)
                PaperFooterTotals(uiState = uiState)
                HorizontalDivider(color = PaperDividerColor)
                PaperLegalFooter(uiState = uiState, enabled = fieldsEnabled, onIntent = onIntent)
            }
        }
    }
}

@Composable
private fun PaperHeader(
    uiState: InvoiceFormUiState,
    enabled: Boolean,
    onIntent: (InvoiceFormIntent) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        // Header gauche : émetteur = identité fixe du cabinet (non éditable, comme sur le Web).
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(PaperDividerColor, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("🏢")
            }
            Text(
                text = uiState.issuer.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.semantics { testTag = InvoicePaperCanvasTags.CABINET_NAME },
            )
            Text(
                text = uiState.issuer.siret,
                style = MaterialTheme.typography.bodySmall.copy(color = PaperMutedText),
                modifier = Modifier.semantics { testTag = InvoicePaperCanvasTags.CABINET_SIRET },
            )
        }

        // Header droit : encart client, éditable en place, bordé pour le distinguer de l'émetteur.
        Column(
            modifier = Modifier
                .weight(1f)
                .border(width = 1.dp, color = PaperDividerColor, shape = RoundedCornerShape(6.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = tr(StringKey.PREVIEW_BILL_TO),
                style = MaterialTheme.typography.labelSmall,
                color = PaperMutedText,
            )
            PaperField(
                value = uiState.clientName,
                tag = InvoicePaperCanvasTags.CLIENT_NAME,
                enabled = enabled,
                textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                onValueChange = { onIntent(InvoiceFormIntent.ClientNameChanged(it)) },
            )
            PaperField(
                value = uiState.clientSiret,
                tag = InvoicePaperCanvasTags.CLIENT_SIRET,
                enabled = enabled,
                textStyle = MaterialTheme.typography.bodySmall.copy(color = PaperMutedText),
                keyboardType = KeyboardType.Number,
                inputFilter = ::filterSiret,
                onValueChange = { onIntent(InvoiceFormIntent.ClientSiretChanged(it)) },
            )
        }
    }
}

@Composable
private fun PaperLinesTable(
    uiState: InvoiceFormUiState,
    enabled: Boolean,
    onIntent: (InvoiceFormIntent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            TableHeaderCell(tr(StringKey.PREVIEW_COL_DESCRIPTION), weight = 3f)
            TableHeaderCell(tr(StringKey.PREVIEW_COL_QUANTITY), weight = 1f)
            TableHeaderCell(tr(StringKey.PREVIEW_COL_UNIT_PRICE_HT), weight = 1.5f)
            TableHeaderCell(tr(StringKey.PREVIEW_COL_TOTAL_HT), weight = 1.5f, textAlign = TextAlign.End)
        }
        uiState.lines.forEachIndexed { index, line ->
            PaperLineRow(index = index, line = line, enabled = enabled, onIntent = onIntent)
        }
    }
}

@Composable
private fun PaperLineRow(
    index: Int,
    line: InvoiceLineFormState,
    enabled: Boolean,
    onIntent: (InvoiceFormIntent) -> Unit,
) {
    fun update(
        label: String = line.label,
        quantity: String = line.quantity,
        unitPriceHt: String = line.unitPriceHt,
        vatRate: VatRate = line.vatRate,
    ) {
        onIntent(InvoiceFormIntent.UpdateLine(index, label, quantity, unitPriceHt, vatRate))
    }

    val language = LocalAppLanguage.current

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PaperField(
            value = line.label,
            tag = InvoicePaperCanvasTags.lineLabelTag(index),
            enabled = enabled,
            modifier = Modifier.weight(3f),
            onValueChange = { update(label = it) },
        )
        PaperField(
            value = line.quantity,
            tag = InvoicePaperCanvasTags.lineQuantityTag(index),
            enabled = enabled,
            modifier = Modifier.weight(1f),
            keyboardType = KeyboardType.Number,
            inputFilter = ::filterQuantity,
            onValueChange = { update(quantity = it) },
        )
        PaperField(
            value = line.unitPriceHt,
            tag = InvoicePaperCanvasTags.lineUnitPriceTag(index),
            enabled = enabled,
            modifier = Modifier.weight(1.5f),
            keyboardType = KeyboardType.Decimal,
            inputFilter = ::filterAmount,
            onValueChange = { update(unitPriceHt = it) },
        )
        // Total de ligne en lecture seule — **HT**, comme la 5e colonne de l'aperçu A4 (US-14) et
        // comme l'exige la présentation PPF 2026. Calculé à la volée depuis la saisie brute et
        // jamais stocké : aucune divergence possible avec le total général du ViewModel.
        Text(
            text = formatMoney(line.liveTotalHtCents(), language),
            modifier = Modifier
                .weight(1.5f)
                .semantics { testTag = InvoicePaperCanvasTags.lineTotalTag(index) },
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun PaperFooterTotals(uiState: InvoiceFormUiState) {
    val language = LocalAppLanguage.current
    val breakdown = computeVatBreakdown(uiState.liveValidDomainLines())
    val vatLabel = tr(StringKey.PAPER_VAT_LABEL)
    val onLabel = tr(StringKey.PAPER_VAT_BASE_ON)

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        PaperTotalLine(
            label = tr(StringKey.PREVIEW_TOTAL_HT),
            value = formatMoney(uiState.totalHt.cents, language),
            tag = InvoicePaperCanvasTags.TOTAL_HT,
        )
        breakdown.forEach { vatBreakdown ->
            Text(
                text = "$vatLabel ${vatBreakdown.rate.label} $onLabel " +
                    "${formatMoney(vatBreakdown.baseHt.cents, language)} : " +
                    formatMoney(vatBreakdown.vatAmount.cents, language),
                style = MaterialTheme.typography.bodySmall,
                color = PaperMutedText,
                modifier = Modifier.semantics { testTag = InvoicePaperCanvasTags.vatBreakdownTag(vatBreakdown.rate) },
            )
        }
        PaperTotalLine(
            label = tr(StringKey.PREVIEW_TOTAL_VAT),
            value = formatMoney(uiState.totalVat.cents, language),
            tag = InvoicePaperCanvasTags.TOTAL_VAT,
        )
        PaperTotalLine(
            label = tr(StringKey.PREVIEW_TOTAL_TTC),
            value = formatMoney(uiState.totalTtc.cents, language),
            tag = InvoicePaperCanvasTags.TOTAL_TTC,
            emphasize = true,
        )
    }
}

/** Ligne du bloc récapitulatif — même composition « Libellé : montant » que l'aperçu A4 (US-14). */
@Composable
private fun PaperTotalLine(label: String, value: String, tag: String, emphasize: Boolean = false) {
    val text = "$label : $value"
    Text(
        text = text,
        style = if (emphasize) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyMedium,
        fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier.semantics {
            testTag = tag
            contentDescription = text
        },
    )
}

@Composable
private fun RowScope.TableHeaderCell(text: String, weight: Float, textAlign: TextAlign = TextAlign.Start) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = PaperMutedText,
        textAlign = textAlign,
    )
}

/**
 * Champ transparent "WYSIWYG" : ressemble à du texte plat par défaut, ne révèle un léger contour
 * bleu clair que lorsqu'il a le focus, pour signaler qu'il est éditable sans casser l'illusion de
 * feuille de papier.
 */
@Composable
private fun PaperField(
    value: String,
    tag: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    keyboardType: KeyboardType = KeyboardType.Text,
    /** Normalisation de la frappe — mêmes règles que le formulaire classique (voir `InputFilters`). */
    inputFilter: ((String) -> String)? = null,
) {
    var isFocused by remember { mutableStateOf(false) }
    val outlineColor = if (isFocused) PaperFieldFocusOutline else Color.Transparent
    val fillColor = if (isFocused) PaperFieldFocusFill else Color.Transparent

    BasicTextField(
        value = value,
        onValueChange = { raw -> onValueChange(inputFilter?.invoke(raw) ?: raw) },
        enabled = enabled,
        singleLine = true,
        textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(PaperFieldFocusOutline),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused }
            // 48 dp : cible tactile minimale Material — une cellule de tableau reste tapable au
            // doigt même quand son texte ne fait qu'une ligne.
            .height(48.dp)
            .background(fillColor, RoundedCornerShape(4.dp))
            .border(width = 1.5.dp, color = outlineColor, shape = RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 12.dp)
            .requiredHeight(48.dp)
            .semantics(mergeDescendants = true) { testTag = tag },
    )
}

/**
 * Total HT d'une ligne recalculé en direct depuis la saisie brute (texte), sans passer par la
 * revalidation du ViewModel — nécessaire pour que le total de ligne se mette à jour à chaque
 * frappe, y compris pendant une saisie intermédiaire encore invalide (auquel cas 0 est affiché).
 */
private fun InvoiceLineFormState.liveTotalHtCents(): Long = toLiveDomainLineOrNull()?.totalHt?.cents ?: 0L

/** Lignes valides recalculées en direct — même règle que le ViewModel : une ligne invalide n'entre pas dans les totaux. */
private fun InvoiceFormUiState.liveValidDomainLines(): List<InvoiceLine> = lines.mapNotNull { it.toLiveDomainLineOrNull() }

/** Mêmes règles de validité que [InvoiceFormViewModel] — sinon le total de ligne et le total
 * général afficheraient des valeurs divergentes pour une ligne encore incomplète. */
private fun InvoiceLineFormState.toLiveDomainLineOrNull(): InvoiceLine? {
    if (label.isBlank()) return null
    val quantity = quantity.toIntOrNull() ?: return null
    if (quantity <= 0) return null
    val unitPriceCents = parseAmountToCents(unitPriceHt) ?: return null
    if (unitPriceCents <= 0) return null
    return InvoiceLine(label = label, quantity = quantity, unitPriceHt = Money(unitPriceCents), vatRate = vatRate)
}

/**
 * Pied de page réglementaire de la feuille (US-16) : la mention de l'article L.441-10 — ou la
 * formule de courtoisie — suivie de la case qui les commute.
 *
 * Corps réduit et encre grisée, comme le pied de l'aperçu A4 (US-14) : ces mentions sont
 * obligatoires mais ne doivent pas concurrencer le montant à payer. La case, elle, réutilise
 * telle quelle celle du formulaire classique — même composable, même tag, même sémantique : les
 * deux modes de saisie restent deux vues d'un seul état, jamais deux implémentations.
 */
@Composable
private fun PaperLegalFooter(
    uiState: InvoiceFormUiState,
    enabled: Boolean,
    onIntent: (InvoiceFormIntent) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LegalFooterText(
            applyB2bPenalties = uiState.applyB2bPenalties,
            style = MaterialTheme.typography.bodySmall,
            color = PaperMutedText,
        )
        B2bPenaltiesCheckbox(
            checked = uiState.applyB2bPenalties,
            enabled = enabled,
            onToggle = { onIntent(InvoiceFormIntent.ToggleB2bPenalties(it)) },
        )
    }
}
