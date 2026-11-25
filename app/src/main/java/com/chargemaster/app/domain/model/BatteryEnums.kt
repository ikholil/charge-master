package com.chargemaster.app.domain.model

/** Charging status as reported by Android (BatteryManager.EXTRA_STATUS). */
enum class ChargingStatus { CHARGING, DISCHARGING, NOT_CHARGING, FULL, UNKNOWN }

/** Power source as reported by Android (BatteryManager.EXTRA_PLUGGED). */
enum class ChargingSource {
    AC, USB, WIRELESS, DOCK, NONE, UNKNOWN;

    val isExternalPower: Boolean get() = this == AC || this == USB || this == WIRELESS || this == DOCK
}

/**
 * Android's *operational* battery condition (BatteryManager.EXTRA_HEALTH).
 * This is NOT a State-of-Health percentage.
 */
enum class BatteryHealthStatus { GOOD, OVERHEAT, DEAD, OVER_VOLTAGE, UNSPECIFIED_FAILURE, COLD, UNKNOWN }

/** PowerManager thermal status (API 29+). */
enum class ThermalStatus { NONE, LIGHT, MODERATE, SEVERE, CRITICAL, EMERGENCY, SHUTDOWN, UNSUPPORTED }

/** How much we trust an estimate. */
enum class Confidence { INSUFFICIENT, LOW, MEDIUM, HIGH }

enum class TemperatureUnit { CELSIUS, FAHRENHEIT }

enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }

/**
 * Trade-off between monitoring detail and energy use.
 * Foreground intervals only apply while the app is visible.
 */
enum class MonitoringMode(
    val foregroundIntervalMs: Long,
    val backgroundIntervalMinutes: Long,
    /** Interval for the charging-only sampler, null = disabled. */
    val chargingIntervalMinutes: Long?,
) {
    BASIC(foregroundIntervalMs = 5_000, backgroundIntervalMinutes = 60, chargingIntervalMinutes = null),
    BALANCED(foregroundIntervalMs = 2_000, backgroundIntervalMinutes = 30, chargingIntervalMinutes = 10),
    DETAILED(foregroundIntervalMs = 1_000, backgroundIntervalMinutes = 15, chargingIntervalMinutes = 3),
}

/** Unit the device uses for BATTERY_PROPERTY_CURRENT_* values. */
enum class CurrentUnit { MICROAMPERES, MILLIAMPERES }

/** Sign convention used by the device for current values. */
enum class CurrentSign {
    /** Android documented: positive = charging (into the battery). */
    STANDARD,
    /** Device reports negative values while charging. */
    INVERTED,
}

/** User override for current interpretation. AUTO = learned from observations. */
enum class CalibrationMode { AUTO, FORCE_PRIMARY, FORCE_SECONDARY }

enum class SessionEndReason { UNPLUGGED, DEVICE_RESTART, MONITORING_GAP, NONE }
