#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

APP_ID="com.chargemaster.app.debug"
MAIN_ACTIVITY="com.chargemaster.app.app.MainActivity"
INTERVAL="${LIVE_RELOAD_INTERVAL:-1}"
SERIAL="${ANDROID_SERIAL:-}"
AVD="${ANDROID_AVD:-}"
LAUNCH=true
ONCE=false

usage() {
    cat <<EOF
Usage: $(basename "$0") [OPTIONS]

Watch source files, rebuild & install the debug APK, and relaunch the app on change.
If no device/emulator is connected, an available AVD is launched automatically.

Options:
  -s, --serial <id>     Target adb device/emulator serial (or set ANDROID_SERIAL)
  -a, --avd <name>      AVD name to launch if no device is connected (default: first AVD)
  -i, --interval <sec>  Polling interval in seconds when inotifywait is unavailable (default: 1)
      --no-launch       Install updated APK without relaunching the activity
      --once            Run a single build, install, and launch cycle then exit
  -h, --help            Show this help message
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        -s|--serial)
            [[ $# -ge 2 ]] || { echo "Error: $1 requires an argument." >&2; exit 1; }
            SERIAL="$2"
            shift 2
            ;;
        -a|--avd)
            [[ $# -ge 2 ]] || { echo "Error: $1 requires an argument." >&2; exit 1; }
            AVD="$2"
            shift 2
            ;;
        -i|--interval)
            [[ $# -ge 2 ]] || { echo "Error: $1 requires an argument." >&2; exit 1; }
            INTERVAL="$2"
            shift 2
            ;;
        --no-launch)
            LAUNCH=false
            shift
            ;;
        --once)
            ONCE=true
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "Unknown option: $1" >&2
            usage >&2
            exit 1
            ;;
    esac
done

find_sdk_tool() {
    local rel_path="$1"
    local bin_name
    bin_name="$(basename "$rel_path")"
    if command -v "$bin_name" >/dev/null 2>&1; then
        command -v "$bin_name"
        return 0
    fi
    local candidates=(
        "${ANDROID_HOME:-}/$rel_path"
        "${ANDROID_SDK_ROOT:-}/$rel_path"
        "$HOME/Android/Sdk/$rel_path"
    )
    if [[ -f "$ROOT_DIR/local.properties" ]]; then
        local sdk_dir
        sdk_dir="$(grep -E '^sdk\.dir=' "$ROOT_DIR/local.properties" | head -n1 | cut -d= -f2- | tr -d '\r' || true)"
        if [[ -n "$sdk_dir" ]]; then
            candidates=("$sdk_dir/$rel_path" "${candidates[@]}")
        fi
    fi
    for c in "${candidates[@]}"; do
        if [[ -x "$c" ]]; then
            echo "$c"
            return 0
        fi
    done
    return 1
}

ADB="$(find_sdk_tool "platform-tools/adb" || true)"
if [[ -z "$ADB" ]]; then
    echo "Error: adb not found. Add Android platform-tools to PATH or set ANDROID_HOME." >&2
    exit 1
fi

EMULATOR="$(find_sdk_tool "emulator/emulator" || true)"

adb_cmd() {
    if [[ -n "$SERIAL" ]]; then
        "$ADB" -s "$SERIAL" "$@"
    else
        "$ADB" "$@"
    fi
}

start_emulator() {
    if [[ -z "$EMULATOR" ]]; then
        echo "Error: No device connected and Android emulator binary not found." >&2
        exit 1
    fi

    local avd_name="$AVD"
    if [[ -z "$avd_name" ]]; then
        avd_name="$("$EMULATOR" -list-avds | head -n1 | tr -d '\r' || true)"
    fi
    if [[ -z "$avd_name" ]]; then
        echo "Error: No Android Virtual Devices (AVDs) found. Create one in Android Studio." >&2
        exit 1
    fi

    echo "[live-reload] No device connected. Launching emulator '$avd_name'..."
    nohup "$EMULATOR" -avd "$avd_name" >/tmp/charge-master-emulator.log 2>&1 </dev/null &

    echo "[live-reload] Waiting for emulator to connect..."
    "$ADB" wait-for-device

    local deadline=$((SECONDS + 180))
    while [[ $SECONDS -lt $deadline ]]; do
        local boot_completed
        boot_completed="$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
        if [[ "$boot_completed" == "1" ]]; then
            echo "[live-reload] Emulator booted."
            return 0
        fi
        sleep 2
    done

    echo "Error: Timed out waiting for emulator '$avd_name' to finish booting." >&2
    exit 1
}

check_device() {
    if [[ -n "$SERIAL" ]]; then
        export ANDROID_SERIAL="$SERIAL"
        if ! "$ADB" devices | awk 'NR>1 && $2=="device" {print $1}' | grep -qx "$SERIAL"; then
            echo "Error: Device '$SERIAL' is not connected or not ready." >&2
            "$ADB" devices
            exit 1
        fi
        return 0
    fi

    local devices
    devices="$("$ADB" devices | awk 'NR>1 && $2=="device" {print $1}')"
    local count
    count="$(printf '%s\n' "$devices" | sed '/^$/d' | wc -l)"
    if [[ "$count" -eq 0 ]]; then
        start_emulator
        devices="$("$ADB" devices | awk 'NR>1 && $2=="device" {print $1}')"
        count="$(printf '%s\n' "$devices" | sed '/^$/d' | wc -l)"
        if [[ "$count" -eq 0 ]]; then
            echo "Error: Emulator started but no device is ready in adb." >&2
            exit 1
        fi
    fi
    if [[ "$count" -gt 1 ]]; then
        echo "Error: Multiple devices connected. Pass -s <serial> or set ANDROID_SERIAL:" >&2
        "$ADB" devices
        exit 1
    fi
    SERIAL="$(printf '%s\n' "$devices" | head -n1)"
    export ANDROID_SERIAL="$SERIAL"
}

strings_fingerprint() {
    if [[ -f "$ROOT_DIR/tools/strings_table.py" ]]; then
        sha256sum "$ROOT_DIR/tools/strings_table.py" | awk '{print $1}'
    fi
}

watch_snapshot() {
    find "$ROOT_DIR/app/src" \
        "$ROOT_DIR/app/build.gradle.kts" \
        "$ROOT_DIR/build.gradle.kts" \
        "$ROOT_DIR/gradle/libs.versions.toml" \
        "$ROOT_DIR/tools/strings_table.py" \
        -type f -printf '%p %T@\n' 2>/dev/null | sed "s|^$ROOT_DIR/||" | sort
}

LAST_STRINGS_HASH="$(strings_fingerprint)"

deploy() {
    local current_strings_hash
    current_strings_hash="$(strings_fingerprint)"
    if [[ "$current_strings_hash" != "$LAST_STRINGS_HASH" ]]; then
        echo "[live-reload] strings_table.py changed; regenerating strings.xml..."
        if python3 "$ROOT_DIR/tools/gen_strings.py"; then
            LAST_STRINGS_HASH="$current_strings_hash"
        else
            echo "[live-reload] gen_strings.py failed; fix errors and save again." >&2
            return 1
        fi
    fi

    echo "[live-reload] Building and installing debug APK..."
    if ! "$ROOT_DIR/gradlew" :app:installDebug --console=plain; then
        echo "[live-reload] Build/install failed; waiting for next change..." >&2
        return 1
    fi

    if [[ "$LAUNCH" == true ]]; then
        echo "[live-reload] Launching $APP_ID/$MAIN_ACTIVITY on $SERIAL..."
        adb_cmd shell am start -S -n "$APP_ID/$MAIN_ACTIVITY" >/dev/null
    fi
    echo "[live-reload] Ready ($(date '+%H:%M:%S'))."
}

trap 'echo ""; echo "[live-reload] Stopped."; exit 0' INT TERM

check_device
echo "[live-reload] Target device: $SERIAL"

deploy || true
if [[ "$ONCE" == true ]]; then
    exit 0
fi

if command -v inotifywait >/dev/null 2>&1; then
    echo "[live-reload] Watching for changes with inotifywait (Ctrl+C to stop)..."
    while inotifywait -r -q -e modify,create,delete,move \
        "$ROOT_DIR/app/src" \
        "$ROOT_DIR/app/build.gradle.kts" \
        "$ROOT_DIR/build.gradle.kts" \
        "$ROOT_DIR/gradle/libs.versions.toml" \
        "$ROOT_DIR/tools/strings_table.py" >/dev/null; do
        # Debounce rapid editor saves.
        sleep 0.3
        deploy || true
    done
else
    echo "[live-reload] Watching for changes (polling every ${INTERVAL}s, Ctrl+C to stop)..."
    last_snapshot="$(watch_snapshot)"
    while true; do
        sleep "$INTERVAL"
        current_snapshot="$(watch_snapshot)"
        if [[ "$current_snapshot" != "$last_snapshot" ]]; then
            changed_files="$(comm -3 <(printf '%s\n' "$last_snapshot") <(printf '%s\n' "$current_snapshot") | awk '{print $1}' | sort -u | head -n 5 | tr '\n' ' ')"
            echo "[live-reload] Change detected: ${changed_files:-files updated}"
            last_snapshot="$current_snapshot"
            deploy || true
        fi
    done
fi
