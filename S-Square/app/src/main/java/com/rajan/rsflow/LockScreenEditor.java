package com.rajan.rsflow;

import static com.rajan.rsflow.MainActivity.FILL;
import static com.rajan.rsflow.MainActivity.WRAP;

import android.content.Intent;
import android.graphics.Canvas;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextClock;
import android.widget.TextView;

final class LockScreenEditor {
    private final MainActivity activity;
    private final FlowUi ui;
    private final FlowStore store;
    private LockDesign draft;
    private View preview;
    private FrameLayout mockup;
    private TextClock clock;
    private LinearLayout content;
    private int width, height;

    LockScreenEditor(MainActivity activity) {
        this.activity = activity;
        ui = activity.ui;
        store = activity.store;
    }

    void build() {
        activity.header("Lock screen", "Home", activity::leaveLockEditor);
        content = activity.scrollContent();
        DisplayMetrics display = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getRealMetrics(display);
        int shortSide = Math.max(1, Math.min(display.widthPixels, display.heightPixels));
        int longSide = Math.max(shortSide, Math.max(display.widthPixels, display.heightPixels));
        height = Math.min(2560, longSide);
        width = Math.max(1, Math.round(shortSide * (height / (float) longSide)));
        int previewHeight =
                Math.min(
                        326,
                        Math.max(
                                160,
                                activity.getResources().getConfiguration().screenHeightDp / 3));
        draft = new LockDesign(activity);
        preview =
                new View(activity) {
                    @Override
                    protected void onDraw(Canvas canvas) {
                        draft.draw(canvas, getWidth(), getHeight());
                    }
                };
        mockup = new FrameLayout(activity);
        mockup.addView(preview, new FrameLayout.LayoutParams(FILL, FILL));
        mockup.setBackground(ui.rounded(ui.panel, 18, ui.outline));
        mockup.setClipToOutline(true);
        mockup.setContentDescription("Lock screen preview");
        clock = new TextClock(activity);
        clock.setSingleLine(true);
        clock.setIncludeFontPadding(false);
        clock.setShadowLayer(ui.dp(2), 0, ui.dp(1), 0x99000000);
        mockup.addView(clock, new FrameLayout.LayoutParams(FILL, WRAP, Gravity.TOP));
        mockup.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> renderClock());
        renderClock();
        preview.setBackground(ui.rounded(ui.panel, 18, ui.outline));
        preview.setClipToOutline(true);
        LinearLayout.LayoutParams frame =
                new LinearLayout.LayoutParams(
                        ui.dp(Math.round(previewHeight * (float) width / height)),
                        ui.dp(previewHeight));
        frame.gravity = Gravity.CENTER_HORIZONTAL;
        content.addView(mockup, frame);
        ui.gap(content, 10);
        content.addView(
                ui.label(
                        "Wallpaper preview. Android adds your clock, notifications and unlock"
                                + " controls.",
                        13,
                        ui.dim));
        ui.gap(content, 8);
        row("Wallpapers", this::background, "Choose photo", activity.files::chooseLockPhoto);
        row(
                "Solid color",
                () ->
                        new TileEditor(activity)
                                .colors(
                                        "Lock-screen color",
                                        store.number("lockSolid", 0xff111722),
                                        color -> {
                                            FlowStore.put(store.options, "lockSolid", color);
                                            change("lockImage", Wallpapers.SOLID);
                                        }),
                "Reset crop",
                this::resetCrop);
        row(
                "Caption",
                () ->
                        activity.prompt(
                                "Wallpaper caption",
                                "Optional text",
                                store.option("lockText", ""),
                                64,
                                false,
                                value -> change("lockText", value)),
                "Font",
                () -> pick("Caption font", "lockFont", FlowUi.FONT_NAMES, store.number("font", 3)));
        row(
                "Text color",
                () ->
                        pick(
                                "Caption color",
                                "lockInk",
                                new String[] {"White", "Black", "Launcher accent"},
                                0),
                "System lock settings",
                this::system);
        slider("Dimming", "lockDim", 0, 90, 20, "%");
        slider("Zoom", "lockZoom", 100, 200, 100, "%");
        slider("Horizontal crop", "lockX", 0, 100, 50, "%");
        slider("Vertical crop", "lockY", 0, 100, 50, "%");
        slider("Caption position", "lockTextY", 10, 90, 70, "%");
        slider("Caption size", "lockTextSize", 12, 48, 23, "");
        ui.gap(content, 10);
        content.addView(ui.label("Clock preview", 18, ui.text));
        row(
                "Clock style",
                () ->
                        pick(
                                "Preview style",
                                "lockClockStyle",
                                new String[] {"Digital", "Stacked", "Hidden"},
                                0),
                "Clock font",
                () ->
                        pick(
                                "Preview font",
                                "lockClockFont",
                                FlowUi.FONT_NAMES,
                                store.number("font", 0)));
        row(
                "Clock alignment",
                () ->
                        pick(
                                "Preview alignment",
                                "lockClockAlign",
                                new String[] {"Left", "Center", "Right"},
                                1),
                "Time format",
                () ->
                        pick(
                                "Preview time format",
                                "lockClockFormat",
                                new String[] {"System", "12-hour", "24-hour"},
                                0));
        row(
                "Clock color",
                () ->
                        new TileEditor(activity)
                                .colors(
                                        "Preview clock color",
                                        store.number("lockClockColor", 0xffffffff),
                                        color -> change("lockClockColor", color)),
                "Android clock settings",
                this::system);
        slider("Clock size", "lockClockSize", 19, 95, 53, "");
        slider("Clock position", "lockClockY", 5, 85, 19, "%");

