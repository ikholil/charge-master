package com.hikmahlab.chargemaster.domain.engine

import com.hikmahlab.chargemaster.domain.model.AlertPreferences
import com.hikmahlab.chargemaster.domain.model.ChargingSession
import kotlin.math.roundToInt

/**
 * Charging Habit Score: how often recorded sessions met the user's own targets.
 * It describes habits, not battery health, and needs a minimum number of sessions.
 */
object HabitAnalyzer {
    const val MIN_SESSIONS = 3
    /** Tolerance above the user's charge target that still counts as "stopped near target". */
    private const val TARGET_TOLERANCE = 5

    enum class Indicator { STOPPED_NEAR_TARGET, AVOIDED_DEEP_DISCHARGE, STAYED_COOL, AVOIDED_LONG_CHARGES }

    /** [met] of [eligible] sessions satisfied the indicator. */
    data class IndicatorResult(val indicator: Indicator, val met: Int, val eligible: Int) {
        val ratio: Float get() = if (eligible == 0) 0f else met.toFloat() / eligible
    }

    data class HabitScore(val score: Int?, val sessionCount: Int, val indicators: List<IndicatorResult>)

    fun analyze(sessions: List<ChargingSession>, prefs: AlertPreferences): HabitScore {
        val closed = sessions.filter { !it.isOpen && it.startLevel != null && it.endLevel != null }
        if (closed.size < MIN_SESSIONS) return HabitScore(null, closed.size, emptyList())

        val target = prefs.threshold80
        val results = buildList {
            add(IndicatorResult(Indicator.STOPPED_NEAR_TARGET, closed.count { it.endLevel!! <= target + TARGET_TOLERANCE }, closed.size))
            add(IndicatorResult(Indicator.AVOIDED_DEEP_DISCHARGE, closed.count { it.startLevel!! >= prefs.lowBatteryThreshold }, closed.size))
            val withTemp = closed.filter { it.maxTempC != null }
            if (withTemp.isNotEmpty()) {
                add(IndicatorResult(Indicator.STAYED_COOL, withTemp.count { it.maxTempC!! < prefs.highTempThresholdC }, withTemp.size))
            }
            val longLimit = prefs.longChargeHours * 3_600_000L
            add(IndicatorResult(Indicator.AVOIDED_LONG_CHARGES, closed.count { it.durationMs() < longLimit }, closed.size))
        }
        val score = (results.map { it.ratio }.average() * 100).roundToInt()
        return HabitScore(score, closed.size, results)
    }
}
