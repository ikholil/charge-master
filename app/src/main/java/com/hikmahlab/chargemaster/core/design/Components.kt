package com.hikmahlab.chargemaster.core.design

import androidx.annotation.StringRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikmahlab.chargemaster.R
import com.hikmahlab.chargemaster.domain.model.Confidence
import kotlin.math.PI
import kotlin.math.sin

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

/** Pastel assigned to the tile currently being drawn (set by [MetricGrid]). */
val LocalTilePastel = compositionLocalOf<Pastel?> { null }

/* ───────────────────────── Typography helpers ───────────────────────── */

/** Small muted field label. */
@Composable
fun Overline(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines: Int = 1) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

private val NUMBER_UNIT = Regex("""^([+\-−]?[\d০-৯][\d০-৯.,]*)\s?([^\d০-৯]*)$""")

/**
 * Renders "3.98 V" as a bold number with a smaller, softer unit. Values that aren't a single
 * number plus unit (durations, words) are drawn whole.
 */
@Composable
fun NumberWithUnit(
    value: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.headlineSmall,
    unitScale: Float = 0.55f,
    color: Color = MaterialTheme.colorScheme.onSurface,
    textAlign: TextAlign? = null,
    unitColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val m = NUMBER_UNIT.matchEntire(value.trim())
    val text = if (m != null && m.groupValues[2].isNotBlank()) buildAnnotatedString {
        append(m.groupValues[1])
        withStyle(SpanStyle(fontSize = style.fontSize * unitScale, color = unitColor, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp)) {
            val unit = m.groupValues[2]
            append(if (unit.startsWith("%") || unit.startsWith("°")) unit else " $unit")
        }
    } else buildAnnotatedString { append(value) }
    Text(text, modifier = modifier, style = style, color = color, maxLines = 2, textAlign = textAlign)
}

/* ───────────────────────── Structure ───────────────────────── */

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(bottom = 12.dp).heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
        action?.invoke()
    }
}

/** Label that introduces a group of cards. */
@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(start = 6.dp, top = 12.dp).heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
        action?.invoke()
    }
}

