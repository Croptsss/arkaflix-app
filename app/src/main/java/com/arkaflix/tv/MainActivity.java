package com.arkaflix.tv;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;

/**
 * ArkaFlix para TV. Dos modos (se cambia con una pulsación LARGA de Atrás, o con Menú, y se recuerda):
 *  - WEB: arkaflix.com completa + navegación con flechas/OK que añade la app (assets/dpad.js).
 *  - TV : arkaflix.com/tv (versión pensada para mando).
 * Novedades 1.3: pantalla completa de vídeo nativa (el botón ya no «sube» el reproductor), mando durante el vídeo, logo propio,
 * foco sin recuadro amarillo, cookies de terceros, informe en pantalla si algo falla.
 */
public class MainActivity extends Activity {
    private WebView web;
    private FrameLayout root;
    private SharedPreferences prefs;
    private String dpadJs = "", baseJs = "";
    private boolean longBack = false;
    private final Handler h = new Handler(Looper.getMainLooper());
    private boolean failed = false;
    private long lastBack = 0;
    private View splash;
    private TextView diag;
    private boolean everFinished = false, fellBack = false, cacheRetried = false;
    private final ArrayList<String> errs = new ArrayList<String>();
    private int loadToken = 0;
    private int crashes = 0;
    private long lastCrash = 0;

