package com.chargemaster.app.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.chargemaster.app.core.design.ChargeMasterTheme
import com.chargemaster.app.domain.model.UserPreferences
import com.chargemaster.app.domain.repository.PreferencesRepository
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(prefs: PreferencesRepository) : ViewModel() {
    val preferences: StateFlow<UserPreferences?> =
        prefs.preferences.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Keep the splash until preferences load so the correct theme/start screen is shown.
        splash.setKeepOnScreenCondition { vm.preferences.value == null }
        enableEdgeToEdge()
        setContent {
            val prefs = vm.preferences.collectAsStateWithLifecycle().value ?: return@setContent
            ChargeMasterTheme(
                themeMode = prefs.themeMode,
                dynamicColor = prefs.dynamicColor,
                highContrast = prefs.highContrast,
                reduceMotion = prefs.reduceMotion,
            ) {
                ChargeMasterNavHost(startOnboarding = !prefs.onboardingComplete)
            }
        }
    }
}
