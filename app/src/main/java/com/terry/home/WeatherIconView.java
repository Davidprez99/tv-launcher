package com.terry.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/** Small outline weather icon drawn from a WMO weather code (the codes Open-Meteo returns). */
public class WeatherIconView extends View {

    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF oval = new RectF();
    private int code = 0;
    private boolean day = true;

    public WeatherIconView(Context c, AttributeSet a) {
        super(c, a);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setColor(0xE6FFFFFF);
        stroke.setStrokeWidth(1.6f * c.getResources().getDisplayMetrics().density);
        stroke.setShadowLayer(6, 0, 1, 0x99000000);
        setLayerType(LAYER_TYPE_SOFTWARE, null); // shadow on a stroked path needs software drawing; the view is tiny
    }

    void set(int wmoCode, boolean isDay) {
        code = wmoCode;
        day = isDay;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float s = Math.min(getWidth(), getHeight()) / 24f; // draw on a 24×24 grid
        c.save();
        c.scale(s, s);
        if (code == 0) {
            if (day) sun(c, 12, 12, 4.5f); else moon(c);
        } else if (code <= 3) {
            if (code < 3) { if (day) sun(c, 8, 8, 3.2f); else smallMoon(c); }
            cloud(c);
        } else if (code == 45 || code == 48) {
            line(c, 4, 9, 20, 9); line(c, 3, 13, 21, 13); line(c, 5, 17, 19, 17);
        } else if ((code >= 71 && code <= 77) || code == 85 || code == 86) {
            cloud(c); dot(c, 8, 20); dot(c, 12, 21.5f); dot(c, 16, 20);
        } else if (code >= 95) {
            cloud(c);
            path.reset(); path.moveTo(13, 15); path.lineTo(10.5f, 19); path.lineTo(13.5f, 19); path.lineTo(11, 23);
            c.drawPath(path, stroke);
        } else { // drizzle, rain, showers
            cloud(c); line(c, 8, 18.5f, 7, 21.5f); line(c, 12, 18.5f, 11, 21.5f); line(c, 16, 18.5f, 15, 21.5f);
        }
        c.restore();
    }

    private void sun(Canvas c, float cx, float cy, float r) {
        c.drawCircle(cx, cy, r, stroke);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI / 4 * i;
            float x1 = (float) (cx + Math.cos(a) * (r + 2)), y1 = (float) (cy + Math.sin(a) * (r + 2));
            float x2 = (float) (cx + Math.cos(a) * (r + 4)), y2 = (float) (cy + Math.sin(a) * (r + 4));
            c.drawLine(x1, y1, x2, y2, stroke);
        }
    }

    private void moon(Canvas c) {
        path.reset();
        oval.set(4, 4, 20, 20);
        path.addArc(oval, 60, 260);
        path.quadTo(9, 12, 16.5f, 18.5f);
        c.drawPath(path, stroke);
    }

    private void smallMoon(Canvas c) {
        path.reset();
        oval.set(3, 3, 13, 13);
        path.addArc(oval, 60, 260);
        path.quadTo(6, 8, 10.5f, 12);
        c.drawPath(path, stroke);
    }

    private void cloud(Canvas c) {
        path.reset();
        path.moveTo(7, 17);
        path.cubicTo(3.5f, 17, 3, 12.5f, 6.5f, 12);
        path.cubicTo(7, 8, 12.5f, 7, 14, 10.5f);
        path.cubicTo(17.5f, 9.5f, 20.5f, 12, 19.5f, 14.5f);
        path.cubicTo(21.5f, 15.5f, 20.5f, 17, 18.5f, 17);
        path.close();
        c.drawPath(path, stroke);
    }

    private void line(Canvas c, float x1, float y1, float x2, float y2) {
        c.drawLine(x1, y1, x2, y2, stroke);
    }

    private void dot(Canvas c, float x, float y) {
        c.drawCircle(x, y, 0.6f, stroke);
    }
}
