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
import androidx.compose.foundation.clickable
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
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text, style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        action?.invoke()
    }
}

@Composable
fun AppCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    val elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, colors = colors, elevation = elevation) {
            Column(Modifier.padding(16.dp), content = content)
        }
    } else {
        Card(modifier = modifier.fillMaxWidth(), shape = shape, colors = colors, elevation = elevation) {
            Column(Modifier.padding(16.dp), content = content)
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
) {
    val shown = value ?: unavailableText
    val kindText = valueKindLabel(if (value == null) ValueKind.UNSUPPORTED else kind)
    Card(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = "$label: $shown. $kindText" },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                shown,
                style = if (value == null) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleLarge,
                color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
            )
            Spacer(Modifier.height(4.dp))
            KindTag(if (value == null) ValueKind.UNSUPPORTED else kind)
            footnote?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun KindTag(kind: ValueKind) {
    val color = when (kind) {
        ValueKind.LIVE -> MaterialTheme.colorScheme.primary
        ValueKind.ESTIMATE -> Tokens.Warning
        ValueKind.HISTORICAL -> Tokens.Info
        ValueKind.USER_PROVIDED -> MaterialTheme.colorScheme.secondary
        ValueKind.UNSUPPORTED -> MaterialTheme.colorScheme.outline
    }
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
    Surface(modifier = modifier.fillMaxWidth(), color = tint.copy(alpha = 0.10f), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun KeyValueRow(label: String, value: String?, modifier: Modifier = Modifier, kind: ValueKind? = null) {
    val shown = value ?: stringResource(R.string.unavailable)
    Row(
        modifier.fillMaxWidth().padding(vertical = 6.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(shown, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, textAlign = TextAlign.End,
                color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
            if (kind != null && value != null && kind != ValueKind.LIVE) KindTag(kind)
        }
    }
}

@Composable
fun NavRow(title: String, subtitle: String?, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun EmptyState(@StringRes title: Int, @StringRes body: Int, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
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
