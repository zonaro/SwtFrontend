#!/bin/sh
# SwtFrontend — Build & Install via ADB
# POSIX sh, distro-agnostic, Android SDK autodetect, device checks.
# Usage:
#   ./install.sh              # build + install (default)
#   ./install.sh --build-only # only build, no adb
#   ./install.sh --install-only # only install (skips build)
#   ./install.sh --launch     # build + install + launch LibraryActivity
#   ./install.sh --help       # help
set -eu

PROJECT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
APK_PATH="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
GRADLEW="$PROJECT_DIR/gradlew"

# --- resolve adb (PATH first, then ~/android-setup/sdk, then $ANDROID_SDK_ROOT) ---
resolve_adb() {
    if command -v adb >/dev/null 2>&1; then
        command -v adb
        return 0
    fi
    for p in "$HOME/android-setup/sdk/platform-tools/adb" "$HOME/Android/Sdk/platform-tools/adb" "${ANDROID_SDK_ROOT:-}/platform-tools/adb" "${ANDROID_HOME:-}/platform-tools/adb"; do
        if [ -x "$p" ]; then
            printf '%s\n' "$p"
            return 0
        fi
    done
    return 1
}

usage() {
    cat <<'USAGE'
Usage: ./install.sh [OPTIONS]

Options:
  --build-only    Only build :app:assembleDebug (no adb)
  --install-only  Only install APK via adb (skip build)
  --launch        Build, install and launch LibraryActivity
  --help          Show this help
  -h              Show this help

Examples:
  ./install.sh                # build + install
  ./install.sh --launch       # build + install + launch
  ./install.sh --install-only # reinstall existing APK
USAGE
}

BUILD=1
INSTALL=1
LAUNCH=0

for arg in "$@"; do
    case "$arg" in
        --build-only) INSTALL=0 ;;
        --install-only) BUILD=0 ;;
        --launch) LAUNCH=1 ;;
        --help|-h) usage; exit 0 ;;
        *) printf 'Unknown option: %s\n' "$arg" >&2; usage >&2; exit 2 ;;
    esac
done

# --- preflight: gradlew + adb ---
if [ "$BUILD" = 1 ] || [ "$LAUNCH" = 1 ]; then
    if [ ! -x "$GRADLEW" ]; then
        printf 'ERROR: gradlew not found/executable at %s\n' "$GRADLEW" >&2
        exit 1
    fi
fi

ADB=""
if [ "$INSTALL" = 1 ] || [ "$LAUNCH" = 1 ]; then
    if ! ADB="$(resolve_adb)"; then
        printf 'ERROR: adb not found. Install platform-tools or set ANDROID_SDK_ROOT.\n' >&2
        printf '  Tried: PATH, ~/android-setup/sdk, ~/Android/Sdk, $ANDROID_SDK_ROOT\n' >&2
        exit 1
    fi
    printf 'Using adb: %s\n' "$ADB"
    "$ADB" --version 2>&1 | head -n 1
fi

# --- build ---
if [ "$BUILD" = 1 ] || [ "$LAUNCH" = 1 ]; then
    printf '\n==> Building :app:assembleDebug ...\n'
    # Use JDK 17 if available at ~/android-setup/jdk17 (Temurin) to match gradle.properties
    if [ -d "$HOME/android-setup/jdk17" ]; then
        export JAVA_HOME="$HOME/android-setup/jdk17"
        export PATH="$JAVA_HOME/bin:$PATH"
        printf 'JAVA_HOME=%s\n' "$JAVA_HOME"
    fi
    # shellcheck disable=SC2086
    "$GRADLEW" :app:assembleDebug --console=plain
    printf 'Build OK: %s\n' "$APK_PATH"
fi

if [ -f "$APK_PATH" ]; then
    # shellcheck disable=SC2012
    APK_SIZE="$(ls -lh "$APK_PATH" 2>/dev/null | awk '{print $5}')"
    printf 'APK: %s (%s)\n' "$APK_PATH" "${APK_SIZE:-unknown}"
else
    if [ "$INSTALL" = 1 ] || [ "$LAUNCH" = 1 ]; then
        printf 'ERROR: APK not found at %s (run build first)\n' "$APK_PATH" >&2
        exit 1
    fi
fi

# --- install ---
if [ "$INSTALL" = 1 ] || [ "$LAUNCH" = 1 ]; then
    printf '\n==> Checking adb devices ...\n'
    DEVICES_RAW="$("$ADB" devices -l 2>&1)"
    printf '%s\n' "$DEVICES_RAW"

    # Parse device list (skip header line "List of devices attached")
    DEVICES="$(printf '%s\n' "$DEVICES_RAW" | awk 'NR>1 && NF>=2 {print $1, $2}')"
    DEVICE_COUNT="$(printf '%s\n' "$DEVICES" | grep -c -v '^ *$' || true)"
    if [ -z "$DEVICES" ] || [ "$DEVICE_COUNT" -eq 0 ]; then
        printf '\nERROR: Nenhum device/emulador conectado.\n' >&2
        printf '  1) Ative Depuração USB no device\n' >&2
        printf '  2) Conecte via USB ou rode um emulador\n' >&2
        printf '  3) adb devices -l deve listar seu device como "device"\n' >&2
        exit 1
    fi

    # Detect unauthorized/offline
    if printf '%s\n' "$DEVICES_RAW" | grep -q "unauthorized"; then
        printf '\nERROR: Device está "unauthorized" (R9QX602Y1CM).\n' >&2
        printf '  No device, toque em "Permitir depuração USB" e confirme a RSA fingerprint.\n' >&2
        printf '  Dicas:\n' >&2
        printf '    - Desbloqueie a tela e verifique o diálogo de permissão\n' >&2
        printf '    - adb kill-server && adb start-server && adb devices\n' >&2
        printf '    - Revogue autorizações em: Config > Opções do desenvolvedor > Revogar autorizações\n' >&2
        printf '    - Reconecte o USB e aceite novamente\n' >&2
        exit 1
    fi
    if printf '%s\n' "$DEVICES_RAW" | grep -q "offline"; then
        printf '\nERROR: Device está "offline". Reconecte o USB ou reinicie o adb server.\n' >&2
        exit 1
    fi

    # Warn if multiple devices (adb install will use default; user can set ANDROID_SERIAL)
    if [ "$DEVICE_COUNT" -gt 1 ]; then
        printf '\nWARN: %s devices conectados. Usando default. Defina ANDROID_SERIAL para escolher.\n' "$DEVICE_COUNT" >&2
        printf '%s\n' "$DEVICES" >&2
    fi

    printf '\n==> Installing %s ...\n' "$APK_PATH"
    set +e
    "$ADB" install -r "$APK_PATH" 2>&1
    INSTALL_RC=$?
    set -e
    if [ $INSTALL_RC -ne 0 ]; then
        printf '\nERROR: adb install falhou (exit %s)\n' "$INSTALL_RC" >&2
        printf '  Tente: adb install -r -t %s\n' "$APK_PATH" >&2
        exit $INSTALL_RC
    fi
    printf '\nInstall OK (br.com.redclaw.swt)\n'
fi

if [ "$LAUNCH" = 1 ]; then
    printf '\n==> Launching LibraryActivity ...\n'
    "$ADB" shell am start -n br.com.redclaw.swt/.views.LibraryActivity 2>&1 || {
        printf 'WARN: am start falhou, tentando MainActivity...\n' >&2
        "$ADB" shell am start -n br.com.redclaw.swt/.views.MainActivity 2>&1 || true
    }
    printf 'Launch enviado. Verifique o device.\n'
fi

printf '\nDone.\n'
