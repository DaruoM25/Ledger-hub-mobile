package com.ledgerhub.presentation.invoicedetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceStatus

/** Tags de test — contrat partagé entre l'UI (commonMain) et les tests (commonTest). */
object InvoiceDetailTags {
    const val SCREEN = "invoice_detail_screen"
    const val STATUS_BADGE = "invoice_detail_status_badge"
    const val TOTAL_TTC = "invoice_detail_total_ttc"
    const val CREATE_CREDIT_NOTE_BUTTON = "invoice_detail_create_credit_note_button"
}

internal fun InvoiceStatus.label(): String = when (this) {
    InvoiceStatus.DRAFT -> "Brouillon"
    InvoiceStatus.PENDING_REGULARIZATION -> "À régulariser"
    InvoiceStatus.DEPOSITED -> "Déposée"
    InvoiceStatus.APPROVED -> "Approuvée par l'administration"
    InvoiceStatus.PAID -> "Encaissée"
    InvoiceStatus.REJECTED -> "Rejetée par la plateforme"
    InvoiceStatus.REFUSED -> "Refusée"
    InvoiceStatus.CANCELLED -> "Annulée"
}

internal fun InvoiceStatus.badgeColor(): Color = when (this) {
    InvoiceStatus.DRAFT -> Color(0xFF9E9E9E)
    InvoiceStatus.PENDING_REGULARIZATION -> Color(0xFFD97706)
    InvoiceStatus.DEPOSITED -> Color(0xFF1565C0)
    InvoiceStatus.APPROVED -> Color(0xFF00A86B)
    InvoiceStatus.PAID -> Color(0xFF4CAF50)
    InvoiceStatus.REJECTED -> Color(0xFFD32F2F)
    InvoiceStatus.REFUSED -> Color(0xFFD84315)
    InvoiceStatus.CANCELLED -> Color(0xFFF44336)
}

/** Composable stateful : charge l'état (avoir existant ?) via [InvoiceDetailViewModel]. */
@Composable
fun InvoiceDetailScreen(
    viewModel: InvoiceDetailViewModel,
    onCreateCreditNoteClick: (Invoice) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    InvoiceDetailContent(
        uiState = uiState,
        onCreateCreditNoteClick = { onCreateCreditNoteClick(uiState.invoice) },
    )
}

@Composable
internal fun InvoiceDetailContent(
    uiState: InvoiceDetailUiState,
    onCreateCreditNoteClick: () -> Unit = {},
) {
    val invoice = uiState.invoice

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .semantics { testTag = InvoiceDetailTags.SCREEN }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(invoice.number, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        StatusBadge(invoice.status)
        Text("Émetteur : ${invoice.issuer.name}")
        Text("Destinataire : ${invoice.recipient.name}")
        Text(
            text = "TTC : ${formatCents(invoice.totalTtc.cents)} €",
            modifier = Modifier.semantics { testTag = InvoiceDetailTags.TOTAL_TTC },
            style = MaterialTheme.typography.titleMedium,
        )

        if (uiState.canCreateCreditNote) {
            Button(
                onClick = onCreateCreditNoteClick,
                modifier = Modifier.semantics { testTag = InvoiceDetailTags.CREATE_CREDIT_NOTE_BUTTON },
            ) {
                Text("Annuler par un avoir")
            }
        }
    }
}

@Composable
private fun StatusBadge(status: InvoiceStatus) {
    Surface(
        color = status.badgeColor(),
        contentColor = Color.White,
        shape = RoundedCornerShape(50),
        modifier = Modifier.semantics {
            testTag = InvoiceDetailTags.STATUS_BADGE
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

private fun formatCents(cents: Long): String {
    val sign = if (cents < 0) "-" else ""
    val absCents = kotlin.math.abs(cents)
    val whole = absCents / 100
    val fraction = (absCents % 100).toString().padStart(2, '0')
    return "$sign$whole.$fraction"
}
