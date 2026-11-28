package com.hikmahlab.chargemaster.domain.model

data class AlertPreferences(
    val lowBatteryEnabled: Boolean = true,
    val lowBatteryThreshold: Int = 20,
    val threshold80Enabled: Boolean = true,
    val threshold80: Int = 80,
    val threshold90Enabled: Boolean = false,
    val threshold90: Int = 90,
    val fullEnabled: Boolean = false,
    val chargingStartedEnabled: Boolean = false,
    val chargingStoppedEnabled: Boolean = false,
    val chargingCompleteEnabled: Boolean = true,
    val highTempEnabled: Boolean = true,
    val highTempThresholdC: Float = 42f,
    val longChargeEnabled: Boolean = true,
    val longChargeHours: Int = 6,
    val unusualSpeedEnabled: Boolean = true,
)

/** Optional dashboard sections the user can show or hide (adaptive dashboard). */
enum class DashboardCard { ESTIMATE, TEMPERATURE, VOLTAGE, POWER, CURRENT, ANDROID_HEALTH, THERMAL, LAST_CHARGE, QUICK_LINKS }

data class UserPreferences(
    val onboardingComplete: Boolean = false,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val highContrast: Boolean = false,
    val reduceMotion: Boolean = false,
    val monitoringMode: MonitoringMode = MonitoringMode.BALANCED,
    val backgroundMonitoring: Boolean = true,
    val retentionDays: Int = 30,
    /** User-entered manufacturer design capacity; never inferred. */
    val designCapacityMah: Int? = null,
    val currentUnitMode: CalibrationMode = CalibrationMode.AUTO,
    val currentSignMode: CalibrationMode = CalibrationMode.AUTO,
    val alerts: AlertPreferences = AlertPreferences(),
    val dashboardCards: Set<DashboardCard> = DashboardCard.entries.toSet(),
)

/** What the app has learned about how this device reports current. */
data class CurrentCalibration(
    val learnedUnit: CurrentUnit? = null,
    val learnedSign: CurrentSign? = null,
)
