package com.rajan.rsflow;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

final class Wallpapers {
    static final int PATTERN = 0;
    static final int SOLID = 1;
    static final int SYSTEM = 2;
    static final int PHOTO = 3;
    static final int FIRST_IMAGE = 4;
    static final int MIDNIGHT = 10;
    static final int FIRST_BOKEH = 11;
    static final String[] NAMES = {
        "Flow pattern",
        "Solid",
        "System wallpaper",
        "My photo",
        "Cobalt Glass",
        "Forest Fold",
        "Dusk Bloom",
        "Alpine Dawn",
        "Tidal Glass",
        "Crescent Nebula",
        "Midnight Glass",
        "Pistachio Bokeh",
        "Cobalt Bokeh",
        "Copper Bokeh",
        "Orchid Bokeh",
        "Lagoon Bokeh",
        "Rose Bokeh",
        "Ice Bokeh",
        "Olive Bokeh",
        "Ember Bokeh",
        "Moonstone Bokeh"
    };
    private static final String[] FILES = {
        "cobalt.webp",
        "forest.webp",
        "dusk.webp",
        "alpine.webp",
        "tidal.webp",
        "crescent.webp",
        "midnight.webp",
        "bokeh-pistachio.webp",
        "bokeh-cobalt.webp",
        "bokeh-copper.webp"
    };
    private final Context context;
    private int cachedChoice = -1;
    private Bitmap cachedImage;

    Wallpapers(Context context) {
        this.context = context;
    }

    static int choice(FlowStore store) {
        return FlowUi.clamp(store.number("background", FIRST_BOKEH), PATTERN, NAMES.length - 1);
    }

    Bitmap image(int choice) {
        if (choice < FIRST_IMAGE || choice >= NAMES.length) return null;
        if (cachedChoice != choice) {
            cachedImage = read(choice, 1);
            cachedChoice = choice;
        }
        return cachedImage;
    }

    Bitmap thumbnail(int choice) {
        return read(choice, 4);
    }

    private Bitmap read(int choice, int sampleSize) {
        if (choice < FIRST_IMAGE || choice >= NAMES.length) return null;
        if (choice - FIRST_IMAGE >= FILES.length) return renderBokeh(choice, sampleSize);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize;
        try (InputStream stream =
                context.getAssets().open("wallpapers/" + FILES[choice - FIRST_IMAGE])) {
            return BitmapFactory.decodeStream(stream, null, options);
        } catch (IOException e) {
            android.util.Log.w("RSFlow", "Could not open the built-in wallpaper", e);
            return null;
        }
    }

    private static Bitmap renderBokeh(int choice, int sampling) {
        int width = 768 / sampling, height = 1536 / sampling;
        int[][] palettes = {
            {0xff140d24, 0xff733c96, 0xffdbc2f2}, {0xff041820, 0xff167f89, 0xff8de1d4},
            {0xff210d1e, 0xff9c3258, 0xffeea7b6}, {0xff101c2a, 0xff507196, 0xffb8e5f6},
            {0xff131709, 0xff5c6d19, 0xffd7ce82}, {0xff231008, 0xffa04b24, 0xffffb36f},
            {0xff181821, 0xff57637e, 0xffdfd9ef}
        };
        int[] colors = palettes[choice - FIRST_BOKEH - 3];
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(
                new LinearGradient(
                        0,
                        0,
                        width,
                        height,
                        0xff070a10,
                        FlowUi.blend(colors[0], colors[1], .28f),
                        Shader.TileMode.CLAMP));
        canvas.drawPaint(paint);
        Random random = new Random(3218L + 71 * choice);
        for (int i = 0; i < 32; i++) {
            float x = width * (-.11f + 1.22f * random.nextFloat());
            float y = height * (.32f + .79f * random.nextFloat());
            float r = width * (.045f + .31f * random.nextFloat());
            int tint = FlowUi.blend(colors[1], colors[2], random.nextFloat() * .66f) & 0xffffff;
            int alpha = (25 + random.nextInt(47)) << 24;
            paint.setShader(
                    new RadialGradient(
                            x,
                            y,
                            r,
                            new int[] {tint | alpha, tint | alpha, tint},
                            new float[] {0, .65f, 1},
                            Shader.TileMode.CLAMP));
            canvas.drawCircle(x, y, r, paint);
        }
        return bitmap;
    }
}
