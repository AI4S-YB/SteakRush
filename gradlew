#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
LOCAL_GRADLE="$ROOT_DIR/.deps/gradle-8.7/bin/gradle"

if [ -x "$LOCAL_GRADLE" ]; then
  exec "$LOCAL_GRADLE" "$@"
fi

if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi

echo "Gradle not found. Put Gradle 8.7 at .deps/gradle-8.7 or add gradle to PATH." >&2
exit 1
