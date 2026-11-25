package com.chargemaster.app.domain

import com.chargemaster.app.domain.engine.SnapshotNormalizer
import com.chargemaster.app.domain.model.BatteryHealthStatus
import com.chargemaster.app.domain.model.CalibrationMode
import com.chargemaster.app.domain.model.ChargingSource
import com.chargemaster.app.domain.model.ChargingStatus
import com.chargemaster.app.domain.model.CurrentCalibration
import com.chargemaster.app.domain.model.CurrentSign
import com.chargemaster.app.domain.model.CurrentUnit
import com.chargemaster.app.domain.model.PowerEstimator
import com.chargemaster.app.domain.model.ThermalStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotNormalizerTest {
    private fun norm(r: com.chargemaster.app.domain.engine.RawBatteryData, cal: CurrentCalibration = CurrentCalibration(),
                     unit: CalibrationMode = CalibrationMode.AUTO, sign: CalibrationMode = CalibrationMode.AUTO) =
        SnapshotNormalizer.normalize(r, cal, unit, sign)

    @Test fun `level is scaled to percent`() {
        assertEquals(50, SnapshotNormalizer.normalizeLevel(50, 100))
        assertEquals(75, SnapshotNormalizer.normalizeLevel(150, 200))
        assertEquals(100, SnapshotNormalizer.normalizeLevel(255, 255))
    }

    @Test fun `missing level or scale is unavailable`() {
        assertNull(SnapshotNormalizer.normalizeLevel(-1, 100))
        assertNull(SnapshotNormalizer.normalizeLevel(50, 0))
    }

    @Test fun `temperature is tenths of celsius and implausible values rejected`() {
        assertEquals(30.5f, SnapshotNormalizer.normalizeTemperature(305)!!, 0.001f)
        assertNull(SnapshotNormalizer.normalizeTemperature(Int.MIN_VALUE))
        assertNull(SnapshotNormalizer.normalizeTemperature(2000))
        assertNull(SnapshotNormalizer.normalizeTemperature(-500))
    }

    @Test fun `voltage in millivolts, volts converted, garbage rejected`() {
        assertEquals(3980, SnapshotNormalizer.normalizeVoltage(3980))
        assertEquals(4000, SnapshotNormalizer.normalizeVoltage(4))
        assertNull(SnapshotNormalizer.normalizeVoltage(0))
        assertNull(SnapshotNormalizer.normalizeVoltage(Int.MIN_VALUE))
        assertNull(SnapshotNormalizer.normalizeVoltage(99_000))
    }

    @Test fun `unsupported current properties are null, never zero`() {
        val s = norm(raw(currentNow = Int.MIN_VALUE)).snapshot
        assertNull(s.currentNowMa)
        assertNull(s.powerW)
        val z = norm(raw(currentNow = 0)).snapshot
        assertNull(z.currentNowMa)
    }

    @Test fun `microamp values converted to milliamps and unit learned`() {
        val r = norm(raw(status = 2, plugged = 1, currentNow = 2_500_000))
        assertEquals(2500f, r.snapshot.currentNowMa!!, 0.01f)
        assertEquals(CurrentUnit.MICROAMPERES, r.calibration.learnedUnit)
    }

    @Test fun `milliamp devices detected from charging current`() {
        val r = norm(raw(status = 2, plugged = 1, currentNow = 2500))
        assertEquals(2500f, r.snapshot.currentNowMa!!, 0.01f)
        assertEquals(CurrentUnit.MILLIAMPERES, r.calibration.learnedUnit)
    }

    @Test fun `learned microamp unit applies to small idle values`() {
        val r = norm(raw(currentNow = -8000), CurrentCalibration(learnedUnit = CurrentUnit.MICROAMPERES))
        assertEquals(-8f, r.snapshot.currentNowMa!!, 0.01f)
    }

    @Test fun `inverted sign devices are learned while charging and corrected`() {
        val first = norm(raw(status = 2, plugged = 2, currentNow = -1_500_000))
        assertEquals(CurrentSign.INVERTED, first.calibration.learnedSign)
        assertEquals(1500f, first.snapshot.currentNowMa!!, 0.01f)
        // Later, discharging on the same device: raw positive means discharge.
        val later = norm(raw(currentNow = 300_000), first.calibration)
        assertEquals(-300f, later.snapshot.currentNowMa!!, 0.01f)
        assertFalse(later.snapshot.currentCalibrationUncertain)
    }

    @Test fun `standard sign devices keep the sign`() {
        val r = norm(raw(status = 2, plugged = 1, currentNow = 1_000_000))
        assertEquals(CurrentSign.STANDARD, r.calibration.learnedSign)
        assertTrue(r.snapshot.currentNowMa!! > 0)
    }

    @Test fun `sign is uncertain before any charging observation`() {
        val r = norm(raw(currentNow = -400_000))
        assertTrue(r.snapshot.currentCalibrationUncertain)
    }

    @Test fun `user override forces inverted sign`() {
        val r = norm(raw(currentNow = 500_000), sign = CalibrationMode.FORCE_SECONDARY)
        assertEquals(-500f, r.snapshot.currentNowMa!!, 0.01f)
    }

    @Test fun `user override forces milliamp unit`() {
        val r = norm(raw(currentNow = 25_000), unit = CalibrationMode.FORCE_SECONDARY, sign = CalibrationMode.FORCE_PRIMARY)
        // 25 000 mA is out of the plausible range and must be rejected rather than displayed.
        assertNull(r.snapshot.currentNowMa)
    }

    @Test fun `charge counter in microamp-hours converted, unsupported rejected`() {
        assertEquals(3200f, SnapshotNormalizer.normalizeChargeCounter(3_200_000)!!, 0.01f)
        assertEquals(3200f, SnapshotNormalizer.normalizeChargeCounter(3200)!!, 0.01f)
        assertNull(SnapshotNormalizer.normalizeChargeCounter(Int.MIN_VALUE))
        assertNull(SnapshotNormalizer.normalizeChargeCounter(0))
    }

    @Test fun `status, source, health and thermal mapping`() {
        assertEquals(ChargingStatus.CHARGING, SnapshotNormalizer.mapStatus(2))
        assertEquals(ChargingStatus.FULL, SnapshotNormalizer.mapStatus(5))
        assertEquals(ChargingStatus.UNKNOWN, SnapshotNormalizer.mapStatus(-1))
        assertEquals(ChargingSource.AC, SnapshotNormalizer.mapPlugged(1))
        assertEquals(ChargingSource.USB, SnapshotNormalizer.mapPlugged(2))
        assertEquals(ChargingSource.WIRELESS, SnapshotNormalizer.mapPlugged(4))
        assertEquals(ChargingSource.DOCK, SnapshotNormalizer.mapPlugged(8))
        assertEquals(ChargingSource.NONE, SnapshotNormalizer.mapPlugged(0))
        assertEquals(ChargingSource.UNKNOWN, SnapshotNormalizer.mapPlugged(-1))
        assertEquals(BatteryHealthStatus.OVERHEAT, SnapshotNormalizer.mapHealth(3))
        assertEquals(BatteryHealthStatus.UNKNOWN, SnapshotNormalizer.mapHealth(1))
        assertEquals(ThermalStatus.UNSUPPORTED, SnapshotNormalizer.mapThermal(null))
        assertEquals(ThermalStatus.SEVERE, SnapshotNormalizer.mapThermal(3))
    }

    @Test fun `cycle count only when reported`() {
        assertNull(norm(raw(cycle = -1)).snapshot.cycleCount)
        assertEquals(412, norm(raw(cycle = 412)).snapshot.cycleCount)
    }

    @Test fun `power is voltage times current with sanity bound`() {
        assertEquals(12.0f, PowerEstimator.estimateWatts(4000, 3000f)!!, 0.001f)
        assertEquals(-1.6f, PowerEstimator.estimateWatts(4000, -400f)!!, 0.001f)
        assertNull(PowerEstimator.estimateWatts(null, 3000f))
        assertNull(PowerEstimator.estimateWatts(4000, null))
        assertNull(PowerEstimator.estimateWatts(4000, 0f))
        assertNull(PowerEstimator.estimateWatts(5000, 60_000f))
    }
}
