package com.hikmahlab.chargemaster.core.monitoring

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hikmahlab.chargemaster.domain.repository.BatteryRepository
import com.hikmahlab.chargemaster.domain.repository.PreferencesRepository
import com.hikmahlab.chargemaster.domain.usecase.PruneHistoryUseCase
import com.hikmahlab.chargemaster.domain.usecase.RecordBatterySampleUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Periodic background sample (15–60 min, depending on monitoring mode). */
@HiltWorker
class BatterySampleWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val battery: BatteryRepository,
    private val record: RecordBatterySampleUseCase,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching {
        record(battery.readSnapshot())
        Result.success()
    }.getOrElse { Result.retry() }
}

/**
 * Runs only while the device is charging (WorkManager charging constraint), then re-enqueues
 * itself. This gives finer charging-session detail without a persistent foreground service;
 * the cost is negligible because the device is on external power.
 */
@HiltWorker
class ChargingSampleWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val battery: BatteryRepository,
    private val record: RecordBatterySampleUseCase,
    private val prefs: PreferencesRepository,
    private val scheduler: MonitoringScheduler,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { record(battery.readSnapshot()) }
        val p = prefs.current()
        val interval = p.monitoringMode.chargingIntervalMinutes
        if (p.backgroundMonitoring && interval != null) scheduler.enqueueChargingSampler(interval, append = true)
        return Result.success()
    }
}

/** Daily housekeeping: applies the data retention setting. */
@HiltWorker
class MaintenanceWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val prune: PruneHistoryUseCase,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching { prune(); Result.success() }.getOrElse { Result.retry() }
}
