package com.chargemaster.app.feature.widgets

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.chargemaster.app.app.MainActivity
import com.chargemaster.app.core.common.Fmt
import com.chargemaster.app.data.datasource.AndroidBatteryDataSource
import com.chargemaster.app.domain.engine.SnapshotNormalizer
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Quick Settings tile showing the battery level; tapping opens Charge Master. */
@AndroidEntryPoint
class BatteryTileService : TileService() {
    @Inject lateinit var source: AndroidBatteryDataSource

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        val intent = source.stickyIntent()
        val level = intent?.let {
            SnapshotNormalizer.normalizeLevel(
                it.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1),
                it.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1),
            )
        }
        tile.state = Tile.STATE_ACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = level?.let(Fmt::percent)
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
