package com.terry.home;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

/**
 * Full-screen browser for one site (Flystream). The remote's arrows move an on-screen pointer, OK clicks,
 * Back goes back a page. Pop-ups are blocked and the page can't redirect itself to another site.
 */
public class WebActivity extends Activity {

    static final String EXTRA_URL = "url";

    private WebView web;
    private CursorView cursor;
    private FrameLayout root;
    private View fullscreenView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private String allowedHost;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        String url = getIntent().getStringExtra(EXTRA_URL);
        allowedHost = baseHost(Uri.parse(url).getHost());

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        web = new WebView(this);
        cursor = new CursorView(this);
        root.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(cursor, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportMultipleWindows(true); // so onCreateWindow fires and pop-ups can be refused
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        // Desktop layout reads better from the couch than a phone layout.
        s.setUserAgentString(s.getUserAgentString().replace("Mobile", "").replaceAll("Android [^;)]*;?", "X11; Linux x86_64;"));

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                if (!req.isForMainFrame()) return false;
                String host = req.getUrl().getHost();
                // Ad redirects hijack the whole page to another site; only let Flystream itself load up top.
                return host == null || !baseHost(host).equals(allowedHost);
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                return false; // block pop-ups and new tabs
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                fullscreenView = view;
                fullscreenCallback = callback;
                root.addView(view, 1, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                web.setVisibility(View.GONE);
            }

            @Override
            public void onHideCustomView() {
                exitFullscreen();
            }
        });

        web.loadUrl(url);
    }

    private static String baseHost(String host) {
        if (host == null) return "";
        String[] parts = host.split("\\.");
        int n = parts.length;
        return n >= 2 ? parts[n - 2] + "." + parts[n - 1] : host;
    }

    private void exitFullscreen() {
        if (fullscreenView == null) return;
        root.removeView(fullscreenView);
        fullscreenView = null;
        if (fullscreenCallback != null) fullscreenCallback.onCustomViewHidden();
        fullscreenCallback = null;
        web.setVisibility(View.VISIBLE);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;

        if (code == KeyEvent.KEYCODE_BACK) {
            if (!down) return true;
            if (fullscreenView != null) exitFullscreen();
            else if (web.canGoBack()) web.goBack();
            else finish();
            return true;
        }

        // While a video is full screen, let the player handle the remote (play/pause, seek).
        if (fullscreenView != null) return super.dispatchKeyEvent(e);

        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (down) cursor.move(code, e.getRepeatCount(), web);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (down && e.getRepeatCount() == 0) tap(MotionEvent.ACTION_DOWN);
                else if (!down) tap(MotionEvent.ACTION_UP);
                return true;
        }
        return super.dispatchKeyEvent(e);
    }

    private long tapDownTime;

    private void tap(int action) {
        long now = SystemClock.uptimeMillis();
        if (action == MotionEvent.ACTION_DOWN) tapDownTime = now;
        MotionEvent ev = MotionEvent.obtain(tapDownTime, now, action, cursor.x, cursor.y, 0);
        ev.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        web.dispatchTouchEvent(ev);
        ev.recycle();
    }

    @Override
    protected void onPause() {
        super.onPause();
        web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    @Override
    protected void onDestroy() {
        web.destroy();
        super.onDestroy();
        // This activity has its own ":web" process; ending it hands the WebView's memory straight back to the TV.
        if (isFinishing()) android.os.Process.killProcess(android.os.Process.myPid());
    }

    /** White pointer drawn over the page; scrolls the page when pushed against an edge. */
    static final class CursorView extends View {
        float x = -1, y = -1;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float r;

        CursorView(Context c) {
            super(c);
            r = 9 * c.getResources().getDisplayMetrics().density;
            fill.setColor(0xEEFFFFFF);
            ring.setColor(0x99000000);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(r / 3);
            setFocusable(false);
        }

        void move(int code, int repeat, WebView web) {
            if (x < 0) { x = getWidth() / 2f; y = getHeight() / 2f; }
            float oldX = x, oldY = y;
            // A tap moves ~3% of the screen; holding the button speeds up to ~14% per step within half a second.
            float step = getWidth() * (0.03f + Math.min(repeat, 10) * 0.011f);
            float edge = r * 2;
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (y - step < edge && web.canScrollVertically(-1)) web.scrollBy(0, (int) -step * 2);
                    else y = Math.max(r, y - step);
                    break;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (y + step > getHeight() - edge && web.canScrollVertically(1)) web.scrollBy(0, (int) step * 2);
                    else y = Math.min(getHeight() - r, y + step);
                    break;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    x = Math.max(r, x - step);
                    break;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    x = Math.min(getWidth() - r, x + step);
                    break;
            }
            // Redraw only the old and new pointer spots, not the whole screen over the page.
            int pad = (int) (r * 2);
            invalidate((int) oldX - pad, (int) oldY - pad, (int) oldX + pad, (int) oldY + pad);
            invalidate((int) x - pad, (int) y - pad, (int) x + pad, (int) y + pad);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (x < 0) { x = getWidth() / 2f; y = getHeight() / 2f; }
            canvas.drawCircle(x, y, r, fill);
            canvas.drawCircle(x, y, r, ring);
        }
    }
}
