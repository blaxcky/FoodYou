#!/usr/bin/env bash

set -Eeuo pipefail

readonly AVD_NAME="FoodYou_API_36"
readonly APP_ID="com.maksimowiczm.foodyou"
readonly MAIN_ACTIVITY=".app.infrastructure.android.MainActivity"
readonly DEFAULT_SDK_DIR="/home/markus/Android/Sdk"
readonly DEFAULT_ANDROID_CLI="/home/markus/.local/bin/android"
readonly DEFAULT_JAVA_HOME="/usr/lib/jvm/java-21-openjdk"
readonly BOOT_TIMEOUT_SECONDS="${FOODYOU_EMULATOR_BOOT_TIMEOUT_SECONDS:-240}"
readonly EMULATOR_UNIT="foodyou-emulator.service"
readonly SYSTEMCTL="/usr/bin/systemctl"
readonly SYSTEMD_RUN="/usr/bin/systemd-run"

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly SCRIPT_DIR
REPO_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd)"
readonly REPO_ROOT

sdk_dir_from_local_properties() {
    local properties_file="$REPO_ROOT/local.properties"
    local configured_sdk

    [[ -f "$properties_file" ]] || return 1
    configured_sdk="$(sed -n 's/^sdk\.dir=//p' "$properties_file" | tail -n 1)"
    [[ -n "$configured_sdk" ]] || return 1
    printf '%s\n' "$configured_sdk"
}

