package com.chargemaster.app.domain.usecase

import com.chargemaster.app.domain.engine.AlertEvaluator
import com.chargemaster.app.domain.engine.BatteryAlert
import com.chargemaster.app.domain.engine.CapacityEstimator
import com.chargemaster.app.domain.engine.ChargingAnalyzer
import com.chargemaster.app.domain.engine.SessionAccumulator
import com.chargemaster.app.domain.model.BatteryHealthSnapshot
import com.chargemaster.app.domain.model.BatteryReading
import com.chargemaster.app.domain.model.BatterySnapshot
import com.chargemaster.app.domain.model.ChargingSession
import com.chargemaster.app.domain.model.Confidence
import com.chargemaster.app.domain.repository.BatteryRepository
import com.chargemaster.app.domain.repository.HealthRepository
import com.chargemaster.app.domain.repository.PreferencesRepository
import com.chargemaster.app.domain.repository.SessionRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Delivers alerts to the user (implemented with Android notifications). */
fun interface AlertSink { suspend fun deliver(alert: BatteryAlert) }

/** Refreshes home-screen widgets. */
fun interface WidgetRefresher { suspend fun refresh() }

/**
 * The single pipeline every sample passes through, whether it comes from the live foreground
 * monitor or a background worker: persist → session tracking → capacity estimate → alerts → widgets.
 */
@Singleton
class RecordBatterySampleUseCase @Inject constructor(
    private val battery: BatteryRepository,
    private val sessions: SessionRepository,
    private val health: HealthRepository,
    private val prefs: PreferencesRepository,
    private val alertSink: AlertSink,
    private val widgets: WidgetRefresher,
) {
    private val mutex = Mutex()
    private val accumulator = SessionAccumulator()
    private var lastSnapshot: BatterySnapshot? = null
    private var lastPersistedAt: Long? = null
    private var lastWidgetRefreshAt = 0L

    /**
     * @param minPersistIntervalMs readings closer together than this are not stored unless the
     * plug state changed (keeps the database small while the UI samples quickly).
     */
    suspend operator fun invoke(snapshot: BatterySnapshot, minPersistIntervalMs: Long = 0) = mutex.withLock {
        val previous = lastSnapshot ?: battery.latestReading()?.toSnapshotStub()
        val plugChanged = previous != null && previous.isPluggedIn != snapshot.isPluggedIn
        val persist = plugChanged || lastPersistedAt.let { it == null || snapshot.timestamp - it >= minPersistIntervalMs }
        if (!persist) {
            // Fast UI samples are display-only; history, sessions and alerts use persisted samples.
            lastSnapshot = snapshot
            return@withLock
        }
        battery.insertReading(BatteryReading.from(snapshot))
        lastPersistedAt = snapshot.timestamp

        var open = sessions.openSession()
        run {
            val update = accumulator.onSample(open, previous, sessions.lastClosedSession(), snapshot)
            open = when (update) {
                is SessionAccumulator.Update.Started -> update.session.copy(id = sessions.save(update.session))
                is SessionAccumulator.Update.Updated -> update.session.also { sessions.save(it) }
                is SessionAccumulator.Update.Resumed -> update.session.also { sessions.save(it) }
                is SessionAccumulator.Update.Closed -> { sessions.save(update.session); onSessionClosed(update.session); null }
                is SessionAccumulator.Update.Discarded -> { sessions.delete(update.session.id); null }
                SessionAccumulator.Update.None -> open
            }
        }

        val p = prefs.current()
        val typical = if (snapshot.isPluggedIn) ChargingAnalyzer.typicalPower(sessions.recent(30), snapshot.source) else null
        val oldState = prefs.alertState()
        val result = AlertEvaluator.evaluate(oldState, snapshot, p.alerts, open, typical)
        if (result.state != oldState) prefs.setAlertState(result.state)
        result.alerts.forEach { alertSink.deliver(it) }

        lastSnapshot = snapshot
        if (plugChanged || snapshot.timestamp - lastWidgetRefreshAt >= WIDGET_REFRESH_MS) {
            lastWidgetRefreshAt = snapshot.timestamp
            runCatching { widgets.refresh() }
        }
    }

    /** Called on boot: a session can't continue across a restart we didn't observe. */
    suspend fun closeSessionAfterRestart() = mutex.withLock {
        sessions.openSession()?.let { open ->
            val closed = accumulator.closeForRestart(open)
            sessions.save(closed)
            onSessionClosed(closed)
        }
        lastSnapshot = null
    }

    private suspend fun onSessionClosed(session: ChargingSession) {
        val end = session.endTime ?: return
        val readings = battery.readingsBetween(session.startTime, end)
        val estimate = CapacityEstimator.estimateFromReadings(readings)
        if (estimate.capacityMah != null && estimate.confidence != Confidence.INSUFFICIENT) {
            health.insert(
                BatteryHealthSnapshot(
                    timestamp = end,
                    reportedHealth = lastSnapshot?.health ?: com.chargemaster.app.domain.model.BatteryHealthStatus.UNKNOWN,
                    estimatedCapacityMah = estimate.capacityMah,
                    designCapacityMah = prefs.current().designCapacityMah,
                    confidence = estimate.confidence,
                    sessionId = session.id,
                ),
            )
        }
    }

    private fun BatteryReading.toSnapshotStub() = BatterySnapshot(
        timestamp = timestamp, level = level, status = status, source = source,
        health = com.chargemaster.app.domain.model.BatteryHealthStatus.UNKNOWN,
        temperatureC = temperatureC, voltageMv = voltageMv, currentNowMa = currentNowMa,
        currentAverageMa = currentAverageMa, currentCalibrationUncertain = false,
        chargeCounterMah = chargeCounterMah, energyCounterMwh = null, technology = null, present = null,
        cycleCount = null, screenOn = screenOn, systemChargeTimeRemainingMs = null,
        thermalStatus = com.chargemaster.app.domain.model.ThermalStatus.UNSUPPORTED,
    )

    companion object { private const val WIDGET_REFRESH_MS = 60_000L }
}
