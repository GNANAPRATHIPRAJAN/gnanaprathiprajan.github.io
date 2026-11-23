package com.rajan.rsflow;

import static com.rajan.rsflow.MainActivity.FILL;
import static com.rajan.rsflow.MainActivity.WRAP;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Map;

final class Customize {
    private final MainActivity activity;
    private final FlowStore store;
    private final FlowUi ui;
    private LinearLayout content;
    private LinearLayout group;

    Customize(MainActivity activity) {
        this.activity = activity;
        ui = activity.ui;
        store = activity.store;
    }

    void build() {
        activity.header("Customize", "Home", activity::leaveSettings);
        content = activity.scrollContent();
        section("Windows style");
        LinearLayout switcher = ui.row();
        group.addView(switcher);
        for (int i = 0; i < 2; i++) {
            boolean modern = i == 1;
            if (modern) ui.gapHorizontal(switcher, 8);
            TextView style =
                    ui.button(
                            modern ? "Windows 11" : "Windows 10",
                            store.desktop() == modern,
                            () -> {
                                FlowStore.put(store.options, "corner", modern ? 18 : 3);
                                store.set("shell", modern ? 1 : 0);
                                activity.refresh();
                            });
            style.setSelected(store.desktop() == modern);
            switcher.addView(style, new LinearLayout.LayoutParams(0, ui.dp(50), 1));
        }
        action("Themes", "Astra, Pistachio, Cobalt Metro and more", this::themes);
        action(
                "Astra icons",
                "Preview and apply the new two-tone pack",
                () -> new IconGallery(activity).open(BuiltInIcons.ASTRA, null));
        home();
        tiles();
        apps();
    }

    private void home() {
        section("Appearance");
        action("Midnight Glass preset", "Preview the four-column layout", this::previewMidnight);

        action(
                "Lock screen",
                "Wallpaper and clock preview",
                () -> activity.setScreen(MainActivity.Screen.LOCK_SCREEN));
        options("Color theme", "theme", FlowUi.THEMES, 0);
        action(
                "Accent color",
                String.format(java.util.Locale.ROOT, "#%06X", ui.accent & 0xffffff),
                this::accent);
        options("Font", "font", FlowUi.FONT_NAMES, 3);
        range("Launcher text scale", "textScale", 75, 150, 100, "%");
        options("Screen transitions", "motion", new String[] {"Instant", "Fast", "Smooth"}, 1);
        action("Home wallpaper", Wallpapers.NAMES[Wallpapers.choice(store)], this::wallpaper);
        action(
                "Solid wallpaper color",
                "Swatches or #RRGGBB",
                () ->
                        new TileEditor(activity)
                                .colors(
                                        "Solid wallpaper",
                                        store.number("solidColor", ui.base),
                                        c -> {
                                            store.set("solidColor", c);
                                            store.set("background", Wallpapers.SOLID);
                                            activity.goHome();
                                        }));
        action("Choose photo", "Browse images on this device", activity.files::choosePhoto);
        options("Wallpaper sizing", "wallScale", new String[] {"Fill screen", "Fit image"}, 0);
        range("Wallpaper dimming", "wallDim", 0, 95, 20, "%");

        section("Home and widgets");
        toggle("Full-screen Start and Apps", "fullHome", true, null);
        toggle(
                "Floating shortcut cube",
                "floatingCube",
                true,
                "Swipe to turn, long-press for placement");
        section("Clock");
        toggle("Show Home clock", "showClock", true, null);
        toggle("Show date", "showDate", true, null);
        options("Clock style", "clockStyle", new String[] {"Display", "Compact", "Light"}, 0);
        options("Clock alignment", "clockAlign", new String[] {"Left", "Center", "Right"}, 0);
        options(
                "Time format",
                "clockFormat",
                new String[] {"System default", "12-hour", "24-hour"},
                0);

        section("Action Center");
        toggle(
                "Show notification counts on tiles",
                "badges",
                false,
                "Requires Android notification access");
        action(
                "Android notification access",
                "Optional · view and manage active Android notifications",
                () -> NotificationAccess.open(activity));
        if (android.os.Build.VERSION.SDK_INT >= 33)
            action(
                    "Android blocked notification access?",
                    null,
                    () -> NotificationAccess.help(activity));
        section("Gestures");
        toggle("Enable swipe gestures", "gestures", true, null);
        toggle(
                "Swipe down to Action Center",
                "actionCenterSwipe",
                true,
                "From empty space when Home is scrolled to the top");
        toggle("Swipe right to the previous page", "drawerHomeSwipe", true, "From All apps");
        toggle("Swipe up to All apps", "homeAppsSwipe", true, "From the bottom of Start");
        toggle("Swipe left to the next page", "appsSideSwipe", true, "Start → All apps");
        toggle(
                "Enable app and tile swipe shortcuts",
                "tileGestures",
                true,
                "Set a direction in a tile’s long-press menu");
        toggle(
                "Swipe up on a tile to assign a second app",
                "quickAssign",
                false,
                "When Home can’t scroll in that direction");
        toggle("Double-tap empty Home space to customize", "doubleTap", false, null);
        toggle("Gesture vibration", "haptics", true, null);
        range("Swipe distance", "gestureDistance", 35, 105, 60, " dp");

        section("Data and launcher");
        action(
                "Default Home app",
                activity.isDefault() ? "S-Square is active" : "Set S-Square as your launcher",
                activity::requestHome);
        action(
                "Android settings",
                "Manage system navigation and other device settings",
                () -> activity.startSafely(new Intent(Settings.ACTION_SETTINGS)));
        action(
                "Export layout and settings",
                "Save a local JSON backup",
                activity.files::exportLayout);
        action(
                "Import layout and settings",
                "Restore an RS Flow or SRLauncher JSON file",
                activity.files::importLayout);
        action(
                "Import copied backup",
                "Restore JSON copied from SRLauncher",
                activity.files::importClipboard);
        action("About S-Square", "Version 2.2.0 · Developed by gnanaprathiprajan", this::about);
    }

