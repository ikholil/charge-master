package com.chargemaster.app.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.DeviceThermostat
import androidx.compose.material.icons.outlined.ElectricBolt
import androidx.compose.material.icons.outlined.ElectricalServices
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.chargemaster.app.core.design.CardHeader
import com.chargemaster.app.core.design.IconBadge
import com.chargemaster.app.core.design.InfoBanner
import com.chargemaster.app.core.design.KindDot
import com.chargemaster.app.core.design.MetricCard
import com.chargemaster.app.core.design.MetricGrid
import com.chargemaster.app.core.design.NavRow
import com.chargemaster.app.core.design.ProgressTrack
import com.chargemaster.app.core.design.StatBlock
import com.chargemaster.app.core.design.StatusPill
import com.chargemaster.app.core.design.Tokens
import com.chargemaster.app.core.design.ValueKind
import com.chargemaster.app.core.design.color
import com.chargemaster.app.core.design.durationText
import com.chargemaster.app.core.design.labelRes
import com.chargemaster.app.core.design.tipForDay
import com.chargemaster.app.domain.engine.CapacityEstimator
import com.chargemaster.app.domain.engine.ChargeSpeed
import com.chargemaster.app.domain.engine.DischargeAnalyzer
import com.chargemaster.app.domain.engine.EstimateMethod
import com.chargemaster.app.domain.engine.SinceUnplugCalculator
import com.chargemaster.app.domain.engine.TemperatureZone
import com.chargemaster.app.domain.engine.TimeEstimate
import com.chargemaster.app.domain.engine.TimeEstimator
import com.chargemaster.app.domain.model.BatteryHealthStatus
import com.chargemaster.app.domain.model.BatterySnapshot
import com.chargemaster.app.domain.model.ChargingSession
import com.chargemaster.app.domain.model.ChargingStatus
import com.chargemaster.app.domain.model.DashboardCard
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
import kotlin.math.abs

data class DashboardState(
    val snapshot: BatterySnapshot? = null,
    val unit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val session: ChargingSession? = null,
    val etaFull: TimeEstimate? = null,
    val eta80: TimeEstimate? = null,
    val remaining: TimeEstimate? = null,
    val cards: Set<DashboardCard> = DashboardCard.entries.toSet(),
    val lastSession: ChargingSession? = null,
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
    private val lastClosed = sessions.observeRecent(3).map { list -> list.firstOrNull { !it.isOpen } }

    val state: StateFlow<DashboardState> = combine(
        battery.liveSnapshots, prefs.preferences, sessions.observeOpenSession(), dischargeRate, capacity,
    ) { s, p, session, rate, cap ->
        val charging = s.isPluggedIn
        DashboardState(
            cards = p.dashboardCards,
            snapshot = s,
            unit = p.temperatureUnit,
            session = session,
            etaFull = if (charging) TimeEstimator.timeToLevel(100, s.level, s.systemChargeTimeRemainingMs, session, s.currentNowMa, cap) else null,
            eta80 = if (charging) TimeEstimator.timeToLevel(80, s.level, null, session, s.currentNowMa, cap) else null,
            remaining = if (!charging) TimeEstimator.timeRemainingOnBattery(s.level, rate) else null,
        )
    }.combine(lastClosed) { st, last -> st.copy(lastSession = last) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState())
}

@Composable
fun DashboardScreen(
    onOpenCharging: () -> Unit,
    onOpenTemperature: () -> Unit,
    onOpenHealth: () -> Unit,
    onOpenTips: () -> Unit = {},
    vm: DashboardViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    DashboardContent(state, onOpenCharging, onOpenTemperature, onOpenHealth, onOpenTips)
}

