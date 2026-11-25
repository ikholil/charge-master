package com.chargemaster.app.data.datasource

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import com.chargemaster.app.domain.engine.RawBatteryData
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads battery data through public Android APIs only. No root, no hidden APIs.
 * Each call is cheap: the sticky broadcast is cached by the system and property reads are
 * single binder calls. Must be called off the main thread.
 */
@Singleton
class AndroidBatteryDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager?
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager?

    fun stickyIntent(): Intent? =
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

    fun read(intent: Intent? = stickyIntent(), now: Long = System.currentTimeMillis()): RawBatteryData {
        val bm = batteryManager
        return RawBatteryData(
            timestamp = now,
            level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1,
            scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1,
            status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1,
            health = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1,
            plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1,
            temperatureTenthsC = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE,
            voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, Int.MIN_VALUE) ?: Int.MIN_VALUE,
            technology = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY),
            present = intent?.takeIf { it.hasExtra(BatteryManager.EXTRA_PRESENT) }
                ?.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true),
            cycleCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                intent?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1) ?: -1
            } else -1,
            currentNow = bm.intProp(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW),
            currentAverage = bm.intProp(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE),
            chargeCounter = bm.intProp(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER),
            energyCounter = bm?.let { runCatching { it.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER) }.getOrNull() }
                ?: Long.MIN_VALUE,
            chargeTimeRemainingMs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                bm?.let { runCatching { it.computeChargeTimeRemaining() }.getOrNull() } ?: -1L
            } else -1L,
            interactive = powerManager?.isInteractive,
            thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) powerManager?.currentThermalStatus else null,
        )
    }

    private fun BatteryManager?.intProp(id: Int): Int =
        this?.let { runCatching { it.getIntProperty(id) }.getOrNull() } ?: Int.MIN_VALUE
}
