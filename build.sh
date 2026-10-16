#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
exec ./gradlew --no-daemon clean :app:assembleRelease :app:bundleRelease "$@"
