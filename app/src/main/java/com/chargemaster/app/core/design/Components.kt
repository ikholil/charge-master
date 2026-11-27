package com.chargemaster.app.core.design

import androidx.annotation.StringRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chargemaster.app.R
import com.chargemaster.app.domain.model.Confidence

/** Category of a displayed value — the app always makes the provenance visible. */
enum class ValueKind { LIVE, ESTIMATE, HISTORICAL, USER_PROVIDED, UNSUPPORTED }

@Composable
fun valueKindLabel(kind: ValueKind): String = stringResource(
    when (kind) {
        ValueKind.LIVE -> R.string.kind_live
        ValueKind.ESTIMATE -> R.string.kind_estimate
        ValueKind.HISTORICAL -> R.string.kind_historical
        ValueKind.USER_PROVIDED -> R.string.kind_user
        ValueKind.UNSUPPORTED -> R.string.kind_unsupported
    },
)

@Composable
fun BatteryRing(
    level: Int?,
    charging: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
    stroke: Dp = 18.dp,
    centerContent: @Composable ColumnScope.() -> Unit,
) {
    val reduce = LocalReduceMotion.current
    val target = (level ?: 0) / 100f
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = if (reduce) tween(0) else tween(900),
        label = "ring",
    )
    val pulse = if (charging && !reduce) {
        val t = rememberInfiniteTransition(label = "pulse")
        t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse), label = "p").value
    } else 1f
    val track = MaterialTheme.colorScheme.surfaceVariant
    val c1 = MaterialTheme.colorScheme.primary
    val c2 = if (level != null && level <= 15) Tokens.Danger else Tokens.Accent
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val sw = stroke.toPx()
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - sw, this.size.height - sw)
            val tl = androidx.compose.ui.geometry.Offset(sw / 2, sw / 2)
            drawArc(track, -90f, 360f, false, tl, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
            if (level != null) {
                drawArc(
                    brush = Brush.sweepGradient(listOf(c1, c2, c1)),
                    startAngle = -90f, sweepAngle = 360f * progress, useCenter = false,
                    topLeft = tl, size = arcSize, alpha = if (charging) 0.65f + 0.35f * pulse else 1f,
                    style = Stroke(sw, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, content = centerContent)
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp).heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text, style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        action?.invoke()
    }
}

/** Small uppercase-style label that introduces a group of cards. */
@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(start = 4.dp, top = 12.dp).heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        action?.invoke()
    }
}

/** Card title row with a tinted icon badge. */
@Composable
fun CardHeader(title: String, icon: ImageVector? = null, tint: Color = MaterialTheme.colorScheme.primary, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp).heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            IconBadge(icon, tint = tint, size = 32.dp)
            Spacer(Modifier.width(12.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
        action?.invoke()
    }
}

