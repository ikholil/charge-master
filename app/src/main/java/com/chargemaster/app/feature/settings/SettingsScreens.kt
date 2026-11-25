package com.chargemaster.app.feature.settings

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.chargemaster.app.domain.model.DashboardCard
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chargemaster.app.BuildConfig
import com.chargemaster.app.R
import com.chargemaster.app.core.common.Fmt
import com.chargemaster.app.core.design.AppCard
import com.chargemaster.app.core.design.InfoBanner
import com.chargemaster.app.core.design.NavRow
import com.chargemaster.app.core.design.ScreenScaffold
import com.chargemaster.app.core.design.SectionHeader
import com.chargemaster.app.domain.model.CalibrationMode
import com.chargemaster.app.domain.model.MonitoringMode
import com.chargemaster.app.domain.model.TemperatureUnit
import com.chargemaster.app.domain.model.ThemeMode
import com.chargemaster.app.feature.alerts.NotificationChannels
import com.chargemaster.app.feature.widgets.ChargingBatteryWidgetReceiver
import com.chargemaster.app.feature.widgets.DetailedBatteryWidgetReceiver
import com.chargemaster.app.feature.widgets.MinimalBatteryWidgetReceiver

@Composable
fun SettingsScreen(
    onOpenNotifications: () -> Unit,
    onOpenWidgets: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val p by vm.preferences.collectAsStateWithLifecycle()
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    ScreenScaffold(title = stringResource(R.string.nav_settings)) {
        item {
            AppCard {
                NavRow(stringResource(R.string.notification_settings), stringResource(R.string.notification_settings_subtitle), Icons.Outlined.Notifications, onOpenNotifications)
                NavRow(stringResource(R.string.widgets), stringResource(R.string.widgets_subtitle), Icons.Outlined.Widgets, onOpenWidgets)
                NavRow(stringResource(R.string.diagnostics), stringResource(R.string.diagnostics_subtitle), Icons.Outlined.MonitorHeart, onOpenDiagnostics)
                NavRow(stringResource(R.string.privacy), stringResource(R.string.privacy_subtitle), Icons.Outlined.PrivacyTip, onOpenPrivacy)
            }
        }
        item {
            AppCard {
                SectionHeader(stringResource(R.string.monitoring))
                RadioGroup(
                    MonitoringMode.entries, p.monitoringMode,
                    label = { stringResource(when (it) { MonitoringMode.BASIC -> R.string.mode_basic; MonitoringMode.BALANCED -> R.string.mode_balanced; MonitoringMode.DETAILED -> R.string.mode_detailed }) },
                    description = { stringResource(when (it) { MonitoringMode.BASIC -> R.string.mode_basic_desc; MonitoringMode.BALANCED -> R.string.mode_balanced_desc; MonitoringMode.DETAILED -> R.string.mode_detailed_desc }) },
                    onSelect = vm::setMode,
                )
                SwitchRow(stringResource(R.string.background_monitoring), stringResource(R.string.background_monitoring_desc), p.backgroundMonitoring, vm::setBackground)
                InfoBanner(stringResource(R.string.monitoring_tradeoff))
            }
        }
        item {
            AppCard {
                SectionHeader(stringResource(R.string.language))
                val current = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-').ifEmpty { "" }
                var selected by rememberSaveable { mutableStateOf(current) }
                RadioGroup(
                    listOf("", "en", "bn"), selected,
                    label = { tag -> when (tag) { "en" -> "English"; "bn" -> "বাংলা"; else -> stringResource(R.string.language_system) } },
                    onSelect = { tag ->
                        selected = tag
                        AppCompatDelegate.setApplicationLocales(
                            if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag),
                        )
                    },
                )
            }
        }
        item {
            AppCard {
                SectionHeader(stringResource(R.string.dashboard_cards))
                Text(stringResource(R.string.dashboard_cards_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                DashboardCard.entries.forEach { card ->
                    SwitchRow(stringResource(card.labelRes()), null, card in p.dashboardCards) { vm.toggleCard(card, it) }
                }
            }
        }
        item {
            AppCard {
                SectionHeader(stringResource(R.string.units))
                RadioGroup(
                    TemperatureUnit.entries, p.temperatureUnit,
                    label = { stringResource(if (it == TemperatureUnit.CELSIUS) R.string.celsius else R.string.fahrenheit) },
                    onSelect = vm::setUnit,
                )
            }
        }
        item {
            AppCard {
                SectionHeader(stringResource(R.string.appearance))
                RadioGroup(
                    ThemeMode.entries, p.themeMode,
                    label = { stringResource(when (it) { ThemeMode.SYSTEM -> R.string.theme_system; ThemeMode.LIGHT -> R.string.theme_light; ThemeMode.DARK -> R.string.theme_dark; ThemeMode.AMOLED -> R.string.theme_amoled }) },
                    onSelect = vm::setTheme,
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SwitchRow(stringResource(R.string.dynamic_color), stringResource(R.string.dynamic_color_desc), p.dynamicColor, vm::setDynamic)
                }
                SwitchRow(stringResource(R.string.high_contrast), null, p.highContrast, vm::setHighContrast)
                SwitchRow(stringResource(R.string.reduce_motion), stringResource(R.string.reduce_motion_desc), p.reduceMotion, vm::setReduceMotion)
            }
        }
        item {
            AppCard {
                SectionHeader(stringResource(R.string.current_interpretation))
                Text(stringResource(R.string.current_interpretation_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.current_unit), style = MaterialTheme.typography.labelLarge)
                RadioGroup(
                    CalibrationMode.entries, p.currentUnitMode,
                    label = { stringResource(when (it) { CalibrationMode.AUTO -> R.string.auto_detect; CalibrationMode.FORCE_PRIMARY -> R.string.unit_microamps; CalibrationMode.FORCE_SECONDARY -> R.string.unit_milliamps }) },
                    onSelect = vm::setUnitMode,
                )
                Text(stringResource(R.string.current_sign), style = MaterialTheme.typography.labelLarge)
                RadioGroup(
                    CalibrationMode.entries, p.currentSignMode,
                    label = { stringResource(when (it) { CalibrationMode.AUTO -> R.string.auto_detect; CalibrationMode.FORCE_PRIMARY -> R.string.sign_standard; CalibrationMode.FORCE_SECONDARY -> R.string.sign_inverted }) },
                    onSelect = vm::setSignMode,
                )
                TextButton(onClick = vm::resetCalibration) { Text(stringResource(R.string.reset_detection)) }
            }
        }
        item {
            AppCard {
                SectionHeader(stringResource(R.string.data))
                val options = listOf(7, 30, 90, 365)
                RadioGroup(options, p.retentionDays.takeIf { it in options } ?: 30,
                    label = { pluralStringResource(R.plurals.retention_days, it, it) }, onSelect = vm::setRetention)
                Text(stringResource(R.string.retention_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { confirmClear = true }) { Text(stringResource(R.string.clear_history)) }
            }
        }
        item {
            Text(stringResource(R.string.version_info, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text(stringResource(R.string.clear_history)) },
        text = { Text(stringResource(R.string.clear_history_confirm)) },
        confirmButton = { TextButton(onClick = { vm.clearData(); confirmClear = false }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun DashboardCard.labelRes() = when (this) {
    DashboardCard.ESTIMATE -> R.string.card_estimate
    DashboardCard.TEMPERATURE -> R.string.metric_temperature
    DashboardCard.VOLTAGE -> R.string.metric_voltage
    DashboardCard.POWER -> R.string.metric_power
    DashboardCard.CURRENT -> R.string.metric_current
    DashboardCard.ANDROID_HEALTH -> R.string.metric_android_health
    DashboardCard.THERMAL -> R.string.metric_thermal
    DashboardCard.LAST_CHARGE -> R.string.last_charge
    DashboardCard.QUICK_LINKS -> R.string.card_quick_links
}

@Composable
fun NotificationSettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val p by vm.preferences.collectAsStateWithLifecycle()
    val a = p.alerts
    val context = LocalContext.current
    var granted by rememberSaveable { mutableStateOf(NotificationChannels.hasPermission(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val u = p.temperatureUnit
    ScreenScaffold(title = stringResource(R.string.notification_settings), onBack = onBack) {
        if (!granted) item {
            AppCard {
                Text(stringResource(R.string.notifications_disabled), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                }) { Text(stringResource(R.string.allow_notifications)) }
            }
        }
        item {
            AppCard {
                SectionHeader(stringResource(R.string.battery_alerts))
                SwitchRow(stringResource(R.string.alert_low), null, a.lowBatteryEnabled) { v -> vm.updateAlerts { it.copy(lowBatteryEnabled = v) } }
                IntSliderRow(stringResource(R.string.threshold), a.lowBatteryThreshold, 5..50, a.lowBatteryEnabled, Fmt::percent) { v -> vm.updateAlerts { it.copy(lowBatteryThreshold = v) } }
            }
        }
        item {
            AppCard {
                SectionHeader(stringResource(R.string.charging_alerts))
                SwitchRow(stringResource(R.string.alert_threshold_n, Fmt.percent(a.threshold80)), stringResource(R.string.alert_80_desc), a.threshold80Enabled) { v -> vm.updateAlerts { it.copy(threshold80Enabled = v) } }
                IntSliderRow(stringResource(R.string.threshold), a.threshold80, 50..95, a.threshold80Enabled, Fmt::percent) { v -> vm.updateAlerts { it.copy(threshold80 = v) } }
                SwitchRow(stringResource(R.string.alert_threshold_n, Fmt.percent(a.threshold90)), null, a.threshold90Enabled) { v -> vm.updateAlerts { it.copy(threshold90Enabled = v) } }
                IntSliderRow(stringResource(R.string.threshold), a.threshold90, 50..99, a.threshold90Enabled, Fmt::percent) { v -> vm.updateAlerts { it.copy(threshold90 = v) } }
                SwitchRow(stringResource(R.string.alert_full), null, a.fullEnabled) { v -> vm.updateAlerts { it.copy(fullEnabled = v) } }
                SwitchRow(stringResource(R.string.alert_complete), stringResource(R.string.alert_complete_desc), a.chargingCompleteEnabled) { v -> vm.updateAlerts { it.copy(chargingCompleteEnabled = v) } }
                SwitchRow(stringResource(R.string.alert_started), null, a.chargingStartedEnabled) { v -> vm.updateAlerts { it.copy(chargingStartedEnabled = v) } }
                SwitchRow(stringResource(R.string.alert_stopped), stringResource(R.string.alert_stopped_desc), a.chargingStoppedEnabled) { v -> vm.updateAlerts { it.copy(chargingStoppedEnabled = v) } }
                SwitchRow(stringResource(R.string.alert_long), null, a.longChargeEnabled) { v -> vm.updateAlerts { it.copy(longChargeEnabled = v) } }
                IntSliderRow(stringResource(R.string.hours), a.longChargeHours, 1..24, a.longChargeEnabled, { context.getString(R.string.n_hours, it) }) { v -> vm.updateAlerts { it.copy(longChargeHours = v) } }
                SwitchRow(stringResource(R.string.alert_unusual), stringResource(R.string.alert_unusual_desc), a.unusualSpeedEnabled) { v -> vm.updateAlerts { it.copy(unusualSpeedEnabled = v) } }
            }
        }
        item {
            AppCard {
                SectionHeader(stringResource(R.string.temperature_alerts))
                SwitchRow(stringResource(R.string.alert_temp), null, a.highTempEnabled) { v -> vm.updateAlerts { it.copy(highTempEnabled = v) } }
                IntSliderRow(stringResource(R.string.threshold), a.highTempThresholdC.toInt(), 35..55, a.highTempEnabled, { Fmt.temperature(it.toFloat(), u) }) { v ->
                    vm.updateAlerts { it.copy(highTempThresholdC = v.toFloat()) }
                }
            }
        }
        item { InfoBanner(stringResource(R.string.no_charge_control)) }
    }
}

@Composable
fun WidgetsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val manager = AppWidgetManager.getInstance(context)
    val canPin = manager.isRequestPinAppWidgetSupported
    ScreenScaffold(title = stringResource(R.string.widgets), onBack = onBack) {
        item { InfoBanner(stringResource(R.string.widgets_explainer)) }
        listOf(
            Triple(R.string.widget_minimal, R.string.widget_minimal_desc, MinimalBatteryWidgetReceiver::class.java),
            Triple(R.string.widget_detailed, R.string.widget_detailed_desc, DetailedBatteryWidgetReceiver::class.java),
            Triple(R.string.widget_charging, R.string.widget_charging_desc, ChargingBatteryWidgetReceiver::class.java),
        ).forEach { (title, desc, cls) ->
            item {
                AppCard {
                    Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    if (canPin) OutlinedButton(onClick = { manager.requestPinAppWidget(ComponentName(context, cls), null, null) }) {
                        Text(stringResource(R.string.add_to_home))
                    } else Text(stringResource(R.string.widget_manual_add), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            AppCard {
                Text(stringResource(R.string.quick_tile), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.quick_tile_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    ScreenScaffold(title = stringResource(R.string.privacy), onBack = onBack) {
        listOf(
            R.string.privacy_offline_title to R.string.privacy_offline_body,
            R.string.privacy_data_title to R.string.privacy_data_body,
            R.string.privacy_permissions_title to R.string.privacy_permissions_body,
            R.string.privacy_sharing_title to R.string.privacy_sharing_body,
            R.string.privacy_limits_title to R.string.privacy_limits_body,
        ).forEach { (t, b) ->
            item {
                AppCard {
                    Text(stringResource(t), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(b), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
