package com.chargemaster.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BatteryReadingDao {
    @Insert suspend fun insert(reading: BatteryReadingEntity): Long
    @Insert suspend fun insertAll(readings: List<BatteryReadingEntity>)

    @Query("SELECT * FROM battery_readings WHERE timestamp >= :since ORDER BY timestamp ASC")
    fun observeSince(since: Long): Flow<List<BatteryReadingEntity>>

    @Query("SELECT * FROM battery_readings WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp ASC")
    suspend fun between(from: Long, to: Long): List<BatteryReadingEntity>

    @Query("SELECT * FROM battery_readings WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp ASC")
    fun observeBetween(from: Long, to: Long): Flow<List<BatteryReadingEntity>>

    @Query("SELECT * FROM battery_readings ORDER BY timestamp DESC LIMIT 1")
    suspend fun latest(): BatteryReadingEntity?

    @Query("SELECT COUNT(*) FROM battery_readings")
    fun observeCount(): Flow<Int>

    @Query("SELECT MIN(timestamp) FROM battery_readings")
    fun observeOldest(): Flow<Long?>

    @Query("DELETE FROM battery_readings WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long): Int

    @Query("DELETE FROM battery_readings") suspend fun clear()
}

@Dao
interface ChargingSessionDao {
    @Insert suspend fun insert(session: ChargingSessionEntity): Long
    @Update suspend fun update(session: ChargingSessionEntity)

    @Query("DELETE FROM charging_sessions WHERE id = :id") suspend fun delete(id: Long)

    @Query("SELECT * FROM charging_sessions WHERE endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    suspend fun open(): ChargingSessionEntity?

    @Query("SELECT * FROM charging_sessions WHERE endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    fun observeOpen(): Flow<ChargingSessionEntity?>

    @Query("SELECT * FROM charging_sessions WHERE endTime IS NOT NULL ORDER BY endTime DESC LIMIT 1")
    suspend fun lastClosed(): ChargingSessionEntity?

    @Query("SELECT * FROM charging_sessions ORDER BY startTime DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ChargingSessionEntity>>

    @Query("SELECT * FROM charging_sessions WHERE startTime >= :since ORDER BY startTime DESC")
    fun observeSince(since: Long): Flow<List<ChargingSessionEntity>>

    @Query("SELECT * FROM charging_sessions ORDER BY startTime DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<ChargingSessionEntity>

    @Query("SELECT * FROM charging_sessions WHERE id = :id")
    fun observeById(id: Long): Flow<ChargingSessionEntity?>

    @Query("DELETE FROM charging_sessions WHERE startTime < :before AND endTime IS NOT NULL")
    suspend fun deleteOlderThan(before: Long): Int

    @Query("DELETE FROM charging_sessions") suspend fun clear()
}

@Dao
interface BatteryHealthDao {
    @Insert suspend fun insert(snapshot: BatteryHealthSnapshotEntity): Long

    @Query("SELECT * FROM battery_health_snapshots ORDER BY timestamp ASC")
    fun observeAll(): Flow<List<BatteryHealthSnapshotEntity>>

    @Query("DELETE FROM battery_health_snapshots") suspend fun clear()
}
