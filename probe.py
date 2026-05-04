#!/usr/bin/env python3
"""Live provider health check. Metadata only, never fetches media bytes.
Usage: python3 probe.py  (exit 1 if any provider fails)
Needs TMDB_API_KEY from local.properties (vap leg only)."""
import base64, json, os, re, sys, time, urllib.parse
import requests
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

ROOT = os.path.dirname(os.path.abspath(__file__))
BASE = os.path.join(ROOT, "probe_last.json")
UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Safari/537.36"
MOVIES = ("1560520", "980431", "1108427")  # Batman Knightfall, Avatar Aang, Moana
TV, SEASON, EP = "127532", "1", "1"
S = requests.Session()

def get(url, referer, timeout=15):
    t = time.time()
    r = S.get(url, headers={"User-Agent": UA, "Referer": referer}, timeout=timeout)
    return r.status_code, r.text, int((time.time() - t) * 1000)

def heights_m3u8(text):
    return sorted({int(h) for h in re.findall(r"RESOLUTION=\d+x(\d+)", text)})

def en_media(text):
    return any("TYPE=SUBTITLES" in m and re.search(r'LANGUAGE="?en', m)
               for m in re.findall(r"#EXT-X-MEDIA:[^\r\n]*", text))

def res(tag, ok, formats=0, maxh=None, en=False, ms=0, err=""):
    return {"provider": tag, "ok": ok, "formats": formats, "max": maxh,
            "enSubs": en, "ms": ms, "err": err[:120]}

def love(tid, kind, s, e):
    t = time.time()
    try:
        if kind == "movie":
            base, ref = f"https://api.vidlove.cc/movie?id={tid}&mode=json", f"https://player.vidlove.cc/embed/movie/{tid}"
        else:
            base, ref = f"https://api.vidlove.cc/tv?id={tid}&season={s}&episode={e}&mode=json", f"https://player.vidlove.cc/embed/tv/{tid}/{s}/{e}"
        fm, subs, mx = [], [], 0
        for u in (base, base + "&sources=vidapi", base + "&sources=moviebox",
                   base + "&sources=dcloud", base + "&sources=tcloud", base + "&sources=moviebox2"):
            c, b, _ = get(u, ref)
            if c != 200:
                continue
            root = json.loads(b)
            src = root.get("source") or {}
            for q in (src.get("qualities") or []):
                if (q.get("url") or "").startswith("http"):
                    fm.append(q["url"])
                    m = re.search(r"(\d{3,4})p", q.get("quality", ""))
                    mx = max(mx, int(m.group(1)) if m else 0)
            for x in (root.get("subtitles") or []):
                if (x.get("file") or "").startswith("http"):
                    subs.append(x)
        en = any("english" in (x.get("label") or "").lower() for x in subs)
        if not fm:
            return res("love", False, 0, None, en, int((time.time() - t) * 1000), "empty")
        return res("love", True, len(set(fm)), mx or None, en, int((time.time() - t) * 1000))
    except Exception as ex:
        return res("love", False, err=f"{type(ex).__name__}: {ex}", ms=int((time.time() - t) * 1000))

ROCK_KEY = bytes.fromhex("7f3e9c2a8b5d1f4e6a9c3b7d2e5f8a1c4b6d9e2f5a8c1b4d7e9f2a5c8b1d4e7f")

def rock(tid, kind, s, e):
    t = time.time()
    try:
        path = f"movie/{tid}" if kind == "movie" else f"tv/{tid}/{s}/{e}"
        c, b, _ = get(f"https://vidrock.net/api/{path}", "https://vidrock.net/")
        if c != 200:
            return res("rock", False, err=f"http={c}", ms=int((time.time() - t) * 1000))
        api = json.loads(b)
        keys = [k for k in api] + [f"{x}_{y}" for x in ("flix", "hind", "telugu", "tamil", "hindi") for y in ("", "dub", "sub")]
        n = 0
        for k in keys[:8]:
            enc = (api.get(k) or {}).get("url", "")
            if not enc:
                continue
            raw = base64.b64decode(enc.replace("-", "+").replace("_", "/") + "=" * (-len(enc) % 4))
            url = AESGCM(ROCK_KEY).decrypt(raw[:12], raw[12:], None).decode()
            n += url.startswith("http")
        return res("rock", n > 0, n, None, False, int((time.time() - t) * 1000))
    except Exception as ex:
        return res("rock", False, err=f"{type(ex).__name__}: {ex}", ms=int((time.time() - t) * 1000))

