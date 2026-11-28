package com.hikmahlab.chargemaster.data.repository

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.hikmahlab.chargemaster.core.common.ApplicationScope
import com.hikmahlab.chargemaster.core.common.IoDispatcher
import com.hikmahlab.chargemaster.core.database.BatteryReadingDao
import com.hikmahlab.chargemaster.data.datasource.AndroidBatteryDataSource
import com.hikmahlab.chargemaster.data.local.toDomain
import com.hikmahlab.chargemaster.data.local.toEntity
import com.hikmahlab.chargemaster.domain.engine.SnapshotNormalizer
import com.hikmahlab.chargemaster.domain.model.BatteryReading
import com.hikmahlab.chargemaster.domain.model.BatterySnapshot
import com.hikmahlab.chargemaster.domain.repository.BatteryRepository
import com.hikmahlab.chargemaster.domain.repository.PreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BatteryRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val source: AndroidBatteryDataSource,
    private val dao: BatteryReadingDao,
    private val prefs: PreferencesRepository,
    @ApplicationScope scope: CoroutineScope,
    @IoDispatcher private val io: CoroutineDispatcher,
) : BatteryRepository {

    private val calibrationLock = Mutex()

    private suspend fun normalize(intent: Intent?): BatterySnapshot = calibrationLock.withLock {
        val p = prefs.current()
        val cal = prefs.calibration()
        val result = SnapshotNormalizer.normalize(source.read(intent), cal, p.currentUnitMode, p.currentSignMode)
        if (result.calibration != cal) prefs.setCalibration(result.calibration)
        result.snapshot
    }

    override suspend fun readSnapshot(): BatterySnapshot = withContext(io) { normalize(source.stickyIntent()) }

    /**
     * Event-driven on ACTION_BATTERY_CHANGED, plus a gentle timer (per monitoring mode) because
     * current/power change without a broadcast. Stops entirely 5 s after the last collector leaves.
     */
    override val liveSnapshots: Flow<BatterySnapshot> = callbackFlow {
        val wake = Channel<Intent?>(Channel.CONFLATED)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) { wake.trySend(intent) }
        }
        val sticky = ContextCompat.registerReceiver(
            context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        val job = launch(io) {
            var lastIntent: Intent? = sticky
            while (true) {
                send(normalize(lastIntent))
                val interval = prefs.current().monitoringMode.foregroundIntervalMs
                val next = withTimeoutOrNull(interval) { wake.receive() }
                if (next != null) lastIntent = next
            }
        }
        awaitClose {
            job.cancel()
            runCatching { context.unregisterReceiver(receiver) }
        }
    }.flowOn(io).shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    override fun observeReadingsSince(since: Long) = dao.observeSince(since).map { l -> l.map { it.toDomain() } }
    override fun observeReadingsBetween(from: Long, to: Long) =
        dao.observeBetween(from, to).map { l -> l.map { it.toDomain() } }
    override suspend fun readingsBetween(from: Long, to: Long) = dao.between(from, to).map { it.toDomain() }
    override suspend fun latestReading() = dao.latest()?.toDomain()
    override suspend fun insertReading(reading: BatteryReading) { dao.insert(reading.toEntity()) }
    override fun observeReadingCount() = dao.observeCount().distinctUntilChanged()
    override fun observeOldestReading() = dao.observeOldest()
    override suspend fun pruneReadingsOlderThan(before: Long) = dao.deleteOlderThan(before)
    override suspend fun clearReadings() = dao.clear()
}
