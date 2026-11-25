package com.chargemaster.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chargemaster.app.domain.model.AlertPreferences
import com.chargemaster.app.domain.model.CalibrationMode
import com.chargemaster.app.domain.model.CurrentCalibration
import com.chargemaster.app.domain.model.MonitoringMode
import com.chargemaster.app.domain.model.TemperatureUnit
import com.chargemaster.app.domain.model.ThemeMode
import com.chargemaster.app.domain.model.UserPreferences
import com.chargemaster.app.domain.repository.PreferencesRepository
import com.chargemaster.app.domain.usecase.ClearAllDataUseCase
import com.chargemaster.app.domain.usecase.PruneHistoryUseCase
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
) : ViewModel() {
    val preferences: StateFlow<UserPreferences> = prefs.preferences.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

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
}
