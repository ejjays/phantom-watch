#!/usr/bin/env python3
"""Serve spike/web with no-store, and proxy /top10 to the telemetry worker.

The page is same-origin with this server, so the chart fetch needs no CORS from
the worker. The worker key is read here and never reaches the bundle.
"""
import functools
import http.server
import os
import sys
import urllib.parse
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..', 'web')
PROPS = os.path.join(HERE, '..', '..', 'local.properties')
PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8080


def prop(name):
    try:
        with open(PROPS) as f:
            for line in f:
                if line.startswith(name + '='):
                    return line.split('=', 1)[1].strip()
    except OSError:
        pass
    return ''


WORKER = prop('TELEMETRY_URL').removesuffix('/e')
KEY = prop('TELEMETRY_KEY')
UA = 'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Safari/537.36'

_geo = None


def country():
    """IP country of this network, which is the only location signal a browser has."""
    global _geo
    if _geo is None:
        _geo = ''
        try:
            req = urllib.request.Request('https://www.cloudflare.com/cdn-cgi/trace', headers={'User-Agent': UA})
            with urllib.request.urlopen(req, timeout=8) as r:
                for line in r.read().decode().splitlines():
                    if line.startswith('loc='):
                        code = line[4:].strip().upper()
                        if len(code) == 2:
                            _geo = code
        except Exception:
            pass
    return _geo


class Handler(http.server.SimpleHTTPRequestHandler):
    def end_headers(self):
        self.send_header('Cache-Control', 'no-store, must-revalidate')
        super().end_headers()

    def do_GET(self):
        if self.path.startswith('/top10'):
            return self.chart()
        if self.path.startswith('/geo'):
            body = ('{"c":"%s"}' % country()).encode()
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Content-Length', str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return
        super().do_GET()

    def chart(self):
        if not WORKER or not KEY:
            return self.send_error(503, 'TELEMETRY_URL/KEY missing in local.properties')
        qs = urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query)
        qs['k'] = [KEY]
        url = WORKER + '/top10?' + urllib.parse.urlencode({k: v[0] for k, v in qs.items()})
        # cloudflare 403s the default Python-urllib agent
        req = urllib.request.Request(url, headers={'User-Agent': UA})
        try:
            with urllib.request.urlopen(req, timeout=25) as r:
                body = r.read()
        except Exception as e:
            return self.send_error(502, str(e))
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt, *a):
        sys.stderr.write('%s %s\n' % (self.address_string(), fmt % a))


http.server.ThreadingHTTPServer(
    ('0.0.0.0', PORT), functools.partial(Handler, directory=ROOT)
).serve_forever()