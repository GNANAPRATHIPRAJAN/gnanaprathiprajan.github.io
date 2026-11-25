package com.rajan.rsflow;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Each window samples the same viewport-sized wallpaper, including while the tile board scrolls. */
final class PanoramaWindow extends View {
    private final Bitmap image;
    private final View viewport;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF bounds = new RectF();
    private final int[] origin = new int[2], tile = new int[2];
    private final boolean fit;
    private final int scrim;

    PanoramaWindow(Context context, Bitmap image, View viewport, FlowStore store) {
        super(context);
        this.image = image;
        this.viewport = viewport;
        fit = store.number("wallScale", 0) == 1;
        int dim = FlowUi.clamp(store.number("wallDim", 20), 0, 95);
        scrim = ((dim * 255 / 100) << 24);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override protected void onDraw(Canvas canvas) {
        float w = viewport.getWidth(), h = viewport.getHeight();
        float sx = w / image.getWidth(), sy = h / image.getHeight();
        float scale = fit ? Math.min(sx, sy) : Math.max(sx, sy);
        float iw = image.getWidth() * scale, ih = image.getHeight() * scale;
        viewport.getLocationInWindow(origin);
        getLocationInWindow(tile);
        float left = origin[0] - tile[0] + (w - iw) / 2;
        float top = origin[1] - tile[1] + (h - ih) / 2;
        bounds.set(left, top, left + iw, top + ih);
        canvas.drawBitmap(image, null, bounds, paint);
        canvas.drawColor(scrim);
    }
}
