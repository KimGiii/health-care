#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${DESTINATION:-}" ]]; then
  DEVICE_NAME="$(xcrun simctl list devices available -j \
    | ruby -rjson -e 'devices = JSON.parse(STDIN.read)["devices"].values.flatten; phone = devices.find { |d| d["name"].start_with?("iPhone") }; puts phone["name"] if phone')"
  if [[ -z "$DEVICE_NAME" ]]; then
    echo "No available iPhone simulator found. Set DESTINATION explicitly." >&2
    exit 1
  fi
  DESTINATION="platform=iOS Simulator,name=${DEVICE_NAME},OS=latest"
fi
RESULT_BUNDLE="${RESULT_BUNDLE:-build/coverage/HealthCareTests.xcresult}"

rm -rf "$RESULT_BUNDLE"

xcodebuild test \
  -project HealthCare.xcodeproj \
  -scheme HealthCare \
  -destination "$DESTINATION" \
  -only-testing:HealthCareTests \
  -enableCodeCoverage YES \
  -resultBundlePath "$RESULT_BUNDLE" \
  CODE_SIGNING_ALLOWED=NO

xcrun xccov view --report "$RESULT_BUNDLE"
