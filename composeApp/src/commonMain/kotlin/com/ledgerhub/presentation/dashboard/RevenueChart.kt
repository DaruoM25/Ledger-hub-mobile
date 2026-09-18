package com.ledgerhub.presentation.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.ledgerhub.domain.i18n.AppTranslations
import com.ledgerhub.domain.i18n.StringKey
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.time.SystemClock
import com.ledgerhub.presentation.i18n.LocalAppLanguage
import com.ledgerhub.presentation.i18n.tr
import com.ledgerhub.presentation.invoices.formatMoney
import com.ledgerhub.presentation.theme.LedgerHubTheme

/** Hauteur fixe du tracé — lisible sur mobile sans dominer l'écran. */
private val ChartHeight = 180.dp
private val PointRadius = 3.5.dp
private const val GRID_LINES = 3
private const val DEFAULT_MONTHS_COUNT = 6

/**
 * Génère les 6 derniers mois glissants au format "AAAA-MM" si la liste de données est vide.
 */
private fun defaultMonthlySeries(): List<MonthlyRevenue> {
    val nowIso = SystemClock.nowIso()
    val year = nowIso.take(4).toIntOrNull() ?: 2026
    val currentMonth = nowIso.drop(5).take(2).toIntOrNull() ?: 9

    return (0 until DEFAULT_MONTHS_COUNT).map { offset ->
        var m = currentMonth - (DEFAULT_MONTHS_COUNT - 1 - offset)
        var y = year
        while (m <= 0) {
            m += 12
            y -= 1
        }
        val monthStr = m.toString().padStart(2, '0')
        MonthlyRevenue(month = "$y-$monthStr", amount = Money.ZERO)
    }
}

private fun monthAbbrKey(monthIndex: Int): StringKey = when (monthIndex) {
    1 -> StringKey.MONTH_ABBR_1
    2 -> StringKey.MONTH_ABBR_2
    3 -> StringKey.MONTH_ABBR_3
    4 -> StringKey.MONTH_ABBR_4
    5 -> StringKey.MONTH_ABBR_5
    6 -> StringKey.MONTH_ABBR_6
    7 -> StringKey.MONTH_ABBR_7
    8 -> StringKey.MONTH_ABBR_8
    9 -> StringKey.MONTH_ABBR_9
    10 -> StringKey.MONTH_ABBR_10
    11 -> StringKey.MONTH_ABBR_11
    else -> StringKey.MONTH_ABBR_12
}

/**
 * Graphe **ligne + aire** du chiffre d'affaires mensuel — dessiné entièrement via l'API Canvas
 * native de Compose (aucune librairie tierce), à l'identique de la courbe du Dashboard Web :
 * polyligne entre les points, aire dégradée sous la courbe, points ronds, lignes de repère
 * discrètes, libellés de mois, annotations Min / Max.
 *
 * S'étire sur toute la largeur disponible ([Modifier.fillMaxWidth]) pour afficher l'ensemble
 * des 6 mois sans défilement horizontal.
 */
