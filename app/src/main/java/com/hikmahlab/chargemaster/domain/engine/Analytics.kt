package com.hikmahlab.chargemaster.domain.engine

import com.hikmahlab.chargemaster.domain.model.BatteryReading
import com.hikmahlab.chargemaster.domain.model.ChargingSession
import com.hikmahlab.chargemaster.domain.model.ChargingSource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class DischargeStats(
    val observedHours: Float,
    val averageRatePerHour: Float?,
    val screenOnRatePerHour: Float?,
    val screenOnHours: Float,
    val screenOffRatePerHour: Float?,
    val screenOffHours: Float,
    val chargingHours: Float,
    val dischargingHours: Float,
    val coverage: Float,
)

/** Discharge analysis based only on Charge Master's own observations. */
object DischargeAnalyzer {
    private const val MAX_PAIR_GAP_MS = 2 * 3_600_000L
    private const val MIN_PAIR_GAP_MS = 60_000L
    private const val MIN_HOURS_FOR_RATE = 0.5f

    fun analyze(readings: List<BatteryReading>, windowMs: Long): DischargeStats {
        val sorted = readings.filter { it.level != null }.sortedBy { it.timestamp }
        var dropAll = 0f; var hoursAll = 0f
        var dropOn = 0f; var hoursOn = 0f
        var dropOff = 0f; var hoursOff = 0f
        var chargingHours = 0f
        for (i in 1 until sorted.size) {
            val a = sorted[i - 1]; val b = sorted[i]
            val gap = b.timestamp - a.timestamp
            if (gap !in MIN_PAIR_GAP_MS..MAX_PAIR_GAP_MS) continue
            val h = gap / 3_600_000f
            if (a.isPluggedIn || b.isPluggedIn) {
                if (a.isPluggedIn && b.isPluggedIn) chargingHours += h
                continue
            }
            val drop = (a.level!! - b.level!!).toFloat()
            if (drop < 0) continue // level rose while unplugged: unreliable pair
            dropAll += drop; hoursAll += h
            when {
                a.screenOn == true && b.screenOn == true -> { dropOn += drop; hoursOn += h }
                a.screenOn == false && b.screenOn == false -> { dropOff += drop; hoursOff += h }
            }
        }
        fun rate(d: Float, h: Float) = if (h >= MIN_HOURS_FOR_RATE) d / h else null
        val windowHours = windowMs / 3_600_000f
        return DischargeStats(
            observedHours = hoursAll + chargingHours,
            averageRatePerHour = rate(dropAll, hoursAll),
            screenOnRatePerHour = rate(dropOn, hoursOn),
            screenOnHours = hoursOn,
            screenOffRatePerHour = rate(dropOff, hoursOff),
            screenOffHours = hoursOff,
            chargingHours = chargingHours,
            dischargingHours = hoursAll,
            coverage = if (windowHours > 0) ((hoursAll + chargingHours) / windowHours).coerceIn(0f, 1f) else 0f,
        )
    }
}

data class TemperatureStats(
    val min: Float?, val max: Float?, val average: Float?,
    val chargingAverage: Float?, val chargingMax: Float?,
    val dischargingAverage: Float?, val dischargingMax: Float?,
    val sampleCount: Int,
    /** °C per hour over the last few samples; null when not enough data. */
    val recentTrendPerHour: Float?,
)

object TemperatureAnalyzer {
    fun analyze(readings: List<BatteryReading>): TemperatureStats {
        val r = readings.filter { it.temperatureC != null }.sortedBy { it.timestamp }
        val all = r.map { it.temperatureC!! }
        val ch = r.filter { it.isPluggedIn }.map { it.temperatureC!! }
        val dis = r.filter { !it.isPluggedIn }.map { it.temperatureC!! }
        val recent = r.takeLast(6)
        val trend = if (recent.size >= 3) {
            val hours = (recent.last().timestamp - recent.first().timestamp) / 3_600_000f
            if (hours >= 0.1f) (recent.last().temperatureC!! - recent.first().temperatureC!!) / hours else null
        } else null
        return TemperatureStats(
            min = all.minOrNull(), max = all.maxOrNull(), average = all.averageOrNull(),
            chargingAverage = ch.averageOrNull(), chargingMax = ch.maxOrNull(),
            dischargingAverage = dis.averageOrNull(), dischargingMax = dis.maxOrNull(),
            sampleCount = all.size, recentTrendPerHour = trend,
        )
    }
}

data class SourceStats(val source: ChargingSource, val sessions: Int, val averagePowerW: Float?, val averagePercentPerHour: Float?)

data class ChargingStats(
    val sessionCount: Int,
    val averageDurationMs: Long?,
    val fastest: ChargingSession?,
    val slowest: ChargingSession?,
    val peakPowerW: Float?,
    val bySource: List<SourceStats>,
    /** Number of sessions started on each local calendar day (index 0 = oldest). */
    val sessionsPerDay: List<Int>,
    /** The calendar days matching [sessionsPerDay]. */
    val days: List<LocalDate>,
)

