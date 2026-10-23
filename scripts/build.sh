#!/bin/sh
set -eu

case "${1:-}" in
  ""|--build-only) ;;
  *) echo "Usage: sh scripts/build.sh" >&2; exit 2 ;;
esac
TV_SERIAL=${BRAVIA_TV:-192.168.68.104:5555}

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
SDK="$ROOT/work/android-sdk"
JAVAC=javac
BT="$SDK/build-tools/35.0.0"

cd "$ROOT/launcher"
mkdir -p build/classes-output build/dex-output build/gen-output
find build/classes-output -type f -name '*.class' -delete
mkdir -p build/private-assets

# The Android app reads an asset at runtime, so stage local environment values
# only for AAPT2 packaging. Never commit this generated properties file.
FILE_OMDB_KEY=
FILE_WATCHMODE_KEY=
FILE_MOTN_KEY=
FILE_TMDB_KEY=
if [ -f "$ROOT/.env" ]; then
  while IFS= read -r env_line || [ -n "$env_line" ]; do
    case "$env_line" in ''|\#*) continue ;; *=*) ;; *) continue ;; esac
    env_name=${env_line%%=*}
    env_value=${env_line#*=}
    case "$env_name" in
      OMDB_API_KEY) FILE_OMDB_KEY=$env_value ;;
      WATCHMODE_API_KEY) FILE_WATCHMODE_KEY=$env_value ;;
      MOVIEOFTHENIGHT_API_KEY) FILE_MOTN_KEY=$env_value ;;
      TMDB_READ_ACCESS_TOKEN) FILE_TMDB_KEY=$env_value ;;
    esac
  done < "$ROOT/.env"
fi
BUILD_OMDB_KEY=${OMDB_API_KEY:-$FILE_OMDB_KEY}
BUILD_WATCHMODE_KEY=${WATCHMODE_API_KEY:-$FILE_WATCHMODE_KEY}
BUILD_MOTN_KEY=${MOVIEOFTHENIGHT_API_KEY:-$FILE_MOTN_KEY}
BUILD_TMDB_KEY=${TMDB_READ_ACCESS_TOKEN:-$FILE_TMDB_KEY}
umask 077
printf 'omdb=%s\nwatchmode=%s\nmovieofthenight=%s\ntmdb=%s\n' \
  "$BUILD_OMDB_KEY" "$BUILD_WATCHMODE_KEY" "$BUILD_MOTN_KEY" "$BUILD_TMDB_KEY" \
  > build/private-assets/movie-api.properties
cleanup_private_build() {
  rm -f build/private-assets/movie-api.properties build/unsigned-output.apk \
    build/with-dex-output.apk build/aligned-output.apk build/sony-bravia-unified-home.apk
}
trap cleanup_private_build EXIT HUP INT TERM
"$BT/aapt2" compile --dir app/src/main/res -o build/res-output.zip
"$BT/aapt2" link -o build/unsigned-output.apk -I "$SDK/platforms/android-35/android.jar" \
  --manifest app/src/main/AndroidManifest.xml --java build/gen-output --auto-add-overlay --min-sdk-version 26 \
  --target-sdk-version 35 --version-code 1 --version-name 1.0 -A build/private-assets build/res-output.zip
rm -f build/private-assets/movie-api.properties
"$JAVAC" -source 8 -target 8 -classpath "$SDK/platforms/android-35/android.jar" \
  -d build/classes-output build/gen-output/tv/bravia/applehome/R.java \
  $(find app/src/main/java -name '*.java' -print)
"$BT/d8" --lib "$SDK/platforms/android-35/android.jar" --output build/dex-output \
  $(find build/classes-output -name '*.class' -print)
cp build/unsigned-output.apk build/with-dex-output.apk
(cd build/dex-output && zip -0 -q ../with-dex-output.apk classes.dex)
"$BT/zipalign" -f 4 build/with-dex-output.apk build/aligned-output.apk
"$BT/apksigner" sign --ks "$ROOT/work/debug.keystore" --ks-pass pass:android \
  --key-pass pass:android --out build/sony-bravia-unified-home.apk build/aligned-output.apk
mkdir -p "$ROOT/outputs"
cp build/sony-bravia-unified-home.apk "$ROOT/outputs/sony-bravia-unified-home.apk"

echo "Built $ROOT/outputs/sony-bravia-unified-home.apk"
