#!/usr/bin/env bash
# Installs the Android SDK pieces this project needs to compile an APK.
# Idempotent: re-running it is a no-op once everything is in place.
set -euo pipefail

export DEBIAN_FRONTEND=noninteractive
export ANDROID_HOME="${ANDROID_HOME:-/home/vscode/android-sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"

CMDLINE_TOOLS_VERSION="13114758"   # Android cmdline-tools 19.0
GRADLE_VERSION="8.11.1"            # minimum for AGP 8.8.x is 8.10.2
GRADLE_DIST="/home/vscode/gradle-${GRADLE_VERSION}"
SDKMANAGER="${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager"

export PATH="${GRADLE_DIST}/bin:${PATH}"

sudo apt-get update -qq
sudo apt-get install -y -qq --no-install-recommends unzip zip curl ca-certificates

mkdir -p "${ANDROID_HOME}/cmdline-tools"

if [ ! -x "${SDKMANAGER}" ]; then
  echo "==> Installing Android command-line tools (${CMDLINE_TOOLS_VERSION})"
  tmp="$(mktemp -d)"
  curl -fsSL -o "${tmp}/cmdline-tools.zip" \
    "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip"
  unzip -q "${tmp}/cmdline-tools.zip" -d "${tmp}/unzipped"
  rm -rf "${ANDROID_HOME}/cmdline-tools/latest"
  mv "${tmp}/unzipped/cmdline-tools" "${ANDROID_HOME}/cmdline-tools/latest"
  rm -rf "${tmp}"
else
  echo "==> Android command-line tools already present"
fi

echo "==> Accepting SDK licenses"
yes 2>/dev/null | "${SDKMANAGER}" --licenses >/dev/null || true

echo "==> Installing SDK packages"
"${SDKMANAGER}" --install "platform-tools" "platforms;android-35" "build-tools;35.0.0"

if [ ! -x "${GRADLE_DIST}/bin/gradle" ]; then
  echo "==> Installing Gradle ${GRADLE_VERSION}"
  tmp="$(mktemp -d)"
  curl -fsSL -o "${tmp}/gradle.zip" \
    "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
  unzip -q "${tmp}/gradle.zip" -d /home/vscode
  rm -rf "${tmp}"
else
  echo "==> Gradle ${GRADLE_VERSION} already present"
fi

echo "==> Installed SDK packages"
"${SDKMANAGER}" --list_installed

echo "==> setup-android.sh complete"