    private void tiles() {
        section("Tiles");
        toggle("App-colored tiles", "appColors", false, null);
        toggle("Tile outlines", "tileOutline", false, null);
        toggle("Cube swipe faces", "cubeFaces", true, "Turn to the linked app, then tap to open");
        toggle("Lock Home layout", "lockTiles", false, null);
        toggle(
                "Compact tile packing",
                "denseTiles",
                true,
                "Fill gaps between tiles of different sizes");
        range("Start side margin", "tilesMargin", 6, 32, 12, " dp");
        options(
                "Columns",
                "columns",
                new String[] {"1", "2", "3", "4", "5"},
                new int[] {1, 2, 3, 4, 5},
                4);
        toggle(
                "Square tiles",
                "squareTiles",
                !store.options.has("height"),
                "Match tile height to the column width");
        range("Tile height with square tiles off", "height", 80, 172, 102, " dp");
        range("Tile corner radius", "corner", 0, 34, store.desktop() ? 20 : 16, " dp");
        range("Tile spacing", "gap", 2, 24, 7, " dp");
        range("Tile opacity", "tileOpacity", 30, 100, 88, "%");
        int defaultColor = store.number("defaultTileColor", 0);
        action(
                "Default tile color",
                defaultColor == 0
                        ? "Follow theme"
                        : String.format(java.util.Locale.ROOT, "#%06X", defaultColor & 0xffffff),
                () ->
                        activity.dialog()
                                .setTitle("Default tile color")
                                .setItems(
                                        new String[] {"Follow theme", "Custom hex color"},
                                        (dialog, index) -> {
                                            if (index == 0) {
                                                store.set("defaultTileColor", 0);
                                                activity.refresh();
                                            } else
                                                new TileEditor(activity)
                                                        .hex(
                                                                "Default tile color",
                                                                defaultColor == 0
                                                                        ? ui.panel
                                                                        : defaultColor,
                                                                color -> {
                                                                    store.set(
                                                                            "defaultTileColor",
                                                                            color);
                                                                    activity.refresh();
                                                                });
                                        })
                                .show());
        range("Tile icon size", "iconSize", 20, 64, 36, " dp");
        range("Tile label size", "labelSize", 11, 20, 12, " sp");
        toggle("Tint tiles with the accent", "tintedTiles", false, null);
        toggle("Show tile labels", "showLabels", true, null);
        toggle("Show app icons on tiles", "showTileIcons", true, null);

        section("Tile icons");
        options(
                "Icon style",
                "icons",
                new String[] {"Rounded", "Square", "Circle", "Monochrome", "Tile glyph"},
                0);
        String selected = store.option("iconPack", "");
        Map<String, String> packs = activity.iconPacks.available();
        action("Icon pack", IconPacks.title(selected, packs), () -> iconPack(packs, "iconPack"));
        action(
                "Desktop Color",
                "Preview Maps, Music, Photos and more",
                () -> new IconGallery(activity).open(BuiltInIcons.DESKTOP, null));
        action(
                "Browse built-in icons",
                activity.builtInIcons.catalog().size() + " designs, including Desktop Color",
                () -> {
                    IconGallery gallery = new IconGallery(activity);
                    gallery.choosePack(pack -> gallery.open(pack, null));
                });
    }