def zee(tid, kind, s, e):
    t = time.time()
    try:
        path = f"/streams/movie/{tid}" if kind == "movie" else f"/streams/tv/{tid}/{s}/{e}"
        c, b, _ = get(f"https://core.vidzee.wtf{path}?s=dcloud&e=0", "https://player.vidzee.wtf/")
        url = json.loads(b).get("url", "") if c == 200 else ""
        return res("zee", url.startswith("http"), url.startswith("http"), None, False,
                   int((time.time() - t) * 1000), "" if url.startswith("http") else f"http={c}")
    except Exception as ex:
        return res("zee", False, err=f"{type(ex).__name__}: {ex}", ms=int((time.time() - t) * 1000))

def vix(tid, kind, s, e):
    t = time.time()
    try:
        api = f"https://vixsrc.to/api/movie/{tid}" if kind == "movie" else f"https://vixsrc.to/api/tv/{tid}/{s}/{e}"
        c, b, _ = get(api, "https://vixsrc.to/")
        src = json.loads(b).get("src", "") if c == 200 else ""
        if not src.startswith("/embed/"):
            return res("vix", False, err="no src", ms=int((time.time() - t) * 1000))
        _, html, _ = get(f"https://vixsrc.to{src}", "https://vixsrc.to/")
        tok = re.search(r"""['"]token['"]:\s?['"]([^'"]*)['"]""", html)
        exp = re.search(r"""['"]expires['"]:\s?['"]([^'"]*)['"]""", html)
        pg = re.search(r"""url:\s?['"]([^'"]*)['"]""", html)
        if not (tok and exp and pg):
            return res("vix", False, err="no token", ms=int((time.time() - t) * 1000))
        base = urllib.parse.urlparse(pg.group(1))
        q = (base.query + "&" if base.query else "") + f"token={tok.group(1)}&expires={exp.group(1)}&h=1"
        master = f"{base.scheme}://{base.netloc}{base.path}.m3u8?{q}"
        c2, m3u8, _ = get(master, "https://vixsrc.to/")
        hs = heights_m3u8(m3u8) if c2 == 200 else []
        return res("vix", bool(hs), len(hs), max(hs) if hs else None, en_media(m3u8),
                   int((time.time() - t) * 1000), "" if hs else f"m3u8 http={c2}")
    except Exception as ex:
        return res("vix", False, err=f"{type(ex).__name__}: {ex}", ms=int((time.time() - t) * 1000))

def tmdb_key():
    try:
        for line in open(os.path.join(ROOT, "local.properties")):
            if line.startswith("TMDB_API_KEY="):
                return line.strip().split("=", 1)[1]
    except OSError:
        pass
    return ""

def vap(tid, kind, s, e, key):
    t = time.time()
    try:
        if not key:
            return res("vap", True, err="SKIP no TMDB key")
        ep = "tv" if kind == "tv" else "movie"
        c, b, _ = get(f"https://api.themoviedb.org/3/{ep}/{tid}/external_ids?api_key={key}", "https://www.themoviedb.org/")
        imdb = json.loads(b).get("imdb_id", "") if c == 200 else ""
        if not imdb:
            return res("vap", False, err="no imdb", ms=int((time.time() - t) * 1000))
        q = f"imdb={imdb}&type=movie" if kind == "movie" else f"imdb={imdb}&type=tv&season={s}&episode={e}"
        ref = f"https://nextgencloudfabric.com/embed/movie/{imdb}" if kind == "movie" else f"https://nextgencloudfabric.com/embed/tv/{imdb}/{s}/{e}"
        c2, b2, _ = get(f"https://streamdata.vaplayer.ru/api.php?{q}", ref)
        arr = (json.loads(b2).get("data") or {}).get("stream_urls") or []
        if not (c2 == 200 and arr and arr[0].startswith("http")):
            return res("vap", False, err=f"http={c2}", ms=int((time.time() - t) * 1000))
        _, man, _ = get(arr[0], "https://nextgencloudfabric.com/")
        hs = heights_m3u8(man)
        return res("vap", bool(hs), len(hs), max(hs) if hs else None, en_media(man),
                   int((time.time() - t) * 1000), "" if hs else "empty manifest")
    except Exception as ex:
        return res("vap", False, err=f"{type(ex).__name__}: {ex}", ms=int((time.time() - t) * 1000))

