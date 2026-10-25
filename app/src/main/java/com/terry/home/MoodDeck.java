package com.terry.home;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * "Don't have time to browse?": a deck of mood cards. Left throws the card away; Right or OK spins a slot-machine reel
 * of that mood's posters, lands on a random title, counts down "Opening in 3…" and opens it in Netflix. During the
 * countdown OK opens now, Right spins again, Left or Back cancels. Moods and titles come from assets/moods/moods.json.
 */
final class MoodDeck {

    static final class Title { String key, name, id; }
    static final class Mood { String name; int color; List<Title> titles = new ArrayList<>(); }

    private enum State { IDLE, SPINNING, COUNTDOWN }

    private final Activity activity;
    private final FrameLayout root;
    private final SceneFader scenes;
    private final Typeface light, regular;
    private final float dp;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final List<Mood> moods = new ArrayList<>();
    private final List<Integer> order = new ArrayList<>(); // order.get(0) is the top card
    private final Map<String, Bitmap> posters = new HashMap<>();

    private final Card[] cards = new Card[3];  // [0] top, [1] and [2] peek behind
    private View glow;
    private TextView countdownText;
    private View progress, progressTrack;
    private State state = State.IDLE;
    private Title chosen;
    private int glowColor;

    MoodDeck(Activity a, FrameLayout root, SceneFader scenes, Typeface light, Typeface regular) {
        this.activity = a;
        this.root = root;
        this.scenes = scenes;
        this.light = light;
        this.regular = regular;
        this.dp = a.getResources().getDisplayMetrics().density;
        load();
        build();
    }

    // ---- data --------------------------------------------------------------

    private void load() {
        try (InputStream in = activity.getAssets().open("moods/moods.json")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            JSONObject cfg = new JSONObject(out.toString("UTF-8"));
            JSONObject titles = cfg.getJSONObject("titles");
            boolean netflix = Netflix.installed(activity); // titles whose app isn't installed are skipped
            List<Title> all = new ArrayList<>();
            Map<String, Title> byKey = new HashMap<>();
            for (java.util.Iterator<String> it = titles.keys(); it.hasNext(); ) {
                String k = it.next();
                JSONObject t = titles.getJSONObject(k);
                Title tt = new Title();
                tt.key = k;
                tt.name = t.getString("name");
                tt.id = t.getString("id");
                byKey.put(k, tt);
                if (netflix) all.add(tt);
            }
            JSONArray ms = cfg.getJSONArray("moods");
            for (int i = 0; i < ms.length(); i++) {
                JSONObject m = ms.getJSONObject(i);
                Mood mood = new Mood();
                mood.name = m.getString("name");
                mood.color = Color.parseColor(m.getString("color"));
                JSONArray ts = m.getJSONArray("titles");
                if (ts.length() == 0) mood.titles.addAll(all); // "Anything at all"
                else if (netflix) for (int j = 0; j < ts.length(); j++) {
                    Title t = byKey.get(ts.getString(j));
                    if (t != null) mood.titles.add(t);
                }
                if (!mood.titles.isEmpty()) { moods.add(mood); order.add(moods.size() - 1); }
            }
        } catch (Exception ignored) { }
    }

    private Bitmap poster(String key) {
        Bitmap b = posters.get(key);
        if (b != null) return b;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        try (InputStream in = activity.getAssets().open("moods/posters/" + key + ".jpg")) {
            b = BitmapFactory.decodeStream(in, null, o);
        } catch (Exception e) {
            return null;
        }
        posters.put(key, b);
        return b;
    }

    // ---- views -------------------------------------------------------------

