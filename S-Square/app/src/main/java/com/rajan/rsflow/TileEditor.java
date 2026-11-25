package com.rajan.rsflow;

import static com.rajan.rsflow.MainActivity.FILL;
import static com.rajan.rsflow.MainActivity.WRAP;

import android.app.AlertDialog;
import android.graphics.Color;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

final class TileEditor {
    private final MainActivity activity;
    private final FlowStore store;
    private final FlowUi ui;

    TileEditor(MainActivity activity) {
        this.activity = activity;
        store = activity.store;
        ui = activity.ui;
    }

    void add() {
        FlowStore.Page page = activity.page();
        Sheet sheet = new Sheet(activity, "Add to Home");
        sheet.add(
                "▦",
                "App",
                () ->
                        activity.chooseApp(
                                "Pin app",
                                app -> {
                                    if (activity.addApp(app, page)) activity.refresh();
                                },
                                null));
        sheet.add("⊞", "Multiple apps", () -> addApps(page));
        sheet.add("▧", "Folder", () -> addFolder(page));
        sheet.add("◴", "Live tile", () -> addLive(page));

        sheet.add(
                "✎",
                "Note",
                () -> {
                    FlowStore.Tile n = new FlowStore.Tile(FlowStore.NOTE);
                    noteDialog(
                            n,
                            () -> {
                                if (activity.addTile(n, page)) activity.refresh();
                            });
                });
        sheet.show();
    }

