package com.chargemaster.app.feature

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chargemaster.app.core.design.ChargeMasterTheme
import com.chargemaster.app.domain.MIN
import com.chargemaster.app.domain.engine.EstimateMethod
import com.chargemaster.app.domain.engine.TimeEstimate
import com.chargemaster.app.domain.model.ChargingSource
import com.chargemaster.app.domain.model.ThemeMode
import com.chargemaster.app.domain.snapshot
import com.chargemaster.app.feature.dashboard.DashboardContent
import com.chargemaster.app.feature.dashboard.DashboardState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders reference screenshots to app/build/screenshots for visual review.
 * Sample values below are test fixtures, never shown by the app itself.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val state = DashboardState(
        snapshot = snapshot(10 * MIN, level = 78, source = ChargingSource.USB, current = 2850f, temp = 32.5f, voltage = 3980),
        etaFull = TimeEstimate(42, EstimateMethod.SYSTEM),
        eta80 = TimeEstimate(4, EstimateMethod.OBSERVED_RATE),
    )

    private fun shoot(name: String, theme: ThemeMode) {
        compose.setContent {
            ChargeMasterTheme(themeMode = theme, reduceMotion = true) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { DashboardContent(state) }
            }
        }
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test @Config(qualifiers = "w400dp-h900dp-xhdpi") fun dashboardLight() = shoot("dashboard_light", ThemeMode.LIGHT)
    @Test @Config(qualifiers = "w400dp-h900dp-night-xhdpi") fun dashboardDark() = shoot("dashboard_dark", ThemeMode.DARK)
    @Test @Config(qualifiers = "bn-w400dp-h900dp-xhdpi") fun dashboardBangla() = shoot("dashboard_bn", ThemeMode.LIGHT)
}
