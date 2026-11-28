package com.hikmahlab.chargemaster.core.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class ChartPoint(val x: Float, val y: Float)

/**
 * Lightweight Canvas line chart. Gaps larger than [gapThreshold] (in x units) break the line so
 * missing monitoring time is never drawn as if it had been measured.
 * [description] is announced by screen readers in place of the visual chart.
 */
@Composable
fun LineChart(
    points: List<ChartPoint>,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    yLabel: (Float) -> String = { "%.0f".format(it) },
    minY: Float? = null,
    maxY: Float? = null,
    threshold: Float? = null,
    thresholdColor: Color = Tokens.Danger,
    gapThreshold: Float? = null,
    showDots: Boolean = false,
    /** Labels for the first and last x positions (e.g. start/end time). */
    xLabels: Pair<String, String>? = null,
) {
    val measurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = remember(labelColor) { TextStyle(fontSize = 10.sp, color = labelColor) }
    Canvas(modifier.fillMaxWidth().height(height).semantics { contentDescription = description }) {
        if (points.isEmpty()) return@Canvas
        val sorted = points.sortedBy { it.x }
        val ys = sorted.map { it.y } + listOfNotNull(threshold)
        var lo = minY ?: ys.min()
        var hi = maxY ?: ys.max()
        if (hi - lo < 1e-3f) { lo -= 1f; hi += 1f }
        val pad = (hi - lo) * 0.08f
        if (minY == null) lo -= pad
        if (maxY == null) hi += pad
        val x0 = sorted.first().x; val x1 = sorted.last().x
        val xr = (x1 - x0).takeIf { it > 0f } ?: 1f
        val leftPad = 40.dp.toPx(); val topPad = 6.dp.toPx()
        val bottomPad = if (xLabels != null) 18.dp.toPx() else 4.dp.toPx()
        val w = size.width - leftPad; val h = size.height - bottomPad - topPad
        fun px(p: ChartPoint) = Offset(leftPad + (p.x - x0) / xr * w, topPad + h - (p.y - lo) / (hi - lo) * h)

        // Grid + labels
        for (i in 0..2) {
            val v = lo + (hi - lo) * i / 2f
            val y = topPad + h - (v - lo) / (hi - lo) * h
            drawLine(grid, Offset(leftPad, y), Offset(size.width, y), strokeWidth = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)))
            val layout = measurer.measure(yLabel(v), labelStyle)
            drawText(layout, topLeft = Offset(0f, (y - layout.size.height / 2f).coerceIn(0f, size.height - layout.size.height)))
        }
        xLabels?.let { (first, last) ->
            val a = measurer.measure(first, labelStyle)
            val b = measurer.measure(last, labelStyle)
            drawText(a, topLeft = Offset(leftPad, size.height - a.size.height))
            drawText(b, topLeft = Offset(size.width - b.size.width, size.height - b.size.height))
        }
        threshold?.let { t ->
            val y = topPad + h - (t - lo) / (hi - lo) * h
            drawLine(thresholdColor, Offset(leftPad, y), Offset(size.width, y), strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        }

        // Segments split on gaps
        val segments = mutableListOf<MutableList<ChartPoint>>(mutableListOf())
        sorted.forEachIndexed { i, p ->
            if (i > 0 && gapThreshold != null && p.x - sorted[i - 1].x > gapThreshold) segments += mutableListOf<ChartPoint>()
            segments.last() += p
        }
        segments.filter { it.isNotEmpty() }.forEach { seg ->
            if (seg.size == 1) { drawCircle(color, 3.dp.toPx(), px(seg[0])); return@forEach }
            val path = Path(); val fill = Path()
            seg.forEachIndexed { i, p ->
                val o = px(p)
                if (i == 0) { path.moveTo(o.x, o.y); fill.moveTo(o.x, topPad + h); fill.lineTo(o.x, o.y) }
                else { path.lineTo(o.x, o.y); fill.lineTo(o.x, o.y) }
            }
            fill.lineTo(px(seg.last()).x, topPad + h); fill.close()
            drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.12f), Color.Transparent)))
            drawPath(path, color, style = Stroke(1.75.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            if (showDots) seg.forEach { drawCircle(color, 1.75.dp.toPx(), px(it)) }
        }
    }
}

@Composable
fun BarChart(
    values: List<Float>,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp = 120.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    labels: List<String>? = null,
) {
    val measurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier.fillMaxWidth().height(height).semantics { contentDescription = description }) {
        if (values.isEmpty()) return@Canvas
        val max = values.max().takeIf { it > 0f } ?: 1f
        val labelH = if (labels != null) 14.dp.toPx() else 0f
        val slot = size.width / values.size
        val bw = (slot * 0.4f).coerceAtMost(10.dp.toPx())
        val h = size.height - labelH
        values.forEachIndexed { i, v ->
            val left = i * slot + (slot - bw) / 2
            drawRoundRect(track, Offset(left, h - 1.dp.toPx()), Size(bw, 1.dp.toPx()))
            val bh = h * (v / max)
            if (bh > 0) drawRoundRect(color, Offset(left, h - bh), Size(bw, bh), CornerRadius(bw / 2))
            labels?.getOrNull(i)?.let { label ->
                val l = measurer.measure(label, TextStyle(fontSize = 9.sp, color = labelColor))
                drawText(l, topLeft = Offset(left + bw / 2 - l.size.width / 2, h + 2f))
            }
        }
    }
}

/** Scatter plot (e.g. charging power vs battery level). */
@Composable
fun ScatterChart(
    points: List<ChartPoint>,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
    color: Color = MaterialTheme.colorScheme.secondary,
    xRange: ClosedFloatingPointRange<Float>? = null,
) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.fillMaxWidth().height(height).semantics { contentDescription = description }) {
        if (points.isEmpty()) return@Canvas
        val xlo = xRange?.start ?: points.minOf { it.x }
        val xhi = (xRange?.endInclusive ?: points.maxOf { it.x }).let { if (it - xlo < 1e-3f) xlo + 1f else it }
        val ylo = 0f
        val yhi = points.maxOf { it.y }.let { if (it <= 0f) 1f else it * 1.1f }
        drawAxes(grid)
        points.forEach { p ->
            val x = (p.x - xlo) / (xhi - xlo) * size.width
            val y = size.height - (p.y - ylo) / (yhi - ylo) * size.height
            drawCircle(color, 3.dp.toPx(), Offset(x, y), alpha = 0.55f)
        }
    }
}

private fun DrawScope.drawAxes(c: Color) {
    drawLine(c, Offset(0f, size.height), Offset(size.width, size.height), 1f)
    drawLine(c, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1f)
    drawLine(c, Offset(0f, 0f), Offset(size.width, 0f), 1f)
}
