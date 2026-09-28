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

    + '.pw-top{position:absolute;left:0;right:0;top:0;padding:8px 8px 0 0;display:flex;align-items:center;min-height:56px}'
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

    + '.pw-bottom{position:absolute;left:0;right:0;bottom:0;padding:0 16px 8px;display:flex;flex-direction:column}'
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
    + '.pw-flash[data-tap="1"]{cursor:pointer}';

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
      + '<div class="pw-flash"></div>';

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
    gear.dataset.off = '1';
    gear.disabled = true;
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
      scrub: null, drag: false, hideTimer: 0, hudTimer: 0, lastTap: 0, lastSide: '',
      accSide: '', accTotal: 0, accAt: 0, raf: 0,
    };
    return S;
  }

  function duration(S) {
    var d = S.v.duration;
    return isFinite(d) && d > 0 ? d * 1000 : 0;
  }

  function render(S) {
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
      S.stage.dataset.fs = on ? '1' : '0';
      S.fs.innerHTML = '<span class="pw-glyph">' + (on ? ICON.fullscreen_exit : ICON.fullscreen) + '</span>';
      S.fs.setAttribute('aria-label', on ? 'Exit fullscreen' : 'Enter fullscreen');
      if (!on) S.stage.dataset.lock = '0';
      S.back.innerHTML = '<span class="pw-glyph">' + (on ? ICON.chevron_down : ICON.back) + '</span>';
      S.back.setAttribute('aria-label', on ? 'Exit fullscreen' : 'Close player');
    };
    S.onKey = onKey(S);
    S.onResize = function () { render(S); };
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

  function load(S, item) {
    var v = S.v;
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
    if (++st.i < st.list.length) load(S, st.list[st.i]);
    else S.stage.dataset.stall = '0';
  }

  window.pwAttach = function (v, sourcesJson) {
    var list = [];
    try { list = JSON.parse(sourcesJson); } catch (e) { return; }
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
    S.scrub = null;
    setPlayIcon(S);
    load(S, list[0]);
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
