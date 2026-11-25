package com.chargemaster.app.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.chargemaster.app.data.local.enumOrDefault
import com.chargemaster.app.domain.engine.AlertState
import com.chargemaster.app.domain.model.AlertPreferences
import com.chargemaster.app.domain.model.CalibrationMode
import com.chargemaster.app.domain.model.CurrentCalibration
import com.chargemaster.app.domain.model.CurrentSign
import com.chargemaster.app.domain.model.CurrentUnit
import com.chargemaster.app.domain.model.DashboardCard
import com.chargemaster.app.domain.model.MonitoringMode
import com.chargemaster.app.domain.model.TemperatureUnit
import com.chargemaster.app.domain.model.ThemeMode
import com.chargemaster.app.domain.model.UserPreferences
import com.chargemaster.app.domain.repository.PreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "charge_master_prefs")

/**
 * Device-specific learned state (current calibration, alert de-dup). Kept separate and excluded
 * from backup: restoring another phone's calibration would misread this phone's sensors.
 */
private val Context.deviceStateStore: DataStore<Preferences> by preferencesDataStore(name = DEVICE_STATE_STORE)

const val DEVICE_STATE_STORE = "charge_master_device_state"

@Singleton
class DataStorePreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : PreferencesRepository {

    private object K {
        val onboarding = booleanPreferencesKey("onboarding_complete")
        val tempUnit = stringPreferencesKey("temperature_unit")
        val theme = stringPreferencesKey("theme_mode")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val contrast = booleanPreferencesKey("high_contrast")
        val reduceMotion = booleanPreferencesKey("reduce_motion")
        val mode = stringPreferencesKey("monitoring_mode")
        val background = booleanPreferencesKey("background_monitoring")
        val retention = intPreferencesKey("retention_days")
        val design = intPreferencesKey("design_capacity_mah")
        val unitMode = stringPreferencesKey("current_unit_mode")
        val signMode = stringPreferencesKey("current_sign_mode")
        val cards = stringSetPreferencesKey("dashboard_cards")
        // alerts
        val aLow = booleanPreferencesKey("alert_low")
        val aLowT = intPreferencesKey("alert_low_threshold")
        val a80 = booleanPreferencesKey("alert_80")
        val a80T = intPreferencesKey("alert_80_threshold")
        val a90 = booleanPreferencesKey("alert_90")
        val a90T = intPreferencesKey("alert_90_threshold")
        val aFull = booleanPreferencesKey("alert_full")
        val aStart = booleanPreferencesKey("alert_started")
        val aStop = booleanPreferencesKey("alert_stopped")
        val aComplete = booleanPreferencesKey("alert_complete")
        val aTemp = booleanPreferencesKey("alert_temp")
        val aTempT = floatPreferencesKey("alert_temp_threshold")
        val aLong = booleanPreferencesKey("alert_long")
        val aLongH = intPreferencesKey("alert_long_hours")
        val aUnusual = booleanPreferencesKey("alert_unusual")
        // calibration
        val calUnit = stringPreferencesKey("cal_unit")
        val calSign = stringPreferencesKey("cal_sign")
        // alert state
        val sWasPlugged = stringPreferencesKey("as_was_plugged")
        val sStartLevel = intPreferencesKey("as_start_level")
        val sLow = booleanPreferencesKey("as_low")
        val sThresholds = stringSetPreferencesKey("as_thresholds")
        val sComplete = booleanPreferencesKey("as_complete")
        val sTemp = booleanPreferencesKey("as_temp")
        val sLong = booleanPreferencesKey("as_long")
        val sUnusual = booleanPreferencesKey("as_unusual")
    }

    private val data: Flow<Preferences> = context.dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    private val deviceData: Flow<Preferences> = context.deviceStateStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    private suspend fun editDevice(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.deviceStateStore.edit { block(it) }
    }

    override val preferences: Flow<UserPreferences> = data.map { p ->
        val d = AlertPreferences()
        UserPreferences(
            onboardingComplete = p[K.onboarding] ?: false,
            temperatureUnit = enumOrDefault(p[K.tempUnit] ?: "", TemperatureUnit.CELSIUS),
            themeMode = enumOrDefault(p[K.theme] ?: "", ThemeMode.SYSTEM),
            dynamicColor = p[K.dynamic] ?: false,
            highContrast = p[K.contrast] ?: false,
            reduceMotion = p[K.reduceMotion] ?: false,
            monitoringMode = enumOrDefault(p[K.mode] ?: "", MonitoringMode.BALANCED),
            backgroundMonitoring = p[K.background] ?: true,
            retentionDays = p[K.retention] ?: 30,
            designCapacityMah = p[K.design]?.takeIf { it > 0 },
            currentUnitMode = enumOrDefault(p[K.unitMode] ?: "", CalibrationMode.AUTO),
            currentSignMode = enumOrDefault(p[K.signMode] ?: "", CalibrationMode.AUTO),
            dashboardCards = p[K.cards]?.mapNotNull { n -> runCatching { DashboardCard.valueOf(n) }.getOrNull() }?.toSet()
                ?: DashboardCard.entries.toSet(),
            alerts = AlertPreferences(
                lowBatteryEnabled = p[K.aLow] ?: d.lowBatteryEnabled,
                lowBatteryThreshold = p[K.aLowT] ?: d.lowBatteryThreshold,
                threshold80Enabled = p[K.a80] ?: d.threshold80Enabled,
                threshold80 = p[K.a80T] ?: d.threshold80,
                threshold90Enabled = p[K.a90] ?: d.threshold90Enabled,
                threshold90 = p[K.a90T] ?: d.threshold90,
                fullEnabled = p[K.aFull] ?: d.fullEnabled,
                chargingStartedEnabled = p[K.aStart] ?: d.chargingStartedEnabled,
                chargingStoppedEnabled = p[K.aStop] ?: d.chargingStoppedEnabled,
                chargingCompleteEnabled = p[K.aComplete] ?: d.chargingCompleteEnabled,
                highTempEnabled = p[K.aTemp] ?: d.highTempEnabled,
                highTempThresholdC = p[K.aTempT] ?: d.highTempThresholdC,
                longChargeEnabled = p[K.aLong] ?: d.longChargeEnabled,
                longChargeHours = p[K.aLongH] ?: d.longChargeHours,
                unusualSpeedEnabled = p[K.aUnusual] ?: d.unusualSpeedEnabled,
            ),
        )
    }

