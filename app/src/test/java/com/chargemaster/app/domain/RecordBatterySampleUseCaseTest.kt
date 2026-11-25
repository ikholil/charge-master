package com.chargemaster.app.domain

import com.chargemaster.app.domain.engine.AlertState
import com.chargemaster.app.domain.engine.BatteryAlert
import com.chargemaster.app.domain.model.AlertPreferences
import com.chargemaster.app.domain.model.BatteryHealthSnapshot
import com.chargemaster.app.domain.model.BatteryReading
import com.chargemaster.app.domain.model.BatterySnapshot
import com.chargemaster.app.domain.model.CalibrationMode
import com.chargemaster.app.domain.model.ChargingSession
import com.chargemaster.app.domain.model.ChargingSource
import com.chargemaster.app.domain.model.CurrentCalibration
import com.chargemaster.app.domain.model.MonitoringMode
import com.chargemaster.app.domain.model.SessionEndReason
import com.chargemaster.app.domain.model.TemperatureUnit
import com.chargemaster.app.domain.model.ThemeMode
import com.chargemaster.app.domain.model.UserPreferences
import com.chargemaster.app.domain.repository.BatteryRepository
import com.chargemaster.app.domain.repository.HealthRepository
import com.chargemaster.app.domain.repository.PreferencesRepository
import com.chargemaster.app.domain.repository.SessionRepository
import com.chargemaster.app.domain.usecase.RecordBatterySampleUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeBattery : BatteryRepository {
    val readings = mutableListOf<BatteryReading>()
    var next: BatterySnapshot? = null
    override val liveSnapshots: Flow<BatterySnapshot> = emptyFlow()
    override suspend fun readSnapshot() = next!!
    override fun observeReadingsSince(since: Long) = MutableStateFlow(readings.filter { it.timestamp >= since })
    override fun observeReadingsBetween(from: Long, to: Long) = MutableStateFlow(readings.filter { it.timestamp in from..to })
    override suspend fun readingsBetween(from: Long, to: Long) = readings.filter { it.timestamp in from..to }
    override suspend fun latestReading() = readings.maxByOrNull { it.timestamp }
    override suspend fun insertReading(reading: BatteryReading) { readings += reading.copy(id = readings.size + 1L) }
    override fun observeReadingCount() = MutableStateFlow(readings.size)
    override fun observeOldestReading() = MutableStateFlow(readings.minOfOrNull { it.timestamp })
    override suspend fun pruneReadingsOlderThan(before: Long): Int { val n = readings.count { it.timestamp < before }; readings.removeAll { it.timestamp < before }; return n }
    override suspend fun clearReadings() = readings.clear()
}

class FakeSessions : SessionRepository {
    val all = linkedMapOf<Long, ChargingSession>()
    private var id = 0L
    override suspend fun openSession() = all.values.lastOrNull { it.isOpen }
    override fun observeOpenSession() = MutableStateFlow(all.values.lastOrNull { it.isOpen })
    override suspend fun lastClosedSession() = all.values.filter { !it.isOpen }.maxByOrNull { it.endTime!! }
    override fun observeRecent(limit: Int) = MutableStateFlow(all.values.toList().takeLast(limit))
    override fun observeSince(since: Long) = MutableStateFlow(all.values.filter { it.startTime >= since })
    override suspend fun recent(limit: Int) = all.values.sortedByDescending { it.startTime }.take(limit)
    override fun observeById(id: Long) = MutableStateFlow(all[id])
    override suspend fun save(session: ChargingSession): Long {
        val sid = if (session.id == 0L) ++id else session.id
        all[sid] = session.copy(id = sid); return sid
    }
    override suspend fun delete(id: Long) { all.remove(id) }
    override suspend fun pruneOlderThan(before: Long) = 0
    override suspend fun clear() = all.clear()
}

class FakeHealth : HealthRepository {
    val list = mutableListOf<BatteryHealthSnapshot>()
    override fun observeSnapshots() = MutableStateFlow(list.toList())
    override suspend fun insert(snapshot: BatteryHealthSnapshot) { list += snapshot }
    override suspend fun clear() = list.clear()
}

