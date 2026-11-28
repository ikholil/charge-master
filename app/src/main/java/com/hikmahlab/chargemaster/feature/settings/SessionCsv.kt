package com.hikmahlab.chargemaster.feature.settings

import com.hikmahlab.chargemaster.domain.model.ChargingSession
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Serialises charging sessions to RFC 4180 CSV. Numbers always use '.' so spreadsheets parse them. */
object SessionCsv {
    private val HEADER = listOf(
        "start_time", "end_time", "duration_min", "start_level", "end_level", "source",
        "avg_power_w", "peak_power_w", "avg_temp_c", "max_temp_c", "percent_per_hour",
        "interruptions", "samples", "complete",
    )

    fun build(sessions: List<ChargingSession>, zone: ZoneId = ZoneId.systemDefault()): String {
        val fmt = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(zone)
        fun time(ms: Long?) = ms?.let { fmt.format(Instant.ofEpochMilli(it)) } ?: ""
        fun num(v: Float?) = v?.let { String.format(Locale.ROOT, "%.2f", it) } ?: ""
        val sb = StringBuilder(HEADER.joinToString(",")).append("\r\n")
        sessions.sortedBy { it.startTime }.forEach { s ->
            listOf(
                time(s.startTime), time(s.endTime), (s.durationMs() / 60_000).toString(),
                s.startLevel?.toString() ?: "", s.endLevel?.toString() ?: "", s.source.name.lowercase(Locale.ROOT),
                num(s.averagePowerW), num(s.peakPowerW), num(s.averageTempC), num(s.maxTempC), num(s.percentPerHour),
                s.interruptions.toString(), s.sampleCount.toString(), s.isComplete.toString(),
            ).joinToString(",") { escape(it) }.let { sb.append(it).append("\r\n") }
        }
        return sb.toString()
    }

    private fun escape(v: String) = if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
}
