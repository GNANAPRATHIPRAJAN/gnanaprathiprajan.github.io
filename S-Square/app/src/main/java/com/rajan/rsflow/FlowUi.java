package com.rajan.rsflow;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.Gravity;
import android.view.View;

final class FlowUi {
    static final String[] THEMES = {"Midnight", "Daylight", "Clay", "Lichen", "System"};
    static final String[] ACCENT_NAMES = {"Cobalt", "Glacier", "Lavender", "Peach", "Rose"};
    static final int[] ACCENTS = {0xff0069b8, 0xffa5ddf9, 0xffc9bbff, 0xffffc69d, 0xfff5abc6};
    final Context context;
    final int base, panel, outline, text, dim, accent, ink;
    static final String[] FONT_NAMES = {"Manrope", "Space Grotesk", "DM Sans", "Android Sans"};
    private static final String[] FONT_FILES = {"Manrope.ttf", "SpaceGrotesk.ttf", "DMSans.ttf"};
    private static final Typeface[] FONTS = new Typeface[FONT_NAMES.length];
    final float textScale;
    final boolean light;
    final Typeface font;

    FlowUi(Context context, FlowStore store) {
        this.context = context;
        textScale = clamp(store.number("textScale", 100), 75, 150) / 100f;
        int theme = clamp(store.number("theme", 0), 0, THEMES.length - 1);
        if (theme == 4) {
            int mode = context.getResources().getConfiguration().uiMode & 0x30;
            theme = mode == 0x20 ? 0 : 1;
        }
        int[][] colors = {
                {0xff000000, 0xff202020, 0xff626262, 0xffffffff, 0xffb5b5b5},
                {0xffffffff, 0xffededed, 0xff8c8c8c, 0xff111111, 0xff525252},
                {0xff211820, 0xff30232c, 0xff533d4a, 0xffffeced, 0xffd3b2bc},
                {0xff151c16, 0xff202c21, 0xff394a39, 0xffedf5e6, 0xffb1c3ab}
        };
        base = store.desktop() && theme == 0 ? 0xff101722 : colors[theme][0];
        panel = store.desktop() && theme == 0 ? 0xff202d3d : colors[theme][1];
        outline = store.desktop() && theme == 0 ? 0xff526478 : colors[theme][2];
        text = colors[theme][3];
        dim = colors[theme][4];
        light = theme == 1;
        accent = store.number("accent", ACCENTS[0]) | 0xff000000;
        ink = readable(accent);
        font = fontAt(context, store.number("font", 3));
    }

    static Typeface fontAt(Context context, int index) {
        int fontIndex = clamp(index, 0, FONT_NAMES.length - 1);
        if (FONTS[fontIndex] == null) {
            try {
                FONTS[fontIndex] = fontIndex < FONT_FILES.length
                        ? Typeface.createFromAsset(context.getAssets(), "fonts/" + FONT_FILES[fontIndex])
                        : Typeface.create("sans-serif", 0);
            } catch (RuntimeException ignored) {
                FONTS[fontIndex] = Typeface.DEFAULT;
            }
        }
        return FONTS[fontIndex];
    }

    int dp(float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    LinearLayout column() {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    LinearLayout row() {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    TextView label(String title, int size, int color) {
        TextView label = new TextView(context);
        label.setText(title);
        label.setTypeface(font);
        label.setTextSize(size * textScale);
        label.setTextColor(color);
        label.setFontFeatureSettings("kern");
        label.setIncludeFontPadding(false);
        return label;
    }

    static void medium(TextView view) { weight(view, 620); }

    static void weight(TextView view, int weight) {
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            view.setTypeface(Typeface.create(view.getTypeface(), weight, false));
        } else if (weight >= 600) {
            view.setTypeface(view.getTypeface(), Typeface.BOLD);
        }
    }

    TextView button(String title, boolean selected, Runnable action) {
        TextView view = label(title, 13, selected ? ink : text);
        medium(view);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(13), dp(10), dp(13), dp(10));
        view.setMinHeight(dp(46));
        view.setBackground(surface(selected ? accent : panel, 14, selected ? accent : outline));
        view.setOnClickListener(v -> action.run());
        view.setFocusable(true);
        return view;
    }

    TextView command(String title, Runnable action) {
        TextView view = label(title, 17, text);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(9), 0, dp(9), 0);
        view.setMinimumHeight(dp(48));
        view.setMinimumWidth(dp(48));
        view.setBackground(surface(Color.TRANSPARENT, 0, 0));
        view.setFocusable(true);
        view.setOnClickListener(v -> action.run());
        return view;
    }

