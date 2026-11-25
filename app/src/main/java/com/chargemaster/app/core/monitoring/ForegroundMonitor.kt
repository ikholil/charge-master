package com.chargemaster.app.core.monitoring

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.chargemaster.app.core.common.ApplicationScope
import com.chargemaster.app.domain.repository.BatteryRepository
import com.chargemaster.app.domain.usecase.RecordBatterySampleUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lifecycle-aware live monitoring: samples only while the app is visible and stops
 * automatically when it goes to the background. Persists at most one reading per minute.
 */
@Singleton
class ForegroundMonitor @Inject constructor(
    private val battery: BatteryRepository,
    private val record: RecordBatterySampleUseCase,
    @ApplicationScope private val scope: CoroutineScope,
) : DefaultLifecycleObserver {
    private var job: Job? = null

    fun install() = ProcessLifecycleOwner.get().lifecycle.addObserver(this)

    override fun onStart(owner: LifecycleOwner) {
        job?.cancel()
        job = scope.launch {
            battery.liveSnapshots.collect { snapshot ->
                runCatching { record(snapshot, minPersistIntervalMs = PERSIST_INTERVAL_MS) }
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        job?.cancel(); job = null
    }

    companion object { const val PERSIST_INTERVAL_MS = 60_000L }
}