def paradise(tid, kind, s, e):
    t = time.time()
    try:
        if kind != "tv" or s != "1":
            return res("paradise", True, err="SKIP tv-s1 only")
        def js(path):
            c, b, _ = get(f"https://api.animeparadise.moe{path}", "https://animeparadise.moe/")
            return json.loads(b) if c == 200 else None
        norm = lambda x: re.sub(r"[^a-z0-9]+", "", x.lower())
        want, show, link = norm("Solo Leveling"), "", ""
        data = (js(f"/search?q=Solo%20Leveling&limit=20") or {}).get("data") or []
        for o in data:
            if (norm(o.get("title", "")) == want or norm(o.get("link", "")) == want) and (js(f"/anime/{o.get('link', '')}") or {}).get("data", {}).get("type") == "TV":
                show, link = o["_id"], o["link"]
                break
        if not show:
            return res("paradise", False, err="show not found", ms=int((time.time() - t) * 1000))
        uid = next((o.get("uid", "") for o in ((js(f"/anime/{show}/episode") or {}).get("data") or [])
                    if str(o.get("number")) == e and o.get("uid")), "")
        if not uid:
            return res("paradise", False, err="ep not found", ms=int((time.time() - t) * 1000))
        ep = ((js(f"/ep/{uid}?origin={show}") or {}).get("data") or {}).get("episode") or {}
        if not ep.get("streamLink"):
            return res("paradise", False, err="no streamLink", ms=int((time.time() - t) * 1000))
        en = any(x.get("type") == "vtt" and "english" in x.get("label", "").lower() for x in (ep.get("subData") or []))
        mu = "https://stream.animeparadise.moe/m3u8?url=" + urllib.parse.quote(ep["streamLink"], safe="")
        _, man, _ = get(mu, "https://animeparadise.moe/")
        hs = heights_m3u8(man) if "#EXTM3U" in man else []
        return res("paradise", bool(hs), len(hs), max(hs) if hs else None, en or en_media(man),
                   int((time.time() - t) * 1000), "" if hs else "empty manifest")
    except Exception as ex:
        return res("paradise", False, err=f"{type(ex).__name__}: {ex}", ms=int((time.time() - t) * 1000))

def vidlink(tid, kind, s, e):
    t = time.time()
    try:
        c, b, _ = get(f"https://enc-dec.app/api/enc-vidlink?text={tid}", "https://vidlink.pro/")
        enc = json.loads(b).get("result", "") if c == 200 else ""
        if not enc:
            return res("vidlink", False, err="enc failed", ms=int((time.time() - t) * 1000))
        path = f"movie/{enc}" if kind == "movie" else f"tv/{enc}/{s}/{e}"
        c2, b2, _ = get(f"https://vidlink.pro/api/b/{path}", "https://vidlink.pro/")
        st = (json.loads(b2).get("stream") or {}) if c2 == 200 else {}
        qs = st.get("qualities") or {}
        caps = st.get("captions") or []
        en = any((x.get("language") or "").lower() == "english" for x in caps)
        hs = [int(k) for k in qs if k.isdigit()]
        return res("vidlink", bool(hs), len(hs), max(hs) if hs else None, en,
                   int((time.time() - t) * 1000), "" if hs else f"http={c2}")
    except Exception as ex:
        return res("vidlink", False, err=f"{type(ex).__name__}: {ex}", ms=int((time.time() - t) * 1000))

MEGA = "https://megaplay.buzz"
MEGA_KEY = b"i?LMTAx0Q6,:}50U".ljust(32, b"\0")
MEGA_IV = b"hlsMediaAttached"

