#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
TV_SERIAL=${BRAVIA_TV:-192.168.68.104:5555}
adb connect "$TV_SERIAL"
adb -s "$TV_SERIAL" install -r "$ROOT/outputs/sony-bravia-unified-home.apk"
adb -s "$TV_SERIAL" shell pm grant tv.bravia.applehome android.permission.ACCESS_FINE_LOCATION
adb -s "$TV_SERIAL" shell pm enable --user 0 com.google.android.apps.tv.launcherx
adb -s "$TV_SERIAL" shell cmd package set-home-activity tv.bravia.applehome/.MainActivity
BRAVIA_TV="$TV_SERIAL" sh "$ROOT/scripts/enable-home-keys.sh"
adb -s "$TV_SERIAL" shell am start -n tv.bravia.applehome/.MainActivity
echo "Installed Sony Bravia Unified Home. Hold Home for 3 seconds to choose a layout."
