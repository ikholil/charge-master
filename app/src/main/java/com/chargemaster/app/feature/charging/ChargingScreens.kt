package com.chargemaster.app.feature.charging

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
import com.chargemaster.app.R
import com.chargemaster.app.core.common.Fmt
import com.chargemaster.app.core.design.AppCard
import com.chargemaster.app.core.design.BarChart
import com.chargemaster.app.core.design.ChartPoint
import com.chargemaster.app.core.design.EmptyState
import com.chargemaster.app.core.design.InfoBanner
import com.chargemaster.app.core.design.KeyValueRow
import com.chargemaster.app.core.design.KindTag
import com.chargemaster.app.core.design.LineChart
import com.chargemaster.app.core.design.MetricCard
import com.chargemaster.app.core.design.MetricGrid
import com.chargemaster.app.core.design.PeriodSelector
import com.chargemaster.app.core.design.ScatterChart
import com.chargemaster.app.core.design.ScreenScaffold
import com.chargemaster.app.core.design.SectionHeader
import com.chargemaster.app.core.design.Tokens
import com.chargemaster.app.core.design.ValueKind
import com.chargemaster.app.core.design.dateTimeText
import com.chargemaster.app.core.design.durationText
import com.chargemaster.app.core.design.labelRes
import com.chargemaster.app.core.design.ConfidenceChip
import com.chargemaster.app.domain.model.ChargingSession
import com.chargemaster.app.domain.model.ChargingStatus
import com.chargemaster.app.domain.model.SessionEndReason
import com.chargemaster.app.domain.model.TemperatureUnit
import com.chargemaster.app.feature.dashboard.methodText
import kotlin.math.abs

@Composable
fun ChargingScreen(onOpenHistory: () -> Unit, onOpenSession: (Long) -> Unit, vm: ChargingViewModel = hiltViewModel()) {
    val st by vm.state.collectAsStateWithLifecycle()
    val s = st.snapshot
    ScreenScaffold(title = stringResource(R.string.charging_monitor)) {
        item {
            AppCard {
                val status = stringResource((s?.status ?: ChargingStatus.UNKNOWN).labelRes())
                Text(status, style = MaterialTheme.typography.titleLarge)
                Text(
                    if (s?.isPluggedIn == true) stringResource(R.string.charging_via, stringResource(s.source.labelRes()))
                    else stringResource(R.string.plug_in_hint),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { (s?.level ?: 0) / 100f },
                    modifier = Modifier.fillMaxWidth().height(10.dp),
                )
                Spacer(Modifier.height(6.dp))
                Text(s?.level?.let(Fmt::percent) ?: stringResource(R.string.unavailable), style = MaterialTheme.typography.headlineSmall)
            }
        }
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
                SectionHeader(stringResource(R.string.live_power_chart))
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
                    )
                    Text(stringResource(R.string.live_chart_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { InfoBanner(stringResource(R.string.no_charge_control)) }
        item {
            SectionHeader(stringResource(R.string.recent_sessions)) {
                TextButton(onClick = onOpenHistory) { Text(stringResource(R.string.view_all)) }
            }
        }
        if (st.recent.isEmpty()) item { EmptyState(R.string.no_sessions_title, R.string.no_sessions_body) }
        items(st.recent, key = { it.id }) { SessionRow(it, st.unit) { onOpenSession(it.id) } }
    }
}


@Composable
fun SessionRow(s: ChargingSession, unit: TemperatureUnit, onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(dateTimeText(s.startTime), style = MaterialTheme.typography.titleSmall)
                Text(
                    "${s.startLevel?.let(Fmt::percent) ?: "—"} → ${s.endLevel?.let(Fmt::percent) ?: "—"} · ${durationText(s.durationMs())} · ${stringResource(s.source.labelRes())}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                s.averagePowerW?.let { Text(Fmt.power(it), style = MaterialTheme.typography.titleSmall) }
                Text(
                    stringResource(when { s.isOpen -> R.string.session_open; s.isComplete -> R.string.session_complete; else -> R.string.session_partial }),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (s.isComplete || s.isOpen) MaterialTheme.colorScheme.primary else Tokens.Warning,
                )
            }
        }
    }
}

