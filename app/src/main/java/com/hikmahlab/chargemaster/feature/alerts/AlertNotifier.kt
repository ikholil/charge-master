package com.hikmahlab.chargemaster.feature.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hikmahlab.chargemaster.R
import com.hikmahlab.chargemaster.app.MainActivity
import com.hikmahlab.chargemaster.core.common.Fmt
import com.hikmahlab.chargemaster.domain.engine.BatteryAlert
import com.hikmahlab.chargemaster.domain.model.ChargingSource
import com.hikmahlab.chargemaster.domain.repository.PreferencesRepository
import com.hikmahlab.chargemaster.domain.usecase.AlertSink
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

object NotificationChannels {
    const val BATTERY = "battery_alerts"
    const val CHARGING = "charging_alerts"
    const val TEMPERATURE = "temperature_alerts"

    fun create(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(BATTERY, context.getString(R.string.channel_battery), NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = context.getString(R.string.channel_battery_desc) },
                NotificationChannel(CHARGING, context.getString(R.string.channel_charging), NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = context.getString(R.string.channel_charging_desc) },
                NotificationChannel(TEMPERATURE, context.getString(R.string.channel_temperature), NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = context.getString(R.string.channel_temperature_desc) },
            ),
        )
    }

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

@Singleton
class AndroidAlertSink @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: PreferencesRepository,
) : AlertSink {

    override suspend fun deliver(alert: BatteryAlert) {
        if (!NotificationChannels.hasPermission(context)) return
        val unit = prefs.current().temperatureUnit
        val c = context
        val (channel, id, title, text) = when (alert) {
            is BatteryAlert.LowBattery -> Quad(NotificationChannels.BATTERY, ID_LOW,
                c.getString(R.string.notif_low_title), c.getString(R.string.notif_low_text, Fmt.percent(alert.level)))
            is BatteryAlert.ThresholdReached -> Quad(NotificationChannels.CHARGING, ID_THRESHOLD,
                c.getString(R.string.notif_threshold_title, Fmt.percent(alert.threshold)),
                c.getString(R.string.notif_threshold_text, Fmt.percent(alert.level)))
            is BatteryAlert.HighTemperature -> Quad(NotificationChannels.TEMPERATURE, ID_TEMP,
                c.getString(R.string.notif_temp_title), c.getString(R.string.notif_temp_text, Fmt.temperature(alert.temperatureC, unit)))
            is BatteryAlert.ChargingStarted -> Quad(NotificationChannels.CHARGING, ID_PLUG,
                c.getString(R.string.notif_started_title),
                c.getString(R.string.notif_started_text, sourceLabel(alert.source), alert.level?.let(Fmt::percent) ?: "—"))
            is BatteryAlert.ChargingStopped -> Quad(NotificationChannels.CHARGING, ID_PLUG,
                c.getString(R.string.notif_stopped_title),
                c.getString(R.string.notif_stopped_text, alert.level?.let(Fmt::percent) ?: "—"))
            BatteryAlert.ChargingComplete -> Quad(NotificationChannels.CHARGING, ID_THRESHOLD,
                c.getString(R.string.notif_complete_title), c.getString(R.string.notif_complete_text))
            is BatteryAlert.LongCharging -> Quad(NotificationChannels.CHARGING, ID_LONG,
                c.getString(R.string.notif_long_title), c.getString(R.string.notif_long_text, alert.hours))
            is BatteryAlert.UnusualSpeed -> Quad(NotificationChannels.CHARGING, ID_UNUSUAL,
                c.getString(R.string.notif_unusual_title),
                c.getString(R.string.notif_unusual_text, Fmt.power(alert.observedW), Fmt.power(alert.typicalW)))
        }
        val open = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(c, channel)
            .setSmallIcon(R.drawable.ic_stat_bolt)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        try {
            NotificationManagerCompat.from(c).notify(id, n)
        } catch (_: SecurityException) {
            // Permission revoked between check and post — nothing to do.
        }
    }

    private fun sourceLabel(s: ChargingSource) = context.getString(
        when (s) {
            ChargingSource.AC -> R.string.source_ac
            ChargingSource.USB -> R.string.source_usb
            ChargingSource.WIRELESS -> R.string.source_wireless
            ChargingSource.DOCK -> R.string.source_dock
            ChargingSource.NONE -> R.string.source_battery
            ChargingSource.UNKNOWN -> R.string.unknown
        },
    )

    private data class Quad(val channel: String, val id: Int, val title: String, val text: String)

    companion object {
        private const val ID_LOW = 1001
        private const val ID_THRESHOLD = 1002
        private const val ID_TEMP = 1003
        private const val ID_PLUG = 1004
        private const val ID_LONG = 1005
        private const val ID_UNUSUAL = 1006
    }
}