@Composable
fun DashboardContent(
    state: DashboardState,
    onOpenCharging: () -> Unit = {},
    onOpenTemperature: () -> Unit = {},
    onOpenHealth: () -> Unit = {},
    onOpenTips: () -> Unit = {},
) {
    val s = state.snapshot
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
    LazyColumn(
        // Clip under the status bar and keep clear of side cutouts; the bottom bar is handled by the nav host.
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { DashboardHeader() }
        item { HeroCard(state) }
        if (s?.isPluggedIn == true) item {
            SessionInProgressCard(state, s, onOpenCharging)
        } else if (DashboardCard.LAST_CHARGE in state.cards) state.lastSession?.let { last ->
            item { LastChargeCard(last, s?.timestamp ?: last.lastSampleTime, onOpenCharging, currentLevel = s?.level) }
        }
        item {
            val unit = state.unit
            val c = state.cards
            val zone = TemperatureZone.of(s?.temperatureC)
            MetricGrid(
                buildList<@Composable (Modifier) -> Unit> {
                    if (DashboardCard.TEMPERATURE in c) add { m: Modifier ->
                        MetricCard(stringResource(R.string.metric_temperature), s?.temperatureC?.let { Fmt.temperature(it, unit) }, Icons.Outlined.Thermostat, m,
                            footnote = zone?.let { stringResource(it.labelRes()) }, tint = zone?.color() ?: MaterialTheme.colorScheme.primary)
                    }
                    if (DashboardCard.VOLTAGE in c) add { m: Modifier -> MetricCard(stringResource(R.string.metric_voltage), s?.voltageMv?.let(Fmt::voltage), Icons.Outlined.ElectricalServices, m) }
                    if (DashboardCard.POWER in c) add { m: Modifier ->
                        MetricCard(
                            stringResource(R.string.metric_power), s?.powerW?.let { Fmt.power(abs(it)) }, Icons.Outlined.Bolt, m,
                            kind = ValueKind.ESTIMATE, unavailableText = stringResource(R.string.not_supported),
                            footnote = s?.powerW?.let { stringResource(if (it >= 0) R.string.direction_in else R.string.direction_out) },
                        )
                    }
                    if (DashboardCard.CURRENT in c) add { m: Modifier ->
                        MetricCard(
                            stringResource(R.string.metric_current), s?.currentNowMa?.let(Fmt::current), Icons.Outlined.Speed, m,
                            unavailableText = stringResource(R.string.not_supported),
                            footnote = if (s?.currentCalibrationUncertain == true) stringResource(R.string.current_uncertain) else null,
                        )
                    }
                    if (DashboardCard.ANDROID_HEALTH in c) add { m: Modifier ->
                        MetricCard(stringResource(R.string.metric_android_health),
                            s?.health?.takeIf { it != BatteryHealthStatus.UNKNOWN }?.let { stringResource(it.labelRes()) },
                            Icons.Outlined.HealthAndSafety, m)
                    }
                    if (DashboardCard.THERMAL in c) add { m: Modifier ->
                        MetricCard(stringResource(R.string.metric_thermal),
                            s?.thermalStatus?.takeIf { it != ThermalStatus.UNSUPPORTED }?.let { stringResource(it.labelRes()) },
                            Icons.Outlined.DeviceThermostat, m, unavailableText = stringResource(R.string.not_supported))
                    }
                },
            )
        }
        if (DashboardCard.QUICK_LINKS in state.cards) item {
            AppCard(contentPadding = 8.dp) {
                Column(Modifier.padding(horizontal = 10.dp)) {
                    NavRow(stringResource(R.string.temperature_monitor), stringResource(R.string.quick_temperature_subtitle), Icons.Outlined.Thermostat, onOpenTemperature, tint = Tokens.Warning)
                    NavRow(stringResource(R.string.battery_health), stringResource(R.string.quick_health_subtitle), Icons.Outlined.HealthAndSafety, onOpenHealth)
                    NavRow(stringResource(R.string.tips_title), stringResource(R.string.quick_tips_subtitle), Icons.Outlined.Lightbulb, onOpenTips, tint = Tokens.Info)
                }
            }
        }
        item { TipOfDayCard(s?.timestamp ?: System.currentTimeMillis(), onOpenTips) }
        item { InfoBanner(stringResource(R.string.dashboard_disclaimer)) }
    }
    }
}

