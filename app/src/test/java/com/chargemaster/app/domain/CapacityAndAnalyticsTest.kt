package com.chargemaster.app.domain

import com.chargemaster.app.core.common.TemperatureConverter
import com.chargemaster.app.domain.engine.CapacityEstimator
import com.chargemaster.app.domain.engine.ChargingAnalyzer
import com.chargemaster.app.domain.engine.DischargeAnalyzer
import com.chargemaster.app.domain.engine.EstimateMethod
import com.chargemaster.app.domain.engine.SessionAccumulator
import com.chargemaster.app.domain.engine.TemperatureAnalyzer
import com.chargemaster.app.domain.engine.TimeEstimator
import com.chargemaster.app.domain.model.BatteryHealthSnapshot
import com.chargemaster.app.domain.model.BatteryHealthStatus
import com.chargemaster.app.domain.model.ChargingSource
import com.chargemaster.app.domain.model.Confidence
import com.chargemaster.app.domain.model.TemperatureUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CapacityEstimatorTest {
    /** Realistic counter: real capacity 4200 mAh plus a small fixed offset, so ratio isn't constant. */
    private fun charge(from: Int, to: Int, capacity: Float = 4200f, offset: Float = 150f) =
        (from..to step 5).mapIndexed { i, l -> reading(i * 10 * MIN, l, plugged = true, counter = offset + capacity * l / 100f) }

    @Test fun `estimates capacity from a wide span`() {
        val e = CapacityEstimator.estimateFromReadings(charge(20, 90))
        assertEquals(4200f, e.capacityMah!!, 1f)
        assertEquals(Confidence.HIGH, e.confidence)
        assertEquals(70, e.levelSpan)
    }

    @Test fun `narrow span is insufficient`() {
        val e = CapacityEstimator.estimateFromReadings(charge(60, 80))
        assertNull(e.capacityMah)
        assertEquals(Confidence.INSUFFICIENT, e.confidence)
    }

    @Test fun `no charge counter means no estimate`() {
        val r = (20..90 step 10).mapIndexed { i, l -> reading(i * MIN, l, plugged = true, counter = null) }
        assertNull(CapacityEstimator.estimateFromReadings(r).capacityMah)
    }

    @Test fun `counter derived from level is flagged low confidence`() {
        val e = CapacityEstimator.estimateFromReadings(charge(20, 90, offset = 0f))
        assertTrue(e.counterDerivedFromLevel)
        assertEquals(Confidence.LOW, e.confidence)
    }

    @Test fun `health percent requires design capacity and confidence`() {
        assertNull(CapacityEstimator.healthPercent(4000f, Confidence.HIGH, null))
        assertNull(CapacityEstimator.healthPercent(4000f, Confidence.LOW, 5000))
        assertEquals(80f, CapacityEstimator.healthPercent(4000f, Confidence.MEDIUM, 5000)!!, 0.01f)
    }

    private fun snap(day: Int, cap: Float, c: Confidence = Confidence.HIGH) =
        BatteryHealthSnapshot(timestamp = day * 86_400_000L, reportedHealth = BatteryHealthStatus.GOOD, estimatedCapacityMah = cap,
            designCapacityMah = 5000, confidence = c, sessionId = null)

    @Test fun `combine uses median of reliable estimates`() {
        val (cap, conf) = CapacityEstimator.combine(listOf(snap(1, 4000f), snap(2, 4100f), snap(3, 9000f, Confidence.LOW), snap(4, 4050f)))
        assertEquals(4050f, cap!!, 0.1f)
        assertEquals(Confidence.HIGH, conf)
    }

    @Test fun `combine with nothing is insufficient`() {
        assertEquals(null to Confidence.INSUFFICIENT, CapacityEstimator.combine(emptyList()))
    }

    @Test fun `wear trend needs enough span`() {
        assertNull(CapacityEstimator.wearTrendPerMonth(listOf(snap(0, 4500f), snap(3, 4490f), snap(6, 4480f)), 5000))
        val trend = CapacityEstimator.wearTrendPerMonth(listOf(snap(0, 4500f), snap(30, 4450f), snap(60, 4400f)), 5000)!!
        assertEquals(-1f, trend, 0.01f) // 50 mAh/30d = 1 % of 5000
    }
}

