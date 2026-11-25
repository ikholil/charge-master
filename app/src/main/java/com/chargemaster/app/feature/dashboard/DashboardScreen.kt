package com.chargemaster.app.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.DeviceThermostat
import androidx.compose.material.icons.outlined.ElectricalServices
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.chargemaster.app.R
import com.chargemaster.app.core.common.Clock
import com.chargemaster.app.core.common.Fmt
import com.chargemaster.app.core.design.AppCard
import com.chargemaster.app.core.design.BatteryRing
import com.chargemaster.app.core.design.InfoBanner
import com.chargemaster.app.core.design.KindTag
import com.chargemaster.app.core.design.MetricCard
import com.chargemaster.app.core.design.MetricGrid
import com.chargemaster.app.core.design.ValueKind
import com.chargemaster.app.core.design.durationText
import com.chargemaster.app.core.design.labelRes
import com.chargemaster.app.domain.engine.CapacityEstimator
import com.chargemaster.app.domain.engine.DischargeAnalyzer
import com.chargemaster.app.domain.engine.EstimateMethod
import com.chargemaster.app.domain.engine.TimeEstimate
import com.chargemaster.app.domain.engine.TimeEstimator
import com.chargemaster.app.domain.model.BatteryHealthStatus
import com.chargemaster.app.domain.model.BatterySnapshot
import com.chargemaster.app.domain.model.ChargingSession
import com.chargemaster.app.domain.model.ChargingStatus
import com.chargemaster.app.domain.model.TemperatureUnit
import com.chargemaster.app.domain.model.ThermalStatus
import com.chargemaster.app.domain.repository.BatteryRepository
import com.chargemaster.app.domain.repository.HealthRepository
import com.chargemaster.app.domain.repository.PreferencesRepository
import com.chargemaster.app.domain.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class DashboardState(
    val snapshot: BatterySnapshot? = null,
    val unit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val session: ChargingSession? = null,
    val etaFull: TimeEstimate? = null,
    val eta80: TimeEstimate? = null,
    val remaining: TimeEstimate? = null,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    battery: BatteryRepository,
    sessions: SessionRepository,
    health: HealthRepository,
    prefs: PreferencesRepository,
    clock: Clock,
) : ViewModel() {
    private val weekMs = 7 * 86_400_000L
    private val dischargeRate = battery.observeReadingsSince(clock.now() - weekMs)
        .map { DischargeAnalyzer.analyze(it, weekMs).averageRatePerHour }
    private val capacity = health.observeSnapshots().map { CapacityEstimator.combine(it).first }

    val state: StateFlow<DashboardState> = combine(
        battery.liveSnapshots, prefs.preferences, sessions.observeOpenSession(), dischargeRate, capacity,
    ) { s, p, session, rate, cap ->
        val charging = s.isPluggedIn
        DashboardState(
            snapshot = s,
            unit = p.temperatureUnit,
            session = session,
            etaFull = if (charging) TimeEstimator.timeToLevel(100, s.level, s.systemChargeTimeRemainingMs, session, s.currentNowMa, cap) else null,
            eta80 = if (charging) TimeEstimator.timeToLevel(80, s.level, null, session, s.currentNowMa, cap) else null,
            remaining = if (!charging) TimeEstimator.timeRemainingOnBattery(s.level, rate) else null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState())
}

@Composable
fun DashboardScreen(
    onOpenCharging: () -> Unit,
    onOpenTemperature: () -> Unit,
    onOpenHealth: () -> Unit,
    vm: DashboardViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    DashboardContent(state, onOpenCharging, onOpenTemperature, onOpenHealth)
}

@Composable
fun DashboardContent(
    state: DashboardState,
    onOpenCharging: () -> Unit = {},
    onOpenTemperature: () -> Unit = {},
    onOpenHealth: () -> Unit = {},
) {
    val s = state.snapshot
    LazyColumn(
        Modifier.fillMaxWidth().statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.tagline), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            val statusLabel = stringResource((s?.status ?: ChargingStatus.UNKNOWN).labelRes())
            val levelText = s?.level?.let(Fmt::percent) ?: stringResource(R.string.unavailable)
            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                BatteryRing(
                    level = s?.level, charging = s?.isPluggedIn == true,
                    modifier = Modifier.semantics(mergeDescendants = true) {
                        contentDescription = "$levelText, $statusLabel"
                    },
                ) {
                    Text(levelText, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.battery_remaining), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(50)) {
                        val src = s?.takeIf { it.isPluggedIn }?.let { " · " + stringResource(it.source.labelRes()) } ?: ""
                        Text("$statusLabel$src", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                    }
                }
            }
        }
        item { EstimateCard(state) }
        item {
            val unit = state.unit
            MetricGrid(
                listOf(
                    { m -> MetricCard(stringResource(R.string.metric_temperature), s?.temperatureC?.let { Fmt.temperature(it, unit) }, Icons.Outlined.Thermostat, m) },
                    { m -> MetricCard(stringResource(R.string.metric_voltage), s?.voltageMv?.let(Fmt::voltage), Icons.Outlined.ElectricalServices, m) },
                    { m ->
                        MetricCard(
                            stringResource(R.string.metric_power), s?.powerW?.let { Fmt.power(kotlin.math.abs(it)) }, Icons.Outlined.Bolt, m,
                            kind = ValueKind.ESTIMATE, unavailableText = stringResource(R.string.not_supported),
                            footnote = s?.powerW?.let { stringResource(if (it >= 0) R.string.direction_in else R.string.direction_out) },
                        )
                    },
                    { m ->
                        MetricCard(
                            stringResource(R.string.metric_current), s?.currentNowMa?.let(Fmt::current), Icons.Outlined.Speed, m,
                            unavailableText = stringResource(R.string.not_supported),
                            footnote = if (s?.currentCalibrationUncertain == true) stringResource(R.string.current_uncertain) else null,
                        )
                    },
                    { m ->
                        MetricCard(stringResource(R.string.metric_android_health),
                            s?.health?.takeIf { it != BatteryHealthStatus.UNKNOWN }?.let { stringResource(it.labelRes()) },
                            Icons.Outlined.HealthAndSafety, m)
                    },
                    { m ->
                        MetricCard(stringResource(R.string.metric_thermal),
                            s?.thermalStatus?.takeIf { it != ThermalStatus.UNSUPPORTED }?.let { stringResource(it.labelRes()) },
                            Icons.Outlined.DeviceThermostat, m, unavailableText = stringResource(R.string.not_supported))
                    },
                ),
            )
        }
        if (s?.isPluggedIn == true) item {
            AppCard(onClick = onOpenCharging) {
                Text(stringResource(R.string.session_in_progress), style = MaterialTheme.typography.titleMedium)
                state.session?.let {
                    Text(stringResource(R.string.session_in_progress_detail, durationText(it.durationMs(s.timestamp)),
                        it.startLevel?.let(Fmt::percent) ?: "—"), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppCard(Modifier.weight(1f), onClick = onOpenTemperature) {
                    Text(stringResource(R.string.temperature_monitor), style = MaterialTheme.typography.titleSmall)
                }
                AppCard(Modifier.weight(1f), onClick = onOpenHealth) {
                    Text(stringResource(R.string.battery_health), style = MaterialTheme.typography.titleSmall)
                }
            }
        }
        item { InfoBanner(stringResource(R.string.dashboard_disclaimer)) }
    }
}

