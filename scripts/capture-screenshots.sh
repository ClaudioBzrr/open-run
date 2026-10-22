#!/usr/bin/env bash
set -euo pipefail

OUT="docs/assets/screenshots"
mkdir -p "$OUT"

dump_ui() {
  for attempt in 1 2 3 4 5; do
    adb shell uiautomator dump --compressed /sdcard/openrun-window.xml >/dev/null 2>&1 || true
    adb shell cat /sdcard/openrun-window.xml > /tmp/openrun-window.xml 2>/dev/null || true
    if grep -q '<hierarchy' /tmp/openrun-window.xml 2>/dev/null; then
      return 0
    fi
    sleep 1
  done
  echo "Could not read the Android view hierarchy" >&2
  return 1
}

coords_for_id() {
  dump_ui
  python3 - "$1" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

resource_id = sys.argv[1]
root = ET.parse('/tmp/openrun-window.xml').getroot()
for node in root.iter('node'):
    if node.get('resource-id') == resource_id:
        numbers = [int(value) for value in re.findall(r'\d+', node.get('bounds', ''))]
        if len(numbers) == 4:
            print((numbers[0] + numbers[2]) // 2, (numbers[1] + numbers[3]) // 2)
            raise SystemExit(0)
raise SystemExit('Could not find ' + resource_id)
PY
}

tap_id() {
  read -r x y < <(coords_for_id "$1")
  adb shell input tap "$x" "$y"
}

capture() {
  adb exec-out screencap -p > "$1"
}

adb shell am force-stop com.openrun.app
adb shell am start -W -n com.openrun.app/.ui.MainActivity >/dev/null
sleep 4
capture "$OUT/01-home.png"

tap_id "com.openrun.app:id/button_menu"
sleep 1
capture "$OUT/02-drawer.png"
adb shell input keyevent 4
sleep 1

tap_id "com.openrun.app:id/text_distance"
sleep 8
capture "$OUT/03-detail.png"
adb shell input keyevent 4
sleep 2

tap_id "com.openrun.app:id/button_view_all"
sleep 2
capture "$OUT/04-all-activities.png"