        LinearLayout footer = ui.column();
        footer.setPadding(ui.dp(16), ui.dp(5), ui.dp(16), ui.dp(10));
        footer.addView(
                ui.button("Apply lock wallpaper", true, this::apply),
                new LinearLayout.LayoutParams(FILL, WRAP));
        activity.root.addView(footer, new LinearLayout.LayoutParams(FILL, WRAP));
    }

    private void row(String first, Runnable a, String second, Runnable b) {
        LinearLayout row = ui.row();
        row.addView(ui.button(first, false, a), new LinearLayout.LayoutParams(0, WRAP, 1));
        ui.gapHorizontal(row, 6);
        row.addView(ui.button(second, false, b), new LinearLayout.LayoutParams(0, WRAP, 1));
        content.addView(row);
        ui.gap(content, 6);
    }

    private void background() {
        java.util.ArrayList<String> labels = new java.util.ArrayList<>();
        java.util.ArrayList<Integer> ids = new java.util.ArrayList<>();
        labels.add("Solid color");
        ids.add(Wallpapers.SOLID);
        int home = Wallpapers.choice(store);
        if (home == Wallpapers.SOLID
                || home == Wallpapers.PHOTO
                || home >= Wallpapers.FIRST_IMAGE) {
            labels.add("Use Home wallpaper");
            ids.add(LockDesign.HOME_WALLPAPER);
        }
        for (int i = Wallpapers.FIRST_IMAGE; i < Wallpapers.NAMES.length; i++) {
            if (Wallpapers.NAMES[i].isEmpty()) continue;
            labels.add(Wallpapers.NAMES[i]);
            ids.add(i);
        }
        labels.add("Local photo");
        ids.add(Wallpapers.PHOTO);
        String[] names = labels.toArray(new String[0]);
        activity.dialog()
                .setTitle("Lock wallpaper")
                .setItems(
                        names,
                        (dialog, i) -> {
                            if (ids.get(i) == Wallpapers.PHOTO
                                    && activity.files.lockPhoto() == null)
                                activity.files.chooseLockPhoto();
                            else change("lockImage", ids.get(i));
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void change(String key, Object value) {
        store.set(key, value);
        redraw();
    }

    private void redraw() {
        draft = new LockDesign(activity);
        preview.invalidate();
        renderClock();
    }

    private void renderClock() {
        int style = store.number("lockClockStyle", 0);
        clock.setVisibility(style == 2 ? View.GONE : View.VISIBLE);
        clock.setSingleLine(style != 1);
        int format = store.number("lockClockFormat", 0);
        String twelve = style == 1 ? "h\nmm" : "h:mm";
        String twentyFour = style == 1 ? "HH\nmm" : "HH:mm";
        clock.setFormat12Hour(format == 2 ? twentyFour : twelve);
        clock.setFormat24Hour(format == 1 ? twelve : twentyFour);
        int align = store.number("lockClockAlign", 1);
        clock.setGravity(align == 0 ? Gravity.LEFT : align == 2 ? Gravity.RIGHT : Gravity.CENTER);
        clock.setPadding(ui.dp(10), 0, ui.dp(10), 0);
        clock.setTextColor(store.number("lockClockColor", 0xffffffff));
        clock.setTypeface(
                FlowUi.fontAt(activity, store.number("lockClockFont", store.number("font", 0))));
        FlowUi.weight(clock, style == 1 ? 620 : 350);
        float pixels = store.number("lockClockSize", 53) * Math.max(1, mockup.getWidth()) / 390f;
        clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, pixels);
        clock.setTranslationY(
                mockup.getHeight() * FlowUi.clamp(store.number("lockClockY", 19), 5, 85) / 100f);
    }

    private void pick(String title, String key, String[] names, int fallback) {
        int selected = FlowUi.clamp(store.number(key, fallback), 0, names.length - 1);
        activity.dialog()
                .setTitle(title)
                .setSingleChoiceItems(
                        names,
                        selected,
                        (dialog, index) -> {
                            dialog.dismiss();
                            change(key, index);
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void slider(String title, String key, int min, int max, int fallback, String unit) {
        int value = FlowUi.clamp(store.number(key, fallback), min, max);
        TextView label = ui.label(title + " · " + value + unit, 14, ui.text);
        content.addView(label);
        SeekBar slider = new SeekBar(activity);
        slider.setContentDescription(title);
        slider.setMax(max - min);
        slider.setProgress(value - min);
        content.addView(slider, new LinearLayout.LayoutParams(FILL, ui.dp(44)));
        slider.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    boolean dragging;

                    @Override
                    public void onStartTrackingTouch(SeekBar bar) {
                        dragging = true;
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar bar) {
                        dragging = false;
                        store.save();
                    }

                    @Override
                    public void onProgressChanged(SeekBar bar, int progress, boolean user) {
                        if (!user) return;
                        int v = progress + min;
                        FlowStore.put(store.options, key, v);
                        label.setText(title + " · " + v + unit);
                        redraw();
                        if (!dragging) store.save();
                    }
                });
    }

    private void resetCrop() {
        for (String key : new String[] {"lockZoom", "lockX", "lockY", "lockDim", "lockTextY"})
            store.options.remove(key);
        store.save();
        activity.refresh();
    }

    private void system() {
        try {
            activity.startActivity(new Intent(Intent.ACTION_SET_WALLPAPER));
        } catch (RuntimeException unavailable) {
            activity.startSafely(new Intent(Settings.ACTION_DISPLAY_SETTINGS));
        }
    }

    private void apply() {
        if (!draft.paperAvailable) {
            activity.toast("Choose a lock-screen background first");
            return;
        }
        activity.dialog()
                .setTitle("Apply to your lock screen?")
                .setMessage(
                        "Android will replace your current lock-screen wallpaper with this design.")
                .setPositiveButton(
                        "Apply", (dialog, which) -> activity.files.applyLock(draft, width, height))
                .setNegativeButton("Cancel", null)
                .show();
    }
}
