package com.terry.home;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HomeActivity extends Activity {

    /** One dock item: a white logo that opens an app (first installed package wins) or a web address. */
    static final class Tile {
        final String label;
        final String[] packages;
        final String url;
        final boolean isHomeApp;
        final int logo, logoW, logoH; // logo size in dp, from equal visual weight (assets-src/logos)
        final int color;               // brand colour for the glow
        final String[] taglines;       // rotate each time you come back home

        Tile(String label, String url, boolean isHomeApp, int logo, int logoW, int logoH, int color,
             String[] taglines, String... packages) {
            this.label = label;
            this.url = url;
            this.isHomeApp = isHomeApp;
            this.logo = logo;
            this.logoW = logoW;
            this.logoH = logoH;
            this.color = color;
            this.taglines = taglines;
            this.packages = packages;
        }
    }

    static final Tile[] TILES = {
            new Tile("YouTube", null, false, R.drawable.logo_youtube, 106, 25, 0xFFFF0033,
                    new String[]{"Just one more video.", "Down the rabbit hole."},
                    "com.google.android.youtube.tv", "com.google.android.youtube"),
            new Tile("Netflix", null, false, R.drawable.logo_netflix, 110, 30, 0xFFE50914,
                    new String[]{"Are you still watching?", "Tonight, something new."},
                    "com.netflix.ninja", "com.netflix.mediaclient"),
            new Tile("Flystream", "https://flystream.net", false, R.drawable.logo_flystream, 150, 32, 0xFF8E3CF7,
                    new String[]{"Leave the gun. Take the cannoli.", "An offer you can't refuse."}),
            new Tile("TED", null, false, R.drawable.logo_ted, 78, 29, 0xFFEB0028,
                    new String[]{"Ideas worth spreading.", "Eighteen minutes to a new idea."},
                    "com.ted.android.tv", "com.ted.android"),
            new Tile("Google TV", null, true, R.drawable.logo_googletv, 150, 30, 0xFF4285F4,
                    new String[]{"Everything else, in one place."},
                    "com.google.android.tvlauncher", "com.google.android.apps.tv.launcherx"),
    };

    private static final float LOGO_SCALE = 0.78f;

    private TextView greeting, time, ampm, tagline, weatherTemp, quoteText, quoteAuthor;
    private View quoteBox;
    private View weatherBox, glow;
    private WeatherIconView weatherIcon;
    private LinearLayout tilesRow;
    private final List<View> dock = new ArrayList<>();
    private View lastDockFocus;
    private Typeface outfitLight, outfitRegular;
    private int glowColor = 0x00000000;
    private ValueAnimator glowAnim;
    private int taglineRound; // which line of each app's taglines to show this time home
    private View pageHome, pageFav, pageMood, dim;
    private View[] pages;
    private Favourites favourites;
    private MoodDeck moodDeck;
    private int page; // 0 home (dock), 1 My Favourites, 2 mood cards

    private final BroadcastReceiver clockReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { updateClock(); maybeFetchWeather(); }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        greeting = findViewById(R.id.greeting);
        time = findViewById(R.id.time);
        ampm = findViewById(R.id.ampm);
        tagline = findViewById(R.id.tagline);
        weatherBox = findViewById(R.id.weather);
        weatherIcon = findViewById(R.id.weather_icon);
        weatherTemp = findViewById(R.id.weather_temp);
        glow = findViewById(R.id.glow);
        tilesRow = findViewById(R.id.tiles);
        quoteBox = findViewById(R.id.quote);
        quoteText = findViewById(R.id.quote_text);
        quoteAuthor = findViewById(R.id.quote_author);

        outfitLight = outfit(250);
        outfitRegular = outfit(400);
        time.setTypeface(outfitLight);
        ampm.setTypeface(outfitRegular);
        greeting.setTypeface(outfitRegular);
        quoteText.setTypeface(outfitLight);
        quoteAuthor.setTypeface(outfitRegular);
        tagline.setTypeface(outfitLight);
        weatherTemp.setTypeface(outfitLight);

        // Soft shadow keeps the text readable on any wallpaper.
        float dp = getResources().getDisplayMetrics().density;
        for (TextView tv : new TextView[]{greeting, time, ampm, tagline, weatherTemp, quoteText, quoteAuthor}) tv.setShadowLayer(8 * dp, 0, 1 * dp, 0xCC000000);

        findViewById(R.id.btn_remote).setOnClickListener(v -> openSettings(Settings.ACTION_BLUETOOTH_SETTINGS));
        findViewById(R.id.btn_wifi).setOnClickListener(v -> openSettings(Settings.ACTION_WIFI_SETTINGS));
        findViewById(R.id.btn_settings).setOnClickListener(v -> openSettings(Settings.ACTION_SETTINGS));
        // Down from the top-right icons goes back to the dock item you were on.
        View.OnKeyListener downToDock = (v, code, e) -> {
            if (code == KeyEvent.KEYCODE_DPAD_DOWN && e.getAction() == KeyEvent.ACTION_DOWN && lastDockFocus != null) {
                lastDockFocus.requestFocus();
                return true;
            }
            return false;
        };
        for (int id : new int[]{R.id.btn_remote, R.id.btn_wifi, R.id.btn_settings}) findViewById(id).setOnKeyListener(downToDock);

        pageHome = findViewById(R.id.page_home);
        pageFav = findViewById(R.id.page_fav);
        dim = findViewById(R.id.dim);
        TextView favTitle = findViewById(R.id.fav_title), favCharacter = findViewById(R.id.fav_character), favSection = findViewById(R.id.fav_section);
        favTitle.setTypeface(outfitLight);
        favCharacter.setTypeface(outfitRegular);
        favSection.setTypeface(outfitRegular);
        for (TextView tv : new TextView[]{favTitle, favCharacter, favSection}) tv.setShadowLayer(8 * dp, 0, 1 * dp, 0xCC000000);
        SceneFader scenes = new SceneFader(this, findViewById(R.id.scene_a), findViewById(R.id.scene_b));
        favourites = new Favourites(this, findViewById(R.id.fav_stage), findViewById(R.id.fav_glow), favTitle, favCharacter, scenes);
        pageMood = findViewById(R.id.page_mood);
        moodDeck = new MoodDeck(this, findViewById(R.id.page_mood), scenes, outfitLight, outfitRegular);
        pages = new View[]{pageHome, pageFav, pageMood};
        pageFav.setFocusable(true);
        pageMood.setFocusable(true);

        handleExtras(getIntent());
        loadWallpaperAsync();
        buildTiles();
    }

    // ---- pages: home (dock) and My Favourites ------------------------------

    private void showFavourites() { showPage(1); }

    private void showHome() { showPage(0); }

    /** Pages stack vertically: Down moves to the next, Up to the previous. The wallpaper darkens as you go down. */
    private void showPage(int target) {
        if (target == page) return;
        int from = page;
        page = target;
        float h = pageHome.getHeight(), dir = target > from ? 1 : -1;
        View out = pages[from], in = pages[target];
        out.animate().translationY(-dir * h * 0.12f).alpha(0f).setDuration(240)
                .withEndAction(() -> { if (page != indexOf(out)) out.setVisibility(View.INVISIBLE); }).start();
        in.setVisibility(View.VISIBLE);
        in.setTranslationY(dir * h * 0.12f);
        in.animate().translationY(0).alpha(1f).setDuration(300).start();
        dim.animate().alpha(target == 0 ? 0f : target == 1 ? 0.35f : 0.5f).setDuration(300).start();

        if (from == 1 && favourites.selected() >= 0) prefs().edit().putInt("last_fav", favourites.selected()).apply();
        if (from == 2) moodDeck.onHide();
        if (target == 0) {
            favourites.hideScene();
            if (lastDockFocus != null) lastDockFocus.requestFocus(); else focusLastUsed();
        } else if (target == 1) {
            pageFav.requestFocus();
            int sel = favourites.selected() >= 0 ? favourites.selected() : prefs().getInt("last_fav", 0);
            favourites.select(Math.max(0, Math.min(sel, Favourites.FAVS.length - 1)));
        } else {
            pageMood.requestFocus();
            moodDeck.onShow();
        }
    }

    private int indexOf(View v) {
        for (int i = 0; i < pages.length; i++) if (pages[i] == v) return i;
        return -1;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (page == 0) return super.dispatchKeyEvent(e);
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        boolean nav = code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN || code == KeyEvent.KEYCODE_DPAD_LEFT
                || code == KeyEvent.KEYCODE_DPAD_RIGHT || code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_BACK;
        if (!nav) return super.dispatchKeyEvent(e);
        if (code == KeyEvent.KEYCODE_NUMPAD_ENTER) code = KeyEvent.KEYCODE_ENTER;

        if (page == 1) {
            if (code == KeyEvent.KEYCODE_BACK) { if (!down) showHome(); return true; }
            if (!down) return true;
            if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
                if (e.getRepeatCount() == 0) {
                    prefs().edit().putInt("last_fav", favourites.selected()).apply();
                    favourites.open();
                }
                return true;
            }
            int r = favourites.move(code);
            if (r < 0) showPage(0); else if (r > 0) showPage(2);
            return true;
        }

        // page 2: mood cards
        if (code == KeyEvent.KEYCODE_BACK) {
            if (down) return true;
            if (moodDeck.busy()) moodDeck.key(KeyEvent.KEYCODE_BACK); else showPage(1);
            return true;
        }
        if (!down || e.getRepeatCount() > 0 && code != KeyEvent.KEYCODE_DPAD_LEFT) return true;
        if (!moodDeck.key(code)) showPage(1);
        return true;
    }

    private Typeface outfit(int weight) {
        try {
            return new Typeface.Builder(getAssets(), "fonts/Outfit.ttf").setFontVariationSettings("'wght' " + weight).build();
        } catch (Exception e) {
            return Typeface.create("sans-serif-light", Typeface.NORMAL);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleExtras(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(clockReceiver, f);
        updateClock();
        showWeather();
        maybeFetchWeather();
        showQuote();

        taglineRound = prefs().getInt("tagline_round", 0);
        prefs().edit().putInt("tagline_round", taglineRound + 1).apply();

        refreshInstalled();
        if (page != 0) pages[page].requestFocus(); else focusLastUsed();
        ensureHomeKeyService();
    }

    /**
     * If Android killed the Home-button helper (it marks it "crashed" and never rebinds), switch it off and back on.
     * The helper lives in its own ":keys" process; when that process is gone, or the setting lost our entry, it needs repair.
     * Needs WRITE_SECURE_SETTINGS, granted once with: adb shell pm grant com.terry.home android.permission.WRITE_SECURE_SETTINGS
     */
    private void ensureHomeKeyService() {
        final String id = getPackageName() + "/" + HomeKeyService.class.getName();
        final android.content.ContentResolver cr = getContentResolver();
        final android.app.ActivityManager am = getSystemService(android.app.ActivityManager.class);
        final String keysProcess = getPackageName() + ":keys";
        new Thread(() -> {
            try {
                Thread.sleep(3000); // let a just-started helper finish connecting before judging it
                for (int attempt = 0; attempt < 3; attempt++) {
                    String enabled = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
                    if (enabled != null && enabled.contains(id) && processRunning(am, keysProcess)) return;
                    // Off then on: clears Android's "crashed" mark so it binds the helper again.
                    Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "");
                    Thread.sleep(800);
                    Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, id);
                    Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1);
                    Thread.sleep(2500);
                }
            } catch (SecurityException | InterruptedException ignored) {
                // Permission not granted: nothing we can do from here.
            }
        }, "home-key-check").start();
    }

    private static boolean processRunning(android.app.ActivityManager am, String name) {
        java.util.List<android.app.ActivityManager.RunningAppProcessInfo> procs = am.getRunningAppProcesses();
        if (procs != null) for (android.app.ActivityManager.RunningAppProcessInfo p : procs) {
            if (name.equals(p.processName)) return true;
        }
        return false;
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(clockReceiver);
    }

    @Override
    public void onBackPressed() {
        // This is the home screen: Back has nowhere to go.
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_HOME) {
            if (page != 0) { showHome(); return true; }
            focusLastUsed();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // ---- clock and weather -------------------------------------------------

    private void updateClock() {
        Calendar now = Calendar.getInstance();
        Date d = now.getTime();
        boolean is24 = DateFormat.is24HourFormat(this);
        time.setText(DateFormat.format(is24 ? "HH:mm" : "h:mm", d));
        ampm.setText(is24 ? "" : DateFormat.format("a", d).toString().toUpperCase(Locale.getDefault()));
        ampm.setVisibility(is24 ? View.GONE : View.VISIBLE);

        int h = now.get(Calendar.HOUR_OF_DAY);
        boolean es = "es".equals(Locale.getDefault().getLanguage());
        String part = es
                ? (h < 5 ? "BUENAS NOCHES" : h < 12 ? "BUENOS DÍAS" : h < 19 ? "BUENAS TARDES" : "BUENAS NOCHES")
                : (h < 5 ? "GOOD NIGHT" : h < 12 ? "GOOD MORNING" : h < 17 ? "GOOD AFTERNOON" : h < 21 ? "GOOD EVENING" : "GOOD NIGHT");
        String name = prefs().getString("name", "");
        greeting.setText(name.isEmpty() ? part : part + ", " + name.toUpperCase(Locale.getDefault()));
    }

    private void showWeather() {
        int[] w = Weather.cached(prefs());
        if (w == null) { weatherBox.setVisibility(View.GONE); return; }
        weatherTemp.setText(w[0] + "°");
        weatherIcon.set(w[1], w[2] == 1);
        weatherBox.setVisibility(View.VISIBLE);
    }

    /** Only while the home screen is showing, and at most every 30 minutes. */
    private void maybeFetchWeather() {
        SharedPreferences p = prefs();
        if (!Weather.due(p)) return;
        p.edit().putLong("w_at", System.currentTimeMillis()).apply(); // don't retry every minute if offline
        new Thread(() -> { if (Weather.fetch(p)) runOnUiThread(this::showWeather); }, "weather").start();
    }

    private SharedPreferences prefs() {
        return getSharedPreferences("home", MODE_PRIVATE);
    }

    /**
     * adb shell am start -n com.terry.home/.HomeActivity --es name 'Your Name'
     * adb shell am start -n com.terry.home/.HomeActivity --es lat 40.71 --es lon -74.01
     */
    private void handleExtras(Intent intent) {
        if (intent == null) return;
        SharedPreferences.Editor e = prefs().edit();
        if (intent.hasExtra("name")) e.putString("name", intent.getStringExtra("name").trim());
        if (intent.hasExtra("lat") && intent.hasExtra("lon")) {
            e.putString("lat", intent.getStringExtra("lat").trim()).putString("lon", intent.getStringExtra("lon").trim()).putLong("w_at", 0);
        }
        // Morning song: adb shell am start -n com.terry.home/.HomeActivity --es song '<YouTube link or video id>' --es song_time 07:00
        if (intent.hasExtra("song")) e.putString("song", youtubeId(intent.getStringExtra("song")));
        if (intent.hasExtra("song_time")) e.putString("song_time", intent.getStringExtra("song_time").trim());
        e.apply();
        if (intent.getBooleanExtra("wake", false)) {
            intent.removeExtra("wake"); // a later recreate of the activity must not count as another wake-up
            maybeMorningSong();
        }
        if (time != null) { updateClock(); maybeFetchWeather(); }
    }

    // ---- morning song ------------------------------------------------------

    /** The TV just woke: if it's around the morning time and the song hasn't played today, open it in YouTube. */
    private void maybeMorningSong() {
        SharedPreferences p = prefs();
        String id = p.getString("song", "");
        if (id.isEmpty()) return;
        Calendar now = Calendar.getInstance();
        String today = DateFormat.format("yyyy-MM-dd", now).toString();
        if (today.equals(p.getString("song_day", ""))) return; // once a day
        int[] hm = parseTime(p.getString("song_time", "07:00"));
        int nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE), at = hm[0] * 60 + hm[1];
        if (nowMin < at - 10 || nowMin > at + 45) return; // only when the TV wakes around that time
        p.edit().putString("song_day", today).apply();
        tilesRow.postDelayed(() -> playOnYouTube(id), 1500); // let the home screen show for a moment first
    }

    private void playOnYouTube(String id) {
        Intent i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com/watch?v=" + id))
                .setPackage("com.google.android.youtube.tv")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Couldn't open YouTube", Toast.LENGTH_SHORT).show();
        }
    }

    private static int[] parseTime(String t) {
        try {
            String[] parts = t.split(":");
            return new int[]{Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())};
        } catch (Exception e) {
            return new int[]{7, 0};
        }
    }

    /** Accepts a full YouTube link (watch?v=, youtu.be/, /shorts/) or a bare 11-character video id. */
    static String youtubeId(String s) {
        s = s.trim();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?:v=|youtu\\.be/|/shorts/|/embed/)([A-Za-z0-9_-]{11})").matcher(s);
        if (m.find()) return m.group(1);
        return s.matches("[A-Za-z0-9_-]{11}") ? s : "";
    }

    // ---- daily quote -------------------------------------------------------

    private List<String> quotes;

    /**
     * One quote per day, rotating through /sdcard/Android/data/com.terry.home/files/quotes.txt
     * (one quote per line; an optional " — Author" at the end of a line overrides the author).
     */
    private void showQuote() {
        if (quotes == null) {
            quotes = new ArrayList<>();
            File dir = getExternalFilesDir(null);
            File f = dir == null ? null : new File(dir, "quotes.txt");
            if (f != null && f.exists()) {
                try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(f), "UTF-8"))) {
                    for (String line; (line = r.readLine()) != null; ) if (!line.trim().isEmpty()) quotes.add(line.trim());
                } catch (Exception ignored) { }
            }
        }
        if (quotes.isEmpty()) { quoteBox.setVisibility(View.GONE); return; }
        // Day of the year: January 1 shows the first quote, and each day moves to the next.
        int day = Calendar.getInstance().get(Calendar.DAY_OF_YEAR) - 1;
        String q = quotes.get(Math.floorMod(day, quotes.size()));
        String author = prefs().getString("quote_author", "Jim Rohn");
        int dash = Math.max(q.lastIndexOf(" — "), q.lastIndexOf(" - "));
        if (dash > 0 && q.length() - dash < 40) { author = q.substring(dash + 3).trim(); q = q.substring(0, dash).trim(); }
        if (!q.startsWith("\u201C") && !q.startsWith("\"")) q = "\u201C" + q + "\u201D";
        quoteText.setText(q);
        quoteText.setTextSize(q.length() > 220 ? 12 : 14); // long ones a size smaller
        quoteAuthor.setText("— " + author);
        quoteBox.setVisibility(View.VISIBLE);
    }

    // ---- wallpaper ---------------------------------------------------------

    /** Uses /sdcard/Android/data/com.terry.home/files/wallpaper.jpg when present. */
    private void loadWallpaperAsync() {
        File dir = getExternalFilesDir(null);
        if (dir == null) return;
        File file = new File(dir, "wallpaper.jpg");
        if (!file.exists()) return;
        ImageView wall = findViewById(R.id.wallpaper);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int targetW = Math.max(dm.widthPixels, 1280);
        new Thread(() -> {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= targetW) sample *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = sample;
            o.inPreferredConfig = Bitmap.Config.RGB_565; // half the memory, no visible difference on a photo
            Bitmap bmp = BitmapFactory.decodeFile(file.getPath(), o);
            if (bmp != null) wall.post(() -> wall.setImageBitmap(bmp));
        }, "wallpaper").start();
    }

    // ---- dock --------------------------------------------------------------

    private void buildTiles() {
        tilesRow.removeAllViews();
        dock.clear();
        float dp = getResources().getDisplayMetrics().density;
        for (Tile t : TILES) {
            View item = makeDockItem(t, dp);
            item.setId(View.generateViewId());
            tilesRow.addView(item);
            dock.add(item);
        }
        refreshInstalled();
    }

    /** Hide items whose app isn't on the TV, then wire navigation across the visible ones. */
    private void refreshInstalled() {
        PackageManager pm = getPackageManager();
        List<View> visible = new ArrayList<>();
        for (int i = 0; i < dock.size(); i++) {
            Tile t = TILES[i];
            View item = dock.get(i);
            boolean present = t.url != null || firstInstalled(pm, t.packages) != null;
            item.setVisibility(present ? View.VISIBLE : View.GONE);
            if (present) visible.add(item);
        }
        for (int i = 0; i < visible.size(); i++) {
            View v = visible.get(i);
            v.setNextFocusUpId(R.id.btn_remote);                                          // Up: the top-right icons
            v.setNextFocusLeftId(i == 0 ? v.getId() : visible.get(i - 1).getId());        // Left/Right stop at the ends
            v.setNextFocusRightId(i == visible.size() - 1 ? v.getId() : visible.get(i + 1).getId());
            v.setNextFocusDownId(v.getId());
        }
    }

    /** Home lands on the app you used last. */
    private void focusLastUsed() {
        String last = prefs().getString("last", null);
        View target = null;
        for (View v : dock) {
            if (v.getVisibility() != View.VISIBLE) continue;
            if (target == null) target = v;
            if (last != null && last.equals(v.getTag())) { target = v; break; }
        }
        if (target != null) target.requestFocus();
    }

    private static String firstInstalled(PackageManager pm, String[] pkgs) {
        for (String p : pkgs) {
            try {
                if (pm.getApplicationInfo(p, 0).enabled) return p;
            } catch (PackageManager.NameNotFoundException ignored) { }
        }
        return null;
    }

    private View makeDockItem(Tile t, float dp) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setClipChildren(false);
        col.setFocusable(true);
        col.setTag(t.label);
        LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        colLp.leftMargin = colLp.rightMargin = (int) (20 * dp);
        col.setLayoutParams(colLp);

        // The logo sits in a fixed-height slot so logos of different heights share one baseline row.
        int slotH = (int) (48 * dp);
        LinearLayout slot = new LinearLayout(this);
        slot.setGravity(Gravity.CENTER);
        slot.setClipChildren(false);
        ImageView logo = new ImageView(this);
        logo.setImageResource(t.logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER); // never stretched
        logo.setAlpha(0.6f);
        slot.addView(logo, new LinearLayout.LayoutParams((int) (t.logoW * LOGO_SCALE * dp), (int) (t.logoH * LOGO_SCALE * dp)));
        col.addView(slot, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, slotH));

        TextView name = new TextView(this);
        name.setText(t.label);
        name.setTextColor(0xCCFFFFFF);
        name.setTextSize(11);
        name.setLetterSpacing(0.08f);
        name.setTypeface(outfitRegular);
        name.setAlpha(0f);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nameLp.topMargin = (int) (8 * dp);
        col.addView(name, nameLp);

        DecelerateInterpolator ease = new DecelerateInterpolator(1.6f);
        col.setOnFocusChangeListener((v, has) -> {
            logo.animate().scaleX(has ? 1.28f : 1f).scaleY(has ? 1.28f : 1f).alpha(has ? 1f : 0.6f)
                    .setInterpolator(ease).setDuration(180).start();
            name.animate().alpha(has ? 1f : 0f).translationY(has ? 0 : -4 * dp).setDuration(180).start();
            if (has) {
                lastDockFocus = v;
                showTagline(t);
                glowTo(t.color);
            }
        });

        col.setOnClickListener(v -> open(t));
        col.setOnKeyListener((v, code, e) -> {
            if (code == KeyEvent.KEYCODE_DPAD_DOWN && e.getAction() == KeyEvent.ACTION_DOWN) { showFavourites(); return true; }
            return false;
        });
        return col;
    }

    private void showTagline(Tile t) {
        String line = t.taglines[Math.floorMod(taglineRound, t.taglines.length)];
        if (line.contentEquals(tagline.getText())) return;
        tagline.animate().cancel();
        tagline.animate().alpha(0f).setDuration(90).withEndAction(() -> {
            tagline.setText(line);
            tagline.animate().alpha(1f).setDuration(160).start();
        }).start();
    }

    /** Soft glow behind the dock in the focused app's brand colour. */
    private void glowTo(int color) {
        if (glowAnim != null) glowAnim.cancel();
        int from = glowColor;
        glowAnim = ValueAnimator.ofObject(new ArgbEvaluator(), from, color);
        glowAnim.setDuration(260);
        glowAnim.addUpdateListener(a -> {
            glowColor = (int) a.getAnimatedValue();
            GradientDrawable g = new GradientDrawable();
            g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
            g.setGradientRadius(glow.getWidth() * 0.5f); // square view squashed by scaleY=0.3 in the layout: a soft, wide oval
            g.setColors(new int[]{(glowColor & 0x00FFFFFF) | 0x66000000, (glowColor & 0x00FFFFFF) | 0x22000000, 0x00000000});
            glow.setBackground(g);
        });
        glowAnim.start();
    }

    private void open(Tile t) {
        prefs().edit().putString("last", t.label).apply();
        try {
            if (t.url != null) {
                startActivity(new Intent(this, WebActivity.class).putExtra(WebActivity.EXTRA_URL, t.url));
                return;
            }
            PackageManager pm = getPackageManager();
            String pkg = firstInstalled(pm, t.packages);
            Intent i;
            if (t.isHomeApp) {
                i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(pkg);
            } else {
                i = pm.getLeanbackLaunchIntentForPackage(pkg);
                if (i == null) i = pm.getLaunchIntentForPackage(pkg);
            }
            if (i == null) throw new IllegalStateException("no launch intent for " + pkg);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Couldn't open " + t.label, Toast.LENGTH_SHORT).show();
        }
    }

    private void openSettings(String action) {
        try {
            startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) { }
        }
    }
}
