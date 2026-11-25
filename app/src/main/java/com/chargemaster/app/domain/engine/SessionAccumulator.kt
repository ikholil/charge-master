package com.chargemaster.app.domain.engine

import com.chargemaster.app.domain.model.BatterySnapshot
import com.chargemaster.app.domain.model.ChargingSession
import com.chargemaster.app.domain.model.SessionEndReason

/**
 * Pure state machine that turns a stream of samples into charging sessions.
 * It never invents data: precision flags record whether boundaries were actually observed.
 */
class SessionAccumulator(
    /** Samples further apart than this count as a monitoring gap. */
    private val maxGapMs: Long = DEFAULT_MAX_GAP_MS,
    /** A re-plug within this window continues the previous session as an interruption. */
    private val mergeWindowMs: Long = DEFAULT_MERGE_WINDOW_MS,
    /** Sessions shorter than this with no level gain are discarded. */
    private val minDurationMs: Long = 60_000,
) {
    sealed interface Update {
        data object None : Update
        data class Started(val session: ChargingSession) : Update
        data class Updated(val session: ChargingSession) : Update
        data class Resumed(val session: ChargingSession) : Update
        data class Closed(val session: ChargingSession) : Update
        data class Discarded(val session: ChargingSession) : Update
    }

    fun onSample(
        open: ChargingSession?,
        previous: BatterySnapshot?,
        recentlyClosed: ChargingSession?,
        sample: BatterySnapshot,
    ): Update {
        val plugged = sample.isPluggedIn
        return when {
            open == null && plugged -> {
                if (recentlyClosed != null && recentlyClosed.endTime != null &&
                    sample.timestamp - recentlyClosed.endTime in 0..mergeWindowMs
                ) {
                    Update.Resumed(
                        accumulate(
                            recentlyClosed.copy(
                                endTime = null,
                                endReason = SessionEndReason.NONE,
                                interruptions = recentlyClosed.interruptions + 1,
                            ),
                            sample,
                        ),
                    )
                } else {
                    Update.Started(start(previous, sample))
                }
            }
            open != null && plugged -> {
                var s = open
                if (sample.timestamp - open.lastSampleTime > maxGapMs) s = s.copy(hadMonitoringGap = true)
                if (sample.source != open.source) s = s.copy(interruptions = s.interruptions + 1)
                Update.Updated(accumulate(s, sample))
            }
            open != null && !plugged -> {
                val gap = sample.timestamp - open.lastSampleTime
                val precise = gap <= maxGapMs
                val closed = open.copy(
                    endTime = if (precise) sample.timestamp else open.lastSampleTime,
                    endPrecise = precise,
                    endReason = SessionEndReason.UNPLUGGED,
                )
                val gain = closed.levelGain ?: 0
                if (closed.durationMs() < minDurationMs && gain <= 0) Update.Discarded(closed) else Update.Closed(closed)
            }
            else -> Update.None
        }
    }

    /** Close a session whose monitoring was cut by a restart; end time = last real observation. */
    fun closeForRestart(open: ChargingSession): ChargingSession = open.copy(
        endTime = open.lastSampleTime,
        endPrecise = false,
        endReason = SessionEndReason.DEVICE_RESTART,
    )

    private fun start(previous: BatterySnapshot?, sample: BatterySnapshot): ChargingSession {
        val precise = previous != null && !previous.isPluggedIn &&
            sample.timestamp - previous.timestamp <= maxGapMs
        val base = ChargingSession(
            startTime = sample.timestamp,
            endTime = null,
            lastSampleTime = sample.timestamp,
            startLevel = sample.level,
            endLevel = sample.level,
            source = sample.source,
            sampleCount = 0,
            powerSampleCount = 0,
            powerSumW = 0.0,
            peakPowerW = null,
            tempSampleCount = 0,
            tempSumC = 0.0,
            maxTempC = null,
            startTempC = sample.temperatureC,
            endTempC = sample.temperatureC,
            interruptions = 0,
            startPrecise = precise,
            endPrecise = false,
            hadMonitoringGap = false,
            endReason = SessionEndReason.NONE,
        )
        return accumulate(base, sample)
    }

    private fun accumulate(s: ChargingSession, sample: BatterySnapshot): ChargingSession {
        // Only count power while energy flows into the battery.
        val power = sample.powerW?.takeIf { it > 0f }
        val temp = sample.temperatureC
        return s.copy(
            lastSampleTime = sample.timestamp,
            endLevel = sample.level ?: s.endLevel,
            startLevel = s.startLevel ?: sample.level,
            sampleCount = s.sampleCount + 1,
            powerSampleCount = s.powerSampleCount + if (power != null) 1 else 0,
            powerSumW = s.powerSumW + (power ?: 0f),
            peakPowerW = maxOfNullable(s.peakPowerW, power),
            tempSampleCount = s.tempSampleCount + if (temp != null) 1 else 0,
            tempSumC = s.tempSumC + (temp ?: 0f),
            maxTempC = maxOfNullable(s.maxTempC, temp),
            startTempC = s.startTempC ?: temp,
            endTempC = temp ?: s.endTempC,
        )
    }

    private fun maxOfNullable(a: Float?, b: Float?): Float? = when {
        a == null -> b
        b == null -> a
        else -> maxOf(a, b)
    }

    companion object {
        const val DEFAULT_MAX_GAP_MS = 45 * 60_000L
        const val DEFAULT_MERGE_WINDOW_MS = 2 * 60_000L
    }
}
