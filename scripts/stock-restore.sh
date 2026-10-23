#!/bin/sh
set -eu

# Restore only the stock Home flow and the packages disabled by this run.
# This does not uninstall anything and does not touch the user's pre-existing
# disabled packages (promos, b2b vendor protocol, selective channel, soft AP).

adb shell pm enable --user 0 com.google.android.apps.tv.launcherx
adb shell pm enable --user 0 com.google.android.tungsten.setupwraith
adb shell pm enable --user 0 com.sony.dtv.sonybugreportsys
adb shell pm enable --user 0 com.sony.dtv.sonyselect
adb shell pm enable --user 0 com.sony.dtv.demosupport
adb shell pm enable --user 0 com.sony.dtv.demosystemsupport
adb shell pm enable --user 0 com.sony.dtv.multiscreendemo
adb shell cmd package set-home-activity com.google.android.apps.tv.launcherx/.home.HomeActivity
adb shell am force-stop tv.bravia.applehome
adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME
echo "Stock Launcher X restored. No packages were uninstalled."
