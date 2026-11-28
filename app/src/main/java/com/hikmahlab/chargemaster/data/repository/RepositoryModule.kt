package com.hikmahlab.chargemaster.data.repository

import com.hikmahlab.chargemaster.core.preferences.DataStorePreferencesRepository
import com.hikmahlab.chargemaster.domain.repository.BatteryRepository
import com.hikmahlab.chargemaster.domain.repository.HealthRepository
import com.hikmahlab.chargemaster.domain.repository.PreferencesRepository
import com.hikmahlab.chargemaster.domain.repository.SessionRepository
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
