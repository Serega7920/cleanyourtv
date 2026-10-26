#!/data/data/com.termux/files/usr/bin/bash
# CleanYourTV: restricted ADB command receiver. No arbitrary shell execution.
set -u
BASE="https://raw.githubusercontent.com/Serega7920/cleanyourtv/main/remote"
ADB_TARGET="192.168.0.15:5555"
STATE="$HOME/.cleanyourtv-last-id"
mkdir -p "$HOME"
command -v adb >/dev/null || { echo "Install adb: pkg install android-tools"; exit 1; }
command -v python >/dev/null || { echo "Install Python: pkg install python"; exit 1; }
adb connect "$ADB_TARGET" >/dev/null 2>&1 || true
echo "CleanYourTV receiver started. Ctrl+C to stop."
while true; do
  payload="$(curl -fsSL --connect-timeout 5 --max-time 12 "$BASE/command.json" 2>/dev/null)" || { sleep 30; continue; }
  parsed="$(printf '%s' "$payload" | python -c 'import json,sys; d=json.load(sys.stdin); i=d.get("id"); a=d.get("action"); assert isinstance(i,int) and i>=0 and a in ("noop","ram_status","launcher_status","storage_status","start_clean_launcher"); print(str(i)+" "+a)' 2>/dev/null)" || { echo "Invalid command ignored"; sleep 30; continue; }
  read -r id action <<< "$parsed"
  last="$(cat "$STATE" 2>/dev/null || echo 0)"
  if [[ "$id" =~ ^[0-9]+$ ]] && (( id > last )); then
    echo "$id" > "$STATE"
    echo "[$(date)] Action: $action (id=$id)"
    case "$action" in
      noop) : ;;
      ram_status) adb -s "$ADB_TARGET" shell free -m ;;
      launcher_status) adb -s "$ADB_TARGET" shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME ;;
      storage_status) adb -s "$ADB_TARGET" shell df -h /data ;;
      start_clean_launcher) adb -s "$ADB_TARGET" shell am start -n com.gathin.tvhome/.MainActivity ;;
    esac
  fi
  sleep 30
done