    View symbol(String name, int tint, int size) { return new Symbol(context, name, tint, dp(size)); }

    GradientDrawable rounded(int color, int radius, int stroke) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        if (stroke != 0) shape.setStroke(dp(1), stroke);
        return shape;
    }

    RippleDrawable surface(int color, int radius, int stroke) {
        int ripple = readable(color) == Color.WHITE ? 0x30ffffff : 0x28000000;
        return new RippleDrawable(ColorStateList.valueOf(ripple), rounded(color, radius, stroke),
                rounded(Color.WHITE, radius, 0));
    }

    void gap(LinearLayout list, int height) {
        list.addView(new View(context), new LinearLayout.LayoutParams(1, dp(height)));
    }

    void gapHorizontal(LinearLayout list, int width) {
        list.addView(new View(context), new LinearLayout.LayoutParams(dp(width), 1));
    }

    static int blend(int a, int b, float ratio) {
        return Color.rgb(Math.round(Color.red(a) * (1 - ratio) + Color.red(b) * ratio),
                Math.round(Color.green(a) * (1 - ratio) + Color.green(b) * ratio),
                Math.round(Color.blue(a) * (1 - ratio) + Color.blue(b) * ratio));
    }

    static int readable(int color) {
        double luminance = 0.2126 * linear(Color.red(color)) + 0.7152 * linear(Color.green(color))
                + 0.0722 * linear(Color.blue(color));
        return luminance > .179 ? 0xff000000 : Color.WHITE;
    }

    private static double linear(int channel) {
        double value = channel / 255.0;
        return value <= .04045 ? value / 12.92 : Math.pow((value + .055) / 1.055, 2.4);
    }

    static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }

    private static final class Symbol extends View {
        private final String name;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int size;
        Symbol(Context context, String name, int tint, int size) {
            super(context);
            this.name = name;
            this.size = size;
            paint.setColor(tint);
            paint.setStrokeWidth(1.65f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStyle(Paint.Style.STROKE);
        }

        @Override protected void onMeasure(int w, int h) {
            setMeasuredDimension(resolveSize(size, w), resolveSize(size, h));
        }

        @Override protected void onDraw(Canvas canvas) {
            canvas.save();
            float factor = Math.min(getWidth(), getHeight()) / 24f;
            canvas.translate((getWidth() - factor * 24) / 2, (getHeight() - factor * 24) / 2);
            canvas.scale(factor, factor);
            switch (name) {
                case "start":
                    paint.setStyle(Paint.Style.FILL);
                    for (int x : new int[]{2, 13}) for (int y : new int[]{2, 13}) canvas.drawRect(x, y, x + 9, y + 9, paint);
                    paint.setStyle(Paint.Style.STROKE);
                    break;
                case "search":
                    canvas.drawCircle(10, 10, 6.5f, paint);
                    path(canvas, 15, 15, 21, 21);
                    break;
                case "action":
                    path(canvas, 3, 4, 21, 4, 21, 16, 14, 16, 8, 21, 8, 16, 3, 16, 3, 4);
                    break;
                case "more":
                    paint.setStyle(Paint.Style.FILL);
                    for (int x : new int[]{4, 12, 20}) canvas.drawCircle(x, 12, 1.6f, paint);
                    paint.setStyle(Paint.Style.STROKE);
                    break;
                case "wifi":
                    for (int r : new int[]{5, 9, 13}) {
                        canvas.drawArc(12-r, 19-r, 12+r, 19+r, 223, 94, false, paint);
                    }
                    canvas.drawPoint(12, 19, paint);
                    break;
                case "music":
                    path(canvas,3,9,7,9,12,4,12,20,7,15,3,15,3,9);
                    canvas.drawArc(9,6,18,18,-55,110,false,paint);
                    canvas.drawArc(8,2,22,22,-51,102,false,paint);
                    break;
                case "bluetooth":
                    path(canvas, 5, 6, 17, 16, 11, 21, 11, 3, 17, 8, 5, 18);
                    break;
                case "sun":
                case "gear":
                    canvas.drawCircle(12, 12, name.equals("sun") ? 4 : 6, paint);
                    if (name.equals("gear")) canvas.drawCircle(12, 12, 2, paint);
                    for (int i = 0; i < 8; i++) {
                        canvas.drawLine(12, 3, 12, 5, paint);
                        canvas.rotate(45, 12, 12);
                    }
                    break;
                case "battery":
                    canvas.drawRect(2, 7, 19, 17, paint);
                    path(canvas, 21, 10, 21, 14);
                    path(canvas, 5, 10, 5, 14);
                    path(canvas, 8, 10, 8, 14);
                    break;
                case "picture":
                    canvas.drawRect(2, 4, 22, 20, paint);
                    canvas.drawCircle(8, 9, 1.5f, paint);
                    path(canvas, 3, 18, 9, 13, 12, 16, 16, 10, 22, 17);
                    break;
                case "home":
                    path(canvas, 3, 11, 12, 3.5f, 21, 11, 19, 11, 19, 20, 5, 20, 5, 11);
                    path(canvas, 10, 20, 10, 14, 14, 14, 14, 20);
                    break;
                case "apps":
                    for (int y : new int[]{4, 13}) for (int x : new int[]{4, 13}) {
                        canvas.drawRoundRect(x, y, x + 7, y + 7, 1.7f, 1.7f, paint);
                    }
                    break;
                case "tune":
                    for (int y : new int[]{6, 12, 18}) {
                        int x = y == 12 ? 15 : 9;
                        canvas.drawLine(3, y, x - 2.4f, y, paint);
                        canvas.drawLine(x + 2.4f, y, 21, y, paint);
                        canvas.drawCircle(x, y, 2.4f, paint);
                    }
                    break;
                case "swipe":
                    path(canvas, 12, 18, 12, 5);
                    path(canvas, 7, 10, 12, 5, 17, 10);
                    path(canvas, 6, 21, 18, 21);
                    break;
                case "folder":
                    path(canvas, 3, 19, 3, 6, 10, 6, 12, 8, 21, 8, 21, 19, 3, 19);
                    break;
                case "note":
                    canvas.drawRoundRect(5, 3, 19, 21, 2, 2, paint);
                    path(canvas, 9, 9, 15, 9);
                    path(canvas, 9, 14, 15, 14);
                    break;
                case "back":
                    path(canvas, 16, 5, 9, 12, 16, 19);
                    break;
                case "next":
                    path(canvas, 8, 5, 15, 12, 8, 19);
                    break;
                default:
                    path(canvas, 12, 5, 12, 19);
                    path(canvas, 5, 12, 19, 12);
            }
            canvas.restore();
        }

        private void path(Canvas canvas, float... points) {
            Path path = new Path();
            path.moveTo(points[0], points[1]);
            for (int i = 2; i < points.length; i += 2) path.lineTo(points[i], points[i + 1]);
            canvas.drawPath(path, paint);
        }
    }

    static final class Banner extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int color;
        Banner(Context context, int color) {
            super(context);
            this.color = color;
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        @Override protected void onDraw(Canvas canvas) {
            float width = getWidth(), height = getHeight();
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            for (int i = 0; i < 8; i++) {
                float off = (i - 1) * height * .105f;
                paint.setAlpha(58 - i * 5);
                paint.setStrokeWidth(i == 1 ? 2.4f : 1.3f);
                Path curve = new Path();
                curve.moveTo(width * .47f, height + off);
                curve.cubicTo(width * .64f, height * .77f + off, width * .74f,
                        height * .19f + off, width * 1.10f, height * .13f + off);
                canvas.drawPath(curve, paint);
            }
        }
    }
}