    override suspend fun current(): UserPreferences = preferences.first()

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }

    override suspend fun setOnboardingComplete(done: Boolean) = edit { it[K.onboarding] = done }
    override suspend fun setTemperatureUnit(unit: TemperatureUnit) = edit { it[K.tempUnit] = unit.name }
    override suspend fun setThemeMode(mode: ThemeMode) = edit { it[K.theme] = mode.name }
    override suspend fun setDynamicColor(enabled: Boolean) = edit { it[K.dynamic] = enabled }
    override suspend fun setHighContrast(enabled: Boolean) = edit { it[K.contrast] = enabled }
    override suspend fun setReduceMotion(enabled: Boolean) = edit { it[K.reduceMotion] = enabled }
    override suspend fun setMonitoringMode(mode: MonitoringMode) = edit { it[K.mode] = mode.name }
    override suspend fun setBackgroundMonitoring(enabled: Boolean) = edit { it[K.background] = enabled }
    override suspend fun setRetentionDays(days: Int) = edit { it[K.retention] = days }
    override suspend fun setDesignCapacity(mah: Int?) = edit {
        if (mah == null || mah <= 0) it.remove(K.design) else it[K.design] = mah
    }
    override suspend fun setCurrentUnitMode(mode: CalibrationMode) = edit { it[K.unitMode] = mode.name }
    override suspend fun setCurrentSignMode(mode: CalibrationMode) = edit { it[K.signMode] = mode.name }

    override suspend fun updateAlerts(transform: (AlertPreferences) -> AlertPreferences) {
        val a = transform(current().alerts)
        edit {
            it[K.aLow] = a.lowBatteryEnabled; it[K.aLowT] = a.lowBatteryThreshold.coerceIn(5, 50)
            it[K.a80] = a.threshold80Enabled; it[K.a80T] = a.threshold80.coerceIn(50, 95)
            it[K.a90] = a.threshold90Enabled; it[K.a90T] = a.threshold90.coerceIn(50, 99)
            it[K.aFull] = a.fullEnabled
            it[K.aStart] = a.chargingStartedEnabled; it[K.aStop] = a.chargingStoppedEnabled
            it[K.aComplete] = a.chargingCompleteEnabled
            it[K.aTemp] = a.highTempEnabled; it[K.aTempT] = a.highTempThresholdC.coerceIn(30f, 60f)
            it[K.aLong] = a.longChargeEnabled; it[K.aLongH] = a.longChargeHours.coerceIn(1, 24)
            it[K.aUnusual] = a.unusualSpeedEnabled
        }
    }

    override suspend fun setDashboardCards(cards: Set<DashboardCard>) = edit { it[K.cards] = cards.map { c -> c.name }.toSet() }

    override suspend fun calibration(): CurrentCalibration {
        val p = deviceData.first()
        return CurrentCalibration(
            learnedUnit = p[K.calUnit]?.let { runCatching { CurrentUnit.valueOf(it) }.getOrNull() },
            learnedSign = p[K.calSign]?.let { runCatching { CurrentSign.valueOf(it) }.getOrNull() },
        )
    }

    override suspend fun setCalibration(calibration: CurrentCalibration) = editDevice {
        calibration.learnedUnit?.let { u -> it[K.calUnit] = u.name } ?: it.remove(K.calUnit)
        calibration.learnedSign?.let { s -> it[K.calSign] = s.name } ?: it.remove(K.calSign)
    }

    override suspend fun alertState(): AlertState {
        val p = deviceData.first()
        return AlertState(
            wasPlugged = p[K.sWasPlugged]?.toBooleanStrictOrNull(),
            plugStartLevel = p[K.sStartLevel]?.takeIf { it >= 0 },
            lowFired = p[K.sLow] ?: false,
            firedThresholds = p[K.sThresholds]?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet(),
            completeFired = p[K.sComplete] ?: false,
            highTempActive = p[K.sTemp] ?: false,
            longChargeFired = p[K.sLong] ?: false,
            unusualFired = p[K.sUnusual] ?: false,
        )
    }

    override suspend fun setAlertState(state: AlertState) = editDevice {
        state.wasPlugged?.let { v -> it[K.sWasPlugged] = v.toString() } ?: it.remove(K.sWasPlugged)
        it[K.sStartLevel] = state.plugStartLevel ?: -1
        it[K.sLow] = state.lowFired
        it[K.sThresholds] = state.firedThresholds.map { t -> t.toString() }.toSet()
        it[K.sComplete] = state.completeFired
        it[K.sTemp] = state.highTempActive
        it[K.sLong] = state.longChargeFired
        it[K.sUnusual] = state.unusualFired
    }
}
