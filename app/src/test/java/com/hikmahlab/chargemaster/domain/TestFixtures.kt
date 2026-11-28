package com.hikmahlab.chargemaster.domain

import com.hikmahlab.chargemaster.domain.engine.RawBatteryData
import com.hikmahlab.chargemaster.domain.model.BatteryHealthStatus
import com.hikmahlab.chargemaster.domain.model.BatteryReading
import com.hikmahlab.chargemaster.domain.model.BatterySnapshot
import com.hikmahlab.chargemaster.domain.model.ChargingSource
import com.hikmahlab.chargemaster.domain.model.ChargingStatus
import com.hikmahlab.chargemaster.domain.model.ThermalStatus

const val MIN = 60_000L
const val HOUR = 3_600_000L

fun snapshot(
    t: Long,
    level: Int? = 50,
    source: ChargingSource = ChargingSource.NONE,
    status: ChargingStatus = if (source.isExternalPower) ChargingStatus.CHARGING else ChargingStatus.DISCHARGING,
    temp: Float? = 30f,
    voltage: Int? = 4000,
    current: Float? = if (source.isExternalPower) 2000f else -400f,
    counter: Float? = null,
) = BatterySnapshot(
    timestamp = t, level = level, status = status, source = source, health = BatteryHealthStatus.GOOD,
    temperatureC = temp, voltageMv = voltage, currentNowMa = current, currentAverageMa = null,
    currentCalibrationUncertain = false, chargeCounterMah = counter, energyCounterMwh = null,
    technology = "Li-ion", present = true, cycleCount = null, screenOn = true,
    systemChargeTimeRemainingMs = null, thermalStatus = ThermalStatus.NONE,
)

fun reading(
    t: Long, level: Int?, plugged: Boolean = false, screenOn: Boolean? = true,
    temp: Float? = 30f, counter: Float? = null, voltage: Int? = 4000, current: Float? = null,
) = BatteryReading(
    timestamp = t, level = level, temperatureC = temp, voltageMv = voltage, currentNowMa = current,
    currentAverageMa = null, chargeCounterMah = counter,
    status = if (plugged) ChargingStatus.CHARGING else ChargingStatus.DISCHARGING,
    source = if (plugged) ChargingSource.AC else ChargingSource.NONE, screenOn = screenOn,
)

fun raw(
    level: Int = 50, scale: Int = 100, status: Int = 3, health: Int = 2, plugged: Int = 0,
    temp: Int = 305, voltage: Int = 3980, currentNow: Int = Int.MIN_VALUE, currentAvg: Int = Int.MIN_VALUE,
    counter: Int = Int.MIN_VALUE, energy: Long = Long.MIN_VALUE, chargeTime: Long = -1, cycle: Int = -1,
    thermal: Int? = null,
) = RawBatteryData(
    timestamp = 1_000L, level = level, scale = scale, status = status, health = health, plugged = plugged,
    temperatureTenthsC = temp, voltage = voltage, technology = "Li-ion", present = true, cycleCount = cycle,
    currentNow = currentNow, currentAverage = currentAvg, chargeCounter = counter, energyCounter = energy,
    chargeTimeRemainingMs = chargeTime, interactive = true, thermalStatus = thermal,
)
