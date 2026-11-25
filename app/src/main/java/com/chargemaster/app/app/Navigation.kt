package com.chargemaster.app.app

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.ElectricBolt
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chargemaster.app.R
import com.chargemaster.app.feature.batteryhealth.HealthScreen
import com.chargemaster.app.feature.charging.ChargingHistoryScreen
import com.chargemaster.app.feature.charging.ChargingScreen
import com.chargemaster.app.feature.charging.SessionDetailScreen
import com.chargemaster.app.feature.dashboard.DashboardScreen
import com.chargemaster.app.feature.diagnostics.DiagnosticsScreen
import com.chargemaster.app.feature.diagnostics.ReportScreen
import com.chargemaster.app.feature.insights.InsightsScreen
import com.chargemaster.app.feature.onboarding.OnboardingScreen
import com.chargemaster.app.feature.settings.NotificationSettingsScreen
import com.chargemaster.app.feature.settings.PrivacyScreen
import com.chargemaster.app.feature.settings.SettingsScreen
import com.chargemaster.app.feature.settings.WidgetsScreen
import com.chargemaster.app.feature.temperature.TemperatureScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val OVERVIEW = "overview"
    const val CHARGING = "charging"
    const val HEALTH = "health"
    const val INSIGHTS = "insights"
    const val SETTINGS = "settings"
    const val HISTORY = "history"
    const val SESSION = "session/{id}"
    fun session(id: Long) = "session/$id"
    const val TEMPERATURE = "temperature"
    const val DIAGNOSTICS = "diagnostics"
    const val REPORT = "report"
    const val WIDGETS = "widgets"
    const val NOTIFICATIONS = "notifications"
    const val PRIVACY = "privacy"
}

private enum class TopLevel(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    OVERVIEW(Routes.OVERVIEW, R.string.nav_overview, Icons.Outlined.BatteryChargingFull),
    CHARGING(Routes.CHARGING, R.string.nav_charging, Icons.Outlined.ElectricBolt),
    HEALTH(Routes.HEALTH, R.string.nav_health, Icons.Outlined.HealthAndSafety),
    INSIGHTS(Routes.INSIGHTS, R.string.nav_insights, Icons.Outlined.Insights),
    SETTINGS(Routes.SETTINGS, R.string.nav_settings, Icons.Outlined.Settings),
}

@Composable
fun ChargeMasterNavHost(startOnboarding: Boolean, nav: NavHostController = rememberNavController()) {
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val showBar = TopLevel.entries.any { it.route == current }
    Scaffold(
        bottomBar = {
            if (showBar) NavigationBar {
                TopLevel.entries.forEach { item ->
                    NavigationBarItem(
                        selected = current == item.route,
                        onClick = {
                            nav.navigate(item.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(item.icon, null) },
                        label = { Text(stringResource(item.label)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = if (startOnboarding) Routes.ONBOARDING else Routes.OVERVIEW,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
        ) {
            val back: () -> Unit = { nav.popBackStack() }
            composable(Routes.ONBOARDING) {
                OnboardingScreen(onDone = {
                    nav.navigate(Routes.OVERVIEW) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                })
            }
            composable(Routes.OVERVIEW) {
                DashboardScreen(
                    onOpenCharging = { nav.navigate(Routes.CHARGING) { launchSingleTop = true } },
                    onOpenTemperature = { nav.navigate(Routes.TEMPERATURE) },
                    onOpenHealth = { nav.navigate(Routes.HEALTH) { launchSingleTop = true } },
                )
            }
            composable(Routes.CHARGING) {
                ChargingScreen(onOpenHistory = { nav.navigate(Routes.HISTORY) }, onOpenSession = { nav.navigate(Routes.session(it)) })
            }
            composable(Routes.HISTORY) { ChargingHistoryScreen(onBack = back, onOpenSession = { nav.navigate(Routes.session(it)) }) }
            composable(Routes.SESSION, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                SessionDetailScreen(onBack = back)
            }
            composable(Routes.HEALTH) {
                HealthScreen(onOpenDiagnostics = { nav.navigate(Routes.DIAGNOSTICS) }, onOpenTemperature = { nav.navigate(Routes.TEMPERATURE) })
            }
            composable(Routes.INSIGHTS) {
                InsightsScreen(
                    onOpenTemperature = { nav.navigate(Routes.TEMPERATURE) },
                    onOpenHistory = { nav.navigate(Routes.HISTORY) },
                    onOpenReport = { nav.navigate(Routes.REPORT) },
                )
            }
            composable(Routes.TEMPERATURE) { TemperatureScreen(onBack = back) }
            composable(Routes.DIAGNOSTICS) { DiagnosticsScreen(onBack = back, onOpenReport = { nav.navigate(Routes.REPORT) }) }
            composable(Routes.REPORT) { ReportScreen(onBack = back) }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onOpenNotifications = { nav.navigate(Routes.NOTIFICATIONS) },
                    onOpenWidgets = { nav.navigate(Routes.WIDGETS) },
                    onOpenPrivacy = { nav.navigate(Routes.PRIVACY) },
                    onOpenDiagnostics = { nav.navigate(Routes.DIAGNOSTICS) },
                )
            }
            composable(Routes.NOTIFICATIONS) { NotificationSettingsScreen(onBack = back) }
            composable(Routes.WIDGETS) { WidgetsScreen(onBack = back) }
            composable(Routes.PRIVACY) { PrivacyScreen(onBack = back) }
        }
    }
}
