package com.chargemaster.app.domain.repository

import com.chargemaster.app.domain.engine.AlertState
import com.chargemaster.app.domain.model.AlertPreferences
import com.chargemaster.app.domain.model.BatteryHealthSnapshot
import com.chargemaster.app.domain.model.BatteryReading
import com.chargemaster.app.domain.model.BatterySnapshot
import com.chargemaster.app.domain.model.CalibrationMode
import com.chargemaster.app.domain.model.ChargingSession
import com.chargemaster.app.domain.model.CurrentCalibration
import com.chargemaster.app.domain.model.DashboardCard
import com.chargemaster.app.domain.model.MonitoringMode
import com.chargemaster.app.domain.model.TemperatureUnit
import com.chargemaster.app.domain.model.ThemeMode
import com.chargemaster.app.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow

interface BatteryRepository {
    /** Hot, lifecycle-bound stream; sampling stops when there are no collectors. */
    val liveSnapshots: Flow<BatterySnapshot>
    /** One-shot read from the sticky battery broadcast + BatteryManager properties. */
    suspend fun readSnapshot(): BatterySnapshot

    fun observeReadingsSince(since: Long): Flow<List<BatteryReading>>
    fun observeReadingsBetween(from: Long, to: Long): Flow<List<BatteryReading>>
    suspend fun readingsBetween(from: Long, to: Long): List<BatteryReading>
    suspend fun latestReading(): BatteryReading?
    suspend fun insertReading(reading: BatteryReading)
    fun observeReadingCount(): Flow<Int>
    fun observeOldestReading(): Flow<Long?>
    suspend fun pruneReadingsOlderThan(before: Long): Int
    suspend fun clearReadings()
}

interface SessionRepository {
    suspend fun openSession(): ChargingSession?
    fun observeOpenSession(): Flow<ChargingSession?>
    suspend fun lastClosedSession(): ChargingSession?
    fun observeRecent(limit: Int): Flow<List<ChargingSession>>
    fun observeSince(since: Long): Flow<List<ChargingSession>>
    suspend fun recent(limit: Int): List<ChargingSession>
    fun observeById(id: Long): Flow<ChargingSession?>
    /** Inserts when id == 0, updates otherwise; returns the id. */
    suspend fun save(session: ChargingSession): Long
    suspend fun delete(id: Long)
    suspend fun pruneOlderThan(before: Long): Int
    suspend fun clear()
}

interface HealthRepository {
    fun observeSnapshots(): Flow<List<BatteryHealthSnapshot>>
    suspend fun insert(snapshot: BatteryHealthSnapshot)
    suspend fun clear()
}

interface PreferencesRepository {
    val preferences: Flow<UserPreferences>
    suspend fun current(): UserPreferences
    suspend fun setOnboardingComplete(done: Boolean)
    suspend fun setTemperatureUnit(unit: TemperatureUnit)
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun setHighContrast(enabled: Boolean)
    suspend fun setReduceMotion(enabled: Boolean)
    suspend fun setMonitoringMode(mode: MonitoringMode)
    suspend fun setBackgroundMonitoring(enabled: Boolean)
    suspend fun setRetentionDays(days: Int)
    suspend fun setDesignCapacity(mah: Int?)
    suspend fun setCurrentUnitMode(mode: CalibrationMode)
    suspend fun setCurrentSignMode(mode: CalibrationMode)
    suspend fun updateAlerts(transform: (AlertPreferences) -> AlertPreferences)
    suspend fun setDashboardCards(cards: Set<DashboardCard>)

    suspend fun calibration(): CurrentCalibration
    suspend fun setCalibration(calibration: CurrentCalibration)
    suspend fun alertState(): AlertState
    suspend fun setAlertState(state: AlertState)
}
