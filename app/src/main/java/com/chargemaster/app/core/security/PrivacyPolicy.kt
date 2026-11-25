package com.chargemaster.app.core.security

import android.os.Build

/**
 * Single place that decides which device details may leave the app (only via a report the user
 * explicitly shares). Identifiers such as serial number, Android ID, IMEI, accounts, installed
 * apps or location are never read.
 */
object DeviceInfoPolicy {
    data class DeviceSummary(val manufacturer: String, val model: String, val androidVersion: String, val sdk: Int)

    fun summary(): DeviceSummary = DeviceSummary(
        manufacturer = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() },
        model = Build.MODEL.orEmpty(),
        androidVersion = Build.VERSION.RELEASE.orEmpty(),
        sdk = Build.VERSION.SDK_INT,
    )
}
