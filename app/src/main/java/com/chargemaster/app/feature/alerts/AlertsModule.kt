package com.chargemaster.app.feature.alerts

import com.chargemaster.app.domain.usecase.AlertSink
import com.chargemaster.app.domain.usecase.WidgetRefresher
import com.chargemaster.app.feature.widgets.GlanceWidgetRefresher
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