    // Pantalla completa de vídeo
    private View customView;
    private WebChromeClient.CustomViewCallback customCb;
    private FrameLayout fsHolder;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = getSharedPreferences("arkaflix", MODE_PRIVATE);
        dpadJs = readAsset("dpad.js");
        baseJs = readAsset("base.js");
        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Pantalla de carga con el logo
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(Color.parseColor("#0b0b0e"));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int px = (int) (200 * getResources().getDisplayMetrics().density);
        box.addView(logo, new LinearLayout.LayoutParams(px, px));
        TextView loading = new TextView(this);
        loading.setText("Cargando…");
        loading.setTextColor(Color.parseColor("#9a9aa3"));
        loading.setTextSize(20);
        loading.setGravity(Gravity.CENTER);
        loading.setPadding(0, 24, 0, 0);
        box.addView(loading, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        splash = box;
        root.addView(splash, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Informe de errores (solo se ve si algo falla)
        diag = new TextView(this);
        diag.setTextColor(Color.WHITE);
        diag.setTextSize(17);
        diag.setTypeface(Typeface.MONOSPACE);
        diag.setPadding(60, 50, 60, 50);
        diag.setBackgroundColor(Color.parseColor("#F2000000"));
        diag.setVisibility(View.GONE);
        root.addView(diag, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);                       // la TV guarda su vínculo en localStorage
        s.setMediaPlaybackRequiresUserGesture(false);       // el vídeo arranca solo
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);   // vídeo/imagen http dentro de la página https
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setTextZoom(100);
        s.setUserAgentString(s.getUserAgentString() + " ArkaFlixTV/1.3 AndroidTV");
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);           // los reproductores incrustados guardan sus ajustes

        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onConsoleMessage(ConsoleMessage m) {
                if (m.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                    String src = m.sourceId() == null ? "" : m.sourceId();
                    int i = src.lastIndexOf('/');
                    note("JS: " + m.message() + " (" + (i >= 0 ? src.substring(i + 1) : src) + ":" + m.lineNumber() + ")");
                }
                return false;
            }
            // Pantalla completa del vídeo: sin esto el botón solo «sube» el reproductor a lo alto de la página
            @Override public void onShowCustomView(View view, CustomViewCallback cb) { showCustom(view, cb); }
            @Override public void onHideCustomView() { hideCustom(); }
            @Override public Bitmap getDefaultVideoPoster() { return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888); }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView v, String url) {
                if (url == null || url.startsWith("data:") || url.equals("about:blank")) return;   // la pantalla «Sin conexión» no cuenta
                failed = false;
                everFinished = true;
                splash.setVisibility(View.GONE);
                checkBlank();
                v.evaluateJavascript(baseJs, null);                // sin recuadro amarillo en ningún modo
                // En modo web se añade la navegación con flechas; la versión /tv ya trae la suya
                String path = android.net.Uri.parse(url).getPath();
                boolean tvPage = path != null && (path.equals("/tv") || path.startsWith("/tv/"));
                if (isWebMode() && !tvPage) { v.evaluateJavascript(dpadJs, null); }
            }
            @Override public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) {
                    note("Red: " + (Build.VERSION.SDK_INT >= 23 ? e.getErrorCode() + " " + e.getDescription() : "error al cargar"));
                    offline();
                }
            }
            @Override public void onReceivedHttpError(WebView v, WebResourceRequest r, WebResourceResponse resp) {
                if (r.isForMainFrame() && resp != null && resp.getStatusCode() >= 500) {
                    note("Servidor: HTTP " + resp.getStatusCode());
                    offline();
                }
            }
            @Override public void onReceivedSslError(WebView v, SslErrorHandler handler, SslError e) {
                handler.cancel();
                note("Certificado SSL rechazado (" + e.getPrimaryError() + "). Casi siempre es la FECHA Y HORA de la tele: ponla en automatico en Ajustes.");
                showDiag("No se pudo abrir la conexion segura.");
                offline();
            }
            @Override public boolean onRenderProcessGone(WebView v, RenderProcessGoneDetail d) {
                long now = System.currentTimeMillis();
                crashes = (now - lastCrash < 60000) ? crashes + 1 : 1;
                lastCrash = now;
                if (crashes > 2) {
                    Toast.makeText(MainActivity.this, "El motor web falla una y otra vez. Actualiza «Android System WebView» en la Play Store.", Toast.LENGTH_LONG).show();
                    finish();
                } else { recreate(); }
                return true;
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                if (!r.isForMainFrame()) return false;             // lo que pase dentro de un reproductor incrustado no se toca
                // Solo se navega dentro de arkaflix.com; cualquier otro enlace se ignora
                String host = r.getUrl().getHost();
                return host == null || !(host.equals("arkaflix.com") || host.endsWith(".arkaflix.com"));
            }
        });
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        web.requestFocus();
        immersive();
        if (b == null) { web.loadUrl(startUrl()); } else { web.restoreState(b); }
        watchdog();
    }

    // ---------- pantalla completa de vídeo ----------
    private void showCustom(View view, WebChromeClient.CustomViewCallback cb) {
        if (customView != null) { try { cb.onCustomViewHidden(); } catch (Throwable t) { } return; }
        customView = view;
        customCb = cb;
        fsHolder = new FrameLayout(this);
        fsHolder.setBackgroundColor(Color.BLACK);
        fsHolder.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(fsHolder, 1, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        web.setVisibility(View.INVISIBLE);
        immersive();
    }

    private void hideCustom() {
        if (customView == null) return;
        try { root.removeView(fsHolder); } catch (Throwable t) { }
        fsHolder = null;
        customView = null;
        web.setVisibility(View.VISIBLE);
        web.requestFocus();
        immersive();
    }

    /** Sale de la pantalla completa por orden del usuario (Atrás): pausa el vídeo, como hacen las apps de TV. */
    private void exitFullscreen() {
        if (customView == null) return;
        WebChromeClient.CustomViewCallback cb = customCb;
        customCb = null;
        runJs("var v=document.querySelector('video');if(v){v.pause()}");
        hideCustom();
        if (cb != null) { try { cb.onCustomViewHidden(); } catch (Throwable t) { } }
    }

    private void runJs(String js) { try { web.evaluateJavascript("(function(){try{" + js + "}catch(e){}})()", null); } catch (Throwable t) { } }
    private void videoToggle() { runJs("var v=document.querySelector('video');if(v){if(v.paused){v.play()}else{v.pause()}}"); }
    private void videoSeek(int sec) { runJs("var v=document.querySelector('video');if(v){v.currentTime=Math.max(0,Math.min(v.duration||1e9,v.currentTime+(" + sec + ")))}"); }

    // ---------- avisos y diagnóstico ----------
    /** Si en 25 s la web no ha terminado de cargar, se enseña un informe en pantalla en vez de dejarla negra. */
    private void watchdog() {
        final int t = ++loadToken;
        h.postDelayed(new Runnable() { @Override public void run() {
            if (t == loadToken && !everFinished) { note("La pagina no termino de cargar en 25 s."); showDiag("La web tarda demasiado."); }
        } }, 25000);
    }

    /** Página «cargada» pero vacía o con caracteres corruptos: se detecta; en blanco y modo web se prueba la versión /tv. */
    private void checkBlank() {
        final int t = ++loadToken;
        h.postDelayed(new Runnable() { @Override public void run() {
            if (t != loadToken || web == null) return;
            web.evaluateJavascript("(function(){try{var t=document.body?document.body.innerText:'';return t.length+'|'+document.querySelectorAll('img').length+'|'+document.readyState+'|'+(t.match(/\\uFFFD/g)||[]).length}catch(e){return 'err'}})()", new ValueCallback<String>() {
                @Override public void onReceiveValue(String v) {
                    String x = v == null ? "" : v.replace("\"", "");
                    String[] p = x.split("\\|");
                    boolean blank = false, garbled = false;
                    try {
                        blank = p.length >= 2 && Integer.parseInt(p[0]) < 15 && Integer.parseInt(p[1]) == 0;
                        garbled = p.length >= 4 && Integer.parseInt(p[3]) > 40 && Integer.parseInt(p[3]) * 5 > Integer.parseInt(p[0]);
                    } catch (Exception ex) { blank = false; garbled = false; }
                    String u = web.getUrl() == null ? "" : web.getUrl();
                    if (u.startsWith("data:") || u.equals("about:blank")) return;
                    if (garbled) {
                        note("Pagina con caracteres corruptos (respuesta comprimida sin descomprimir) en " + u);
                        if (!cacheRetried) { cacheRetried = true; web.clearCache(true); web.reload(); watchdog(); }
                        else { showDiag("La pagina llega corrupta desde el servidor."); }
                        return;
                    }
                    if (!blank) return;
                    note("Pagina en blanco (" + x + ") en " + u);
                    if (isWebMode() && !fellBack) {
                        fellBack = true;
                        prefs.edit().putString("mode", "tv").apply();
                        Toast.makeText(MainActivity.this, "La web completa salio en blanco: abriendo la version para TV", Toast.LENGTH_LONG).show();
                        everFinished = false;
                        splash.setVisibility(View.VISIBLE);
                        web.loadUrl(getString(R.string.url_tv));
                        watchdog();
                    } else { showDiag("La pagina salio en blanco."); }
                }
            });
        } }, 5000);
    }

    private void note(String m) {
        String t = m == null ? "" : (m.length() > 170 ? m.substring(0, 170) : m);
        if (!errs.contains(t)) { errs.add(t); if (errs.size() > 7) errs.remove(0); }
    }

    private void showDiag(String why) {
        StringBuilder sb = new StringBuilder();
        sb.append("ARKAFLIX TV  v").append(getString(R.string.app_version)).append("\n").append(why).append("\n\n");
        sb.append("Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(") - ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n");
        String wv = "?";
        try {
            if (Build.VERSION.SDK_INT >= 26) { PackageInfo pi = WebView.getCurrentWebViewPackage(); if (pi != null) wv = pi.packageName + " " + pi.versionName; }
            else { String ua = web.getSettings().getUserAgentString(); int i = ua.indexOf("Chrome/"); if (i >= 0) wv = ua.substring(i, Math.min(ua.length(), i + 20)); }
        } catch (Throwable t) { }
        sb.append("WebView: ").append(wv).append("\nModo: ").append(isWebMode() ? "web" : "tv").append("   URL: ").append(web.getUrl()).append("\n\n");
        if (errs.isEmpty()) sb.append("(sin errores registrados)\n"); else { sb.append("Errores:\n"); for (String e : errs) sb.append(" - ").append(e).append("\n"); }
        sb.append("\nAtras: cerrar este informe    Mantener Atras: cambiar de modo\nEnvia una foto de esta pantalla para poder arreglarlo.");
        diag.setText(sb.toString());
        splash.setVisibility(View.GONE);
        diag.setVisibility(View.VISIBLE);
    }

    /** Sin conexión: pantalla simple y reintento automático cada 5 s. */
    private void offline() {
        if (failed) return;
        failed = true;
        String html = "<html><body style='margin:0;background:#0b0b0e;color:#fff;font-family:sans-serif;display:flex;align-items:center;justify-content:center;height:100vh;text-align:center'>"
                + "<div><div style='font-size:56px;font-weight:800;color:#e50914;letter-spacing:2px'>ARKAFLIX</div>"
                + "<p style='font-size:28px;margin-top:28px'>Sin conexi&oacute;n</p><p style='font-size:20px;color:#999'>Reintentando&hellip;</p></div></body></html>";
        web.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
        h.postDelayed(new Runnable() { @Override public void run() { if (failed) { failed = false; web.loadUrl(startUrl()); } } }, 5000);
    }

    private void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override public void onWindowFocusChanged(boolean f) { super.onWindowFocusChanged(f); if (f) immersive(); }

    private boolean isWebMode() { return "web".equals(prefs.getString("mode", "web")); }
    private String startUrl() { return getString(isWebMode() ? R.string.url_web : R.string.url_tv); }

    private void toggleMode() {
        boolean toTv = isWebMode();
        prefs.edit().putString("mode", toTv ? "tv" : "web").apply();
        Toast.makeText(this, getString(toTv ? R.string.mode_tv : R.string.mode_web), Toast.LENGTH_LONG).show();
        failed = false;
        everFinished = false;
        diag.setVisibility(View.GONE);
        splash.setVisibility(View.VISIBLE);
        web.loadUrl(startUrl());
        watchdog();
    }

    private String readAsset(String name) {
        try (InputStream in = getAssets().open(name)) {
            ByteArrayOutputStream o = new ByteArrayOutputStream(); byte[] buf = new byte[4096]; int n;
            while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
            return o.toString("UTF-8");
        } catch (Exception e) { return ""; }
    }

    // ---------- mando ----------
    // Con vídeo a pantalla completa: OK / Play-Pausa pausa o reanuda, izquierda/derecha (o rebobinar/adelantar) saltan 10 s, Atrás sale.
    // Sin él: Atrás corto retrocede en la web (o, al llegar al inicio, pide pulsar otra vez para salir). Atrás largo / Menú: cambia de modo.
    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (customView != null) {
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                case KeyEvent.KEYCODE_MEDIA_PLAY:
                case KeyEvent.KEYCODE_MEDIA_PAUSE:
                    if (e.getRepeatCount() == 0) videoToggle();
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_MEDIA_REWIND:
                    videoSeek(-10);
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                    videoSeek(10);
                    return true;
                case KeyEvent.KEYCODE_BACK:
                    e.startTracking();
                    return true;
                default:
                    break;
            }
        }
        if (code == KeyEvent.KEYCODE_BACK) { e.startTracking(); return true; }
        if (code == KeyEvent.KEYCODE_MENU) { if (customView == null) toggleMode(); return true; }
        return super.onKeyDown(code, e);
    }

    @Override
    public boolean onKeyLongPress(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_BACK) {
            longBack = true;
            if (customView == null) toggleMode();
            return true;
        }
        return super.onKeyLongPress(code, e);
    }

    @Override
    public boolean onKeyUp(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_BACK) {
            if (longBack) { longBack = false; return true; }
            if (e.isCanceled()) return true;
            if (customView != null) { exitFullscreen(); return true; }
            if (diag.getVisibility() == View.VISIBLE) { diag.setVisibility(View.GONE); return true; }
            // Dentro de /tv la propia página gestiona el vídeo; si no, se retrocede en el historial
            if (web.canGoBack()) { web.goBack(); return true; }
            long now = System.currentTimeMillis();
            if (now - lastBack < 2500) { finish(); }
            else { lastBack = now; Toast.makeText(this, "Pulsa Atrás otra vez para salir (mantén Atrás para cambiar de modo)", Toast.LENGTH_SHORT).show(); }
            return true;
        }
        return super.onKeyUp(code, e);
    }

    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); web.saveState(out); }
    @Override protected void onPause() { super.onPause(); CookieManager.getInstance().flush(); web.onPause(); }
    @Override protected void onResume() { super.onResume(); web.onResume(); immersive(); }
    @Override protected void onDestroy() { h.removeCallbacksAndMessages(null); web.destroy(); super.onDestroy(); }
}
