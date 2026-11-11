#!/usr/bin/env bash
# Build minime and install it on a USB-connected phone.
#
#   ./install.sh            build the release APK, wait for the phone, install
#   ./install.sh --no-build install the APK that's already built
#
# Uses `adb` from your PATH (or $ANDROID_HOME/platform-tools). Under WSL, where USB devices
# aren't visible to Linux, it drives the Windows adb.exe instead: set MINIME_WIN_ADB_DIR to the
# Windows folder containing adb.exe, or it defaults to %LOCALAPPDATA%\minime-tools\platform-tools.
set -euo pipefail

cd "$(dirname "$0")"

APK=app/build/outputs/apk/release/app-release.apk
PKG=app.minime.launcher

is_wsl() { grep -qi microsoft /proc/version 2>/dev/null; }

if is_wsl; then
  win_dir="${MINIME_WIN_ADB_DIR:-$(cmd.exe /c 'echo %LOCALAPPDATA%' 2>/dev/null | tr -d '\r')\\minime-tools\\platform-tools}"
  ADB="$(wslpath -u "$win_dir")/adb.exe"
  [[ -x "$ADB" ]] || { echo "Windows adb not found at $win_dir (set MINIME_WIN_ADB_DIR)." >&2; exit 1; }
  # adb.exe can't reliably read \\wsl.localhost paths, so stage the APK next to it.
  STAGE="$(dirname "$ADB")/minime.apk"
  STAGE_FOR_ADB="$win_dir\\minime.apk"
  adb() { (cd /mnt/c && "$ADB" "$@" | tr -d '\r'); }
else
  if ! command -v adb >/dev/null && [[ -n "${ANDROID_HOME:-}" ]]; then
    PATH="$ANDROID_HOME/platform-tools:$PATH"
  fi
  command -v adb >/dev/null || { echo "adb not found. Install Android platform-tools." >&2; exit 1; }
  STAGE="$APK"
  STAGE_FOR_ADB="$APK"
fi

if [[ "${1:-}" != "--no-build" ]]; then
  echo "==> Building release APK"
  ./gradlew assembleRelease -q --console=plain
fi
[[ -f "$APK" ]] || { echo "No APK at $APK" >&2; exit 1; }
[[ "$STAGE" == "$APK" ]] || cp "$APK" "$STAGE"

echo "==> Waiting for phone (plug it in, unlock it, and accept the USB debugging prompt)"
warned=""
while true; do
  state=$(adb devices | awk 'NR>1 && NF>=2 {print $2; exit}')
  case "$state" in
    device) break ;;
    unauthorized)
      [[ -n "$warned" ]] || { echo "    Phone found but not authorized: tap 'Allow' on the USB debugging prompt."; warned=1; } ;;
  esac
  sleep 2
done
echo "    Connected: $(adb shell getprop ro.product.manufacturer) $(adb shell getprop ro.product.model)"

echo "==> Installing"
if ! adb install -r "$STAGE_FOR_ADB"; then
  echo "    Install failed (often a signature mismatch with an older build). Retrying after uninstall..."
  adb uninstall "$PKG" >/dev/null || true
  adb install "$STAGE_FOR_ADB"
fi

if adb shell cmd role get-role-holders android.app.role.HOME | grep -q "$PKG"; then
  echo "Done. minime is already your home app; press Home to see the new version."
else
  echo "==> Opening the default home app picker on the phone"
  adb shell am start -a android.settings.HOME_SETTINGS >/dev/null || true
  echo "Done. Pick 'minime' as your home app on the phone."
fi
