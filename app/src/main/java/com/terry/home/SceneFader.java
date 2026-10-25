package com.terry.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/** Crossfades a full-screen background between images from assets (scenes and backdrops), keeping a few decoded. */
final class SceneFader {
    private final Context context;
    private final ImageView a, b;
    private boolean aOnTop;
    private String current;
    private final Map<String, Bitmap> cache = new LinkedHashMap<String, Bitmap>(8, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Bitmap> e) { return size() > 4; } // ~1.8 MB each
    };

    SceneFader(Context c, ImageView a, ImageView b) {
        this.context = c;
        this.a = a;
        this.b = b;
    }

    /** assetPath like "fav/scene_peaky.jpg" or "moods/backdrops/dark.jpg". */
    void show(String assetPath) {
        if (assetPath.equals(current)) return;
        Bitmap bmp = cache.get(assetPath);
        if (bmp == null) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            try (InputStream in = context.getAssets().open(assetPath)) {
                bmp = BitmapFactory.decodeStream(in, null, o);
            } catch (Exception e) {
                return;
            }
            if (bmp == null) return;
            cache.put(assetPath, bmp);
        }
        current = assetPath;
        ImageView in = aOnTop ? b : a, out = aOnTop ? a : b;
        in.setImageBitmap(bmp);
        in.bringToFront();
        in.setAlpha(0f);
        in.animate().setStartDelay(0).alpha(1f).setDuration(380).start();
        out.animate().setStartDelay(380).alpha(0f).setDuration(1).start();
        aOnTop = !aOnTop;
    }

    /** Let the wallpaper show again. */
    void hide() {
        current = null;
        a.animate().setStartDelay(0).alpha(0f).setDuration(300).start();
        b.animate().setStartDelay(0).alpha(0f).setDuration(300).start();
    }
}
