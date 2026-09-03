package com.ledgerhub.presentation.compliance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ledgerhub.domain.compliance.ComplianceCheck
import com.ledgerhub.domain.compliance.ComplianceFinding
import com.ledgerhub.domain.compliance.ComplianceReport
import com.ledgerhub.domain.compliance.ComplianceStatus
import com.ledgerhub.domain.i18n.StringKey
import com.ledgerhub.presentation.i18n.tr
import com.ledgerhub.presentation.theme.LedgerHubTheme

/**
 * Tags de test — contrat partagé entre le panneau (commonMain) et les trois niveaux de tests.
 * Les huit valeurs sont **figées par le cahier des charges US-24** : les modifier casserait les
 * suites QA. Elles sont verrouillées par `CompliancePanelTagsTest` (N1).
 */
object CompliancePanelTags {
    const val PANEL = "compliance_panel"
    const val SCAN_BUTTON = "compliance_scan_btn"
    const val CHECKLIST = "compliance_checklist"
    const val ALERT = "compliance_alert"

    /**
     * Tag de la ligne d'un contrôle. `when` exhaustif plutôt qu'une interpolation sur le nom de
     * l'enum : la forme littérale est ce que le cahier des charges fige, et un cinquième contrôle
     * devra déclarer son tag pour que le code compile.
     */
    fun check(check: ComplianceCheck): String = when (check) {
        ComplianceCheck.SIRET -> "compliance_check_siret"
        ComplianceCheck.VAT -> "compliance_check_vat"
        ComplianceCheck.LEGAL_MENTIONS -> "compliance_check_legal"
        ComplianceCheck.FACTURX_STRUCTURE -> "compliance_check_facturx"
    }

    /** Les huit tags imposés — sert aux contrôles d'unicité et de collision. */
    fun specified(): List<String> =
        listOf(PANEL, SCAN_BUTTON, CHECKLIST, ALERT) + ComplianceCheck.entries.map { check(it) }
}

// ── Sémiologie des gravités ───────────────────────────────────────────────────
// Reprise de la palette du thème : le vert des encaissements, l'ambre des statuts en attente, le
// rouge des erreurs. Le panneau n'introduit pas une quatrième palette pour dire la même chose.
private val PassedGlyph = "✓"
private val WarningGlyph = "⚠"
private val FailedGlyph = "✕"

@Composable
private fun statusColor(status: ComplianceStatus): Color = when (status) {
    ComplianceStatus.PASSED -> LedgerHubTheme.palette.StatusPaidFg
    ComplianceStatus.WARNING -> LedgerHubTheme.palette.StatusPendingFg
    ComplianceStatus.FAILED -> LedgerHubTheme.palette.ErrorText
}

private fun statusGlyph(status: ComplianceStatus): String = when (status) {
    ComplianceStatus.PASSED -> PassedGlyph
    ComplianceStatus.WARNING -> WarningGlyph
    ComplianceStatus.FAILED -> FailedGlyph
}

/**
 * Panneau d'audit et de conformité Factur-X 2026 (US-24).
 *
 * ## Un audit à la demande, et un rapport qui ne survit pas à la frappe
 *
 * Le rapport n'est produit qu'au geste de l'utilisateur — d'où le bouton — et il est invalidé dès
 * la frappe suivante (voir `InvoiceFormViewModel`). Un rapport périmé affiché comme actuel serait
 * pire que pas de rapport du tout : il ferait émettre une facture sur la foi d'un contrôle qui ne
 * porte plus sur elle.
 *
 * @param report `null` tant qu'aucun scan n'a eu lieu — l'absence de rapport est un état à part
 *   entière, distinct d'un rapport vide.
 */
