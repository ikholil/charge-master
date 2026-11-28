# Architecture

A single Gradle module, organised by package into layers that only depend inward.

```
com.hikmahlab.chargemaster
├── app/                 Application, MainActivity, navigation graph
├── core/
│   ├── common/          Units & formatting, coroutine scopes/dispatchers, Clock
│   ├── database/        Room entities, DAOs, database + migrations
│   ├── preferences/     DataStore-backed PreferencesRepository
│   ├── design/          Theme/tokens, components, Canvas charts, label helpers
│   ├── monitoring/      ForegroundMonitor, WorkManager workers & scheduler, BootReceiver
│   └── security/        DeviceInfoPolicy: the only device details that may appear in a report
├── data/
│   ├── datasource/      AndroidBatteryDataSource: public BatteryManager / PowerManager reads
│   ├── local/           Entity ↔ domain mappers
│   └── repository/      Repository implementations + Hilt bindings
├── domain/
│   ├── model/           Pure Kotlin models (nullable = unavailable)
│   ├── engine/          Pure, unit-tested logic (see below)
│   ├── repository/      Repository interfaces
│   └── usecase/         RecordBatterySampleUseCase (the pipeline), retention, clear data
└── feature/             dashboard, charging, batteryhealth, temperature, insights,
                         diagnostics, alerts, widgets, settings, onboarding
```

## Data flow

```
 BatteryManager / ACTION_BATTERY_CHANGED / PowerManager
            │  (RawBatteryData, sentinels for "unsupported")
            ▼
   SnapshotNormalizer ── learns current unit (µA/mA) and sign convention
            │  (BatterySnapshot, null = unavailable)
            ▼
 ┌──────────┴────────────┐
 │ BatteryRepository     │  liveSnapshots: event-driven + mode interval, only while collected
 └──────────┬────────────┘
            ▼
 RecordBatterySampleUseCase  (mutex-serialised; called by ForegroundMonitor & workers)
   1. persist BatteryReading (throttled to ≥ 60 s in foreground, always on plug change)
   2. SessionAccumulator → start / update / resume / close / discard ChargingSession
   3. on close: CapacityEstimator → BatteryHealthSnapshot
   4. AlertEvaluator (persisted de-dup state) → AlertSink (notifications)
   5. WidgetRefresher (≤ once per minute, or on plug change)
```

## Domain engines (all pure Kotlin, JVM-tested)

| Engine | Responsibility |
|---|---|
| `SnapshotNormalizer` | Range checks, sentinel handling, µA/mA detection, sign-convention learning, user overrides |
| `PowerEstimator` | W = V × A with sanity bounds; null when either input is missing |
| `SessionAccumulator` | Session state machine, precision flags, gap/interruption handling, restart closing |
| `AlertEvaluator` | Threshold crossing, hysteresis, once-per-session rules |
| `CapacityEstimator` | ΔQ/ΔLevel capacity, derived-counter detection, confidence, median combine, wear trend |
| `DischargeAnalyzer` | Discharge rates from consecutive unplugged samples (skips gaps > 2 h) |
| `TemperatureAnalyzer`, `ChargingAnalyzer`, `TimeEstimator` | Stats (calendar days in the local time zone) and ETAs, each with its method recorded |
| `HabitAnalyzer` | Charging Habit Score from the user's own thresholds; needs ≥3 completed sessions |

## Monitoring and energy use

- **Foreground**: `ProcessLifecycleOwner` starts collection on `ON_START` and stops it on `ON_STOP`. Samples come from battery broadcasts plus a timer of 1, 2 or 5 s depending on the mode, because current changes don't trigger a broadcast.
- **Background**: a periodic `BatterySampleWorker` runs every 15, 30 or 60 minutes. A `ChargingSampleWorker` with the WorkManager *charging* constraint re-enqueues itself every 3 or 10 minutes, but only while on external power.
- There is no persistent foreground service.
- **Restart**: `BootReceiver` closes the open session using its last real observation and re-applies the schedule.
- **Retention**: a daily `MaintenanceWorker` deletes readings older than the chosen setting (7, 30, 90 or 365 days). Sessions are kept 12× longer.

## Honesty rules enforced in code

- Every metric tile shows a provenance tag: Live reading, Estimate, Historical, User-provided, or Unavailable.
- A current of `0` or `Int.MIN_VALUE` is treated as *unsupported*, never as 0 mA.
- Design capacity is only ever entered by the user. The app never reads it from hidden APIs.
- Health % requires a design capacity and at least medium confidence.
- The cycle count is shown only from `BatteryManager.EXTRA_CYCLE_COUNT` (API 34+).
- Charts break the line at gaps in monitoring instead of interpolating across them.

## Preferences and backup

- User settings live in the `charge_master_prefs` DataStore and are included in Android backup.
- Device-specific learned state lives in the separate `charge_master_device_state` DataStore. This includes the current unit and sign calibration and the alert de-duplication state. It is excluded from backup and device transfer, because another phone's calibration would misread this phone's sensors.
- The battery history database is also excluded from backup.
- The in-app language is applied with `AppCompatDelegate.setApplicationLocales`. On Android 13+ it is stored by the system; on Android 8–12 it is stored by `AppLocalesMetadataHolderService`.

## Database

Room v1 with the schema exported to `app/schemas/`. `MigrationTest` validates the exported schema. When the schema changes:
1. Bump the version.
2. Add a `Migration` to `ChargeMasterDatabase.MIGRATIONS`.
3. Add a migration test.

Destructive migration is never enabled.
