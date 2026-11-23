package com.rajan.rsflow;

import static com.rajan.rsflow.MainActivity.FILL;
import static com.rajan.rsflow.MainActivity.WRAP;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;

/** The desktop taskbar and Start menu operate on the active Home page's pins. */
final class DesktopShell {
    private final MainActivity activity;
    private final FlowUi ui;
    private final FlowStore store;
    private final TileEditor editor;

    DesktopShell(MainActivity activity) {
        this.activity = activity;
        ui = activity.ui;
        store = activity.store;
        editor = new TileEditor(activity);
    }

    private ArrayList<FlowStore.Tile> pinnedApps() {
        ArrayList<FlowStore.Tile> pins = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        for (FlowStore.Tile tile : activity.page().tiles) {
            if (FlowStore.APP.equals(tile.type)
                    && seen.add(tile.app)
                    && activity.apps.find(tile.app) != null) pins.add(tile);
        }
        return pins;
    }

    void taskbar(Runnable controls) {
        LinearLayout bar = ui.row();
        bar.setGravity(
                (store.number("taskbarAlign", 0) == 0 ? Gravity.CENTER_HORIZONTAL : Gravity.START)
                        | Gravity.CENTER_VERTICAL);
        bar.setPadding(ui.dp(8), ui.dp(5), ui.dp(8), ui.dp(5));
        int panel = ui.panel;
        bar.setBackground(ui.rounded(panel, 16, ui.outline));
        ArrayList<FlowStore.Tile> pins = pinnedApps();
        addKey(
                bar,
                "Start menu",
                ui.symbol("start", ui.light ? 0xff0064af : 0xff64bfff, 24),
                this::open);
        addKey(
                bar,
                "All apps",
                ui.symbol("apps", ui.text, 22),
                () -> activity.setScreen(MainActivity.Screen.APPS));
        int width = activity.getResources().getConfiguration().screenWidthDp;
        int available = Math.max(0, Math.min(3, (width - 32) / 52 - 4));
        int shortcuts =
                Math.min(
                        pins.size(),
                        Math.min(available, FlowUi.clamp(store.number("taskbarPins", 2), 0, 3)));
        for (int i = 0; i < shortcuts; i++) {
            FlowStore.Tile tile = pins.get(i);
            addKey(
                    bar,
                    activity.tileName(tile),
                    activity.tileIcon(tile, 26, panel),
                    () -> activity.launch(tile.app));
        }
        addKey(
                bar,
                "Action Center",
                ui.symbol("action", ui.text, 22),
                () -> activity.setScreen(MainActivity.Screen.ACTION_CENTER));
        addKey(bar, "Home controls", ui.symbol("more", ui.text, 21), controls);
        LinearLayout.LayoutParams placement = new LinearLayout.LayoutParams(WRAP, WRAP);
        placement.gravity =
                store.number("taskbarAlign", 0) == 0 ? Gravity.CENTER_HORIZONTAL : Gravity.START;
        placement.setMargins(ui.dp(8), ui.dp(7), ui.dp(8), ui.dp(9));
        activity.root.addView(bar, placement);
    }

    private void addKey(LinearLayout bar, String title, View icon, Runnable click) {
        FrameLayout key = new FrameLayout(activity);
        key.setBackground(ui.surface(Color.TRANSPARENT, 9, 0));
        key.setContentDescription(title);
        key.setFocusable(true);
        key.setOnClickListener(view -> click.run());
        key.addView(icon, new FrameLayout.LayoutParams(ui.dp(25), ui.dp(25), Gravity.CENTER));
        bar.addView(key, new LinearLayout.LayoutParams(ui.dp(52), ui.dp(48)));
    }

