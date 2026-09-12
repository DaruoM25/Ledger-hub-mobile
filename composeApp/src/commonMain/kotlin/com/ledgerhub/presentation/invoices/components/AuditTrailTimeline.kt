package com.ledgerhub.presentation.invoices.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ledgerhub.domain.audit.AuditMilestone
import com.ledgerhub.domain.audit.AuditMilestoneId
import com.ledgerhub.domain.audit.AuditMilestoneState
import com.ledgerhub.domain.audit.abbreviateFingerprint
import com.ledgerhub.domain.i18n.StringKey
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.presentation.i18n.LocalAppLanguage
import com.ledgerhub.presentation.i18n.formatIsoDate
import com.ledgerhub.presentation.i18n.tr
import com.ledgerhub.presentation.invoices.containerColor
import com.ledgerhub.presentation.invoices.labelKey
import com.ledgerhub.presentation.invoices.onContainerColor
import com.ledgerhub.presentation.invoices.tagColor

/** Tags de test — contrat partagé entre l'UI (commonMain) et les tests (Robolectric / instrumenté). */
object AuditTrailTags {
    const val PANEL = "invoice_audit_trail_panel"
    const val SHA256 = "audit_step_sha256"

    fun stepTag(id: AuditMilestoneId): String = when (id) {
        AuditMilestoneId.CREATED -> "audit_step_created"
        AuditMilestoneId.SEALED -> "audit_step_sealed"
        AuditMilestoneId.PPF -> "audit_step_ppf"
        AuditMilestoneId.STATUS -> "audit_step_status"
    }
}

private val DoneColor = Color(0xFF00A86B)
private val PendingColor = Color(0xFF9E9E9E)
private val RejectedColor = Color(0xFFD32F2F)

private val BulletSize = 22.dp
private val ConnectorWidth = 2.dp
private const val PENDING_ALPHA = 0.55f

/**
 * Timeline verticale de traçabilité réglementaire (US-17 & US-28).
 *
 * Purement présentationnelle : l'état des jalons est résolu en amont par
 * [buildAuditTimeline][com.ledgerhub.domain.audit.buildAuditTimeline], sur la Piste d'Audit
 * Fiable réelle. Ce composant ne décide de rien — il ne fait qu'habiller ce qui lui est donné,
 * ce qui rend la logique testable sans composition (voir `AuditTimelineTest`).
 *
 * Chaque jalon est un unique nœud sémantique fusionné : la pastille colorée n'est jamais le seul
 * porteur d'information, un lecteur d'écran annonce l'étape entière — libellé, horodatage,
 * empreinte, badge d'état — en un seul arrêt de focus, au lieu d'égrener quatre fragments dont le
 * premier répète le libellé.
 */
@Composable
fun AuditTrailTimeline(
    milestones: List<AuditMilestone>,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .semantics { testTag = AuditTrailTags.PANEL },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1D24),
        ),
        border = BorderStroke(1.dp, Color(0xFF2C303B)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = tr(StringKey.AUDIT_PANEL_TITLE),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
            )
            Spacer(modifier = Modifier.height(16.dp))
            milestones.forEachIndexed { index, milestone ->
                MilestoneRow(
                    milestone = milestone,
                    // Le dernier jalon ne tire pas de connecteur : rien ne le suit.
                    isLast = index == milestones.lastIndex,
                )
            }
        }
    }
}

/**
 * Une étape : sa pastille et son connecteur à gauche, son contenu à droite.
 *
 * `IntrinsicSize.Min` sur la ligne permet au connecteur de s'étirer sur exactement la hauteur du
 * contenu voisin — sans hauteur codée en dur, qui casserait dès qu'une traduction passe sur deux
 * lignes (le libellé du coffre-fort AWS, notamment, est long dans les deux langues).
 */
@Composable
private fun MilestoneRow(milestone: AuditMilestone, isLast: Boolean) {
    val language = LocalAppLanguage.current
    val accent = milestone.state.accentColor()
    val label = tr(milestone.id.labelKey())

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .semantics(mergeDescendants = true) { testTag = AuditTrailTags.stepTag(milestone.id) },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.width(BulletSize),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Bullet(state = milestone.state, accent = accent)
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(ConnectorWidth)
                        .fillMaxHeight()
                        .background(accent.copy(alpha = 0.35f)),
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = if (isLast) 0.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (milestone.state == AuditMilestoneState.PENDING) {
                    Color(0xFF94A3B8)
                } else {
                    Color.White
                },
            )
            milestone.timestampIso?.let { iso ->
                Text(
                    text = formatTimestamp(iso, language.formatDate(iso)),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF94A3B8),
                    fontWeight = FontWeight.Medium,
                )
            }
            milestone.fingerprint?.let { Fingerprint(it) }
            // Motif de la décision de l'administration ou du refus.
            milestone.reason?.takeIf { it.isNotBlank() }?.let { reason ->
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFCBD5E1),
                )
            }
            MilestoneBadge(milestone = milestone, accent = accent)
        }
    }
}

