package com.chargemaster.app.app

import android.app.Application
import android.os.StrictMode
import com.chargemaster.app.BuildConfig
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.chargemaster.app.core.common.ApplicationScope
import com.chargemaster.app.core.monitoring.ForegroundMonitor
import com.chargemaster.app.core.monitoring.MonitoringScheduler
import com.chargemaster.app.domain.repository.PreferencesRepository
import com.chargemaster.app.feature.alerts.NotificationChannels
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ChargeMasterApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var foregroundMonitor: ForegroundMonitor
    @Inject lateinit var scheduler: MonitoringScheduler
    @Inject lateinit var prefs: PreferencesRepository
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            // Surface accidental disk/network work on the main thread during development.
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectDiskWrites().detectNetwork().penaltyLog().build())
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectLeakedClosableObjects().detectLeakedSqlLiteObjects().penaltyLog().build())
        }
        NotificationChannels.create(this)
        foregroundMonitor.install()
        // Re-apply background scheduling whenever the relevant settings change.
        scope.launch {
            prefs.preferences
                .map { it.monitoringMode to it.backgroundMonitoring }
                .distinctUntilChanged()
                .collect { scheduler.apply(prefs.current()) }
        }
    }
}
