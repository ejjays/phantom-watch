// web player chrome, phone draws in compose over SurfaceView, browser uses <video>, same layout rebuilt in DOM/CSS from PlayerStage.kt, dp == css px
(function () {
  'use strict';

  var CYAN = '#22D3EE';
  var SLATE200 = '#E2E8F0';
  var ICON = window.PW_ICONS || {};
  var EASE = 'cubic-bezier(.4,0,.2,1)';

  // console trail for playback problems, index.html holds sink
  function log(t, m) { if (window.pwLog) window.pwLog(t, m); }

  // page keeps one recent list for library tab and continue rail so playhead saved there not second store
  function seekStart(S) {
    var v = S.v, t = S.startAt || 0;
    if (t > 0 && v.duration > 0 && t < v.duration * 1000 - 30000) {
      try { v.currentTime = t / 1000; } catch (e) {}
      log('play', 'resume at ' + Math.round(t / 1000) + 's');
    }
  }

  var CSS = ''
    + '@font-face{font-family:PWRubik;src:url(./composeResources/dev.phantomwatch.res/font/rubik_regular.ttf) format("truetype");font-weight:400}'
    + '@font-face{font-family:PWRubik;src:url(./composeResources/dev.phantomwatch.res/font/rubik_medium.ttf) format("truetype");font-weight:500}'
    + '@font-face{font-family:PWRubik;src:url(./composeResources/dev.phantomwatch.res/font/rubik_semibold.ttf) format("truetype");font-weight:600}'
    + '@font-face{font-family:PWRubik;src:url(./composeResources/dev.phantomwatch.res/font/rubik_bold.ttf) format("truetype");font-weight:700}'
    + '@font-face{font-family:PWMono;src:url(./composeResources/dev.phantomwatch.res/font/ibmplexmono_regular.ttf) format("truetype");font-weight:400}'
    + '@font-face{font-family:PWMono;src:url(./composeResources/dev.phantomwatch.res/font/ibmplexmono_semibold.ttf) format("truetype");font-weight:600}'

    + '.pw-stage{position:fixed;left:0;top:0;right:auto;bottom:auto;width:100%;height:100%;z-index:2;font-family:PWRubik,system-ui,sans-serif;'
    + 'color:#fff;-webkit-user-select:none;user-select:none;-webkit-tap-highlight-color:transparent;touch-action:manipulation}'
    + '.pw-stage *{box-sizing:border-box;margin:0;padding:0}'
    // pinch is ours in landscape, left to browser it zooms page instead
    + '.pw-stage[data-zoom="1"],.pw-stage[data-zoom="1"] .pw-zones,.pw-stage[data-zoom="1"] video{touch-action:none}'
    + '.pw-zones{position:absolute;inset:0;display:flex}'
    + '.pw-zone{flex:1;height:100%}'
    + '.pw-fade{opacity:0;visibility:hidden;transition:opacity .25s ' + EASE + ',visibility 0s .25s}'
    + '.pw-stage[data-on="1"] .pw-fade{opacity:1;visibility:visible;transition:opacity .25s ' + EASE + ',visibility 0s}'

    // header sits above scrim (z-index 2) so icons stay legible while picture dims
    + '.pw-scrim{position:absolute;left:0;right:0;top:0;bottom:0;background:rgba(0,0,0,.4)}'

    + '.pw-top{position:absolute;left:0;right:0;top:0;padding:8px 8px 0 0;display:flex;align-items:center;min-height:56px;z-index:2}'
    // page is viewport-fit:cover, so chrome keeps itself off notch
    + '.pw-top{padding-left:env(safe-area-inset-left);padding-right:calc(8px + env(safe-area-inset-right))}'
    + '.pw-ib{background:none;border:0;color:#fff;display:flex;align-items:center;justify-content:center;cursor:pointer;flex:0 0 auto}'
    + '.pw-ib:focus{outline:none}'
    + '.pw-ib:focus-visible{outline:2px solid ' + CYAN + ';outline-offset:-2px;border-radius:50%}'
    + '.pw-back{width:48px;height:48px;margin-left:4px}'
    + '.pw-title{flex:1;min-width:0;font-size:15px;font-weight:600;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}'
    + '.pw-top .pw-ib{width:48px;height:48px}'
    + '.pw-cc[data-off="1"]{opacity:.4}'
    + '.pw-cc[data-on="1"]{color:' + CYAN + '}'
    + '.pw-gear[data-off="1"]{opacity:.4}'
    + '.pw-gear[data-on="1"]{color:' + CYAN + '}'
    + '.pw-glyph{width:20px;height:20px;display:block}'

    + '.pw-bottom{position:absolute;left:0;right:0;bottom:0;padding:0 16px 8px;display:flex;flex-direction:column;z-index:2}'
    + '.pw-bottom{padding-left:calc(16px + env(safe-area-inset-left));padding-right:calc(16px + env(safe-area-inset-right))}'
    // as PlayerStage does: row keeps its 48dp targets and bar hit box runs under them
    + '.pw-stage[data-fs="0"] .pw-bottom{flex-direction:column-reverse;padding-bottom:0}'
    + '.pw-stage[data-fs="0"] .pw-bottom .pw-row{margin-bottom:-16px}'
    // portrait keeps title under stage (PlayerPanel), so one beside back repeats it
    + '@media (orientation:portrait){.pw-stage:not([data-fs="1"]) .pw-title{display:none}'
    + '.pw-stage:not([data-fs="1"]) .pw-top .pw-cc{margin-left:auto}}'
    + '.pw-seek{position:relative;height:24px;touch-action:none;cursor:pointer}'
    // PlayerStage keeps slim bar on picture while chrome is away, playhead stays readable
    // without waking controls
    + '.pw-slim{position:absolute;left:16px;right:16px;bottom:env(safe-area-inset-bottom,0px);height:16px;z-index:3;'
    + 'opacity:0;visibility:hidden;cursor:pointer;touch-action:none;'
    + 'transition:opacity .25s ' + EASE + ',visibility 0s .25s}'
    + '.pw-stage[data-fs="0"][data-on="0"]:not([data-lock="1"]) .pw-slim{opacity:1;visibility:visible;'
    + 'transition:opacity .25s ' + EASE + ',visibility 0s}'
    + '.pw-slim .pw-played{background:#fff}'
    + '.pw-scrubtime{position:absolute;left:0;right:0;bottom:24px;text-align:center;font:12px PWMono,monospace;color:#fff;padding-bottom:4px;display:none}'
    + '.pw-scrubtime[data-on="1"]{display:block}'
    + '.pw-trackwrap{position:absolute;left:0;right:0;bottom:0;height:3px;border-radius:2px;background:rgba(255,255,255,.25);overflow:hidden}'
    + '.pw-buf{position:absolute;left:0;top:0;bottom:0;width:0;background:rgba(255,255,255,.4)}'
    + '.pw-played{position:absolute;left:0;top:0;bottom:0;width:0;background:' + CYAN + ';border-radius:0 1.5px 1.5px 0}'
    + '.pw-thumb{position:absolute;bottom:-5px;width:14px;height:14px;border-radius:50%;background:' + CYAN + ';display:none;pointer-events:none}'
    + '.pw-thumb[data-on="1"]{display:block}'

    + '.pw-row{display:flex;align-items:center;width:100%}'
    + '.pw-clock{flex:1;font:11px/1.2 PWMono,monospace;color:' + SLATE200 + '}'
    + '.pw-row .pw-ib{width:48px;height:48px}'

    + '.pw-playbtn{position:absolute;left:50%;top:50%;width:56px;height:56px;margin:-28px 0 0 -28px;border:0;'
    + 'border-radius:50%;background:rgba(0,0,0,.6);color:#fff;display:flex;align-items:center;justify-content:center;'
    + 'cursor:pointer;transform:scale(.85);transition:transform .3s ' + EASE + '}'
    + '.pw-stage[data-on="1"] .pw-playbtn{transform:scale(1)}'
    + '.pw-playbtn:focus{outline:none}'
    + '.pw-playbtn:focus-visible{outline:2px solid ' + CYAN + ';outline-offset:2px}'
    + '.pw-playbtn svg{width:26px;height:26px}'
    + '.pw-playbtn[data-paused="1"] svg{width:30px;height:30px}'
    // glyph wrapper is 20px elsewhere, here it matches icon or svg overflows and flex centres
    // wrapper not icon
    + '.pw-playbtn .pw-glyph{width:26px;height:26px}'
    + '.pw-playbtn[data-paused="1"] .pw-glyph{width:30px;height:30px}'
    + '.pw-stage[data-stall="1"] .pw-playbtn,.pw-stage[data-stall="1"] .pw-hud{display:none}'
    // PlayerResolving pins chrome open while resolver runs, stage reads as player waiting
    + '.pw-stage[data-stall="1"] .pw-fade{opacity:1;visibility:visible;transition:none}'

    + '.pw-lock{position:absolute;left:8px;top:50%;width:44px;height:44px;margin-top:-22px;border:0;background:none;'
    + 'color:#fff;display:none;align-items:center;justify-content:center;cursor:pointer}'
    + '.pw-lock{left:calc(8px + env(safe-area-inset-left))}'
    + '.pw-stage[data-fs="1"] .pw-lock{display:flex}'
    + '.pw-stage[data-lock="1"][data-on="1"] .pw-top,'
    + '.pw-stage[data-lock="1"][data-on="1"] .pw-bottom,'
    + '.pw-stage[data-lock="1"][data-on="1"] .pw-playbtn,'
    + '.pw-stage[data-lock="1"][data-on="1"] .pw-scrim{opacity:0;visibility:hidden;transition:opacity .25s ' + EASE + ',visibility 0s .25s}'

    + '.pw-spin{position:absolute;left:50%;top:50%;width:48px;height:48px;margin:-24px 0 0 -24px;display:none;pointer-events:none;animation:pwspin 1s linear infinite}'
    + '.pw-stage[data-stall="1"] .pw-spin{display:block}'
    + '@keyframes pwspin{to{transform:rotate(360deg)}}'

    + '.pw-hud{position:absolute;top:0;bottom:0;width:50%;display:flex;align-items:center;justify-content:center;'
    + 'opacity:0;visibility:hidden;transform:scale(.9);pointer-events:none;transition:opacity .15s ' + EASE + ',transform .15s ' + EASE + ',visibility 0s .15s}'
    + '.pw-hud[data-on="1"]{opacity:1;visibility:visible;transform:scale(1);transition:opacity .12s ' + EASE + ',transform .16s ' + EASE + ',visibility 0s}'
    + '.pw-hud[data-side="l"]{left:0}.pw-hud[data-side="r"]{right:0}'
    + '.pw-hud svg{width:30px;height:30px;transform:scaleX(var(--flip,1))}'
    + '.pw-hud[data-side="l"]{--flip:-1}'
    + '.pw-hud-n{font-size:20px;font-weight:600;margin-left:8px}'
    + '.pw-hud-icon{display:block;width:30px;height:30px}'
    + '.pw-hud[data-nudge="1"] svg{animation:pwnudge .5s ' + EASE + '}'
    + '@keyframes pwnudge{0%{transform:scaleX(var(--flip,1)) translateX(0)}40%{transform:scaleX(var(--flip,1)) translateX(2px)}100%{transform:scaleX(var(--flip,1)) translateX(0)}}'

    + '.pw-flash{position:absolute;left:50%;top:12.5%;transform:translateX(-50%);max-width:80%;display:none;'
    + 'padding:6px 12px;border-radius:999px;background:rgba(0,0,0,.7);font-size:16px;font-weight:700;text-align:center}'
    + '.pw-flash[data-on="1"]{display:block}'
    + '.pw-flash[data-tap="1"]{cursor:pointer}'

    + '.pw-subs{position:fixed;display:flex;justify-content:center;align-items:flex-end;pointer-events:none;padding:0 8px;z-index:1}'
    + '.pw-subtext{color:#fff;text-align:center;white-space:pre-line;background:rgba(8,12,16,.8);display:none;transform:translateY(-14px)}'
    + '.pw-subtext[data-on="1"]{display:inline-block}'

    + '.pw-menu{position:absolute;right:12px;top:60px;width:min(55%,232px);max-height:55%;overflow-y:auto;'
    + 'background:rgba(0,0,0,.92);border-radius:12px;padding:4px 0;display:none;z-index:6}'
    + '.pw-menu[data-on="1"]{display:block}'
    + '.pw-mrow{display:flex;align-items:center;padding:12px 14px;font-size:14px;color:#fff;cursor:pointer}'
    + '.pw-mrow:hover{background:rgba(255,255,255,.08)}'
    + '.pw-mrow[data-sel="1"]{color:' + CYAN + '}'
    + '.pw-mlabel{flex:1;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}'
    + '.pw-mnote{color:#94A3B8}'
    + '.pw-mval{margin-left:auto;color:rgba(255,255,255,.55);font-size:13px;padding-left:8px;white-space:nowrap}'
    + '.pw-mrow .pw-glyph{width:18px;height:18px;margin-right:12px}'
    + '.pw-mchev{width:16px;height:16px;margin-left:6px;opacity:.6}'
    + '.pw-mcheck{width:16px;height:16px;margin-left:8px;color:' + CYAN + '}'
    + '.pw-mchev svg,.pw-mcheck svg{display:block}'
    + '.pw-mhead{display:flex;align-items:center;padding:12px 10px;font-size:14px;font-weight:500;color:#fff;cursor:pointer}'
    + '.pw-mhead .pw-glyph{width:16px;height:16px;margin-right:6px;transform:rotate(180deg);opacity:.7}';

  // compose sizes stage, on tablet video is one pane of split layout
  var STAGE = null;
  // compose canvas under dom overlays so wheel never reaches it, replay only those with original deltas, browser handles canvas natively, replay doubles scroll
  function composeCanvas() {
    var hosts = document.querySelectorAll('#app div');
    for (var i = 0; i < hosts.length; i++) {
      if (hosts[i].shadowRoot) {
        var c = hosts[i].shadowRoot.querySelector('canvas');
        if (c) return c;
      }
    }
    return document.querySelector('canvas');
  }
  document.addEventListener('wheel', function (e) {
    // only player chrome sits above compose, elsewhere browser delivers wheel to canvas natively and replay doubles scroll
    if (!STAGE || !STAGE.contains(e.target)) return;
    var c = composeCanvas();
    if (!c) return;
    c.dispatchEvent(new WheelEvent('wheel', {
      deltaX: e.deltaX, deltaY: e.deltaY,
      clientX: e.clientX, clientY: e.clientY,
      bubbles: true, cancelable: true, composed: true,
    }));
  }, { passive: true });
  window.pwBounds = function (left, top, width, height) {
    if (!STAGE) return;
    STAGE.style.left = left + 'px';
    STAGE.style.top = top + 'px';
    STAGE.style.width = width + 'px';
    STAGE.style.height = height + 'px';
    if (STAGE.__pwS) applyZoom(STAGE.__pwS);
  };

  // stage sized to picture stream has as phone does, so 2.39:1 film not letterboxed in 16:9 box, zero until first metadata
  window.pwAspect = function () {
    var s = STAGE && STAGE.__pwS;
    if (!s || !s.v || !s.v.videoWidth || !s.v.videoHeight) return 0;
    return s.v.videoWidth / s.v.videoHeight;
  };

  // compose decides whether episodes button belongs on this title and layout, stage may not
  // exist yet so flag is kept and applied when built
  var EPS = false;
  window.pwEpisodes = function (on) {
    EPS = !!on;
    applyEps();
  };

  function applyEps() {
    var b = STAGE && STAGE.querySelector('.pw-eps');
    if (b) b.style.display = EPS ? 'flex' : 'none';
  }

  function el(tag, cls) {
    var e = document.createElement(tag);
    if (cls) e.className = cls;
    return e;
  }

  function glyph(name, cls) {
    var b = el('button', 'pw-ib ' + (cls || ''));
    b.type = 'button';
    b.innerHTML = '<span class="pw-glyph">' + (ICON[name] || '') + '</span>';
    return b;
  }

  function pad(n) { return n < 10 ? '0' + n : '' + n; }

  function clock(ms) {
    var s = Math.max(0, Math.floor(ms / 1000));
    var h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), sec = s % 60;
    return h > 0 ? h + ':' + pad(m) + ':' + pad(sec) : m + ':' + pad(sec);
  }

  function injectCss() {
    if (document.getElementById('pw-player-css')) return;
    var s = el('style');
    s.id = 'pw-player-css';
    s.textContent = CSS;
    document.head.appendChild(s);
  }

  function build(v) {
    injectCss();
    var st = el('div', 'pw-stage');
    STAGE = st;
    // WebPlayer calls pwFullscreen before this element exists, it bails on null stage and data-fs
    // stays off for session, every rule keyed on it misses
    st.dataset.fs = '0';
    st.innerHTML =
      '<div class="pw-scrim pw-fade"></div>'
      + '<div class="pw-zones"><div class="pw-zone" data-z="l"></div>'
      + '<div class="pw-zone" data-z="c"></div><div class="pw-zone" data-z="r"></div></div>'
      + '<div class="pw-subs"><span class="pw-subtext"></span></div>'
      + '<div class="pw-top pw-fade"></div>'
      + '<div class="pw-bottom pw-fade"><div class="pw-seek">'
      + '<div class="pw-scrubtime"></div>'
      + '<div class="pw-trackwrap"><div class="pw-buf"></div><div class="pw-played"></div></div>'
      + '<div class="pw-thumb"></div></div>'
      + '<div class="pw-row"><div class="pw-clock"></div></div></div>'
      + '<div class="pw-slim"><div class="pw-trackwrap"><div class="pw-buf"></div>'
      + '<div class="pw-played"></div></div></div>'
      + '<button class="pw-playbtn pw-fade" type="button"></button>'
      + '<button class="pw-lock pw-fade" type="button"></button>'
      + '<div class="pw-hud" data-side="r"><div class="pw-hud-in"></div></div>'
      + '<svg class="pw-spin" viewBox="0 0 48 48"><circle cx="24" cy="24" r="20" fill="none" '
      + 'stroke="' + CYAN + '" stroke-width="4" stroke-linecap="round" stroke-dasharray="80 200"/></svg>'
      + '<div class="pw-flash"></div><div class="pw-menu"></div>';

    var top = st.querySelector('.pw-top');
    var back = glyph('back', 'pw-back');
    back.setAttribute('aria-label', 'Close player');
    back.addEventListener('click', function () {
      document.dispatchEvent(new Event('pwback'));
    });
    var title = el('div', 'pw-title');
    var cc = glyph('cc_off', 'pw-cc');
    cc.setAttribute('aria-label', 'Subtitles');
    cc.dataset.off = '1';
    cc.disabled = true;
    var gear = glyph('settings', 'pw-gear');
    gear.setAttribute('aria-label', 'Settings');
    top.appendChild(back);
    top.appendChild(title);
    top.appendChild(cc);
    top.appendChild(gear);

    var row = st.querySelector('.pw-row');
    var eps = glyph('episodes', 'pw-eps');
    eps.setAttribute('aria-label', 'Episodes');
    eps.style.display = 'none';
    var mute = glyph('volume_on', 'pw-mute');
    var fs = glyph('fullscreen', 'pw-fs');
    row.appendChild(eps);
    row.appendChild(mute);
    row.appendChild(fs);

    var playBtn = st.querySelector('.pw-playbtn');
    var lockBtn = st.querySelector('.pw-lock');
    lockBtn.innerHTML = '<span class="pw-glyph">' + (ICON.unlock || '') + '</span>';

    var hud = st.querySelector('.pw-hud');
    var hudIn = st.querySelector('.pw-hud-in');
    hudIn.innerHTML = '<span class="pw-hud-icon">' + (ICON.seek_chevron || '') + '</span><span class="pw-hud-n">10</span>';
    hudIn.style.display = 'flex';
    hudIn.style.alignItems = 'center';

    var clockEl = st.querySelector('.pw-clock');
    var scrubTime = st.querySelector('.pw-scrubtime');
    var played = st.querySelector('.pw-bottom .pw-played');
    var buf = st.querySelector('.pw-bottom .pw-buf');
    var slim = st.querySelector('.pw-slim');
    var slimPlayed = st.querySelector('.pw-slim .pw-played');
    var slimBuf = st.querySelector('.pw-slim .pw-buf');
    var thumb = st.querySelector('.pw-thumb');
    var seek = st.querySelector('.pw-seek');
    var flash = st.querySelector('.pw-flash');

    var S = {
      stage: st, v: v, title: title, cc: cc, gear: gear, mute: mute, fs: fs, back: back, eps: eps,
      playBtn: playBtn, lockBtn: lockBtn, clockEl: clockEl, scrubTime: scrubTime,
      played: played, buf: buf, thumb: thumb, seek: seek, flash: flash,
      slim: slim, slimPlayed: slimPlayed, slimBuf: slimBuf,
      hud: hud, hudN: hudIn.querySelector('.pw-hud-n'),
      hudIcon: hudIn.querySelector('.pw-hud-icon'),
      subsWrap: st.querySelector('.pw-subs'), subsEl: st.querySelector('.pw-subtext'),
      menu: st.querySelector('.pw-menu'),
      scrub: null, drag: false, hideTimer: 0, hudTimer: 0, lastTap: 0, lastSide: '',
      accSide: '', accTotal: 0, accAt: 0, raf: 0, flashTimer: 0,
      formats: [], idx: 0, tracks: [], cues: [], subOn: true, subCur: '', subKey: '',
      pendingSeek: null, speed: 1, open: false, page: 0, auto: true, qh: 0,
      mode: 0, pad: 0, safe: false, near: false, zoom: false, cut: { l: 0, r: 0 },
      barsDone: false, zoomInit: false, pinched: 0,
    };
    st.__pwS = S;
    return S;
  }

  function duration(S) {
    var d = S.v.duration;
    return isFinite(d) && d > 0 ? d * 1000 : 0;
  }

  function render(S) {
    renderSubs(S);
    var dur = duration(S);
    var pos = S.v.currentTime * 1000;
    var frac = dur > 0 ? Math.min(1, pos / dur) : 0;
    var shown = S.scrub == null ? frac : Math.min(1, Math.max(0, S.scrub));
    var bufEnd = 0;
    try {
      var b = S.v.buffered;
      for (var i = 0; i < b.length; i++) {
        if (pos >= b.start(i) * 1000 - 1000 && pos <= b.end(i) * 1000 + 1000) { bufEnd = b.end(i) * 1000; break; }
      }
      if (!bufEnd && b.length) bufEnd = b.end(b.length - 1) * 1000;
    } catch (e) {}
    S.played.style.width = (shown * 100) + '%';
    S.buf.style.width = (dur > 0 ? Math.min(1, bufEnd / dur) * 100 : 0) + '%';
    S.slimPlayed.style.width = S.played.style.width;
    S.slimBuf.style.width = S.buf.style.width;
    S.clockEl.textContent = clock(pos) + ' / ' + clock(dur);
    if (S.scrub != null && dur > 0) {
      S.scrubTime.dataset.on = '1';
      S.scrubTime.textContent = clock(S.scrub * dur);
      var w = S.seek.clientWidth;
      var x = Math.round(shown * w);
      S.thumb.dataset.on = '1';
      S.thumb.style.left = (Math.min(Math.max(x, 7), Math.max(w - 7, 7)) - 7) + 'px';
    } else {
      S.scrubTime.dataset.on = '0';
      S.thumb.dataset.on = '0';
    }
  }

  // SubtitleLayer in PlayerVideo.kt: bottom centre of video rect, 5.33% of height, 14 px up, #080C10 at 80%, 0.52em radius
  function subTime(s) {
    var m = /^(?:(\d+):)?(\d{1,2}):(\d{2})[.,](\d{1,3})$/.exec(s.trim());
    if (!m) return -1;
    return ((+(m[1] || 0) * 60 + +m[2]) * 60 + +m[3]) * 1000 + +(m[4] + '00').slice(0, 3);
  }

  function parseCues(text) {
    var out = [];
    var blocks = text.replace(/^\uFEFF/, '').replace(/\r\n?/g, '\n').split(/\n{2,}/);
    for (var i = 0; i < blocks.length; i++) {
      var lines = blocks[i].split('\n'), at = -1;
      for (var j = 0; j < lines.length; j++) if (lines[j].indexOf('-->') > 0) { at = j; break; }
      if (at < 0) continue;
      var span = lines[at].split('-->');
      var start = subTime(span[0]);
      var end = subTime((span[1] || '').trim().split(/\s+/)[0]);
      if (start < 0 || end <= start) continue;
      var body = lines.slice(at + 1).join('\n').replace(/<[^>]*>/g, '')
        .replace(/&nbsp;/g, ' ').replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&amp;/g, '&');
      if (body.trim()) out.push({ s: start, e: end, t: body });
    }
    return out.sort(function (a, b) { return a.s - b.s; });
  }

  function setSub(S, text) {
    if (text === S.subCur) return;
    S.subCur = text;
    S.subsEl.textContent = text;
    S.subsEl.dataset.on = text ? '1' : '0';
  }

  // ---- zoom: PlayerVideo.kt fit/safe/full and ZoomLayer.kt pinch, landscape phone only ----

  var BAR = null, BAR_CTX = null;

  function barFraction(px, w, h) {
    var top = -1, bottom = -1;
    for (var y = 0; y < h; y++) {
      var lit = 0;
      for (var x = 0; x < w; x++) {
        var i = (y * w + x) * 4;
        if ((px[i] + px[i + 1] + px[i + 2]) / 3 > 12) lit++;
      }
      if (lit * 3 >= w) {
        if (top < 0) top = y;
        bottom = y;
      }
    }
    if (top < 0) return null;
    var bars = (top + (h - 1 - bottom)) / h;
    var skew = Math.abs(top - (h - 1 - bottom)) / h;
    return bars >= 0.04 && bars <= 0.4 && skew <= 0.05 ? bars : 0;
  }

  // PixelCopy on phone, canvas here. cross origin media taints canvas and read throws, caller
  // treats that as geometry only
  function frameBars(v) {
    if (!BAR) {
      BAR = document.createElement('canvas');
      BAR.width = 48;
      BAR.height = 48;
      BAR_CTX = BAR.getContext('2d', { willReadFrequently: true });
    }
    BAR_CTX.drawImage(v, 0, 0, 48, 48);
    return barFraction(BAR_CTX.getImageData(0, 0, 48, 48).data, 48, 48);
  }

  // PlayerVideo.kt cutL/cutR: strip fullscreen phone keeps clear of notch
  function cutout() {
    var d = el('div');
    d.style.cssText = 'position:fixed;left:0;top:0;width:0;height:0;visibility:hidden;' +
      'padding-left:env(safe-area-inset-left);padding-right:env(safe-area-inset-right)';
    document.body.appendChild(d);
    var cs = getComputedStyle(d);
    var out = { l: parseFloat(cs.paddingLeft) || 0, r: parseFloat(cs.paddingRight) || 0 };
    document.body.removeChild(d);
    return out;
  }

  function zoomAspect(S) {
    var v = S.v;
    if (!v.videoWidth || !v.videoHeight) return 0;
    // baked in bars are picture user should not see, 2.39 film in 16:9 encode
    return (v.videoWidth / v.videoHeight) / (1 - S.pad);
  }

  // stage box is video box with object-fit:contain picture, mode is scale over contain plus clip for bars, full under notch strip, others inside cutL/cutR
  function applyZoom(S) {
    var v = S.v, r = S.stage.getBoundingClientRect();
    var aspect = zoomAspect(S);
    if (!aspect || r.width <= 0 || r.height <= 0) return;
    var vw = v.videoWidth, vh = v.videoHeight;
    var fs = S.stage.dataset.fs === '1';
    var cut = fs ? S.cut : { l: 0, r: 0 };
    var W = r.width, H = r.height;
    var safeW = Math.max(W - cut.l - cut.r, W * 0.5);
    S.safe = safeW / aspect >= H;
    S.near = !S.safe && safeW / aspect >= H * 0.9;
    var picH = S.mode === 0 ? Math.min(safeW / aspect, H)
      : Math.max(H, (S.mode === 1 ? safeW : W) / aspect);
    var f = Math.min(W / vw, H / vh);
    var t = picH / (vh * (1 - S.pad)) / f;
    // picture centred on box, notch strip makes safe area off centre
    var dx = S.mode === 2 ? 0 : (cut.l - cut.r) / 2;
    v.style.transformOrigin = '50% 50%';
    v.style.transform = t > 1.001 || dx
      ? 'translate(' + dx + 'px,0px) scale(' + t + ')' : '';
    var x0 = (W - vw * f) / 2, y0 = (H - vh * f) / 2, bar = S.pad * vh * f / 2;
    var kx = fs ? 0 : (W - W / t) / 2, ky = (H - H / t) / 2;
    var top = Math.max(0, y0 + bar, ky);
    var bot = Math.max(0, H - Math.min(y0 + vh * f - bar, H - ky));
    var left = Math.max(0, x0, kx);
    var right = Math.max(0, W - Math.min(x0 + vw * f, W - kx));
    v.style.clipPath = (t > 1.001 || S.pad > 0) && (top || bot || left || right)
      ? 'inset(' + top + 'px ' + right + 'px ' + bot + 'px ' + left + 'px)' : '';
    S.subKey = '';
  }

  // PlayerScreen.kt: picture that nearly fills stage opens filled, 4% bar needs no pinch
  function autoFill(S) {
    if (!S.zoom || S.zoomInit || !S.v.videoWidth) return;
    S.zoomInit = true;
    if (S.near) {
      S.mode = 2;
      applyZoom(S);
    }
  }

  // smallest side under 600dp is rule app uses for zoomable
  function updateZoom(S) {
    var was = S.zoom;
    S.cut = cutout();
    S.zoom = window.innerWidth > window.innerHeight && Math.min(window.innerWidth, window.innerHeight) < 600;
    S.stage.dataset.zoom = S.zoom ? '1' : '0';
    if (S.zoom !== was) {
      S.mode = 0;
      S.zoomInit = false;
    }
    applyZoom(S);
    autoFill(S);
  }

  // PlayerVideo.kt samples five times and keeps smallest honest reading, one frame lies,
  // a fade or dark shot reads as bars
  function startBars(S) {
    if (S.barsDone) return;
    S.barsDone = true;
    var seen = [], tries = 0;
    function sample() {
      if (!S.v.__pw) return;
      if (++tries > 100) {
        log('zoom', 'pad none');
        return;
      }
      var b;
      try {
        b = frameBars(S.v);
      } catch (e) {
        log('zoom', 'pad unreadable ' + (e && e.name));
        return;
      }
      if (b == null) {
        setTimeout(sample, 400);
        return;
      }
      seen.push(b);
      var lit = seen.filter(function (x) { return x > 0; });
      S.pad = lit.length ? Math.min.apply(null, lit) : 0;
      applyZoom(S);
      if (seen.length < 5) {
        setTimeout(sample, 400);
        return;
      }
      log('zoom', 'pad ' + Math.round(S.pad * 100) + '%');
    }
    setTimeout(sample, 400);
  }

  // ZoomLayer.kt: fit -> safe -> full on way in, back on way out
  function stepMode(S, acc) {
    var m = S.mode;
    var next = acc > 1
      ? (m === 0 && S.safe ? 1 : 2)
      : (m === 2 && S.safe ? 1 : 0);
    if (next === m) return;
    S.mode = next;
    log('zoom', next === 0 ? 'fit' : next === 1 ? 'safe' : 'full');
    applyZoom(S);
  }

  function pinch(S) {
    var pts = {}, base = 0, acc = 1, done = false;
    function dist() {
      var id = Object.keys(pts);
      if (id.length < 2) return 0;
      var a = pts[id[0]], b = pts[id[1]];
      return Math.sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y));
    }
    S.stage.addEventListener('pointerdown', function (e) {
      if (!S.zoom || S.stage.dataset.lock === '1') return;
      pts[e.pointerId] = { x: e.clientX, y: e.clientY };
      if (Object.keys(pts).length === 2) {
        base = dist();
        acc = 1;
        done = false;
      }
    });
    S.stage.addEventListener('pointermove', function (e) {
      var p = pts[e.pointerId];
      if (!p) return;
      p.x = e.clientX;
      p.y = e.clientY;
      var d = dist();
      if (!base || !d) return;
      if (done) {
        base = d;
        return;
      }
      acc *= d / base;
      base = d;
      if (acc > 1.06 || acc < 0.94) {
        done = true;
        S.pinched = Date.now();
        stepMode(S, acc);
      }
    });
    function up(e) {
      delete pts[e.pointerId];
      if (Object.keys(pts).length < 2) {
        base = 0;
        acc = 1;
        done = false;
      }
    }
    S.stage.addEventListener('pointerup', up);
    S.stage.addEventListener('pointercancel', up);
  }

  // <video> box is not picture, object-fit:contain letterboxes it, portrait phone gives 360x800 box around 360x202 frame, size and anchor to picture
  function pictureRect(S) {
    var r = S.v.getBoundingClientRect();
    var vw = S.v.videoWidth, vh = S.v.videoHeight;
    if (!vw || !vh || !r.width || !r.height) return r;
    var k = Math.min(r.width / vw, r.height / vh);
    var w = vw * k, h = vh * k;
    // baked in rows are not picture, caption anchored under them lands on clip
    var bars = S.pad || 0;
    return {
      left: r.left + (r.width - w) / 2,
      top: r.top + (r.height - h) / 2 + bars * h / 2,
      width: w,
      height: h * (1 - bars),
    };
  }

  function renderSubs(S) {
    if (!S.subOn || !S.cues.length) { setSub(S, ''); return; }
    var r = pictureRect(S);
    if (r.height <= 0) { setSub(S, ''); return; }
    var key = Math.round(r.left) + ',' + Math.round(r.top) + ',' + Math.round(r.width) + ',' + Math.round(r.height);
    if (key !== S.subKey) {
      S.subKey = key;
      // app sizes captions off min(shortSide / aspect, drawn height), not drawn height alone
      // (PlayerScreen.kt:1242), so wide window does not inflate them. fraction 0.0533, floored
      // at 13dp, 15dp in fullscreen
      var aspect = r.width / r.height;
      var shortSide = Math.min(window.innerWidth, window.innerHeight);
      var basis = Math.min(shortSide / aspect, r.height);
      var px = Math.max(basis * 0.0533, STAGE && STAGE.dataset.fs === '1' ? 15 : 13);
      S.subsWrap.style.left = r.left + 'px';
      S.subsWrap.style.top = r.top + 'px';
      S.subsWrap.style.width = r.width + 'px';
      S.subsWrap.style.height = r.height + 'px';
      S.subsEl.style.fontSize = px + 'px';
      S.subsEl.style.lineHeight = (px * 1.2) + 'px';
      S.subsEl.style.borderRadius = (px * 0.52) + 'px';
      S.subsEl.style.padding = (px * 0.33) + 'px ' + (px * 0.59) + 'px';
    }
    var t = S.v.currentTime * 1000, cues = S.cues;
    var lo = 0, hi = cues.length;
    while (lo < hi) {
      var mid = (lo + hi) >> 1;
      if (cues[mid].e <= t) lo = mid + 1; else hi = mid;
    }
    var parts = [];
    for (var i = lo; i < cues.length && cues[i].s <= t; i++) parts.push(cues[i].t);
    setSub(S, parts.join('\n'));
  }

  function subIcon(S) {
    var on = S.subOn && S.cues.length > 0;
    S.cc.innerHTML = '<span class="pw-glyph">' + (on ? ICON.cc_on : ICON.cc_off) + '</span>';
    S.cc.dataset.on = on ? '1' : '0';
    S.cc.setAttribute('aria-label', on ? 'Turn subtitles off' : 'Turn subtitles on');
  }

  // first track that parses wins. providers ship one track per language and media3 selector
  // defaults to english, so english goes first, rest stay as fallback
  function loadSubs(S) {
    S.cues = [];
    S.subCur = '';
    S.cc.disabled = true;
    S.cc.dataset.off = '1';
    subIcon(S);
    var list = S.tracks.slice().sort(function (a, b) {
      return (/english/i.test(b.l || '') ? 1 : 0) - (/english/i.test(a.l || '') ? 1 : 0);
    });
    var i = 0;
    if (!list.length) log('subs', 'none offered');
    function tryNext() {
      if (i >= list.length || !S.v.__pw) return;
      var track = list[i++];
      fetch(track.u, { credentials: 'omit' })
        .then(function (r) { if (!r.ok) throw new Error('http ' + r.status); return r.text(); })
        .then(function (txt) {
          var cues = parseCues(txt);
          if (!cues.length) throw new Error('no cues');
          if (!S.v.__pw) return;
          log('subs', 'loaded ' + cues.length + ' cues (' + (track.l || '?') + ')');
          S.cues = cues;
          S.cc.disabled = false;
          S.cc.dataset.off = '0';
          subIcon(S);
          render(S);
        })
        .catch(function (e) {
          log('subs', 'fail ' + (track.l || '?') + ': ' + (e && e.message));
          tryNext();
        });
    }
    tryNext();
  }

  function tick(S) {
    render(S);
    if (!S.v.paused && !S.v.ended) S.raf = requestAnimationFrame(function () { tick(S); });
    else S.raf = 0;
  }

  function startLoop(S) {
    if (S.raf) return;
    S.raf = requestAnimationFrame(function () { tick(S); });
  }

  function poke(S) {
    S.stage.dataset.on = '1';
    clearTimeout(S.hideTimer);
    S.hideTimer = setTimeout(function () { S.stage.dataset.on = '0'; }, 3000);
  }

  function hide(S) {
    dismissMenu(S);
    S.stage.dataset.on = '0';
    clearTimeout(S.hideTimer);
  }

  function seekTo(S, f) {
    var dur = duration(S);
    if (dur > 0) S.v.currentTime = f * dur / 1000;
    poke(S);
  }

  function showHud(S, side, seconds) {
    S.hud.dataset.side = side === 'l' ? 'l' : 'r';
    S.hudN.textContent = seconds;
    S.hud.dataset.on = '1';
    S.hud.dataset.nudge = '0';
    void S.hud.offsetWidth;
    S.hud.dataset.nudge = '1';
    clearTimeout(S.hudTimer);
    S.hudTimer = setTimeout(function () { S.hud.dataset.on = '0'; }, 1000);
  }

  function accSeek(S, side) {
    var now = Date.now();
    S.accTotal = S.accSide === side && now - S.accAt < 1000 ? S.accTotal + 10 : 10;
    S.accSide = side;
    S.accAt = now;
    var d = side === 'l' ? -10 : 10;
    S.v.currentTime = Math.max(0, S.v.currentTime + d);
    showHud(S, side, S.accTotal);
  }

  function setPlayIcon(S) {
    var playing = !S.v.paused && !S.v.ended;
    S.playBtn.dataset.paused = playing ? '0' : '1';
    S.playBtn.innerHTML = '<span class="pw-glyph">' + (playing ? ICON.pause : ICON.play) + '</span>';
    S.playBtn.setAttribute('aria-label', playing ? 'Pause' : 'Play');
  }

  function togglePlay(S) {
    if (S.v.paused || S.v.ended) S.v.play().catch(function () {});
    else S.v.pause();
    poke(S);
  }

  function zone(S, z) {
    if (S.pinched && Date.now() - S.pinched < 400) return;
    if (S.stage.dataset.lock === '1') { poke(S); return; }
    if (S.open) { closeMenu(S); return; }
    var now = Date.now();
    var prev = S.lastSide;
    var prevAt = S.lastTap;
    S.lastTap = now;
    S.lastSide = z;
    if (z === 'c') {
      if (prev === 'c' && now - prevAt < 320) togglePlay(S);
      else if (S.stage.dataset.on === '1') hide(S);
      else poke(S);
      return;
    }
    if (prev === z && now - prevAt < 320) {
      accSeek(S, z);
      S.lastTap = 0;
      return;
    }
    setTimeout(function () {
      if (S.lastTap === now) {
        if (S.stage.dataset.on === '1') hide(S);
        else poke(S);
      }
    }, 330);
  }

  // browser only paints fullscreen subtree and <video> is body child, so whole document goes fullscreen, compose then drops to stage only layout
  var INAPP = false;
  function setFullscreen() {
    if (INAPP) { INAPP = false; document.dispatchEvent(new Event('pwfs')); return; }
    if (document.fullscreenElement) { document.exitFullscreen().catch(function () {}); return; }
    var el = document.documentElement;
    if (!el.requestFullscreen) { INAPP = true; document.dispatchEvent(new Event('pwfs')); return; }
    try {
      var p = el.requestFullscreen();
      if (p && p.catch) p.catch(function () { INAPP = true; document.dispatchEvent(new Event('pwfs')); });
    } catch (e) {
      INAPP = true;
      document.dispatchEvent(new Event('pwfs'));
    }
  }
  window.pwFsState = function () { return INAPP || !!document.fullscreenElement; };
  document.addEventListener('fullscreenchange', function () {
    if (!document.fullscreenElement) INAPP = false;
    document.dispatchEvent(new Event('pwfs'));
  });

  // app opens fullscreen as landscape on phone (SCREEN_ORIENTATION_SENSOR_LANDSCAPE in
  // PlayerScreen.kt), browser only allows same through lock api and only inside fullscreen element
  function orient(on) {
    try {
      if (!screen.orientation || !screen.orientation.lock) return;
      if (!on) {
        screen.orientation.unlock();
        return;
      }
      var p = screen.orientation.lock('landscape');
      if (p && p.catch) p.catch(function () {});
    } catch (e) {}
  }

  // WebPlayer.kt calls this after flipping its own fullscreen flag, video takes whole window so
  // chrome only redraws its icons and unlocks on way out
  window.pwFullscreen = function (on) {
    if (!STAGE) return;
    if (!on && (document.fullscreenElement || INAPP)) {
      INAPP = false;
      if (document.fullscreenElement) document.exitFullscreen().catch(function () {});
    }
    orient(on);
    STAGE.dataset.fs = on ? '1' : '0';
    if (!on) STAGE.dataset.lock = '0';
    var fsBtn = STAGE.querySelector('.pw-fs');
    var backBtn = STAGE.querySelector('.pw-back');
    if (fsBtn) {
      fsBtn.innerHTML = '<span class="pw-glyph">' + (on ? ICON.fullscreen_exit : ICON.fullscreen) + '</span>';
      fsBtn.setAttribute('aria-label', on ? 'Exit fullscreen' : 'Enter fullscreen');
    }
    if (backBtn) {
      backBtn.innerHTML = '<span class="pw-glyph">' + (on ? ICON.chevron_down : ICON.back) + '</span>';
      backBtn.setAttribute('aria-label', on ? 'Exit fullscreen' : 'Close player');
    }
    // caption size is keyed to picture rect, force remeasure
    if (STAGE.__pwS) updateZoom(STAGE.__pwS);
  };

  function wire(S) {
    var v = S.v;
    pinch(S);
    S.stage.querySelectorAll('.pw-zone').forEach(function (z) {
      z.addEventListener('click', function () { zone(S, z.dataset.z); });
    });

    S.playBtn.addEventListener('click', function (e) { e.stopPropagation(); togglePlay(S); });

    S.cc.addEventListener('click', function (e) {
      e.stopPropagation();
      if (S.cc.disabled) return;
      S.subOn = !S.subOn;
      subIcon(S);
      renderSubs(S);
      poke(S);
    });

    S.gear.addEventListener('click', function (e) {
      e.stopPropagation();
      if (S.open) closeMenu(S);
      else openMenu(S);
    });

    S.menu.addEventListener('click', function (e) { e.stopPropagation(); });

    S.mute.addEventListener('click', function (e) {
      e.stopPropagation();
      v.muted = !v.muted;
      poke(S);
    });

    S.fs.addEventListener('click', function (e) {
      e.stopPropagation();
      setFullscreen();
      poke(S);
    });

    S.lockBtn.addEventListener('click', function (e) {
      e.stopPropagation();
      var locked = S.stage.dataset.lock === '1';
      S.stage.dataset.lock = locked ? '0' : '1';
      S.lockBtn.innerHTML = '<span class="pw-glyph">' + (locked ? ICON.unlock : ICON.lock) + '</span>';
      S.lockBtn.setAttribute('aria-label', locked ? 'Unlock controls' : 'Lock controls');
      if (locked) poke(S); else hide(S);
    });

    S.eps.addEventListener('click', function (e) {
      e.stopPropagation();
      document.dispatchEvent(new Event('pweplist'));
    });

    bindSeek(S, S.seek);
    bindSeek(S, S.slim);

    v.addEventListener('play', function () { setPlayIcon(S); startLoop(S); });
    v.addEventListener('pause', function () { setPlayIcon(S); render(S); });
    v.addEventListener('ended', function () { setPlayIcon(S); render(S); });
    v.addEventListener('loadedmetadata', function () {
      if (S.pendingSeek != null) {
        try { v.currentTime = S.pendingSeek; } catch (e) {}
        S.pendingSeek = null;
      }
      updateZoom(S);
      render(S);
    });
    v.addEventListener('loadeddata', function () { startBars(S); });
    v.addEventListener('loadedmetadata', function () {
      if (S.restored) return;
      S.restored = true;
      seekStart(S);
    });
    v.addEventListener('timeupdate', function () {
      var dur = duration(S);
      if (!S.drag && S.scrub != null && dur > 0 && Math.abs(v.currentTime * 1000 - S.scrub * dur) < 1500) {
        S.scrub = null;
      }
      if (S.raf === 0) render(S);
    });
    v.addEventListener('progress', function () { render(S); });
    v.addEventListener('durationchange', function () { render(S); });
    v.addEventListener('volumechange', function () {
      S.mute.innerHTML = '<span class="pw-glyph">' + (v.muted ? ICON.volume_off : ICON.volume_on) + '</span>';
      if (!v.muted) flash(S, '');
    });
    v.addEventListener('waiting', function () { S.stage.dataset.stall = '1'; });
    v.addEventListener('playing', function () {
      S.stage.dataset.stall = '0';
      if (S.loggedAt !== S.idx) {
        S.loggedAt = S.idx;
        log('play', 'player ready ' + (S.formats[S.idx] && S.formats[S.idx].l || '?') +
          ' ' + v.videoWidth + 'x' + v.videoHeight);
      }
      startLoop(S);
    });
    v.addEventListener('canplay', function () { S.stage.dataset.stall = '0'; });
    v.addEventListener('error', function () { S.stage.dataset.stall = '0'; });

    S.onKey = onKey(S);
    S.onResize = function () { updateZoom(S); render(S); };
    window.addEventListener('keydown', S.onKey);
    window.addEventListener('resize', S.onResize);
    applyEps();
  }

  // two bars, one gesture, chrome bar and slim bar on picture. whichever took pointer keeps it,
  // scrub measured against its own box
  function bindSeek(S, el) {
    el.addEventListener('pointerdown', function (e) {
      if (duration(S) <= 0) return;
      S.drag = true;
      S.dragEl = el;
      try { el.setPointerCapture(e.pointerId); } catch (err) {}
      setScrub(S, e, el);
      // slim bar does not wake chrome, hides bar under finger mid drag
      if (el !== S.slim) poke(S);
    });
    el.addEventListener('pointermove', function (e) {
      if (S.drag) setScrub(S, e, S.dragEl);
    });
    el.addEventListener('pointerup', function (e) {
      if (!S.drag) return;
      S.drag = false;
      setScrub(S, e, S.dragEl);
      seekTo(S, S.scrub);
    });
    el.addEventListener('pointercancel', function () {
      if (!S.drag) return;
      S.drag = false;
      S.scrub = null;
      render(S);
    });
  }

  function setScrub(S, e, el) {
    var r = (el || S.seek).getBoundingClientRect();
    if (r.width <= 0) return;
    S.scrub = Math.min(1, Math.max(0, (e.clientX - r.left) / r.width));
    render(S);
  }

  function onKey(S) {
    return function (e) {
      if (e.target && /^(INPUT|TEXTAREA|SELECT)$/.test(e.target.tagName)) return;
      var k = e.key;
      if (S.open) {
        if (k === 'Escape') { e.preventDefault(); closeMenu(S); }
        return;
      }
      if (k === ' ' || k === 'Enter') { e.preventDefault(); togglePlay(S); return; }
      if (k === 'ArrowLeft') { e.preventDefault(); accSeek(S, 'l'); poke(S); return; }
      if (k === 'ArrowRight') { e.preventDefault(); accSeek(S, 'r'); poke(S); return; }
      if (k === 'm' || k === 'M') { S.v.muted = !S.v.muted; poke(S); return; }
      if (k === 'f' || k === 'F') { setFullscreen(); poke(S); }
    };
  }

  function flash(S, text, tap) {
    S.flash.textContent = text;
    S.flash.dataset.on = text ? '1' : '0';
    S.flash.dataset.tap = tap ? '1' : '0';
  }

  // ---- settings menu: PlayerSettings.kt, minus dub page (proxy resolves one cut) ----

  var SPEEDS = [0.5, 0.75, 1, 1.25, 1.5, 1.75, 2];

  function speedLabel(x) {
    return (x === Math.floor(x) ? Math.floor(x) : x) + '×';
  }

  // labels are "<leg>-1080p" / "<leg>-hd-720p" / "<leg>-auto"
  function heightOf(label) {
    var m = /(\d{3,4})p/.exec(label || '');
    return m ? +m[1] : 0;
  }

  function qualities(S) {
    var seen = {}, out = [];
    for (var i = 0; i < S.formats.length; i++) {
      var h = heightOf(S.formats[i].l);
      if (h > 0 && !seen[h]) { seen[h] = 1; out.push(h); }
    }
    return out.sort(function (a, b) { return b - a; });
  }

  function menuRow(label, value, icon, chevron, selected, note, onClick) {
    var r = el('div', 'pw-mrow');
    if (icon) r.innerHTML = '<span class="pw-glyph">' + (ICON[icon] || '') + '</span>';
    var t = el('span', 'pw-mlabel');
    t.textContent = label;
    if (note) {
      var n = el('span', 'pw-mnote');
      n.textContent = '  ' + note;
      t.appendChild(n);
    }
    r.appendChild(t);
    if (value) {
      var v = el('span', 'pw-mval');
      v.textContent = value;
      r.appendChild(v);
    }
    if (chevron) {
      var c = el('span', 'pw-mchev');
      c.innerHTML = ICON.chevron_right || '';
      r.appendChild(c);
    }
    if (selected) {
      var k = el('span', 'pw-mcheck');
      k.innerHTML = ICON.check || '';
      r.appendChild(k);
      r.dataset.sel = '1';
    }
    r.addEventListener('click', function (e) { e.stopPropagation(); onClick(); });
    return r;
  }

  function menuHead(S, title) {
    var h = el('div', 'pw-mhead');
    h.innerHTML = '<span class="pw-glyph">' + (ICON.chevron_right || '') + '</span>';
    h.appendChild(document.createTextNode(title));
    h.addEventListener('click', function (e) { e.stopPropagation(); S.page = 0; menuRender(S); });
    return h;
  }

  function menuRender(S) {
    var m = S.menu;
    m.innerHTML = '';
    if (S.page === 1) {
      m.appendChild(menuHead(S, 'Quality'));
      m.appendChild(menuRow('Auto', S.auto && S.qh ? S.qh + 'p' : null, null, false, S.auto, null,
        function () { pickQuality(S, 0); }));
      qualities(S).forEach(function (h) {
        m.appendChild(menuRow(h + 'p', null, null, false, !S.auto && S.qh === h, null,
          function () { pickQuality(S, h); }));
      });
    } else if (S.page === 2) {
      m.appendChild(menuHead(S, 'Speed'));
      SPEEDS.forEach(function (x) {
        m.appendChild(menuRow(speedLabel(x), null, null, false, x === S.speed, x === 1 ? 'Normal' : null,
          function () { pickSpeed(S, x); }));
      });
    } else {
      var q = qualities(S);
      if (q.length > 1) {
        // android hides auto resolution on root row (phone layout), it only shows inside quality
        // list, which keeps row short enough never to ellipsize
        var qv = S.auto ? 'Auto' : S.qh + 'p';
        m.appendChild(menuRow('Quality', qv, 'quality', true, false, null,
          function () { S.page = 1; menuRender(S); }));
      }
      m.appendChild(menuRow('Speed', speedLabel(S.speed), 'speed', true, false, null,
        function () { S.page = 2; menuRender(S); }));
    }
  }

  function openMenu(S) {
    S.open = true;
    S.page = 0;
    S.menu.dataset.on = '1';
    S.gear.dataset.on = '1';
    S.stage.dataset.on = '1';
    clearTimeout(S.hideTimer);
    menuRender(S);
  }

  function closeMenu(S) {
    if (!S.open) return;
    dismissMenu(S);
    poke(S);
  }

  function dismissMenu(S) {
    if (!S.open) return;
    S.open = false;
    S.page = 0;
    S.menu.dataset.on = '0';
    S.gear.dataset.on = '0';
  }

  // switching rung reloads source, so playhead is kept and loadedmetadata restores it
  function pickQuality(S, h) {
    var idx = 0, qh = h;
    if (h > 0) {
      for (var i = 0; i < S.formats.length; i++) {
        if (heightOf(S.formats[i].l) === h) { idx = i; break; }
      }
    } else {
      qh = heightOf(S.formats[0].l);
    }
    S.auto = h <= 0;
    S.qh = qh;
    closeMenu(S);
    if (idx === S.idx) return;
    S.pendingSeek = S.v.currentTime;
    load(S, S.formats[idx], idx);
  }

  function pickSpeed(S, x) {
    S.speed = x;
    S.v.playbackRate = x;
    closeMenu(S);
    flash(S, 'Speed ' + speedLabel(x));
    clearTimeout(S.flashTimer);
    S.flashTimer = setTimeout(function () { flash(S, ''); }, 1200);
  }

  // autoplay: play() runs seconds after tap that started resolve, transient activation is gone
  // and unmuted play() is rejected. muted always allowed
  function start(S) {
    var v = S.v;
    var p = v.play();
    if (!p || !p.catch) return;
    p.catch(function (err) {
      if (err && err.name === 'AbortError') return;
      if (!v.muted) {
        log('play', 'autoplay blocked (' + (err && err.name) + '), retrying muted');
        v.muted = true;
        var q = v.play();
        if (q && q.catch) q.catch(function () {});
        flash(S, 'Tap for sound', true);
      }
    });
  }

  function load(S, item, idx) {
    var v = S.v;
    S.idx = idx == null ? 0 : idx;
    log('play', 'direct play ' + (item.l || '?') + ' height=' + (item.h || 0) +
      ' hls=' + (item.h ? 'true' : 'false') + ' #' + S.idx + '/' + S.formats.length);
    v.__pw.list = S.formats;
    v.__pw.i = S.idx;
    S.stage.dataset.stall = '1';
    if (v.__pwHls) { v.__pwHls.destroy(); v.__pwHls = null; }
    v.onerror = null;
    v.removeAttribute('src');
    if (item.h && window.Hls && window.Hls.isSupported()) {
      var h = new window.Hls({ enableWorker: true });
      h.on(window.Hls.Events.ERROR, function (_, d) {
        if (!d.fatal) return;
        var code = d.response && d.response.code ? ' http=' + d.response.code : '';
        log('play', 'player error ' + (d.details || d.type || '?') + code + ' #' + S.idx);
        h.destroy();
        v.__pwHls = null;
        next(S);
      });
      h.loadSource(item.u);
      h.attachMedia(v);
      v.__pwHls = h;
    } else {
      v.onerror = function () {
        log('play', 'player error code=' + (v.error && v.error.code) + ' #' + S.idx);
        next(S);
      };
      v.src = item.u;
    }
    start(S);
  }

  function next(S) {
    var st = S.v.__pw;
    if (!st) return;
    if (++st.i < st.list.length) {
      log('play', 'relink #' + st.i + ' ' + (st.list[st.i].l || '?') + ' at ' + Math.floor(S.v.currentTime) + 's');
      load(S, st.list[st.i], st.i);
    } else {
      log('play', 'player error no source left after ' + st.list.length);
      S.stage.dataset.stall = '0';
      // PlayerScreen flips netErr on same event and offers way out (PlayerFailedOverlay)
      document.dispatchEvent(new Event('pwfail'));
    }
  }

  // builds stage around v and puts it on page, resolving screen and direct attach both start
  // here, caller decides whether source follows
  function mount(v) {
    var S = build(v);
    wire(S);
    v.__pw = { S: S, list: [], i: 0 };
    document.body.appendChild(S.stage);
    S.stage.insertBefore(v, S.stage.firstChild);
    v.style.zIndex = '0';
    updateZoom(S);
    S.fs.innerHTML = '<span class="pw-glyph">' + ICON.fullscreen + '</span>';
    S.mute.innerHTML = '<span class="pw-glyph">' + (v.muted ? ICON.volume_off : ICON.volume_on) + '</span>';
    S.lockBtn.setAttribute('aria-label', 'Lock controls');
    S.title.textContent = v.__pwTitle || '';
    S.flash.addEventListener('click', function () {
      if (S.flash.dataset.tap !== '1') return;
      v.muted = false;
      flash(S, '');
    });
    return S;
  }

  // phone opens player before resolver answers and draws chrome stalled, so stream landing never
  // swaps layout. same here: stage goes up empty and pwAttach fills it later
  window.pwStall = function (v, title) {
    var S = v.__pw ? v.__pw.S : mount(v);
    // PlayerResolving passes canSubs/canSettings false, nothing to pick yet so both read dimmed rather than empty menu
    S.cc.disabled = true;
    S.cc.dataset.off = '1';
    S.gear.disabled = true;
    S.gear.dataset.off = '1';
    S.title.textContent = title || '';
    S.stage.dataset.stall = '1';
    S.stage.dataset.on = '1';
    clearTimeout(S.hideTimer);
    render(S);
  };

  window.pwAttach = function (v, sourcesJson) {
    var doc;
    try { doc = JSON.parse(sourcesJson); } catch (e) { return; }
    var list = (doc && doc.src) || [];
    if (!list.length) return;

    var S = v.__pw ? v.__pw.S : mount(v);
    // outside branch, pwStall may have built stage already, its chrome still has to start auto hiding now something to watch
    poke(S);
    v.__pw.list = list;
    v.__pw.i = 0;
    S.gear.disabled = false;
    S.gear.dataset.off = '0';
    S.formats = list;
    S.tracks = (doc && doc.subs) || [];
    S.qh = heightOf(list[0].l);
    S.auto = true;
    S.subKey = '';
    S.scrub = null;
    // new title starts fitted, own bars measured again
    S.mode = 0;
    S.pad = 0;
    S.barsDone = false;
    S.zoomInit = false;
    S.pinched = 0;
    // fresh title seeks to own saved position once, not on every rung switch
    S.restored = false;
    S.startAt = (doc && doc.start) || 0;
    // element still carries last title size, so only undo zoom here, loadedmetadata measures
    applyZoom(S);
    setPlayIcon(S);
    load(S, list[0], 0);
    loadSubs(S);
  };

  window.pwDetach = function (v) {
    var st = v.__pw;
    if (!st) return;
    v.__pw = null;
    var S = st.S;
    if (v.__pwHls) { v.__pwHls.destroy(); v.__pwHls = null; }
    if (S.raf) cancelAnimationFrame(S.raf);
    clearTimeout(S.hideTimer);
    clearTimeout(S.hudTimer);
    clearTimeout(S.flashTimer);
    S.open = false;
    S.menu.dataset.on = '0';
    window.removeEventListener('keydown', S.onKey);
    window.removeEventListener('resize', S.onResize);
    v.onerror = null;
    v.removeAttribute('src');
    try { v.load(); } catch (e) {}
    if (S.stage.parentNode) S.stage.parentNode.removeChild(S.stage);
  };

  // title effect can run before stage exists, so kept on element
  window.pwTitle = function (v, text) {
    if (!v) return;
    v.__pwTitle = text || '';
    if (v.__pw) v.__pw.S.title.textContent = v.__pwTitle;
  };

  window.pwPlayerMounted = function () { return !!document.querySelector('.pw-stage'); };
})();
