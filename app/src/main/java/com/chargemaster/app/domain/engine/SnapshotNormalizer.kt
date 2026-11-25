package com.chargemaster.app.domain.engine

import com.chargemaster.app.domain.model.BatteryHealthStatus
import com.chargemaster.app.domain.model.BatterySnapshot
import com.chargemaster.app.domain.model.CalibrationMode
import com.chargemaster.app.domain.model.ChargingSource
import com.chargemaster.app.domain.model.ChargingStatus
import com.chargemaster.app.domain.model.CurrentCalibration
import com.chargemaster.app.domain.model.CurrentSign
import com.chargemaster.app.domain.model.CurrentUnit
import com.chargemaster.app.domain.model.ThermalStatus
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Converts raw Android values into a [BatterySnapshot], rejecting unsupported or implausible
 * values and resolving device-specific unit and sign conventions for current.
 *
 * Android constants are duplicated here as literals so this class stays a pure, JVM-testable unit.
 */
object SnapshotNormalizer {

    /** |raw| at or above this cannot be milliamperes on a phone (≥ 10 A). */
    internal const val MICROAMP_THRESHOLD = 10_000
    /** Ignore near-zero currents when learning sign. */
    private const val SIGN_LEARNING_MIN_MA = 50f

    data class Result(val snapshot: BatterySnapshot, val calibration: CurrentCalibration)

    fun normalize(
        raw: RawBatteryData,
        calibration: CurrentCalibration,
        unitMode: CalibrationMode,
        signMode: CalibrationMode,
    ): Result {
        val status = mapStatus(raw.status)
        val source = mapPlugged(raw.plugged)
        val isCharging = status == ChargingStatus.CHARGING

        var cal = calibration
        val unit = resolveUnit(raw.currentNow, isCharging, cal, unitMode).also { (u, learned) ->
            if (learned != null) cal = cal.copy(learnedUnit = learned)
        }.first
        val currentNowAbsMa = toMilliamps(raw.currentNow, unit)
        val currentAvgAbsMa = toMilliamps(raw.currentAverage, unit)

        // Learn sign only from confident observations while actively charging.
        if (signMode == CalibrationMode.AUTO && isCharging && currentNowAbsMa != null &&
            abs(currentNowAbsMa) >= SIGN_LEARNING_MIN_MA
        ) {
            cal = cal.copy(learnedSign = if (currentNowAbsMa > 0) CurrentSign.STANDARD else CurrentSign.INVERTED)
        }
        val sign = when (signMode) {
            CalibrationMode.FORCE_PRIMARY -> CurrentSign.STANDARD
            CalibrationMode.FORCE_SECONDARY -> CurrentSign.INVERTED
            CalibrationMode.AUTO -> cal.learnedSign
        }
        val uncertain = (sign == null) || (unitMode == CalibrationMode.AUTO && cal.learnedUnit == null)
        val effectiveSign = sign ?: CurrentSign.STANDARD
        fun applySign(v: Float?) = v?.let { if (effectiveSign == CurrentSign.INVERTED) -it else it }

        val snapshot = BatterySnapshot(
            timestamp = raw.timestamp,
            level = normalizeLevel(raw.level, raw.scale),
            status = status,
            source = source,
            health = mapHealth(raw.health),
            temperatureC = normalizeTemperature(raw.temperatureTenthsC),
            voltageMv = normalizeVoltage(raw.voltage),
            currentNowMa = applySign(currentNowAbsMa),
            currentAverageMa = applySign(currentAvgAbsMa),
            currentCalibrationUncertain = uncertain && currentNowAbsMa != null,
            chargeCounterMah = normalizeChargeCounter(raw.chargeCounter),
            energyCounterMwh = normalizeEnergyCounter(raw.energyCounter),
            technology = raw.technology?.takeIf { it.isNotBlank() },
            present = raw.present,
            cycleCount = raw.cycleCount.takeIf { it >= 0 },
            screenOn = raw.interactive,
            systemChargeTimeRemainingMs = raw.chargeTimeRemainingMs.takeIf { it > 0 },
            thermalStatus = mapThermal(raw.thermalStatus),
        )
        return Result(snapshot, cal)
    }

    fun normalizeLevel(level: Int, scale: Int): Int? {
        if (level < 0 || scale <= 0) return null
        return (level * 100f / scale).roundToInt().coerceIn(0, 100)
    }

    /** EXTRA_TEMPERATURE is in tenths of °C. Reject implausible values. */
    fun normalizeTemperature(tenths: Int): Float? {
        if (tenths == Int.MIN_VALUE) return null
        val c = tenths / 10f
        return if (c < -40f || c > 100f) null else c
    }

