/* ArkaFlix TV: navegación con mando (flechas + OK) como en cualquier app de TV. Solo ES5 (los WebView de las teles son viejos). */
(function () {
  if (window.__afDpad) return; window.__afDpad = true;
  var D = document;

  /* ---------- aspecto del foco: anillo blanco + rojo de la marca, sin cuadros amarillos ---------- */
  var st = D.createElement('style');
  st.id = 'af-dpad-css';
  st.textContent = '*{-webkit-tap-highlight-color:rgba(0,0,0,0)!important}'
    + 'a,button,input,select,textarea,summary,[tabindex],[role=button],[role=link],[role=tab],[role=menuitem]{outline:none!important}'
    + ':focus{outline:none!important}'
    + '.af-f{outline:3px solid #fff!important;outline-offset:2px!important;box-shadow:0 0 0 6px rgba(229,9,20,.85),0 10px 30px rgba(0,0,0,.65)!important;position:relative;z-index:5}'
    + 'a.cd.af-f,.t10 a.af-f{transform:scale(1.045);transition:transform .15s ease}'
    + 'input.af-f,textarea.af-f,select.af-f{border-radius:8px}'
    + 'html{scroll-behavior:smooth}';
  (D.head || D.documentElement).appendChild(st);
  var cur = null;
  function mark(el) { if (cur && cur !== el) { cur.classList.remove('af-f'); } cur = el; if (el) el.classList.add('af-f'); }
  D.addEventListener('focusin', function (e) { mark(e.target && e.target.classList ? e.target : null); }, true);
  D.addEventListener('focusout', function (e) { if (e.target && e.target.classList) e.target.classList.remove('af-f'); }, true);

  /* ---------- candidatos de foco ---------- */
  var SEL = 'a[href]:not([href="#main"]):not(.skip-link):not(.skip),button:not([disabled]),input:not([disabled]):not([type=hidden]),select:not([disabled]),textarea:not([disabled]),[tabindex]:not([tabindex="-1"]),[role=button],[role=link],[role=tab],[role=menuitem],summary';
  function visible(el) {
    var r = el.getBoundingClientRect(); if (r.width < 4 || r.height < 4) return false;
    var cs = window.getComputedStyle(el); if (cs.visibility === 'hidden' || cs.display === 'none' || cs.opacity === '0' || cs.pointerEvents === 'none') return false;
    return r.bottom > -window.innerHeight && r.top < window.innerHeight * 2 && r.right > -window.innerWidth && r.left < window.innerWidth * 2;
  }
  function overlayRoot() {   // si hay un diálogo/ventana abierta, el foco se queda dentro
    var d = D.querySelector('[role=dialog][aria-modal=true],dialog[open],.afp-ov,.afc-ov,.mm-modal.open'); return d || D;
  }
  function list() { var root = overlayRoot(), n = root.querySelectorAll(SEL), out = [], i; for (i = 0; i < n.length; i++) { if (visible(n[i])) out.push(n[i]); } return out; }
  function center(r) { return { x: r.left + r.width / 2, y: r.top + r.height / 2 }; }

  var keepX = null, keepY = null;     // al subir/bajar se intenta mantener la misma columna; al ir de lado, la misma fila
  function pick(from, dir) {
    var f = from.getBoundingClientRect(), fc = center(f), best = null, bs = 1e12, c = list(), i;
    var vx = (dir === 'up' || dir === 'down') && keepX !== null ? keepX : fc.x;
    var hy = (dir === 'left' || dir === 'right') && keepY !== null ? keepY : fc.y;
    for (i = 0; i < c.length; i++) {
      var el = c[i]; if (el === from || from.contains(el) || el.contains(from)) continue;
      var r = el.getBoundingClientRect(), rc = center(r), dx = rc.x - fc.x, dy = rc.y - fc.y, main, cross, ok;
      if (dir === 'right') { ok = r.left >= f.right - 6 || dx > f.width / 2; main = dx; cross = Math.abs(rc.y - hy) * 3.5; }
      else if (dir === 'left') { ok = r.right <= f.left + 6 || dx < -f.width / 2; main = -dx; cross = Math.abs(rc.y - hy) * 3.5; }
      else if (dir === 'down') { ok = r.top >= f.bottom - 6 || dy > f.height / 2; main = dy; cross = Math.abs(rc.x - vx) * 1.6; }
      else { ok = r.bottom <= f.top + 6 || dy < -f.height / 2; main = -dy; cross = Math.abs(rc.x - vx) * 1.6; }
      if (!ok || main <= 0) continue;
      var score = main + cross;
      if (score < bs) { bs = score; best = el; }
    }
    return best;
  }
  function typing(el) { var t = el && el.tagName; return t === 'INPUT' && !/^(button|checkbox|radio|submit|range)$/i.test(el.type || '') || t === 'TEXTAREA' || (el && el.isContentEditable); }
  function go(el) {
    try { el.focus({ preventScroll: true }); } catch (x) { el.focus(); }
    try { el.scrollIntoView({ block: 'center', inline: 'center', behavior: 'smooth' }); } catch (y) { el.scrollIntoView(); }
  }

  /* ---------- teclas ---------- */
  var KEYS = { 37: 'left', 38: 'up', 39: 'right', 40: 'down' };
  D.addEventListener('keydown', function (e) {
    if (e.defaultPrevented || e.ctrlKey || e.altKey || e.metaKey) return;
    var dir = KEYS[e.keyCode], a = D.activeElement;
    if (dir) {
      if (typing(a) && (dir === 'left' || dir === 'right')) return;       // mover el cursor del texto
      if (a && (a.tagName === 'SELECT' || a.tagName === 'VIDEO' || a.tagName === 'INPUT' && a.type === 'range')) return;
      var v = D.querySelector('video');
      if (a && a.closest && a.closest('.plyr,.cfp-player') && (dir === 'left' || dir === 'right') && v && !v.paused) return; // buscar en el vídeo
      var c0 = a && a !== D.body && a !== D.documentElement && visible(a) ? a : null, nx;
      if (!c0) { var l = list(); nx = l.length ? l[0] : null; keepX = keepY = null; }
      else {
        if (keepX === null || dir === 'left' || dir === 'right') { keepX = center(c0.getBoundingClientRect()).x; }
        if (keepY === null || dir === 'up' || dir === 'down') { keepY = center(c0.getBoundingClientRect()).y; }
        nx = pick(c0, dir);
      }
      if (nx) {
        e.preventDefault();
        var nr = center(nx.getBoundingClientRect());
        if (dir === 'left' || dir === 'right') { keepX = nr.x; } else { keepY = nr.y; }
        go(nx);
      } else if (dir === 'down' || dir === 'up') {
        e.preventDefault(); window.scrollBy({ top: dir === 'down' ? window.innerHeight * 0.6 : -window.innerHeight * 0.6, behavior: 'smooth' });
      } else { e.preventDefault(); }
      return;
    }
    if ((e.keyCode === 13 || e.keyCode === 23) && a && a !== D.body && !typing(a)) {      // OK sobre algo que no es enlace/botón nativo
      var t = a.tagName; if (t !== 'A' && t !== 'BUTTON' && t !== 'INPUT' && t !== 'SELECT' && t !== 'TEXTAREA' && t !== 'SUMMARY') { e.preventDefault(); a.click(); }
    }
  }, false);

  /* ---------- foco inicial y en ventanas emergentes ---------- */
  function firstFocus() {
    var a = D.activeElement;
    if (a && a !== D.body && a !== D.documentElement && visible(a)) return;
    var pref = D.querySelector('.hero a.btn,.hero a[href*="/movie/"],.hero a[href*="/series/"],.hero button,a.btn-play,a.play,#row-recent a.cd,.rail a.cd');
    var el = pref && visible(pref) ? pref : (list()[0] || null);
    if (el) { try { el.focus({ preventScroll: true }); } catch (x) { el.focus(); } }
  }
  var t1 = null;
  function onMut() {
    clearTimeout(t1);
    t1 = setTimeout(function () {
      var ov = D.querySelector('[role=dialog][aria-modal=true],dialog[open],.afp-ov,.afc-ov,.mm-modal.open');
      if (ov && !ov.contains(D.activeElement)) { var l = list(); if (l.length) { try { l[0].focus({ preventScroll: true }); } catch (x) { l[0].focus(); } } }
      else if (!ov) { var a = D.activeElement; if (!a || a === D.body || a === D.documentElement) firstFocus(); }
    }, 350);
  }
  try { new MutationObserver(onMut).observe(D.body, { childList: true, subtree: true }); } catch (x) { }
  setTimeout(firstFocus, 500); setTimeout(firstFocus, 2000);

  /* ---------- pantalla completa automática al dar play (como cualquier app de TV) ---------- */
  D.addEventListener('play', function (e) {
    var v = e.target; if (!v || v.tagName !== 'VIDEO' || window.__afNoAuto) return;
    setTimeout(function () {
      if (D.fullscreenElement || D.webkitFullscreenElement || v.paused) return;
      var c = (v.closest && v.closest('.plyr')) || v;
      var f = c.requestFullscreen || c.webkitRequestFullscreen || c.webkitRequestFullScreen;
      if (f) { try { var p = f.call(c); if (p && p.catch) p.catch(function () { }); } catch (x) { } }
    }, 150);
  }, true);
})();
