package com.chargemaster.app.core.design

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Cable
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.chargemaster.app.domain.engine.ChargeSpeed
import com.chargemaster.app.domain.engine.TemperatureZone
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.chargemaster.app.R
import com.chargemaster.app.core.common.Fmt
import com.chargemaster.app.domain.model.BatteryHealthStatus
import com.chargemaster.app.domain.model.ChargingSource
import com.chargemaster.app.domain.model.ChargingStatus
import com.chargemaster.app.domain.model.ThermalStatus
import java.text.DateFormat
import java.util.Date

@StringRes fun ChargingStatus.labelRes() = when (this) {
    ChargingStatus.CHARGING -> R.string.status_charging
    ChargingStatus.DISCHARGING -> R.string.status_discharging
    ChargingStatus.NOT_CHARGING -> R.string.status_not_charging
    ChargingStatus.FULL -> R.string.status_full
    ChargingStatus.UNKNOWN -> R.string.unknown
}

@StringRes fun ChargingSource.labelRes() = when (this) {
    ChargingSource.AC -> R.string.source_ac
    ChargingSource.USB -> R.string.source_usb
    ChargingSource.WIRELESS -> R.string.source_wireless
    ChargingSource.DOCK -> R.string.source_dock
    ChargingSource.NONE -> R.string.source_battery
    ChargingSource.UNKNOWN -> R.string.unknown
}

@StringRes fun BatteryHealthStatus.labelRes() = when (this) {
    BatteryHealthStatus.GOOD -> R.string.health_good
    BatteryHealthStatus.OVERHEAT -> R.string.health_overheat
    BatteryHealthStatus.DEAD -> R.string.health_dead
    BatteryHealthStatus.OVER_VOLTAGE -> R.string.health_over_voltage
    BatteryHealthStatus.UNSPECIFIED_FAILURE -> R.string.health_failure
    BatteryHealthStatus.COLD -> R.string.health_cold
    BatteryHealthStatus.UNKNOWN -> R.string.unknown
}

@StringRes fun ThermalStatus.labelRes() = when (this) {
    ThermalStatus.NONE -> R.string.thermal_none
    ThermalStatus.LIGHT -> R.string.thermal_light
    ThermalStatus.MODERATE -> R.string.thermal_moderate
    ThermalStatus.SEVERE -> R.string.thermal_severe
    ThermalStatus.CRITICAL -> R.string.thermal_critical
    ThermalStatus.EMERGENCY -> R.string.thermal_emergency
    ThermalStatus.SHUTDOWN -> R.string.thermal_shutdown
    ThermalStatus.UNSUPPORTED -> R.string.not_supported
}

@Composable
fun durationText(ms: Long): String =
    Fmt.duration(ms, stringResource(R.string.unit_hour), stringResource(R.string.unit_min))

fun dateTimeText(ts: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ts))

fun timeText(ts: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ts))

enum class Period(val ms: Long, @StringRes val label: Int, val days: Int) {
    H24(86_400_000L, R.string.period_24h, 1),
    D7(7 * 86_400_000L, R.string.period_7d, 7),
    D30(30 * 86_400_000L, R.string.period_30d, 30),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodSelector(selected: Period, onSelect: (Period) -> Unit, options: List<Period> = Period.entries) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, p ->
            SegmentedButton(
                selected = p == selected, onClick = { onSelect(p) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                label = { Text(stringResource(p.label), maxLines = 1) },
            )
        }
    }
}

@StringRes fun ChargeSpeed.labelRes() = when (this) {
    ChargeSpeed.TRICKLE -> R.string.speed_trickle
    ChargeSpeed.SLOW -> R.string.speed_slow
    ChargeSpeed.STANDARD -> R.string.speed_standard
    ChargeSpeed.FAST -> R.string.speed_fast
    ChargeSpeed.SUPER_FAST -> R.string.speed_super_fast
}

@Composable
fun ChargeSpeed.color(): Color = when (this) {
    ChargeSpeed.TRICKLE, ChargeSpeed.SLOW -> Tokens.Warning
    ChargeSpeed.STANDARD -> Tokens.Info
    ChargeSpeed.FAST, ChargeSpeed.SUPER_FAST -> MaterialTheme.colorScheme.primary
}

@StringRes fun TemperatureZone.labelRes() = when (this) {
    TemperatureZone.COLD -> R.string.zone_cold
    TemperatureZone.IDEAL -> R.string.zone_ideal
    TemperatureZone.WARM -> R.string.zone_warm
    TemperatureZone.HOT -> R.string.zone_hot
    TemperatureZone.CRITICAL -> R.string.zone_critical
}

@StringRes fun TemperatureZone.descriptionRes() = when (this) {
    TemperatureZone.COLD -> R.string.zone_cold_desc
    TemperatureZone.IDEAL -> R.string.zone_ideal_desc
    TemperatureZone.WARM -> R.string.zone_warm_desc
    TemperatureZone.HOT -> R.string.zone_hot_desc
    TemperatureZone.CRITICAL -> R.string.zone_critical_desc
}

@Composable
fun TemperatureZone.color(): Color = when (this) {
    TemperatureZone.COLD -> Tokens.Info
    TemperatureZone.IDEAL -> MaterialTheme.colorScheme.primary
    TemperatureZone.WARM -> Tokens.Warning
    TemperatureZone.HOT -> Tokens.Hot
    TemperatureZone.CRITICAL -> Tokens.Danger
}

/** Battery care tips, shown on the Tips screen and rotated daily on the dashboard. */
data class BatteryTip(val icon: ImageVector, @StringRes val title: Int, @StringRes val body: Int)

val BatteryTips: List<BatteryTip> = listOf(
    BatteryTip(Icons.Outlined.BatteryChargingFull, R.string.tip_1_title, R.string.tip_1_body),
    BatteryTip(Icons.Outlined.Thermostat, R.string.tip_2_title, R.string.tip_2_body),
    BatteryTip(Icons.Outlined.PhoneAndroid, R.string.tip_3_title, R.string.tip_3_body),
    BatteryTip(Icons.Outlined.Cable, R.string.tip_4_title, R.string.tip_4_body),
    BatteryTip(Icons.Outlined.Bedtime, R.string.tip_5_title, R.string.tip_5_body),
    BatteryTip(Icons.Outlined.SportsEsports, R.string.tip_6_title, R.string.tip_6_body),
    BatteryTip(Icons.Outlined.Inventory2, R.string.tip_7_title, R.string.tip_7_body),
    BatteryTip(Icons.Outlined.SystemUpdate, R.string.tip_8_title, R.string.tip_8_body),
)

/** Deterministic tip for the given day so it stays stable across recompositions and restarts. */
fun tipForDay(epochMs: Long): BatteryTip = BatteryTips[((epochMs / 86_400_000L) % BatteryTips.size).toInt()]
