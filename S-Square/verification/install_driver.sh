#!/usr/bin/env bash
set -euo pipefail
src="$(cd "$(dirname "$0")" && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
cd "$work"
sdk="$ANDROID_HOME/platforms/android-35/android.jar"
tools="$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)"
cat >AndroidManifest.xml <<'XML'
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="org.ssquare.uitest">
  <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="35"/>
  <application android:label="S-Square UI Test"/>
  <instrumentation android:name=".TouchDriver" android:targetPackage="org.ssquare.uitest"/>
</manifest>
XML
mkdir classes dex
javac -source 8 -target 8 -classpath "$sdk" -d classes "$src/TouchDriver.java"
jar cf classes.jar -C classes .
"$tools/d8" --min-api 26 --lib "$sdk" --output dex classes.jar
"$tools/aapt2" link -I "$sdk" --manifest AndroidManifest.xml -o test.apk
(cd dex && zip -q ../test.apk classes.dex)
"$tools/zipalign" -f 4 test.apk aligned.apk
keytool -genkeypair -noprompt -alias test -keyalg RSA -keystore test.p12 -storepass testing123 -dname CN=Emulator -validity 7 >/dev/null
"$tools/apksigner" sign --ks test.p12 --ks-pass pass:testing123 aligned.apk
adb install -r aligned.apk
