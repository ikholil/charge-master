package com.hikmahlab.chargemaster.feature.alerts

import com.hikmahlab.chargemaster.domain.usecase.AlertSink
import com.hikmahlab.chargemaster.domain.usecase.WidgetRefresher
import com.hikmahlab.chargemaster.feature.widgets.GlanceWidgetRefresher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class OutputsModule {
    @Binds abstract fun alertSink(impl: AndroidAlertSink): AlertSink
    @Binds abstract fun widgets(impl: GlanceWidgetRefresher): WidgetRefresher
}