    void open() {
        Dialog menu = new Dialog(activity);
        menu.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout card = ui.column();
        card.setPadding(ui.dp(16), ui.dp(15), ui.dp(16), ui.dp(8));
        card.setBackground(ui.rounded(ui.panel, 18, ui.outline));
        TextView search =
                ui.button(
                        "Search apps",
                        false,
                        () -> {
                            menu.dismiss();
                            activity.searchApps();
                        });
        search.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        search.setTextColor(ui.dim);
        search.setBackground(ui.surface(ui.base, 8, ui.outline));
        card.addView(search);
        ui.gap(card, 8);
        LinearLayout heading = ui.row();
        TextView pinned = ui.label("Pinned", 18, ui.text);
        FlowUi.medium(pinned);
        heading.addView(pinned, new LinearLayout.LayoutParams(0, WRAP, 1));
        heading.addView(
                ui.command(
                        "All apps  ›",
                        () -> {
                            menu.dismiss();
                            activity.setScreen(MainActivity.Screen.APPS);
                        }));
        card.addView(heading);
        ScrollView scroll = new ScrollView(activity);
        LinearLayout grid = ui.column();
        ArrayList<FlowStore.Tile> apps = pinnedApps();
        int widthDp = Math.min(activity.getResources().getConfiguration().screenWidthDp - 16, 500);
        int columns =
                Math.min(
                        FlowUi.clamp(store.number("startColumns", 4), 2, 6),
                        Math.max(2, (widthDp - 32) / 62));
        LinearLayout row = null;
        for (int i = 0; i < apps.size(); i++) {
            if (i % columns == 0) {
                row = ui.row();
                grid.addView(row, new LinearLayout.LayoutParams(FILL, WRAP));
            }
            FlowStore.Tile tile = apps.get(i);
            LinearLayout cell = ui.column();
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(ui.dp(2), ui.dp(5), ui.dp(2), ui.dp(5));
            String name = activity.tileName(tile);
            int size = FlowUi.clamp(store.number("startIconSize", 36), 22, 60);
            cell.addView(
                    activity.tileIcon(tile, size, ui.panel),
                    new LinearLayout.LayoutParams(ui.dp(size), ui.dp(size)));
            ui.gap(cell, 4);
            TextView caption = ui.label(name, 12, ui.text);
            caption.setMaxLines(2);
            caption.setEllipsize(TextUtils.TruncateAt.END);
            caption.setGravity(Gravity.CENTER);
            cell.addView(caption);
            cell.setBackground(ui.surface(Color.TRANSPARENT, 9, 0));
            cell.setContentDescription(name);
            cell.setFocusable(true);
            cell.setOnClickListener(
                    v -> {
                        menu.dismiss();
                        activity.launch(tile.app);
                    });
            if (!store.flag("lockTiles", false))
                cell.setOnLongClickListener(
                        v -> {
                            menu.dismiss();
                            editor.edit(tile);
                            return true;
                        });
            row.addView(cell, new LinearLayout.LayoutParams(0, WRAP, 1));
        }
        if (apps.isEmpty())
            grid.addView(ui.label("Pin apps to this Home page to see them here.", 15, ui.dim));
        else if (apps.size() % columns != 0) {
            for (int i = apps.size() % columns; i < columns; i++)
                row.addView(new View(activity), new LinearLayout.LayoutParams(0, 1, 1));
        }
        scroll.addView(grid);
        card.addView(scroll, new LinearLayout.LayoutParams(FILL, 0, 1));
        LinearLayout foot = ui.row();
        if (!store.flag("lockTiles", false))
            foot.addView(
                    ui.command(
                            "+ Add",
                            () -> {
                                menu.dismiss();
                                editor.add();
                            }));
        foot.addView(new View(activity), new LinearLayout.LayoutParams(0, 1, 1));
        foot.addView(
                ui.command(
                        "Customize",
                        () -> {
                            menu.dismiss();
                            activity.setScreen(MainActivity.Screen.SETTINGS);
                        }));
        card.addView(foot);
        menu.setContentView(card);
        Window window = menu.getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = ui.dp(widthDp);
        int availableHeight = Math.max(1, activity.root.getHeight() - ui.dp(12));
        int rows = Math.max(1, (apps.size() + columns - 1) / columns);
        params.height =
                Math.min(
                        availableHeight,
                        ui.dp(
                                172
                                        + Math.min(3, rows)
                                                * (FlowUi.clamp(
                                                                store.number("startIconSize", 36),
                                                                22,
                                                                60)
                                                        + 48)));
        params.y = Math.min(ui.dp(66), Math.max(0, availableHeight - params.height));
        params.dimAmount = .3f;
        window.setAttributes(params);
        menu.setCanceledOnTouchOutside(true);
        activity.trackStartMenu(menu);
        menu.show();
    }
}