@Composable
private fun Bullet(state: AuditMilestoneState, accent: Color) {
    Box(
        modifier = Modifier
            .size(BulletSize)
            .background(
                color = if (state == AuditMilestoneState.PENDING) Color.Transparent else accent,
                shape = CircleShape,
            )
            .border(width = 2.dp, color = accent, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val glyph = when (state) {
            AuditMilestoneState.DONE -> "✓"
            AuditMilestoneState.REJECTED -> "!"
            AuditMilestoneState.PENDING -> null
        }
        if (glyph != null) {
            Text(
                text = glyph,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}

@Composable
private fun Fingerprint(fingerprint: String) {
    val text = "${tr(StringKey.AUDIT_SHA256_LABEL)} : ${fingerprint.abbreviateFingerprint()}"
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
        color = Color(0xFF94A3B8),
        fontWeight = FontWeight.Medium,
        modifier = Modifier.semantics { testTag = AuditTrailTags.SHA256 },
    )
}

@Composable
private fun MilestoneBadge(milestone: AuditMilestone, accent: Color) {
    val status = milestone.reportedStatus
    val text = when {
        status != null -> tr(status.labelKey())
        milestone.state == AuditMilestoneState.PENDING -> tr(StringKey.AUDIT_STEP_PENDING)
        else -> tr(StringKey.AUDIT_STEP_DONE)
    }
    val isPositive = status == InvoiceStatus.APPROVED ||
        status == InvoiceStatus.DEPOSITED ||
        status == InvoiceStatus.PAID ||
        (status == null && milestone.state == AuditMilestoneState.DONE)

    val isNegative = status == InvoiceStatus.REJECTED ||
        status == InvoiceStatus.REFUSED ||
        (status == null && milestone.state == AuditMilestoneState.REJECTED)

    val container = when {
        isPositive -> Color(0xFF1B382B)
        isNegative -> Color(0xFF3E1B1B)
        else -> Color(0xFF242933)
    }
    val content = when {
        isPositive -> Color(0xFF81C784)
        isNegative -> Color(0xFFEF9A9A)
        else -> Color(0xFF94A3B8)
    }

    Surface(
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(6.dp),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 20.dp)
                .padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(content, CircleShape),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun AuditMilestoneState.accentColor(): Color = when (this) {
    AuditMilestoneState.DONE -> DoneColor
    AuditMilestoneState.PENDING -> PendingColor
    AuditMilestoneState.REJECTED -> RejectedColor
}

private fun AuditMilestoneId.labelKey(): StringKey = when (this) {
    AuditMilestoneId.CREATED -> StringKey.AUDIT_STEP_CREATED
    AuditMilestoneId.SEALED -> StringKey.AUDIT_STEP_SEALED
    AuditMilestoneId.PPF -> StringKey.AUDIT_STEP_PPF
    AuditMilestoneId.STATUS -> StringKey.AUDIT_STEP_STATUS
}

/**
 * Un horodatage de PAF est un instant ISO 8601 complet (`2026-08-29T14:33:07.512Z`), pas une
 * simple date : [formatIsoDate] n'en formate que la partie calendaire, l'heure est reprise telle
 * quelle. C'est l'heure UTC qui fait foi dans une piste d'audit — la convertir en heure locale
 * ferait diverger la preuve affichée de la preuve archivée.
 */
private fun formatTimestamp(iso: String, formattedDate: String): String {
    val time = iso.substringAfter('T', missingDelimiterValue = "").substringBefore('.').removeSuffix("Z")
    return if (time.isBlank()) formattedDate else "$formattedDate · ${time.removeSuffix("Z")} UTC"
}

private fun com.ledgerhub.domain.i18n.AppLanguage.formatDate(iso: String): String =
    formatIsoDate(iso.substringBefore('T'), this)