class DischargeAndTemperatureTest {
    @Test fun `discharge rate from unplugged pairs only`() {
        val r = listOf(
            reading(0, 100), reading(HOUR, 90), reading(2 * HOUR, 80),
            reading(3 * HOUR, 81, plugged = true), reading(4 * HOUR, 95, plugged = true),
        )
        val s = DischargeAnalyzer.analyze(r, 24 * HOUR)
        assertEquals(10f, s.averageRatePerHour!!, 0.01f)
        assertEquals(2f, s.dischargingHours, 0.01f)
        assertEquals(1f, s.chargingHours, 0.01f)
    }

    @Test fun `gaps larger than two hours are ignored`() {
        val s = DischargeAnalyzer.analyze(listOf(reading(0, 100), reading(5 * HOUR, 50)), 24 * HOUR)
        assertNull(s.averageRatePerHour)
        assertEquals(0f, s.observedHours, 0.001f)
    }

    @Test fun `screen on and off rates are separated`() {
        val r = listOf(
            reading(0, 100, screenOn = true), reading(HOUR, 85, screenOn = true),
            reading(2 * HOUR, 84, screenOn = false), reading(3 * HOUR, 83, screenOn = false),
        )
        val s = DischargeAnalyzer.analyze(r, 24 * HOUR)
        assertEquals(15f, s.screenOnRatePerHour!!, 0.01f)
        assertEquals(1f, s.screenOffRatePerHour!!, 0.01f)
    }

    @Test fun `remaining time from rate`() {
        assertEquals(300L, TimeEstimator.timeRemainingOnBattery(50, 10f)!!.minutes)
        assertNull(TimeEstimator.timeRemainingOnBattery(50, null))
        assertNull(TimeEstimator.timeRemainingOnBattery(null, 10f))
    }

    @Test fun `temperature stats split by charging state`() {
        val r = listOf(reading(0, 50, temp = 30f), reading(MIN, 50, temp = 34f), reading(2 * MIN, 50, plugged = true, temp = 40f), reading(3 * MIN, 51, temp = null))
        val s = TemperatureAnalyzer.analyze(r)
        assertEquals(30f, s.min!!, 0.01f)
        assertEquals(40f, s.max!!, 0.01f)
        assertEquals(40f, s.chargingMax!!, 0.01f)
        assertEquals(32f, s.dischargingAverage!!, 0.01f)
        assertEquals(3, s.sampleCount)
    }

    @Test fun `temperature conversion`() {
        assertEquals(98.6f, TemperatureConverter.celsiusToFahrenheit(37f), 0.01f)
        assertEquals(0f, TemperatureConverter.fahrenheitToCelsius(32f), 0.01f)
        assertEquals(30f, TemperatureConverter.fromCelsius(30f, TemperatureUnit.CELSIUS), 0.01f)
        assertEquals(9f, TemperatureConverter.deltaFromCelsius(5f, TemperatureUnit.FAHRENHEIT), 0.01f)
    }
}

class ChargingAnalyticsTest {
    private val acc = SessionAccumulator()

    private fun session(start: Long, from: Int, to: Int, minutes: Int, source: ChargingSource = ChargingSource.AC, current: Float = 2000f) =
        run {
            var s = (acc.onSample(null, snapshot(start - MIN), null, snapshot(start, from, source, current = current)) as SessionAccumulator.Update.Started).session
            repeat(3) { s = (acc.onSample(s, null, null, snapshot(start + (it + 1) * minutes / 4 * MIN, from + (to - from) * (it + 1) / 4, source, current = current)) as SessionAccumulator.Update.Updated).session }
            (acc.onSample(s, null, null, snapshot(start + minutes * MIN, to)) as SessionAccumulator.Update.Closed).session
                .copy(endLevel = to)
        }

    @Test fun `fastest, slowest and per-source stats`() {
        val now = 10 * 86_400_000L
        val fast = session(now - 3 * 86_400_000L, 20, 80, 60)
        val slow = session(now - 2 * 86_400_000L, 20, 50, 120, ChargingSource.USB, current = 500f)
        val stats = ChargingAnalyzer.analyze(listOf(fast, slow), now, 7, java.time.ZoneOffset.UTC)
        assertEquals(fast, stats.fastest)
        assertEquals(slow, stats.slowest)
        assertEquals(2, stats.bySource.size)
        assertEquals(7, stats.sessionsPerDay.size)
        assertEquals(2, stats.sessionsPerDay.sum())
        assertEquals(java.time.LocalDate.of(1970, 1, 11), stats.days.last())
        assertEquals(1, stats.sessionsPerDay[3]) // Jan 8
        assertEquals(1, stats.sessionsPerDay[4]) // Jan 9
    }

