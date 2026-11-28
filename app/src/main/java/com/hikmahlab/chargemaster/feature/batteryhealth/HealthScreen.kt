package com.hikmahlab.chargemaster.feature.batteryhealth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Loop
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import com.hikmahlab.chargemaster.core.design.NumberWithUnit
import com.hikmahlab.chargemaster.core.design.Overline
import com.hikmahlab.chargemaster.core.design.ProgressTrack
import com.hikmahlab.chargemaster.core.design.SerifFamily
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.text.font.FontStyle
import com.hikmahlab.chargemaster.core.design.CardHeader
import com.hikmahlab.chargemaster.core.design.KindDot
import com.hikmahlab.chargemaster.core.design.Tokens
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hikmahlab.chargemaster.R
import com.hikmahlab.chargemaster.core.common.Fmt
import com.hikmahlab.chargemaster.core.design.AppCard
import com.hikmahlab.chargemaster.core.design.LocalPastels
import com.hikmahlab.chargemaster.core.design.ChartPoint
import com.hikmahlab.chargemaster.core.design.ConfidenceChip
import com.hikmahlab.chargemaster.core.design.InfoBanner
import com.hikmahlab.chargemaster.core.design.KeyValueRow
import com.hikmahlab.chargemaster.core.design.LineChart
import com.hikmahlab.chargemaster.core.design.NavRow
import com.hikmahlab.chargemaster.core.design.ScreenScaffold
import com.hikmahlab.chargemaster.core.design.ValueKind
import com.hikmahlab.chargemaster.core.design.labelRes
import com.hikmahlab.chargemaster.domain.engine.CapacityEstimator
import com.hikmahlab.chargemaster.domain.model.BatteryHealthSnapshot
import com.hikmahlab.chargemaster.domain.model.BatterySnapshot
import com.hikmahlab.chargemaster.domain.model.Confidence
import com.hikmahlab.chargemaster.domain.model.TemperatureUnit
import com.hikmahlab.chargemaster.domain.repository.BatteryRepository
import com.hikmahlab.chargemaster.domain.repository.HealthRepository
import com.hikmahlab.chargemaster.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HealthState(
    val snapshot: BatterySnapshot? = null,
    val unit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val history: List<BatteryHealthSnapshot> = emptyList(),
    val estimatedCapacity: Float? = null,
    val confidence: Confidence = Confidence.INSUFFICIENT,
    val designCapacity: Int? = null,
    val healthPercent: Float? = null,
    val wearPerMonth: Float? = null,
)

