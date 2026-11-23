package com.rajan.rsflow;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;

/** Immutable design shared by the preview and the bitmap sent to Android. */
final class LockDesign {
    static final int HOME_WALLPAPER = -2;
    final Bitmap image;
    final int background;
    final int dim, zoom, x, y, textY, textSize, ink;
    final String text;
    final Typeface font;
    final boolean paperAvailable;

    LockDesign(MainActivity activity) {
        FlowStore store = activity.store;
        int source = store.number("lockImage", Wallpapers.FIRST_IMAGE);
        int resolved = source == HOME_WALLPAPER ? Wallpapers.choice(store) : source;
        image =
                resolved == Wallpapers.PHOTO
                        ? (source == HOME_WALLPAPER
                                ? activity.files.wallpaper()
                                : activity.files.lockPhoto())
                        : activity.wallpapers.image(resolved);
        background =
                (source == HOME_WALLPAPER
                                ? store.number("solidColor", 0xff111722)
                                : store.number("lockSolid", 0xff111722))
                        | 0xff000000;
        paperAvailable = image != null || resolved == Wallpapers.SOLID;
        dim = FlowUi.clamp(store.number("lockDim", 20), 0, 90);
        zoom = FlowUi.clamp(store.number("lockZoom", 100), 100, 200);
        x = FlowUi.clamp(store.number("lockX", 50), 0, 100);
        y = FlowUi.clamp(store.number("lockY", 50), 0, 100);
        textY = FlowUi.clamp(store.number("lockTextY", 70), 10, 90);
        textSize = FlowUi.clamp(store.number("lockTextSize", 23), 12, 48);
        ink =
                new int[] {Color.WHITE, Color.BLACK, activity.ui.accent}
                        [FlowUi.clamp(store.number("lockInk", 0), 0, 2)];
        text = FlowStore.clipped(store.option("lockText", ""), 64).trim();
        font = FlowUi.fontAt(activity, store.number("lockFont", store.number("font", 3)));
    }

    void draw(Canvas canvas, int width, int height) {
        canvas.drawColor(background);
        if (image != null) {
            float scale =
                    Math.max((float) width / image.getWidth(), (float) height / image.getHeight())
                            * zoom
                            / 100f;
            float w = image.getWidth() * scale, h = image.getHeight() * scale;
            float left = (width - w) * x / 100f, top = (height - h) * y / 100f;
            canvas.drawBitmap(
                    image,
                    null,
                    new RectF(left, top, left + w, top + h),
                    new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
        }
        canvas.drawColor(Color.argb(dim * 255 / 100, 0, 0, 0));
        if (text.isEmpty()) return;
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        paint.setTypeface(font);
        paint.setTextSize(width * textSize / 390f);
        paint.setColor(ink);
        paint.setShadowLayer(
                Math.max(.5f, width / 250f),
                0,
                width / 600f,
                ink == Color.BLACK ? 0x88ffffff : 0xaa000000);
        int textWidth = Math.max(1, Math.round(width * .86f));
        StaticLayout lines =
                StaticLayout.Builder.obtain(text, 0, text.length(), paint, textWidth)
                        .setAlignment(Layout.Alignment.ALIGN_CENTER)
                        .setIncludePad(false)
                        .setMaxLines(3)
                        .setEllipsize(TextUtils.TruncateAt.END)
                        .build();
        float top =
                Math.max(
                        0,
                        Math.min(
                                height - lines.getHeight(),
                                height * textY / 100f - lines.getHeight() / 2f));
        canvas.save();
        canvas.translate((width - textWidth) / 2f, top);
        lines.draw(canvas);
        canvas.restore();
    }

    Bitmap render(int width, int height) {
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        draw(new Canvas(result), width, height);
        return result;
    }
}
