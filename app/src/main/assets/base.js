/* ArkaFlix TV: ajustes visuales para cualquier modo (web o /tv): sin recuadro amarillo/naranja del WebView al pulsar o enfocar. */
(function () {
  if (window.__afBase) return; window.__afBase = true;
  var st = document.createElement('style');
  st.id = 'af-base-css';
  st.textContent = '*{-webkit-tap-highlight-color:rgba(0,0,0,0)!important;-webkit-touch-callout:none}'
    + 'a,button,input,select,textarea,summary,[tabindex],[role=button]{-webkit-tap-highlight-color:rgba(0,0,0,0)!important;outline-color:transparent}'
    + '::selection{background:transparent}input::selection,textarea::selection{background:rgba(229,9,20,.45)}'
    + 'html{-webkit-user-select:none;user-select:none}input,textarea{-webkit-user-select:text;user-select:text}';
  (document.head || document.documentElement).appendChild(st);
})();
