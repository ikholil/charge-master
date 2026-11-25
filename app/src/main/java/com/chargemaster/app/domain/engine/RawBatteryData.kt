package com.chargemaster.app.domain.engine

/**
 * Unprocessed values exactly as Android returned them. Sentinels:
 * Int.MIN_VALUE / Long.MIN_VALUE = property not supported, -1 = missing extra.
 */
data class RawBatteryData(
    val timestamp: Long,
    val level: Int,
    val scale: Int,
    val status: Int,
    val health: Int,
    val plugged: Int,
    val temperatureTenthsC: Int,
    val voltage: Int,
    val technology: String?,
    val present: Boolean?,
    val cycleCount: Int,
    val currentNow: Int,
    val currentAverage: Int,
    val chargeCounter: Int,
    val energyCounter: Long,
    val chargeTimeRemainingMs: Long,
    val interactive: Boolean?,
    val thermalStatus: Int?,
)
