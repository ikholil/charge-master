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
import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.ToggleOn
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.ui.text.style.TextAlign
import com.chargemaster.app.core.design.CardHeader
import com.chargemaster.app.core.design.Tokens
import java.time.LocalDate
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
    val hasSessions by vm.hasSessions.collectAsStateWithLifecycle()
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val exportMessages = mapOf(
        SettingsViewModel.ExportResult.DONE to stringResource(R.string.export_done),
        SettingsViewModel.ExportResult.EMPTY to stringResource(R.string.export_empty),
        SettingsViewModel.ExportResult.FAILED to stringResource(R.string.export_failed),
    )
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.exportSessions(uri, context.contentResolver) { r ->
            Toast.makeText(context, exportMessages.getValue(r), Toast.LENGTH_SHORT).show()
        }
    }
    ScreenScaffold(title = stringResource(R.string.nav_settings)) {
        item {
            SettingsSection(stringResource(R.string.general), padded = false) {
                NavRow(stringResource(R.string.notification_settings), stringResource(R.string.notification_settings_subtitle), Icons.Outlined.Notifications, onOpenNotifications)
                NavRow(stringResource(R.string.widgets), stringResource(R.string.widgets_subtitle), Icons.Outlined.Widgets, onOpenWidgets, tint = Tokens.Info)
                NavRow(stringResource(R.string.diagnostics), stringResource(R.string.diagnostics_subtitle), Icons.Outlined.MonitorHeart, onOpenDiagnostics, tint = Tokens.Warning)
                NavRow(stringResource(R.string.privacy), stringResource(R.string.privacy_subtitle), Icons.Outlined.PrivacyTip, onOpenPrivacy, tint = MaterialTheme.colorScheme.secondary)
            }
        }
        item {
            SettingsSection(stringResource(R.string.monitoring)) {
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
            SettingsSection(stringResource(R.string.language)) {
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
            SettingsSection(stringResource(R.string.dashboard_cards)) {
                Text(stringResource(R.string.dashboard_cards_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                DashboardCard.entries.forEach { card ->
                    SwitchRow(stringResource(card.labelRes()), null, card in p.dashboardCards) { vm.toggleCard(card, it) }
                }
            }
        }
        item {
            SettingsSection(stringResource(R.string.units)) {
                SegmentedChoice(
                    TemperatureUnit.entries, p.temperatureUnit,
                    label = { stringResource(if (it == TemperatureUnit.CELSIUS) R.string.celsius else R.string.fahrenheit) },
                    onSelect = vm::setUnit,
                )
            }
        }
        item {
            SettingsSection(stringResource(R.string.appearance)) {
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
            SettingsSection(stringResource(R.string.current_interpretation)) {
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
            SettingsSection(stringResource(R.string.data)) {
                val options = listOf(7, 30, 90, 365)
                RadioGroup(options, p.retentionDays.takeIf { it in options } ?: 30,
                    label = { pluralStringResource(R.plurals.retention_days, it, it) }, onSelect = vm::setRetention)
                Text(stringResource(R.string.retention_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(4.dp))
                NavRow(
                    stringResource(R.string.export_csv),
                    stringResource(if (hasSessions) R.string.export_csv_desc else R.string.export_empty),
                    Icons.Outlined.FileDownload,
                    onClick = {
                        if (hasSessions) exportLauncher.launch("charge-master-sessions-" + LocalDate.now() + ".csv")
                        else Toast.makeText(context, exportMessages.getValue(SettingsViewModel.ExportResult.EMPTY), Toast.LENGTH_SHORT).show()
                    },
                    tint = if (hasSessions) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { confirmClear = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Outlined.DeleteOutline, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.clear_history))
                }
            }
        }
        item {
            Text(stringResource(R.string.version_info, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
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
            AppCard(containerColor = Tokens.Warning.copy(alpha = 0.10f)) {
                Text(stringResource(R.string.notifications_disabled), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                }) { Text(stringResource(R.string.allow_notifications)) }
            }
        }
        item {
            SettingsSection(stringResource(R.string.battery_alerts)) {
                SwitchRow(stringResource(R.string.alert_low), null, a.lowBatteryEnabled) { v -> vm.updateAlerts { it.copy(lowBatteryEnabled = v) } }
                IntSliderRow(stringResource(R.string.threshold), a.lowBatteryThreshold, 5..50, a.lowBatteryEnabled, Fmt::percent) { v -> vm.updateAlerts { it.copy(lowBatteryThreshold = v) } }
            }
        }
        item {
            SettingsSection(stringResource(R.string.charging_alerts)) {
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
            SettingsSection(stringResource(R.string.temperature_alerts)) {
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
                    CardHeader(stringResource(title), Icons.Outlined.Widgets)
                    Text(stringResource(desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    if (canPin) OutlinedButton(onClick = { manager.requestPinAppWidget(ComponentName(context, cls), null, null) }) {
                        Text(stringResource(R.string.add_to_home))
                    } else Text(stringResource(R.string.widget_manual_add), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            AppCard {
                CardHeader(stringResource(R.string.quick_tile), Icons.Outlined.ToggleOn)
                Text(stringResource(R.string.quick_tile_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    ScreenScaffold(title = stringResource(R.string.privacy), onBack = onBack) {
        listOf(
            Triple(R.string.privacy_offline_title, R.string.privacy_offline_body, Icons.Outlined.WifiOff),
            Triple(R.string.privacy_data_title, R.string.privacy_data_body, Icons.Outlined.Storage),
            Triple(R.string.privacy_permissions_title, R.string.privacy_permissions_body, Icons.Outlined.Key),
            Triple(R.string.privacy_sharing_title, R.string.privacy_sharing_body, Icons.Outlined.Share),
            Triple(R.string.privacy_limits_title, R.string.privacy_limits_body, Icons.Outlined.Info),
        ).forEach { (t, b, icon) ->
            item {
                AppCard {
                    CardHeader(stringResource(t), icon)
                    Text(stringResource(b), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
