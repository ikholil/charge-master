package com.chargemaster.app.feature.charging

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chargemaster.app.core.common.Clock
import com.chargemaster.app.core.design.ChartPoint
import com.chargemaster.app.core.design.Period
import com.chargemaster.app.domain.engine.CapacityEstimator
import com.chargemaster.app.domain.engine.ChargingAnalyzer
import com.chargemaster.app.domain.engine.ChargingStats
import com.chargemaster.app.domain.engine.TimeEstimate
import com.chargemaster.app.domain.engine.TimeEstimator
import com.chargemaster.app.domain.model.BatteryReading
import com.chargemaster.app.domain.model.BatterySnapshot
import com.chargemaster.app.domain.model.ChargingSession
import com.chargemaster.app.domain.model.TemperatureUnit
import com.chargemaster.app.domain.repository.BatteryRepository
import com.chargemaster.app.domain.repository.HealthRepository
import com.chargemaster.app.domain.repository.PreferencesRepository
import com.chargemaster.app.domain.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LiveChargingState(
    val snapshot: BatterySnapshot? = null,
    val unit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val session: ChargingSession? = null,
    val etaFull: TimeEstimate? = null,
    val eta80: TimeEstimate? = null,
    /** In-memory live samples (x = seconds ago, negative). Not persisted. */
    val livePower: List<ChargingPoint> = emptyList(),
    val recent: List<ChargingSession> = emptyList(),
)

data class ChargingPoint(val timestamp: Long, val powerW: Float?, val currentMa: Float?)

@HiltViewModel
class ChargingViewModel @Inject constructor(
    battery: BatteryRepository,
    sessions: SessionRepository,
    health: HealthRepository,
    prefs: PreferencesRepository,
) : ViewModel() {
    private val buffer = MutableStateFlow<List<ChargingPoint>>(emptyList())
    private val live = battery.liveSnapshots.onEach { s ->
        val cutoff = s.timestamp - LIVE_WINDOW_MS
        buffer.value = (buffer.value + ChargingPoint(s.timestamp, s.powerW, s.currentNowMa)).filter { it.timestamp >= cutoff }
    }
    private val capacity = health.observeSnapshots().map { CapacityEstimator.combine(it).first }

    val state: StateFlow<LiveChargingState> = combine(
        live, prefs.preferences, sessions.observeOpenSession(), sessions.observeRecent(5), capacity,
    ) { s, p, open, recent, cap ->
        LiveChargingState(
            snapshot = s, unit = p.temperatureUnit, session = open,
            etaFull = if (s.isPluggedIn) TimeEstimator.timeToLevel(100, s.level, s.systemChargeTimeRemainingMs, open, s.currentNowMa, cap) else null,
            eta80 = if (s.isPluggedIn) TimeEstimator.timeToLevel(80, s.level, null, open, s.currentNowMa, cap) else null,
            recent = recent,
        )
    }.combine(buffer) { st, b -> st.copy(livePower = b) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LiveChargingState())

    companion object { const val LIVE_WINDOW_MS = 5 * 60_000L }
}

data class HistoryState(
    val period: Period = Period.D7,
    val sessions: List<ChargingSession> = emptyList(),
    val stats: ChargingStats? = null,
    /** Charging power vs battery level from stored readings. */
    val powerVsLevel: List<ChartPoint> = emptyList(),
    /** Session average temperature vs average power. */
    val tempVsPower: List<ChartPoint> = emptyList(),
    val unit: TemperatureUnit = TemperatureUnit.CELSIUS,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChargingHistoryViewModel @Inject constructor(
    private val battery: BatteryRepository,
    private val sessions: SessionRepository,
    prefs: PreferencesRepository,
    private val clock: Clock,
) : ViewModel() {
    val period = MutableStateFlow(Period.D7)

    val state: StateFlow<HistoryState> = period.flatMapLatest { p ->
        val since = clock.now() - p.ms
        combine(sessions.observeSince(since), battery.observeReadingsSince(since), prefs.preferences) { list, readings, prefs ->
            HistoryState(
                period = p,
                sessions = list,
                stats = ChargingAnalyzer.analyze(list, clock.now(), p.days),
                powerVsLevel = readings.filter { it.isPluggedIn }.mapNotNull { r ->
                    val w = r.powerW?.takeIf { it > 0 } ?: return@mapNotNull null
                    r.level?.let { ChartPoint(it.toFloat(), w) }
                },
                tempVsPower = list.mapNotNull { s ->
                    val t = s.averageTempC ?: return@mapNotNull null
                    s.averagePowerW?.let { ChartPoint(t, it) }
                },
                unit = prefs.temperatureUnit,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryState())
}

data class SessionDetailState(
    val session: ChargingSession? = null,
    /** Average power of the user's other sessions on the same source (≥3 needed). */
    val typicalPowerW: Float? = null,
    val typicalPercentPerHour: Float? = null,
    val readings: List<BatteryReading> = emptyList(),
    val unit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val capacity: CapacityEstimator.Estimate? = null,
    val loaded: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val battery: BatteryRepository,
    private val sessions: SessionRepository,
    prefs: PreferencesRepository,
) : ViewModel() {
    private val id: Long = checkNotNull(savedState["id"])

    val state: StateFlow<SessionDetailState> = sessions.observeById(id).flatMapLatest { s ->
        if (s == null) flowOf(SessionDetailState(loaded = true))
        else combine(
            battery.observeReadingsBetween(s.startTime, s.endTime ?: s.lastSampleTime), prefs.preferences, sessions.observeRecent(50),
        ) { r, p, recent ->
            val peers = recent.filter { it.id != s.id && !it.isOpen && it.source == s.source }
            val peerPower = peers.mapNotNull { it.averagePowerW }
            val peerRate = peers.filter { (it.levelGain ?: 0) >= 10 }.mapNotNull { it.percentPerHour }
            SessionDetailState(
                session = s,
                typicalPowerW = peerPower.takeIf { it.size >= 3 }?.average()?.toFloat(),
                typicalPercentPerHour = peerRate.takeIf { it.size >= 3 }?.average()?.toFloat(),
                readings = r, unit = p.temperatureUnit,
                capacity = CapacityEstimator.estimateFromReadings(r), loaded = true,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionDetailState())

    fun delete(onDone: () -> Unit) = viewModelScope.launch { sessions.delete(id); onDone() }
}
