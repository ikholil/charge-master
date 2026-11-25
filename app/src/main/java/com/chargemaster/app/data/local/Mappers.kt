package com.chargemaster.app.data.local

import com.chargemaster.app.core.database.BatteryHealthSnapshotEntity
import com.chargemaster.app.core.database.BatteryReadingEntity
import com.chargemaster.app.core.database.ChargingSessionEntity
import com.chargemaster.app.domain.model.BatteryHealthSnapshot
import com.chargemaster.app.domain.model.BatteryHealthStatus
import com.chargemaster.app.domain.model.BatteryReading
import com.chargemaster.app.domain.model.ChargingSession
import com.chargemaster.app.domain.model.ChargingSource
import com.chargemaster.app.domain.model.ChargingStatus
import com.chargemaster.app.domain.model.Confidence
import com.chargemaster.app.domain.model.SessionEndReason

internal inline fun <reified E : Enum<E>> enumOrDefault(name: String, default: E): E =
    runCatching { enumValueOf<E>(name) }.getOrDefault(default)

fun BatteryReadingEntity.toDomain() = BatteryReading(
    id = id, timestamp = timestamp, level = batteryLevel, temperatureC = temperature, voltageMv = voltage,
    currentNowMa = currentNow, currentAverageMa = currentAverage, chargeCounterMah = chargeCounter,
    status = enumOrDefault(chargingStatus, ChargingStatus.UNKNOWN),
    source = enumOrDefault(chargingSource, ChargingSource.UNKNOWN),
    screenOn = screenOn,
)

fun BatteryReading.toEntity() = BatteryReadingEntity(
    id = id, timestamp = timestamp, batteryLevel = level, temperature = temperatureC, voltage = voltageMv,
    currentNow = currentNowMa, currentAverage = currentAverageMa, chargeCounter = chargeCounterMah,
    chargingStatus = status.name, chargingSource = source.name, screenOn = screenOn,
)

fun ChargingSessionEntity.toDomain() = ChargingSession(
    id = id, startTime = startTime, endTime = endTime, lastSampleTime = lastSampleTime,
    startLevel = startLevel, endLevel = endLevel, source = enumOrDefault(source, ChargingSource.UNKNOWN),
    sampleCount = sampleCount, powerSampleCount = powerSampleCount, powerSumW = powerSum, peakPowerW = peakPower,
    tempSampleCount = tempSampleCount, tempSumC = tempSum, maxTempC = maximumTemperature,
    startTempC = startTemperature, endTempC = endTemperature, interruptions = interruptions,
    startPrecise = startPrecise, endPrecise = endPrecise, hadMonitoringGap = hadMonitoringGap,
    endReason = enumOrDefault(endReason, SessionEndReason.NONE),
)

fun ChargingSession.toEntity() = ChargingSessionEntity(
    id = id, startTime = startTime, endTime = endTime, lastSampleTime = lastSampleTime,
    startLevel = startLevel, endLevel = endLevel, source = source.name,
    sampleCount = sampleCount, powerSampleCount = powerSampleCount, powerSum = powerSumW, peakPower = peakPowerW,
    tempSampleCount = tempSampleCount, tempSum = tempSumC, maximumTemperature = maxTempC,
    startTemperature = startTempC, endTemperature = endTempC, interruptions = interruptions,
    startPrecise = startPrecise, endPrecise = endPrecise, hadMonitoringGap = hadMonitoringGap,
    endReason = endReason.name, isComplete = isComplete,
)

fun BatteryHealthSnapshotEntity.toDomain() = BatteryHealthSnapshot(
    id = id, timestamp = timestamp,
    reportedHealth = enumOrDefault(reportedHealth, BatteryHealthStatus.UNKNOWN),
    estimatedCapacityMah = estimatedCapacity, designCapacityMah = designCapacity,
    confidence = enumOrDefault(confidenceLevel, Confidence.INSUFFICIENT), sessionId = sessionId,
)

fun BatteryHealthSnapshot.toEntity() = BatteryHealthSnapshotEntity(
    id = id, timestamp = timestamp, reportedHealth = reportedHealth.name,
    estimatedCapacity = estimatedCapacityMah, designCapacity = designCapacityMah,
    confidenceLevel = confidence.name, sessionId = sessionId,
)
