package com.chargemaster.app.feature.insights

import androidx.compose.foundation.layout.Spacer
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

data class InsightsState(
    val period: Period = Period.H24,
    val readings: List<BatteryReading> = emptyList(),
    val stats: DischargeStats? = null,
    val remaining: TimeEstimate? = null,
    val pluggedIn: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InsightsViewModel @Inject constructor(battery: BatteryRepository, clock: Clock) : ViewModel() {
    val period = MutableStateFlow(Period.H24)
    val state: StateFlow<InsightsState> = period.flatMapLatest { p ->
        combine(battery.observeReadingsSince(clock.now() - p.ms), battery.liveSnapshots) { r, s ->
            val stats = DischargeAnalyzer.analyze(r, p.ms)
            InsightsState(p, r, stats, TimeEstimator.timeRemainingOnBattery(s.level, stats.averageRatePerHour), s.isPluggedIn)
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
                val pts = st.readings.mapNotNull { r -> r.level?.let { ChartPoint(r.timestamp / 3_600_000f, it.toFloat()) } }
                if (pts.size < 2) Text(stringResource(R.string.not_enough_samples), style = MaterialTheme.typography.bodyMedium)
                else LineChart(
                    pts, stringResource(R.string.chart_level_desc, Fmt.percent(pts.first().y.toInt()), Fmt.percent(pts.last().y.toInt())),
                    minY = 0f, maxY = 100f, yLabel = { "${it.toInt()}%" }, gapThreshold = 2f,
                )
                Text(stringResource(R.string.timeline_gaps_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            MetricGrid(
                listOf(
                    { m -> MetricCard(stringResource(R.string.avg_discharge_rate), s?.averageRatePerHour?.let(Fmt::rate), Icons.Outlined.BatteryAlert, m,
                        kind = ValueKind.HISTORICAL, unavailableText = stringResource(R.string.insufficient_data)) },
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
