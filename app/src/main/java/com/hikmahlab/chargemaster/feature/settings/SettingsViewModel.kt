package com.hikmahlab.chargemaster.feature.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import com.hikmahlab.chargemaster.core.common.IoDispatcher
import com.hikmahlab.chargemaster.domain.repository.SessionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import androidx.lifecycle.viewModelScope
import com.hikmahlab.chargemaster.domain.model.AlertPreferences
import com.hikmahlab.chargemaster.domain.model.CalibrationMode
import com.hikmahlab.chargemaster.domain.model.CurrentCalibration
import com.hikmahlab.chargemaster.domain.model.DashboardCard
import com.hikmahlab.chargemaster.domain.model.MonitoringMode
import com.hikmahlab.chargemaster.domain.model.TemperatureUnit
import com.hikmahlab.chargemaster.domain.model.ThemeMode
import com.hikmahlab.chargemaster.domain.model.UserPreferences
import com.hikmahlab.chargemaster.domain.repository.PreferencesRepository
import com.hikmahlab.chargemaster.domain.usecase.ClearAllDataUseCase
import com.hikmahlab.chargemaster.domain.usecase.PruneHistoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: PreferencesRepository,
    private val clearAll: ClearAllDataUseCase,
    private val prune: PruneHistoryUseCase,
    private val sessions: SessionRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {
    val preferences: StateFlow<UserPreferences> = prefs.preferences.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    /** Whether there is anything to export; the export button is disabled otherwise. */
    val hasSessions: StateFlow<Boolean> = sessions.observeRecent(2).map { list -> list.any { !it.isOpen } } // at most one session is open
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    enum class ExportResult { DONE, EMPTY, FAILED }

    /** Writes all closed charging sessions as CSV to a document the user picked. */
    fun exportSessions(uri: Uri, resolver: ContentResolver, onResult: (ExportResult) -> Unit) {
        viewModelScope.launch {
            val result = withContext(io) {
                runCatching {
                    val list = sessions.recent(Int.MAX_VALUE).filter { !it.isOpen }
                    if (list.isEmpty()) return@runCatching ExportResult.EMPTY
                    val stream = resolver.openOutputStream(uri, "wt") ?: return@runCatching ExportResult.FAILED
                    stream.use { it.write(SessionCsv.build(list).toByteArray(Charsets.UTF_8)) }
                    ExportResult.DONE
                }.getOrDefault(ExportResult.FAILED)
            }
            onResult(result)
        }
    }

    private fun go(block: suspend () -> Unit) { viewModelScope.launch { block() } }

    fun setUnit(u: TemperatureUnit) = go { prefs.setTemperatureUnit(u) }
    fun setTheme(t: ThemeMode) = go { prefs.setThemeMode(t) }
    fun setDynamic(v: Boolean) = go { prefs.setDynamicColor(v) }
    fun setHighContrast(v: Boolean) = go { prefs.setHighContrast(v) }
    fun setReduceMotion(v: Boolean) = go { prefs.setReduceMotion(v) }
    fun setMode(m: MonitoringMode) = go { prefs.setMonitoringMode(m) }
    fun setBackground(v: Boolean) = go { prefs.setBackgroundMonitoring(v) }
    fun setRetention(d: Int) = go { prefs.setRetentionDays(d); prune() }
    fun setUnitMode(m: CalibrationMode) = go { prefs.setCurrentUnitMode(m) }
    fun setSignMode(m: CalibrationMode) = go { prefs.setCurrentSignMode(m) }
    fun resetCalibration() = go { prefs.setCalibration(CurrentCalibration()) }
    fun updateAlerts(t: (AlertPreferences) -> AlertPreferences) = go { prefs.updateAlerts(t) }
    fun clearData() = go { clearAll() }
    fun completeOnboarding() = go { prefs.setOnboardingComplete(true) }
    fun toggleCard(card: DashboardCard, visible: Boolean) = go {
        val current = preferences.value.dashboardCards
        prefs.setDashboardCards(if (visible) current + card else current - card)
    }
}
