package com.hikmahlab.chargemaster.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hikmahlab.chargemaster.core.design.ChargeMasterTheme
import com.hikmahlab.chargemaster.domain.model.ThemeMode
import com.hikmahlab.chargemaster.domain.model.UserPreferences
import com.hikmahlab.chargemaster.domain.repository.PreferencesRepository
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

/** AppCompatActivity so per-app language (AppCompatDelegate.setApplicationLocales) works on Android 8–12. */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Keep the splash until preferences load so the correct theme/start screen is shown.
        splash.setKeepOnScreenCondition { vm.preferences.value == null }
        enableEdgeToEdge()
        setContent {
            val prefs = vm.preferences.collectAsStateWithLifecycle().value ?: return@setContent
            val dark = when (prefs.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK, ThemeMode.AMOLED -> true
            }
            // System bar icons follow the in-app theme, not just the system setting.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
                )
                onDispose {}
            }
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

    private companion object {
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