    private void addApps(FlowStore.Page page) {
        List<AppCatalog.App> apps = activity.apps.list();
        String[] labels = new String[apps.size()];
        boolean[] checked = new boolean[apps.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = apps.get(i).label;
        activity.dialog()
                .setTitle("Pin apps to Start")
                .setMultiChoiceItems(labels, checked, (dialog, index, on) -> checked[index] = on)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(
                        "Pin",
                        (dialog, which) -> {
                            int count = 0;
                            for (boolean on : checked) if (on) count++;
                            if (count == 0) return;
                            if (page.tiles.size() + count > FlowStore.MAX_TILES) {
                                activity.toast("There is not enough room for this selection");
                                return;
                            }
                            for (int i = 0; i < checked.length; i++)
                                if (checked[i]) {
                                    FlowStore.Tile tile = new FlowStore.Tile(FlowStore.APP);
                                    tile.app = apps.get(i).key;
                                    page.tiles.add(tile);
                                    activity.revealTile = tile;
                                }
                            store.save();
                            activity.refresh();
                            activity.toast(count + " pinned to Start");
                        })
                .show();
    }

    private void addLive(FlowStore.Page page) {
        activity.dialog()
                .setTitle("Add a live tile")
                .setItems(
                        LiveTile.LABELS,
                        (dialog, selected) -> {
                            int index = selected;
                            FlowStore.Tile tile = new FlowStore.Tile(FlowStore.LIVE);
                            tile.live = LiveTile.IDS[index];
                            tile.liveFlip = LiveFlipTile.supported(tile) ? 2 : 0;
                            if (index == 0) FlowStore.put(store.options, "showClock", true);
                            if (index <= 1 || index == 3) tile.size = TileLayout.WIDE;
                            if (activity.addTile(tile, page)) activity.refresh();
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }

    void addFolder(FlowStore.Page page) {
        if (page.tiles.size() >= FlowStore.MAX_TILES) {
            activity.toast("This page is full");
            return;
        }
        activity.prompt(
                "New folder",
                "Folder name",
                "",
                60,
                false,
                name -> {
                    FlowStore.Tile folder = new FlowStore.Tile(FlowStore.FOLDER);
                    folder.label = name.isEmpty() ? "Folder" : name;
                    if (activity.addTile(folder, page)) {
                        activity.refresh();
                        folder(folder);
                    }
                });
    }

    void open(FlowStore.Tile tile) {
        switch (tile.type) {
            case FlowStore.APP -> activity.launch(activity.face(tile));
            case FlowStore.FOLDER -> folder(tile);
            case FlowStore.NOTE -> noteDialog(tile, this::changed);
            case FlowStore.LIVE -> {
                switch (tile.live) {
                    case "photo" -> activity.media.openGallery();
                    case "music" -> activity.media.music();
                    case "people" -> {
                        if (activity.apps.find(tile.app) != null) activity.launch(tile.app);
                        else
                            activity.startSafely(
                                    new android.content.Intent(android.content.Intent.ACTION_MAIN)
                                            .addCategory(
                                                    "people".equals(tile.live)
                                                            ? android.content.Intent
                                                                    .CATEGORY_APP_CONTACTS
                                                            : android.content.Intent
                                                                    .CATEGORY_APP_MUSIC));
                    }
                    case "battery" ->
                            activity.startSafely(
                                    new android.content.Intent(
                                            android.provider.Settings
                                                    .ACTION_BATTERY_SAVER_SETTINGS));
                    case "calendar" ->
                            activity.startSafely(
                                    new android.content.Intent(android.content.Intent.ACTION_MAIN)
                                            .addCategory(
                                                    android.content.Intent.CATEGORY_APP_CALENDAR));
                    default ->
                            activity.startSafely(
                                    new android.content.Intent(
                                            android.provider.AlarmClock.ACTION_SHOW_ALARMS));
                }
            }
            case FlowStore.WIDGET_PLACEHOLDER -> activity.widgets.choose(activity.page(), tile);
            default -> {}
        }
    }

    void edit(FlowStore.Tile tile) {
        FlowStore.Page page = activity.page();
        Sheet s = new Sheet(activity, activity.tileName(tile));
        s.add("↗", "Resize", () -> resize(tile));
        boolean widget =
                FlowStore.WIDGET.equals(tile.type)
                        || FlowStore.WIDGET_PLACEHOLDER.equals(tile.type);
        if (!widget) s.add("◉", "Color", () -> tileColor(tile));
        s.add(
                "✎",
                "Rename",
                () ->
                        activity.prompt(
                                "Rename",
                                "Tile name",
                                tile.label,
                                60,
                                false,
                                name -> {
                                    tile.label = name;
                                    changed();
                                }));
        if (FlowStore.APP.equals(tile.type)) {
            s.add("▣", "Icon", () -> tileIcon(tile));
            s.add("↔", "Icon size", () -> {
                Sheet sizes = new Sheet(activity, "Icon size");
                for (int dp : new int[] {0, 24, 32, 40, 48, 56, 64}) {
                    sizes.add(dp == tile.iconDp ? "✓" : "·", dp == 0 ? "Use global size" : dp + " dp", () -> {
                        tile.iconDp = dp;
                        changed();
                    });
                }
                sizes.show();
            });
            s.add(
                    "◯",
                    "Shape",
                    () -> {
                        String[] names = {
                            "Use global", "Rounded", "Square", "Circle", "Monochrome", "Free glyph"
                        };
                        Sheet shapes = new Sheet(activity, "Icon shape");
                        for (int i = 0; i < names.length; i++) {
                            int shape = i - 1;
                            shapes.add(
                                    new String[] {"↺", "▢", "□", "○", "◐", "✦"}[i],
                                    names[i],
                                    () -> {
                                        tile.shape = shape;
                                        changed();
                                    });
                        }
                        shapes.show();
                    });
            s.add(
                    "▤",
                    "Category",
                    () -> {
                        Sheet groups = new Sheet(activity, "App category");
                        groups.add(
                                "↺",
                                "Automatic",
                                () -> {
                                    tile.category = "";
                                    changed();
                                });
                        for (String name : AppStyle.NAMES)
                            groups.add(
                                    "•",
                                    name,
                                    () -> {
                                        tile.category = name;
                                        changed();
                                    });
                        groups.show();
                    });
            s.add("☏", "Contact photo", () -> activity.media.pick(MediaTiles.CONTACT, tile));
            s.add(
                    "⇄",
                    "Change app",
                    () ->
                            activity.chooseApp(
                                    "Primary app",
                                    app -> {
                                        tile.app = app.key;
                                        tile.face = "";
                                        changed();
                                    },
                                    null));
        }
        if (LiveFlipTile.supported(tile))
            s.add(
                    "↻",
                    "Live flip",
                    () ->
                            activity.dialog()
                                    .setTitle("Live tile flip")
                                    .setSingleChoiceItems(
                                            new String[] {"Off", "Manual", "Automatic"},
                                            tile.liveFlip,
                                            (dialog, index) -> {
                                                dialog.dismiss();
                                                tile.liveFlip = index;
                                                changed();
                                            })
                                    .setNegativeButton("Cancel", null)
                                    .show());
        if (!widget) s.add("⬒", "Swipe actions", () -> swipeMenu(tile));
        if (FlowStore.FOLDER.equals(tile.type)) s.add("▧", "Folder apps", () -> folder(tile));
        if (FlowStore.NOTE.equals(tile.type))
            s.add("✎", "Edit note", () -> noteDialog(tile, this::changed));
        if (FlowStore.WIDGET_PLACEHOLDER.equals(tile.type))
            s.add("⊞", "Rebind widget", () -> activity.widgets.choose(page, tile));
        s.add("✥", "Arrange", activity::toggleArrange);
        s.add(
                "−",
                "Remove",
                () ->
                        activity.dialog()
                                .setTitle("Remove " + activity.tileName(tile) + "?")
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton(
                                        "Remove",
                                        (d, w) -> {
                                            page.tiles.remove(tile);
                                            activity.files.discardTileIcon(tile);
                                            changed();
                                            activity.widgets.delete(tile);
                                        })
                                .show());
        s.show();
    }

    private void resize(FlowStore.Tile tile) {
        String[] names = {"Small", "Standard", "Wide", "Large", "Tall", "Full row"};
        int selected =
                tile.size == 0 ? (tile.wide ? TileLayout.FULL : TileLayout.STANDARD) : tile.size;
        boolean widget =
                FlowStore.WIDGET.equals(tile.type)
                        || FlowStore.WIDGET_PLACEHOLDER.equals(tile.type);
        if (widget)
            names =
                    new String[] {
                        "Width of one standard tile", "Wide", "Full row", "Full row, extra tall"
                    };
        Sheet sheet = new Sheet(activity, "Tile size");
        for (int i = 0; i < names.length; i++) {
            int pick = i + 1;
            sheet.add(
                    new String[] {"▪", "□", "▬", "▣", "▮", "↔"}[i],
                    names[i],
                    () -> {
                        tile.size = pick;
                        changed();
                    });
        }
        sheet.show();
    }

    private void tileIcon(FlowStore.Tile tile) {
        Sheet sheet = new Sheet(activity, "Tile artwork");
        sheet.add("↺", "Global", () -> setArt(tile, ""));
        sheet.add(
                activity.appIcon(tile.app, 36, ui.panel, "@original"),
                "Original",
                () -> setArt(tile, "@original"),
                null);
        sheet.add("▧", "Local image", () -> activity.files.chooseTileIcon(tile));
        for (String pack : BuiltInIcons.PACKS)
            sheet.add(
                    "✦",
                    BuiltInIcons.name(pack),
                    () -> new IconGallery(activity).open(pack, icon -> setArt(tile, icon)));
        sheet.add("◐", SrArtwork.LABEL, () -> setArt(tile, SrArtwork.PACK));
        sheet.show();
    }

    private void setArt(FlowStore.Tile tile, String icon) {
        activity.files.discardTileIcon(tile);
        tile.icons = icon;
        tile.face = "";
        changed();
    }

    void assign(FlowStore.Tile tile, String direction, boolean options) {
        if (!options) {
            activity.chooseApp(
                    "Choose the app for swipe " + direction,
                    app -> {
                        tile.setSwipe(direction, app.key);
                        changed();
                        activity.toast("Swipe " + direction + " for " + app.label);
                    },
                    null);
            return;
        }
        String[] actions = {"Open another app", "Go to Home", "Open app drawer", "Clear action"};
        activity.dialog()
                .setTitle("Swipe " + direction)
                .setItems(
                        actions,
                        (d, index) -> {
                            if (index == 0) assign(tile, direction, false);
                            else {
                                tile.setSwipe(
                                        direction,
                                        index == 1 ? "@home" : index == 2 ? "@apps" : "");
                                changed();
                            }
                        })
                .show();
    }

    private void swipeMenu(FlowStore.Tile tile) {
        ArrayList<String> labels = new ArrayList<>();
        ArrayList<Runnable> options = new ArrayList<>();
        for (String direction : FlowStore.DIRECTIONS) {
            String key = tile.swipe(direction);
            String value =
                    switch (key) {
                        case "" -> "Unassigned";
                        case "@home" -> "Home";
                        case "@apps" -> "App drawer";
                        default -> activity.appName(key);
                    };
            offer(
                    labels,
                    options,
                    "Swipe " + direction + "   ·   " + value,
                    () -> assign(tile, direction, true));
            if (!key.isEmpty())
                offer(
                        labels,
                        options,
                        "Run " + direction + " action",
                        () -> {
                            if ("@home".equals(key)) activity.goHome();
                            else if ("@apps".equals(key))
                                activity.setScreen(MainActivity.Screen.APPS);
                            else activity.launch(key);
                        });
        }
        list("Swipe actions", labels, options);
    }

    private void folder(FlowStore.Tile tile) {
        boolean locked = store.flag("lockTiles", false);
        LinearLayout frame = ui.column();
        frame.setPadding(ui.dp(16), ui.dp(6), ui.dp(16), ui.dp(14));
        android.widget.GridView list = new android.widget.GridView(activity);
        list.setNumColumns(activity.getResources().getConfiguration().screenWidthDp >= 385 ? 4 : 3);
        list.setHorizontalSpacing(ui.dp(6));
        list.setVerticalSpacing(ui.dp(8));
        list.setStretchMode(android.widget.GridView.STRETCH_COLUMN_WIDTH);
        int height =
                Math.min(
                        440,
                        Math.max(
                                112,
                                activity.getResources().getConfiguration().screenHeightDp
                                        * 48
                                        / 100));
        frame.addView(list, new LinearLayout.LayoutParams(FILL, ui.dp(height)));
        list.setAdapter(
                new android.widget.BaseAdapter() {
                    public int getCount() {
                        return tile.items.size();
                    }

                    public String getItem(int position) {
                        return tile.items.get(position);
                    }

                    public long getItemId(int position) {
                        return position;
                    }

                    public View getView(int position, View convert, android.view.ViewGroup parent) {
                        LinearLayout row = convert == null ? ui.column() : (LinearLayout) convert;
                        row.removeAllViews();
                        row.setGravity(android.view.Gravity.CENTER);
                        row.setPadding(ui.dp(8), ui.dp(7), ui.dp(8), ui.dp(7));
                        row.setMinimumHeight(ui.dp(48));
                        String key = getItem(position);
                        row.addView(activity.appIcon(key, 34, ui.panel));
                        ui.gap(row, 6);
                        TextView label = ui.label(activity.appName(key), 12, ui.text);
                        label.setMaxLines(2);
                        label.setGravity(
                                android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL);
                        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
                        row.setContentDescription(activity.appName(key));
                        row.addView(label, new LinearLayout.LayoutParams(FILL, ui.dp(34)));
                        return row;
                    }
                });
        if (tile.items.isEmpty()) {
            list.setVisibility(View.GONE);
            frame.addView(ui.label("This folder is empty", 15, ui.dim));
        }
        AlertDialog.Builder builder =
                activity.dialog()
                        .setTitle(activity.tileName(tile))
                        .setView(frame)
                        .setNegativeButton("Done", null);
        if (!locked) builder.setPositiveButton("Add apps", (d, w) -> folderPicker(tile));
        AlertDialog window = builder.create();
        list.setOnItemClickListener(
                (p, v, i, id) -> {
                    window.dismiss();
                    activity.launch(tile.items.get(i));
                });
        if (!locked)
            list.setOnItemLongClickListener(
                    (p, v, i, id) -> {
                        String key = tile.items.get(i);
                        activity.dialog()
                                .setTitle("Remove " + activity.appName(key) + " from this folder?")
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton(
                                        "Remove",
                                        (dialog, w) -> {
                                            tile.items.remove(key);
                                            window.dismiss();
                                            changed();
                                            folder(tile);
                                        })
                                .show();
                        return true;
                    });
        window.show();
        android.view.View decor = window.getWindow().getDecorView();
        if (store.number("motion", 1) != 0
                && android.animation.ValueAnimator.areAnimatorsEnabled()) {
            decor.setAlpha(0);
            decor.setScaleX(.96f);
            decor.setScaleY(.96f);
            decor.animate().alpha(1).scaleX(1).scaleY(1).setDuration(160).start();
        }
    }

    private void folderPicker(FlowStore.Tile tile) {
        List<AppCatalog.App> all = activity.apps.list();
        String[] labels = new String[all.size()];
        boolean[] checked = new boolean[all.size()];
        for (int i = 0; i < all.size(); i++) {
            labels[i] = all.get(i).label;
            checked[i] = tile.items.contains(all.get(i).key);
        }
        activity.dialog()
                .setTitle("Folder apps")
                .setMultiChoiceItems(labels, checked, (d, i, on) -> checked[i] = on)
                .setNegativeButton("Cancel", (d, w) -> folder(tile))
                .setPositiveButton(
                        "Save",
                        (d, w) -> {
                            java.util.LinkedHashSet<String> chosen =
                                    new java.util.LinkedHashSet<>(tile.items);
                            for (int i = 0; i < all.size(); i++) {
                                if (checked[i]) chosen.add(all.get(i).key);
                                else chosen.remove(all.get(i).key);
                            }
                            if (chosen.size() > FlowStore.MAX_FOLDER_APPS) {
                                activity.toast(
                                        "Choose up to " + FlowStore.MAX_FOLDER_APPS + " apps");
                                folderPicker(tile);
                                return;
                            }
                            tile.items.clear();
                            tile.items.addAll(chosen);
                            changed();
                            folder(tile);
                        })
                .show();
    }

    private void noteDialog(FlowStore.Tile note, Runnable save) {
        LinearLayout list = ui.column();
        list.setPadding(ui.dp(18), ui.dp(9), ui.dp(18), 0);
        android.widget.EditText label = activity.input("Title", note.label, 60, false);
        android.widget.EditText body = activity.input("Note", note.body, 500, true);
        body.setMinLines(4);
        body.setGravity(android.view.Gravity.TOP);
        list.addView(label);
        ui.gap(list, 8);
        list.addView(body);
        activity.dialog()
                .setTitle("Note")
                .setView(list)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(
                        "Save",
                        (d, w) -> {
                            note.label = label.getText().toString().trim();
                            note.body = body.getText().toString().trim();
                            save.run();
                        })
                .show();
    }

    private View scroller(LinearLayout list) {
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(list, new ScrollView.LayoutParams(FILL, WRAP));
        return scroll;
    }

    private void tileColor(FlowStore.Tile tile) {
        colors(
                "Tile color",
                tile.color == 0 ? ui.accent : tile.color,
                c -> {
                    tile.color = c;
                    changed();
                });
    }

    void colors(String title, int value, java.util.function.IntConsumer select) {
        Sheet s = new Sheet(activity, title);
        if (title.equals("Tile color")) s.add("↺", "Automatic", () -> select.accept(0));
        s.add("#", "Hex color", () -> hex(title, value, select));
        for (int i = 0; i < SrArtwork.SWATCHES.length; i++) {
            int c = SrArtwork.SWATCHES[i];
            View v = new View(activity);
            v.setBackground(ui.rounded(c, 12, ui.outline));
            s.add(v, SrArtwork.SWATCH_NAMES[i], () -> select.accept(c), null);
        }
        s.show();
    }

    void hex(String title, int current, java.util.function.IntConsumer save) {
        activity.prompt(
                title,
                "#RRGGBB",
                String.format(java.util.Locale.ROOT, "#%06X", current & 0xffffff),
                7,
                false,
                value -> {
                    String hex = value.startsWith("#") ? value : "#" + value;
                    if (!hex.matches("#[0-9a-fA-F]{6}"))
                        activity.toast("Enter a six-digit hex color");
                    else save.accept(Color.parseColor(hex));
                });
    }

    private void changed() {
        store.save();
        activity.refresh();
    }

    private static void offer(
            List<String> labels, List<Runnable> actions, String title, Runnable action) {
        labels.add(title);
        actions.add(action);
    }

    private void list(String title, List<String> labels, List<Runnable> actions) {
        activity.dialog()
                .setTitle(title)
                .setItems(
                        labels.toArray(new String[0]), (dialog, index) -> actions.get(index).run())
                .setNegativeButton("Close", null)
                .show();
    }
}
