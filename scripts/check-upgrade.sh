#!/usr/bin/env bash
set -euo pipefail
app=com.a3322505a.guitarlearning
runner=$app.test/androidx.test.runner.AndroidJUnitRunner
test_class=$app.learning.UpgradeSmokeTest
run_test() {
  local method=$1
  local log_path="upgrade-$method.log"
  adb shell am instrument -w -r -e class "$test_class#$method" "$runner" | tee "$log_path"
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