    private void apps() {
        section("Icons and app drawer");
        range("App list icon size", "drawerIconSize", 22, 64, 32, " dp");
        range("App list label size", "drawerLabelSize", 12, 30, 17, " sp");
        toggle("Quick search field", "drawerSearch", true, null);
        toggle("Compact rows", "drawerCompact", true, null);
        Map<String, String> packs = activity.iconPacks.available();
        String selected =
                store.option("drawerPack", store.option("iconPack", BuiltInIcons.DESKTOP));
        action(
                "App drawer icons",
                IconPacks.title(selected, packs),
                () -> iconPack(packs, "drawerPack"));
        toggle(
                "Show recently installed apps",
                "recentApps",
                false,
                "Apps installed within the past seven days");
        action("Hidden apps", null, this::hidden);
    }

    private void themes() {
        ScrollView scroll = new ScrollView(activity);
        LinearLayout items = ui.column();
        items.setPadding(ui.dp(14), ui.dp(9), ui.dp(14), ui.dp(10));
        scroll.addView(items);
        AlertDialog dialog =
                activity.dialog()
                        .setTitle("Theme presets")
                        .setView(scroll)
                        .setNegativeButton("Cancel", null)
                        .create();
        for (ThemePreset preset : ThemePreset.values()) {
            LinearLayout row = ui.row();
            row.setPadding(ui.dp(7), ui.dp(6), ui.dp(9), ui.dp(6));
            row.setBackground(ui.surface(ui.panel, 14, ui.outline));
            ImageView preview = new ImageView(activity);
            preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            preview.setImageBitmap(activity.wallpapers.thumbnail(preset.paper));
            row.addView(preview, new LinearLayout.LayoutParams(ui.dp(45), ui.dp(65)));
            ui.gapHorizontal(row, 12);
            row.addView(
                    description(
                            preset.label,
                            BuiltInIcons.name(preset.tilePack) + " · wallpaper and palette"),
                    new LinearLayout.LayoutParams(0, WRAP, 1));
            android.view.View color = new View(activity);
            color.setBackground(ui.rounded(preset.accent, 8, 0));
            row.addView(color, new LinearLayout.LayoutParams(ui.dp(21), ui.dp(21)));
            row.setFocusable(true);
            row.setOnClickListener(
                    v -> {
                        dialog.dismiss();
                        preset.apply(store);
                        activity.refresh();
                    });
            items.addView(row, new LinearLayout.LayoutParams(FILL, WRAP));
            ui.gap(items, 8);
        }
        dialog.show();
    }

