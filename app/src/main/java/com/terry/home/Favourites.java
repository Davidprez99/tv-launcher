package com.terry.home;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.InputStream;
import java.util.Locale;

/**
 * "My Favourites": a two-row lineup of characters standing over the wallpaper in black and white. The selected one
 * turns to colour and grows (the front row also steps forward), a glow in their colour sits behind them, the
 * background crossfades to their show's darkened black-and-white scene, and OK opens the show in Netflix.
 * Images live in assets/fav (made by assets-src/fav: cut-outs, B&W copies, scenes).
 */
final class Favourites {

    static final class Fav {
        final String character, title, netflixId, asset, scene;
        final int color, row;          // row 0 = back, 1 = front
        final float centerX, top;      // fractions of the screen

        Fav(String character, String title, String netflixId, String asset, String scene, int color, int row, float centerX, float top) {
            this.character = character;
            this.title = title;
            this.netflixId = netflixId;
            this.asset = asset;
            this.scene = scene;
            this.color = color;
            this.row = row;
            this.centerX = centerX;
            this.top = top;
        }
    }

    // Back row first, so the front row draws over it.
    static final Fav[] FAVS = {
            new Fav("Tommy Shelby", "Peaky Blinders", "80002479", "tommy", "scene_peaky", 0xFFC09050, 0, 0.42f, 0.27f),
            new Fav("Harvey Specter", "Suits", "70195800", "harvey", "scene_suits", 0xFF4A7BC0, 0, 0.62f, 0.27f),
            new Fav("Berlin", "Money Heist", "80192098", "berlin", "scene_heist", 0xFFD0202E, 1, 0.29f, 0.36f),
            new Fav("Tanjiro", "Demon Slayer", "81091393", "tanjiro", "scene_demon", 0xFF2EAA6B, 1, 0.76f, 0.40f),
    };

    private final Activity activity;
    private final FrameLayout stage;
    private final View glow;
    private final TextView title, character;
    private final SceneFader scenes;
    private final View[] holders = new View[FAVS.length];
    private final ImageView[] colour = new ImageView[FAVS.length];
    private int selected = -1;
    private int glowColor;
    private ValueAnimator glowAnim;
    private final DecelerateInterpolator ease = new DecelerateInterpolator(1.6f);

    Favourites(Activity a, FrameLayout stage, View glow, TextView title, TextView character, SceneFader scenes) {
        this.activity = a;
        this.stage = stage;
        this.glow = glow;
        this.title = title;
        this.character = character;
        this.scenes = scenes;
        stage.post(this::build);
    }

    private void build() {
        int w = stage.getWidth(), h = stage.getHeight();
        for (int i = 0; i < FAVS.length; i++) {
            Fav f = FAVS[i];
            Bitmap bw = asset("fav/" + f.asset + "_bw.png"), col = asset("fav/" + f.asset + ".png");
            if (bw == null || col == null) continue;
            FrameLayout holder = new FrameLayout(activity);
            ImageView b = new ImageView(activity), c = new ImageView(activity);
            b.setImageBitmap(bw);
            c.setImageBitmap(col);
            c.setAlpha(0f);
            holder.addView(b, new FrameLayout.LayoutParams(bw.getWidth(), bw.getHeight()));
            holder.addView(c, new FrameLayout.LayoutParams(bw.getWidth(), bw.getHeight()));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(bw.getWidth(), bw.getHeight());
            lp.leftMargin = Math.round(f.centerX * w - bw.getWidth() / 2f);
            lp.topMargin = Math.round(f.top * h);
            stage.addView(holder, lp);
            holder.setPivotX(bw.getWidth() / 2f);
            holder.setPivotY(bw.getHeight() * 0.35f);
            holders[i] = holder;
            colour[i] = c;
        }
    }

    private Bitmap asset(String path) {
        try (InputStream in = activity.getAssets().open(path)) {
            return BitmapFactory.decodeStream(in);
        } catch (Exception e) {
            return null;
        }
    }

