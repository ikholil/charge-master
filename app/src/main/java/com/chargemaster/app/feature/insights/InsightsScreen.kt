package com.chargemaster.app.feature.insights

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.pluralStringResource
import com.chargemaster.app.core.common.TemperatureConverter
import com.chargemaster.app.core.design.Tokens
import com.chargemaster.app.core.design.dateTimeText
import com.chargemaster.app.domain.engine.HabitAnalyzer
import com.chargemaster.app.domain.model.TemperatureUnit
import com.chargemaster.app.domain.repository.PreferencesRepository
import com.chargemaster.app.domain.repository.SessionRepository
import kotlinx.coroutines.flow.map
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.ScreenLockPortrait
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Timelapse
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.chargemaster.app.R
import com.chargemaster.app.core.common.Clock
import com.chargemaster.app.core.common.Fmt
import com.chargemaster.app.core.design.AppCard
import com.chargemaster.app.core.design.ChartPoint
import com.chargemaster.app.core.design.InfoBanner
import com.chargemaster.app.core.design.KeyValueRow
import com.chargemaster.app.core.design.LineChart
import com.chargemaster.app.core.design.MetricCard
import com.chargemaster.app.core.design.MetricGrid
import com.chargemaster.app.core.design.NavRow
import com.chargemaster.app.core.design.Period
import com.chargemaster.app.core.design.PeriodSelector
import com.chargemaster.app.core.design.ScreenScaffold
import com.chargemaster.app.core.design.SectionHeader
import com.chargemaster.app.core.design.ValueKind
import com.chargemaster.app.core.design.durationText
import com.chargemaster.app.domain.engine.DischargeAnalyzer
import com.chargemaster.app.domain.engine.DischargeStats
import com.chargemaster.app.domain.engine.TimeEstimate
import com.chargemaster.app.domain.engine.TimeEstimator
import com.chargemaster.app.domain.model.BatteryReading
import com.chargemaster.app.domain.repository.BatteryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class TimelineMetric { LEVEL, TEMPERATURE, VOLTAGE }