@Composable
private fun DashboardHeader() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(Icons.Outlined.ElectricBolt, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.tagline), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HeroCard(state: DashboardState) {
    val s = state.snapshot
    val statusLabel = stringResource((s?.status ?: ChargingStatus.UNKNOWN).labelRes())
    val levelText = s?.level?.let(Fmt::percent) ?: stringResource(R.string.unavailable)
    val charging = s?.isPluggedIn == true
    AppCard {
        Box(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp), contentAlignment = Alignment.Center) {
            BatteryRing(
                level = s?.level, charging = charging, size = 196.dp, stroke = 16.dp,
                modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "$levelText, $statusLabel" },
            ) {
                if (charging) Icon(Icons.Outlined.ElectricBolt, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 2.dp))
                Text(levelText, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(stringResource(R.string.battery_remaining), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            val src = s?.takeIf { it.isPluggedIn }?.let { " · " + stringResource(it.source.labelRes()) } ?: ""
            StatusPill("$statusLabel$src", if (charging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            if (charging) ChargeSpeed.classify(s?.powerW)?.let { StatusPill(stringResource(it.labelRes()), it.color(), icon = Icons.Outlined.Speed) }
        }
        if (DashboardCard.ESTIMATE in state.cards && s != null) {
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(14.dp))
            EstimateSection(state, s)
        }
    }
}

@Composable
private fun EstimateSection(state: DashboardState, s: BatterySnapshot) {
    if (s.isPluggedIn) {
        if (s.status == ChargingStatus.FULL) {
            Text(stringResource(R.string.status_full), style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            return
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            StatBlock(stringResource(R.string.eta_full), state.etaFull?.let { durationText(it.minutes * 60_000) } ?: stringResource(R.string.estimating),
                Modifier.weight(1f), center = true)
            VerticalDivider(Modifier.fillMaxHeight().padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
            StatBlock(stringResource(R.string.eta_80), state.eta80?.let { durationText(it.minutes * 60_000) } ?: stringResource(R.string.estimating),
                Modifier.weight(1f), center = true)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            KindDot(ValueKind.ESTIMATE)
            state.etaFull?.let {
                Text(" · " + methodText(it.method), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        StatBlock(stringResource(R.string.remaining_on_battery),
            state.remaining?.let { durationText(it.minutes * 60_000) } ?: stringResource(R.string.insufficient_history),
            Modifier.fillMaxWidth(), center = true)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { KindDot(ValueKind.ESTIMATE) }
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.remaining_explainer), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun SessionInProgressCard(state: DashboardState, s: BatterySnapshot, onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Outlined.ElectricBolt)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.session_in_progress), style = MaterialTheme.typography.titleSmall)
                state.session?.let {
                    Text(stringResource(R.string.session_in_progress_detail, durationText(it.durationMs(s.timestamp)),
                        it.startLevel?.let(Fmt::percent) ?: "—"), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        s.level?.let {
            Spacer(Modifier.height(12.dp))
            ProgressTrack(it / 100f, height = 8.dp)
        }
    }
}

@Composable
private fun TipOfDayCard(now: Long, onClick: () -> Unit) {
    val tip = tipForDay(now)
    AppCard(onClick = onClick, containerColor = Tokens.Info.copy(alpha = 0.08f)) {
        Row(verticalAlignment = Alignment.Top) {
            IconBadge(tip.icon, tint = Tokens.Info)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.tip_of_day), style = MaterialTheme.typography.labelMedium, color = Tokens.Info)
                Text(stringResource(tip.title), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(2.dp))
                Text(stringResource(tip.body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Summary of the most recent completed charge, shown while on battery. When the current level is
 * known it also shows usage since unplugging.
 */
@Composable
fun LastChargeCard(last: ChargingSession, now: Long, onClick: () -> Unit, currentLevel: Int? = null) {
    val since = SinceUnplugCalculator.compute(last, currentLevel, now)
    AppCard(onClick = onClick) {
        CardHeader(stringResource(if (since != null) R.string.since_unplug else R.string.last_charge), Icons.Outlined.BatteryStd) {
            KindDot(ValueKind.HISTORICAL)
        }
        if (since != null) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                StatBlock(stringResource(R.string.on_battery_for), durationText(since.elapsedMs), Modifier.weight(1f))
                VerticalDivider(Modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.outlineVariant)
                StatBlock(stringResource(R.string.used_since), Fmt.percent(since.usedPercent), Modifier.weight(1f).padding(start = 12.dp))
                VerticalDivider(Modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.outlineVariant)
                StatBlock(stringResource(R.string.drain_rate), since.ratePerHour?.let(Fmt::rate) ?: "—", Modifier.weight(1f).padding(start = 12.dp))
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.last_charge), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            "${last.startLevel?.let(Fmt::percent) ?: "—"} → ${last.endLevel?.let(Fmt::percent) ?: "—"}",
            style = if (since != null) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
        )
        Text(
            stringResource(R.string.last_charge_detail, durationText(last.durationMs()), durationText(now - (last.endTime ?: last.lastSampleTime))),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