/** Card title with a friendly icon bubble. */
@Composable
fun CardHeader(title: String, icon: ImageVector? = null, tint: Color = MaterialTheme.colorScheme.primary, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(bottom = 14.dp).heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            IconBadge(icon, tint = tint, size = 34.dp)
            Spacer(Modifier.width(12.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
        action?.invoke()
    }
}

/** Section title placed between cards. */
@Composable
fun SectionTitle(text: String, action: (@Composable () -> Unit)? = null) {
    GroupLabel(text, action = action)
}

/** Soft rounded card. No border, no shadow — just a colour step from the page. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = Color.Unspecified,
    contentPadding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val color = if (containerColor == Color.Unspecified) MaterialTheme.colorScheme.surface else containerColor
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = color, shape = MaterialTheme.shapes.large,
        onClick = onClick ?: {}, enabled = onClick != null,
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = contentPadding), content = content)
    }
}

/** Icon sitting in a soft circular bubble. */
@Composable
fun IconBadge(icon: ImageVector, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary, size: Dp = 40.dp, container: Color = tint.copy(alpha = 0.14f)) {
    Box(modifier.size(size).background(container, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/** Rounded tinted capsule for statuses. */
@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Surface(modifier = modifier, color = color.copy(alpha = 0.15f), shape = CircleShape) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = color, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
            } else {
                Box(Modifier.size(7.dp).background(color, CircleShape))
                Spacer(Modifier.width(7.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Chunky rounded progress bar. */
@Composable
fun ProgressTrack(progress: Float, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary, height: Dp = 10.dp) {
    val reduce = LocalReduceMotion.current
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), if (reduce) tween(0) else tween(700), label = "track")
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier.fillMaxWidth().height(height)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        if (p > 0f) drawRoundRect(color, size = Size((size.width * p).coerceAtLeast(size.height), size.height), cornerRadius = r)
    }
}

/**
 * The signature gauge: a big rounded battery filled with liquid up to [level]. While charging the
 * liquid's edge ripples gently. Small notches mark the 20–80% range batteries age slowest in.
 */
@Composable
fun CellGauge(level: Int?, charging: Boolean, modifier: Modifier = Modifier, height: Dp = 112.dp) {
    val reduce = LocalReduceMotion.current
    val progress by animateFloatAsState((level ?: 0) / 100f, if (reduce) tween(0) else tween(1100), label = "liquid")
    val phase = if (!reduce) {
        rememberInfiniteTransition(label = "wave").animateFloat(0f, (2 * PI).toFloat(),
            infiniteRepeatable(tween(if (charging) 1600 else 4200, easing = LinearEasing), RepeatMode.Restart), label = "phase").value
    } else 0f
    val pastel = LocalPastels.current.mint
    val liquid = when {
        level == null -> MaterialTheme.colorScheme.outline
        level <= 15 -> Tokens.Danger
        else -> MaterialTheme.colorScheme.primary
    }
    val body = MaterialTheme.colorScheme.surface
    val band = liquid.copy(alpha = 0.35f)
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val capW = 10.dp.toPx(); val capH = size.height * 0.36f
            val bodyW = size.width - capW - 4.dp.toPx()
            val r = 26.dp.toPx()
            drawRoundRect(body, size = Size(bodyW, size.height), cornerRadius = CornerRadius(r))
            drawRoundRect(body, topLeft = Offset(bodyW + 4.dp.toPx(), (size.height - capH) / 2), size = Size(capW, capH), cornerRadius = CornerRadius(capW / 2))
            val pad = 7.dp.toPx()
            val innerW = bodyW - pad * 2; val innerH = size.height - pad * 2
            val clip = Path().apply { addRoundRect(RoundRect(pad, pad, pad + innerW, pad + innerH, CornerRadius(r - pad))) }
            val fx = pad + innerW * progress
            val amp = if (progress in 0.02f..0.98f) 5.dp.toPx() else 0f
            fun wave(offset: Float, ampScale: Float): Path = Path().apply {
                moveTo(pad, pad)
                val steps = 24
                for (i in 0..steps) {
                    val y = pad + innerH * i / steps
                    val x = fx + amp * ampScale * sin(phase + offset + i / steps.toFloat() * 2f * PI.toFloat())
                    lineTo(x, y)
                }
                lineTo(pad, pad + innerH); close()
            }
            clipPath(clip) {
                drawRect(pastel.container, Offset(pad, pad), Size(innerW, innerH))
                if (progress > 0f) {
                    drawPath(wave(PI.toFloat(), 1.3f), liquid.copy(alpha = 0.35f))
                    drawPath(wave(0f, 1f), liquid)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        // Band under the battery spanning the 20–80% range (aligned to the liquid area).
        Canvas(Modifier.fillMaxWidth().height(6.dp)) {
            val capW = 10.dp.toPx() + 4.dp.toPx(); val pad = 7.dp.toPx()
            val innerW = size.width - capW - pad * 2
            val x0 = pad + innerW * 0.2f; val x1 = pad + innerW * 0.8f
            drawRoundRect(band, Offset(x0, 0f), Size(x1 - x0, size.height), CornerRadius(size.height / 2))
        }
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.kindest_range), style = MaterialTheme.typography.labelMedium, color = pastel.content,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}

/** Chunky circular gauge. */
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
    val progress by animateFloatAsState((level ?: 0) / 100f, if (reduce) tween(0) else tween(900), label = "ring")
    val pulse = if (charging && !reduce) {
        rememberInfiniteTransition(label = "pulse").animateFloat(0.6f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse), label = "p").value
    } else 1f
    val track = MaterialTheme.colorScheme.surfaceVariant
    val color = if (level != null && level <= 15) Tokens.Danger else MaterialTheme.colorScheme.primary
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val sw = stroke.toPx()
            val arc = Size(this.size.width - sw, this.size.height - sw)
            val tl = Offset(sw / 2, sw / 2)
            drawArc(track, -90f, 360f, false, tl, arc, style = Stroke(sw, cap = StrokeCap.Round))
            if (level != null) drawArc(color, -90f, 360f * progress, false, tl, arc, alpha = pulse, style = Stroke(sw, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, content = centerContent)
    }
}

/**
 * Chunky horizontal gauge split into coloured zones with a round knob at [value].
 * [zones] are (upper bound, colour) pairs in ascending order within [range].
 */
@Composable
fun ZoneGauge(value: Float?, range: ClosedFloatingPointRange<Float>, zones: List<Pair<Float, Color>>, modifier: Modifier = Modifier) {
    val knob = MaterialTheme.colorScheme.surface
    val ink = MaterialTheme.colorScheme.onSurface
    Canvas(modifier.fillMaxWidth().height(28.dp)) {
        val barH = 12.dp.toPx()
        val top = (size.height - barH) / 2
        val span = range.endInclusive - range.start
        val gap = 3.dp.toPx()
        var from = range.start
        zones.forEach { (to, c) ->
            val x0 = (from - range.start) / span * size.width
            val x1 = (to.coerceAtMost(range.endInclusive) - range.start) / span * size.width
            if (x1 > x0) drawRoundRect(c, Offset(x0, top), Size((x1 - x0 - gap).coerceAtLeast(1f), barH), CornerRadius(barH / 2))
            from = to
        }
        if (value != null) {
            val rad = size.height / 2
            val x = ((value.coerceIn(range.start, range.endInclusive) - range.start) / span * size.width).coerceIn(rad, size.width - rad)
            val c = Offset(x, size.height / 2)
            drawCircle(knob, rad - 1.dp.toPx(), c)
            drawCircle(ink, rad - 2.5.dp.toPx(), c, style = Stroke(3.dp.toPx()))
        }
    }
}

/* ───────────────────────── Values ───────────────────────── */

/**
 * A pastel metric tile. Pass value = null for unavailable data; it renders the localized
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
    tint: Color = Color.Unspecified,
) {
    val shown = value ?: unavailableText
    val kindText = valueKindLabel(if (value == null) ValueKind.UNSUPPORTED else kind)
    val pastel = LocalTilePastel.current ?: LocalPastels.current.mint
    val container = if (value == null) MaterialTheme.colorScheme.surfaceContainerHigh else pastel.container
    val ink = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else pastel.content
    Column(
        modifier.clip(RoundedCornerShape(26.dp)).background(container).padding(16.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$label: $shown. $kindText" },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, tint = ink, size = 38.dp, container = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f))
            Spacer(Modifier.weight(1f))
            if (value != null && kind != ValueKind.LIVE) KindDot(kind)
        }
        Spacer(Modifier.height(14.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        if (value == null) {
            Text(shown, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            NumberWithUnit(value, style = MaterialTheme.typography.headlineSmall, unitColor = ink)
        }
        footnote?.let {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (tint != Color.Unspecified) {
                    Box(Modifier.size(7.dp).background(tint, CircleShape))
                    Spacer(Modifier.width(6.dp))
                }
                Text(it, style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.85f))
            }
        }
    }
}

/** Provenance chip. Live readings are the default and carry no chip. */
@Composable
fun KindDot(kind: ValueKind) {
    if (kind == ValueKind.LIVE) return
    val p = LocalPastels.current
    val pastel = when (kind) {
        ValueKind.ESTIMATE -> p.butter
        ValueKind.HISTORICAL -> p.sky
        ValueKind.USER_PROVIDED -> p.lilac
        else -> Pastel(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Surface(color = pastel.container, shape = CircleShape) {
        Text(valueKindLabel(kind), style = MaterialTheme.typography.labelSmall, color = pastel.content,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp), maxLines = 1)
    }
}

@Composable
fun KindTag(kind: ValueKind) = KindDot(kind)

@Composable
fun ConfidenceChip(confidence: Confidence) {
    val (res, color) = when (confidence) {
        Confidence.HIGH -> R.string.confidence_high to MaterialTheme.colorScheme.primary
        Confidence.MEDIUM -> R.string.confidence_medium to Tokens.Info
        Confidence.LOW -> R.string.confidence_low to Tokens.Warning
        Confidence.INSUFFICIENT -> R.string.confidence_insufficient to MaterialTheme.colorScheme.outline
    }
    StatusPill(stringResource(res), color)
}

/** Friendly note in a soft tinted bubble. */
@Composable
fun InfoBanner(text: String, modifier: Modifier = Modifier, icon: ImageVector = Icons.Outlined.Info, tint: Color = Tokens.Info) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(tint.copy(alpha = 0.10f)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, tint = tint, size = 32.dp, container = tint.copy(alpha = 0.16f))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f))
    }
}

