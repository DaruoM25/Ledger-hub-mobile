package com.ledgerhub.presentation.invoices.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * Timeline verticale de traçabilité réglementaire (US-17).
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
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .semantics { testTag = AuditTrailTags.PANEL },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = tr(StringKey.AUDIT_PANEL_TITLE),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
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
            // La fusion est ce qui donne au jalon un texte propre : sans elle, le libellé, le badge
            // et l'empreinte restent des nœuds frères et l'étape n'existe comme tout ni pour un
            // lecteur d'écran, ni pour une assertion de test. Le texte annoncé est celui réellement
            // affiché — pas une `contentDescription` parallèle, qui divergerait tôt ou tard de lui.
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
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(
                    alpha = if (milestone.state == AuditMilestoneState.PENDING) PENDING_ALPHA else 1f,
                ),
            )
            milestone.timestampIso?.let { iso ->
                Text(
                    text = formatTimestamp(iso, language.formatDate(iso)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            milestone.fingerprint?.let { Fingerprint(it) }
            // Motif de la décision de l'administration. Affiché sous le jalon concerné plutôt que
            // dans un encart séparé : c'est la justification de cet état précis, détachée elle
            // perdrait son référent.
            milestone.reason?.takeIf { it.isNotBlank() }?.let { reason ->
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        // Glyphe en plus de la couleur : la seule teinte ne distinguerait pas les états pour un
        // utilisateur daltonien.
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
                // Redondant à l'oral : le badge énonce déjà l'état en toutes lettres. Annoncer
                // « ✓ » en tête de chaque jalon n'ajoute rien et alourdit la lecture.
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}

/**
 * Empreinte d'intégrité. Tronquée à l'affichage — 64 caractères hexadécimaux sont illisibles sur
 * un téléphone — et en police à chasse fixe, la seule qui rende une empreinte comparable d'un
 * coup d'œil.
 */
@Composable
private fun Fingerprint(fingerprint: String) {
    val text = "${tr(StringKey.AUDIT_SHA256_LABEL)} : ${fingerprint.abbreviateFingerprint()}"
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        // Pas de `contentDescription` ici : le texte de ce `Text` est déjà repris dans la
        // fusion du jalon, la doubler ferait annoncer l'empreinte deux fois.
        modifier = Modifier.semantics { testTag = AuditTrailTags.SHA256 },
    )
}

/**
 * Badge d'état. Le jalon « statut » emprunte la palette réglementaire du statut réel
 * ([tagColor] / [containerColor]) — celle déjà employée par la liste et le détail : deux chartes
 * pour un même statut seraient une source de confusion, pas de clarté.
 */
@Composable
private fun MilestoneBadge(milestone: AuditMilestone, accent: Color) {
    val status = milestone.reportedStatus
    val text = when {
        status != null -> tr(status.labelKey())
        milestone.state == AuditMilestoneState.PENDING -> tr(StringKey.AUDIT_STEP_PENDING)
        else -> tr(StringKey.AUDIT_STEP_DONE)
    }
    val container = status?.containerColor() ?: accent.copy(alpha = 0.12f)
    val content = status?.onContainerColor() ?: accent

    Surface(color = container, contentColor = content, shape = RoundedCornerShape(6.dp)) {
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
                    .background(status?.tagColor() ?: accent, CircleShape),
            )
            Text(text = text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
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