    /** EXTRA_VOLTAGE is documented in mV; a few devices report volts. */
    fun normalizeVoltage(raw: Int): Int? {
        if (raw == Int.MIN_VALUE || raw <= 0) return null
        val mv = if (raw in 1..10) raw * 1000 else raw
        return if (mv in 2_000..6_000) mv else null
    }

    /**
     * Returns the unit to use and, if a new fact was learned, that unit.
     * Values ≥ [MICROAMP_THRESHOLD] are always µA. A charging current between 100 and 10 000
     * would be under 10 mA if it were µA — not a real charge — so the device uses mA.
     */
    internal fun resolveUnit(
        raw: Int,
        isCharging: Boolean,
        cal: CurrentCalibration,
        mode: CalibrationMode,
    ): Pair<CurrentUnit, CurrentUnit?> {
        when (mode) {
            CalibrationMode.FORCE_PRIMARY -> return CurrentUnit.MICROAMPERES to null
            CalibrationMode.FORCE_SECONDARY -> return CurrentUnit.MILLIAMPERES to null
            CalibrationMode.AUTO -> Unit
        }
        if (raw == Int.MIN_VALUE || raw == 0) return (cal.learnedUnit ?: CurrentUnit.MICROAMPERES) to null
        val magnitude = abs(raw.toLong())
        if (magnitude >= MICROAMP_THRESHOLD) {
            return CurrentUnit.MICROAMPERES to CurrentUnit.MICROAMPERES.takeIf { cal.learnedUnit != it }
        }
        cal.learnedUnit?.let { return it to null }
        if (isCharging && magnitude >= 100) return CurrentUnit.MILLIAMPERES to CurrentUnit.MILLIAMPERES
        // Unlearned and ambiguous: follow Android documentation (µA).
        return CurrentUnit.MICROAMPERES to null
    }

    /** Returns magnitude-with-raw-sign in mA, or null when unsupported. */
    internal fun toMilliamps(raw: Int, unit: CurrentUnit): Float? {
        if (raw == Int.MIN_VALUE || raw == 0) return null // 0 is what most unsupported devices return
        val ma = if (unit == CurrentUnit.MICROAMPERES) raw / 1000f else raw.toFloat()
        return if (abs(ma) > 20_000f) null else ma
    }

    /** BATTERY_PROPERTY_CHARGE_COUNTER is µAh; some devices report mAh. */
    fun normalizeChargeCounter(raw: Int): Float? {
        if (raw == Int.MIN_VALUE || raw <= 0) return null
        val mah = if (raw >= 30_000) raw / 1000f else raw.toFloat()
        return if (mah in 50f..30_000f) mah else null
    }

    /** BATTERY_PROPERTY_ENERGY_COUNTER is nWh. */
    fun normalizeEnergyCounter(raw: Long): Float? {
        if (raw == Long.MIN_VALUE || raw <= 0) return null
        return raw / 1_000_000f
    }

    fun mapStatus(v: Int) = when (v) {
        2 -> ChargingStatus.CHARGING
        3 -> ChargingStatus.DISCHARGING
        4 -> ChargingStatus.NOT_CHARGING
        5 -> ChargingStatus.FULL
        else -> ChargingStatus.UNKNOWN
    }

    fun mapPlugged(v: Int) = when {
        v < 0 -> ChargingSource.UNKNOWN
        v == 0 -> ChargingSource.NONE
        v and 1 != 0 -> ChargingSource.AC
        v and 2 != 0 -> ChargingSource.USB
        v and 4 != 0 -> ChargingSource.WIRELESS
        v and 8 != 0 -> ChargingSource.DOCK
        else -> ChargingSource.UNKNOWN
    }

    fun mapHealth(v: Int) = when (v) {
        2 -> BatteryHealthStatus.GOOD
        3 -> BatteryHealthStatus.OVERHEAT
        4 -> BatteryHealthStatus.DEAD
        5 -> BatteryHealthStatus.OVER_VOLTAGE
        6 -> BatteryHealthStatus.UNSPECIFIED_FAILURE
        7 -> BatteryHealthStatus.COLD
        else -> BatteryHealthStatus.UNKNOWN
    }

    fun mapThermal(v: Int?) = when (v) {
        null -> ThermalStatus.UNSUPPORTED
        0 -> ThermalStatus.NONE
        1 -> ThermalStatus.LIGHT
        2 -> ThermalStatus.MODERATE
        3 -> ThermalStatus.SEVERE
        4 -> ThermalStatus.CRITICAL
        5 -> ThermalStatus.EMERGENCY
        6 -> ThermalStatus.SHUTDOWN
        else -> ThermalStatus.UNSUPPORTED
    }
}
