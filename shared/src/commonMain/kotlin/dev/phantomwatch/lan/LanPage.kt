package dev.phantomwatch.lan

// Chrome has no HLS support, so the page pulls hls.js (pinned 1.7.4, checked 2026-11-01)
// no "$" anywhere below: this is a Kotlin raw string
object LanPage {
    data class Track(val label: String, val path: String, val default: Boolean)

    fun html(src: String, hls: Boolean, tracks: List<Track>): String = PAGE
        .replace("{{SRC}}", js(src))
        .replace("{{HLS}}", if (hls) "true" else "false")
        .replace("{{SIDECAR}}", if (tracks.isEmpty()) "false" else "true")
        .replace("{{TRACKS}}", tracks.joinToString("") { t ->
            "<track kind=\"subtitles\" label=\"${esc(t.label)}\" src=\"${esc(t.path)}\"" +
                if (t.default) " default>" else ">"
        })

    // the src lands inside a JS string literal, so a query's "&" must stay raw
    private fun js(s: String): String = s
        .replace("\\", "\\\\").replace("\"", "\\\"").replace("<", "\\u003c")

    private fun esc(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private const val PAGE = """
<!doctype html>
<html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Phantom Watch</title>
<style>
  html,body{margin:0;height:100%;background:#000;color:#ccc;
    font:14px/1.4 system-ui,-apple-system,Segoe UI,Roboto,sans-serif}
  body{display:flex;flex-direction:column;justify-content:center;align-items:center}
  video{width:100%;max-height:100vh;background:#000}
  #lv{position:fixed;top:8px;left:10px;font:12px/1 monospace;color:#fff;opacity:.5;pointer-events:none}
  #err{display:none;padding:16px;max-width:560px;text-align:center;color:#f88}
</style></head>
<body>
<video id="v" controls playsinline preload="metadata">{{TRACKS}}</video>
<div id="lv"></div>
<div id="err"></div>
<script src="https://cdn.jsdelivr.net/npm/hls.js@1.7.4/dist/hls.min.js"></script>
<script>
var src = "{{SRC}}";
var isHls = {{HLS}};
var sidecar = {{SIDECAR}};
var v = document.getElementById("v");
var box = document.getElementById("err");
var lv = document.getElementById("lv");
function fail(msg) { box.textContent = msg; box.style.display = "block"; }
// the app picks English by language; manifests instead flag their own default (often Italian)
function english(tracks) {
  for (var i = 0; i < tracks.length; i++) {
    if (/^(en|eng|english)/i.test(tracks[i].lang || "") || /english/i.test(tracks[i].name || "")) return i;
  }
  return -1;
}
v.addEventListener("error", function () {
  fail("This browser cannot decode this stream (codec or container). Try VLC with the same URL.");
});
if (isHls) {
  if (window.Hls && window.Hls.isSupported()) {
    var h = new window.Hls({ enableWorker: true, capLevelToPlayerSize: false });
    h.on(window.Hls.Events.ERROR, function (_, d) {
      if (!d.fatal) return;
      fail(d.type === "networkError"
        ? "The stream stopped responding. Reopen this page, or pick another source on the phone."
        : "This stream is protected or uses a codec the browser lacks.");
    });
    h.on(window.Hls.Events.LEVEL_SWITCHED, function () {
      var l = h.levels[h.currentLevel];
      lv.textContent = l && l.height > 0 ? l.height + "p" : "";
    });
    h.on(window.Hls.Events.MANIFEST_PARSED, function () {
      // hls.js opens on the lowest rung; the phone is already watching 1080p
      var best = 0;
      for (var i = 1; i < h.levels.length; i++) {
        if (h.levels[i].height > h.levels[best].height) best = i;
      }
      h.startLevel = best;
      h.nextLevel = best;
      var a = english(h.audioTracks || []);
      if (a >= 0) h.audioTrack = a;
      // our own <track> elements win when the provider ships sidecar subtitles
      h.subtitleTrack = sidecar ? -1 : english(h.subtitleTracks || []);
    });
    h.attachMedia(v);
    h.loadSource(src);
  } else if (v.canPlayType("application/vnd.apple.mpegurl")) {
    v.src = src;
  } else {
    fail("Could not load the HLS player. Check that this laptop has internet.");
  }
} else {
  v.src = src;
}
</script>
</body></html>
"""
}
