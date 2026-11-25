package com.chargemaster.app.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(
    entities = [BatteryReadingEntity::class, ChargingSessionEntity::class, BatteryHealthSnapshotEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class ChargeMasterDatabase : RoomDatabase() {
    abstract fun readingDao(): BatteryReadingDao
    abstract fun sessionDao(): ChargingSessionDao
    abstract fun healthDao(): BatteryHealthDao

    companion object {
        const val NAME = "charge_master.db"

        /**
         * Add a Migration here for every schema bump (schemas are exported to app/schemas
         * and verified by MigrationTest). Never fall back to destructive migration in release.
         */
        val MIGRATIONS = arrayOf<androidx.room.migration.Migration>()

        fun build(context: Context): ChargeMasterDatabase =
            Room.databaseBuilder(context, ChargeMasterDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun db(@ApplicationContext context: Context) = ChargeMasterDatabase.build(context)
    @Provides fun readingDao(db: ChargeMasterDatabase) = db.readingDao()
    @Provides fun sessionDao(db: ChargeMasterDatabase) = db.sessionDao()
    @Provides fun healthDao(db: ChargeMasterDatabase) = db.healthDao()
}
