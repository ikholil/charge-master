package com.hikmahlab.chargemaster.domain.model

/**
 * One normalized battery observation. Every nullable value means "unavailable on this device
 * or not reported right now" — callers must never substitute placeholder numbers.
 */
data class BatterySnapshot(
    val timestamp: Long,
    val level: Int?,
    val status: ChargingStatus,
    val source: ChargingSource,
    val health: BatteryHealthStatus,
    val temperatureC: Float?,
    val voltageMv: Int?,
    /** Normalized: positive = current flowing into the battery, in mA. */
    val currentNowMa: Float?,
    val currentAverageMa: Float?,
    /** True when the sign of the current was inferred rather than learned/known. */
    val currentCalibrationUncertain: Boolean,
    val chargeCounterMah: Float?,
    val energyCounterMwh: Float?,
    val technology: String?,
    val present: Boolean?,
    /** Only reported by Android 14+ on supporting devices. */
    val cycleCount: Int?,
    val screenOn: Boolean?,
    /** BatteryManager.computeChargeTimeRemaining() (API 28+), null if unknown. */
    val systemChargeTimeRemainingMs: Long?,
    val thermalStatus: ThermalStatus,
) {
    val isPluggedIn: Boolean get() = source.isExternalPower

    /** Estimated power, W = V × A. Positive = into battery. Null if not reliably computable. */
    val powerW: Float? get() = PowerEstimator.estimateWatts(voltageMv, currentNowMa)
}

object PowerEstimator {
    /** Upper sanity bound for phone/tablet battery power. */
    private const val MAX_PLAUSIBLE_WATTS = 240f

    fun estimateWatts(voltageMv: Int?, currentMa: Float?): Float? {
        if (voltageMv == null || currentMa == null) return null
        if (voltageMv <= 0 || currentMa == 0f) return null
        val watts = (voltageMv / 1000f) * (currentMa / 1000f)
        return if (kotlin.math.abs(watts) > MAX_PLAUSIBLE_WATTS) null else watts
    }
}