@Composable
private fun EstimateCard(state: DashboardState) {
    val s = state.snapshot ?: return
    AppCard {
        if (s.isPluggedIn) {
            if (s.status == ChargingStatus.FULL) {
                Text(stringResource(R.string.status_full), style = MaterialTheme.typography.titleLarge)
                return@AppCard
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.eta_full), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(state.etaFull?.let { durationText(it.minutes * 60_000) } ?: stringResource(R.string.estimating),
                        style = MaterialTheme.typography.headlineSmall)
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.eta_80), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(state.eta80?.let { durationText(it.minutes * 60_000) } ?: stringResource(R.string.estimating),
                        style = MaterialTheme.typography.headlineSmall)
                }
            }
            Spacer(Modifier.height(6.dp))
            KindTag(ValueKind.ESTIMATE)
            state.etaFull?.let { Text(methodText(it.method), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            Text(stringResource(R.string.remaining_on_battery), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(state.remaining?.let { durationText(it.minutes * 60_000) } ?: stringResource(R.string.insufficient_history),
                style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            KindTag(ValueKind.ESTIMATE)
            Text(stringResource(R.string.remaining_explainer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun methodText(m: EstimateMethod): String = stringResource(
    when (m) {
        EstimateMethod.SYSTEM -> R.string.method_system
        EstimateMethod.OBSERVED_RATE -> R.string.method_observed
        EstimateMethod.CURRENT_AND_CAPACITY -> R.string.method_current
    },
)
