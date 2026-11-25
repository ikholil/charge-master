package com.chargemaster.app.core.monitoring

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.chargemaster.app.core.common.ApplicationScope
import com.chargemaster.app.domain.repository.BatteryRepository
import com.chargemaster.app.domain.repository.PreferencesRepository
import com.chargemaster.app.domain.usecase.RecordBatterySampleUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Handles restarts and app updates: closes the interrupted session and re-applies scheduling. */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var record: RecordBatterySampleUseCase
    @Inject lateinit var battery: BatteryRepository
    @Inject lateinit var prefs: PreferencesRepository
    @Inject lateinit var scheduler: MonitoringScheduler
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        scope.launch {
            try {
                if (action == Intent.ACTION_BOOT_COMPLETED) record.closeSessionAfterRestart()
                scheduler.apply(prefs.current())
                record(battery.readSnapshot())
            } finally {
                pending.finish()
            }
        }
    }
}
