package com.chargemaster.app.feature.diagnostics

import android.content.Intent
import android.widget.Toast
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.chargemaster.app.R
import com.chargemaster.app.core.common.Fmt
import com.chargemaster.app.core.design.AppCard
import com.chargemaster.app.core.design.InfoBanner
import com.chargemaster.app.core.design.KeyValueRow
import com.chargemaster.app.core.design.NavRow
import com.chargemaster.app.core.design.ScreenScaffold
import com.chargemaster.app.core.design.SectionHeader
import com.chargemaster.app.core.design.dateTimeText
import com.chargemaster.app.core.design.labelRes
import com.chargemaster.app.core.security.DeviceInfoPolicy
import com.chargemaster.app.domain.model.BatterySnapshot
import com.chargemaster.app.domain.model.CurrentCalibration
import com.chargemaster.app.domain.model.TemperatureUnit
import com.chargemaster.app.domain.repository.BatteryRepository
import com.chargemaster.app.domain.repository.HealthRepository
import com.chargemaster.app.domain.repository.PreferencesRepository
import com.chargemaster.app.domain.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class DiagnosticsState(
    val snapshot: BatterySnapshot? = null,
    val unit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val readingCount: Int = 0,
    val oldest: Long? = null,
    val sessionCount: Int = 0,
    val calibration: CurrentCalibration = CurrentCalibration(),
    val report: String = "",
)

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    battery: BatteryRepository,
    sessions: SessionRepository,
    health: HealthRepository,
    private val prefs: PreferencesRepository,
    builder: DiagnosticReportBuilder,
) : ViewModel() {
    private val meta = combine(
        battery.observeReadingCount(), battery.observeOldestReading(), sessions.observeRecent(10_000).map { it.size }, health.observeSnapshots(),
    ) { count, oldest, sc, h -> Meta(count, oldest, sc, h) }

    val state: StateFlow<DiagnosticsState> = combine(battery.liveSnapshots, prefs.preferences, meta) { s, p, m ->
        DiagnosticsState(
            snapshot = s, unit = p.temperatureUnit, readingCount = m.count, oldest = m.oldest, sessionCount = m.sessions,
            calibration = prefs.calibration(),
            report = builder.build(s, p.temperatureUnit, m.count, m.oldest, m.sessions, m.health, p.designCapacityMah),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiagnosticsState())

    private data class Meta(val count: Int, val oldest: Long?, val sessions: Int, val health: List<com.chargemaster.app.domain.model.BatteryHealthSnapshot>)
}

@Composable
fun DiagnosticsScreen(onBack: () -> Unit, onOpenReport: () -> Unit, vm: DiagnosticsViewModel = hiltViewModel()) {
    val st by vm.state.collectAsStateWithLifecycle()
    val s = st.snapshot
    val d = DeviceInfoPolicy.summary()
    ScreenScaffold(title = stringResource(R.string.diagnostics), onBack = onBack) {
        item {
            AppCard {
                SectionHeader(stringResource(R.string.report_device))
                KeyValueRow(stringResource(R.string.manufacturer), d.manufacturer)
                KeyValueRow(stringResource(R.string.model), d.model)
                KeyValueRow(stringResource(R.string.android_version), "${d.androidVersion} (API ${d.sdk})")
            }
        }
        item {
            AppCard {
                SectionHeader(stringResource(R.string.report_battery_now))
                KeyValueRow(stringResource(R.string.metric_status), s?.let { stringResource(it.status.labelRes()) })
                KeyValueRow(stringResource(R.string.charging_source), s?.let { stringResource(it.source.labelRes()) })
                KeyValueRow(stringResource(R.string.metric_android_health), s?.let { stringResource(it.health.labelRes()) })
                KeyValueRow(stringResource(R.string.metric_temperature), s?.temperatureC?.let { Fmt.temperature(it, st.unit) })
                KeyValueRow(stringResource(R.string.metric_voltage), s?.voltageMv?.let(Fmt::voltage))
                KeyValueRow(stringResource(R.string.metric_current), s?.currentNowMa?.let(Fmt::current))
                KeyValueRow(stringResource(R.string.current_average), s?.currentAverageMa?.let(Fmt::current))
                KeyValueRow(stringResource(R.string.charge_counter), s?.chargeCounterMah?.let(Fmt::capacity))
                KeyValueRow(stringResource(R.string.battery_present), s?.present?.let { stringResource(if (it) R.string.yes else R.string.no) })
            }
        }
        item {
            AppCard {
                SectionHeader(stringResource(R.string.sensor_availability))
                val a = s?.let(SensorAvailability::of)
                AvailabilityRow(R.string.metric_current, a?.current)
                AvailabilityRow(R.string.current_average, a?.currentAverage)
                AvailabilityRow(R.string.charge_counter, a?.chargeCounter)
                AvailabilityRow(R.string.energy_counter, a?.energyCounter)
                AvailabilityRow(R.string.metric_temperature, a?.temperature)
                AvailabilityRow(R.string.metric_voltage, a?.voltage)
                AvailabilityRow(R.string.cycle_count, a?.cycleCount)
                AvailabilityRow(R.string.metric_thermal, a?.thermal)
                AvailabilityRow(R.string.system_charge_estimate, a?.systemChargeTime)
            }
        }
        item {
            AppCard {
                SectionHeader(stringResource(R.string.data_reliability))
                KeyValueRow(stringResource(R.string.current_unit_detected), st.calibration.learnedUnit?.let {
                    stringResource(if (it == com.chargemaster.app.domain.model.CurrentUnit.MICROAMPERES) R.string.unit_microamps else R.string.unit_milliamps)
                } ?: stringResource(R.string.not_yet_learned))
                KeyValueRow(stringResource(R.string.current_sign_detected), st.calibration.learnedSign?.let {
                    stringResource(if (it == com.chargemaster.app.domain.model.CurrentSign.STANDARD) R.string.sign_standard else R.string.sign_inverted)
                } ?: stringResource(R.string.not_yet_learned))
                Text(stringResource(R.string.reliability_explainer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            AppCard {
                SectionHeader(stringResource(R.string.report_coverage))
                KeyValueRow(stringResource(R.string.stored_readings), st.readingCount.toString())
                KeyValueRow(stringResource(R.string.history_since), st.oldest?.let(::dateTimeText))
                KeyValueRow(stringResource(R.string.recorded_sessions), st.sessionCount.toString())
            }
        }
        item {
            AppCard { NavRow(stringResource(R.string.battery_report), stringResource(R.string.report_subtitle), Icons.Outlined.Description, onOpenReport) }
        }
    }
}

@Composable
private fun AvailabilityRow(label: Int, ok: Boolean?) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.let { it }, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok == true) Icons.Outlined.CheckCircle else Icons.Outlined.RemoveCircleOutline, null,
            tint = if (ok == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(stringResource(if (ok == true) R.string.supported else R.string.not_supported), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
fun ReportScreen(onBack: () -> Unit, vm: DiagnosticsViewModel = hiltViewModel()) {
    val st by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val chooserTitle = stringResource(R.string.share_report)
    val subject = stringResource(R.string.report_title)
    val clipboard = LocalClipboardManager.current
    val copiedText = stringResource(R.string.copied)
    ScreenScaffold(title = stringResource(R.string.battery_report), onBack = onBack) {
        item { InfoBanner(stringResource(R.string.report_privacy_note)) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = st.report.isNotEmpty(), onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, subject)
                        putExtra(Intent.EXTRA_TEXT, st.report)
                    }
                    context.startActivity(Intent.createChooser(send, chooserTitle))
                }) {
                    Icon(Icons.Outlined.Share, null)
                    Spacer(Modifier.width(8.dp))
                    Text(chooserTitle)
                }
                OutlinedButton(enabled = st.report.isNotEmpty(), onClick = {
                    clipboard.setText(AnnotatedString(st.report))
                    Toast.makeText(context, copiedText, Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Outlined.ContentCopy, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.copy))
                }
            }
        }
        item {
            AppCard {
                SelectionContainer {
                    Text(st.report, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                }
            }
        }
    }
}
