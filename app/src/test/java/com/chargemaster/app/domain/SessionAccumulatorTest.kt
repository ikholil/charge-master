package com.chargemaster.app.domain

import com.chargemaster.app.domain.engine.SessionAccumulator
import com.chargemaster.app.domain.engine.SessionAccumulator.Update
import com.chargemaster.app.domain.model.ChargingSource
import com.chargemaster.app.domain.model.SessionEndReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionAccumulatorTest {
    private val acc = SessionAccumulator()

    @Test fun `no session while unplugged`() {
        assertEquals(Update.None, acc.onSample(null, null, null, snapshot(0)))
    }

    @Test fun `plug-in starts a precise session when previous sample was unplugged and recent`() {
        val prev = snapshot(0, level = 30)
        val u = acc.onSample(null, prev, null, snapshot(5 * MIN, level = 30, source = ChargingSource.AC))
        val s = (u as Update.Started).session
        assertTrue(s.startPrecise)
        assertEquals(30, s.startLevel)
        assertEquals(ChargingSource.AC, s.source)
        assertTrue(s.isOpen)
    }

    @Test fun `start is imprecise without a recent unplugged observation`() {
        val u = acc.onSample(null, null, null, snapshot(0, source = ChargingSource.USB))
        assertFalse((u as Update.Started).session.startPrecise)
    }

    @Test fun `full lifecycle aggregates power and temperature`() {
        var s = (acc.onSample(null, snapshot(0), null, snapshot(MIN, level = 20, source = ChargingSource.AC, current = 3000f, temp = 30f)) as Update.Started).session
        s = (acc.onSample(s, null, null, snapshot(11 * MIN, level = 35, source = ChargingSource.AC, current = 2000f, temp = 34f)) as Update.Updated).session
        val closed = (acc.onSample(s, null, null, snapshot(21 * MIN, level = 45, source = ChargingSource.NONE)) as Update.Closed).session
        assertEquals(20, closed.startLevel)
        assertEquals(35, closed.endLevel) // last plugged observation, not the unplugged one
        assertEquals(12f, closed.peakPowerW!!, 0.01f)
        assertEquals(10f, closed.averagePowerW!!, 0.01f)
        assertEquals(34f, closed.maxTempC!!, 0.01f)
        assertEquals(32f, closed.averageTempC!!, 0.01f)
        assertEquals(30f, closed.startTempC!!, 0.01f)
        assertEquals(34f, closed.endTempC!!, 0.01f)
        assertTrue(closed.isComplete)
        assertEquals(SessionEndReason.UNPLUGGED, closed.endReason)
    }

    @Test fun `negative power (net discharge while plugged) is not counted as charging power`() {
        val s = (acc.onSample(null, null, null, snapshot(0, source = ChargingSource.USB, current = -200f)) as Update.Started).session
        assertEquals(0, s.powerSampleCount)
        assertNull(s.peakPowerW)
    }

    @Test fun `monitoring gap marks session incomplete`() {
        val s = (acc.onSample(null, snapshot(0), null, snapshot(MIN, source = ChargingSource.AC)) as Update.Started).session
        val u = (acc.onSample(s, null, null, snapshot(3 * HOUR, source = ChargingSource.AC)) as Update.Updated).session
        assertTrue(u.hadMonitoringGap)
        val closed = (acc.onSample(u, null, null, snapshot(3 * HOUR + MIN)) as Update.Closed).session
        assertFalse(closed.isComplete)
    }

    @Test fun `late unplug detection uses last observation as end and is imprecise`() {
        val s = (acc.onSample(null, snapshot(0), null, snapshot(MIN, level = 40, source = ChargingSource.AC)) as Update.Started).session
        val s2 = (acc.onSample(s, null, null, snapshot(30 * MIN, level = 60, source = ChargingSource.AC)) as Update.Updated).session
        val closed = (acc.onSample(s2, null, null, snapshot(4 * HOUR)) as Update.Closed).session
        assertEquals(30 * MIN, closed.endTime)
        assertFalse(closed.endPrecise)
    }

    @Test fun `source change counts as interruption`() {
        val s = (acc.onSample(null, null, null, snapshot(0, source = ChargingSource.USB)) as Update.Started).session
        val u = (acc.onSample(s, null, null, snapshot(MIN, source = ChargingSource.AC)) as Update.Updated).session
        assertEquals(1, u.interruptions)
    }

    @Test fun `quick re-plug resumes previous session as interruption`() {
        val s = (acc.onSample(null, snapshot(0), null, snapshot(MIN, level = 20, source = ChargingSource.AC)) as Update.Started).session
        val s2 = (acc.onSample(s, null, null, snapshot(10 * MIN, level = 30, source = ChargingSource.AC)) as Update.Updated).session
        val closed = (acc.onSample(s2, null, null, snapshot(11 * MIN, level = 30)) as Update.Closed).session.copy(id = 7)
        val resumed = acc.onSample(null, snapshot(11 * MIN), closed, snapshot(12 * MIN, level = 30, source = ChargingSource.AC))
        val r = (resumed as Update.Resumed).session
        assertEquals(7, r.id)
        assertEquals(1, r.interruptions)
        assertTrue(r.isOpen)
        assertEquals(20, r.startLevel)
    }

    @Test fun `tiny session with no gain is discarded`() {
        val s = (acc.onSample(null, null, null, snapshot(0, level = 50, source = ChargingSource.USB)) as Update.Started).session
        val u = acc.onSample(s, null, null, snapshot(20_000, level = 50))
        assertTrue(u is Update.Discarded)
    }

    @Test fun `restart closes at last observation`() {
        val s = (acc.onSample(null, null, null, snapshot(0, source = ChargingSource.AC)) as Update.Started).session
        val closed = acc.closeForRestart(s.copy(lastSampleTime = 20 * MIN))
        assertEquals(20 * MIN, closed.endTime)
        assertEquals(SessionEndReason.DEVICE_RESTART, closed.endReason)
        assertFalse(closed.isComplete)
    }

    @Test fun `missing readings do not fabricate values`() {
        val s = (acc.onSample(null, null, null, snapshot(0, source = ChargingSource.AC, temp = null, voltage = null, level = null)) as Update.Started).session
        assertNull(s.startLevel)
        assertNull(s.averageTempC)
        assertNull(s.averagePowerW)
        val u = (acc.onSample(s, null, null, snapshot(MIN, source = ChargingSource.AC, level = 41)) as Update.Updated).session
        assertEquals(41, u.startLevel) // first real observation fills in
    }
}
