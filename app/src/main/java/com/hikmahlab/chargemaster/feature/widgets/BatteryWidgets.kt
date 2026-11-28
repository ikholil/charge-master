package com.hikmahlab.chargemaster.feature.widgets

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.hikmahlab.chargemaster.R
import com.hikmahlab.chargemaster.app.MainActivity
import com.hikmahlab.chargemaster.core.common.Fmt
import com.hikmahlab.chargemaster.core.design.DarkColors
import com.hikmahlab.chargemaster.core.design.LightColors
import com.hikmahlab.chargemaster.domain.engine.TimeEstimator
import com.hikmahlab.chargemaster.domain.model.ChargingSource
import com.hikmahlab.chargemaster.domain.model.ChargingStatus
import com.hikmahlab.chargemaster.domain.repository.BatteryRepository
import com.hikmahlab.chargemaster.domain.repository.PreferencesRepository
import com.hikmahlab.chargemaster.domain.repository.SessionRepository
import com.hikmahlab.chargemaster.domain.usecase.WidgetRefresher
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun battery(): BatteryRepository
    fun sessions(): SessionRepository
    fun prefs(): PreferencesRepository
}

/** Pre-formatted, display-ready values. Unavailable values are explicit labels, never numbers. */
data class WidgetData(
    val level: Int?,
    val levelText: String,
    val statusText: String,
    val temperatureText: String,
    val voltageText: String,
    val sourceText: String,
    val etaText: String,
    val charging: Boolean,
) {
    companion object {
        suspend fun load(context: Context): WidgetData {
            val ep = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
            val s = ep.battery().readSnapshot()
            val unit = ep.prefs().current().temperatureUnit
            val na = context.getString(R.string.unavailable)
            val session = if (s.isPluggedIn) ep.sessions().openSession() else null
            val eta = if (s.isPluggedIn) TimeEstimator.timeToLevel(100, s.level, s.systemChargeTimeRemainingMs, session, s.currentNowMa, null) else null
            return WidgetData(
                level = s.level,
                levelText = s.level?.let(Fmt::percent) ?: na,
                statusText = context.getString(statusRes(s.status)),
                temperatureText = s.temperatureC?.let { Fmt.temperature(it, unit) } ?: na,
                voltageText = s.voltageMv?.let(Fmt::voltage) ?: na,
                sourceText = context.getString(sourceRes(s.source)),
                etaText = when {
                    !s.isPluggedIn -> context.getString(R.string.not_charging)
                    s.status == ChargingStatus.FULL -> context.getString(R.string.status_full)
                    eta == null -> context.getString(R.string.estimating)
                    else -> context.getString(R.string.widget_eta, Fmt.duration(eta.minutes * 60_000,
                        context.getString(R.string.unit_hour), context.getString(R.string.unit_min)))
                },
                charging = s.isPluggedIn,
            )
        }

        fun statusRes(s: ChargingStatus) = when (s) {
            ChargingStatus.CHARGING -> R.string.status_charging
            ChargingStatus.DISCHARGING -> R.string.status_discharging
            ChargingStatus.NOT_CHARGING -> R.string.status_not_charging
            ChargingStatus.FULL -> R.string.status_full
            ChargingStatus.UNKNOWN -> R.string.unknown
        }

        fun sourceRes(s: ChargingSource) = when (s) {
            ChargingSource.AC -> R.string.source_ac
            ChargingSource.USB -> R.string.source_usb
            ChargingSource.WIRELESS -> R.string.source_wireless
            ChargingSource.DOCK -> R.string.source_dock
            ChargingSource.NONE -> R.string.source_battery
            ChargingSource.UNKNOWN -> R.string.unknown
        }
    }
}

private val widgetColors = ColorProviders(light = LightColors, dark = DarkColors)

@Composable
private fun WidgetSurface(description: String, content: @Composable () -> Unit) {
    Column(
        modifier = GlanceModifier.fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .cornerRadius(20.dp)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>())
            .semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

class MinimalBatteryWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val d = WidgetData.load(context)
        provideContent {
            GlanceTheme(colors = widgetColors) {
                WidgetSurface("${d.levelText}, ${d.statusText}") {
                    Text(d.levelText, style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.primary))
                    Text(d.statusText, style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant))
                }
            }
        }
    }
}

class DetailedBatteryWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val d = WidgetData.load(context)
        val tempLabel = context.getString(R.string.metric_temperature)
        val voltLabel = context.getString(R.string.metric_voltage)
        provideContent {
            GlanceTheme(colors = widgetColors) {
                WidgetSurface("${d.levelText}, ${d.statusText}, $tempLabel ${d.temperatureText}, $voltLabel ${d.voltageText}") {
                    // Narrow placement: stack the values instead of using two columns.
                    if (LocalSize.current.width < 160.dp) {
                        Text(d.levelText, style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.primary))
                        Text(d.temperatureText, style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurface))
                        Text(d.voltageText, style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant))
                        return@WidgetSurface
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
                        Column(modifier = GlanceModifier.defaultWeight()) {
                            Text(d.levelText, style = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.primary))
                            Text(d.statusText, style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(tempLabel, style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurfaceVariant))
                            Text(d.temperatureText, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = GlanceTheme.colors.onSurface))
                            Spacer(GlanceModifier.height(4.dp))
                            Text(voltLabel, style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurfaceVariant))
                            Text(d.voltageText, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = GlanceTheme.colors.onSurface))
                        }
                    }
                }
            }
        }
    }
}

class ChargingBatteryWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val d = WidgetData.load(context)
        provideContent {
            GlanceTheme(colors = widgetColors) {
                WidgetSurface("${d.levelText}, ${d.sourceText}, ${d.etaText}") {
                    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(d.levelText, style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.primary))
                        Spacer(GlanceModifier.width(8.dp))
                        Text(d.sourceText, style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant))
                    }
                    val compact = LocalSize.current.height < 90.dp
                    if (!compact) Spacer(GlanceModifier.height(8.dp))
                    if (!compact) LinearProgressIndicator(
                        progress = (d.level ?: 0) / 100f,
                        modifier = GlanceModifier.fillMaxWidth().height(8.dp),
                        color = GlanceTheme.colors.primary,
                        backgroundColor = GlanceTheme.colors.surfaceVariant,
                    )
                    Spacer(GlanceModifier.height(if (compact) 2.dp else 8.dp))
                    Text(d.etaText, style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurface))
                }
            }
        }
    }
}

class MinimalBatteryWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = MinimalBatteryWidget() }
class DetailedBatteryWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = DetailedBatteryWidget() }
class ChargingBatteryWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = ChargingBatteryWidget() }

/** Widgets update only when a real sample is recorded (event-driven), never by polling. */
@Singleton
class GlanceWidgetRefresher @Inject constructor(@ApplicationContext private val context: Context) : WidgetRefresher {
    override suspend fun refresh() {
        MinimalBatteryWidget().updateAll(context)
        DetailedBatteryWidget().updateAll(context)
        ChargingBatteryWidget().updateAll(context)
    }
}
