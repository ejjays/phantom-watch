#!/usr/bin/env bash
# Phantom seedbox setup — Ubuntu 24.04 arm64, runs once over SSH as a sudo user.
# Installs: qbittorrent-nox (torrent engine, localhost web API) + Caddy (public
# HTTPS file server + authed qbt proxy) + nightly cleanup cron.
# Usage: sudo bash setup-seedbox.sh <basic-auth-user>
# Prints the admin password + app config lines at the end. Re-runnable.
set -euo pipefail
ADMIN_USER="${1:?usage: sudo bash setup-seedbox.sh <basic-auth-user>}"
ADMIN_PASS="$(tr -dc 'A-Za-z0-9' </dev/urandom | head -c 24)"
PUB_IP="$(curl -s --max-time 10 ifconfig.me || hostname -I | awk '{print $1}')"
DOMAIN="$(echo "$PUB_IP" | tr '.' '-')".sslip.io"

export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq qbittorrent-nox curl cron python3 ufw > /dev/null

id seedbox 2>/dev/null || useradd -m -s /bin/false seedbox
mkdir -p /srv/seed /etc/seedbox /home/seedbox/.config/qBittorrent
chown -R seedbox:seedbox /srv/seed /home/seedbox/.config

cat > /home/seedbox/.config/qBittorrent/qBittorrent.conf <<'EOF'
[Preferences]
WebUI\Address=127.0.0.1
WebUI\Port=8080
WebUI\LocalHostAuth=true
WebUI\AuthSubnetWhitelist=127.0.0.1/32
WebUI\AuthSubnetWhitelistEnabled=true
WebUI\HostHeaderValidation=false
WebUI\ReverseProxySupportEnabled=true
WebUI\TrustedReverseProxiesList=127.0.0.1
Downloads\SavePath=/srv/seed/
Downloads\TempPathEnabled=false
Queueing\MaxActiveDownloads=5
BitTorrent\DHT=true
BitTorrent\PeX=true
BitTorrent\Encryption=1
Connection\PortRangeMin=6881
EOF
chown seedbox:seedbox /home/seedbox/.config/qBittorrent/qBittorrent.conf

cat > /etc/systemd/system/qbittorrent.service <<'EOF'
[Unit]
Description=qBittorrent-nox seedbox
After=network-online.target
[Service]
User=seedbox
ExecStart=/usr/bin/qbittorrent-nox --webui-port=8080
Restart=on-failure
RestartSec=5
[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable --now qbittorrent

if ! command -v caddy >/dev/null; then
  curl -sL --max-time 60 "https://github.com/caddyserver/caddy/releases/latest/download/caddy_2.9.1_linux_arm64.tar.gz" -o /tmp/caddy.tgz
  tar -xzf /tmp/caddy.tgz -C /usr/bin caddy
  chmod +x /usr/bin/caddy
fi
PASS_HASH="$(caddy hash-password --plaintext "$ADMIN_PASS")"
mkdir -p /etc/caddy
cat > /etc/caddy/Caddyfile <<EOF
$DOMAIN {
	basicauth {
		$ADMIN_USER $PASS_HASH
	}
	handle /qbt/* {
		uri strip_prefix /qbt
		reverse_proxy 127.0.0.1:8080
	}
	handle /files/* {
		uri strip_prefix /files
		file_server {
			root /srv/seed
		}
	}
	handle {
		respond "ok" 200
	}
}
EOF
if [ ! -f /etc/systemd/system/caddy.service ]; then
  cat > /etc/systemd/system/caddy.service <<'EOF'
[Unit]
Description=Caddy seedbox frontend
After=network-online.target
[Service]
ExecStart=/usr/bin/caddy run --config /etc/caddy/Caddyfile
Restart=on-failure
RestartSec=5
AmbientCapabilities=CAP_NET_BIND_SERVICE
[Install]
WantedBy=multi-user.target
EOF
fi
systemctl daemon-reload
systemctl enable --now caddy

cat > /usr/local/bin/cleanup-seed.py <<'EOF'
#!/usr/bin/env python3
# Delete qbt torrents+files completed >48h ago; if disk >85%, oldest first.
import json, shutil, time, urllib.request, urllib.parse
BASE = "http://127.0.0.1:8080/api/v2"
def api(path, data=None):
    body = urllib.parse.urlencode(data).encode() if data else None
    req = urllib.request.Request(BASE + path, data=body)
    with urllib.request.urlopen(req, timeout=20) as r:
        raw = r.read().decode()
        try:
            return json.loads(raw)
        except Exception:
            return raw
now = time.time()
items = api("/torrents/info?filter=all") or []
old = [t for t in items if t.get("completion_on", 0) > 0 and now - t["completion_on"] > 48 * 3600]
def disk_pct():
    u = shutil.disk_usage("/srv/seed")
    return u.used / u.total
cands = sorted(items, key=lambda t: t.get("completion_on", 0))
while old or (cands and disk_pct() > 0.85):
    t = (old or [cands[0]])[0]
    try:
        api("/torrents/delete", {"hashes": t["hash"], "deleteFiles": "true"})
        print("deleted", t.get("name"), flush=True)
    except Exception as e:
        print("delete fail", e, flush=True)
        break
    old = [x for x in old if x["hash"] != t["hash"]]
    cands = [x for x in cands if x["hash"] != t["hash"]]
    if not old and disk_pct() <= 0.85:
        break
EOF
chmod +x /usr/local/bin/cleanup-seed.py
(crontab -l 2>/dev/null; echo "17 4 * * * /usr/local/bin/cleanup-seed.py >>/var/log/seed-cleanup.log 2>&1") | crontab -

ufw allow 22/tcp >/dev/null 2>&1 || true
ufw allow 80/tcp >/dev/null 2>&1 || true
ufw allow 443/tcp >/dev/null 2>&1 || true
ufw allow 6881/tcp >/dev/null 2>&1 || true
ufw allow 6881/udp >/dev/null 2>&1 || true

echo "=== SEEDBOX READY ==="
echo "base=https://$DOMAIN"
echo "user=$ADMIN_USER"
echo "pass=$ADMIN_PASS"
echo "files at https://$DOMAIN/files/<infohash>/<path> (basic auth)"
echo "qbittorrent at https://$DOMAIN/qbt/ (basic auth)"
echo "=== APP local.properties ==="
echo "SEEDBOX_URL=https://$DOMAIN"
echo "SEEDBOX_USER=$ADMIN_USER"
echo "SEEDBOX_PASS=$ADMIN_PASS"
