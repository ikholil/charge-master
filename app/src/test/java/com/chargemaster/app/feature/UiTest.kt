package com.chargemaster.app.feature

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chargemaster.app.core.design.ChargeMasterTheme
import com.chargemaster.app.core.design.MetricCard
import com.chargemaster.app.core.design.ValueKind
import com.chargemaster.app.domain.model.ChargingSource
import com.chargemaster.app.domain.snapshot
import com.chargemaster.app.feature.dashboard.DashboardContent
import com.chargemaster.app.feature.dashboard.DashboardState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun metricCardShowsUnavailableInsteadOfNumber() {
        compose.setContent { ChargeMasterTheme { MetricCard("Current", null, Icons.Outlined.Bolt, kind = ValueKind.LIVE, unavailableText = "Not supported") } }
        compose.onNodeWithText("Not supported").assertIsDisplayed()
        compose.onNodeWithContentDescription("Current: Not supported. Unavailable").assertExists()
    }

    @Test fun dashboardShowsLiveValuesAndUnsupportedSensors() {
        val s = snapshot(0, level = 78, source = ChargingSource.USB, current = null, temp = 32.5f, voltage = 3980)
        compose.setContent { ChargeMasterTheme(reduceMotion = true) { DashboardContent(DashboardState(snapshot = s)) } }
        compose.onAllNodesWithText("78%").fetchSemanticsNodes().let { assertTrue(it.isNotEmpty()) }
        val list = compose.onNode(hasScrollAction())
        list.performScrollToNode(hasText("32.5°C"))
        compose.onNodeWithText("32.5°C").assertExists()
        compose.onNodeWithText("3.98 V").assertExists()
        list.performScrollToNode(hasText("Device thermal status"))
        // Current and power are not supported on this simulated device → labelled, not invented.
        assertTrue(compose.onAllNodesWithText("Not supported").fetchSemanticsNodes().size >= 2)
    }
}