    private void build() {
        glow = new View(activity);
        FrameLayout.LayoutParams glp = new FrameLayout.LayoutParams((int) (640 * dp), (int) (640 * dp), Gravity.CENTER);
        glp.topMargin = (int) (30 * dp);
        glow.setScaleY(0.55f);
        root.addView(glow, glp);

        LinearLayout head = new LinearLayout(activity);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView section = text("DON'T HAVE TIME TO BROWSE?", 10, 0x99FFFFFF, regular);
        section.setLetterSpacing(0.22f);
        section.setSingleLine(true);
        TextView hint = text("◀  nope        yes  ▶", 12, 0x99FFFFFF, light);
        section.setGravity(Gravity.CENTER_HORIZONTAL);
        head.addView(section, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-2, -2);
        hlp.topMargin = (int) (8 * dp);
        head.addView(hint, hlp);
        FrameLayout.LayoutParams headLp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        headLp.topMargin = (int) (34 * dp);
        root.addView(head, headLp);

        // Back to front, so the top card draws last.
        for (int k = 2; k >= 0; k--) {
            cards[k] = new Card();
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams((int) (560 * dp), (int) (300 * dp), Gravity.CENTER);
            lp.topMargin = (int) (10 * dp);
            root.addView(cards[k], lp);
        }

        countdownText = text("", 14, 0xE6FFFFFF, light);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        clp.bottomMargin = (int) (48 * dp);
        root.addView(countdownText, clp);
        progressTrack = new View(activity);
        progressTrack.setBackgroundColor(0x33FFFFFF);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams((int) (300 * dp), (int) (3 * dp), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        tlp.bottomMargin = (int) (36 * dp);
        root.addView(progressTrack, tlp);
        progress = new View(activity);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams((int) (300 * dp), (int) (3 * dp), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        plp.bottomMargin = (int) (36 * dp);
        root.addView(progress, plp);
        progress.setPivotX(0);
        hideCountdown();

        bindAll();
    }

    private TextView text(String s, float sp, int color, Typeface tf) {
        TextView t = new TextView(activity);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(tf);
        t.setShadowLayer(8 * dp, 0, dp, 0xCC000000);
        return t;
    }

    /** One mood card: colour fading to near-black, three fanned posters, the mood name and a title count. */
    private final class Card extends FrameLayout {
        final ImageView[] fan = new ImageView[3];
        final TextView name, count;
        final GradientDrawable bg = new GradientDrawable();

        Card() {
            super(activity);
            setClipChildren(false);
            bg.setCornerRadius(20 * dp);
            setBackground(bg);
            setElevation(14 * dp);
            setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), 20 * dp); }
            });

            int pw = (int) (118 * dp), ph = (int) (177 * dp);
            float[] rot = {-10f, 10f, 0f};       // middle poster added last so it sits on top
            float[] dx = {-74f, 74f, 0f};
            for (int i = 0; i < 3; i++) {
                ImageView p = new ImageView(activity);
                p.setScaleType(ImageView.ScaleType.CENTER_CROP);
                p.setOutlineProvider(new ViewOutlineProvider() {
                    @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), 8 * dp); }
                });
                p.setClipToOutline(true);
                p.setElevation((i == 2 ? 10 : 6) * dp);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(pw, ph, Gravity.CENTER_VERTICAL | Gravity.END);
                lp.rightMargin = (int) ((150 - dx[i]) * dp - pw / 2f);
                addView(p, lp);
                p.setRotation(rot[i]);
                fan[i] = p;
            }

            LinearLayout words = new LinearLayout(activity);
            words.setOrientation(LinearLayout.VERTICAL);
            name = text("", 30, Color.WHITE, light);
            count = text("", 12, 0xB3FFFFFF, regular);
            count.setLetterSpacing(0.12f);
            words.addView(name);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
            clp.topMargin = (int) (4 * dp);
            words.addView(count, clp);
            FrameLayout.LayoutParams wlp = new FrameLayout.LayoutParams((int) (250 * dp), -2, Gravity.BOTTOM | Gravity.START);
            wlp.leftMargin = (int) (34 * dp);
            wlp.bottomMargin = (int) (30 * dp);
            addView(words, wlp);
        }

        void bind(Mood m) {
            bg.setOrientation(GradientDrawable.Orientation.TL_BR);
            // Solid (no transparency), so the cards behind never show through: mood colour fading to near-black.
            bg.setColors(new int[]{blend(m.color, 0xFF0C0C0E, 0.1f), blend(m.color, 0xFF0C0C0E, 0.55f), 0xFF0C0C0E});
            name.setText(m.name);
            count.setText((m.titles.size() + (m.titles.size() == 1 ? " TITLE" : " TITLES")).toUpperCase(Locale.getDefault()));
            for (int i = 0; i < 3; i++) {
                Title t = m.titles.get(i % m.titles.size());
                fan[i].setImageBitmap(poster(t.key));
                fan[i].setVisibility(View.VISIBLE);
            }
        }

        void resetFan() {
            float[] rot = {-10f, 10f, 0f};
            for (int i = 0; i < 3; i++) {
                fan[i].animate().cancel();
                fan[i].setRotation(rot[i]);
                fan[i].setScaleX(1f);
                fan[i].setScaleY(1f);
                fan[i].setTranslationY(0);
                fan[i].setTranslationX(0);
                fan[i].setAlpha(1f);
            }
        }
    }

    // ---- deck --------------------------------------------------------------

    private Mood top() { return moods.get(order.get(0)); }

    private void bindAll() {
        if (moods.isEmpty()) return;
        for (int k = 0; k < 3; k++) {
            Card c = cards[k];
            if (k >= order.size()) { c.setVisibility(View.INVISIBLE); continue; }
            c.setVisibility(View.VISIBLE);
            c.bind(moods.get(order.get(k)));
            c.resetFan();
            c.animate().cancel();
            c.setTranslationX(0);
            c.setRotation(0);
            c.setAlpha(k == 0 ? 1f : 0.85f - 0.25f * k);
            c.setScaleX(1f - 0.06f * k);
            c.setScaleY(1f - 0.06f * k);
            c.setTranslationY(-22 * dp * k);
        }
    }

    /** Called when the deck page comes into view. */
    void onShow() {
        if (moods.isEmpty()) return;
        state = State.IDLE;
        hideCountdown();
        bindAll();
        glowTo(top().color);
        scenes.show("moods/backdrops/" + top().titles.get(0).key + ".jpg");
    }

    void onHide() {
        cancel();
        handler.removeCallbacksAndMessages(null);
    }

    /** Left: the top card flies off to the left, tilting and fading; the next one moves up. */
    private void nope() {
        if (order.size() < 2) return;
        Card c = cards[0];
        c.animate().translationX(-900 * dp).rotation(-18).alpha(0f).setInterpolator(new AccelerateInterpolator(1.3f))
                .setDuration(260).withEndAction(() -> {
                    order.add(order.remove(0));
                    bindAll();
                    // the new top card grows into place from the peek position
                    cards[0].setScaleX(0.94f);
                    cards[0].setScaleY(0.94f);
                    cards[0].setTranslationY(-22 * dp);
                    cards[0].animate().scaleX(1f).scaleY(1f).translationY(0).setInterpolator(new DecelerateInterpolator(1.5f)).setDuration(200).start();
                    glowTo(top().color);
                    scenes.show("moods/backdrops/" + top().titles.get(0).key + ".jpg");
                }).start();
    }

    /** Right or OK: spin the reel and land on a random title. */
    private void spin() {
        Mood m = top();
        hideCountdown();
        state = State.SPINNING;
        Card c = cards[0];
        c.resetFan();
        c.fan[0].animate().alpha(0f).translationX(40 * dp).setDuration(160).start();
        c.fan[1].animate().alpha(0f).translationX(-40 * dp).setDuration(160).start();
        ImageView reel = c.fan[2];
        reel.animate().scaleX(1.12f).scaleY(1.12f).setDuration(160).start();

        chosen = m.titles.get(random.nextInt(m.titles.size()));
        // A run of posters that ends on the chosen one; fast at first, slowing down like a slot machine.
        List<Title> seq = new ArrayList<>();
        int steps = 16 + random.nextInt(6);
        List<Title> pool = new ArrayList<>(m.titles);
        while (seq.size() < steps) { Collections.shuffle(pool, random); seq.addAll(pool); }
        seq = new ArrayList<>(seq.subList(0, steps));
        if (seq.size() > 1 && seq.get(seq.size() - 2) == chosen && m.titles.size() > 1) seq.set(seq.size() - 2, pool.get(0) == chosen ? pool.get(pool.size() - 1) : pool.get(0));
        seq.add(chosen);
        long t = 0;
        for (int i = 0; i < seq.size(); i++) {
            Title s = seq.get(i);
            boolean last = i == seq.size() - 1;
            t += (long) (45 * Math.pow(1.16, i));
            handler.postDelayed(() -> {
                reel.setImageBitmap(poster(s.key));
                reel.setTranslationY(-14 * dp);
                reel.animate().translationY(0).setDuration(last ? 260 : 70).setInterpolator(new DecelerateInterpolator()).start();
                if (last) landed();
            }, t);
        }
    }

    private void landed() {
        scenes.show("moods/backdrops/" + chosen.key + ".jpg");
        state = State.COUNTDOWN;
        countdownText.setVisibility(View.VISIBLE);
        progressTrack.setVisibility(View.VISIBLE);
        progress.setVisibility(View.VISIBLE);
        progress.setBackgroundColor(top().color);
        progress.setScaleX(0f);
        progress.animate().scaleX(1f).setInterpolator(new LinearInterpolator()).setDuration(3000).start();
        for (int s = 3; s >= 1; s--) {
            int n = s;
            handler.postDelayed(() -> countdownText.setText(chosen.name + "  ·  Opening in " + n + "…"), (3 - s) * 1000L);
        }
        handler.postDelayed(this::openChosen, 3000);
    }

    private void openChosen() {
        if (chosen == null) return;
        Title t = chosen;
        handler.removeCallbacksAndMessages(null);
        state = State.IDLE;
        hideCountdown();
        cards[0].resetFan();
        cards[0].bind(top());
        Netflix.open(activity, t.id, t.name);
    }

    private void cancel() {
        handler.removeCallbacksAndMessages(null);
        progress.animate().cancel();
        state = State.IDLE;
        chosen = null;
        hideCountdown();
        if (!moods.isEmpty()) {
            cards[0].resetFan();
            cards[0].bind(top());
            scenes.show("moods/backdrops/" + top().titles.get(0).key + ".jpg");
        }
    }

    private void hideCountdown() {
        countdownText.setVisibility(View.INVISIBLE);
        progressTrack.setVisibility(View.INVISIBLE);
        progress.setVisibility(View.INVISIBLE);
    }

    private static int blend(int a, int b, float t) {
        int r = (int) (Color.red(a) * (1 - t) + Color.red(b) * t), g = (int) (Color.green(a) * (1 - t) + Color.green(b) * t),
                bl = (int) (Color.blue(a) * (1 - t) + Color.blue(b) * t);
        return Color.rgb(r, g, bl);
    }

    private void glowTo(int color) {
        glowColor = color;
        GradientDrawable g = new GradientDrawable();
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setGradientRadius(320 * dp);
        g.setColors(new int[]{(color & 0x00FFFFFF) | 0x66000000, (color & 0x00FFFFFF) | 0x1F000000, 0});
        glow.setBackground(g);
        glow.setAlpha(0.4f);
        glow.animate().alpha(1f).setDuration(300).start();
    }

    /** Keys while the deck is showing. Returns false for Up when idle (caller goes back to My Favourites). */
    boolean key(int keyCode) {
        if (moods.isEmpty()) return keyCode != KeyEvent.KEYCODE_DPAD_UP;
        switch (state) {
            case SPINNING:
                if (keyCode == KeyEvent.KEYCODE_BACK) cancel();
                return true;
            case COUNTDOWN:
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) openChosen();
                else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) { handler.removeCallbacksAndMessages(null); spin(); }
                else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_BACK) cancel();
                return true;
            default:
                switch (keyCode) {
                    case KeyEvent.KEYCODE_DPAD_LEFT: nope(); return true;
                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                    case KeyEvent.KEYCODE_DPAD_CENTER:
                    case KeyEvent.KEYCODE_ENTER: spin(); return true;
                    case KeyEvent.KEYCODE_DPAD_UP: return false;
                }
                return true;
        }
    }

    /** True while a spin or countdown is running (Back should cancel it, not leave the page). */
    boolean busy() { return state != State.IDLE; }
}