    private void previewMidnight() {
        LinearLayout panel = ui.column();
        panel.setPadding(ui.dp(14), ui.dp(9), ui.dp(14), ui.dp(8));
        android.widget.FrameLayout canvas = new android.widget.FrameLayout(activity);
        canvas.setBackground(ui.rounded(0xff141625, 19, ui.outline));
        canvas.setClipToOutline(true);
        ImageView paper = new ImageView(activity);
        paper.setScaleType(ImageView.ScaleType.CENTER_CROP);
        paper.setImageBitmap(activity.wallpapers.image(Wallpapers.MIDNIGHT));
        canvas.addView(paper, new android.widget.FrameLayout.LayoutParams(FILL, FILL));
        LinearLayout tiles = ui.column();
        tiles.setPadding(ui.dp(10), ui.dp(10), ui.dp(10), 0);
        canvas.addView(tiles, new android.widget.FrameLayout.LayoutParams(FILL, FILL));
        TextView clock = ui.label("09:42", 25, 0xffeff0ff);
        clock.setGravity(android.view.Gravity.CENTER);
        clock.setBackground(ui.rounded(0xe329304d, 12, 0xff54617f));
        tiles.addView(clock, new LinearLayout.LayoutParams(FILL, ui.dp(48)));
        String[] names = {
            "Phone", "Gallery", "Music", "Files", "Camera", "Apps", "Settings", "Weather"
        };
        String[] marks = {
            "phone", "gallery", "music", "files", "camera", "browser", "settings", "weather"
        };
        for (int r = 0; r < 2; r++) {
            ui.gap(tiles, 6);
            LinearLayout row = ui.row();
            tiles.addView(row);
            for (int i = 0; i < 4; i++) {
                if (i > 0) ui.gapHorizontal(row, 5);
                int index = r * 4 + i;
                LinearLayout tile = ui.column();
                tile.setGravity(android.view.Gravity.CENTER);
                tile.setBackground(ui.rounded(0xe329304d, 11, 0xff54617f));
                ImageView figure = new ImageView(activity);
                figure.setImageResource(
                        activity.getResources()
                                .getIdentifier(
                                        "flow_line_" + marks[index],
                                        "drawable",
                                        activity.getPackageName()));
                tile.addView(figure, new LinearLayout.LayoutParams(ui.dp(20), ui.dp(20)));
                ui.gap(tile, 3);
                TextView caption = ui.label(names[index], 9, 0xffeff0ff);
                caption.setGravity(android.view.Gravity.CENTER);
                caption.setMaxLines(1);
                tile.addView(caption, new LinearLayout.LayoutParams(FILL, WRAP));
                row.addView(tile, new LinearLayout.LayoutParams(0, ui.dp(51), 1));
            }
        }
        panel.addView(canvas, new LinearLayout.LayoutParams(FILL, ui.dp(200)));
        activity.dialog()
                .setTitle("Midnight Glass")
                .setView(panel)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(
                        "Apply",
                        (dialog, button) -> {
                            store.applyMidnightStyle();
                            activity.goHome();
                        })
                .show();
    }

    private void section(String title) {
        if (group != null) ui.gap(content, 19);
        TextView label = ui.label(title, 17, ui.text);
        FlowUi.weight(label, 700);
        if (android.os.Build.VERSION.SDK_INT >= 28) label.setAccessibilityHeading(true);
        content.addView(label);
        ui.gap(content, 8);
        group = ui.column();
        group.setBackground(ui.rounded(ui.panel, 16, ui.outline));
        group.setPadding(ui.dp(9), ui.dp(7), ui.dp(9), ui.dp(7));
        content.addView(group, new LinearLayout.LayoutParams(FILL, WRAP));
    }

    private LinearLayout description(String title, String subtitle) {
        LinearLayout text = ui.column();
        TextView label = ui.label(title, 15, ui.text);
        FlowUi.medium(label);
        text.addView(label);
        if (subtitle != null && !subtitle.isEmpty()) {
            ui.gap(text, 4);
            text.addView(ui.label(subtitle, 12, ui.dim));
        }
        return text;
    }

    private LinearLayout entry(String title, String subtitle) {
        LinearLayout row = ui.row();
        row.setMinimumHeight(ui.dp(53));
        row.setPadding(ui.dp(7), ui.dp(8), ui.dp(7), ui.dp(8));
        row.addView(description(title, subtitle), new LinearLayout.LayoutParams(0, WRAP, 1));
        group.addView(row, new LinearLayout.LayoutParams(FILL, WRAP));
        return row;
    }

    private void action(String title, String subtitle, Runnable work) {
        LinearLayout row = entry(title, subtitle);
        ui.gapHorizontal(row, 7);
        row.addView(ui.symbol("next", ui.dim, 17));
        row.setBackground(ui.surface(android.graphics.Color.TRANSPARENT, 11, 0));
        row.setOnClickListener(v -> work.run());
        row.setFocusable(true);
        row.setContentDescription(title + (subtitle == null ? "" : ", " + subtitle));
    }

    private void toggle(String title, String key, boolean fallback, String subtitle) {
        LinearLayout row = entry(title, subtitle);
        ui.gapHorizontal(row, 9);
        Switch toggle = new Switch(activity);
        toggle.setContentDescription(title);
        toggle.setChecked(store.flag(key, fallback));
        toggle.setThumbTintList(
                new ColorStateList(
                        new int[][] {{android.R.attr.state_checked}, {}},
                        new int[] {ui.accent, ui.dim}));
        toggle.setTrackTintList(ColorStateList.valueOf(ui.outline));
        toggle.setOnCheckedChangeListener(
                (button, enabled) -> {
                    store.set(key, enabled);
                    if ("badges".equals(key)) BadgeListener.enable(enabled);
                });
        row.addView(toggle, new LinearLayout.LayoutParams(WRAP, WRAP));
        row.setOnClickListener(v -> toggle.toggle());
    }