object ChargingAnalyzer {
    /** Speed comparisons need at least this much gain to be meaningful. */
    private const val MIN_GAIN_FOR_SPEED = 10

    fun analyze(sessions: List<ChargingSession>, now: Long, days: Int, zone: ZoneId = ZoneId.systemDefault()): ChargingStats {
        val closed = sessions.filter { !it.isOpen }
        val speedEligible = closed.filter { (it.levelGain ?: 0) >= MIN_GAIN_FOR_SPEED && it.percentPerHour != null }
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val dayList = (days - 1 downTo 0).map { today.minusDays(it.toLong()) }
        val perDay = IntArray(days)
        sessions.forEach { s ->
            val d = Instant.ofEpochMilli(s.startTime).atZone(zone).toLocalDate()
            val idx = days - 1 - ChronoUnit.DAYS.between(d, today).toInt()
            if (idx in 0 until days) perDay[idx]++
        }
        return ChargingStats(
            sessionCount = sessions.size,
            averageDurationMs = closed.map { it.durationMs() }.takeIf { it.isNotEmpty() }?.average()?.toLong(),
            fastest = speedEligible.maxByOrNull { it.percentPerHour!! },
            slowest = speedEligible.minByOrNull { it.percentPerHour!! },
            peakPowerW = sessions.mapNotNull { it.peakPowerW }.maxOrNull(),
            bySource = closed.groupBy { it.source }.map { (src, list) ->
                SourceStats(
                    source = src,
                    sessions = list.size,
                    averagePowerW = list.mapNotNull { it.averagePowerW }.averageOrNull(),
                    averagePercentPerHour = list.filter { (it.levelGain ?: 0) >= MIN_GAIN_FOR_SPEED }
                        .mapNotNull { it.percentPerHour }.averageOrNull(),
                )
            }.sortedByDescending { it.sessions },
            sessionsPerDay = perDay.toList(),
            days = dayList,
        )
    }

    /** Median average power of the most recent complete sessions on [source]; needs ≥5. */
    fun typicalPower(sessions: List<ChargingSession>, source: ChargingSource): Float? {
        val values = sessions
            .filter { !it.isOpen && it.source == source && it.powerSampleCount >= 3 }
            .sortedByDescending { it.startTime }
            .take(10)
            .mapNotNull { it.averagePowerW }
            .sorted()
        if (values.size < 5) return null
        return values[values.size / 2]
    }
}

data class TimeEstimate(val minutes: Long, val method: EstimateMethod)

enum class EstimateMethod { SYSTEM, OBSERVED_RATE, CURRENT_AND_CAPACITY }

object TimeEstimator {
    private const val MIN_ELAPSED_MS = 5 * 60_000L
    private const val MIN_GAIN = 2

    /**
     * Time until [target] % while charging. Uses Android's own estimate for 100 %,
     * otherwise the rate observed during the current session, otherwise current ÷ remaining charge.
     */
    fun timeToLevel(
        target: Int,
        level: Int?,
        systemRemainingMs: Long?,
        session: ChargingSession?,
        currentMa: Float?,
        capacityMah: Float?,
    ): TimeEstimate? {
        if (level == null) return null
        if (level >= target) return TimeEstimate(0, EstimateMethod.OBSERVED_RATE)
        if (target == 100 && systemRemainingMs != null) {
            return TimeEstimate(systemRemainingMs / 60_000, EstimateMethod.SYSTEM)
        }
        if (session != null && session.startLevel != null && session.endLevel != null) {
            // Rate from the session's own samples (level and time from the same observations).
            val elapsed = session.lastSampleTime - session.startTime
            val gain = session.endLevel - session.startLevel
            if (elapsed >= MIN_ELAPSED_MS && gain >= MIN_GAIN) {
                val msPerPercent = elapsed.toDouble() / gain
                return TimeEstimate(((target - level) * msPerPercent / 60_000).toLong(), EstimateMethod.OBSERVED_RATE)
            }
        }
        if (currentMa != null && currentMa > 50f && capacityMah != null) {
            val remainingMah = capacityMah * (target - level) / 100f
            return TimeEstimate((remainingMah / currentMa * 60).toLong(), EstimateMethod.CURRENT_AND_CAPACITY)
        }
        return null
    }

    /** Remaining time on battery from the observed average discharge rate (%/h). */
    fun timeRemainingOnBattery(level: Int?, ratePerHour: Float?): TimeEstimate? {
        if (level == null || ratePerHour == null || ratePerHour <= 0.1f) return null
        return TimeEstimate((level / ratePerHour * 60).toLong(), EstimateMethod.OBSERVED_RATE)
    }
}

internal fun List<Float>.averageOrNull(): Float? = if (isEmpty()) null else average().toFloat()
