package com.rajan.rsflow;

import static com.rajan.rsflow.MainActivity.FILL;

import android.app.AlertDialog;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

final class IconGallery {
    private final MainActivity activity;
    private final FlowUi ui;

    IconGallery(MainActivity activity) {
        this.activity = activity;
        ui = activity.ui;
    }

    void choosePack(Consumer<String> selected) {
        List<String> packs = BuiltInIcons.PACKS;
        activity.dialog()
                .setTitle("Built-in icon packs")
                .setItems(
                        packs.stream().map(BuiltInIcons::name).toArray(String[]::new),
                        (dialog, index) -> selected.accept(packs.get(index)))
                .setNegativeButton("Cancel", null)
                .show();
    }

    void open(String pack, Consumer<String> selected) {
        LinearLayout panel = ui.column();
        panel.setPadding(ui.dp(18), ui.dp(8), ui.dp(18), 0);
        EditText search = activity.input("Search icons", "", 80, false);
        panel.addView(search);

        android.widget.GridView list = new android.widget.GridView(activity);
        list.setNumColumns(3);
        IconRows rows = new IconRows(pack);
        list.setAdapter(rows);
        final String[] groups = {
            "All icons",
            "Communication",
            "Media",
            "Games",
            "Finance",
            "Travel",
            "Nature",
            "Productivity",
            "Tools",
            "Animals",
            "Birds",
            "Robotics",
            "Computers"
        };
        TextView category = ui.command("Category · All icons", () -> {});
        category.setOnClickListener(
                v ->
                        activity.dialog()
                                .setTitle("Icon category")
                                .setItems(
                                        groups,
                                        (dialog, index) -> {
                                            rows.category = index;
                                            category.setText("Category · " + groups[index]);
                                            rows.filter(search.getText().toString());
                                        })
                                .show());
        panel.addView(category);
        rows.filter("");
        TextView empty = ui.label("No matching icons", 14, ui.dim);
        empty.setGravity(Gravity.CENTER);
        FrameLayout contents = new FrameLayout(activity);
        contents.addView(list, new FrameLayout.LayoutParams(FILL, FILL));
        contents.addView(empty, new FrameLayout.LayoutParams(FILL, FILL));
        list.setEmptyView(empty);
        int height = Math.min(380, activity.getResources().getConfiguration().screenHeightDp / 2);
        panel.addView(contents, new LinearLayout.LayoutParams(FILL, ui.dp(height)));
        MainActivity.watch(search, rows::filter);

        AlertDialog.Builder builder =
                activity.dialog()
                        .setTitle(BuiltInIcons.name(pack))
                        .setView(panel)
                        .setNegativeButton(selected == null ? "Close" : "Cancel", null);
        if (selected == null) {
            builder.setPositiveButton(
                    "Apply pack",
                    (dialog, which) -> {
                        if (BuiltInIcons.colorful(pack)
                                && activity.store.number("icons", 0) == 3)
                            FlowStore.put(activity.store.options, "icons", 0);
                        activity.store.set("iconPack", pack);
                        activity.refresh();
                    });
        }
        AlertDialog dialog = builder.create();
        if (selected != null) {
            list.setOnItemClickListener(
                    (parent, view, position, id) -> {
                        dialog.dismiss();
                        selected.accept(BuiltInIcons.choice(pack, rows.getItem(position)));
                    });
        }
        panel.setFocusableInTouchMode(true);
        panel.requestFocus();
        dialog.show();
        dialog.getWindow()
                .setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    private static int group(String icon) {
        return switch (icon) {
            case "phone",
                            "messages",
                            "mail",
                            "contacts",
                            "chat",
                            "paperplane",
                            "social",
                            "snapshot",
                            "satellite" ->
                    1;
            case "cassette",
                            "turntable",
                            "filmstrip",
                            "music_queue",
                            "gallery_stack",
                            "camera_roll",
                            "radio_wave",
                            "music",
                            "gallery",
                            "video",
                            "headphones",
                            "camera",
                            "podcast",
                            "paint",
                            "palette",
                            "guitar",
                            "piano",
                            "vinyl",
                            "drums",
                            "ticket",
                            "theatre",
                            "film",
                            "recorder" ->
                    2;
            case "gamepad",
                            "chess",
                            "bowling",
                            "trophy",
                            "puzzle",
                            "football",
                            "cards",
                            "fitness" ->
                    3;
            case "safe",
                            "candles",
                            "receipt",
                            "wallet",
                            "shopping",
                            "bank",
                            "coins",
                            "credit",
                            "chart",
                            "store" ->
                    4;
            case "map_fold",
                            "maps",
                            "transport",
                            "travel",
                            "compass",
                            "bicycle",
                            "train",
                            "rocket" ->
                    5;
            case "forest_tree",
                            "wavecrest",
                            "crystal",
                            "weather",
                            "moon",
                            "star",
                            "leaf",
                            "mountain",
                            "planet",
                            "telescope",
                            "rainbow",
                            "sunrise",
                            "flower",
                            "food",
                            "coffee" ->
                    6;
            case "browser",
                            "calendar",
                            "calculator",
                            "files",
                            "cloud",
                            "document",
                            "spreadsheet",
                            "slides",
                            "notes",
                            "book",
                            "translate",
                            "education",
                            "news",
                            "focus",
                            "bookmark",
                            "lab",
                            "flask" ->
                    7;
            case "moth", "cat", "fox", "paw", "whale" -> 9;
            case "heron", "owl", "swallow", "feather" -> 10;
            case "robot", "rover", "robot_arm" -> 11;
            case "window_stack", "chip", "network", "code", "monitor" -> 12;
            default -> 8;
        };
    }

    private final class IconRows extends BaseAdapter {
        private final List<BuiltInIcons.Symbol> visible = new ArrayList<>();
        private final String pack;

        int category;

        IconRows(String pack) {
            this.pack = pack;
        }

        void filter(String raw) {
            String query = raw.trim().toLowerCase(Locale.ROOT);
            visible.clear();
            for (BuiltInIcons.Symbol symbol : activity.builtInIcons.catalog()) {
                if ((category == 0 || category == group(symbol.id)) && symbol.matches(query))
                    visible.add(symbol);
            }
            if (query.isEmpty() && category == 0 && BuiltInIcons.DESKTOP.equals(pack)) {
                List<String> featured =
                        java.util.Arrays.asList(
                                "maps",
                                "music",
                                "gallery",
                                "camera",
                                "files",
                                "phone",
                                "calendar",
                                "browser");
                visible.sort(
                        java.util.Comparator.comparingInt(
                                icon -> {
                                    int pos = featured.indexOf(icon.id);
                                    return pos < 0 ? featured.size() : pos;
                                }));
            }
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return visible.size();
        }

        @Override
        public BuiltInIcons.Symbol getItem(int index) {
            return visible.get(index);
        }

        @Override
        public long getItemId(int index) {
            return index;
        }

        @Override
        public View getView(int index, View convert, ViewGroup parent) {
            LinearLayout row = convert == null ? ui.column() : (LinearLayout) convert;
            row.removeAllViews();
            row.setGravity(Gravity.CENTER);
            row.setPadding(0, ui.dp(5), 0, ui.dp(5));
            BuiltInIcons.Symbol symbol = getItem(index);
            row.addView(
                    activity.appIcon("", 40, ui.panel, BuiltInIcons.choice(pack, symbol)),
                    new LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)));
            ui.gap(row, 4);
            row.addView(ui.label(symbol.name, 15, ui.text));
            return row;
        }
    }
}
