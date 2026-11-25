(function () {
  'use strict';

  var CYAN = '#22D3EE';
  var SLATE200 = '#E2E8F0';
  var ICON = window.PW_ICONS || {};
  var EASE = 'cubic-bezier(.4,0,.2,1)';

  function log(t, m) { if (window.pwLog) window.pwLog(t, m); }

  function seekStart(S) {
    var v = S.v, t = S.startAt || 0;
    if (t > 0 && v.duration > 0 && t < v.duration * 1000 - 30000) {
      try { v.currentTime = t / 1000; } catch (e) {}
      log('play', 'resume at ' + Math.round(t / 1000) + 's');
    }
  }

  var CSS = ''
    + '@font-face{font-family:PWRubik;src:url(./composeResources/dev.phantomwatch.res/font/work_sans_regular.ttf) format("truetype");font-weight:400}'
    + '@font-face{font-family:PWRubik;src:url(./composeResources/dev.phantomwatch.res/font/work_sans_medium.ttf) format("truetype");font-weight:500}'
    + '@font-face{font-family:PWRubik;src:url(./composeResources/dev.phantomwatch.res/font/work_sans_semibold.ttf) format("truetype");font-weight:600}'
    + '@font-face{font-family:PWRubik;src:url(./composeResources/dev.phantomwatch.res/font/work_sans_bold.ttf) format("truetype");font-weight:700}'
    + '@font-face{font-family:PWMono;src:url(./composeResources/dev.phantomwatch.res/font/work_sans_medium.ttf) format("truetype");font-weight:400}'
    + '@font-face{font-family:PWMono;src:url(./composeResources/dev.phantomwatch.res/font/work_sans_semibold.ttf) format("truetype");font-weight:600}'
    + '@font-face{font-family:PWLilita;src:url(./composeResources/dev.phantomwatch.res/font/lilita_one_regular.ttf) format("truetype");font-weight:400}'

    + '.pw-stage{position:fixed;left:0;top:0;right:auto;bottom:auto;width:100%;height:100%;z-index:2;font-family:PWRubik,system-ui,sans-serif;'
    + 'color:#fff;-webkit-user-select:none;user-select:none;-webkit-tap-highlight-color:transparent;touch-action:manipulation}'
    + '.pw-stage *{box-sizing:border-box;margin:0;padding:0}'
    // pinch is browser zoom in landscape, handled here
    + '.pw-stage[data-zoom="1"],.pw-stage[data-zoom="1"] .pw-zones,.pw-stage[data-zoom="1"] video{touch-action:none}'
    + '.pw-zones{position:absolute;inset:0;display:flex}'
    + '.pw-zone{flex:1;height:100%}'
    + '.pw-fade{opacity:0;visibility:hidden;transition:opacity .25s ' + EASE + ',visibility 0s .25s}'
    + '.pw-stage[data-on="1"] .pw-fade{opacity:1;visibility:visible;transition:opacity .25s ' + EASE + ',visibility 0s}'

    + '.pw-scrim{position:absolute;left:0;right:0;top:0;bottom:0;background:rgba(0,0,0,.4)}'

    + '.pw-top{position:absolute;left:0;right:0;top:0;padding:8px 8px 0 0;display:flex;align-items:center;min-height:56px;z-index:2}'
    + '.pw-top{padding-left:env(safe-area-inset-left);padding-right:calc(8px + env(safe-area-inset-right))}'
    + '.pw-ib{background:none;border:0;color:#fff;display:flex;align-items:center;justify-content:center;cursor:pointer;flex:0 0 auto}'
    + '.pw-ib:focus{outline:none}'
    + '.pw-ib:focus-visible{outline:2px solid ' + CYAN + ';outline-offset:-2px;border-radius:50%}'
    + '.pw-back{width:48px;height:48px;margin-left:4px}'
    + '.pw-title{flex:1;min-width:0;font-family:PWLilita,PWRubik,sans-serif;font-size:15px;font-weight:400;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}'
    + '.pw-stage[data-fs="1"] .pw-title{font-size:17px}'
    + '.pw-stage:not([data-fs="1"]) .pw-title{display:none}'
    + '.pw-stage:not([data-fs="1"]) .pw-top .pw-cc{margin-left:auto}'
    + '.pw-top .pw-ib{width:48px;height:48px}'
    + '.pw-cc[data-off="1"]{opacity:.4}'
    + '.pw-cc[data-on="1"]{color:' + CYAN + '}'
    + '.pw-gear[data-off="1"]{opacity:.4}'
    + '.pw-gear[data-on="1"]{color:' + CYAN + '}'
    + '.pw-glyph{width:20px;height:20px;display:block}'

    + '.pw-bottom{position:absolute;left:0;right:0;bottom:0;padding:0 16px 8px;display:flex;flex-direction:column;z-index:2}'
    + '.pw-bottom{padding-left:calc(16px + env(safe-area-inset-left));padding-right:calc(16px + env(safe-area-inset-right))}'
    + '.pw-stage[data-fs="0"] .pw-bottom{flex-direction:column-reverse;padding-bottom:0}'
    + '.pw-stage[data-fs="0"] .pw-bottom .pw-row{margin-bottom:-16px}'
    + '.pw-seek{position:relative;height:24px;touch-action:none;cursor:pointer}'
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
    + '.pw-stage[data-on="1"] .pw-thumb{display:block}'

    + '.pw-row{display:flex;align-items:center;width:100%}'
    + '.pw-clock{flex:1;font:11px/1.2 PWMono,monospace;color:' + SLATE200 + '}'
    + '.pw-row .pw-ib{width:48px;height:48px}'
    + '.pw-row .pw-eps{display:none;width:auto;height:48px;padding:0 10px;border-radius:28px;gap:6px}'
    + '.pw-eps-text{font:600 12px PWRubik,sans-serif;color:#fff;white-space:nowrap}'
    // 56dp controls and 24dp glyphs match landscape phone
    + '@media (orientation:landscape){.pw-top .pw-ib,.pw-row .pw-ib{width:56px;height:56px}'
    + '.pw-row .pw-eps{width:auto;height:56px}'
    + '.pw-top .pw-glyph,.pw-row .pw-glyph{width:24px;height:24px}}'

    + '.pw-playbtn{position:absolute;left:50%;top:50%;width:56px;height:56px;margin:-28px 0 0 -28px;border:0;'
    + 'border-radius:50%;background:rgba(0,0,0,.6);color:#fff;display:flex;align-items:center;justify-content:center;'
    + 'cursor:pointer;transform:scale(.85);transition:transform .3s ' + EASE + '}'
    + '.pw-stage[data-on="1"] .pw-playbtn{transform:scale(1)}'
    + '.pw-playbtn:focus{outline:none}'
    + '.pw-playbtn:focus-visible{outline:2px solid ' + CYAN + ';outline-offset:2px}'
    + '.pw-playbtn svg{width:26px;height:26px}'
    + '.pw-playbtn[data-paused="1"] svg{width:30px;height:30px}'
    + '.pw-playbtn .pw-glyph{width:26px;height:26px}'
    + '.pw-playbtn[data-paused="1"] .pw-glyph{width:30px;height:30px}'
    + '.pw-stage[data-stall="1"] .pw-playbtn,.pw-stage[data-stall="1"] .pw-hud{display:none}'
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
    + '.pw-subs[data-top="1"]{align-items:flex-start}'
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
    + '.pw-mhead .pw-glyph{width:16px;height:16px;margin-right:6px;transform:rotate(180deg);opacity:.7}'

    + '.pw-pill{position:absolute;right:16px;bottom:88px;display:none;align-items:center;height:44px;padding:0 12px 0 16px;'
    + 'border:1px solid rgba(34,211,238,.45);border-radius:999px;background:rgba(0,0,0,.6);color:#fff;'
    + 'font:600 13px/1 PWRubik,sans-serif;letter-spacing:.2px;cursor:pointer;z-index:4;white-space:nowrap}'
    + '.pw-stage[data-on="0"] .pw-pill{bottom:24px}'
    + '.pw-pill[data-compact="1"]{height:36px}'
    + '.pw-pill .pw-glyph{width:14px;height:14px;margin-left:6px;display:block}'
    + '.pw-pill[data-before="1"] .pw-glyph{margin:0 6px 0 0}'
    + '.pw-undo .pw-glyph{width:16px;height:16px}'

    + '.pw-next{position:absolute;left:50%;top:50%;transform:translate(-50%,-50%);width:min(320px,calc(100% - 32px));'
    + 'border-radius:20px;background:#F21A1A1A;padding:20px;display:none;flex-direction:column;align-items:center;'
    + 'text-align:center;z-index:7;color:#fff;font-family:PWRubik,sans-serif}'
    + '.pw-next[data-on="1"]{display:flex}'
    + '.pw-next-kicker{color:' + CYAN + ';font-size:11px;font-weight:600}'
    + '.pw-next-se{margin-top:8px;font-size:15px;font-weight:600}'
    + '.pw-next-name{margin-top:4px;font-size:13px;color:#94A3B8;max-width:100%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}'
    + '.pw-next-left{margin-top:8px;font-size:12px;color:#64748B}'
    + '.pw-next-btns{margin-top:14px;display:flex;gap:10px}'
    + '.pw-next-btns button{border:0;border-radius:50px;padding:10px 20px;font:500 13px PWRubik,sans-serif;cursor:pointer}'
    + '.pw-next-play{background:rgba(255,255,255,.12);color:#CBD5E1}'
    + '.pw-next-stay{background:' + CYAN + ';color:#0B0B0B;font-weight:600}'

    + '.pw-swipe{position:absolute;left:50%;top:8px;transform:translateX(-50%);width:min(50%,220px);height:40px;'
    + 'border-radius:20px;background:rgba(0,0,0,.7);display:none;align-items:center;gap:8px;padding:0 12px;z-index:5;'
    + 'color:#fff;font:600 12px PWRubik,sans-serif}'
    + '.pw-stage[data-swipe="1"] .pw-swipe{display:flex}'
    + '.pw-swipe-track{flex:1;height:3px;border-radius:2px;background:rgba(255,255,255,.3);overflow:hidden}'
    + '.pw-swipe-fill{height:100%;background:#fff;width:0}'
    + '.pw-swipe svg{width:18px;height:18px;display:block;flex:0 0 auto}'

    // preview anchored to bar: frame ends 5px above, dot 10px below top
    + '.pw-preview{position:absolute;bottom:14px;left:0;display:none;flex-direction:column;align-items:center;'
    + 'z-index:5;pointer-events:none}'
    + '.pw-preview[data-on="1"]{display:flex}'
    + '.pw-preview-frame{position:relative;border:1px solid ' + CYAN + ';border-radius:8px;background:#000;overflow:hidden}'
    + '.pw-preview-frame video{width:100%;height:100%;object-fit:cover;display:block}'
    + '.pw-preview-time{position:absolute;right:4px;bottom:4px;padding:1px 5px;border-radius:3px;background:rgba(0,0,0,.45);'
    + 'font:600 11px PWRubik,sans-serif;color:#fff}'
    + '.pw-preview-caret{width:16px;height:7px;background:' + CYAN + ';border-radius:0 0 50% 50% / 0 0 100% 100%}'
    + '.pw-preview-dot{width:5px;height:5px;border-radius:50%;background:' + CYAN + ';margin-top:3px}'

    + '.pw-failed{position:absolute;inset:0;background:rgba(0,0,0,.72);display:none;flex-direction:column;'
    + 'align-items:center;justify-content:center;gap:12px;z-index:8;color:#fff;font-family:PWRubik,sans-serif}'
    + '.pw-failed[data-on="1"]{display:flex}'
    + '.pw-failed button{border:0;border-radius:8px;padding:10px 20px;font:500 14px PWRubik,sans-serif;cursor:pointer}'
    + '.pw-fail-go{background:' + CYAN + ';color:#0B0B0B}'
    + '.pw-fail-back{background:transparent;color:#fff}';

  var STAGE = null;
  // wheel over chrome replayed to canvas, native wheel would double scroll
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

  // stream aspect so 2.39 film not letterboxed, zero before metadata
  window.pwAspect = function () {
    var s = STAGE && STAGE.__pwS;
    if (!s || !s.v || !s.v.videoWidth || !s.v.videoHeight) return 0;
    return s.v.videoWidth / s.v.videoHeight;
  };

  var EPS = false;
  window.pwEpisodes = function (on) {
    EPS = !!on;
    applyEps();
  };

  var CONFIG = null;
  function langName(code) {
    if (!code) return '';
    try { return new Intl.DisplayNames(['en'], { type: 'language' }).of(code) || ''; } catch (e) { return ''; }
  }
  window.pwConfig = function (json) {
    CONFIG = json;
    if (STAGE && STAGE.__pwS) applyConfig(STAGE.__pwS, json);
  };

  // ani.zip maps tmdb to mal, aniskip gives op/ed windows, both allow cors
  function fetchSkip(S) {
    var r = S.skipReq;
    if (!r || S.skipDone || !r.tmdb || !r.episode) return;
    var sec = S.v.duration;
    if (!isFinite(sec) || sec < 60) return;
    S.skipDone = true;
    var ep = String(r.episode).replace(/^S?\d+E/i, '');
    fetch('https://api.ani.zip/mappings?themoviedb_id=' + encodeURIComponent(r.tmdb))
      .then(function (x) { return x.ok ? x.json() : null; })
      .then(function (m) {
        var mal = m && m.mappings && m.mappings.mal_id;
        if (!mal) return null;
        return fetch('https://api.aniskip.com/v2/skip-times/' + mal + '/' + encodeURIComponent(ep) +
          '?episodeLength=' + Math.floor(sec) + '&types[]=op&types[]=mixed-op&types[]=ed')
          .then(function (x) { return x.ok ? x.json() : null; });
      })
      .then(function (j) {
        var list = (j && j.results) || [];
        function win(types) {
          for (var i = 0; i < list.length; i++) {
            var row = list[i], iv = row.interval;
            if (!iv || types.indexOf(row.skipType) < 0) continue;
            var a = +iv.startTime * 1000, b = +iv.endTime * 1000;
            if (b > a && a >= 0 && b <= sec * 1000) return [a, b];
          }
          return null;
        }
        S.pills.intro = win(['op', 'mixed-op']);
        S.pills.outro = win(['ed']);
        updatePills(S);
        log('skip', 'intro=' + !!S.pills.intro + ' outro=' + !!S.pills.outro);
      })
      .catch(function (e) { log('skip', 'fail ' + (e && e.message)); });
  }

  var SUBSTOP = false;
  function applySubsTop(S, on) {
    S.subsTop = on;
    S.subKey = '';
    S.subsWrap.dataset.top = on ? '1' : '0';
    S.subsEl.style.transform = on ? 'translateY(0)' : 'translateY(-14px)';
    renderSubs(S);
  }
  window.pwSubsTop = function (on) {
    SUBSTOP = !!on;
    var S = STAGE && STAGE.__pwS;
    if (S) applySubsTop(S, SUBSTOP);
  };

  var NEXT = null;
  window.pwNext = function (json) {
    NEXT = json;
    var S = STAGE && STAGE.__pwS;
    if (!S) return;
    try { S.next = JSON.parse(json || 'null'); } catch (e) { S.next = null; }
    updateNext(S);
  };

  function applyConfig(S, json) {
    var d;
    try { d = JSON.parse(json || '{}'); } catch (e) { return; }
    S.cfg = {
      seekStep: [5, 10, 15, 30][d.seekStep] || 10,
      nextCount: [5, 10, 15][d.nextCount] || 10,
      subsSize: d.subsSize == null ? 1 : d.subsSize,
      subsBg: d.subsBg == null ? 1 : d.subsBg,
      subsStyle: d.subsStyle == null ? 1 : d.subsStyle,
      skipIntro: d.skipIntro == null ? 1 : d.skipIntro,
      autoplay: d.autoplay == null ? 1 : d.autoplay,
    };
    S.baseLang = langName(d.language) || 'Sub';
    S.pills = d.pills || { intro: null, recap: null, outro: null };
    S.next = d.next || null;
    S.skipReq = { tmdb: d.tmdb || '', episode: d.episode || '' };
    S.skipDone = false;
    S.subOn = d.subsOn !== 0;
    S.subKey = '';
    subIcon(S);
    updatePills(S);
    render(S);
  }

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
      + '<div class="pw-thumb"></div>'
      + '<div class="pw-preview"><div class="pw-preview-frame"><span class="pw-preview-time"></span></div>'
      + '<div class="pw-preview-caret"></div><div class="pw-preview-dot"></div></div></div>'
      + '<div class="pw-row"><div class="pw-clock"></div></div></div>'
      + '<div class="pw-slim"><div class="pw-trackwrap"><div class="pw-buf"></div>'
      + '<div class="pw-played"></div></div></div>'
      + '<button class="pw-playbtn pw-fade" type="button"></button>'
      + '<button class="pw-lock pw-fade" type="button"></button>'
      + '<div class="pw-hud" data-side="r"><div class="pw-hud-in"></div></div>'
      + '<svg class="pw-spin" viewBox="0 0 48 48"><circle cx="24" cy="24" r="20" fill="none" '
      + 'stroke="' + CYAN + '" stroke-width="4" stroke-linecap="round" stroke-dasharray="80 200"/></svg>'
      + '<div class="pw-flash"></div><div class="pw-menu"></div>'
      + '<button class="pw-pill" type="button" data-kind="skip"></button>'
      + '<button class="pw-pill pw-undo" type="button" data-kind="undo"></button>'
      + '<div class="pw-next"><div class="pw-next-kicker">UP NEXT</div><div class="pw-next-se"></div>'
      + '<div class="pw-next-name"></div><div class="pw-next-left"></div><div class="pw-next-btns">'
      + '<button class="pw-next-play" type="button">Autoplay</button>'
      + '<button class="pw-next-stay" type="button">Stay on this episode</button></div></div>'
      + '<div class="pw-swipe"><span class="pw-swipe-icon"></span>'
      + '<div class="pw-swipe-track"><div class="pw-swipe-fill"></div></div><span class="pw-swipe-n"></span></div>'
      + '<div class="pw-failed"><div>Playback failed</div>'
      + '<button class="pw-fail-go" type="button">Choose source</button>'
      + '<button class="pw-fail-back" type="button">Back</button></div>';

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
    var eps = el('button', 'pw-ib pw-eps');
    eps.type = 'button';
    eps.setAttribute('aria-label', 'Episodes');
    eps.innerHTML = '<span class="pw-eps-text">EPISODES</span><span class="pw-glyph">' + (ICON.episodes || '') + '</span>';
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
      skipPill: st.querySelector('.pw-pill[data-kind="skip"]'),
      undoPill: st.querySelector('.pw-pill[data-kind="undo"]'),
      nextCard: st.querySelector('.pw-next'),
      swipe: st.querySelector('.pw-swipe'),
      preview: st.querySelector('.pw-preview'),
      previewFrame: st.querySelector('.pw-preview-frame'),
      failed: st.querySelector('.pw-failed'),
      scrub: null, drag: false, hideTimer: 0, hudTimer: 0, lastTap: 0, lastSide: '',
      accSide: '', accTotal: 0, accAt: 0, raf: 0, flashTimer: 0,
      formats: [], idx: 0, tracks: [], cues: [], subOn: true, subCur: '', subKey: '',
      pendingSeek: null, speed: 1, open: false, page: 0, auto: true, qh: 0,
      mode: 0, pad: 0, safe: false, near: false, zoom: false, cut: { l: 0, r: 0 },
      barsDone: false, zoomInit: false, pinched: 0,
      cfg: { seekStep: 10, subsSize: 1, subsBg: 1, skipIntro: 1, autoplay: 1, nextCount: 10 },
      pills: { intro: null, recap: null, outro: null },
      next: null, nextLeft: 0, stay: false, seekBack: null, seekBackTimer: 0,
      bright: 1, swipeTimer: 0, swipeKind: '',
    };
    st.__pwS = S;
    return S;
  }

  function duration(S) {
    var d = S.v.duration;
    return isFinite(d) && d > 0 ? d * 1000 : 0;
  }

  var PILL_NEXT = '<span class="pw-glyph">' + (ICON.skip_next_filled || '') + '</span>';
  var PILL_UNDO = '<span class="pw-glyph">' + (ICON.revert || '') + '</span>';

  function pill(btn, on, text, svg, before) {
    if (!on) { btn.style.display = 'none'; return; }
    btn.style.display = 'flex';
    if (btn.__text !== text) {
      btn.__text = text;
      btn.innerHTML = (before ? svg : '') + '<span>' + text + '</span>' + (before ? '' : svg);
    }
  }

  // recap wins over intro, outro never shares screen
  function updatePills(S) {
    var t = S.v.currentTime * 1000, dur = duration(S), p = S.pills;
    var kind = null;
    if (dur > 0) {
      if (p.recap && t >= p.recap[0] && t < p.recap[1]) kind = 'recap';
      else if (S.cfg.skipIntro !== 0 && p.intro && t >= p.intro[0] && t < p.intro[1]) kind = 'intro';
      else if (p.outro && t >= p.outro[0] && t < p.outro[1]) kind = 'next';
    }
    S.skipPill.dataset.kind = kind || '';
    pill(S.skipPill, !!kind, kind === 'recap' ? 'Skip recap' : kind === 'intro' ? 'Skip intro' : 'Next episode', PILL_NEXT, false);
    pill(S.undoPill, !!S.seekBack, 'Undo', PILL_UNDO, true);
  }

  function updateNext(S) {
    var dur = duration(S), n = S.next;
    if (!n || !S.cfg.autoplay || S.stay || !dur) { S.nextCard.dataset.on = '0'; return; }
    var win = (S.cfg.nextCount || 10) * 1000;
    var leftMs = S.v.ended ? 0 : dur - S.v.currentTime * 1000;
    if (leftMs > win) { S.nextCard.dataset.on = '0'; return; }
    S.nextCard.dataset.on = '1';
    S.nextCard.querySelector('.pw-next-se').textContent = n.se || '';
    S.nextCard.querySelector('.pw-next-name').textContent = n.name || '';
    var left = S.v.ended ? Math.max(0, (S.cfg.nextCount || 10) - Math.floor((Date.now() - S.endAt) / 1000))
      : Math.ceil(leftMs / 1000);
    S.nextCard.querySelector('.pw-next-left').textContent = 'Up next in ' + left + 's';
  }

  function render(S) {
    renderSubs(S);
    updatePills(S);
    updateNext(S);
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
    var w = S.seek.clientWidth;
    var x = Math.round(shown * w);
    S.thumb.style.left = (Math.min(Math.max(x, 7), Math.max(w - 7, 7)) - 7) + 'px';
    if (S.scrub != null && dur > 0) {
      S.scrubTime.dataset.on = '1';
      S.scrubTime.textContent = clock(S.scrub * dur);
    } else {
      S.scrubTime.dataset.on = '0';
    }
  }

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

  // cross origin media taints canvas, caller treats failed read as geometry
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
    return (v.videoWidth / v.videoHeight) / (1 - S.pad);
  }

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

  function autoFill(S) {
    if (!S.zoom || S.zoomInit || !S.v.videoWidth) return;
    S.zoomInit = true;
    if (S.near) {
      S.mode = 2;
      applyZoom(S);
    }
  }

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

  // five samples, smallest honest reading, fade reads as bars
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

  // video box letterboxes picture, anchor captions to picture rect
  function pictureRect(S) {
    var r = S.v.getBoundingClientRect();
    var vw = S.v.videoWidth, vh = S.v.videoHeight;
    if (!vw || !vh || !r.width || !r.height) return r;
    var k = Math.min(r.width / vw, r.height / vh);
    var w = vw * k, h = vh * k;
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
    var key = Math.round(r.left) + ',' + Math.round(r.top) + ',' + Math.round(r.width) + ',' + Math.round(r.height) + ',' + S.cfg.subsStyle;
    if (key !== S.subKey) {
      S.subKey = key;
      // 5.33% of min(shortSide/aspect, drawn height), floor 13px, 15px fullscreen
      var aspect = r.width / r.height;
      var shortSide = Math.min(window.innerWidth, window.innerHeight);
      var basis = Math.min(shortSide / aspect, r.height);
      var scale = [0.82, 1, 1.22][S.cfg.subsSize] || 1;
      var outlined = S.cfg.subsStyle === 1;
      var theater = !!S.subsTop;
      var floor = STAGE && (STAGE.dataset.fs === '1' || theater) ? 15 : 13;
      var px = Math.max(basis * 0.0533 * scale * (outlined ? 1.14 : 1), floor);
      S.subsWrap.style.left = r.left + 'px';
      S.subsWrap.style.top = (theater ? r.top + r.height + 6 : r.top) + 'px';
      S.subsWrap.style.width = r.width + 'px';
      S.subsWrap.style.height = (theater ? 90 : r.height) + 'px';
      S.subsEl.style.fontSize = px + 'px';
      S.subsEl.style.lineHeight = (px * 1.2) + 'px';
      if (outlined) {
        S.subsEl.style.background = 'transparent';
        S.subsEl.style.padding = '0';
        S.subsEl.style.borderRadius = '0';
        S.subsEl.style.webkitTextStroke = Math.max(1.8, px * 0.055) + 'px #000';
        S.subsEl.style.textShadow = '0 ' + (px * 0.15) + 'px ' + (px * 0.42) + 'px rgba(0,0,0,.95)';
        S.subsEl.style.fontWeight = '600';
      } else {
        S.subsEl.style.borderRadius = (px * 0.52) + 'px';
        S.subsEl.style.padding = (px * 0.33) + 'px ' + (px * 0.59) + 'px';
        S.subsEl.style.background = 'rgba(8,12,16,' + [0, 0.8, 0.95][S.cfg.subsBg] + ')';
        S.subsEl.style.webkitTextStroke = '';
        S.subsEl.style.textShadow = '';
        S.subsEl.style.fontWeight = '';
      }
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
    S.hideTimer = setTimeout(function () { S.stage.dataset.on = '0'; }, 4000);
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
    var step = S.cfg.seekStep || 10;
    S.accTotal = S.accSide === side && now - S.accAt < 1000 ? S.accTotal + step : step;
    S.accSide = side;
    S.accAt = now;
    var d = side === 'l' ? -step : step;
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

  // whole document fullscreen, video is body child
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
    if (STAGE.__pwS) updateZoom(STAGE.__pwS);
  };

  // brightness is css filter, browser cannot set window brightness
  function verticalSwipe(S) {
    var start = null, kind = '', base = 0;
    function show(val) {
      S.stage.dataset.swipe = '1';
      S.swipe.querySelector('.pw-swipe-icon').innerHTML = kind === 'bright'
        ? (ICON.brightness || '') : (ICON.volume_on || '');
      S.swipe.querySelector('.pw-swipe-fill').style.width = Math.round(val * 100) + '%';
      S.swipe.querySelector('.pw-swipe-n').textContent = Math.round(val * 100) + '%';
      clearTimeout(S.swipeTimer);
      S.swipeTimer = setTimeout(function () { S.stage.dataset.swipe = '0'; }, 500);
    }
    S.stage.querySelectorAll('.pw-zone').forEach(function (z) {
      z.addEventListener('pointerdown', function (e) {
        if (S.stage.dataset.lock === '1' || S.open || e.pointerType === 'mouse') return;
        kind = z.dataset.z === 'l' ? 'bright' : z.dataset.z === 'r' ? 'volume' : '';
        if (!kind) return;
        start = { x: e.clientX, y: e.clientY, id: e.pointerId };
        base = kind === 'bright' ? S.bright : S.v.volume;
        try { z.setPointerCapture(e.pointerId); } catch (err) {}
      });
      z.addEventListener('pointermove', function (e) {
        if (!start || e.pointerId !== start.id || !kind) return;
        var dy = start.y - e.clientY, dx = Math.abs(e.clientX - start.x);
        if (Math.abs(dy) < 24 || Math.abs(dy) < dx * 1.5) return;
        var val = Math.min(1, Math.max(0.05, base + dy / 200));
        if (kind === 'bright') {
          S.bright = val;
          S.v.style.filter = 'brightness(' + val + ')';
        } else {
          S.v.volume = val;
          S.v.muted = false;
        }
        show(val);
      });
      function up() { start = null; kind = ''; }
      z.addEventListener('pointerup', up);
      z.addEventListener('pointercancel', up);
    });
  }

  function wire(S) {
    var v = S.v;
    pinch(S);
    verticalSwipe(S);
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

    S.skipPill.addEventListener('click', function (e) {
      e.stopPropagation();
      var kind = S.skipPill.dataset.kind;
      if (kind === 'next') document.dispatchEvent(new Event('pwnext'));
      else {
        var r = kind === 'recap' ? S.pills.recap : S.pills.intro;
        if (r) S.v.currentTime = r[1] / 1000;
      }
      poke(S);
    });

    S.undoPill.addEventListener('click', function (e) {
      e.stopPropagation();
      if (S.seekBack != null) S.v.currentTime = S.seekBack / 1000;
      S.seekBack = null;
      pill(S.undoPill, false, '', '', false);
      poke(S);
    });

    S.nextCard.querySelector('.pw-next-play').addEventListener('click', function (e) {
      e.stopPropagation();
      clearTimeout(S.autoTimer);
      S.stay = false;
      document.dispatchEvent(new Event('pwnext'));
    });
    S.nextCard.querySelector('.pw-next-stay').addEventListener('click', function (e) {
      e.stopPropagation();
      clearTimeout(S.autoTimer);
      S.stay = true;
      S.nextCard.dataset.on = '0';
      poke(S);
    });
    S.failed.querySelector('.pw-fail-go').addEventListener('click', function (e) {
      e.stopPropagation();
      S.failed.dataset.on = '0';
      openMenu(S);
      S.page = 1;
      menuRender(S);
    });
    S.failed.querySelector('.pw-fail-back').addEventListener('click', function (e) {
      e.stopPropagation();
      document.dispatchEvent(new Event('pwback'));
    });

    bindSeek(S, S.seek);
    bindSeek(S, S.slim);

    v.addEventListener('play', function () { setPlayIcon(S); startLoop(S); });
    v.addEventListener('pause', function () { setPlayIcon(S); render(S); });
    v.addEventListener('ended', function () {
      setPlayIcon(S);
      S.endAt = Date.now();
      render(S);
      if (S.cfg.autoplay && S.next && !S.stay) {
        clearTimeout(S.autoTimer);
        S.autoTimer = setTimeout(function () { document.dispatchEvent(new Event('pwnext')); }, (S.cfg.nextCount || 10) * 1000);
      }
    });
    v.addEventListener('loadedmetadata', function () {
      if (S.pendingSeek != null) {
        try { v.currentTime = S.pendingSeek; } catch (e) {}
        S.pendingSeek = null;
      }
      updateZoom(S);
      fetchSkip(S);
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

  function bindSeek(S, el) {
    el.addEventListener('pointerdown', function (e) {
      if (duration(S) <= 0) return;
      S.drag = true;
      S.dragEl = el;
      try { el.setPointerCapture(e.pointerId); } catch (err) {}
      setScrub(S, e, el);
      if (el !== S.slim) poke(S);
    });
    el.addEventListener('pointermove', function (e) {
      if (S.drag) setScrub(S, e, S.dragEl);
    });
    el.addEventListener('pointerup', function (e) {
      if (!S.drag) return;
      S.drag = false;
      setScrub(S, e, S.dragEl);
      S.preview.dataset.on = '0';
      if (S.scrub != null && duration(S) > 0) {
        S.seekBack = S.v.currentTime * 1000;
        clearTimeout(S.seekBackTimer);
        S.seekBackTimer = setTimeout(function () { S.seekBack = null; updatePills(S); }, 4000);
      }
      seekTo(S, S.scrub);
    });
    el.addEventListener('pointercancel', function () {
      if (!S.drag) return;
      S.drag = false;
      S.scrub = null;
      S.preview.dataset.on = '0';
      render(S);
    });
  }

  function setScrub(S, e, el) {
    var r = (el || S.seek).getBoundingClientRect();
    if (r.width <= 0) return;
    S.scrub = Math.min(1, Math.max(0, (e.clientX - r.left) / r.width));
    render(S);
    var dur = duration(S);
    if (S.scrub == null || !dur) { S.preview.dataset.on = '0'; return; }
    S.preview.dataset.on = '1';
    S.preview.querySelector('.pw-preview-time').textContent = clock(S.scrub * dur);
    var stageRect = S.stage.getBoundingClientRect();
    var bar = Math.max(r.width, 1);
    var budget = Math.max(stageRect.height * 0.5, 1);
    var aspect = S.v.videoWidth && S.v.videoHeight ? S.v.videoWidth / S.v.videoHeight : 16 / 9;
    var cap = Math.min(280, budget * aspect, bar);
    var width = Math.max(Math.min(176, cap), Math.min(bar * 0.6, cap));
    S.previewFrame.style.width = Math.round(width) + 'px';
    S.previewFrame.style.height = Math.round(Math.min(width / aspect, budget)) + 'px';
    var x = bar > width ? Math.min(Math.max(Math.round(S.scrub * bar) - width / 2, 0), bar - width) : 0;
    S.preview.style.left = Math.round(x) + 'px';
    // hls blob cannot be shared, preview frame only for direct files
    if (!S.previewV) {
      var pv = document.createElement('video');
      pv.muted = true;
      pv.preload = 'auto';
      pv.setAttribute('playsinline', '');
      pv.style.cssText = 'width:100%;height:100%;object-fit:cover;display:block';
      S.previewFrame.insertBefore(pv, S.previewFrame.firstChild);
      S.previewV = pv;
    }
    var item = S.formats[S.idx];
    if (item && !item.h && S.previewV.getAttribute('src') !== item.u) S.previewV.setAttribute('src', item.u);
    if (S.previewV.readyState >= 2) {
      try { S.previewV.currentTime = S.scrub * dur / 1000; } catch (err) {}
    }
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
      if (k === 'n' || k === 'N') { document.dispatchEvent(new Event('pwnext')); return; }
      if (k === 's' || k === 'S') {
        var kind = S.skipPill.dataset.kind;
        if (kind === 'next') document.dispatchEvent(new Event('pwnext'));
        else {
          var r = kind === 'recap' ? S.pills.recap : S.pills.intro;
          if (r) S.v.currentTime = r[1] / 1000;
        }
        return;
      }
      if (k === 'f' || k === 'F') { setFullscreen(); poke(S); }
    };
  }

  function flash(S, text, tap) {
    S.flash.textContent = text;
    S.flash.dataset.on = text ? '1' : '0';
    S.flash.dataset.tap = tap ? '1' : '0';
  }


  var SPEEDS = [0.5, 0.75, 1, 1.25, 1.5, 1.75, 2];

  function speedLabel(x) {
    return (x === Math.floor(x) ? Math.floor(x) : x) + '×';
  }

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

  function providerName(label) {
    var map = {
      love: 'VidLove', rock: 'VidRock', vix: 'VixSrc', zee: 'VidZee',
      paradise: 'AnimeParadise', vap: 'VAPlayer', vidlink: 'VidLink',
      hianime: 'HiAnime', allanime: 'AllAnime', pinoy: 'Pinoy',
    };
    var leg = (label || '').split('-')[0];
    return map[leg] || leg;
  }

  function dubs(S) {
    var out = [], seen = {};
    var base = S.formats[S.idx] || S.formats[0];
    if (base) {
      var l0 = (S.baseLang || 'Sub') + ' · ' + providerName(base.l);
      out.push({ label: l0, shown: l0.split(' · ')[0], i: S.idx });
    }
    S.formats.forEach(function (f, i) {
      var l = f.l || '';
      if (!/(^|[-_])dub([-_]|$)/i.test(l)) return;
      var label = (f.d || 'English') + ' · ' + providerName(l);
      if (seen[label]) return;
      seen[label] = 1;
      out.push({ label: label, shown: label.split(' · ')[0], i: i });
    });
    return out;
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
    } else if (S.page === 4) {
      m.appendChild(menuHead(S, 'Dubs'));
      dubs(S).forEach(function (d) {
        m.appendChild(menuRow(d.shown, null, null, false, d.i === S.idx, null, function () {
          var at = S.v.currentTime;
          closeMenu(S);
          S.pendingSeek = at;
          load(S, S.formats[d.i], d.i);
        }));
      });
    } else {
      var q = qualities(S);
      if (q.length > 1) {
        var qv = S.auto ? (window.innerWidth >= 840 && S.qh ? 'Auto · ' + S.qh + 'p' : 'Auto') : S.qh + 'p';
        m.appendChild(menuRow('Quality', qv, 'quality', true, false, null,
          function () { S.page = 1; menuRender(S); }));
      }
      m.appendChild(menuRow('Speed', speedLabel(S.speed), 'speed', true, false, null,
        function () { S.page = 2; menuRender(S); }));
      var ds = dubs(S);
      if (ds.length > 1) {
        var cur = ds.filter(function (d) { return d.i === S.idx; })[0];
        m.appendChild(menuRow('Dubs', cur ? cur.shown : 'Sub', 'dubs', true, false, null,
          function () { S.page = 4; menuRender(S); }));
      }
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

  // rung switch reloads source, loadedmetadata restores playhead
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

  // unmuted play rejected after resolve, muted retry allowed
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
      S.failed.dataset.on = '1';
      poke(S);
      document.dispatchEvent(new Event('pwfail'));
    }
  }

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
    if (CONFIG) applyConfig(S, CONFIG);
    applySubsTop(S, SUBSTOP);
    if (NEXT) { try { S.next = JSON.parse(NEXT); } catch (e) {} }
    S.title.textContent = v.__pwTitle || '';
    S.flash.addEventListener('click', function () {
      if (S.flash.dataset.tap !== '1') return;
      v.muted = false;
      flash(S, '');
    });
    return S;
  }

  window.pwStall = function (v, title) {
    var S = v.__pw ? v.__pw.S : mount(v);
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
    S.mode = 0;
    S.pad = 0;
    S.barsDone = false;
    S.zoomInit = false;
    S.pinched = 0;
    // seek saved position once per title, not on rung switch
    S.restored = false;
    S.stay = false;
    S.seekBack = null;
    clearTimeout(S.autoTimer);
    clearTimeout(S.seekBackTimer);
    S.failed.dataset.on = '0';
    S.nextCard.dataset.on = '0';
    S.startAt = (doc && doc.start) || 0;
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
    clearTimeout(S.autoTimer);
    clearTimeout(S.seekBackTimer);
    clearTimeout(S.swipeTimer);
    S.preview.dataset.on = '0';
    S.open = false;
    S.menu.dataset.on = '0';
    window.removeEventListener('keydown', S.onKey);
    window.removeEventListener('resize', S.onResize);
    v.onerror = null;
    v.removeAttribute('src');
    try { v.load(); } catch (e) {}
    if (S.stage.parentNode) S.stage.parentNode.removeChild(S.stage);
  };

  window.pwTitle = function (v, text) {
    if (!v) return;
    v.__pwTitle = text || '';
    if (v.__pw) v.__pw.S.title.textContent = v.__pwTitle;
  };

  window.pwPlayerMounted = function () { return !!document.querySelector('.pw-stage'); };
})();
