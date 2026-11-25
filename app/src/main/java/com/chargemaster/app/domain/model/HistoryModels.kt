package com.chargemaster.app.domain.model

/** A persisted historical observation. */
data class BatteryReading(
    val id: Long = 0,
    val timestamp: Long,
    val level: Int?,
    val temperatureC: Float?,
    val voltageMv: Int?,
    val currentNowMa: Float?,
    val currentAverageMa: Float?,
    val chargeCounterMah: Float?,
    val status: ChargingStatus,
    val source: ChargingSource,
    val screenOn: Boolean?,
) {
    val isPluggedIn: Boolean get() = source.isExternalPower
    val powerW: Float? get() = PowerEstimator.estimateWatts(voltageMv, currentNowMa)

    companion object {
        fun from(s: BatterySnapshot) = BatteryReading(
            timestamp = s.timestamp,
            level = s.level,
            temperatureC = s.temperatureC,
            voltageMv = s.voltageMv,
            currentNowMa = s.currentNowMa,
            currentAverageMa = s.currentAverageMa,
            chargeCounterMah = s.chargeCounterMah,
            status = s.status,
            source = s.source,
            screenOn = s.screenOn,
        )
    }
}

/**
 * A charging session. Aggregates are stored as running sums so averages stay exact
 * without keeping every sample.
 */
data class ChargingSession(
    val id: Long = 0,
    val startTime: Long,
    val endTime: Long?,
    val lastSampleTime: Long,
    val startLevel: Int?,
    val endLevel: Int?,
    val source: ChargingSource,
    val sampleCount: Int,
    val powerSampleCount: Int,
    val powerSumW: Double,
    val peakPowerW: Float?,
    val tempSampleCount: Int,
    val tempSumC: Double,
    val maxTempC: Float?,
    val startTempC: Float?,
    val endTempC: Float?,
    val interruptions: Int,
    /** Start was observed close to the real plug-in moment. */
    val startPrecise: Boolean,
    /** End was observed close to the real unplug moment. */
    val endPrecise: Boolean,
    val hadMonitoringGap: Boolean,
    val endReason: SessionEndReason,
) {
    val isOpen: Boolean get() = endTime == null
    val isComplete: Boolean get() = !isOpen && startPrecise && endPrecise && !hadMonitoringGap
    val averagePowerW: Float? get() = if (powerSampleCount > 0) (powerSumW / powerSampleCount).toFloat() else null
    val averageTempC: Float? get() = if (tempSampleCount > 0) (tempSumC / tempSampleCount).toFloat() else null
    fun durationMs(now: Long = lastSampleTime): Long = (endTime ?: now) - startTime
    val levelGain: Int? get() = if (startLevel != null && endLevel != null) endLevel - startLevel else null

    /** Percentage points per hour; null when too short to be meaningful. */
    val percentPerHour: Float?
        get() {
            val gain = levelGain ?: return null
            val hours = durationMs() / 3_600_000f
            if (hours < 5f / 60f || gain < 1) return null
            return gain / hours
        }
}

data class BatteryHealthSnapshot(
    val id: Long = 0,
    val timestamp: Long,
    val reportedHealth: BatteryHealthStatus,
    val estimatedCapacityMah: Float?,
    val designCapacityMah: Int?,
    val confidence: Confidence,
    val sessionId: Long?,
)
