package com.chargemaster.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chargemaster.app.core.preferences.DEVICE_STATE_STORE
import com.chargemaster.app.core.preferences.DataStorePreferencesRepository
import com.chargemaster.app.domain.engine.AlertState
import com.chargemaster.app.domain.model.CurrentCalibration
import com.chargemaster.app.domain.model.CurrentSign
import com.chargemaster.app.domain.model.CurrentUnit
import com.chargemaster.app.domain.model.DashboardCard
import com.chargemaster.app.domain.model.MonitoringMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import androidx.datastore.preferences.preferencesDataStoreFile

@RunWith(AndroidJUnit4::class)
class PreferencesRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repo = DataStorePreferencesRepository(context)

    @Test fun defaultsAndRoundTrip() = runTest {
        val d = repo.current()
        assertEquals(MonitoringMode.BALANCED, d.monitoringMode)
        assertEquals(DashboardCard.entries.toSet(), d.dashboardCards)

        repo.setMonitoringMode(MonitoringMode.DETAILED)
        repo.setDashboardCards(setOf(DashboardCard.TEMPERATURE))
        repo.updateAlerts { it.copy(lowBatteryThreshold = 99, highTempThresholdC = 45f) }
        repo.setDesignCapacity(5000)
        val p = repo.current()
        assertEquals(MonitoringMode.DETAILED, p.monitoringMode)
        assertEquals(setOf(DashboardCard.TEMPERATURE), p.dashboardCards)
        assertEquals(50, p.alerts.lowBatteryThreshold) // clamped to the allowed range
        assertEquals(45f, p.alerts.highTempThresholdC, 0.01f)
        assertEquals(5000, p.designCapacityMah)
        repo.setDesignCapacity(null)
        assertEquals(null, repo.current().designCapacityMah)
    }

    @Test fun deviceStateLivesInItsOwnNonBackedUpStore() = runTest {
        repo.setCalibration(CurrentCalibration(CurrentUnit.MILLIAMPERES, CurrentSign.INVERTED))
        repo.setAlertState(AlertState(wasPlugged = true, plugStartLevel = 30, firedThresholds = setOf(80)))
        assertEquals(CurrentCalibration(CurrentUnit.MILLIAMPERES, CurrentSign.INVERTED), repo.calibration())
        assertEquals(setOf(80), repo.alertState().firedThresholds)
        assertEquals(30, repo.alertState().plugStartLevel)
        // The backup exclusion in res/xml relies on this exact location.
        val expected = File(context.filesDir, "datastore/$DEVICE_STATE_STORE.preferences_pb")
        assertEquals(expected.canonicalPath, context.preferencesDataStoreFile(DEVICE_STATE_STORE).canonicalPath)
    }
}