@Composable
fun ChargingHistoryScreen(onBack: () -> Unit, onOpenSession: (Long) -> Unit, vm: ChargingHistoryViewModel = hiltViewModel()) {
    val st by vm.state.collectAsStateWithLifecycle()
    ScreenScaffold(title = stringResource(R.string.charging_history), onBack = onBack) {
        item { PeriodSelector(st.period, { vm.period.value = it }, options = listOf(com.chargemaster.app.core.design.Period.D7, com.chargemaster.app.core.design.Period.D30)) }
        val stats = st.stats
        if (stats == null || st.sessions.isEmpty()) {
            item { EmptyState(R.string.no_sessions_title, R.string.no_sessions_body) }
            return@ScreenScaffold
        }
        item {
            AppCard {
                SectionHeader(stringResource(R.string.charging_calendar))
                BarChart(
                    stats.sessionsPerDay.map { it.toFloat() },
                    description = stringResource(R.string.chart_sessions_per_day_desc, stats.sessionCount, stats.sessionsPerDay.size),
                )
                Text(stringResource(R.string.sessions_count, stats.sessionCount), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            AppCard {
                SectionHeader(stringResource(R.string.summary))
                KeyValueRow(stringResource(R.string.average_duration), stats.averageDurationMs?.let { durationText(it) }, kind = ValueKind.HISTORICAL)
                KeyValueRow(stringResource(R.string.peak_observed_power), stats.peakPowerW?.let(Fmt::power), kind = ValueKind.HISTORICAL)
                KeyValueRow(stringResource(R.string.fastest_session), stats.fastest?.percentPerHour?.let(Fmt::rate), kind = ValueKind.HISTORICAL)
                KeyValueRow(stringResource(R.string.slowest_session), stats.slowest?.percentPerHour?.let(Fmt::rate), kind = ValueKind.HISTORICAL)
            }
        }
        item {
            AppCard {
                SectionHeader(stringResource(R.string.speed_by_source))
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
                SectionHeader(stringResource(R.string.power_vs_level))
                if (st.powerVsLevel.size < 5) Text(stringResource(R.string.insufficient_history), style = MaterialTheme.typography.bodyMedium)
                else ScatterChart(st.powerVsLevel, stringResource(R.string.chart_power_vs_level_desc, st.powerVsLevel.size), xRange = 0f..100f)
                Text(stringResource(R.string.power_vs_level_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            AppCard {
                SectionHeader(stringResource(R.string.temp_vs_power))
                if (st.tempVsPower.size < 3) Text(stringResource(R.string.insufficient_history), style = MaterialTheme.typography.bodyMedium)
                else ScatterChart(st.tempVsPower, stringResource(R.string.chart_temp_vs_power_desc, st.tempVsPower.size))
            }
        }
        item { SectionHeader(stringResource(R.string.all_sessions)) }
        items(st.sessions, key = { it.id }) { SessionRow(it, st.unit) { onOpenSession(it.id) } }
    }
}

@Composable
fun SessionDetailScreen(onBack: () -> Unit, vm: SessionDetailViewModel = hiltViewModel()) {
    val st by vm.state.collectAsStateWithLifecycle()
    val s = st.session
    ScreenScaffold(title = stringResource(R.string.session_details), onBack = onBack) {
        if (s == null) {
            if (st.loaded) item { EmptyState(R.string.session_not_found, R.string.session_not_found_body) }
            return@ScreenScaffold
        }
        item {
            AppCard {
                Text(dateTimeText(s.startTime), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KindTag(ValueKind.HISTORICAL)
                    Text(stringResource(when { s.isOpen -> R.string.session_open; s.isComplete -> R.string.session_complete; else -> R.string.session_partial }),
                        style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(8.dp))
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
        val readings = st.readings
        item {
            AppCard {
                SectionHeader(stringResource(R.string.level_over_time))
                val pts = readings.mapNotNull { r -> r.level?.let { ChartPoint((r.timestamp - s.startTime) / 60_000f, it.toFloat()) } }
                if (pts.size < 2) Text(stringResource(R.string.not_enough_samples)) else
                    LineChart(pts, stringResource(R.string.chart_level_desc, Fmt.percent(pts.first().y.toInt()), Fmt.percent(pts.last().y.toInt())),
                        minY = 0f, maxY = 100f, yLabel = { "${it.toInt()}%" }, gapThreshold = 45f, showDots = true)
            }
        }
        item {
            AppCard {
                SectionHeader(stringResource(R.string.power_over_time))
                val pts = readings.mapNotNull { r -> r.powerW?.takeIf { it > 0 }?.let { ChartPoint((r.timestamp - s.startTime) / 60_000f, it) } }
                if (pts.size < 2) Text(stringResource(R.string.not_enough_samples)) else
                    LineChart(pts, stringResource(R.string.chart_live_power_desc, Fmt.power(pts.minOf { it.y }), Fmt.power(pts.maxOf { it.y }), Fmt.power(pts.last().y)),
                        yLabel = { Fmt.decimal1(it) + " W" }, color = MaterialTheme.colorScheme.secondary, gapThreshold = 45f, showDots = true)
            }
        }
        item {
            AppCard {
                SectionHeader(stringResource(R.string.temperature_over_time))
                val pts = readings.mapNotNull { r -> r.temperatureC?.let { ChartPoint((r.timestamp - s.startTime) / 60_000f, com.chargemaster.app.core.common.TemperatureConverter.fromCelsius(it, st.unit)) } }
                if (pts.size < 2) Text(stringResource(R.string.not_enough_samples)) else
                    LineChart(pts, stringResource(R.string.chart_temp_desc, Fmt.decimal1(pts.minOf { it.y }), Fmt.decimal1(pts.maxOf { it.y })),
                        yLabel = { Fmt.decimal1(it) + "°" }, color = Tokens.Warning, gapThreshold = 45f, showDots = true)
            }
        }
        st.capacity?.let { est ->
            item {
                AppCard {
                    SectionHeader(stringResource(R.string.capacity_from_session))
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
