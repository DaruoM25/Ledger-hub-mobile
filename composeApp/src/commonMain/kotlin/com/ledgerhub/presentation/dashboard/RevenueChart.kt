package com.ledgerhub.presentation.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ledgerhub.domain.dashboard.MonthlyRevenue
import com.ledgerhub.domain.i18n.StringKey
import com.ledgerhub.presentation.i18n.LocalAppLanguage
import com.ledgerhub.presentation.i18n.tr
import com.ledgerhub.presentation.invoices.formatMoney

/** Hauteur fixe du tracé — lisible sur mobile sans dominer l'écran. */
private val ChartHeight = 180.dp
private val PointRadius = 3.5.dp
private const val GRID_LINES = 3

/**
 * Graphe **ligne + aire** du chiffre d'affaires mensuel — dessiné entièrement via l'API Canvas
 * native de Compose (aucune librairie tierce), à l'identique de la courbe du Dashboard Web :
 * polyligne entre les points, aire dégradée sous la courbe, points ronds, lignes de repère
 * discrètes, libellés de mois, annotations Min / Max.
 *
 * Toutes les couleurs viennent de [MaterialTheme.colorScheme] : le graphe suit automatiquement le
 * thème clair/sombre — jamais de couleur codée en dur ici.
 */
@Composable
fun RevenueChart(
    data: List<MonthlyRevenue>,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val areaBrush = Brush.verticalGradient(
        listOf(lineColor.copy(alpha = 0.28f), Color.Transparent),
    )
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = labelColor, fontSize = 11.sp)

    // Animation de tracé progressif (MOB-DASH-03) :
    // Une valeur [0f -> 1f] en 650 ms anime le dévoilement horizontal de gauche à droite
    // de la courbe et de son aire dégradée. Se réinitialise proprement à chaque affichage/rechargement.
    val pathProgress = remember { Animatable(0f) }
    LaunchedEffect(data) {
        pathProgress.snapTo(0f)
        if (data.isNotEmpty()) {
            pathProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 650,
                    easing = LinearOutSlowInEasing,
                ),
            )
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { testTag = DashboardTags.REVENUE_CHART },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
        ) {
            Canvas(
                modifier = Modifier
                    .width(480.dp)
                    .height(ChartHeight)
                    .semantics {
                        contentDescription = "Graphique du chiffre d'affaires des ${data.size} derniers mois"
                    },
            ) {
                if (data.isEmpty()) return@Canvas

                val padX = 16.dp.toPx()
                val availableWidth = (size.width - 2 * padX).coerceAtLeast(1f)
                val labelReservedHeight = 20.dp.toPx()
                val plotHeight = size.height - labelReservedHeight
                val maxCents = data.maxOf { it.amount.cents }.coerceAtLeast(1L)
                val minCents = data.minOf { it.amount.cents }.coerceAtLeast(0L)
                val span = (maxCents - minCents).coerceAtLeast(1L)

                // Lignes de repère horizontales — repères de lecture discrets, comme sur le Web.
                repeat(GRID_LINES + 1) { i ->
                    val y = plotHeight * i / GRID_LINES
                    drawLine(
                        color = gridColor.copy(alpha = 0.4f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = Stroke.HairlineWidth,
                    )
                }

                val stepX = if (data.size <= 1) 0f else availableWidth / (data.size - 1)
                fun pointFor(index: Int, cents: Long): Offset {
                    val x = if (data.size <= 1) size.width / 2f else padX + index * stepX
                    val norm = (cents - minCents).toFloat() / span.toFloat()
                    val y = plotHeight - plotHeight * norm
                    return Offset(x, y)
                }

                val points = data.mapIndexed { index, monthly -> pointFor(index, monthly.amount.cents) }

                // Révélation progressive horizontale (MOB-DASH-03) :
                // Dévoilement de gauche à droite sur les 480 dp du canvas au rythme de pathProgress.
                val pointRadiusPx = PointRadius.toPx()
                val startX = padX - pointRadiusPx
                val endX = padX + availableWidth + pointRadiusPx
                val currentRevealX = startX + (endX - startX) * pathProgress.value

                clipRect(left = 0f, top = 0f, right = currentRevealX, bottom = size.height) {
                    // Aire sous la courbe.
                    val areaPath = Path().apply {
                        moveTo(points.first().x, plotHeight)
                        points.forEach { lineTo(it.x, it.y) }
                        lineTo(points.last().x, plotHeight)
                        close()
                    }
                    drawPath(path = areaPath, brush = areaBrush)

                    // Polyligne.
                    val linePath = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        points.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(path = linePath, color = lineColor, style = Stroke(width = 2.5.dp.toPx()))

                    // Points ronds.
                    points.forEach { p ->
                        drawCircle(color = lineColor, radius = pointRadiusPx, center = p)
                    }
                }

                // Libellés de mois sous l'axe.
                data.forEachIndexed { index, monthly ->
                    val monthLabel = monthly.month.takeLast(2) // "AAAA-MM" -> "MM"
                    val layout = textMeasurer.measure(monthLabel, style = labelStyle)
                    val cx = if (data.size <= 1) size.width / 2f else padX + index * stepX
                    drawText(
                        textLayoutResult = layout,
                        topLeft = Offset(
                            x = (cx - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width),
                            y = plotHeight + (labelReservedHeight - layout.size.height) / 2f,
                        ),
                    )
                }
            }
        }

        if (data.isNotEmpty()) {
            val lang = LocalAppLanguage.current
            val min = data.minOf { it.amount.cents }
            val max = data.maxOf { it.amount.cents }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "${tr(StringKey.CHART_MIN)} ${formatMoney(min, lang)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${tr(StringKey.CHART_MAX)} ${formatMoney(max, lang)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