@Composable
fun RevenueChart(
    data: List<MonthlyRevenue>,
    modifier: Modifier = Modifier,
) {
    val effectiveData = if (data.isEmpty()) defaultMonthlySeries() else data
    val isAllZero = effectiveData.all { it.amount.cents == 0L }
    val currentLanguage = LocalAppLanguage.current

    val accentColor = LedgerHubTheme.palette.Accent
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
    // de la courbe et de son aire dégradée. Se réinitialise au montage et à chaque mise à jour.
    val pathProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit, data) {
        pathProgress.snapTo(0f)
        pathProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 650,
                easing = LinearOutSlowInEasing,
            ),
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { testTag = DashboardTags.REVENUE_CHART },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(ChartHeight)
                .semantics {
                    contentDescription = "Graphique du chiffre d'affaires des ${effectiveData.size} derniers mois"
                },
        ) {
            val padX = 16.dp.toPx()
            val availableWidth = (size.width - 2 * padX).coerceAtLeast(1f)
            val labelReservedHeight = 20.dp.toPx()
            val plotHeight = size.height - labelReservedHeight
            val maxCents = effectiveData.maxOf { it.amount.cents }
            val minCents = effectiveData.minOf { it.amount.cents }
            val span = (maxCents - minCents).coerceAtLeast(1L)

            // Lignes de repère horizontales — repères de lecture discrets.
            repeat(GRID_LINES + 1) { i ->
                val y = plotHeight * i / GRID_LINES
                drawLine(
                    color = gridColor.copy(alpha = 0.4f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = Stroke.HairlineWidth,
                )
            }

            val stepX = if (effectiveData.size <= 1) 0f else availableWidth / (effectiveData.size - 1)
            fun pointFor(index: Int, cents: Long): Offset {
                val x = if (effectiveData.size <= 1) size.width / 2f else padX + index * stepX
                val norm = if (isAllZero || maxCents == minCents) 0f else ((cents - minCents).toFloat() / span.toFloat()).coerceIn(0f, 1f)
                val y = plotHeight - plotHeight * norm
                return Offset(x, y)
            }

            val points = effectiveData.mapIndexed { index, monthly -> pointFor(index, monthly.amount.cents) }

            // Révélation progressive horizontale (MOB-DASH-03) :
            val pointRadiusPx = PointRadius.toPx()
            val startX = padX - pointRadiusPx
            val endX = padX + availableWidth + pointRadiusPx
            val currentRevealX = startX + (endX - startX) * pathProgress.value

            clipRect(left = 0f, top = 0f, right = currentRevealX, bottom = size.height) {
                if (isAllZero) {
                    // Empty State : Tracé bleu d'accentuation continu à y = 0
                    val baselinePath = Path().apply {
                        moveTo(points.first().x, plotHeight)
                        points.drop(1).forEach { lineTo(it.x, plotHeight) }
                    }
                    drawPath(
                        path = baselinePath,
                        color = accentColor,
                        style = Stroke(width = 2.dp.toPx()),
                    )

                    // Points ronds bleus d'accentuation à 0 sur la ligne de base
                    points.forEach { p ->
                        drawCircle(
                            color = accentColor,
                            radius = pointRadiusPx,
                            center = p,
                        )
                    }
                } else {
                    // Aire sous la courbe
                    val areaPath = Path().apply {
                        moveTo(points.first().x, plotHeight)
                        points.forEach { lineTo(it.x, it.y) }
                        lineTo(points.last().x, plotHeight)
                        close()
                    }
                    drawPath(path = areaPath, brush = areaBrush)

                    // Polyligne
                    val linePath = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        points.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(path = linePath, color = lineColor, style = Stroke(width = 2.5.dp.toPx()))

                    // Points ronds
                    points.forEach { p ->
                        drawCircle(color = lineColor, radius = pointRadiusPx, center = p)
                    }
                }
            }

            // Libellés de mois sous l'axe (traduits selon la langue active, espacés sur toute la largeur)
            effectiveData.forEachIndexed { index, monthly ->
                val monthInt = monthly.month.takeLast(2).toIntOrNull() ?: 1
                val monthLabel = AppTranslations.get(monthAbbrKey(monthInt), currentLanguage)
                val layout = textMeasurer.measure(monthLabel, style = labelStyle)
                val cx = if (effectiveData.size <= 1) size.width / 2f else padX + index * stepX
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        x = (cx - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width),
                        y = plotHeight + (labelReservedHeight - layout.size.height) / 2f,
                    ),
                )
            }
        }

        val min = if (isAllZero) 0L else effectiveData.minOf { it.amount.cents }
        val max = if (isAllZero) 0L else effectiveData.maxOf { it.amount.cents }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "${tr(StringKey.CHART_MIN)} ${formatMoney(min, currentLanguage)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${tr(StringKey.CHART_MAX)} ${formatMoney(max, currentLanguage)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
