#!/data/data/com.termux/files/usr/bin/sh
# Build the debug APK and install it through the app's own PackageInstaller session (no adb, no installer URI).
set -eu
cd "$(dirname "$0")/.."

apk=app/build/outputs/apk/debug/app-debug.apk
pkg=dev.phantomwatch.debug
activity=$pkg/dev.phantomwatch.MainActivity
port=$((8000 + $$ % 1000))
legacy=0
if [ "${1:-}" = "--legacy" ]; then
    legacy=1
    echo "legacy mode: serving the APK without the token header"
fi

./gradlew :app:assembleDebug --offline -q

apk_of() { pm path "$pkg" 2>/dev/null | sed -n 's/^package://p'; }
before=$(stat -c %Y "$(apk_of)" 2>/dev/null || echo 0)

# The token keeps other local apps from reading the APK. --legacy serves it without
# one, for installing a build that predates the token check; use it once.
token=$(od -An -N32 -tx1 /dev/urandom | tr -d ' \n')
python3 - "$port" "$token" "$apk" "$legacy" <<'PY' >/dev/null 2>&1 &
import hmac
import http.server
import pathlib
import sys

port, token, apk, legacy = (
    int(sys.argv[1]), sys.argv[2], pathlib.Path(sys.argv[3]).resolve(), sys.argv[4] == "1"
)

class Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path != "/" + apk.name or (
            not legacy and not hmac.compare_digest(self.headers.get("X-PhantomWatch-Update", ""), token)
        ):
            self.send_error(403)
            return
        size = apk.stat().st_size
        self.send_response(200)
        self.send_header("Content-Type", "application/vnd.android.package-archive")
        self.send_header("Content-Length", str(size))
        self.end_headers()
        with apk.open("rb") as source:
            while chunk := source.read(1024 * 1024):
                self.wfile.write(chunk)

    def log_message(self, *_):
        pass

server = http.server.HTTPServer(("127.0.0.1", port), Handler)
server.timeout = 180
server.handle_request()
server.server_close()
PY
server=$!
trap 'kill "$server" 2>/dev/null || true' EXIT

sleep 1
am start -n "$pkg/dev.phantomwatch.UpdateInstallActivity" \
    --es url "http://127.0.0.1:$port/$(basename "$apk")" \
    --es token "$token" >/dev/null 2>&1 || {
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
