package com.chargemaster.app.feature.temperature

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.BiasAlignment
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.chargemaster.app.core.design.CardHeader
import com.chargemaster.app.core.design.IconBadge
import com.chargemaster.app.core.design.StatusPill
import com.chargemaster.app.core.design.ZoneGauge
import com.chargemaster.app.core.design.color
import com.chargemaster.app.core.design.descriptionRes
import com.chargemaster.app.domain.engine.TemperatureZone
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeviceThermostat
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.chargemaster.app.core.design.dateTimeText
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.chargemaster.app.R
import com.chargemaster.app.core.common.Clock
import com.chargemaster.app.core.common.Fmt
import com.chargemaster.app.core.common.TemperatureConverter
import com.chargemaster.app.core.design.AppCard
import com.chargemaster.app.core.design.ChartPoint
import com.chargemaster.app.core.design.InfoBanner
import com.chargemaster.app.core.design.KeyValueRow
import com.chargemaster.app.core.design.LineChart
import com.chargemaster.app.core.design.MetricCard
import com.chargemaster.app.core.design.MetricGrid
import com.chargemaster.app.core.design.Period
import com.chargemaster.app.core.design.PeriodSelector
import com.chargemaster.app.core.design.ScreenScaffold
import com.chargemaster.app.core.design.Tokens
import com.chargemaster.app.core.design.ValueKind
import com.chargemaster.app.core.design.labelRes
import com.chargemaster.app.domain.engine.TemperatureAnalyzer
import com.chargemaster.app.domain.engine.TemperatureStats
import com.chargemaster.app.domain.model.BatteryReading
import com.chargemaster.app.domain.model.BatterySnapshot
import com.chargemaster.app.domain.model.TemperatureUnit
import com.chargemaster.app.domain.model.ThermalStatus
import com.chargemaster.app.domain.repository.BatteryRepository
import com.chargemaster.app.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class TemperatureState(
    val period: Period = Period.H24,
    val snapshot: BatterySnapshot? = null,
    val readings: List<BatteryReading> = emptyList(),
    val stats: TemperatureStats? = null,
    val unit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val thresholdC: Float = 42f,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TemperatureViewModel @Inject constructor(
    battery: BatteryRepository,
    prefs: PreferencesRepository,
    clock: Clock,
) : ViewModel() {
    val period = MutableStateFlow(Period.H24)
    val state: StateFlow<TemperatureState> = period.flatMapLatest { p ->
        combine(battery.liveSnapshots, battery.observeReadingsSince(clock.now() - p.ms), prefs.preferences) { s, r, pr ->
            TemperatureState(p, s, r, TemperatureAnalyzer.analyze(r), pr.temperatureUnit, pr.alerts.highTempThresholdC)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TemperatureState())
}

@Composable
fun TemperatureScreen(onBack: () -> Unit, vm: TemperatureViewModel = hiltViewModel()) {
    val st by vm.state.collectAsStateWithLifecycle()
    val u = st.unit
    fun t(c: Float?) = c?.let { Fmt.temperature(it, u) }
    ScreenScaffold(title = stringResource(R.string.temperature_monitor), onBack = onBack) {
        item { TemperatureHero(st.snapshot?.temperatureC, u) }
        item {
            MetricGrid(
                listOf(
                    { m -> MetricCard(stringResource(R.string.metric_thermal),
                        st.snapshot?.thermalStatus?.takeIf { it != ThermalStatus.UNSUPPORTED }?.let { stringResource(it.labelRes()) },
                        Icons.Outlined.DeviceThermostat, m, unavailableText = stringResource(R.string.not_supported)) },
                    { m -> MetricCard(stringResource(R.string.alert_threshold), Fmt.temperature(st.thresholdC, u), Icons.Outlined.WarningAmber, m,
                        kind = ValueKind.USER_PROVIDED) },
                    { m -> MetricCard(stringResource(R.string.temperature_trend),
                        st.stats?.recentTrendPerHour?.let { stringResource(R.string.trend_per_hour, Fmt.decimal1(TemperatureConverter.deltaFromCelsius(it, u))) },
                        Icons.AutoMirrored.Outlined.TrendingUp, m, kind = ValueKind.HISTORICAL, unavailableText = stringResource(R.string.insufficient_data)) },
                ),
            )
        }
        item { PeriodSelector(st.period, { vm.period.value = it }) }
        item {
            AppCard {
                CardHeader(stringResource(R.string.temperature_history), Icons.AutoMirrored.Outlined.ShowChart, tint = Tokens.Warning)
                val origin = st.readings.firstOrNull()?.timestamp ?: 0L
                val pts = st.readings.mapNotNull { r -> r.temperatureC?.let { ChartPoint((r.timestamp - origin) / 3_600_000f, TemperatureConverter.fromCelsius(it, u)) } }
                val span = st.readings.firstOrNull()?.let { dateTimeText(it.timestamp) to dateTimeText(st.readings.last().timestamp) }
                if (pts.size < 2) Text(stringResource(R.string.not_enough_samples), style = MaterialTheme.typography.bodyMedium)
                else LineChart(
                    pts,
                    stringResource(R.string.chart_temp_desc, Fmt.decimal1(pts.minOf { it.y }), Fmt.decimal1(pts.maxOf { it.y })),
                    color = Tokens.Warning, yLabel = { Fmt.decimal1(it) + "°" },
                    threshold = TemperatureConverter.fromCelsius(st.thresholdC, u), gapThreshold = 1.5f, xLabels = span,
                )
            }
        }
        st.stats?.let { s ->
            item {
                AppCard {
                    CardHeader(stringResource(R.string.summary), Icons.Outlined.Summarize, tint = Tokens.Warning)
                    KeyValueRow(stringResource(R.string.min_observed), t(s.min), kind = ValueKind.HISTORICAL)
                    KeyValueRow(stringResource(R.string.max_observed), t(s.max), kind = ValueKind.HISTORICAL)
                    KeyValueRow(stringResource(R.string.average), t(s.average), kind = ValueKind.HISTORICAL)
                    KeyValueRow(stringResource(R.string.charging_average), t(s.chargingAverage), kind = ValueKind.HISTORICAL)
                    KeyValueRow(stringResource(R.string.charging_max), t(s.chargingMax), kind = ValueKind.HISTORICAL)
                    KeyValueRow(stringResource(R.string.discharging_average), t(s.dischargingAverage), kind = ValueKind.HISTORICAL)
                    KeyValueRow(stringResource(R.string.discharging_max), t(s.dischargingMax), kind = ValueKind.HISTORICAL)
                    Spacer(Modifier.height(4.dp))
                    Text(pluralStringResource(R.plurals.samples_count, s.sampleCount, s.sampleCount), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { InfoBanner(stringResource(R.string.temperature_explainer)) }
    }
}

@Composable
private fun TemperatureHero(celsius: Float?, unit: TemperatureUnit) {
    val zone = TemperatureZone.of(celsius)
    val color = zone?.color() ?: MaterialTheme.colorScheme.outline
    val zoneDescription = stringResource(R.string.temperature_zone) + ": " +
        (zone?.let { stringResource(it.labelRes()) } ?: stringResource(R.string.unavailable))
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Outlined.Thermostat, tint = color, size = 44.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.battery_temperature), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(celsius?.let { Fmt.temperature(it, unit) } ?: stringResource(R.string.unavailable), style = MaterialTheme.typography.headlineMedium)
            }
            zone?.let { StatusPill(stringResource(it.labelRes()), color) }
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.temperature_zone), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        ZoneGauge(
            value = celsius, range = 0f..50f,
            zones = listOf(
                TemperatureZone.COLD_BELOW to TemperatureZone.COLD.color(),
                TemperatureZone.WARM_FROM to TemperatureZone.IDEAL.color(),
                TemperatureZone.HOT_FROM to TemperatureZone.WARM.color(),
                TemperatureZone.CRITICAL_FROM to TemperatureZone.HOT.color(),
                50f to TemperatureZone.CRITICAL.color(),
            ),
            modifier = Modifier.semantics { contentDescription = zoneDescription },
        )
        Box(Modifier.fillMaxWidth().height(16.dp)) {
            listOf(0f, TemperatureZone.COLD_BELOW, TemperatureZone.WARM_FROM, TemperatureZone.HOT_FROM, TemperatureZone.CRITICAL_FROM, 50f).forEach { c ->
                Text(
                    String.format(java.util.Locale.getDefault(), "%.0f°", TemperatureConverter.fromCelsius(c, unit)),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(BiasAlignment(c / 25f - 1f, 0f)),
                )
            }
        }
        zone?.let {
            Spacer(Modifier.height(12.dp))
            InfoBanner(stringResource(it.descriptionRes()), tint = color)
        }
    }
}
