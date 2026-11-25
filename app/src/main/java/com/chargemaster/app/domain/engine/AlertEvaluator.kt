package com.chargemaster.app.domain.engine

import com.chargemaster.app.domain.model.AlertPreferences
import com.chargemaster.app.domain.model.BatterySnapshot
import com.chargemaster.app.domain.model.ChargingSession
import com.chargemaster.app.domain.model.ChargingSource
import com.chargemaster.app.domain.model.ChargingStatus

sealed interface BatteryAlert {
    data class LowBattery(val level: Int) : BatteryAlert
    data class ThresholdReached(val threshold: Int, val level: Int) : BatteryAlert
    data class HighTemperature(val temperatureC: Float) : BatteryAlert
    data class ChargingStarted(val source: ChargingSource, val level: Int?) : BatteryAlert
    data class ChargingStopped(val level: Int?) : BatteryAlert
    data object ChargingComplete : BatteryAlert
    data class LongCharging(val hours: Int) : BatteryAlert
    data class UnusualSpeed(val observedW: Float, val typicalW: Float) : BatteryAlert
}

/** Persisted de-duplication state so alerts fire once per crossing, not on every sample. */
data class AlertState(
    val wasPlugged: Boolean? = null,
    val plugStartLevel: Int? = null,
    val lowFired: Boolean = false,
    val firedThresholds: Set<Int> = emptySet(),
    val completeFired: Boolean = false,
    val highTempActive: Boolean = false,
    val longChargeFired: Boolean = false,
    val unusualFired: Boolean = false,
)

object AlertEvaluator {
    private const val LOW_RESET_MARGIN = 5
    private const val TEMP_RESET_MARGIN_C = 2f
    private const val UNUSUAL_RATIO = 0.4f
    private const val UNUSUAL_MAX_LEVEL = 70
    private const val UNUSUAL_MIN_SAMPLES = 3

    data class Result(val alerts: List<BatteryAlert>, val state: AlertState)

    fun evaluate(
        state: AlertState,
        s: BatterySnapshot,
        prefs: AlertPreferences,
        openSession: ChargingSession?,
        /** Median average power of past complete sessions on the same source, if enough exist. */
        typicalPowerW: Float?,
    ): Result {
        val alerts = mutableListOf<BatteryAlert>()
        var st = state
        val plugged = s.isPluggedIn
        val level = s.level

        // Plug transitions
        if (st.wasPlugged != null && plugged != st.wasPlugged) {
            if (plugged) {
                if (prefs.chargingStartedEnabled) alerts += BatteryAlert.ChargingStarted(s.source, level)
            } else {
                if (prefs.chargingStoppedEnabled && s.status != ChargingStatus.FULL) alerts += BatteryAlert.ChargingStopped(level)
            }
        }
        if (plugged && st.wasPlugged != true) {
            st = st.copy(
                plugStartLevel = openSession?.startLevel ?: level,
                firedThresholds = emptySet(), completeFired = false,
                longChargeFired = false, unusualFired = false, lowFired = false,
            )
        }
        if (!plugged && st.wasPlugged == true) {
            st = st.copy(plugStartLevel = null, firedThresholds = emptySet(), completeFired = false)
        }
        st = st.copy(wasPlugged = plugged)

        // Low battery
        if (level != null) {
            if (!plugged && prefs.lowBatteryEnabled && !st.lowFired && level <= prefs.lowBatteryThreshold) {
                alerts += BatteryAlert.LowBattery(level)
                st = st.copy(lowFired = true)
            } else if (st.lowFired && level > prefs.lowBatteryThreshold + LOW_RESET_MARGIN) {
                st = st.copy(lowFired = false)
            }
        }

        // Charge thresholds — only when the level crossed the threshold during this charge.
        if (plugged && level != null) {
            val startLevel = st.plugStartLevel ?: level
            val thresholds = buildList {
                if (prefs.threshold80Enabled) add(prefs.threshold80)
                if (prefs.threshold90Enabled) add(prefs.threshold90)
                if (prefs.fullEnabled) add(100)
            }.distinct().sorted()
            val crossed = thresholds.filter { t -> level >= t && startLevel < t && t !in st.firedThresholds }
            if (crossed.isNotEmpty()) {
                // Only the highest newly crossed threshold is shown, avoiding a burst.
                alerts += BatteryAlert.ThresholdReached(crossed.last(), level)
                st = st.copy(firedThresholds = st.firedThresholds + crossed)
            }
            if (prefs.chargingCompleteEnabled && !st.completeFired && s.status == ChargingStatus.FULL) {
                if (100 !in crossed) alerts += BatteryAlert.ChargingComplete
                st = st.copy(completeFired = true)
            }
        }

        // Temperature with hysteresis
        val temp = s.temperatureC
        if (temp != null) {
            if (prefs.highTempEnabled && !st.highTempActive && temp >= prefs.highTempThresholdC) {
                alerts += BatteryAlert.HighTemperature(temp)
                st = st.copy(highTempActive = true)
            } else if (st.highTempActive && temp < prefs.highTempThresholdC - TEMP_RESET_MARGIN_C) {
                st = st.copy(highTempActive = false)
            }
        }

        // Long charging session
        if (plugged && openSession != null && prefs.longChargeEnabled && !st.longChargeFired) {
            val hours = openSession.durationMs(s.timestamp) / 3_600_000.0
            if (hours >= prefs.longChargeHours) {
                alerts += BatteryAlert.LongCharging(prefs.longChargeHours)
                st = st.copy(longChargeFired = true)
            }
        }

        // Unusual speed: only with history, below the taper zone, and not when hot (throttling is normal).
        if (plugged && prefs.unusualSpeedEnabled && !st.unusualFired && typicalPowerW != null &&
            openSession != null && openSession.powerSampleCount >= UNUSUAL_MIN_SAMPLES &&
            s.status == ChargingStatus.CHARGING && level != null && level < UNUSUAL_MAX_LEVEL &&
            (temp == null || temp < prefs.highTempThresholdC - TEMP_RESET_MARGIN_C)
        ) {
            val avg = openSession.averagePowerW
            if (avg != null && avg < typicalPowerW * UNUSUAL_RATIO) {
                alerts += BatteryAlert.UnusualSpeed(avg, typicalPowerW)
                st = st.copy(unusualFired = true)
            }
        }

        return Result(alerts, st)
    }
}
