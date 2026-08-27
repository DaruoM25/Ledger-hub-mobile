package com.ledgerhub.presentation.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ledgerhub.domain.dashboard.MonthlyRevenue

/** Hauteur fixe du graphique — assez grande pour rester lisible sur mobile sans dominer l'écran. */
private val ChartHeight = 200.dp
private val BarCornerRadius = 6.dp
private val BarSpacingRatio = 0.35f // largeur d'un espace inter-barres, relative à la largeur d'une barre

/**
 * Graphique en barres verticales du CA mensuel — dessiné entièrement via l'API Canvas native de
 * Compose (aucune librairie de graphique tierce), avec une animation de "pousse" au premier
 * affichage et des données. Les couleurs viennent toutes de [MaterialTheme.colorScheme], donc le
 * graphique s'adapte automatiquement aux thèmes clair/sombre — jamais de couleur codée en dur ici.
 */
@Composable
fun RevenueBarChart(
    data: List<MonthlyRevenue>,
    modifier: Modifier = Modifier,
) {
    val barColor = MaterialTheme.colorScheme.primary
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = labelColor, fontSize = 11.sp)

    // Une seule valeur d'animation pilote la "pousse" de toutes les barres depuis 0 — plus simple
    // et tout aussi convaincant visuellement qu'un Animatable par barre, et se rejoue proprement
    // à chaque nouveau jeu de données (rechargement du tableau de bord).
    val revealProgress = remember { Animatable(0f) }
    LaunchedEffect(data) {
        revealProgress.snapTo(0f)
        if (data.isNotEmpty()) {
            revealProgress.animateTo(targetValue = 1f, animationSpec = tween(durationMillis = 600))
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(ChartHeight)
            .semantics {
                testTag = DashboardTags.REVENUE_CHART
                contentDescription = "Graphique du chiffre d'affaires des ${data.size} derniers mois"
            },
    ) {
        if (data.isEmpty()) return@Canvas

        val labelReservedHeight = 20.dp.toPx()
        val plotHeight = size.height - labelReservedHeight
        val maxCents = data.maxOf { it.amount.cents.coerceAtLeast(0L) }.coerceAtLeast(1L)

        val barCount = data.size
        val slotWidth = size.width / barCount
        val barWidth = slotWidth / (1f + BarSpacingRatio)
        val barInset = (slotWidth - barWidth) / 2f

        // Ligne de base — repère visuel du "0" du graphique.
        drawLine(
            color = axisColor,
            start = Offset(0f, plotHeight),
            end = Offset(size.width, plotHeight),
            strokeWidth = Stroke.HairlineWidth,
        )

        data.forEachIndexed { index, monthly ->
            val fraction = (monthly.amount.cents.coerceAtLeast(0L).toFloat() / maxCents.toFloat())
            val barHeight = plotHeight * fraction * revealProgress.value
            val left = index * slotWidth + barInset

            drawRoundRect(
                color = barColor,
                topLeft = Offset(left, plotHeight - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(BarCornerRadius.toPx(), BarCornerRadius.toPx()),
            )

            val monthLabel = monthly.month.takeLast(2) // "AAAA-MM" -> "MM", suffisant sur un axe de 6 points
            val layout = textMeasurer.measure(monthLabel, style = labelStyle)
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(
                    x = index * slotWidth + (slotWidth - layout.size.width) / 2f,
                    y = plotHeight + (labelReservedHeight - layout.size.height) / 2f,
                ),
            )
        }
    }
}
