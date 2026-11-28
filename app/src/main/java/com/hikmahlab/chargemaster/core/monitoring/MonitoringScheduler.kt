package com.hikmahlab.chargemaster.core.monitoring

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.hikmahlab.chargemaster.domain.model.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MonitoringScheduler @Inject constructor(@ApplicationContext private val context: Context) {
    private val wm get() = WorkManager.getInstance(context)

    fun apply(prefs: UserPreferences) {
        wm.enqueueUniquePeriodicWork(
            MAINTENANCE, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<MaintenanceWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build(),
        )
        if (!prefs.backgroundMonitoring) {
            wm.cancelUniqueWork(PERIODIC); wm.cancelUniqueWork(CHARGING)
            return
        }
        wm.enqueueUniquePeriodicWork(
            PERIODIC, ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<BatterySampleWorker>(prefs.monitoringMode.backgroundIntervalMinutes, TimeUnit.MINUTES)
                .build(),
        )
        val chargingInterval = prefs.monitoringMode.chargingIntervalMinutes
        if (chargingInterval == null) wm.cancelUniqueWork(CHARGING) else enqueueChargingSampler(chargingInterval, append = false)
    }

    fun enqueueChargingSampler(intervalMinutes: Long, append: Boolean) {
        val request = OneTimeWorkRequestBuilder<ChargingSampleWorker>()
            .setInitialDelay(intervalMinutes, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiresCharging(true).build())
            .build()
        wm.enqueueUniqueWork(
            CHARGING,
            if (append) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    companion object {
        const val PERIODIC = "battery_periodic_sample"
        const val CHARGING = "battery_charging_sample"
        const val MAINTENANCE = "history_maintenance"
    }
}
