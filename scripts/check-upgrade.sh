#!/usr/bin/env bash
set -euo pipefail
app=com.a3322505a.guitarlearning
runner=$app.test/androidx.test.runner.AndroidJUnitRunner
test_class=$app.learning.UpgradeSmokeTest
run_test() {
  local method=$1
  local case_class=${2:-$test_class}
  local log_path="upgrade-$method.log"
  adb shell am instrument -w -r -e class "$case_class#$method" "$runner" | tee "$log_path"
  if ! grep -q 'OK (1 test)' "$log_path"; then
    echo "Upgrade test failed: $method" >&2
    exit 1
  fi
}
adb install -r upgrade-check/base.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
run_test seedProfile
adb shell am force-stop "$app"
adb install -r upgrade-check/update.apk
adb shell am start -W -n "$app/.MainActivity"
run_test verifyPreservedProfile

# Scoped regression for this batch, using the same emulator and instrumentation runner.
run_test verifyStaticAudioPlaybackAndCancellation "$app.audio.AudioOutputSmokeTest"

# Optional fixed-contract captures for manual layout review; not an additional pass gate.
adb shell settings put secure immersive_mode_confirmations confirmed
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
adb shell am instrument -w -r -e class "$app.learning.UiPreviewTest#captureContracts" "$runner" > ui-previews.log 2>&1 || true
mkdir -p ui-previews
preview_path="/sdcard/Android/data/$app/files/previews"
expected=$(adb shell "find '$preview_path' -type f -name '*.png' | wc -l" | tr -d '\r[:space:]')
for attempt in 1 2 3; do
  adb pull "$preview_path/." ui-previews/ || true
  actual=$(find ui-previews -type f -name '*.png' | wc -l)
  echo "Preview export attempt $attempt: $actual / ${expected:-unknown} PNG files" | tee -a ui-previews.log
  if [[ "$expected" =~ ^[0-9]+$ ]] && (( actual >= expected )); then break; fi
done