    int selected() { return selected; }

    void select(int i) {
        if (i < 0 || i >= FAVS.length || holders[i] == null) return;
        selected = i;
        for (int k = 0; k < FAVS.length; k++) {
            if (holders[k] == null) continue;
            boolean on = k == i;
            boolean front = FAVS[k].row == 1;
            float s = on ? (front ? 1.08f : 1.05f) : 1f;
            colour[k].animate().alpha(on ? 1f : 0f).setDuration(220).start();
            holders[k].animate().scaleX(s).scaleY(s).translationY(on && front ? 10 : 0)
                    .setInterpolator(ease).setDuration(220).start();
        }
        Fav f = FAVS[i];
        title.setText(f.title.toUpperCase(Locale.getDefault()));
        character.setText(f.character);
        title.setAlpha(0f);
        title.animate().alpha(1f).setDuration(200).start();
        character.setAlpha(0f);
        character.animate().alpha(1f).setDuration(260).start();
        moveGlow(i);
        showScene(f.scene);
    }

    /** Glow in the character's colour, centred behind them. */
    private void moveGlow(int i) {
        View h = holders[i];
        float cx = h.getLeft() + h.getWidth() / 2f, cy = h.getTop() + h.getHeight() * 0.38f;
        glow.animate().translationX(cx - glow.getWidth() / 2f).translationY(cy - glow.getHeight() / 2f)
                .setInterpolator(ease).setDuration(260).start();
        if (glowAnim != null) glowAnim.cancel();
        glowAnim = ValueAnimator.ofObject(new ArgbEvaluator(), glowColor, FAVS[i].color);
        glowAnim.setDuration(260);
        glowAnim.addUpdateListener(a -> {
            glowColor = (int) a.getAnimatedValue();
            GradientDrawable g = new GradientDrawable();
            g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
            g.setGradientRadius(glow.getWidth() * 0.5f);
            g.setColors(new int[]{(glowColor & 0x00FFFFFF) | 0x73000000, (glowColor & 0x00FFFFFF) | 0x26000000, 0});
            glow.setBackground(g);
        });
        glowAnim.start();
    }

    private void showScene(String key) {
        scenes.show("fav/" + key + ".jpg");
    }

    /** Called when the section is hidden: let the wallpaper come back. */
    void hideScene() {
        scenes.hide();
    }

    /** Arrow keys inside the lineup: 0 = handled, -1 = Up from the back row (to the dock), +1 = Down from the front row (to the mood deck). */
    int move(int keyCode) {
        Fav cur = FAVS[selected];
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT: {
                int dir = keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1;
                int best = -1;
                float bestDx = Float.MAX_VALUE;
                for (int k = 0; k < FAVS.length; k++) {
                    if (FAVS[k].row != cur.row || holders[k] == null) continue;
                    float dx = (FAVS[k].centerX - cur.centerX) * dir;
                    if (dx > 0 && dx < bestDx) { bestDx = dx; best = k; }
                }
                if (best >= 0) select(best); // stops at the ends
                return 0;
            }
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (cur.row == 0) { select(nearestInRow(1, cur.centerX)); return 0; }
                return 1;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (cur.row == 1) { select(nearestInRow(0, cur.centerX)); return 0; }
                return -1;
        }
        return 0;
    }

    private int nearestInRow(int row, float x) {
        int best = selected;
        float bestD = Float.MAX_VALUE;
        for (int k = 0; k < FAVS.length; k++) {
            if (FAVS[k].row != row || holders[k] == null) continue;
            float d = Math.abs(FAVS[k].centerX - x);
            if (d < bestD) { bestD = d; best = k; }
        }
        return best;
    }

    /** OK: open the show in the Netflix app. */
    void open() {
        Fav f = FAVS[selected];
        Netflix.open(activity, f.netflixId, f.title);
    }
}
