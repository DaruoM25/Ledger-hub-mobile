package com.ledgerhub.presentation.quotes

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ledgerhub.domain.quote.Quote
import com.ledgerhub.domain.quote.QuoteStatus

/** Tags de test — contrat partagé entre l'UI (commonMain) et les tests (commonTest). */
object QuotesTags {
    const val SCREEN = "quotes_screen"
    const val LOADING_INDICATOR = "quotes_loading_indicator"
    const val LOAD_ERROR = "quotes_load_error"
    const val EMPTY_STATE = "quotes_empty_state"
    const val LIST = "quotes_list"
    const val CREATE_BUTTON = "quotes_create_button"

    fun filterChipTag(filter: QuoteStatusFilter) = "quotes_filter_${filter.name}"
    fun rowTag(quoteNumber: String) = "quotes_row_$quoteNumber"
    fun statusBadgeTag(quoteNumber: String) = "quotes_status_badge_$quoteNumber"
    fun editButtonTag(quoteNumber: String) = "quotes_edit_button_$quoteNumber"
    fun convertButtonTag(quoteNumber: String) = "quotes_convert_button_$quoteNumber"
    fun conversionLoadingTag(quoteNumber: String) = "quotes_convert_loading_$quoteNumber"
    fun convertedInvoiceTag(quoteNumber: String) = "quotes_converted_invoice_$quoteNumber"
    const val CONVERSION_ERROR = "quotes_conversion_error"
}

/** Libellé et couleur du badge de statut réglementaire d'un devis. */
internal fun QuoteStatus.label(): String = when (this) {
    QuoteStatus.DRAFT -> "Brouillon"
    QuoteStatus.SENT -> "Envoyé"
    QuoteStatus.ACCEPTED -> "Accepté"
    QuoteStatus.REJECTED -> "Refusé"
}

internal fun QuoteStatus.badgeColor(): Color = when (this) {
    QuoteStatus.DRAFT -> Color(0xFF9E9E9E)
    QuoteStatus.SENT -> Color(0xFF2196F3)
    QuoteStatus.ACCEPTED -> Color(0xFF4CAF50)
    QuoteStatus.REJECTED -> Color(0xFFF44336)
}

@Composable
fun QuotesView(
    viewModel: QuotesViewModel = remember { QuotesViewModel() },
    onCreateQuote: () -> Unit = {},
    onEditQuote: (Quote) -> Unit = {},
    onConvertToInvoice: ((Quote) -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsState()
    QuotesContent(
        uiState = uiState,
        onIntent = viewModel::processIntent,
        onCreateQuote = onCreateQuote,
        onEditQuote = onEditQuote,
        onConvertToInvoice = onConvertToInvoice,
    )
}

@Composable
internal fun QuotesContent(
    uiState: QuotesUiState,
    onIntent: (QuotesIntent) -> Unit = {},
    onCreateQuote: () -> Unit = {},
    onEditQuote: (Quote) -> Unit = {},
    onConvertToInvoice: ((Quote) -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { testTag = QuotesTags.SCREEN }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Devis", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TextButton(
                onClick = onCreateQuote,
                modifier = Modifier.semantics { testTag = QuotesTags.CREATE_BUTTON },
            ) {
                Text("＋ Nouveau", style = MaterialTheme.typography.labelMedium)
            }
        }

        // Filtres par statut
        QuotesFilterRow(
            selectedFilter = uiState.statusFilter,
            counts = uiState.counts,
            onFilterSelected = { onIntent(QuotesIntent.FilterSelected(it)) },
        )

        when {
            uiState.isLoading -> Row(
                modifier = Modifier.semantics { testTag = QuotesTags.LOADING_INDICATOR },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Text("Chargement des devis…")
            }

            uiState.loadErrorMessage != null -> Text(
                text = uiState.loadErrorMessage,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics {
                    testTag = QuotesTags.LOAD_ERROR
                    contentDescription = uiState.loadErrorMessage
                },
            )

            uiState.filteredQuotes.isEmpty() -> Text(
                text = "Aucun devis pour le moment",
                modifier = Modifier.semantics { testTag = QuotesTags.EMPTY_STATE },
            )

            else -> LazyColumn(
                modifier = Modifier.semantics { testTag = QuotesTags.LIST },
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(uiState.filteredQuotes, key = { it.number }) { quote ->
                    QuoteRow(
                        quote = quote,
                        isConverting = uiState.convertingQuoteNumber == quote.number,
                        convertedInvoiceNumber = uiState.invoiceFor(quote)?.number,
                        onEdit = { onEditQuote(quote) },
                        onConvert = {
                            if (onConvertToInvoice != null) {
                                onConvertToInvoice(quote)
                            } else {
                                onIntent(QuotesIntent.ConvertToInvoice(quote.number))
                            }
                        },
                    )
                }
            }
        }

        if (uiState.conversionErrorMessage != null) {
            Text(
                text = uiState.conversionErrorMessage,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics {
                    testTag = QuotesTags.CONVERSION_ERROR
                    contentDescription = uiState.conversionErrorMessage
                },
            )
        }
    }
}