/** Flat card: tonal surface with a hairline border instead of a shadow. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentPadding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    val colors = CardDefaults.cardColors(containerColor = containerColor)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    val elevation = CardDefaults.cardElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp, 0.dp)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, colors = colors, elevation = elevation, border = border) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    } else {
        Card(modifier = modifier.fillMaxWidth(), shape = shape, colors = colors, elevation = elevation, border = border) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    }
}

/** Icon inside a softly tinted rounded square. */
@Composable
fun IconBadge(icon: ImageVector, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary, size: Dp = 36.dp) {
    Box(
        modifier.size(size).background(tint.copy(alpha = 0.12f), RoundedCornerShape(size * 0.32f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

/** Rounded pill used for statuses (charging speed, temperature zone…). */
@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Surface(modifier = modifier, color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(50)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(5.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Thick rounded progress track. */
@Composable
fun ProgressTrack(progress: Float, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary, height: Dp = 10.dp) {
    val reduce = LocalReduceMotion.current
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), if (reduce) tween(0) else tween(700), label = "track")
    Box(modifier.fillMaxWidth().height(height).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))) {
        if (p > 0f) Box(Modifier.fillMaxWidth(p).height(height).background(color, RoundedCornerShape(50)))
    }
}

/**
 * Horizontal gauge split into coloured zones with a marker at [value].
 * [zones] are (upper bound, colour) pairs in ascending order within [range].
 */
@Composable
fun ZoneGauge(value: Float?, range: ClosedFloatingPointRange<Float>, zones: List<Pair<Float, Color>>, modifier: Modifier = Modifier) {
    val markerColor = MaterialTheme.colorScheme.onSurface
    val ring = MaterialTheme.colorScheme.surfaceContainerLow
    Canvas(modifier.fillMaxWidth().height(22.dp)) {
        val barH = 8.dp.toPx()
        val top = (size.height - barH) / 2
        val span = range.endInclusive - range.start
        var from = range.start
        val gap = 3.dp.toPx()
        zones.forEach { (to, c) ->
            val x0 = (from - range.start) / span * size.width
            val x1 = ((to.coerceAtMost(range.endInclusive)) - range.start) / span * size.width
            if (x1 > x0) drawRoundRect(c, androidx.compose.ui.geometry.Offset(x0, top),
                androidx.compose.ui.geometry.Size((x1 - x0 - gap).coerceAtLeast(1f), barH), androidx.compose.ui.geometry.CornerRadius(barH / 2))
            from = to
        }
        if (value != null) {
            val x = ((value.coerceIn(range.start, range.endInclusive) - range.start) / span * size.width).coerceIn(size.height / 2, size.width - size.height / 2)
            val c = androidx.compose.ui.geometry.Offset(x, size.height / 2)
            drawCircle(ring, size.height / 2, c)
            drawCircle(markerColor, size.height / 2 - 3.dp.toPx(), c)
        }
    }
}

/**
 * A metric tile. Pass value = null for unavailable data; it renders the localized
 * "Not supported"/"Unavailable" label instead of a number.
 */
@Composable
fun MetricCard(
    label: String,
    value: String?,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    kind: ValueKind = ValueKind.LIVE,
    unavailableText: String = stringResource(R.string.unavailable),
    footnote: String? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    val shown = value ?: unavailableText
    val kindText = valueKindLabel(if (value == null) ValueKind.UNSUPPORTED else kind)
    Card(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = "$label: $shown. $kindText" },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon, tint = if (value == null) MaterialTheme.colorScheme.outline else tint, size = 32.dp)
                Spacer(Modifier.weight(1f))
                KindDot(if (value == null) ValueKind.UNSUPPORTED else kind)
            }
            Spacer(Modifier.height(12.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(
                shown,
                style = if (value == null) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleLarge,
                fontWeight = if (value == null) FontWeight.Normal else FontWeight.SemiBold,
                color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
            )
            footnote?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Compact provenance indicator: coloured dot + short label. */
@Composable
fun KindDot(kind: ValueKind) {
    val color = kindColor(kind)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Spacer(Modifier.width(4.dp))
        Text(valueKindLabel(kind), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun kindColor(kind: ValueKind): Color = when (kind) {
    ValueKind.LIVE -> MaterialTheme.colorScheme.primary
    ValueKind.ESTIMATE -> Tokens.Warning
    ValueKind.HISTORICAL -> Tokens.Info
    ValueKind.USER_PROVIDED -> MaterialTheme.colorScheme.secondary
    ValueKind.UNSUPPORTED -> MaterialTheme.colorScheme.outline
}

@Composable
fun KindTag(kind: ValueKind) {
    val color = kindColor(kind)
    Surface(color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(50)) {
        Text(
            valueKindLabel(kind), style = MaterialTheme.typography.labelSmall, color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun ConfidenceChip(confidence: Confidence) {
    val (res, color) = when (confidence) {
        Confidence.HIGH -> R.string.confidence_high to MaterialTheme.colorScheme.primary
        Confidence.MEDIUM -> R.string.confidence_medium to Tokens.Info
        Confidence.LOW -> R.string.confidence_low to Tokens.Warning
        Confidence.INSUFFICIENT -> R.string.confidence_insufficient to MaterialTheme.colorScheme.outline
    }
    Surface(color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(50)) {
        Text(stringResource(res), style = MaterialTheme.typography.labelMedium, color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

@Composable
fun InfoBanner(text: String, modifier: Modifier = Modifier, icon: ImageVector = Icons.Outlined.Info, tint: Color = Tokens.Info) {
    Surface(modifier = modifier.fillMaxWidth(), color = tint.copy(alpha = 0.09f), shape = MaterialTheme.shapes.medium) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun KeyValueRow(label: String, value: String?, modifier: Modifier = Modifier, kind: ValueKind? = null) {
    val shown = value ?: stringResource(R.string.unavailable)
    Row(
        modifier.fillMaxWidth().padding(vertical = 7.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(shown, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, textAlign = TextAlign.End,
                color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
            if (kind != null && value != null && kind != ValueKind.LIVE) KindTag(kind)
        }
    }
}

@Composable
fun NavRow(title: String, subtitle: String?, icon: ImageVector, onClick: () -> Unit, tint: Color = MaterialTheme.colorScheme.primary) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(role = Role.Button, onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, tint = tint)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun EmptyState(@StringRes title: Int, @StringRes body: Int, modifier: Modifier = Modifier, icon: ImageVector = Icons.Outlined.Inbox) {
    Column(modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        IconBadge(icon, tint = MaterialTheme.colorScheme.outline, size = 56.dp)
        Spacer(Modifier.height(14.dp))
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun MetricGrid(items: List<@Composable (Modifier) -> Unit>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.chunked(2).forEach { row ->
            // Equal-height cards per row.
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { it(Modifier.weight(1f).fillMaxHeight()) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Label above a prominent value; used for compact stat rows. */
@Composable
fun StatBlock(label: String, value: String, modifier: Modifier = Modifier, center: Boolean = false) {
    Column(modifier, horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = if (center) TextAlign.Center else TextAlign.Start)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
            textAlign = if (center) TextAlign.Center else TextAlign.Start)
    }
}
