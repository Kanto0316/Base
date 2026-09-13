#!/bin/sh

# The binary Gradle wrapper JAR is intentionally not versioned because this
# repository accepts source-only changes. CI provisions Gradle before invoking
# this launcher (see .github/workflows/android-build.yml).
if ! command -v gradle >/dev/null 2>&1; then
    echo "Gradle is required. Install Gradle 8.11.1 or use Android Studio." >&2
    exit 1
fi

exec gradle "$@"
