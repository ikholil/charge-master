package com.hikmahlab.chargemaster.domain

import com.hikmahlab.chargemaster.domain.engine.ChargeSpeed
import com.hikmahlab.chargemaster.domain.engine.SinceUnplugCalculator
import com.hikmahlab.chargemaster.domain.engine.TemperatureZone
import com.hikmahlab.chargemaster.domain.model.ChargingSession
import com.hikmahlab.chargemaster.domain.model.ChargingSource
import com.hikmahlab.chargemaster.domain.model.SessionEndReason
import com.hikmahlab.chargemaster.feature.settings.SessionCsv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class ClassifiersTest {
    private fun session(end: Long?, endLevel: Int?) = ChargingSession(
        id = 1, startTime = 0, endTime = end, lastSampleTime = end ?: 0, startLevel = 20, endLevel = endLevel,
        source = ChargingSource.AC, sampleCount = 4, powerSampleCount = 2, powerSumW = 30.0, peakPowerW = 18f,
        tempSampleCount = 0, tempSumC = 0.0, maxTempC = null, startTempC = null, endTempC = null, interruptions = 0,
        startPrecise = true, endPrecise = true, hadMonitoringGap = false, endReason = SessionEndReason.UNPLUGGED,
    )

    @Test fun speedTiers() {
        assertNull(ChargeSpeed.classify(null))
        assertNull(ChargeSpeed.classify(-3f))
        assertEquals(ChargeSpeed.TRICKLE, ChargeSpeed.classify(1f))
        assertEquals(ChargeSpeed.SLOW, ChargeSpeed.classify(5f))
        assertEquals(ChargeSpeed.STANDARD, ChargeSpeed.classify(10f))
        assertEquals(ChargeSpeed.FAST, ChargeSpeed.classify(25f))
        assertEquals(ChargeSpeed.SUPER_FAST, ChargeSpeed.classify(45f))
    }

    @Test fun temperatureZones() {
        assertNull(TemperatureZone.of(null))
        assertEquals(TemperatureZone.COLD, TemperatureZone.of(5f))
        assertEquals(TemperatureZone.IDEAL, TemperatureZone.of(30f))
        assertEquals(TemperatureZone.WARM, TemperatureZone.of(37f))
        assertEquals(TemperatureZone.HOT, TemperatureZone.of(42f))
        assertEquals(TemperatureZone.CRITICAL, TemperatureZone.of(48f))
    }

    @Test fun sinceUnplugComputesUsageAndRate() {
        val r = SinceUnplugCalculator.compute(session(HOUR, 80), 60, 3 * HOUR)!!
        assertEquals(2 * HOUR, r.elapsedMs)
        assertEquals(20, r.usedPercent)
        assertEquals(10f, r.ratePerHour!!, 0.01f)
    }

    @Test fun sinceUnplugRejectsUnreliableInputs() {
        assertNull(SinceUnplugCalculator.compute(session(null, 80), 60, 3 * HOUR)) // still charging
        assertNull(SinceUnplugCalculator.compute(session(HOUR, 50), 60, 3 * HOUR)) // level rose: unrecorded charge
        assertNull(SinceUnplugCalculator.compute(session(HOUR, 80), 79, HOUR + 5 * MIN)!!.ratePerHour) // too short for a rate
    }

    @Test fun csvHasHeaderAndEscapes() {
        val csv = SessionCsv.build(listOf(session(HOUR, 80)), ZoneOffset.UTC)
        val lines = csv.trimEnd().split("\r\n")
        assertEquals(2, lines.size)
        assertTrue(lines[0].startsWith("start_time,end_time,duration_min"))
        assertTrue(lines[1].startsWith("1970-01-01T00:00:00Z,1970-01-01T01:00:00Z,60,20,80,ac,15.00,18.00"))
    }
}
