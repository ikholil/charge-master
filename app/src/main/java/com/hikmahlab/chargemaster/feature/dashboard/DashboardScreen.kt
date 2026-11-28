package com.hikmahlab.chargemaster.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.DeviceThermostat
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hikmahlab.chargemaster.R
import com.hikmahlab.chargemaster.core.common.Clock
import com.hikmahlab.chargemaster.core.common.Fmt
import com.hikmahlab.chargemaster.core.design.AppCard
import com.hikmahlab.chargemaster.core.design.CellGauge
import com.hikmahlab.chargemaster.core.design.IconBadge
import com.hikmahlab.chargemaster.core.design.InfoBanner
import com.hikmahlab.chargemaster.core.design.LocalPastels
import androidx.compose.material.icons.outlined.ElectricBolt
import com.hikmahlab.chargemaster.core.design.KindDot
import com.hikmahlab.chargemaster.core.design.MetricCard
import com.hikmahlab.chargemaster.core.design.MetricGrid
import com.hikmahlab.chargemaster.core.design.NavRow
import com.hikmahlab.chargemaster.core.design.NumberWithUnit
import com.hikmahlab.chargemaster.core.design.Overline
import com.hikmahlab.chargemaster.core.design.ScreenArrangement
import com.hikmahlab.chargemaster.core.design.SectionHeader
import com.hikmahlab.chargemaster.core.design.SectionTitle
import com.hikmahlab.chargemaster.core.design.SerifFamily
import com.hikmahlab.chargemaster.core.design.StatBlock
import com.hikmahlab.chargemaster.core.design.StatusPill
import com.hikmahlab.chargemaster.core.design.Tokens
import com.hikmahlab.chargemaster.core.design.VRule
import com.hikmahlab.chargemaster.core.design.ValueKind
import com.hikmahlab.chargemaster.core.design.color
import com.hikmahlab.chargemaster.core.design.durationText
import com.hikmahlab.chargemaster.core.design.labelRes
import com.hikmahlab.chargemaster.core.design.tipForDay
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ElectricalServices
import androidx.compose.material.icons.outlined.Speed
import com.hikmahlab.chargemaster.domain.engine.CapacityEstimator
import com.hikmahlab.chargemaster.domain.engine.ChargeSpeed
import com.hikmahlab.chargemaster.domain.engine.DischargeAnalyzer
import com.hikmahlab.chargemaster.domain.engine.EstimateMethod
import com.hikmahlab.chargemaster.domain.engine.SinceUnplugCalculator
import com.hikmahlab.chargemaster.domain.engine.TemperatureZone
import com.hikmahlab.chargemaster.domain.engine.TimeEstimate
import com.hikmahlab.chargemaster.domain.engine.TimeEstimator
import com.hikmahlab.chargemaster.domain.model.BatteryHealthStatus
import com.hikmahlab.chargemaster.domain.model.BatterySnapshot
import com.hikmahlab.chargemaster.domain.model.ChargingSession
import com.hikmahlab.chargemaster.domain.model.ChargingStatus
import com.hikmahlab.chargemaster.domain.model.DashboardCard
import com.hikmahlab.chargemaster.domain.model.TemperatureUnit
import com.hikmahlab.chargemaster.domain.model.ThermalStatus
import com.hikmahlab.chargemaster.domain.repository.BatteryRepository
import com.hikmahlab.chargemaster.domain.repository.HealthRepository
import com.hikmahlab.chargemaster.domain.repository.PreferencesRepository
import com.hikmahlab.chargemaster.domain.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
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
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = ScreenArrangement,
    ) {
        item { DashboardHeader() }
        item { Hero(state) }
        if (DashboardCard.ESTIMATE in state.cards && s != null) item { EstimateSection(state, s) }
        if (s?.isPluggedIn == true) item {
            SessionInProgress(state, s, onOpenCharging)
        } else if (DashboardCard.LAST_CHARGE in state.cards) state.lastSession?.let { last ->
            item { LastChargeCard(last, s?.timestamp ?: last.lastSampleTime, onOpenCharging, currentLevel = s?.level) }
        }
        item { Readings(state) }
        if (DashboardCard.QUICK_LINKS in state.cards) item {
            AppCard {
                SectionHeader(stringResource(R.string.explore))
                val p = LocalPastels.current
                NavRow(stringResource(R.string.temperature_monitor), stringResource(R.string.quick_temperature_subtitle), Icons.Outlined.Thermostat, onOpenTemperature, tint = p.peach.content)
                NavRow(stringResource(R.string.battery_health), stringResource(R.string.quick_health_subtitle), Icons.Outlined.HealthAndSafety, onOpenHealth, tint = p.mint.content)
                NavRow(stringResource(R.string.tips_title), stringResource(R.string.quick_tips_subtitle), Icons.Outlined.Lightbulb, onOpenTips, tint = p.butter.content)
            }
        }
        item { TipOfDay(s?.timestamp ?: System.currentTimeMillis(), onOpenTips) }
        item { InfoBanner(stringResource(R.string.dashboard_disclaimer)) }
    }
    }
}

@Composable
private fun DashboardHeader() {
    val date = remember {
        LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()))
    }
    val mint = LocalPastels.current.mint
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Overline(date)
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        }
        IconBadge(Icons.Outlined.ElectricBolt, tint = mint.content, container = mint.container, size = 48.dp)
    }
}