@Composable
fun CompliancePanel(
    report: ComplianceReport?,
    enabled: Boolean,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics { testTag = CompliancePanelTags.PANEL },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🛡️")
                Text(
                    text = tr(StringKey.COMPLIANCE_PANEL_TITLE),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = tr(StringKey.COMPLIANCE_PANEL_SUBTITLE),
                style = MaterialTheme.typography.bodySmall,
                color = LedgerHubTheme.palette.SecondaryText,
            )

            Button(
                onClick = onScan,
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(containerColor = LedgerHubTheme.palette.Accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics { testTag = CompliancePanelTags.SCAN_BUTTON },
            ) {
                Text(tr(StringKey.COMPLIANCE_SCAN_ACTION))
            }

            if (report == null) {
                Text(
                    text = tr(StringKey.COMPLIANCE_NOT_SCANNED),
                    style = MaterialTheme.typography.bodySmall,
                    color = LedgerHubTheme.palette.SecondaryText,
                )
            } else {
                ComplianceChecklist(report)
                ComplianceAlert(report)
            }
        }
    }
}

/**
 * Checklist des quatre contrôles.
 *
 * Le conteneur est tagué **sans fusionner** : chaque ligne doit rester atteignable une par une,
 * sous son propre tag imposé. Les fusionner en ferait un bloc de texte unique — illisible au
 * lecteur d'écran, et invisible aux assertions par contrôle.
 */
@Composable
private fun ComplianceChecklist(report: ComplianceReport) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = false) { testTag = CompliancePanelTags.CHECKLIST },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ComplianceCheck.ordered().forEach { check ->
            report.findingFor(check)?.let { ChecklistRow(it) }
        }
    }
}

/**
 * Ligne de checklist — nœud sémantique **fusionné** : un contrôle est une unité, pas un glyphe
 * suivi de deux textes. Elle n'a aucun enfant interactif, la fusion ne masque donc rien.
 */
@Composable
private fun ChecklistRow(finding: ComplianceFinding) {
    val color = statusColor(finding.status)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .semantics(mergeDescendants = true) { testTag = CompliancePanelTags.check(finding.check) },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = statusGlyph(finding.status),
            color = color,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = tr(finding.check.titleKey),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = tr(finding.messageKey),
                style = MaterialTheme.typography.bodySmall,
                color = LedgerHubTheme.palette.SecondaryText,
            )
        }
    }
}

/**
 * Bandeau d'alerte — **ambre** pour un écart admissible, **rouge** pour un écart bloquant.
 *
 * Rendu seulement lorsqu'il y a quelque chose à signaler : une facture conforme affiche sa
 * conformité en clair, sans bandeau. Un bandeau permanent qui dirait tantôt « tout va bien »
 * tantôt « rien ne va » cesserait d'être lu.
 */
@Composable
private fun ComplianceAlert(report: ComplianceReport) {
    if (report.isCompliant) {
        Text(
            text = tr(StringKey.COMPLIANCE_ALL_PASSED),
            style = MaterialTheme.typography.bodySmall,
            color = LedgerHubTheme.palette.StatusPaidFg,
            fontWeight = FontWeight.SemiBold,
        )
        return
    }

    val blocking = report.overallStatus.isBlocking
    val background = if (blocking) LedgerHubTheme.palette.StatusPendingBg else LedgerHubTheme.palette.StatusDraftBg
    val foreground = if (blocking) LedgerHubTheme.palette.ErrorText else LedgerHubTheme.palette.StatusPendingFg
    val title = if (blocking) {
        tr(StringKey.COMPLIANCE_ALERT_ERROR_TITLE)
    } else {
        tr(StringKey.COMPLIANCE_ALERT_WARNING_TITLE)
    }

    Surface(
        color = background,
        contentColor = foreground,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, foreground),
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { testTag = CompliancePanelTags.ALERT },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(statusGlyph(report.overallStatus), fontWeight = FontWeight.Bold)
                Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            // Les écarts bloquants d'abord : ce sont eux qui empêchent l'émission.
            (report.failures + report.warnings).forEach { finding ->
                Text(
                    text = "• " + tr(finding.messageKey),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