data class InsightsState(
    val period: Period = Period.H24,
    val readings: List<BatteryReading> = emptyList(),
    val stats: DischargeStats? = null,
    val remaining: TimeEstimate? = null,
    val pluggedIn: Boolean = false,
    /** 7-day average discharge rate, used as a baseline for shorter/longer periods. */
    val baselineRate: Float? = null,
    val habit: HabitAnalyzer.HabitScore? = null,
    val unit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val metric: TimelineMetric = TimelineMetric.LEVEL,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InsightsViewModel @Inject constructor(
    battery: BatteryRepository,
    sessions: SessionRepository,
    prefs: PreferencesRepository,
    clock: Clock,
) : ViewModel() {
    val period = MutableStateFlow(Period.H24)
    val metric = MutableStateFlow(TimelineMetric.LEVEL)
    private val weekMs = 7 * 86_400_000L
    private val baseline = battery.observeReadingsSince(clock.now() - weekMs).map { DischargeAnalyzer.analyze(it, weekMs).averageRatePerHour }
    private val habit = combine(sessions.observeSince(clock.now() - 30 * 86_400_000L), prefs.preferences) { list, p ->
        HabitAnalyzer.analyze(list, p.alerts) to p.temperatureUnit
    }

    val state: StateFlow<InsightsState> = period.flatMapLatest { p ->
        combine(battery.observeReadingsSince(clock.now() - p.ms), battery.liveSnapshots, baseline, habit, metric) { r, s, base, h, m ->
            val stats = DischargeAnalyzer.analyze(r, p.ms)
            InsightsState(
                period = p, readings = r, stats = stats,
                remaining = TimeEstimator.timeRemainingOnBattery(s.level, stats.averageRatePerHour ?: base),
                pluggedIn = s.isPluggedIn, baselineRate = base, habit = h.first, unit = h.second, metric = m,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsState())
}

@Composable
fun InsightsScreen(
    onOpenTemperature: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenReport: () -> Unit,
    vm: InsightsViewModel = hiltViewModel(),
) {
    val st by vm.state.collectAsStateWithLifecycle()
    val s = st.stats
    ScreenScaffold(title = stringResource(R.string.insights)) {
        item { PeriodSelector(st.period, { vm.period.value = it }) }
        item {
            AppCard {
                SectionHeader(stringResource(R.string.battery_timeline))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimelineMetric.entries.forEach { m ->
                        FilterChip(selected = st.metric == m, onClick = { vm.metric.value = m }, label = {
                            Text(stringResource(when (m) {
                                TimelineMetric.LEVEL -> R.string.metric_level
                                TimelineMetric.TEMPERATURE -> R.string.metric_temperature
                                TimelineMetric.VOLTAGE -> R.string.metric_voltage
                            }))
                        })
                    }
                }
                Spacer(Modifier.height(8.dp))
                val origin = st.readings.firstOrNull()?.timestamp ?: 0L
                fun x(t: Long) = (t - origin) / 3_600_000f
                val pts = st.readings.mapNotNull { r ->
                    when (st.metric) {
                        TimelineMetric.LEVEL -> r.level?.toFloat()
                        TimelineMetric.TEMPERATURE -> r.temperatureC?.let { TemperatureConverter.fromCelsius(it, st.unit) }
                        TimelineMetric.VOLTAGE -> r.voltageMv?.let { it / 1000f }
                    }?.let { ChartPoint(x(r.timestamp), it) }
                }
                val span = st.readings.firstOrNull()?.let { first -> dateTimeText(first.timestamp) to dateTimeText(st.readings.last().timestamp) }
                if (pts.size < 2) Text(stringResource(R.string.not_enough_samples), style = MaterialTheme.typography.bodyMedium)
                else when (st.metric) {
                    TimelineMetric.LEVEL -> LineChart(
                        pts, stringResource(R.string.chart_level_desc, Fmt.percent(pts.first().y.toInt()), Fmt.percent(pts.last().y.toInt())),
                        minY = 0f, maxY = 100f, yLabel = { "${it.toInt()}%" }, gapThreshold = 2f, xLabels = span,
                    )
                    TimelineMetric.TEMPERATURE -> LineChart(
                        pts, stringResource(R.string.chart_temp_desc, Fmt.decimal1(pts.minOf { it.y }), Fmt.decimal1(pts.maxOf { it.y })),
                        color = Tokens.Warning, yLabel = { Fmt.decimal1(it) + "°" }, gapThreshold = 2f, xLabels = span,
                    )
                    TimelineMetric.VOLTAGE -> LineChart(
                        pts, stringResource(R.string.chart_voltage_desc, Fmt.decimal1(pts.minOf { it.y }), Fmt.decimal1(pts.maxOf { it.y })),
                        color = Tokens.Info, yLabel = { String.format(java.util.Locale.getDefault(), "%.2f V", it) }, gapThreshold = 2f, xLabels = span,
                    )
                }
                Text(stringResource(R.string.timeline_gaps_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            MetricGrid(
                listOf(
                    { m -> MetricCard(stringResource(R.string.avg_discharge_rate), s?.averageRatePerHour?.let(Fmt::rate), Icons.Outlined.BatteryAlert, m,
                        kind = ValueKind.HISTORICAL, unavailableText = stringResource(R.string.insufficient_data),
                        footnote = st.baselineRate?.takeIf { st.period != Period.D7 }?.let { stringResource(R.string.vs_7day_average, Fmt.rate(it)) }) },
                    { m -> MetricCard(stringResource(R.string.remaining_on_battery), st.remaining?.let { durationText(it.minutes * 60_000) }, Icons.Outlined.Timelapse, m,
                        kind = ValueKind.ESTIMATE, unavailableText = stringResource(if (st.pluggedIn) R.string.status_charging else R.string.insufficient_data)) },
                    { m -> MetricCard(stringResource(R.string.screen_on_drain), s?.screenOnRatePerHour?.let(Fmt::rate), Icons.Outlined.PhoneAndroid, m,
                        kind = ValueKind.ESTIMATE, unavailableText = stringResource(R.string.insufficient_data),
                        footnote = s?.let { durationText((it.screenOnHours * 3_600_000).toLong()) }) },
                    { m -> MetricCard(stringResource(R.string.screen_off_drain), s?.screenOffRatePerHour?.let(Fmt::rate), Icons.Outlined.ScreenLockPortrait, m,
                        kind = ValueKind.ESTIMATE, unavailableText = stringResource(R.string.insufficient_data),
                        footnote = s?.let { durationText((it.screenOffHours * 3_600_000).toLong()) }) },
                ),
            )
        }
        s?.let { stats ->
            item {
                AppCard {
                    SectionHeader(stringResource(R.string.charging_vs_discharging))
                    KeyValueRow(stringResource(R.string.time_charging), durationText((stats.chargingHours * 3_600_000).toLong()), kind = ValueKind.HISTORICAL)
                    KeyValueRow(stringResource(R.string.time_discharging), durationText((stats.dischargingHours * 3_600_000).toLong()), kind = ValueKind.HISTORICAL)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.monitoring_coverage, Fmt.percent((stats.coverage * 100).toInt())), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(progress = { stats.coverage }, modifier = Modifier)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.coverage_explainer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { HabitCard(st.habit) }
        item { InfoBanner(stringResource(R.string.per_app_explainer)) }
        item {
            AppCard {
                NavRow(stringResource(R.string.temperature_monitor), stringResource(R.string.temperature_subtitle), Icons.Outlined.Thermostat, onOpenTemperature)
                NavRow(stringResource(R.string.charging_history), stringResource(R.string.history_subtitle), Icons.Outlined.History, onOpenHistory)
                NavRow(stringResource(R.string.battery_report), stringResource(R.string.report_subtitle), Icons.Outlined.Description, onOpenReport)
            }
        }
    }
}

@Composable
private fun HabitCard(habit: HabitAnalyzer.HabitScore?) {
    AppCard {
        SectionHeader(stringResource(R.string.habit_score))
        val score = habit?.score
        if (habit == null || score == null) {
            Text(stringResource(R.string.habit_needs_sessions, HabitAnalyzer.MIN_SESSIONS, habit?.sessionCount ?: 0),
                style = MaterialTheme.typography.bodyMedium)
            return@AppCard
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { score / 100f }, modifier = Modifier.size(72.dp), strokeWidth = 7.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant)
                Text(score.toString(), style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(when {
                    score >= 80 -> R.string.habit_great
                    score >= 60 -> R.string.habit_good
                    else -> R.string.habit_room
                }), style = MaterialTheme.typography.titleMedium)
                Text(pluralStringResource(R.plurals.habit_based_on, habit.sessionCount, habit.sessionCount),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(8.dp))
        habit.indicators.forEach { r ->
            val label = stringResource(when (r.indicator) {
                HabitAnalyzer.Indicator.STOPPED_NEAR_TARGET -> R.string.habit_target
                HabitAnalyzer.Indicator.AVOIDED_DEEP_DISCHARGE -> R.string.habit_deep
                HabitAnalyzer.Indicator.STAYED_COOL -> R.string.habit_cool
                HabitAnalyzer.Indicator.AVOIDED_LONG_CHARGES -> R.string.habit_long
            })
            Column(Modifier.padding(vertical = 4.dp)) {
                Row {
                    Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text("${r.met}/${r.eligible}", style = MaterialTheme.typography.labelLarge)
                }
                LinearProgressIndicator(progress = { r.ratio }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.habit_explainer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
