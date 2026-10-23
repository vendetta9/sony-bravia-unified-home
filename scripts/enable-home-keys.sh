#!/bin/sh
set -eu
TV_SERIAL=${BRAVIA_TV:-192.168.68.104:5555}
HOME_HELPER=tv.bravia.applehome/.input.HomeKeyService
TV_SERVICES=$(adb -s "$TV_SERIAL" shell settings get secure enabled_accessibility_services | tr -d '\r')
TV_OTHER_SERVICES=$(printf '%s' "$TV_SERVICES" | tr ':' '\n' | awk '
  NF && $0 != "null" &&
  $0 != "tv.bravia.applehome/.HomeKeyService" &&
  $0 != "tv.bravia.applehome/tv.bravia.applehome.HomeKeyService" &&
  $0 != "tv.bravia.applehome/.input.HomeKeyService" &&
  $0 != "tv.bravia.applehome/tv.bravia.applehome.input.HomeKeyService" { print }
' | paste -sd ':' -)
# Explicitly unbind/rebind this helper to clear Android's crashed-service marker.
# Instrumentation finishing can stop the target process without a normal service lifecycle.
if [ -n "$TV_OTHER_SERVICES" ]; then
  adb -s "$TV_SERIAL" shell settings put secure enabled_accessibility_services "$TV_OTHER_SERVICES"
  TV_SERVICES="$TV_OTHER_SERVICES:$HOME_HELPER"
else
  adb -s "$TV_SERIAL" shell settings delete secure enabled_accessibility_services
  TV_SERVICES="$HOME_HELPER"
fi
sleep 1
adb -s "$TV_SERIAL" shell settings put secure enabled_accessibility_services "$TV_SERVICES"
adb -s "$TV_SERIAL" shell settings put secure accessibility_enabled 1
sleep 1
# Binding is asynchronous on this Sony firmware; do not return success early.
for HOME_BIND_ATTEMPT in 1 2 3 4 5 6 7 8 9 10; do
  if adb -s "$TV_SERIAL" shell dumpsys accessibility | awk '/Bound services:/ && /Sony Bravia Unified Home keys/{bound=1} END{exit !bound}'; then
    echo "Home-key helper bound."
    exit 0
  fi
  sleep 1
done
echo "Home-key helper did not bind; check Accessibility settings." >&2
exit 1
