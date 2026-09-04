#!/usr/bin/env bash
set -eu

adb install -r app/build/outputs/apk/debug/app-debug.apk

tap_fraction() {
  local x_fraction="$1"
  local y_fraction="$2"
  local size width height x y
  size=$(adb shell wm size | sed -n 's/.*Physical size: //p' | tr -d '\r')
  width=${size%x*}
  height=${size#*x}
  x=$(awk -v value="$width" -v fraction="$x_fraction" 'BEGIN { printf "%d", value * fraction }')
  y=$(awk -v value="$height" -v fraction="$y_fraction" 'BEGIN { printf "%d", value * fraction }')
  adb shell input tap "$x" "$y"
}

open_home() {
  adb shell am force-stop com.a3322505a.guitarlearning
  adb shell am start -W -n com.a3322505a.guitarlearning/.MainActivity >/dev/null
  sleep 60
  tap_fraction 0.50 0.53
  sleep 45
}

exercise_range() {
  local label="$1"
  local y_fraction="$2"
  adb logcat -c
  open_home
  tap_fraction 0.50 "$y_fraction"
  sleep 60
  echo "===== $label process ====="
  adb shell pidof com.a3322505a.guitarlearning || true
  echo "===== $label exceptions ====="
  adb logcat -d -v threadtime | grep -A 80 -B 10 -E 'FATAL EXCEPTION|AndroidRuntime|IllegalArgumentException|IllegalStateException' || true
}

exercise_range "第一把位｜0–4 品" 0.47
exercise_range "中把位｜5–8 品" 0.535
exercise_range "全指板｜0–12 品" 0.60
