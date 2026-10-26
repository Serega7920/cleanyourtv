#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail
HOST=192.168.0.15:5555
PKG=com.gathin.tvhome
ARTIFACT=12646673306
DIR="$HOME/cleanyourtv-backup"
mkdir -p "$DIR"
echo "[1/6] Connect TV"
adb connect "$HOST"
adb -s "$HOST" get-state | grep -qx device
echo "[2/6] Backup current launcher APK"
PATHS="$(adb -s "$HOST" shell pm path "$PKG" | tr -d '\r')"
BASE="$(printf '%s\n' "$PATHS" | grep '^package:' | head -1 | cut -d: -f2-)"
test -n "$BASE"
adb -s "$HOST" pull "$BASE" "$DIR/launcher-original.apk"
test -s "$DIR/launcher-original.apk"
echo "[3/6] Download new launcher"
curl --fail --location --silent --show-error -H "Authorization: Bearer $(cat "$HOME/.config/cleanyourtv/github_token")" "https://api.github.com/repos/Serega7920/cleanyourtv/actions/artifacts/$ARTIFACT/zip" -o "$DIR/new.zip"
unzip -p "$DIR/new.zip" app-release.apk > "$DIR/launcher-new.apk"
test -s "$DIR/launcher-new.apk"
echo "[4/6] Try non-destructive upgrade"
if adb -s "$HOST" install -r "$DIR/launcher-new.apk"; then
  echo "Updated without uninstall"
else
  echo "Signature differs; uninstall would erase launcher settings."
  echo "Backed up original APK at $DIR/launcher-original.apk"
  echo "No destructive action taken. Exit safely."
  exit 12
fi
echo "[5/6] Start launcher"
adb -s "$HOST" shell am start -n "$PKG/.MainActivity"
echo "[6/6] Done"
