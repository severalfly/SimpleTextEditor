#!/usr/bin/env bash
# 在 Git Bash 里编译
set -e
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export JAVA_HOME="$ROOT/toolchain/jdk17"
export ANDROID_HOME="$ROOT/toolchain/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
TASK="${1:-assembleDebug}"
exec "$ROOT/toolchain/gradle-8.13/bin/gradle" "$TASK" --console=plain
