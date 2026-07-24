#!/data/data/com.termux/files/usr/bin/sh
# Build the debug APK and install it through the app's own PackageInstaller session (no adb, no installer URI).
set -eu
cd "$(dirname "$0")"

apk=app/build/outputs/apk/debug/app-debug.apk
pkg=dev.phantomwatch.debug
activity=$pkg/dev.phantomwatch.MainActivity
port=$((8000 + $$ % 1000))

./gradlew :app:assembleDebug --offline -q

apk_of() { pm path "$pkg" 2>/dev/null | sed -n 's/^package://p'; }
before=$(stat -c %Y "$(apk_of)" 2>/dev/null || echo 0)

python3 -m http.server "$port" --bind 127.0.0.1 -d "$(dirname "$apk")" >/dev/null 2>&1 &
server=$!
trap 'kill $server 2>/dev/null' EXIT

sleep 1
am start -n "$pkg/dev.phantomwatch.UpdateInstallActivity" \
    --es url "http://127.0.0.1:$port/$(basename "$apk")" >/dev/null 2>&1 || {
    echo "app has no update helper yet: install $apk by hand once (Files app)"
    exit 1
}

deadline=$(($(date +%s) + 180))
until [ "$(stat -c %Y "$(apk_of)" 2>/dev/null || echo 0)" -gt "$before" ]; do
    [ "$(date +%s)" -lt "$deadline" ] || {
        echo "install did not finish: allow 'install unknown apps' for Phantom Watch and watch the prompt"
        exit 1
    }
    sleep 1
done

am start -n "$activity" >/dev/null
echo "installed and launched $pkg"
