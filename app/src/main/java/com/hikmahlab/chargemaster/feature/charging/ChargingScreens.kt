package com.hikmahlab.chargemaster.feature.charging

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ElectricalServices
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Dock
import androidx.compose.material.icons.outlined.ElectricBolt
import androidx.compose.material.icons.outlined.ScatterPlot
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material.icons.outlined.Wifi
import com.hikmahlab.chargemaster.core.design.CardHeader
import com.hikmahlab.chargemaster.core.design.CellGauge
import com.hikmahlab.chargemaster.core.design.NumberWithUnit
import com.hikmahlab.chargemaster.core.design.Overline
import androidx.compose.foundation.layout.size
import com.hikmahlab.chargemaster.core.design.GroupLabel
import com.hikmahlab.chargemaster.core.design.IconBadge
import com.hikmahlab.chargemaster.core.design.ProgressTrack
import com.hikmahlab.chargemaster.core.design.StatusPill
import com.hikmahlab.chargemaster.core.design.color
import com.hikmahlab.chargemaster.domain.engine.ChargeSpeed
import com.hikmahlab.chargemaster.domain.model.ChargingSource
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import com.hikmahlab.chargemaster.core.design.timeText
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hikmahlab.chargemaster.R
import com.hikmahlab.chargemaster.core.common.Fmt
import com.hikmahlab.chargemaster.core.design.AppCard
import com.hikmahlab.chargemaster.core.design.LocalPastels
import com.hikmahlab.chargemaster.core.design.BarChart
import com.hikmahlab.chargemaster.core.design.ChartPoint
import com.hikmahlab.chargemaster.core.design.EmptyState
import com.hikmahlab.chargemaster.core.design.InfoBanner
import com.hikmahlab.chargemaster.core.design.KeyValueRow
import com.hikmahlab.chargemaster.core.design.KindTag
import com.hikmahlab.chargemaster.core.design.LineChart
import com.hikmahlab.chargemaster.core.design.MetricCard
import com.hikmahlab.chargemaster.core.design.MetricGrid
import com.hikmahlab.chargemaster.core.design.PeriodSelector
import com.hikmahlab.chargemaster.core.design.ScatterChart
import com.hikmahlab.chargemaster.core.design.ScreenScaffold
import com.hikmahlab.chargemaster.core.design.SectionHeader
import com.hikmahlab.chargemaster.core.design.SectionTitle
import com.hikmahlab.chargemaster.core.design.Tokens
import com.hikmahlab.chargemaster.core.design.ValueKind
import com.hikmahlab.chargemaster.core.design.dateTimeText
import com.hikmahlab.chargemaster.core.design.durationText
import com.hikmahlab.chargemaster.core.design.labelRes
import com.hikmahlab.chargemaster.core.design.ConfidenceChip
import com.hikmahlab.chargemaster.domain.model.ChargingSession
import com.hikmahlab.chargemaster.domain.model.ChargingStatus
import com.hikmahlab.chargemaster.domain.model.SessionEndReason
import com.hikmahlab.chargemaster.domain.model.TemperatureUnit
import com.hikmahlab.chargemaster.feature.dashboard.LastChargeCard
import com.hikmahlab.chargemaster.feature.dashboard.methodText
import kotlin.math.abs

