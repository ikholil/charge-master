package com.hikmahlab.chargemaster.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "battery_readings", indices = [Index("timestamp")])
data class BatteryReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val batteryLevel: Int?,
    val temperature: Float?,
    val voltage: Int?,
    val currentNow: Float?,
    val currentAverage: Float?,
    val chargeCounter: Float?,
    val chargingStatus: String,
    val chargingSource: String,
    val screenOn: Boolean?,
)

@Entity(tableName = "charging_sessions", indices = [Index("startTime"), Index("endTime")])
data class ChargingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long?,
    val lastSampleTime: Long,
    val startLevel: Int?,
    val endLevel: Int?,
    val source: String,
    val sampleCount: Int,
    val powerSampleCount: Int,
    val powerSum: Double,
    val peakPower: Float?,
    val tempSampleCount: Int,
    val tempSum: Double,
    val maximumTemperature: Float?,
    val startTemperature: Float?,
    val endTemperature: Float?,
    val interruptions: Int,
    val startPrecise: Boolean,
    val endPrecise: Boolean,
    val hadMonitoringGap: Boolean,
    val endReason: String,
    @ColumnInfo(defaultValue = "0") val isComplete: Boolean,
)

@Entity(tableName = "battery_health_snapshots", indices = [Index("timestamp")])
data class BatteryHealthSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val reportedHealth: String,
    val estimatedCapacity: Float?,
    val designCapacity: Int?,
    val confidenceLevel: String,
    val sessionId: Long?,
)
