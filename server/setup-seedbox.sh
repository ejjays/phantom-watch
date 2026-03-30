#!/usr/bin/env bash
# Phantom seedbox setup — Ubuntu 24.04 arm64, runs once over SSH as a sudo user.
# Installs: qbittorrent-nox (torrent engine, localhost web API) + Caddy (public
# HTTPS file server + authed qbt proxy) + nightly cleanup cron.
# Usage: sudo bash setup-seedbox.sh <basic-auth-user>
# Prints the admin password + app config lines at the end. Re-runnable.
set -euo pipefail
ADMIN_USER="${1:?usage: sudo bash setup-seedbox.sh <basic-auth-user>}"
ADMIN_PASS="$(python3 -c 'import secrets, string; print("".join(secrets.choice(string.ascii_letters + string.digits) for _ in range(24)))')"
PUB_IP="$(curl -s --max-time 10 ifconfig.me || hostname -I | awk '{print $1}')"
DOMAIN="$(echo "$PUB_IP" | tr '.' '-').sslip.io"

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
WebUI\LocalHostAuth=false
WebUI\AuthSubnetWhitelist=0.0.0.0/0, ::/0
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

for i in $(seq 1 30); do
  curl -s --max-time 5 http://127.0.0.1:8080/api/v2/app/version >/dev/null 2>&1 && break
  sleep 10
done
python3 - <<'EOF'
import json, urllib.request
trs = ["udp://tracker.opentrackr.org:1337/announce", "udp://open.stealth.si:80/announce",
  "udp://tracker.torrent.eu.org:451/announce", "udp://explodie.org:6969/announce",
  "udp://tracker.bittor.pw:6969/announce", "udp://tracker0.ufibox.com:6969/announce",
  "udp://exodus.desync.com:6969/announce", "udp://tracker.moeking.me:6969/announce",
  "udp://tracker1.bt.moack.co.kr:80/announce", "udp://tracker2.dler.org:80/announce"]
payload = json.dumps({"listen_port": 6881, "announce_to_all_trackers": True,
  "announce_to_all_tiers": True, "add_trackers_enabled": True, "add_trackers": "\n".join(trs)})
req = urllib.request.Request("http://127.0.0.1:8080/api/v2/app/setPreferences",
  data=urllib.parse.urlencode({"json": payload}).encode())
try:
    urllib.request.urlopen(req, timeout=20).read()
    print("qbt tuned")
except Exception as e:
    print("qbt tune skipped:", e)
EOF

if ! command -v caddy >/dev/null; then
  ARCH="$(uname -m)"
  case "$ARCH" in
    x86_64) CADDY_ARCH="amd64" ;;
    aarch64) CADDY_ARCH="arm64" ;;
    *) CADDY_ARCH="amd64" ;;
  esac
  curl -sL --max-time 60 "https://github.com/caddyserver/caddy/releases/download/v2.9.1/caddy_2.9.1_linux_${CADDY_ARCH}.tar.gz" -o /tmp/caddy.tgz
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
systemctl restart caddy

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
(crontab -l 2>/dev/null || true; echo "17 4 * * * /usr/local/bin/cleanup-seed.py >>/var/log/seed-cleanup.log 2>&1") | crontab -

chmod 755 /srv/seed || true
ufw allow 22/tcp >/dev/null 2>&1 || true
ufw allow 80/tcp >/dev/null 2>&1 || true
ufw allow 443/tcp >/dev/null 2>&1 || true
ufw allow 6881/tcp >/dev/null 2>&1 || true
ufw allow 6881/udp >/dev/null 2>&1 || true

# Oracle Linux / Ubuntu iptables default reject rule handling
if iptables -L INPUT -n | grep -q "reject-with icmp-host-prohibited"; then
  for port in 80 443 6881; do
    iptables -C INPUT -p tcp --dport "$port" -j ACCEPT 2>/dev/null || iptables -I INPUT 6 -p tcp --dport "$port" -j ACCEPT
  done
  iptables -C INPUT -p udp --dport 6881 -j ACCEPT 2>/dev/null || iptables -I INPUT 6 -p udp --dport 6881 -j ACCEPT
  command -v netfilter-persistent >/dev/null && netfilter-persistent save 2>/dev/null || true
fi

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