@HiltViewModel
class HealthViewModel @Inject constructor(
    battery: BatteryRepository,
    health: HealthRepository,
    private val prefs: PreferencesRepository,
) : ViewModel() {
    val state: StateFlow<HealthState> = combine(battery.liveSnapshots, health.observeSnapshots(), prefs.preferences) { s, h, p ->
        val (cap, conf) = CapacityEstimator.combine(h)
        HealthState(
            snapshot = s, unit = p.temperatureUnit, history = h,
            estimatedCapacity = cap, confidence = conf, designCapacity = p.designCapacityMah,
            healthPercent = CapacityEstimator.healthPercent(cap, conf, p.designCapacityMah),
            wearPerMonth = CapacityEstimator.wearTrendPerMonth(h, p.designCapacityMah),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HealthState())

    fun setDesignCapacity(mah: Int?) = viewModelScope.launch { prefs.setDesignCapacity(mah) }
}

@Composable
fun HealthScreen(onOpenDiagnostics: () -> Unit, onOpenTemperature: () -> Unit, onOpenTips: () -> Unit = {}, vm: HealthViewModel = hiltViewModel()) {
    val st by vm.state.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf(false) }
    val s = st.snapshot
    ScreenScaffold(title = stringResource(R.string.battery_health)) {
        item {
            val lilac = LocalPastels.current.lilac
            AppCard(containerColor = lilac.container, contentPadding = 22.dp) {
                val pct = st.healthPercent
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Overline(stringResource(R.string.estimated_health), Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
                if (pct != null) NumberWithUnit(Fmt.percent1(pct), style = MaterialTheme.typography.displayLarge, unitScale = 0.42f, unitColor = lilac.content)
                else Text(stringResource(R.string.insufficient_data), style = MaterialTheme.typography.headlineMedium.copy(fontFamily = SerifFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (pct != null) {
                    Spacer(Modifier.height(14.dp))
                    ProgressTrack(pct / 100f, color = lilac.content)
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    stringResource(
                        when {
                            st.designCapacity == null -> R.string.health_needs_design
                            st.confidence < Confidence.MEDIUM -> R.string.health_needs_data
                            else -> R.string.health_explainer
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                KeyValueRow(stringResource(R.string.design_capacity), st.designCapacity?.let { Fmt.capacity(it.toFloat()) }, kind = ValueKind.USER_PROVIDED)
                androidx.compose.material3.Button(onClick = { editing = true }, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = lilac.content, contentColor = MaterialTheme.colorScheme.surface)) { Text(stringResource(R.string.set_design_capacity)) }
            }
        }
        item {
            AppCard {
                CardHeader(stringResource(R.string.android_reported_status), Icons.Outlined.Android)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s?.health?.let { stringResource(it.labelRes()) } ?: stringResource(R.string.unavailable), style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f))
                    KindDot(ValueKind.LIVE)
                }
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.android_status_explainer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            AppCard {
                CardHeader(stringResource(R.string.estimated_capacity), Icons.Outlined.BatteryStd) {
                    if (st.estimatedCapacity != null) KindDot(ValueKind.ESTIMATE)
                }
                val cap = st.estimatedCapacity
                if (cap != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NumberWithUnit(Fmt.capacity(cap), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                        ConfidenceChip(st.confidence)
                    }
                } else {
                    Text(stringResource(R.string.insufficient_data), style = MaterialTheme.typography.titleLarge.copy(fontFamily = SerifFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.capacity_method_explainer, CapacityEstimator.MIN_SPAN_PERCENT),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(pluralStringResource(R.plurals.capacity_measurements, st.history.size, st.history.size), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (s?.chargeCounterMah == null) {
                    Spacer(Modifier.height(6.dp))
                    InfoBanner(stringResource(R.string.charge_counter_unsupported))
                }
            }
        }
        item {
            AppCard {
                CardHeader(stringResource(R.string.wear_trend), Icons.AutoMirrored.Outlined.TrendingDown)
                val design = st.designCapacity
                val pts = st.history.filter { it.estimatedCapacityMah != null && it.confidence >= Confidence.MEDIUM }
                    .map { ChartPoint((it.timestamp - (st.history.firstOrNull()?.timestamp ?: 0)) / 86_400_000f, it.estimatedCapacityMah!!) }
                if (pts.size < 2) {
                    Text(stringResource(R.string.wear_insufficient), style = MaterialTheme.typography.bodyMedium)
                } else {
                    LineChart(
                        pts, stringResource(R.string.chart_capacity_desc, pts.size, Fmt.capacity(pts.first().y), Fmt.capacity(pts.last().y)),
                        yLabel = { Fmt.capacity(it) }, showDots = true,
                        threshold = design?.toFloat(), thresholdColor = MaterialTheme.colorScheme.outline,
                    )
                    KeyValueRow(stringResource(R.string.wear_rate), st.wearPerMonth?.let { stringResource(R.string.wear_rate_value, Fmt.decimal1(it)) }
                        ?: stringResource(R.string.wear_insufficient), kind = ValueKind.ESTIMATE)
                }
            }
        }
        item {
            AppCard {
                CardHeader(stringResource(R.string.cycle_count), Icons.Outlined.Loop)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s?.cycleCount?.toString() ?: stringResource(R.string.not_reported),
                        style = if (s?.cycleCount != null) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    KindDot(if (s?.cycleCount != null) ValueKind.LIVE else ValueKind.UNSUPPORTED)
                }
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.cycle_count_explainer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            AppCard {
                CardHeader(stringResource(R.string.battery_details), Icons.Outlined.Info)
                KeyValueRow(stringResource(R.string.technology), s?.technology)
                KeyValueRow(stringResource(R.string.metric_voltage), s?.voltageMv?.let(Fmt::voltage))
                KeyValueRow(stringResource(R.string.metric_temperature), s?.temperatureC?.let { Fmt.temperature(it, st.unit) })
                KeyValueRow(stringResource(R.string.charge_counter), s?.chargeCounterMah?.let(Fmt::capacity))
                KeyValueRow(stringResource(R.string.energy_counter), s?.energyCounterMwh?.let(Fmt::energy))
            }
        }
        item {
            AppCard(contentPadding = 8.dp) {
                Column {
                NavRow(stringResource(R.string.diagnostics), stringResource(R.string.diagnostics_subtitle), Icons.Outlined.MonitorHeart, onOpenDiagnostics)
                NavRow(stringResource(R.string.temperature_monitor), stringResource(R.string.quick_temperature_subtitle), Icons.Outlined.Thermostat, onOpenTemperature)
                NavRow(stringResource(R.string.tips_title), stringResource(R.string.tips_subtitle), Icons.Outlined.Lightbulb, onOpenTips)
                }
            }
        }
    }
    if (editing) DesignCapacityDialog(st.designCapacity, onDismiss = { editing = false }) { vm.setDesignCapacity(it); editing = false }
}

@Composable
private fun DesignCapacityDialog(current: Int?, onDismiss: () -> Unit, onSave: (Int?) -> Unit) {
    var text by remember { mutableStateOf(current?.toString() ?: "") }
    val value = text.toIntOrNull()
    val valid = text.isEmpty() || (value != null && value in 500..20_000)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.design_capacity)) },
        text = {
            androidx.compose.foundation.layout.Column {
                Text(stringResource(R.string.design_capacity_help), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text, onValueChange = { t -> text = t.filter(Char::isDigit).take(5) },
                    label = { Text("mAh") }, singleLine = true, isError = !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { if (!valid) Text(stringResource(R.string.design_capacity_invalid)) },
                )
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onSave(value) }) { Text(stringResource(R.string.save)) } },
        dismissButton = {
            Row {
                if (current != null) TextButton(onClick = { onSave(null) }) { Text(stringResource(R.string.clear)) }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}