class FakePrefs(initial: UserPreferences = UserPreferences()) : PreferencesRepository {
    val state = MutableStateFlow(initial)
    var cal = CurrentCalibration()
    var alert = AlertState()
    override val preferences = state
    override suspend fun current() = state.value
    override suspend fun setOnboardingComplete(done: Boolean) { state.value = state.value.copy(onboardingComplete = done) }
    override suspend fun setTemperatureUnit(unit: TemperatureUnit) { state.value = state.value.copy(temperatureUnit = unit) }
    override suspend fun setThemeMode(mode: ThemeMode) { state.value = state.value.copy(themeMode = mode) }
    override suspend fun setDynamicColor(enabled: Boolean) {}
    override suspend fun setHighContrast(enabled: Boolean) {}
    override suspend fun setReduceMotion(enabled: Boolean) {}
    override suspend fun setMonitoringMode(mode: MonitoringMode) { state.value = state.value.copy(monitoringMode = mode) }
    override suspend fun setBackgroundMonitoring(enabled: Boolean) {}
    override suspend fun setRetentionDays(days: Int) { state.value = state.value.copy(retentionDays = days) }
    override suspend fun setDesignCapacity(mah: Int?) { state.value = state.value.copy(designCapacityMah = mah) }
    override suspend fun setCurrentUnitMode(mode: CalibrationMode) {}
    override suspend fun setCurrentSignMode(mode: CalibrationMode) {}
    override suspend fun updateAlerts(transform: (AlertPreferences) -> AlertPreferences) { state.value = state.value.copy(alerts = transform(state.value.alerts)) }
    override suspend fun setDashboardCards(cards: Set<com.chargemaster.app.domain.model.DashboardCard>) { state.value = state.value.copy(dashboardCards = cards) }
    override suspend fun calibration() = cal
    override suspend fun setCalibration(calibration: CurrentCalibration) { cal = calibration }
    override suspend fun alertState() = alert
    override suspend fun setAlertState(state: AlertState) { alert = state }
}

class RecordBatterySampleUseCaseTest {
    private val battery = FakeBattery()
    private val sessions = FakeSessions()
    private val health = FakeHealth()
    private val prefs = FakePrefs()
    private val alerts = mutableListOf<BatteryAlert>()
    private var widgetRefreshes = 0
    private val record = RecordBatterySampleUseCase(battery, sessions, health, prefs, { alerts += it }, { widgetRefreshes++ })

    @Test fun `full charge cycle creates a session, a capacity estimate and threshold alert`() = runTest {
        record(snapshot(0, 20))
        var t = 0L
        for (level in 20..90 step 5) {
            t += 10 * MIN
            record(snapshot(t, level, ChargingSource.AC, current = 2500f, counter = 100f + 4300f * level / 100f))
        }
        record(snapshot(t + 5 * MIN, 90))

        val s = sessions.all.values.single()
        assertFalse(s.isOpen)
        assertTrue(s.isComplete)
        assertEquals(20, s.startLevel)
        assertEquals(90, s.endLevel)
        assertEquals(SessionEndReason.UNPLUGGED, s.endReason)
        assertEquals(17, battery.readings.size)
        val h = health.list.single()
        assertEquals(4300f, h.estimatedCapacityMah!!, 1f)
        assertTrue(alerts.any { it is BatteryAlert.ThresholdReached && it.threshold == 80 })
        assertTrue(widgetRefreshes >= 2)
    }

    @Test fun `fast samples are throttled for persistence`() = runTest {
        record(snapshot(0, 50), minPersistIntervalMs = 60_000)
        record(snapshot(1_000, 50), minPersistIntervalMs = 60_000)
        record(snapshot(2_000, 50), minPersistIntervalMs = 60_000)
        assertEquals(1, battery.readings.size)
        // A plug change is always persisted immediately.
        record(snapshot(3_000, 50, ChargingSource.USB), minPersistIntervalMs = 60_000)
        assertEquals(2, battery.readings.size)
        assertNotNull(sessions.openSession())
    }

    @Test fun `restart closes the open session as incomplete`() = runTest {
        record(snapshot(0, 30))
        record(snapshot(MIN, 30, ChargingSource.AC))
        record(snapshot(10 * MIN, 35, ChargingSource.AC))
        record.closeSessionAfterRestart()
        val s = sessions.all.values.single()
        assertEquals(SessionEndReason.DEVICE_RESTART, s.endReason)
        assertEquals(10 * MIN, s.endTime)
        assertFalse(s.isComplete)
    }

    @Test fun `alert state persists across calls to avoid duplicates`() = runTest {
        record(snapshot(0, 21))
        record(snapshot(MIN, 20))
        record(snapshot(2 * MIN, 19))
        assertEquals(1, alerts.count { it is BatteryAlert.LowBattery })
        assertTrue(prefs.alert.lowFired)
    }
}
