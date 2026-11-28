package com.hikmahlab.chargemaster.core.common

import com.hikmahlab.chargemaster.domain.model.TemperatureUnit
import java.util.Locale
import kotlin.math.abs

object TemperatureConverter {
    fun celsiusToFahrenheit(c: Float): Float = c * 9f / 5f + 32f
    fun fahrenheitToCelsius(f: Float): Float = (f - 32f) * 5f / 9f
    fun fromCelsius(c: Float, unit: TemperatureUnit): Float =
        if (unit == TemperatureUnit.FAHRENHEIT) celsiusToFahrenheit(c) else c
    /** Converts a temperature *difference* (e.g. a trend). */
    fun deltaFromCelsius(dc: Float, unit: TemperatureUnit): Float =
        if (unit == TemperatureUnit.FAHRENHEIT) dc * 9f / 5f else dc
}

/** Locale-aware number formatting used across UI, notifications, widgets and reports. */
object Fmt {
    private val locale: Locale get() = Locale.getDefault()

    fun temperature(c: Float, unit: TemperatureUnit): String {
        val v = TemperatureConverter.fromCelsius(c, unit)
        return String.format(locale, "%.1f%s", v, if (unit == TemperatureUnit.FAHRENHEIT) "°F" else "°C")
    }
    fun voltage(mv: Int): String = String.format(locale, "%.2f V", mv / 1000f)
    fun current(ma: Float): String = String.format(locale, "%,.0f mA", ma)
    fun power(w: Float): String = String.format(locale, "%.1f W", w)
    fun capacity(mah: Float): String = String.format(locale, "%,.0f mAh", mah)
    fun energy(mwh: Float): String = String.format(locale, "%,.0f mWh", mwh)
    fun percent(p: Int): String = String.format(locale, "%d%%", p)
    fun percent1(p: Float): String = String.format(locale, "%.1f%%", p)
    fun rate(pph: Float): String = String.format(locale, "%.1f%%/h", pph)
    fun decimal1(v: Float): String = String.format(locale, "%.1f", v)

    /** "1 h 12 min" / "42 min" using compact unit symbols. */
    fun duration(ms: Long, hourSym: String = "h", minSym: String = "min"): String {
        val totalMin = abs(ms) / 60_000
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h > 0 && m > 0 -> String.format(locale, "%d %s %d %s", h, hourSym, m, minSym)
            h > 0 -> String.format(locale, "%d %s", h, hourSym)
            else -> String.format(locale, "%d %s", m, minSym)
        }
    }
}