def hianime(tid, kind, s, e, key):
    t = time.time()
    try:
        if kind != "tv":
            return res("hianime", True, err="SKIP tv only")
        if not key:
            return res("hianime", True, err="SKIP no TMDB key")
        ep = "tv" if kind == "tv" else "movie"
        c, b, _ = get(f"https://api.themoviedb.org/3/{ep}/{tid}?api_key={key}", "https://www.themoviedb.org/")
        if c != 200:
            return res("hianime", False, err=f"tmdb http={c}", ms=int((time.time() - t) * 1000))
        title = (json.loads(b).get("name") or json.loads(b).get("title") or "").split(":")[0].strip()
        if not title:
            return res("hianime", True, err="SKIP no title")
        q = {"query": "query($s:String){Media(search:$s,type:ANIME){idMal}}", "variables": {"s": title}}
        r = S.post("https://graphql.anilist.co", json=q, headers={"User-Agent": UA, "Referer": "https://anilist.co/"}, timeout=15)
        mal = ((r.json().get("data") or {}).get("Media") or {}).get("idMal") if r.status_code == 200 else None
        if not mal:
            return res("hianime", True, err="SKIP no MAL mapping")
        epnum = e if kind == "tv" else "1"
        page = f"{MEGA}/stream/mal/{mal}/{epnum}/sub"
        c, html, _ = get(page, page)
        did = re.search(r'data-id=["\'](\d+)["\']', html if c == 200 else "")
        if not did:
            return res("hianime", False, err="no data-id", ms=int((time.time() - t) * 1000))
        c2, js, _ = get(f"{MEGA}/stream/getSources?id={did.group(1)}&id={did.group(1)}", page)
        enc = (json.loads(js).get("enc") if c2 == 200 else "") or ""
        if not enc:
            return res("hianime", False, err="no enc", ms=int((time.time() - t) * 1000))
        raw = base64.b64decode(enc.replace("-", "+").replace("_", "/") + "=" * (-len(enc) % 4))
        dec = Cipher(algorithms.AES(MEGA_KEY), modes.CBC(MEGA_IV)).decryptor()
        pt = dec.update(raw) + dec.finalize()
        m = re.search(r"([a-z0-9.-]+\.[a-z]+/anime/[a-f0-9]+/[a-f0-9]+/master\.m3u8)", pt.decode("utf-8", "replace"))
        if not m:
            return res("hianime", False, err="decrypt no master", ms=int((time.time() - t) * 1000))
        c3, man, _ = get("https://" + m.group(1), MEGA + "/")
        hs = heights_m3u8(man) if c3 == 200 else []
        en = any((x.get("label") or "").lower() == "english" for x in ((json.loads(js).get("tracks") or [])))
        if not hs:
            return res("hianime", False, err=f"manifest http={c3}", ms=int((time.time() - t) * 1000))
        return res("hianime", True, len(hs), max(hs), en, int((time.time() - t) * 1000))
    except Exception as ex:
        return res("hianime", False, err=f"{type(ex).__name__}: {ex}", ms=int((time.time() - t) * 1000))

JOBS = [("movie", t, None, None) for t in MOVIES] + [("tv", TV, SEASON, EP)]
LEGS = [love, rock, zee, vix, vap, paradise, vidlink, hianime]

def main():
    key = tmdb_key()
    prev = {}
    try:
        prev = json.load(open(BASE))
    except (OSError, ValueError):
        pass
    rows, cur, fails = [], {}, 0
    for kind, tid, s, e in JOBS:
        def run(leg):
            return leg(tid, kind, s, e, key) if leg in (vap, hianime) else leg(tid, kind, s, e)
        for leg in LEGS:
            r = run(leg)
            if not r["ok"] and not str(r["err"]).startswith("SKIP"):
                time.sleep(5)
                r = run(leg)
                if r["ok"]:
                    r["retry"] = True
            r["title"] = f"{kind}/{tid}" + (f" s{s}e{e}" if kind == "tv" else "")
            rows.append(r)
            k = f"{leg.__name__}:{r['title']}"
            cur[k] = {"ok": r["ok"], "formats": r["formats"], "max": r["max"]}
            p = prev.get(k)
            r["flag"] = "" if not p or p["ok"] == r["ok"] else ("recovered" if r["ok"] else "REGRESSED")
            if str(r["err"]).startswith("SKIP"):
                r["flag"] = "skip"
            elif not r["ok"]:
                fails += 1
    print(f"{'provider':9} {'title':16} {'ms':>6} {'fmts':>4} {'max':>5} en {'st':6} note")
    for r in rows:
        mx = f"{r['max']}p" if r["max"] else "-"
        note = (r["flag"] or r["err"]) + (" retry" if r.get("retry") else "")
        print(f"{r['provider']:9} {r['title']:16} {r['ms']:6} {r['formats']:4} {mx:>5} "
              f"{'Y' if r['enSubs'] else '-'} {('FAIL' if not r['ok'] and not r['flag'] == 'skip' else 'ok'):6} {note}")
    json.dump(cur, open(BASE, "w"), indent=1)
    print(f"\n{len(rows) - fails}/{len(rows)} ok" + (" — baseline updated" if not prev else ""))
    sys.exit(1 if fails else 0)

main()
