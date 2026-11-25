package com.chargemaster.app.domain.usecase

import com.chargemaster.app.core.common.Clock
import com.chargemaster.app.domain.repository.BatteryRepository
import com.chargemaster.app.domain.repository.HealthRepository
import com.chargemaster.app.domain.repository.PreferencesRepository
import com.chargemaster.app.domain.repository.SessionRepository
import javax.inject.Inject

/** Applies the user's data-retention setting. Sessions are kept 12× longer than raw readings. */
class PruneHistoryUseCase @Inject constructor(
    private val battery: BatteryRepository,
    private val sessions: SessionRepository,
    private val prefs: PreferencesRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke() {
        val days = prefs.current().retentionDays.coerceAtLeast(1)
        val now = clock.now()
        battery.pruneReadingsOlderThan(now - days * DAY_MS)
        sessions.pruneOlderThan(now - (days * 12L).coerceAtMost(730) * DAY_MS)
    }

    companion object { const val DAY_MS = 86_400_000L }
}

class ClearAllDataUseCase @Inject constructor(
    private val battery: BatteryRepository,
    private val sessions: SessionRepository,
    private val health: HealthRepository,
) {
    suspend operator fun invoke() {
        battery.clearReadings(); sessions.clear(); health.clear()
    }
}
