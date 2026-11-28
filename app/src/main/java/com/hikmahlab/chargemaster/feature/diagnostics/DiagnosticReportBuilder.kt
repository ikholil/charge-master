package com.hikmahlab.chargemaster.feature.diagnostics

import android.content.Context
import com.hikmahlab.chargemaster.R
import com.hikmahlab.chargemaster.core.common.Fmt
import com.hikmahlab.chargemaster.core.design.dateTimeText
import com.hikmahlab.chargemaster.core.design.labelRes
import com.hikmahlab.chargemaster.core.security.DeviceInfoPolicy
import com.hikmahlab.chargemaster.domain.engine.CapacityEstimator
import com.hikmahlab.chargemaster.domain.model.BatteryHealthSnapshot
import com.hikmahlab.chargemaster.domain.model.BatterySnapshot
import com.hikmahlab.chargemaster.domain.model.TemperatureUnit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

data class SensorAvailability(
    val current: Boolean,
    val currentAverage: Boolean,
    val chargeCounter: Boolean,
    val energyCounter: Boolean,
    val temperature: Boolean,
    val voltage: Boolean,
    val cycleCount: Boolean,
    val thermal: Boolean,
    val systemChargeTime: Boolean,
) {
    companion object {
        fun of(s: BatterySnapshot) = SensorAvailability(
            current = s.currentNowMa != null,
            currentAverage = s.currentAverageMa != null,
            chargeCounter = s.chargeCounterMah != null,
            energyCounter = s.energyCounterMwh != null,
            temperature = s.temperatureC != null,
            voltage = s.voltageMv != null,
            cycleCount = s.cycleCount != null,
            thermal = s.thermalStatus != com.hikmahlab.chargemaster.domain.model.ThermalStatus.UNSUPPORTED,
            systemChargeTime = s.systemChargeTimeRemainingMs != null,
        )
    }
}

/** Builds a plain-text, human-readable report. Contains no personal identifiers. */
class DiagnosticReportBuilder @Inject constructor(@ApplicationContext private val context: Context) {
    fun build(
        s: BatterySnapshot,
        unit: TemperatureUnit,
        readingCount: Int,
        oldestReading: Long?,
        sessionCount: Int,
        health: List<BatteryHealthSnapshot>,
        designCapacity: Int?,
    ): String {
        val c = context
        val na = c.getString(R.string.unavailable)
        val yes = c.getString(R.string.supported)
        val no = c.getString(R.string.not_supported)
        val d = DeviceInfoPolicy.summary()
        val a = SensorAvailability.of(s)
        val (cap, conf) = CapacityEstimator.combine(health)
        fun line(label: Int, value: String?) = "• ${c.getString(label)}: ${value ?: na}"
        fun avail(label: Int, ok: Boolean) = "• ${c.getString(label)}: ${if (ok) yes else no}"
        return buildString {
            appendLine(c.getString(R.string.report_title))
            appendLine(c.getString(R.string.report_generated, dateTimeText(s.timestamp)))
            appendLine()
            appendLine("— ${c.getString(R.string.report_device)} —")
            appendLine(line(R.string.manufacturer, d.manufacturer))
            appendLine(line(R.string.model, d.model))
            appendLine(line(R.string.android_version, "${d.androidVersion} (API ${d.sdk})"))
            appendLine()
            appendLine("— ${c.getString(R.string.report_battery_now)} —")
            appendLine(line(R.string.metric_level, s.level?.let(Fmt::percent)))
            appendLine(line(R.string.metric_status, c.getString(s.status.labelRes())))
            appendLine(line(R.string.charging_source, c.getString(s.source.labelRes())))
            appendLine(line(R.string.metric_android_health, c.getString(s.health.labelRes())))
            appendLine(line(R.string.metric_temperature, s.temperatureC?.let { Fmt.temperature(it, unit) }))
            appendLine(line(R.string.metric_voltage, s.voltageMv?.let(Fmt::voltage)))
            appendLine(line(R.string.metric_current, s.currentNowMa?.let(Fmt::current)))
            appendLine(line(R.string.report_power_estimate, s.powerW?.let { Fmt.power(kotlin.math.abs(it)) }))
            appendLine(line(R.string.technology, s.technology))
            appendLine(line(R.string.cycle_count, s.cycleCount?.toString()))
            appendLine()
            appendLine("— ${c.getString(R.string.sensor_availability)} —")
            appendLine(avail(R.string.metric_current, a.current))
            appendLine(avail(R.string.current_average, a.currentAverage))
            appendLine(avail(R.string.charge_counter, a.chargeCounter))
            appendLine(avail(R.string.energy_counter, a.energyCounter))
            appendLine(avail(R.string.metric_temperature, a.temperature))
            appendLine(avail(R.string.metric_voltage, a.voltage))
            appendLine(avail(R.string.cycle_count, a.cycleCount))
            appendLine(avail(R.string.metric_thermal, a.thermal))
            appendLine(avail(R.string.system_charge_estimate, a.systemChargeTime))
            appendLine()
            appendLine("— ${c.getString(R.string.report_estimates)} —")
            appendLine(line(R.string.estimated_capacity, cap?.let(Fmt::capacity)) + " (${c.getString(confidenceRes(conf))})")
            appendLine(line(R.string.design_capacity, designCapacity?.let { Fmt.capacity(it.toFloat()) + " — " + c.getString(R.string.kind_user) }))
            CapacityEstimator.healthPercent(cap, conf, designCapacity)?.let { appendLine(line(R.string.estimated_health, Fmt.percent1(it))) }
            appendLine()
            appendLine("— ${c.getString(R.string.report_coverage)} —")
            appendLine(line(R.string.stored_readings, readingCount.toString()))
            appendLine(line(R.string.history_since, oldestReading?.let(::dateTimeText)))
            appendLine(line(R.string.recorded_sessions, sessionCount.toString()))
            appendLine()
            appendLine(c.getString(R.string.report_footer))
        }
    }

    private fun confidenceRes(c: com.hikmahlab.chargemaster.domain.model.Confidence) = when (c) {
        com.hikmahlab.chargemaster.domain.model.Confidence.HIGH -> R.string.confidence_high
        com.hikmahlab.chargemaster.domain.model.Confidence.MEDIUM -> R.string.confidence_medium
        com.hikmahlab.chargemaster.domain.model.Confidence.LOW -> R.string.confidence_low
        com.hikmahlab.chargemaster.domain.model.Confidence.INSUFFICIENT -> R.string.confidence_insufficient
    }
}
