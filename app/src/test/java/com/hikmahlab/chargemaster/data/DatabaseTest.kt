package com.hikmahlab.chargemaster.data

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hikmahlab.chargemaster.core.database.ChargeMasterDatabase
import com.hikmahlab.chargemaster.data.local.toDomain
import com.hikmahlab.chargemaster.data.local.toEntity
import com.hikmahlab.chargemaster.domain.MIN
import com.hikmahlab.chargemaster.domain.engine.SessionAccumulator
import com.hikmahlab.chargemaster.domain.model.ChargingSource
import com.hikmahlab.chargemaster.domain.reading
import com.hikmahlab.chargemaster.domain.snapshot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseTest {
    private lateinit var db: ChargeMasterDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), ChargeMasterDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun tearDown() = db.close()

    @Test fun readingsRoundTripPreservesNulls() = runTest {
        val dao = db.readingDao()
        dao.insert(reading(1000, null, temp = null, voltage = null, counter = null).toEntity())
        dao.insert(reading(2000, 55, plugged = true, counter = 3000f).toEntity())
        val all = dao.between(0, 5000).map { it.toDomain() }
        assertEquals(2, all.size)
        assertNull(all[0].level); assertNull(all[0].temperatureC); assertNull(all[0].voltageMv)
        assertEquals(ChargingSource.AC, all[1].source)
        assertEquals(3000f, all[1].chargeCounterMah!!, 0.01f)
        assertEquals(2000L, dao.latest()!!.timestamp)
    }

    @Test fun retentionPruning() = runTest {
        val dao = db.readingDao()
        (1..10).forEach { dao.insert(reading(it * 1000L, 50).toEntity()) }
        assertEquals(4, dao.deleteOlderThan(5000))
        assertEquals(6, dao.observeCount().first())
    }

    @Test fun sessionLifecycleInDatabase() = runTest {
        val dao = db.sessionDao()
        val acc = SessionAccumulator()
        val started = (acc.onSample(null, snapshot(0), null, snapshot(MIN, 20, ChargingSource.USB)) as SessionAccumulator.Update.Started).session
        val id = dao.insert(started.toEntity())
        assertEquals(id, dao.open()!!.id)
        val closed = (acc.onSample(started.copy(id = id), null, null, snapshot(2 * MIN, 21)) as SessionAccumulator.Update.Closed).session
        dao.update(closed.toEntity())
        assertNull(dao.open())
        val fromDb = dao.lastClosed()!!.toDomain()
        assertEquals(closed, fromDb)
    }
}

/** Verifies the exported schema (app/schemas) matches the entities; add a test per migration here. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), ChargeMasterDatabase::class.java)

    @Test fun createVersion1FromExportedSchema() {
        helper.createDatabase("migration-test", 1).use { db ->
            db.execSQL("INSERT INTO battery_readings (timestamp, batteryLevel, chargingStatus, chargingSource) VALUES (1, 50, 'CHARGING', 'AC')")
            db.query("SELECT COUNT(*) FROM battery_readings").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
        }
        // Opening with Room validates the schema against the current entities.
        helper.runMigrationsAndValidate("migration-test", 1, true, *ChargeMasterDatabase.MIGRATIONS)
    }
}
