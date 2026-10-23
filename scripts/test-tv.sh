#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
TV_SERIAL=${BRAVIA_TV:-192.168.68.104:5555}
SDK="$ROOT/work/android-sdk"
BT="$SDK/build-tools/35.0.0"
TEST_WORK=$(mktemp -d /private/tmp/bravia-unified-test.XXXXXX)
cleanup_test() {
  adb -s "$TV_SERIAL" uninstall tv.bravia.unifiedtests >/dev/null 2>&1 || :
  BRAVIA_TV="$TV_SERIAL" sh "$ROOT/scripts/enable-home-keys.sh"
  adb -s "$TV_SERIAL" shell am start -W -n tv.bravia.applehome/.MainActivity >/dev/null
  rm -rf "$TEST_WORK"
}
trap cleanup_test EXIT HUP INT TERM
# Run only against the APK built from this checkout.
sh "$ROOT/scripts/build-install.sh"
mkdir -p "$TEST_WORK/classes" "$TEST_WORK/dex"
"$BT/aapt2" link -o "$TEST_WORK/test-unsigned.apk" -I "$SDK/platforms/android-35/android.jar" \
  --manifest "$ROOT/tests/instrumentation/AndroidManifest.xml"
javac -source 8 -target 8 -classpath "$SDK/platforms/android-35/android.jar:$ROOT/launcher/build/classes-output" \
  -d "$TEST_WORK/classes" "$ROOT/tests/instrumentation/src/tv/bravia/unifiedtests/HomeFlowVerification.java"
"$BT/d8" --lib "$SDK/platforms/android-35/android.jar" --classpath "$ROOT/launcher/build/classes-output" \
  --output "$TEST_WORK/dex" "$TEST_WORK/classes/tv/bravia/unifiedtests/"*.class
(cd "$TEST_WORK/dex" && zip -0 -q "$TEST_WORK/test-unsigned.apk" classes.dex)
"$BT/zipalign" -f 4 "$TEST_WORK/test-unsigned.apk" "$TEST_WORK/test-aligned.apk"
"$BT/apksigner" sign --ks "$ROOT/work/debug.keystore" --ks-pass pass:android --key-pass pass:android \
  --out "$TEST_WORK/test.apk" "$TEST_WORK/test-aligned.apk"
adb -s "$TV_SERIAL" install -t -r "$TEST_WORK/test.apk"
adb -s "$TV_SERIAL" shell am instrument -w tv.bravia.unifiedtests/.HomeFlowVerification > "$TEST_WORK/result.txt"
cat "$TEST_WORK/result.txt"
if ! awk '/^INSTRUMENTATION_RESULT: result=PASS/{passed=1} END{exit !passed}' "$TEST_WORK/result.txt"; then
  echo "TV verification failed." >&2
  exit 1
fi