@Composable
private fun Hero(state: DashboardState) {
    val s = state.snapshot
    val statusLabel = stringResource((s?.status ?: ChargingStatus.UNKNOWN).labelRes())
    val levelText = s?.level?.let(Fmt::percent) ?: stringResource(R.string.unavailable)
    val charging = s?.isPluggedIn == true
    val speed = if (charging) ChargeSpeed.classify(s?.powerW) else null
    val mint = LocalPastels.current.mint
    AppCard(containerColor = mint.container, contentPadding = 22.dp) {
        Row(
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "$levelText, $statusLabel" },
            verticalAlignment = Alignment.Bottom,
        ) {
            if (s?.level != null) NumberWithUnit(levelText, style = MaterialTheme.typography.displayLarge, unitScale = 0.42f, modifier = Modifier.weight(1f), unitColor = mint.content)
            else Text(levelText, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.outline, modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 14.dp)) {
                val src = s?.takeIf { it.isPluggedIn }?.let { stringResource(it.source.labelRes()) }
                StatusPill(statusLabel, if (charging) mint.content else MaterialTheme.colorScheme.onSurfaceVariant)
                if (src != null) Text(src, style = MaterialTheme.typography.bodySmall, color = mint.content, modifier = Modifier.padding(top = 4.dp, end = 6.dp))
                speed?.let {
                    Spacer(Modifier.height(6.dp))
                    StatusPill(stringResource(it.labelRes()), it.color())
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        CellGauge(s?.level, charging)
    }
}

@Composable
private fun EstimateSection(state: DashboardState, s: BatterySnapshot) {
    AppCard {
        SectionHeader(stringResource(R.string.estimates))
        if (s.isPluggedIn) {
            if (s.status == ChargingStatus.FULL) {
                Text(stringResource(R.string.status_full), style = MaterialTheme.typography.titleLarge)
                return@AppCard
            }
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                StatBlock(stringResource(R.string.eta_80), state.eta80?.let { durationText(it.minutes * 60_000) } ?: stringResource(R.string.estimating), Modifier.weight(1f))
                VRule(Modifier.padding(horizontal = 16.dp))
                StatBlock(stringResource(R.string.eta_full), state.etaFull?.let { durationText(it.minutes * 60_000) } ?: stringResource(R.string.estimating), Modifier.weight(1f))
            }
            state.etaFull?.let {
                Spacer(Modifier.height(12.dp))
                Text(methodText(it.method), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            StatBlock(stringResource(R.string.remaining_on_battery),
                state.remaining?.let { durationText(it.minutes * 60_000) } ?: stringResource(R.string.insufficient_history))
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.remaining_explainer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Readings(state: DashboardState) {
    val s = state.snapshot
    val unit = state.unit
    val c = state.cards
    val zone = TemperatureZone.of(s?.temperatureC)
    val cells = buildList<@Composable (Modifier) -> Unit> {
        if (DashboardCard.TEMPERATURE in c) add { m: Modifier ->
            MetricCard(stringResource(R.string.metric_temperature), s?.temperatureC?.let { Fmt.temperature(it, unit) }, Icons.Outlined.Thermostat, m,
                footnote = zone?.let { stringResource(it.labelRes()) }, tint = zone?.color() ?: MaterialTheme.colorScheme.outline)
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
    }
    if (cells.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.readings))
        Spacer(Modifier.height(6.dp))
        MetricGrid(cells)
    }
}

@Composable
private fun SessionInProgress(state: DashboardState, s: BatterySnapshot, onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Overline(stringResource(R.string.session_in_progress), color = MaterialTheme.colorScheme.primary)
                state.session?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.session_in_progress_detail, durationText(it.durationMs(s.timestamp)),
                        it.startLevel?.let(Fmt::percent) ?: "—"), style = MaterialTheme.typography.bodyLarge)
                }
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun TipOfDay(now: Long, onClick: () -> Unit) {
    val tip = tipForDay(now)
    val butter = LocalPastels.current.butter
    AppCard(onClick = onClick, containerColor = butter.container, contentPadding = 20.dp) {
        Row(verticalAlignment = Alignment.Top) {
            IconBadge(tip.icon, tint = butter.content, container = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), size = 44.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.tip_of_day), style = MaterialTheme.typography.labelMedium, color = butter.content)
                Spacer(Modifier.height(2.dp))
                Text(stringResource(tip.title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(tip.body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
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
        SectionHeader(stringResource(if (since != null) R.string.since_unplug else R.string.last_charge)) { KindDot(ValueKind.HISTORICAL) }
        if (since != null) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                StatBlock(stringResource(R.string.on_battery_for), durationText(since.elapsedMs), Modifier.weight(1.3f))
                VRule(Modifier.padding(horizontal = 14.dp))
                StatBlock(stringResource(R.string.used_since), Fmt.percent(since.usedPercent), Modifier.weight(1f))
                VRule(Modifier.padding(horizontal = 14.dp))
                StatBlock(stringResource(R.string.drain_rate), since.ratePerHour?.let(Fmt::rate) ?: "—", Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
        }
        Row(verticalAlignment = Alignment.Bottom) {
            if (since != null) {
                Text(stringResource(R.string.last_charge) + "  ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "${last.startLevel?.let(Fmt::percent) ?: "—"} → ${last.endLevel?.let(Fmt::percent) ?: "—"}",
                style = if (since != null) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleLarge,
            )
        }
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
