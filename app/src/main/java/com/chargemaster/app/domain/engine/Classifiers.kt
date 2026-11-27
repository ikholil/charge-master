package com.chargemaster.app.domain.engine

import com.chargemaster.app.domain.model.ChargingSession

/** Coarse charging-speed tier derived from estimated power into the battery. */
enum class ChargeSpeed {
    TRICKLE, SLOW, STANDARD, FAST, SUPER_FAST;

    companion object {
        /** Tier boundaries (W) follow common charger classes: 5 W USB, 7.5–18 W, 18–30 W, 30 W+. */
        fun classify(powerW: Float?): ChargeSpeed? {
            val w = powerW?.takeIf { it > 0f } ?: return null
            return when {
                w < 2.5f -> TRICKLE
                w < 7.5f -> SLOW
                w < 18f -> STANDARD
                w < 30f -> FAST
                else -> SUPER_FAST
            }
        }
    }
}

/** Battery temperature comfort zone, based on common lithium-ion operating guidance. */
enum class TemperatureZone {
    COLD, IDEAL, WARM, HOT, CRITICAL;

    companion object {
        const val COLD_BELOW = 10f
        const val WARM_FROM = 35f
        const val HOT_FROM = 40f
        const val CRITICAL_FROM = 45f

        fun of(celsius: Float?): TemperatureZone? = when {
            celsius == null -> null
            celsius < COLD_BELOW -> COLD
            celsius < WARM_FROM -> IDEAL
            celsius < HOT_FROM -> WARM
            celsius < CRITICAL_FROM -> HOT
            else -> CRITICAL
        }
    }
}

/** Battery usage since the last completed charge ended. */
data class SinceUnplug(val elapsedMs: Long, val usedPercent: Int, val ratePerHour: Float?)

object SinceUnplugCalculator {
    /** Minimum time on battery before a drain rate is shown; shorter spans are too noisy. */
    private const val MIN_RATE_MS = 15 * 60_000L

    fun compute(last: ChargingSession?, currentLevel: Int?, now: Long): SinceUnplug? {
        if (last == null || last.isOpen || currentLevel == null) return null
        val end = last.endTime ?: return null
        val endLevel = last.endLevel ?: return null
        val elapsed = now - end
        if (elapsed <= 0) return null
        val used = endLevel - currentLevel
        // A level higher than at unplug means charging happened we did not record: don't guess.
        if (used < 0) return null
        val rate = if (elapsed >= MIN_RATE_MS) used / (elapsed / 3_600_000f) else null
        return SinceUnplug(elapsed, used, rate)
    }
}
