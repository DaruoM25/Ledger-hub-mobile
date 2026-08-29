package com.ledgerhub.presentation.invoices.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ledgerhub.domain.i18n.StringKey
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.presentation.i18n.LocalAppLanguage
import com.ledgerhub.presentation.i18n.formatIsoDate
import com.ledgerhub.presentation.i18n.tr
import com.ledgerhub.presentation.invoices.format
import com.ledgerhub.presentation.invoices.labelKey
import com.ledgerhub.presentation.invoices.tagColor

/** Tags de test — contrat partagé entre l'UI (commonMain) et les tests (commonTest). */
object InvoiceCardTags {
    fun card(number: String) = "invoice_card_$number"
    fun statusTag(number: String) = "invoice_card_status_$number"
    fun lockTag(number: String) = "invoice_card_lock_$number"
    const val FACTURX_BADGE = "invoice_card_facturx_badge"
}

/** Mention de conformité — réforme française de la facturation électronique 2026 (Factur-X). */
const val FACTURX_BADGE_LABEL = "Conforme Factur-X 2026"

/**
 * Carte d'une facture dans la liste (US-02) : numéro, statut fiscal, destinataire, date,
 * badge de conformité Factur-X et montant TTC formaté. Toute facture servie par le
 * `LedgerRepository` est un flux structuré conforme 2026, d'où le badge systématique.
 */
@Composable
fun InvoiceCard(
    invoice: Invoice,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { testTag = InvoiceCardTags.card(invoice.number) },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = invoice.number,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    // La carte n'expose aucune action mutable ; le cadenas annonce simplement
                    // qu'une fois émise, la facture ne se modifie plus — visible sans ouvrir
                    // le détail (règle portée par Invoice.isEditable).
                    if (!invoice.isEditable) {
                        val lockedHint = tr(StringKey.INVOICE_LOCKED_HINT)
                        Text(
                            text = "🔒",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.semantics {
                                testTag = InvoiceCardTags.lockTag(invoice.number)
                                contentDescription = lockedHint
                            },
                        )
                    }
                }
                StatusTag(invoice.status, InvoiceCardTags.statusTag(invoice.number))
            }

            Text(invoice.recipient.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${tr(StringKey.LIST_ISSUED_ON)} ${formatIsoDate(invoice.issueDate, LocalAppLanguage.current)}",
                style = MaterialTheme.typography.bodySmall,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FacturXBadge()
                Text(
                    text = invoice.totalTtc.format(LocalAppLanguage.current),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** Pastille de statut réutilisable (liste + détail). [tag] permet de la cibler en test. */
@Composable
internal fun StatusTag(status: InvoiceStatus, tag: String) {
    val label = tr(status.labelKey())
    Surface(
        color = status.tagColor(),
        contentColor = Color.White,
        shape = RoundedCornerShape(50),
        modifier = Modifier.semantics {
            testTag = tag
            contentDescription = label
        },
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(PaddingValues(horizontal = 10.dp, vertical = 4.dp)),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/** Badge vert de conformité Factur-X. [tag] par défaut = celui de la carte de liste. */
@Composable
internal fun FacturXBadge(tag: String = InvoiceCardTags.FACTURX_BADGE) {
    val label = tr(StringKey.FACTURX_BADGE)
    Surface(
        color = Color(0xFFE8F5E9),
        contentColor = Color(0xFF1B5E20),
        shape = RoundedCornerShape(50),
        modifier = Modifier.semantics {
            testTag = tag
            contentDescription = label
        },
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(PaddingValues(horizontal = 10.dp, vertical = 4.dp)),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}
