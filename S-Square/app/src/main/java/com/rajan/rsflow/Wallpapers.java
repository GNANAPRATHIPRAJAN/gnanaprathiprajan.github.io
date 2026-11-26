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
    static final int FIRST_SCENE = 11;
    static final int FIRST_PIXEL = 21;
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
        "Jade Rift", "Blue Summit", "Copper Dunes", "Violet Fjord",
        "", "", "", "", "", "",
        "Pixel · Citron orbit",
        "Pixel · Coral dunes",
        "Pixel · Iris arcs",
        "Amber Wilds", "Polar Aurora", "Vermilion Mesa"
    };
    private static final String[] FILES = {
        "cobalt.webp",
        "forest.webp",
        "dusk.webp",
        "alpine.webp",
        "tidal.webp",
        "crescent.webp",
        "midnight.webp",
        "jade-rift.webp", "blue-summit.webp", "copper-dunes.webp", "violet-fjord.webp"
    };
    private final Context context;
    private int cachedChoice = -1;
    private Bitmap cachedImage;

    Wallpapers(Context context) {
        this.context = context;
    }

    static int choice(FlowStore store) {
        return normalized(FlowUi.clamp(store.number("background", FIRST_SCENE), PATTERN, NAMES.length - 1));
    }

    static int normalized(int id) {
        return id >= 15 && id <= 20 ? new int[]{11,13,12,11,13,14}[id-15] : id;
    }

    Bitmap image(int choice) {
        choice = normalized(choice);
        if (choice < FIRST_IMAGE || choice >= NAMES.length) return null;
        if (cachedChoice != choice) {
            cachedImage = read(choice, 1);
            cachedChoice = choice;
        }
        return cachedImage;
    }

    Bitmap thumbnail(int choice) {
        return read(normalized(choice), 4);
    }

    private Bitmap read(int choice, int sampleSize) {
        if (choice < FIRST_IMAGE || choice >= NAMES.length) return null;
        if (choice > 24) return horizon(choice, sampleSize);
        if (choice >= FIRST_PIXEL && choice < 24) return pixelArt(choice, sampleSize);

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize;
        try (InputStream stream =
                context.getAssets().open("wallpapers/" + (choice == 24 ? "amber-wilds.webp" : FILES[choice - FIRST_IMAGE]))) {
            return BitmapFactory.decodeStream(stream, null, options);
        } catch (IOException e) {
            android.util.Log.w("RSFlow", "Could not open the built-in wallpaper", e);
            return null;
        }
    }

    private static Bitmap horizon(int choice, int sample) {
        int width = 768 / sample, height = 1536 / sample;
        Bitmap image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(image);
        c.scale(width / 768f, height / 1536f);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        boolean aurora = choice == 25;
        int[] sky = aurora ? new int[]{0xff111533, 0xff177a80, 0xff90d9b3}
                : new int[]{0xff462958, 0xffe15d52, 0xfff9c37c};
        p.setShader(new LinearGradient(0, 0, 420, 1340, sky, null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, 768, 1536, p);
        p.setShader(null);
        if (aurora) {
            Random stars = new Random(1221);
            p.setColor(0xaaddfff9);
            for (int i = 0; i < 110; i++) c.drawCircle(stars.nextInt(768), stars.nextInt(840), 1 + stars.nextFloat(), p);
            for (int i = 0; i < 85; i++) {
                android.graphics.Path ribbon = new android.graphics.Path();
                ribbon.moveTo(-50, 385 + i);
                ribbon.cubicTo(210, 40 + 2 * i, 360, 730 - i, 825, 210 + 2 * i);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(3);
                p.setColor(0x304ef5c3);
                c.drawPath(ribbon, p);
            }
            p.setStyle(Paint.Style.FILL);
        } else {
            p.setColor(0xffffdb9c);
            c.drawCircle(526, 709, 116, p);
        }
        dune(c, p, aurora ? 0xff366374 : 0xffa05267, 1070, 690, 1180, 820);
        dune(c, p, aurora ? 0xff22394f : 0xff753a52, 1190, 1380, 730, 1100);
        dune(c, p, aurora ? 0xff101c32 : 0xff40243d, 1350, 1150, 1460, 1170);
        return image;
    }

    private static Bitmap pixelArt(int choice, int sampling) {
        int width = 768 / sampling, height = 1536 / sampling;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bitmap);
        c.scale(width / 768f, height / 1536f);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int scene = choice - FIRST_PIXEL;
        if (scene == 0) {
            c.drawColor(0xff182d32);
            p.setColor(0xff34595b);
            c.drawCircle(620, 1380, 780, p);
            p.setColor(0xff7cafa1);
            c.drawCircle(620, 1380, 520, p);
            p.setColor(0xffcbe783);
            c.drawCircle(620, 1380, 294, p);
            p.setColor(0xfffff2bd);
            c.drawCircle(152, 802, 54, p);
        } else if (scene == 1) {
            c.drawColor(0xff543948);
            p.setColor(0xffffcea3);
            c.drawCircle(541, 736, 118, p);
            dune(c, p, 0xffc66e72, 1010, 510, 1450, 831);
            dune(c, p, 0xffed9a7f, 1174, 1320, 710, 1060);
            dune(c, p, 0xff83445b, 1450, 970, 1390, 1110);
        } else {
            c.drawColor(0xff252039);
            c.rotate(-28, 384, 1024);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(116);
            int[] colors = {0xff645589, 0xffa090be, 0xffa8c5de, 0xffeadbbd};
            for (int i = 0; i < colors.length; i++) {
                p.setColor(colors[i]);
                int r = 520 - 104 * i;
                c.drawRoundRect(378-r, 870-r, 378+r, 1980+r, r, r, p);
            }
        }
        return bitmap;
    }

    private static void dune(Canvas c, Paint p, int tint, int start, int c1, int c2, int end) {
        android.graphics.Path path = new android.graphics.Path();
        path.moveTo(0, start);
        path.cubicTo(320, c1, 490, c2, 768, end);
        path.lineTo(768, 1536);
        path.lineTo(0, 1536);
        path.close();
        p.setColor(tint);
        c.drawPath(path, p);
    }

}
