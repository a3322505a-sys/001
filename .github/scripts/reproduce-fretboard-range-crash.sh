#!/usr/bin/env bash
set -u

adb install -r app/build/outputs/apk/debug/app-debug.apk

tap_text() {
  local target="$1"
  adb shell uiautomator dump /sdcard/window.xml >/dev/null
  adb pull /sdcard/window.xml window.xml >/dev/null
  local coordinates
  coordinates=$(TARGET_TEXT="$target" python3 -c 'import os, re, xml.etree.ElementTree as ET; target=os.environ["TARGET_TEXT"]; root=ET.parse("window.xml").getroot(); node=next((n for n in root.iter("node") if n.attrib.get("text")==target), None); assert node is not None, f"UI text not found: {target}"; x1,y1,x2,y2=map(int,re.findall(r"\d+",node.attrib["bounds"])); print(f"{(x1+x2)//2} {(y1+y2)//2}")')
  adb shell input tap $coordinates
  sleep 3
}

open_home() {
  adb shell am force-stop com.a3322505a.guitarlearning
  adb shell am start -W -n com.a3322505a.guitarlearning/.MainActivity >/dev/null
  sleep 3
  tap_text "指板训练"
}

exercise_range() {
  local label="$1"
  adb logcat -c
  open_home
  tap_text "$label"
  sleep 5
  echo "===== $label process ====="
  adb shell pidof com.a3322505a.guitarlearning || true
  echo "===== $label exceptions ====="
  adb logcat -d -v threadtime | grep -A 80 -B 10 -E 'FATAL EXCEPTION|AndroidRuntime|IllegalArgumentException|IllegalStateException' || true
}

exercise_range "第一把位｜0–4 品"
exercise_range "中把位｜5–8 品"
exercise_range "全指板｜0–12 品"
