#!/usr/bin/env bash
# Everything that can be verified from the shell, in one run:
# unit tests, lint (errors fail the run) and a debug build.
#
#   ./check.sh          # test + lint + assembleDebug
#   ./check.sh usage    # the usage probe `teyla products` reads
set -euo pipefail
cd "$(dirname "$0")"

if [ "${1:-}" = "usage" ]; then
  # Qupa promises no telemetry: there is nothing to count.
  echo "telemetry=none"
  exit 0
fi

# The Android build needs a JDK 17+ and the Android SDK (local.properties: sdk.dir=...).
if [ -z "${JAVA_HOME:-}" ] && ! command -v java >/dev/null 2>&1; then
  for jdk in "$HOME"/.local/jdk-*/Contents/Home; do
    [ -d "$jdk" ] && export JAVA_HOME="$jdk" && break
  done
fi
if [ ! -f local.properties ] && [ -z "${ANDROID_HOME:-}" ] && [ -d "$HOME/Library/Android/sdk" ]; then
  echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
fi

# On a shared laptop, wait for the machine guard before a heavy build.
if command -v teyla >/dev/null 2>&1; then
  teyla load --wait --kind build
fi

./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
echo "check: ok"
