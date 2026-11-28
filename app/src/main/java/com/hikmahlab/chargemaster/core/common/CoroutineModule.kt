package com.hikmahlab.chargemaster.core.common

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class ApplicationScope
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class IoDispatcher

fun interface Clock { fun now(): Long }

@Module
@InstallIn(SingletonComponent::class)
object CoroutineModule {
    @Provides @Singleton @ApplicationScope
    fun appScope(@IoDispatcher io: CoroutineDispatcher): CoroutineScope = CoroutineScope(SupervisorJob() + io)

    @Provides @IoDispatcher
    fun io(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    fun clock(): Clock = Clock { System.currentTimeMillis() }
}
