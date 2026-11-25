package com.chargemaster.app.core.design

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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

@Composable
fun PeriodSelector(selected: Period, onSelect: (Period) -> Unit, options: List<Period> = Period.entries) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { p ->
            FilterChip(selected = p == selected, onClick = { onSelect(p) }, label = { Text(stringResource(p.label)) })
        }
    }
}
