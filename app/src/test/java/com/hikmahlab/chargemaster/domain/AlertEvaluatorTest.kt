package com.hikmahlab.chargemaster.domain

import com.hikmahlab.chargemaster.domain.engine.AlertEvaluator
import com.hikmahlab.chargemaster.domain.engine.AlertState
import com.hikmahlab.chargemaster.domain.engine.BatteryAlert
import com.hikmahlab.chargemaster.domain.engine.SessionAccumulator
import com.hikmahlab.chargemaster.domain.model.AlertPreferences
import com.hikmahlab.chargemaster.domain.model.ChargingSource
import com.hikmahlab.chargemaster.domain.model.ChargingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertEvaluatorTest {
    private val prefs = AlertPreferences(threshold90Enabled = true, fullEnabled = true, chargingStartedEnabled = true, chargingStoppedEnabled = true)

    private fun run(states: List<com.hikmahlab.chargemaster.domain.model.BatterySnapshot>, p: AlertPreferences = prefs, typical: Float? = null): List<BatteryAlert> {
        var st = AlertState()
        val all = mutableListOf<BatteryAlert>()
        states.forEach {
            val r = AlertEvaluator.evaluate(st, it, p, null, typical)
            st = r.state; all += r.alerts
        }
        return all
    }

    @Test fun `low battery fires once and resets after recovery`() {
        val a = run(listOf(snapshot(0, 25), snapshot(1, 20), snapshot(2, 19), snapshot(3, 18)))
        assertEquals(listOf<BatteryAlert>(BatteryAlert.LowBattery(20)), a)
        val b = run(listOf(snapshot(0, 20), snapshot(1, 30), snapshot(2, 20)))
        assertEquals(2, b.count { it is BatteryAlert.LowBattery })
    }

    @Test fun `low battery does not fire while plugged`() {
        val a = run(listOf(snapshot(0, 10, source = ChargingSource.AC)), p = prefs.copy(chargingStartedEnabled = false))
        assertTrue(a.none { it is BatteryAlert.LowBattery })
    }

    @Test fun `threshold fires when crossed during charge, once`() {
        val c = ChargingSource.AC
        val a = run(listOf(snapshot(0, 70), snapshot(1, 75, c), snapshot(2, 80, c), snapshot(3, 81, c), snapshot(4, 85, c)))
        assertEquals(1, a.count { it == BatteryAlert.ThresholdReached(80, 80) })
    }

    @Test fun `threshold does not fire if plugged in above it`() {
        val c = ChargingSource.AC
        val a = run(listOf(snapshot(0, 85), snapshot(1, 85, c), snapshot(2, 86, c)))
        assertTrue(a.none { it is BatteryAlert.ThresholdReached })
    }

    @Test fun `multiple thresholds crossed at once produce one alert for the highest`() {
        val c = ChargingSource.AC
        val a = run(listOf(snapshot(0, 70), snapshot(1, 70, c), snapshot(2, 92, c)))
        assertEquals(listOf(BatteryAlert.ThresholdReached(90, 92)), a.filterIsInstance<BatteryAlert.ThresholdReached>())
    }

    @Test fun `thresholds re-arm after unplug`() {
        val c = ChargingSource.AC
        val a = run(listOf(snapshot(0, 70), snapshot(1, 79, c), snapshot(2, 80, c), snapshot(3, 70), snapshot(4, 75, c), snapshot(5, 80, c)))
        assertEquals(2, a.count { it is BatteryAlert.ThresholdReached })
    }

    @Test fun `plug transitions fire started and stopped`() {
        val a = run(listOf(snapshot(0, 50), snapshot(1, 50, ChargingSource.USB), snapshot(2, 55)))
        assertTrue(a.contains(BatteryAlert.ChargingStarted(ChargingSource.USB, 50)))
        assertTrue(a.contains(BatteryAlert.ChargingStopped(55)))
    }

    @Test fun `unplugging after a completed charge is not an interruption`() {
        val c = ChargingSource.AC
        val a = run(listOf(snapshot(0, 90), snapshot(1, 99, c), snapshot(2, 100, c, status = ChargingStatus.FULL), snapshot(3, 100)))
        assertTrue(a.none { it is BatteryAlert.ChargingStopped })
    }

    @Test fun `low battery does not re-fire after a brief plug-in at low level`() {
        val a = run(listOf(snapshot(0, 19), snapshot(1, 19, ChargingSource.USB), snapshot(2, 19)), p = prefs.copy(chargingStartedEnabled = false, chargingStoppedEnabled = false))
        assertEquals(1, a.count { it is BatteryAlert.LowBattery })
    }

    @Test fun `first ever sample does not fire a plug transition`() {
        val a = run(listOf(snapshot(0, 50, ChargingSource.USB)))
        assertTrue(a.none { it is BatteryAlert.ChargingStarted })
    }

    @Test fun `high temperature uses hysteresis`() {
        val a = run(listOf(snapshot(0, temp = 41f), snapshot(1, temp = 42.5f), snapshot(2, temp = 43f), snapshot(3, temp = 41f), snapshot(4, temp = 43f),
            snapshot(5, temp = 39f), snapshot(6, temp = 42f)))
        assertEquals(2, a.count { it is BatteryAlert.HighTemperature })
    }

    @Test fun `disabled alerts never fire`() {
        val off = AlertPreferences(lowBatteryEnabled = false, threshold80Enabled = false, highTempEnabled = false, chargingCompleteEnabled = false, longChargeEnabled = false, unusualSpeedEnabled = false)
        val a = run(listOf(snapshot(0, 10, temp = 50f), snapshot(1, 85, ChargingSource.AC, temp = 50f)), p = off)
        assertTrue(a.isEmpty())
    }

    @Test fun `charging complete fires on FULL status`() {
        val c = ChargingSource.AC
        val a = run(listOf(snapshot(0, 90), snapshot(1, 99, c), snapshot(2, 100, c, status = ChargingStatus.FULL)), p = prefs.copy(fullEnabled = false))
        assertTrue(a.contains(BatteryAlert.ChargingComplete))
    }

    @Test fun `unusual speed needs history and fires once`() {
        val acc = SessionAccumulator()
        var session = (acc.onSample(null, null, null, snapshot(0, 20, ChargingSource.AC, current = 500f)) as SessionAccumulator.Update.Started).session
        repeat(3) { i -> session = (acc.onSample(session, null, null, snapshot((i + 1) * MIN, 21 + i, ChargingSource.AC, current = 500f)) as SessionAccumulator.Update.Updated).session }
        var st = AlertState(wasPlugged = true, plugStartLevel = 20)
        val noHistory = AlertEvaluator.evaluate(st, snapshot(5 * MIN, 25, ChargingSource.AC, current = 500f), prefs, session, typicalPowerW = null)
        assertTrue(noHistory.alerts.none { it is BatteryAlert.UnusualSpeed })
        val r1 = AlertEvaluator.evaluate(st, snapshot(5 * MIN, 25, ChargingSource.AC, current = 500f), prefs, session, typicalPowerW = 15f)
        assertTrue(r1.alerts.any { it is BatteryAlert.UnusualSpeed })
        st = r1.state
        val r2 = AlertEvaluator.evaluate(st, snapshot(6 * MIN, 26, ChargingSource.AC, current = 500f), prefs, session, typicalPowerW = 15f)
        assertTrue(r2.alerts.none { it is BatteryAlert.UnusualSpeed })
    }

    @Test fun `long charging reminder`() {
        val acc = SessionAccumulator()
        val session = (acc.onSample(null, null, null, snapshot(0, 20, ChargingSource.AC)) as SessionAccumulator.Update.Started).session
        val r = AlertEvaluator.evaluate(AlertState(wasPlugged = true, plugStartLevel = 20), snapshot(7 * HOUR, 100, ChargingSource.AC), prefs.copy(fullEnabled = false), session, null)
        assertTrue(r.alerts.contains(BatteryAlert.LongCharging(6)))
    }
}