    private void options(String title, String key, String[] choices, int fallback) {
        int[] values = new int[choices.length];
        for (int i = 0; i < values.length; i++) values[i] = i;
        options(title, key, choices, values, fallback);
    }

    private void options(String title, String key, String[] choices, int[] values, int fallback) {
        int current = store.number(key, fallback), checked = 0;
        for (int i = 0; i < values.length; i++) if (values[i] == current) checked = i;
        int selected = checked;
        action(
                title,
                choices[selected],
                () ->
                        activity.dialog()
                                .setTitle(title)
                                .setSingleChoiceItems(
                                        choices,
                                        selected,
                                        (dialog, i) -> {
                                            dialog.dismiss();
                                            store.set(key, values[i]);
                                            activity.refresh();
                                        })
                                .setNegativeButton("Cancel", null)
                                .show());
    }

    private void range(String title, String key, int min, int max, int fallback, String units) {
        LinearLayout panel = ui.column();
        panel.setPadding(ui.dp(7), ui.dp(8), ui.dp(7), ui.dp(4));
        int initial = FlowUi.clamp(store.number(key, fallback), min, max);
        TextView label = ui.label(title + " · " + initial + units, 14, ui.text);
        FlowUi.medium(label);
        panel.addView(label);
        SeekBar slider = new SeekBar(activity);
        slider.setContentDescription(title);
        slider.setMax(max - min);
        slider.setProgress(initial - min);
        slider.setThumbTintList(ColorStateList.valueOf(ui.accent));
        slider.setProgressTintList(ColorStateList.valueOf(ui.accent));
        slider.setProgressBackgroundTintList(ColorStateList.valueOf(ui.outline));
        panel.addView(slider, new LinearLayout.LayoutParams(FILL, ui.dp(43)));
        group.addView(panel, new LinearLayout.LayoutParams(FILL, WRAP));
        slider.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    private boolean dragging;

                    @Override
                    public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                        label.setText(title + " · " + (min + progress) + units);
                        if (fromUser && !dragging) store.set(key, min + progress);
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar bar) {
                        dragging = true;
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar bar) {
                        dragging = false;
                        store.set(key, min + bar.getProgress());
                        if ("textScale".equals(key)) activity.refresh();
                    }
                });
    }

    private void accent() {
        ArrayList<String> values = new ArrayList<>();
        java.util.Collections.addAll(values, FlowUi.ACCENT_NAMES);
        values.add("SRLauncher palette");
        values.add("Custom hex color");
        activity.dialog()
                .setTitle("Accent color")
                .setItems(
                        values.toArray(new String[0]),
                        (dialog, index) -> {
                            if (index < FlowUi.ACCENTS.length) saveAccent(FlowUi.ACCENTS[index]);
                            else if (index == FlowUi.ACCENTS.length) {
                                activity.dialog()
                                        .setTitle("SRLauncher palette")
                                        .setItems(
                                                SrArtwork.SWATCH_NAMES,
                                                (palette, selected) ->
                                                        saveAccent(SrArtwork.SWATCHES[selected]))
                                        .setNegativeButton("Cancel", null)
                                        .show();
                            } else
                                new TileEditor(activity)
                                        .hex("Accent color", ui.accent, this::saveAccent);
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveAccent(int color) {
        store.set("accent", color);
        activity.refresh();
    }

    void wallpaper() {
        ScrollView scroll = new ScrollView(activity);
        LinearLayout items = ui.column();
        items.setPadding(ui.dp(18), ui.dp(5), ui.dp(18), ui.dp(5));
        scroll.addView(items);
        AlertDialog dialog =
                activity.dialog()
                        .setTitle("Home wallpaper")
                        .setView(scroll)
                        .setNegativeButton("Cancel", null)
                        .create();
        int selected = Wallpapers.choice(store);
        for (int display = 0; display < Wallpapers.NAMES.length; display++) {
            int i =
                    display < Wallpapers.FIRST_IMAGE
                            ? display
                            : display < Wallpapers.FIRST_IMAGE + 10
                                    ? Wallpapers.FIRST_BOKEH + display - Wallpapers.FIRST_IMAGE
                                    : display - 10;
            if (i == Wallpapers.FIRST_BOKEH || i == Wallpapers.FIRST_IMAGE) {
                TextView groupTitle =
                        ui.label(
                                i == Wallpapers.FIRST_BOKEH
                                        ? "Bokeh collection"
                                        : "Studio collection",
                                17,
                                ui.text);
                groupTitle.setPadding(0, ui.dp(12), 0, ui.dp(8));
                items.addView(groupTitle);
            }
            int choice = i;
            Runnable select =
                    () -> {
                        dialog.dismiss();
                        if (choice == Wallpapers.PHOTO && activity.files.wallpaper() == null) {
                            activity.files.choosePhoto();
                        } else {
                            store.set("background", choice);
                            if (activity.screen == MainActivity.Screen.HOME) activity.refresh();
                            else activity.goHome();
                        }
                    };
            if (i < Wallpapers.FIRST_IMAGE) {
                items.addView(ui.button(Wallpapers.NAMES[i], i == selected, select));
            } else {
                LinearLayout preview = ui.row();
                preview.setFocusable(true);
                preview.setContentDescription(Wallpapers.NAMES[i]);
                preview.setOnClickListener(view -> select.run());
                preview.setBackground(
                        ui.surface(i == selected ? ui.accent : ui.panel, 13, ui.outline));
                preview.setPadding(ui.dp(8), ui.dp(7), ui.dp(8), ui.dp(7));
                ImageView image = new ImageView(activity);
                image.setImageBitmap(activity.wallpapers.thumbnail(i));
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                preview.addView(image, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(82)));
                ui.gapHorizontal(preview, 14);
                preview.addView(
                        ui.label(Wallpapers.NAMES[i], 15, i == selected ? ui.ink : ui.text));
                items.addView(preview, new LinearLayout.LayoutParams(FILL, WRAP));
            }
            ui.gap(items, 7);
        }
        dialog.show();
    }

    private void iconPack(Map<String, String> installed, String setting) {
        ArrayList<String> ids = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();
        ids.add("");
        labels.add(IconPacks.title("", installed));
        for (String key : BuiltInIcons.PACKS) {
            ids.add(key);
            labels.add(IconPacks.title(key, installed));
        }
        ids.add(SrArtwork.PACK);
        labels.add(SrArtwork.LABEL + " · built in");
        installed.forEach(
                (key, value) -> {
                    ids.add(key);
                    labels.add(value);
                });
        int checked =
                ids.indexOf(store.option(setting, store.option("iconPack", BuiltInIcons.DESKTOP)));
        activity.dialog()
                .setTitle("Icon pack")
                .setSingleChoiceItems(
                        labels.toArray(new String[0]),
                        checked,
                        (dialog, index) -> {
                            dialog.dismiss();
                            store.set(setting, ids.get(index));
                            activity.refresh();
                        })
                .setNegativeButton("Close", null)
                .show();
    }

    private void hidden() {
        if (store.hidden.isEmpty() && store.hiddenPackages.isEmpty()) {
            activity.dialog()
                    .setTitle("Hidden apps")
                    .setMessage("Long-press an app in All apps to hide it.")
                    .setPositiveButton("Close", null)
                    .show();
            return;
        }
        ArrayList<String> keys = new ArrayList<>(store.hidden);
        keys.addAll(store.hiddenPackages);
        String[] names = new String[keys.size()];
        for (int i = 0; i < keys.size(); i++)
            names[i] =
                    "Show "
                            + (activity.apps.find(keys.get(i)) == null
                                    ? keys.get(i)
                                    : activity.appName(keys.get(i)));
        activity.dialog()
                .setTitle("Hidden apps")
                .setItems(
                        names,
                        (dialog, index) -> {
                            store.hidden.remove(keys.get(index));
                            store.hiddenPackages.remove(keys.get(index));
                            store.save();
                            activity.refresh();
                        })
                .setNeutralButton(
                        "Show all",
                        (dialog, index) -> {
                            store.hidden.clear();
                            store.hiddenPackages.clear();
                            store.save();
                            activity.refresh();
                        })
                .setNegativeButton("Close", null)
                .show();
    }

    private void about() {
        activity.dialog()
                .setTitle("S-Square")
                .setMessage(
                        "Version 2.2.0\n"
                                + "Developed by gnanaprathiprajan\n\n"
                                + "S-Square works offline. Your tiles, notes, preferences and"
                                + " selected background are kept on this device. All customization"
                                + " controls are available without an account.\n\n"
                                + "Manrope, Space Grotesk and DM Sans are distributed under the SIL"
                                + " Open Font License.")
                .setPositiveButton("Close", null)
                .show();
    }
}
