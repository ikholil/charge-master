package com.chargemaster.app.data.repository

import com.chargemaster.app.core.preferences.DataStorePreferencesRepository
import com.chargemaster.app.domain.repository.BatteryRepository
import com.chargemaster.app.domain.repository.HealthRepository
import com.chargemaster.app.domain.repository.PreferencesRepository
import com.chargemaster.app.domain.repository.SessionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun battery(impl: BatteryRepositoryImpl): BatteryRepository
    @Binds abstract fun sessions(impl: SessionRepositoryImpl): SessionRepository
    @Binds abstract fun health(impl: HealthRepositoryImpl): HealthRepository
    @Binds abstract fun prefs(impl: DataStorePreferencesRepository): PreferencesRepository
}
