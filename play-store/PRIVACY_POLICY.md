# Charge Master — Privacy Policy

*Last updated: 2026-11-28*  
*Developer: Hikmahlab (`com.hikmahlab.chargemaster`)*

Charge Master does not collect, transmit, or sell personal data.

## On-device only
Charge Master is an offline-first application. It stores the following data strictly on your device:
- **Battery readings:** level, temperature, voltage, current, charge counter, charging status and power source, and screen on/off state at sample time.
- **Charging sessions:** session start/end times, levels, power, temperature, and interruption counts.
- **Capacity estimates:** estimated full-charge capacity and optional user-entered design capacity.
- **Settings:** your theme, language, monitoring mode, retention period, and alert preferences.

This data never leaves your device. The app has no `INTERNET` permission, no analytics SDKs, and no advertising SDKs.

## Sharing and exports
- **Diagnostic report:** If you choose to share a battery report, the plain-text report is passed to the app you select in Android's share sheet. It contains battery metrics and your device manufacturer, model, and Android version. It does not contain identifiers such as serial number, IMEI, Android ID, accounts, or location.
- **CSV export:** If you export charging history, the CSV file is saved only to the location you choose via Android's document picker.

## Permissions
- **Notifications (`POST_NOTIFICATIONS`):** Used only for battery and charging alerts that you enable.
- **Start at boot (`RECEIVE_BOOT_COMPLETED`):** Allows battery monitoring to resume and close interrupted charging sessions after a device restart.

## Retention, backup, and deletion
- Readings are retained for the period you choose in **Settings › Data**: 7, 30, 90, or 365 days.
- You can delete all stored history at any time in **Settings › Data › Clear all history**.
- Uninstalling the app removes all app data.
- The battery history database (`charge_master.db`) and device-state store are excluded from Android cloud backup and device-to-device transfer.

## Children's privacy
Charge Master is a general utility app and is not directed at children under 13.

## Contact
**Hikmahlab**  
Website: [https://hikmahlab.com](https://hikmahlab.com)  
Email: [hello@hikmahlab.com](mailto:hello@hikmahlab.com)  
Address: Sector 11, Uttara, Dhaka 1230, Bangladesh