    @Test fun `sessions are bucketed by local calendar day`() {
        // 23:30 and 00:30 local time fall on different days even though they're 1 h apart.
        val zone = java.time.ZoneId.of("Asia/Dhaka")
        val day = java.time.LocalDate.of(2026, 11, 20)
        fun at(h: Int, m: Int) = day.atTime(h, m).atZone(zone).toInstant().toEpochMilli()
        val a = session(at(23, 0), 20, 40, 20)
        val b = session(at(23, 0) + 90 * MIN, 40, 60, 20)
        val stats = ChargingAnalyzer.analyze(listOf(a, b), at(23, 0) + 2 * HOUR, 2, zone)
        assertEquals(listOf(1, 1), stats.sessionsPerDay)
    }

    @Test fun `typical power needs five sessions`() {
        val list = (1..4).map { session(it * 86_400_000L, 20, 60, 60) }
        assertNull(ChargingAnalyzer.typicalPower(list, ChargingSource.AC))
        val more = list + session(6 * 86_400_000L, 20, 60, 60)
        assertNotNull(ChargingAnalyzer.typicalPower(more, ChargingSource.AC))
        assertNull(ChargingAnalyzer.typicalPower(more, ChargingSource.USB))
    }

    @Test fun `time to level prefers system estimate then observed rate`() {
        val s = session(0, 20, 50, 30).copy(endTime = null, lastSampleTime = 30 * MIN, endLevel = 50)
        assertEquals(EstimateMethod.SYSTEM, TimeEstimator.timeToLevel(100, 50, 40 * MIN, s, null, null)!!.method)
        val obs = TimeEstimator.timeToLevel(80, 50, null, s, null, null)!!
        assertEquals(EstimateMethod.OBSERVED_RATE, obs.method)
        assertEquals(30L, obs.minutes) // 30 % in 30 min → 30 more %
        assertEquals(0L, TimeEstimator.timeToLevel(80, 85, null, s, null, null)!!.minutes)
        assertNull(TimeEstimator.timeToLevel(80, 50, null, null, null, null))
        assertEquals(EstimateMethod.CURRENT_AND_CAPACITY, TimeEstimator.timeToLevel(80, 50, null, null, 2000f, 4000f)!!.method)
    }
}

class HabitAnalyzerTest {
    private val acc = SessionAccumulator()
    private val prefs = com.chargemaster.app.domain.model.AlertPreferences()

    private fun s(from: Int, to: Int, maxTemp: Float = 32f, minutes: Int = 60) = run {
        var x = (acc.onSample(null, null, null, snapshot(0, from, ChargingSource.AC, temp = maxTemp)) as SessionAccumulator.Update.Started).session
        x = (acc.onSample(x, null, null, snapshot(minutes * MIN, to, ChargingSource.AC, temp = maxTemp)) as SessionAccumulator.Update.Updated).session
        (acc.onSample(x, null, null, snapshot(minutes * MIN + MIN, to)) as SessionAccumulator.Update.Closed).session
    }

    @Test fun `needs minimum sessions`() {
        val h = com.chargemaster.app.domain.engine.HabitAnalyzer.analyze(listOf(s(30, 80), s(30, 80)), prefs)
        assertNull(h.score)
        assertEquals(2, h.sessionCount)
    }

    @Test fun `perfect habits score 100`() {
        val h = com.chargemaster.app.domain.engine.HabitAnalyzer.analyze(List(4) { s(30, 80) }, prefs)
        assertEquals(100, h.score)
        assertEquals(4, h.indicators.size)
    }

    @Test fun `indicators reflect each habit`() {
        val list = listOf(s(30, 80), s(10, 100), s(25, 85, maxTemp = 45f), s(40, 100, minutes = 8 * 60))
        val h = com.chargemaster.app.domain.engine.HabitAnalyzer.analyze(list, prefs)
        val byType = h.indicators.associateBy { it.indicator }
        assertEquals(2, byType.getValue(com.chargemaster.app.domain.engine.HabitAnalyzer.Indicator.STOPPED_NEAR_TARGET).met)
        assertEquals(3, byType.getValue(com.chargemaster.app.domain.engine.HabitAnalyzer.Indicator.AVOIDED_DEEP_DISCHARGE).met)
        assertEquals(3, byType.getValue(com.chargemaster.app.domain.engine.HabitAnalyzer.Indicator.STAYED_COOL).met)
        assertEquals(3, byType.getValue(com.chargemaster.app.domain.engine.HabitAnalyzer.Indicator.AVOIDED_LONG_CHARGES).met)
        assertEquals(69, h.score) // mean of 0.5, 0.75, 0.75, 0.75
    }
}