resolve_sdk_dir() {
    if [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then
        printf '%s\n' "$ANDROID_SDK_ROOT"
    elif [[ -n "${ANDROID_HOME:-}" ]]; then
        printf '%s\n' "$ANDROID_HOME"
    elif sdk_dir_from_local_properties; then
        return 0
    else
        printf '%s\n' "$DEFAULT_SDK_DIR"
    fi
}

SDK_DIR="$(resolve_sdk_dir)"
readonly SDK_DIR
readonly ADB="$SDK_DIR/platform-tools/adb"
readonly EMULATOR="$SDK_DIR/emulator/emulator"
readonly JAVA_HOME_DIR="${FOODYOU_JAVA_HOME:-$DEFAULT_JAVA_HOME}"
readonly CAPTURE_DIR="$REPO_ROOT/captures/android-emulator"
readonly EMULATOR_LOG="$CAPTURE_DIR/emulator.log"

if [[ -n "${ANDROID_CLI_BIN:-}" ]]; then
    ANDROID_CLI="$ANDROID_CLI_BIN"
elif command -v android >/dev/null 2>&1; then
    ANDROID_CLI="$(command -v android)"
else
    ANDROID_CLI="$DEFAULT_ANDROID_CLI"
fi
readonly ANDROID_CLI

log() {
    printf '%s\n' "$*" >&2
}

fail() {
    log "Error: $*"
    exit 1
}

require_executable() {
    local executable="$1"
    local description="$2"
    [[ -x "$executable" ]] || fail "$description not found or not executable: $executable"
}

check_prerequisites() {
    require_executable "$ADB" "adb"
    require_executable "$EMULATOR" "Android Emulator"
    require_executable "$ANDROID_CLI" "Android CLI"
    require_executable "$SYSTEMCTL" "systemctl"
    require_executable "$SYSTEMD_RUN" "systemd-run"

    local adb_output
    adb_output="$("$ADB" start-server 2>&1)" ||
        fail "adb server is unavailable. Run this command with Codex host access. Details: $adb_output"
}

ensure_avd_exists() {
    "$EMULATOR" -list-avds | grep -Fxq "$AVD_NAME" ||
        fail "AVD $AVD_NAME is missing. Recreate it with the API 36 Google APIs x86_64 image."
}

ensure_kvm_available() {
    [[ -e /dev/kvm ]] ||
        fail "/dev/kvm is unavailable. Run this command with Codex host access, outside the filesystem sandbox."
    [[ -r /dev/kvm && -w /dev/kvm ]] ||
        fail "/dev/kvm is not readable and writable by the current user."

    local acceleration
    acceleration="$("$EMULATOR" -accel-check 2>&1)" || fail "KVM acceleration check failed: $acceleration"
    grep -q "KVM.*usable" <<<"$acceleration" || fail "KVM acceleration is not usable: $acceleration"
}

emulator_serials() {
    "$ADB" devices | awk '$1 ~ /^emulator-[0-9]+$/ && $2 == "device" { print $1 }'
}

avd_name_for_serial() {
    local serial="$1"
    "$ADB" -s "$serial" emu avd name 2>/dev/null | tr -d '\r' | head -n 1
}

find_food_you_serial() {
    local serial
    local avd_name
    local match=""

    while IFS= read -r serial; do
        [[ -n "$serial" ]] || continue
        avd_name="$(avd_name_for_serial "$serial" || true)"
        if [[ "$avd_name" == "$AVD_NAME" ]]; then
            [[ -z "$match" ]] || fail "More than one running emulator reports AVD name $AVD_NAME."
            match="$serial"
        fi
    done < <(emulator_serials)

    [[ -n "$match" ]] || return 1
    printf '%s\n' "$match"
}

wait_for_serial() {
    local service_unit="${1:-}"
    local deadline=$((SECONDS + BOOT_TIMEOUT_SECONDS))
    local serial

    while ((SECONDS < deadline)); do
        if serial="$(find_food_you_serial)"; then
            printf '%s\n' "$serial"
            return 0
        fi
        if [[ -n "$service_unit" ]] && ! "$SYSTEMCTL" --user is-active --quiet "$service_unit"; then
            tail -n 40 "$EMULATOR_LOG" >&2 || true
            fail "The emulator process exited before $AVD_NAME appeared in adb. Full log: $EMULATOR_LOG"
        fi
        sleep 1
    done

    fail "Timed out after ${BOOT_TIMEOUT_SECONDS}s waiting for $AVD_NAME to appear in adb."
}

wait_for_boot() {
    local serial="$1"
    local deadline=$((SECONDS + BOOT_TIMEOUT_SECONDS))
    local boot_completed

    "$ADB" -s "$serial" wait-for-device
    while ((SECONDS < deadline)); do
        boot_completed="$("$ADB" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
        if [[ "$boot_completed" == "1" ]]; then
            printf '%s\n' "$serial"
            return 0
        fi
        sleep 1
    done

    fail "Timed out after ${BOOT_TIMEOUT_SECONDS}s waiting for $serial to finish booting."
}

start_avd() {
    local boot_mode="${1:-quick}"
    local serial
    local -a start_args=(
        "@$AVD_NAME"
        -no-window
        -no-audio
        -no-boot-anim
        -gpu host
        -feature -Vulkan
    )

    check_prerequisites
    ensure_avd_exists
    ensure_kvm_available

    if serial="$(find_food_you_serial)"; then
        if [[ "$boot_mode" == "cold" ]]; then
            log "Stopping running $AVD_NAME before cold boot..."
            "$ANDROID_CLI" --sdk="$SDK_DIR" emulator stop "$serial"
            wait_until_stopped "$serial"
        else
            log "Reusing $AVD_NAME on $serial..."
            wait_for_boot "$serial"
            return 0
        fi
    fi

    if [[ "$boot_mode" == "cold" ]]; then
        start_args+=(-no-snapshot-load)
    fi

    mkdir -p "$CAPTURE_DIR"
    if "$SYSTEMCTL" --user is-active --quiet "$EMULATOR_UNIT"; then
        log "Waiting for the existing $EMULATOR_UNIT service..."
    else
        : >"$EMULATOR_LOG"
        "$SYSTEMCTL" --user reset-failed "$EMULATOR_UNIT" >/dev/null 2>&1 || true
        log "Starting $AVD_NAME headlessly in $EMULATOR_UNIT..."
        "$SYSTEMD_RUN" \
            --user \
            --unit="$EMULATOR_UNIT" \
            --collect \
            --property=Type=exec \
            --property="StandardOutput=append:$EMULATOR_LOG" \
            --property="StandardError=append:$EMULATOR_LOG" \
            "$EMULATOR" "${start_args[@]}" >/dev/null
    fi
    serial="$(wait_for_serial "$EMULATOR_UNIT")"
    wait_for_boot "$serial"
}

wait_until_stopped() {
    local serial="$1"
    local deadline=$((SECONDS + 60))

    while ((SECONDS < deadline)); do
        if ! emulator_serials | grep -Fxq "$serial"; then
            return 0
        fi
        sleep 1
    done

    fail "Timed out waiting for $serial to stop."
}

require_running_serial() {
    local serial
    serial="$(find_food_you_serial)" || fail "$AVD_NAME is not running. Start it with: $0 start"
    wait_for_boot "$serial"
}

show_status() {
    local serial

    check_prerequisites
    ensure_avd_exists
    if serial="$(find_food_you_serial)"; then
        local boot_completed
        boot_completed="$("$ADB" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
        printf 'AVD: %s\nSerial: %s\nBoot completed: %s\n' "$AVD_NAME" "$serial" "${boot_completed:-0}"
    else
        printf 'AVD: %s\nStatus: stopped\n' "$AVD_NAME"
    fi
}

install_and_launch() {
    local serial
    serial="$(start_avd)"

    require_executable "$JAVA_HOME_DIR/bin/java" "JDK 21"
    require_executable "$REPO_ROOT/gradlew" "Gradle wrapper"

    log "Building and installing devRelease on $serial..."
    GRADLE_USER_HOME="$REPO_ROOT/.gradle" \
        JAVA_HOME="$JAVA_HOME_DIR" \
        ANDROID_SERIAL="$serial" \
        "$REPO_ROOT/gradlew" :app:installDevRelease --console=plain

    log "Launching $APP_ID on $serial..."
    "$ADB" -s "$serial" shell am force-stop "$APP_ID"
    "$ADB" -s "$serial" shell am start -W -n "$APP_ID/$MAIN_ACTIVITY"
}

inspect_app() {
    local serial
    serial="$(require_running_serial)"
    mkdir -p "$CAPTURE_DIR"

    "$ANDROID_CLI" --sdk="$SDK_DIR" layout \
        --device="$serial" \
        --pretty \
        --full \
        --output="$CAPTURE_DIR/layout.json"
    "$ANDROID_CLI" --sdk="$SDK_DIR" screen capture \
        --device="$serial" \
        --output="$CAPTURE_DIR/screen.png"
    "$ANDROID_CLI" --sdk="$SDK_DIR" screen capture \
        --device="$serial" \
        --annotate \
        --output="$CAPTURE_DIR/screen-annotated.png"

    printf 'Layout: %s\nScreenshot: %s\nAnnotated screenshot: %s\n' \
        "$CAPTURE_DIR/layout.json" \
        "$CAPTURE_DIR/screen.png" \
        "$CAPTURE_DIR/screen-annotated.png"
}

stop_avd() {
    local serial

    check_prerequisites
    if ! serial="$(find_food_you_serial)"; then
        log "$AVD_NAME is already stopped."
        return 0
    fi

    log "Stopping $AVD_NAME on $serial..."
    "$ANDROID_CLI" --sdk="$SDK_DIR" emulator stop "$serial"
    wait_until_stopped "$serial"
}

print_usage() {
    cat <<EOF
Usage: $0 <command>

Commands:
  start       Start $AVD_NAME headlessly, or reuse it, and wait for boot.
  cold-start  Restart $AVD_NAME headlessly without loading a snapshot.
  wait        Wait for a starting $AVD_NAME to finish booting.
  status      Show the state and serial of $AVD_NAME.
  install     Start the AVD, install devRelease, and launch FoodYou.
  inspect     Save layout JSON and screenshots under captures/android-emulator/.
  stop        Stop only $AVD_NAME.
EOF
}

case "${1:-}" in
    start)
        start_avd
        ;;
    cold-start)
        start_avd cold
        ;;
    wait)
        check_prerequisites
        ensure_avd_exists
        wait_for_boot "$(wait_for_serial)"
        ;;
    status)
        show_status
        ;;
    install)
        install_and_launch
        ;;
    inspect)
        inspect_app
        ;;
    stop)
        stop_avd
        ;;
    -h|--help|help)
        print_usage
        ;;
    *)
        print_usage >&2
        exit 2
        ;;
esac
