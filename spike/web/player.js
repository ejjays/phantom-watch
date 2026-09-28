// Player chrome for the web build. The phone draws this in Compose over a SurfaceView; a browser
// gets a <video> element, so the same layout is rebuilt in DOM/CSS from player/PlayerStage.kt.
// Sizes are dp == CSS px, matching the Compose density of 1 on the rest of the web UI.
(function () {
  'use strict';

  var CYAN = '#22D3EE';
  var SLATE200 = '#E2E8F0';
  var ICON = window.PW_ICONS || {};
  var EASE = 'cubic-bezier(.4,0,.2,1)';

  var CSS = ''
    + '@font-face{font-family:PWRubik;src:url(./composeResources/dev.phantomwatch.res/font/rubik_regular.ttf) format("truetype");font-weight:400}'
    + '@font-face{font-family:PWRubik;src:url(./composeResources/dev.phantomwatch.res/font/rubik_medium.ttf) format("truetype");font-weight:500}'
    + '@font-face{font-family:PWRubik;src:url(./composeResources/dev.phantomwatch.res/font/rubik_semibold.ttf) format("truetype");font-weight:600}'
    + '@font-face{font-family:PWRubik;src:url(./composeResources/dev.phantomwatch.res/font/rubik_bold.ttf) format("truetype");font-weight:700}'
    + '@font-face{font-family:PWMono;src:url(./composeResources/dev.phantomwatch.res/font/ibmplexmono_regular.ttf) format("truetype");font-weight:400}'
    + '@font-face{font-family:PWMono;src:url(./composeResources/dev.phantomwatch.res/font/ibmplexmono_semibold.ttf) format("truetype");font-weight:600}'

    + '.pw-stage{position:fixed;inset:0;z-index:2;font-family:PWRubik,system-ui,sans-serif;'
    + 'color:#fff;-webkit-user-select:none;user-select:none;-webkit-tap-highlight-color:transparent;touch-action:manipulation}'
    + '.pw-stage *{box-sizing:border-box;margin:0;padding:0}'
    + '.pw-zones{position:absolute;inset:0;display:flex}'
    + '.pw-zone{flex:1;height:100%}'
    + '.pw-fade{opacity:0;visibility:hidden;transition:opacity .25s ' + EASE + ',visibility 0s .25s}'
    + '.pw-stage[data-on="1"] .pw-fade{opacity:1;visibility:visible;transition:opacity .25s ' + EASE + ',visibility 0s}'

    // scrim starts under the header, exactly like the Compose offset
    + '.pw-scrim{position:absolute;left:0;right:0;bottom:0;top:var(--hh,56px);background:rgba(0,0,0,.4)}'

    + '.pw-top{position:absolute;left:0;right:0;top:0;padding:8px 8px 0 0;display:flex;align-items:center;min-height:56px;z-index:2}'
    + '.pw-ib{background:none;border:0;color:#fff;display:flex;align-items:center;justify-content:center;cursor:pointer;flex:0 0 auto}'
    + '.pw-ib:focus{outline:none}'
    + '.pw-ib:focus-visible{outline:2px solid ' + CYAN + ';outline-offset:-2px;border-radius:50%}'
    + '.pw-back{width:48px;height:48px;margin-left:4px}'
    + '.pw-title{flex:1;min-width:0;font-size:15px;font-weight:600;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}'
    + '.pw-top .pw-ib{width:48px;height:48px}'
    + '.pw-cc[data-off="1"]{opacity:.4}'
    + '.pw-cc[data-on="1"]{color:' + CYAN + '}'
    + '.pw-gear[data-on="1"]{color:' + CYAN + '}'
    + '.pw-glyph{width:20px;height:20px;display:block}'

    + '.pw-bottom{position:absolute;left:0;right:0;bottom:0;padding:0 16px 8px;display:flex;flex-direction:column;z-index:2}'
    + '.pw-seek{position:relative;height:24px;touch-action:none;cursor:pointer}'
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
    + '.pw-stage[data-stall="1"] .pw-playbtn,.pw-stage[data-stall="1"] .pw-hud{display:none}'

    + '.pw-lock{position:absolute;left:8px;top:50%;width:44px;height:44px;margin-top:-22px;border:0;background:none;'
    + 'color:#fff;display:none;align-items:center;justify-content:center;cursor:pointer}'
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
      if (document.fullscreenElement) document.exitFullscreen();
      else document.dispatchEvent(new Event('pwback'));
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
    var mute = glyph('volume_on', 'pw-mute');
    var fs = glyph('fullscreen', 'pw-fs');
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
    var played = st.querySelector('.pw-played');
    var buf = st.querySelector('.pw-buf');
    var thumb = st.querySelector('.pw-thumb');
    var seek = st.querySelector('.pw-seek');
    var flash = st.querySelector('.pw-flash');

    var S = {
      stage: st, v: v, title: title, cc: cc, gear: gear, mute: mute, fs: fs, back: back,
      playBtn: playBtn, lockBtn: lockBtn, clockEl: clockEl, scrubTime: scrubTime,
      played: played, buf: buf, thumb: thumb, seek: seek, flash: flash,
      hud: hud, hudN: hudIn.querySelector('.pw-hud-n'),
      hudIcon: hudIn.querySelector('.pw-hud-icon'),
      subsWrap: st.querySelector('.pw-subs'), subsEl: st.querySelector('.pw-subtext'),
      menu: st.querySelector('.pw-menu'),
      scrub: null, drag: false, hideTimer: 0, hudTimer: 0, lastTap: 0, lastSide: '',
      accSide: '', accTotal: 0, accAt: 0, raf: 0, flashTimer: 0,
      formats: [], idx: 0, tracks: [], cues: [], subOn: true, subCur: '', subKey: '',
      pendingSeek: null, speed: 1, open: false, page: 0, auto: true, qh: 0,
    };
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

  // SubtitleLayer in player/PlayerVideo.kt: bottom-centre of the video rect, 5.33% of its height,
  // 14 px up, #080C10 at 80% with a 0.52em radius.
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

  // The <video> box is not the picture: object-fit:contain letterboxes it, and a portrait phone
  // gives a 360x800 box around a 360x202 frame. Size and anchor to the picture itself.
  function pictureRect(S) {
    var r = S.v.getBoundingClientRect();
    var vw = S.v.videoWidth, vh = S.v.videoHeight;
    if (!vw || !vh || !r.width || !r.height) return r;
    var k = Math.min(r.width / vw, r.height / vh);
    var w = vw * k, h = vh * k;
    return { left: r.left + (r.width - w) / 2, top: r.top + (r.height - h) / 2, width: w, height: h };
  }

  function renderSubs(S) {
    if (!S.subOn || !S.cues.length) { setSub(S, ''); return; }
    var r = pictureRect(S);
    if (r.height <= 0) { setSub(S, ''); return; }
    var key = Math.round(r.left) + ',' + Math.round(r.top) + ',' + Math.round(r.width) + ',' + Math.round(r.height);
    if (key !== S.subKey) {
      S.subKey = key;
      var px = r.height * 0.0533;
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

  // First track that parses wins. Providers ship one track per language and media3's selector
  // defaults to English, so English goes first; everything else stays as a fallback.
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
    function tryNext() {
      if (i >= list.length || !S.v.__pw) return;
      var track = list[i++];
      fetch(track.u, { credentials: 'omit' })
        .then(function (r) { if (!r.ok) throw new Error('http ' + r.status); return r.text(); })
        .then(function (txt) {
          var cues = parseCues(txt);
          if (!cues.length) throw new Error('no cues');
          if (!S.v.__pw) return;
          S.cues = cues;
          S.cc.disabled = false;
          S.cc.dataset.off = '0';
          subIcon(S);
          render(S);
        })
        .catch(function () { tryNext(); });
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
    // replay the nudge on every tap
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

  function setFullscreen(S, on) {
    if (on) {
      var r = S.stage.requestFullscreen && S.stage.requestFullscreen();
      if (r && r.catch) r.catch(function () {});
    } else if (document.fullscreenElement) {
      document.exitFullscreen();
    }
  }

  function wire(S) {
    var v = S.v;
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
      setFullscreen(S, !document.fullscreenElement);
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

    S.seek.addEventListener('pointerdown', function (e) {
      if (duration(S) <= 0) return;
      S.drag = true;
      S.seek.setPointerCapture(e.pointerId);
      setScrub(S, e);
      poke(S);
    });
    S.seek.addEventListener('pointermove', function (e) {
      if (S.drag) setScrub(S, e);
    });
    S.seek.addEventListener('pointerup', function (e) {
      if (!S.drag) return;
      S.drag = false;
      setScrub(S, e);
      seekTo(S, S.scrub);
    });
    S.seek.addEventListener('pointercancel', function () {
      if (!S.drag) return;
      S.drag = false;
      S.scrub = null;
      render(S);
    });

    v.addEventListener('play', function () { setPlayIcon(S); startLoop(S); });
    v.addEventListener('pause', function () { setPlayIcon(S); render(S); });
    v.addEventListener('ended', function () { setPlayIcon(S); render(S); });
    v.addEventListener('loadedmetadata', function () {
      if (S.pendingSeek != null) {
        try { v.currentTime = S.pendingSeek; } catch (e) {}
        S.pendingSeek = null;
      }
      render(S);
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
    v.addEventListener('playing', function () { S.stage.dataset.stall = '0'; startLoop(S); });
    v.addEventListener('canplay', function () { S.stage.dataset.stall = '0'; });
    v.addEventListener('error', function () { S.stage.dataset.stall = '0'; });

    S.onFs = function () {
      var on = document.fullscreenElement === S.stage;
      S.subKey = '';
      S.stage.dataset.fs = on ? '1' : '0';
      S.fs.innerHTML = '<span class="pw-glyph">' + (on ? ICON.fullscreen_exit : ICON.fullscreen) + '</span>';
      S.fs.setAttribute('aria-label', on ? 'Exit fullscreen' : 'Enter fullscreen');
      if (!on) S.stage.dataset.lock = '0';
      S.back.innerHTML = '<span class="pw-glyph">' + (on ? ICON.chevron_down : ICON.back) + '</span>';
      S.back.setAttribute('aria-label', on ? 'Exit fullscreen' : 'Close player');
    };
    S.onKey = onKey(S);
    S.onResize = function () { S.subKey = ''; render(S); };
    document.addEventListener('fullscreenchange', S.onFs);
    window.addEventListener('keydown', S.onKey);
    window.addEventListener('resize', S.onResize);
  }

  function setScrub(S, e) {
    var r = S.seek.getBoundingClientRect();
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
      if (k === 'f' || k === 'F') { setFullscreen(S, !document.fullscreenElement); poke(S); }
    };
  }

  function flash(S, text, tap) {
    S.flash.textContent = text;
    S.flash.dataset.on = text ? '1' : '0';
    S.flash.dataset.tap = tap ? '1' : '0';
  }

  // ---- settings menu: PlayerSettings.kt, minus the dub page (the proxy resolves one cut) ----

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
        // Android hides the auto resolution on the root row (phone layout); it only shows
        // inside the quality list, which keeps the row short enough never to ellipsize.
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

  // Switching rung reloads the source, so keep the playhead and let loadedmetadata restore it.
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

  // Autoplay: play() runs seconds after the tap that started the resolve, so the browser's
  // transient activation is gone and an unmuted play() is rejected. Muted is always allowed.
  function start(S) {
    var v = S.v;
    var p = v.play();
    if (!p || !p.catch) return;
    p.catch(function (err) {
      if (err && err.name === 'AbortError') return;
      if (!v.muted) {
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
        h.destroy();
        v.__pwHls = null;
        next(S);
      });
      h.loadSource(item.u);
      h.attachMedia(v);
      v.__pwHls = h;
    } else {
      v.onerror = function () { next(S); };
      v.src = item.u;
    }
    start(S);
  }

  function next(S) {
    var st = S.v.__pw;
    if (!st) return;
    if (++st.i < st.list.length) load(S, st.list[st.i], st.i);
    else S.stage.dataset.stall = '0';
  }

  window.pwAttach = function (v, sourcesJson) {
    var doc;
    try { doc = JSON.parse(sourcesJson); } catch (e) { return; }
    var list = (doc && doc.src) || [];
    if (!list.length) return;

    var S = v.__pw ? v.__pw.S : null;
    if (!S) {
      S = build(v);
      wire(S);
      v.__pw = { S: S, list: list, i: 0 };
      document.body.appendChild(S.stage);
      S.stage.insertBefore(v, S.stage.firstChild);
      v.style.zIndex = '0';
      S.fs.innerHTML = '<span class="pw-glyph">' + ICON.fullscreen + '</span>';
      S.mute.innerHTML = '<span class="pw-glyph">' + (v.muted ? ICON.volume_off : ICON.volume_on) + '</span>';
      S.lockBtn.setAttribute('aria-label', 'Lock controls');
      S.title.textContent = v.__pwTitle || '';
      S.flash.addEventListener('click', function () {
        if (S.flash.dataset.tap !== '1') return;
        v.muted = false;
        flash(S, '');
      });
      poke(S);
    }
    v.__pw.list = list;
    v.__pw.i = 0;
    S.formats = list;
    S.tracks = (doc && doc.subs) || [];
    S.qh = heightOf(list[0].l);
    S.auto = true;
    S.subKey = '';
    S.scrub = null;
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
    document.removeEventListener('fullscreenchange', S.onFs);
    window.removeEventListener('keydown', S.onKey);
    window.removeEventListener('resize', S.onResize);
    v.onerror = null;
    v.removeAttribute('src');
    try { v.load(); } catch (e) {}
    if (S.stage.parentNode) S.stage.parentNode.removeChild(S.stage);
  };

  // The title effect can run before the stage exists, so keep it on the element.
  window.pwTitle = function (v, text) {
    if (!v) return;
    v.__pwTitle = text || '';
    if (v.__pw) v.__pw.S.title.textContent = v.__pwTitle;
  };

  // expose for the boot overlay / debugging
  window.pwPlayerMounted = function () { return !!document.querySelector('.pw-stage'); };
})();