@Composable
fun KeyValueRow(label: String, value: String?, modifier: Modifier = Modifier, kind: ValueKind? = null) {
    val shown = value ?: stringResource(R.string.unavailable)
    Row(
        modifier.fillMaxWidth().padding(vertical = 8.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(16.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(shown, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End,
                color = if (value == null) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface)
            if (kind != null && value != null && kind != ValueKind.LIVE) {
                Spacer(Modifier.height(3.dp))
                KindTag(kind)
            }
        }
    }
}

@Composable
fun NavRow(title: String, subtitle: String?, icon: ImageVector, onClick: () -> Unit, tint: Color = MaterialTheme.colorScheme.primary) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(role = Role.Button, onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, tint = tint, size = 42.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
    }
}

@Composable
fun EmptyState(@StringRes title: Int, @StringRes body: Int, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val p = LocalPastels.current.lilac
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        IconBadge(icon ?: Icons.Outlined.Inbox, tint = p.content, size = 84.dp, container = p.container)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Two-column grid of pastel tiles; colours rotate so neighbours never match. */
@Composable
fun MetricGrid(items: List<@Composable (Modifier) -> Unit>) {
    val cycle = LocalPastels.current.cycle
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.chunked(2).forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEachIndexed { c, item ->
                    CompositionLocalProvider(LocalTilePastel provides cycle[(r * 2 + c) % cycle.size]) {
                        item(Modifier.weight(1f).fillMaxHeight())
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Label above a bold value; used for compact stat rows. */
@Composable
fun StatBlock(label: String, value: String, modifier: Modifier = Modifier, center: Boolean = false) {
    Column(modifier, horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start) {
        Overline(label)
        Spacer(Modifier.height(4.dp))
        NumberWithUnit(value, style = MaterialTheme.typography.titleLarge, textAlign = if (center) TextAlign.Center else TextAlign.Start)
    }
}

/** Soft vertical divider for stat columns. */
@Composable
fun VRule(modifier: Modifier = Modifier) {
    Box(modifier.width(2.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant, CircleShape))
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

/** Spacing shared by screen lists. */
val ScreenArrangement: Arrangement.Vertical = Arrangement.spacedBy(12.dp)