@Composable
fun ChargingScreen(onOpenHistory: () -> Unit, onOpenSession: (Long) -> Unit, vm: ChargingViewModel = hiltViewModel()) {
    val st by vm.state.collectAsStateWithLifecycle()
    val s = st.snapshot
    ScreenScaffold(title = stringResource(R.string.charging_monitor)) {
        item {
            val charging = s?.isPluggedIn == true
            val speed = if (charging) ChargeSpeed.classify(s?.powerW) else null
            val mint = LocalPastels.current.mint
            AppCard(containerColor = mint.container, contentPadding = 22.dp) {
                StatusPill(stringResource((s?.status ?: ChargingStatus.UNKNOWN).labelRes()),
                    if (charging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                val sub = when {
                    charging -> stringResource(R.string.charging_via, stringResource(s!!.source.labelRes()))
                    s?.status == ChargingStatus.DISCHARGING -> stringResource(R.string.plug_in_hint)
                    else -> null
                }
                sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 14.dp, top = 2.dp)) }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    if (s?.level != null) NumberWithUnit(Fmt.percent(s.level), style = MaterialTheme.typography.displayLarge, unitScale = 0.42f, modifier = Modifier.weight(1f), unitColor = mint.content)
                    else Text(stringResource(R.string.unavailable), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.outline, modifier = Modifier.weight(1f))
                    speed?.let { StatusPill(stringResource(it.labelRes()), it.color(), modifier = Modifier.padding(bottom = 16.dp)) }
                }
                Spacer(Modifier.height(18.dp))
                CellGauge(s?.level, charging)
                if (speed != null) {
                    InfoBanner(stringResource(R.string.speed_tier_note), modifier = Modifier.padding(top = 10.dp), tint = speed.color())
                }
            }
        }
        if (s != null && !s.isPluggedIn) st.recent.firstOrNull { !it.isOpen }?.let { last ->
            item { LastChargeCard(last, s.timestamp, onClick = { onOpenSession(last.id) }, currentLevel = s.level) }
        }
        item { SectionTitle(stringResource(R.string.readings)) }
        item {
            MetricGrid(
                listOf(
                    { m -> MetricCard(stringResource(R.string.metric_power), s?.powerW?.let { Fmt.power(abs(it)) }, Icons.Outlined.Bolt, m,
                        kind = ValueKind.ESTIMATE, unavailableText = stringResource(R.string.not_supported)) },
                    { m -> MetricCard(stringResource(R.string.metric_current), s?.currentNowMa?.let(Fmt::current), Icons.Outlined.Speed, m,
                        unavailableText = stringResource(R.string.not_supported),
                        footnote = if (s?.currentCalibrationUncertain == true) stringResource(R.string.current_uncertain) else null) },
                    { m -> MetricCard(stringResource(R.string.metric_voltage), s?.voltageMv?.let(Fmt::voltage), Icons.Outlined.ElectricalServices, m) },
                    { m -> MetricCard(stringResource(R.string.metric_temperature), s?.temperatureC?.let { Fmt.temperature(it, st.unit) }, Icons.Outlined.Thermostat, m) },
                    { m -> MetricCard(stringResource(R.string.session_duration),
                        st.session?.let { durationText(it.durationMs(s?.timestamp ?: it.lastSampleTime)) }, Icons.Outlined.Timer, m,
                        kind = ValueKind.HISTORICAL, unavailableText = stringResource(R.string.not_charging)) },
                    { m -> MetricCard(stringResource(R.string.eta_full),
                        if (s?.status == ChargingStatus.FULL) stringResource(R.string.status_full) else st.etaFull?.let { durationText(it.minutes * 60_000) },
                        Icons.Outlined.Power, m, kind = ValueKind.ESTIMATE,
                        unavailableText = stringResource(if (s?.isPluggedIn == true) R.string.estimating else R.string.not_charging),
                        footnote = st.etaFull?.let { methodText(it.method) }) },
                ),
            )
        }
        if (s?.isPluggedIn == true && s.status != ChargingStatus.FULL) item {
            AppCard {
                KeyValueRow(stringResource(R.string.eta_80), st.eta80?.let { durationText(it.minutes * 60_000) } ?: stringResource(R.string.estimating), kind = ValueKind.ESTIMATE)
                st.session?.peakPowerW?.let { KeyValueRow(stringResource(R.string.peak_power), Fmt.power(it), kind = ValueKind.HISTORICAL) }
                st.session?.averagePowerW?.let { KeyValueRow(stringResource(R.string.average_power), Fmt.power(it), kind = ValueKind.HISTORICAL) }
            }
        }
        item {
            AppCard {
                CardHeader(stringResource(R.string.live_power_chart), Icons.AutoMirrored.Outlined.ShowChart)
                val pts = st.livePower.mapNotNull { p -> p.powerW?.let { ChartPoint((p.timestamp - (s?.timestamp ?: 0)) / 1000f, abs(it)) } }
                if (pts.size < 2) {
                    Text(stringResource(if (s?.powerW == null) R.string.power_not_supported else R.string.collecting_samples),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LineChart(
                        pts,
                        description = stringResource(R.string.chart_live_power_desc, Fmt.power(pts.minOf { it.y }), Fmt.power(pts.maxOf { it.y }), Fmt.power(pts.last().y)),
                        yLabel = { Fmt.decimal1(it) + " W" },
                        gapThreshold = 30f,
                        xLabels = timeText(st.livePower.first().timestamp) to stringResource(R.string.now),
                    )
                    Text(stringResource(R.string.live_chart_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { InfoBanner(stringResource(R.string.no_charge_control)) }
        item {
            GroupLabel(stringResource(R.string.recent_sessions)) {
                TextButton(onClick = onOpenHistory) { Text(stringResource(R.string.view_all)) }
            }
        }
        if (st.recent.isEmpty()) item { EmptyState(R.string.no_sessions_title, R.string.no_sessions_body) }
        items(st.recent, key = { it.id }) { SessionRow(it, st.unit) { onOpenSession(it.id) } }
    }
}


@Suppress("UNUSED_PARAMETER")
@Composable
fun SessionRow(s: ChargingSession, unit: TemperatureUnit, onClick: () -> Unit) {
    AppCard(onClick = onClick, contentPadding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(s.source.icon(), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("${s.startLevel?.let(Fmt::percent) ?: "—"} → ${s.endLevel?.let(Fmt::percent) ?: "—"}",
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"))
                Text(
                    "${dateTimeText(s.startTime)} · ${durationText(s.durationMs())}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                s.averagePowerW?.let { NumberWithUnit(Fmt.power(it), style = MaterialTheme.typography.titleMedium) }
                if (!s.isComplete || s.isOpen) Overline(
                    stringResource(if (s.isOpen) R.string.session_open else R.string.session_partial),
                    color = if (s.isOpen) MaterialTheme.colorScheme.primary else Tokens.Warning,
                )
            }
        }
    }
}

private fun ChargingSource.icon(): ImageVector = when (this) {
    ChargingSource.AC -> Icons.Outlined.Power
    ChargingSource.USB -> Icons.Outlined.Usb
    ChargingSource.WIRELESS -> Icons.Outlined.Wifi
    ChargingSource.DOCK -> Icons.Outlined.Dock
    else -> Icons.Outlined.ElectricBolt
}

@Composable
fun ChargingHistoryScreen(onBack: () -> Unit, onOpenSession: (Long) -> Unit, vm: ChargingHistoryViewModel = hiltViewModel()) {
    val st by vm.state.collectAsStateWithLifecycle()
    ScreenScaffold(title = stringResource(R.string.charging_history), onBack = onBack) {
        item { PeriodSelector(st.period, { vm.period.value = it }, options = listOf(com.hikmahlab.chargemaster.core.design.Period.D7, com.hikmahlab.chargemaster.core.design.Period.D30)) }
        val stats = st.stats
        if (stats == null || st.sessions.isEmpty()) {
            item { EmptyState(R.string.no_sessions_title, R.string.no_sessions_body) }
            return@ScreenScaffold
        }
        item {
            AppCard {
                CardHeader(stringResource(R.string.charging_calendar), Icons.Outlined.CalendarMonth)
                val locale = java.util.Locale.getDefault()
                val labels = stats.days.mapIndexed { i, d ->
                    if (stats.days.size <= 7) d.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, locale)
                    else if ((stats.days.size - 1 - i) % 5 == 0) String.format(locale, "%d", d.dayOfMonth) else ""
                }
                BarChart(
                    stats.sessionsPerDay.map { it.toFloat() },
                    description = stringResource(R.string.chart_sessions_per_day_desc, stats.sessionCount, stats.sessionsPerDay.size),
                    labels = labels,
                )
                Text(pluralStringResource(R.plurals.sessions_count, stats.sessionCount, stats.sessionCount), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            AppCard {
                CardHeader(stringResource(R.string.summary), Icons.Outlined.Summarize)
                KeyValueRow(stringResource(R.string.average_duration), stats.averageDurationMs?.let { durationText(it) }, kind = ValueKind.HISTORICAL)
                KeyValueRow(stringResource(R.string.peak_observed_power), stats.peakPowerW?.let(Fmt::power), kind = ValueKind.HISTORICAL)
                KeyValueRow(stringResource(R.string.fastest_session), stats.fastest?.percentPerHour?.let(Fmt::rate), kind = ValueKind.HISTORICAL)
                KeyValueRow(stringResource(R.string.slowest_session), stats.slowest?.percentPerHour?.let(Fmt::rate), kind = ValueKind.HISTORICAL)
            }
        }
        item {
            AppCard {
                CardHeader(stringResource(R.string.speed_by_source), Icons.Outlined.Speed)
                stats.bySource.forEach { src ->
                    KeyValueRow(
                        "${stringResource(src.source.labelRes())} (${src.sessions})",
                        listOfNotNull(src.averagePowerW?.let(Fmt::power), src.averagePercentPerHour?.let(Fmt::rate)).joinToString(" · ").ifEmpty { null },
                        kind = ValueKind.HISTORICAL,
                    )
                }
                Text(stringResource(R.string.speed_comparison_note), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            AppCard {
                CardHeader(stringResource(R.string.power_vs_level), Icons.Outlined.ScatterPlot)
                if (st.powerVsLevel.size < 5) Text(stringResource(R.string.insufficient_history), style = MaterialTheme.typography.bodyMedium)
                else ScatterChart(st.powerVsLevel, stringResource(R.string.chart_power_vs_level_desc, st.powerVsLevel.size), xRange = 0f..100f)
                Text(stringResource(R.string.power_vs_level_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            AppCard {
                CardHeader(stringResource(R.string.temp_vs_power), Icons.Outlined.Thermostat)
                if (st.tempVsPower.size < 3) Text(stringResource(R.string.insufficient_history), style = MaterialTheme.typography.bodyMedium)
                else ScatterChart(st.tempVsPower, stringResource(R.string.chart_temp_vs_power_desc, st.tempVsPower.size))
            }
        }
        item { GroupLabel(stringResource(R.string.all_sessions)) }
        items(st.sessions, key = { it.id }) { SessionRow(it, st.unit) { onOpenSession(it.id) } }
    }
}

@Composable
fun SessionDetailScreen(onBack: () -> Unit, vm: SessionDetailViewModel = hiltViewModel()) {
    val st by vm.state.collectAsStateWithLifecycle()
    val s = st.session
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.delete_session)) },
        text = { Text(stringResource(R.string.delete_session_confirm)) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(onBack) }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
    )
    ScreenScaffold(
        title = stringResource(R.string.session_details), onBack = onBack,
        actions = {
            if (s != null && !s.isOpen) IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.delete_session))
            }
        },
    ) {
        if (s == null) {
            if (st.loaded) item { EmptyState(R.string.session_not_found, R.string.session_not_found_body) }
            return@ScreenScaffold
        }
        item {
            AppCard {
                Text("${s.startLevel?.let(Fmt::percent) ?: "—"} → ${s.endLevel?.let(Fmt::percent) ?: "—"}",
                    style = MaterialTheme.typography.displaySmall)
                Text(dateTimeText(s.startTime), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(stringResource(when { s.isOpen -> R.string.session_open; s.isComplete -> R.string.session_complete; else -> R.string.session_partial }),
                        if (s.isComplete || s.isOpen) MaterialTheme.colorScheme.primary else Tokens.Warning)
                    ChargeSpeed.classify(s.averagePowerW)?.let { StatusPill(stringResource(it.labelRes()), it.color(), icon = Icons.Outlined.Speed) }
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(6.dp))
                KeyValueRow(stringResource(R.string.start_time), dateTimeText(s.startTime) + if (!s.startPrecise) " *" else "")
                KeyValueRow(stringResource(R.string.end_time), s.endTime?.let { dateTimeText(it) + if (!s.endPrecise) " *" else "" }
                    ?: stringResource(R.string.session_open))
                KeyValueRow(stringResource(R.string.session_duration), durationText(s.durationMs()))
                KeyValueRow(stringResource(R.string.level_change), "${s.startLevel?.let(Fmt::percent) ?: "—"} → ${s.endLevel?.let(Fmt::percent) ?: "—"}")
                KeyValueRow(stringResource(R.string.charging_source), stringResource(s.source.labelRes()))
                KeyValueRow(stringResource(R.string.average_power), s.averagePowerW?.let(Fmt::power), kind = ValueKind.ESTIMATE)
                KeyValueRow(stringResource(R.string.peak_power), s.peakPowerW?.let(Fmt::power), kind = ValueKind.ESTIMATE)
                KeyValueRow(stringResource(R.string.charge_speed), s.percentPerHour?.let(Fmt::rate))
                KeyValueRow(stringResource(R.string.start_end_temperature),
                    if (s.startTempC != null && s.endTempC != null) "${Fmt.temperature(s.startTempC, st.unit)} → ${Fmt.temperature(s.endTempC, st.unit)}" else null)
                KeyValueRow(stringResource(R.string.average_temperature), s.averageTempC?.let { Fmt.temperature(it, st.unit) })
                KeyValueRow(stringResource(R.string.max_temperature), s.maxTempC?.let { Fmt.temperature(it, st.unit) })
                KeyValueRow(stringResource(R.string.interruptions), s.interruptions.toString())
                KeyValueRow(stringResource(R.string.samples), s.sampleCount.toString())
                if (s.endReason == SessionEndReason.DEVICE_RESTART) KeyValueRow(stringResource(R.string.end_reason), stringResource(R.string.end_reason_restart))
            }
        }
        if (!s.isComplete && !s.isOpen) item { InfoBanner(stringResource(R.string.partial_session_explainer), tint = Tokens.Warning) }
        if (st.typicalPowerW != null || st.typicalPercentPerHour != null) item {
            AppCard {
                CardHeader(stringResource(R.string.compared_to_usual, stringResource(s.source.labelRes())), Icons.AutoMirrored.Outlined.CompareArrows)
                KeyValueRow(stringResource(R.string.average_power),
                    listOfNotNull(s.averagePowerW?.let(Fmt::power), st.typicalPowerW?.let { stringResource(R.string.usual_value, Fmt.power(it)) })
                        .joinToString(" · ").ifEmpty { null })
                KeyValueRow(stringResource(R.string.charge_speed),
                    listOfNotNull(s.percentPerHour?.let(Fmt::rate), st.typicalPercentPerHour?.let { stringResource(R.string.usual_value, Fmt.rate(it)) })
                        .joinToString(" · ").ifEmpty { null })
                Text(stringResource(R.string.speed_comparison_note), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val span = timeText(s.startTime) to timeText(s.endTime ?: s.lastSampleTime)
        val readings = st.readings
        item {
            AppCard {
                CardHeader(stringResource(R.string.level_over_time), Icons.Outlined.BatteryChargingFull)
                val pts = readings.mapNotNull { r -> r.level?.let { ChartPoint((r.timestamp - s.startTime) / 60_000f, it.toFloat()) } }
                if (pts.size < 2) Text(stringResource(R.string.not_enough_samples)) else
                    LineChart(pts, stringResource(R.string.chart_level_desc, Fmt.percent(pts.first().y.toInt()), Fmt.percent(pts.last().y.toInt())),
                        minY = 0f, maxY = 100f, yLabel = { "${it.toInt()}%" }, gapThreshold = 45f, showDots = true, xLabels = span)
            }
        }
        item {
            AppCard {
                CardHeader(stringResource(R.string.power_over_time), Icons.Outlined.Bolt)
                val pts = readings.mapNotNull { r -> r.powerW?.takeIf { it > 0 }?.let { ChartPoint((r.timestamp - s.startTime) / 60_000f, it) } }
                if (pts.size < 2) Text(stringResource(R.string.not_enough_samples)) else
                    LineChart(pts, stringResource(R.string.chart_live_power_desc, Fmt.power(pts.minOf { it.y }), Fmt.power(pts.maxOf { it.y }), Fmt.power(pts.last().y)),
                        yLabel = { Fmt.decimal1(it) + " W" }, color = MaterialTheme.colorScheme.secondary, gapThreshold = 45f, showDots = true, xLabels = span)
            }
        }
        item {
            AppCard {
                CardHeader(stringResource(R.string.temperature_over_time), Icons.Outlined.Thermostat)
                val pts = readings.mapNotNull { r -> r.temperatureC?.let { ChartPoint((r.timestamp - s.startTime) / 60_000f, com.hikmahlab.chargemaster.core.common.TemperatureConverter.fromCelsius(it, st.unit)) } }
                if (pts.size < 2) Text(stringResource(R.string.not_enough_samples)) else
                    LineChart(pts, stringResource(R.string.chart_temp_desc, Fmt.decimal1(pts.minOf { it.y }), Fmt.decimal1(pts.maxOf { it.y })),
                        yLabel = { Fmt.decimal1(it) + "°" }, color = Tokens.Warning, gapThreshold = 45f, showDots = true, xLabels = span)
            }
        }
        st.capacity?.let { est ->
            item {
                AppCard {
                    CardHeader(stringResource(R.string.capacity_from_session), Icons.Outlined.BatteryStd)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(est.capacityMah?.let(Fmt::capacity) ?: stringResource(R.string.insufficient_data), style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        ConfidenceChip(est.confidence)
                    }
                    Text(stringResource(R.string.capacity_session_note, est.levelSpan), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