@Composable
private fun QuotesFilterRow(
    selectedFilter: QuoteStatusFilter,
    counts: Map<QuoteStatusFilter, Int>,
    onFilterSelected: (QuoteStatusFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        QuoteStatusFilter.entries.forEach { filter ->
            val isSelected = filter == selectedFilter
            val count = counts[filter] ?: 0
            FilterChip(
                selected = isSelected,
                onClick = { onFilterSelected(filter) },
                label = { Text("${filter.label} ($count)", style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.semantics { testTag = QuotesTags.filterChipTag(filter) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}

@Composable
private fun QuoteRow(
    quote: Quote,
    isConverting: Boolean,
    convertedInvoiceNumber: String?,
    onEdit: () -> Unit,
    onConvert: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().semantics { testTag = QuotesTags.rowTag(quote.number) }) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(quote.number, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(quote.recipient.name, style = MaterialTheme.typography.bodyMedium)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (quote.isEditable) {
                        TextButton(
                            onClick = onEdit,
                            modifier = Modifier.semantics { testTag = QuotesTags.editButtonTag(quote.number) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text("Modifier", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    StatusBadge(quoteNumber = quote.number, status = quote.status)
                }
            }
            Text("Validité : ${quote.validityDate}", style = MaterialTheme.typography.bodySmall)
            Text("TTC : ${quote.totalTtc.cents / 100}.${(quote.totalTtc.cents % 100).toString().padStart(2, '0')} €")

            when {
                convertedInvoiceNumber != null -> Text(
                    text = "Convertie en facture $convertedInvoiceNumber",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics {
                        testTag = QuotesTags.convertedInvoiceTag(quote.number)
                        contentDescription = "Convertie en facture $convertedInvoiceNumber"
                    },
                )

                isConverting -> Row(
                    modifier = Modifier.semantics { testTag = QuotesTags.conversionLoadingTag(quote.number) },
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    Text("Conversion en facture…")
                }

                quote.isConvertibleToInvoice -> Button(
                    onClick = onConvert,
                    modifier = Modifier.semantics { testTag = QuotesTags.convertButtonTag(quote.number) },
                ) {
                    Text("Convertir en facture")
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(quoteNumber: String, status: QuoteStatus) {
    Surface(
        color = status.badgeColor(),
        contentColor = Color.White,
        shape = RoundedCornerShape(50),
        modifier = Modifier.semantics {
            testTag = QuotesTags.statusBadgeTag(quoteNumber)
            contentDescription = status.label()
        },
    ) {
        Text(
            text = status.label(),
            modifier = Modifier.padding(PaddingValues(horizontal = 10.dp, vertical = 4.dp)),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
