#!/usr/bin/env bash
set -euo pipefail

mkdir -p visual-qa

dump_ui() {
  adb shell uiautomator dump /sdcard/window.xml >/dev/null
  adb pull /sdcard/window.xml visual-qa/window.xml >/dev/null
}

tap_text() {
  local label="$1"
  dump_ui
  local point
  point="$(python3 -c 'import re, sys, xml.etree.ElementTree as ET; label=sys.argv[1]; nodes=[node for node in ET.parse("visual-qa/window.xml").getroot().iter("node") if node.attrib.get("text") == label]; bounds=list(map(int, re.findall(r"\d+", nodes[0].attrib["bounds"]))) if nodes else (_ for _ in ()).throw(SystemExit(f"Could not find UI text: {label}")); print((bounds[0] + bounds[2]) // 2, (bounds[1] + bounds[3]) // 2)' "$label")"
  adb shell input tap $point
  sleep 2
}

./gradlew installDebug
adb shell am start -n com.a3322505a.guitarlearning/.MainActivity
sleep 3

tap_text "指板训练"
tap_text "第一把位｜0–4 品"
sleep 3
adb exec-out screencap -p > visual-qa/01-awaiting.png

dump_ui
read -r left top right bottom < <(
  python3 -c 'import re, xml.etree.ElementTree as ET; nodes=[node for node in ET.parse("visual-qa/window.xml").getroot().iter("node") if node.attrib.get("content-desc", "").startswith("真实六弦0至12品指板")]; print(*map(int, re.findall(r"\d+", nodes[0].attrib["bounds"]))) if nodes else (_ for _ in ()).throw(SystemExit("Could not find fretboard bounds"))'
)
board_width=$((right - left))
board_height=$((bottom - top))
tap_y=$((top + board_height / 4))
adb shell input tap $((left + board_width * 30 / 100)) "$tap_y"
adb shell input tap $((left + board_width * 15 / 100)) "$tap_y"
sleep 2
adb exec-out screencap -p > visual-qa/02-error.png

tap_text "返回"
sleep 2
adb exec-out screencap -p > visual-qa/03-session-stats.png
