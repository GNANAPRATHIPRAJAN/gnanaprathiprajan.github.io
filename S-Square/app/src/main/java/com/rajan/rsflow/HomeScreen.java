package com.rajan.rsflow;

import static com.rajan.rsflow.MainActivity.FILL;
import static com.rajan.rsflow.MainActivity.WRAP;

import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;

final class HomeScreen {
    private final MainActivity activity;
    private final FlowUi ui;
    private final FlowStore store;
    private final TileEditor editor;
    private android.graphics.Bitmap panorama;
    private final ArrayList<View> windows = new ArrayList<>();
    private static final int[] VIVID = {0xff0053d7, 0xffb00069, 0xff006e68, 0xffc03700, 0xff5016bd, 0xff276908};

    HomeScreen(MainActivity activity) {
        this.activity = activity;
        ui = activity.ui;
        store = activity.store;
        editor = new TileEditor(activity);
    }

    void build() {
        LinearLayout content = activity.scrollContent();
        content.setOnLongClickListener(
                v -> {
                    menu();
                    return true;
                });
        if (store.number("tileFinish", 0) == 1) {
            int source = Wallpapers.choice(store);
            panorama = source == Wallpapers.PHOTO ? activity.files.wallpaper() : activity.wallpapers.image(source);
            if (panorama == null) panorama = activity.wallpapers.image(5);
            activity.contentScroll.setOnScrollChangeListener((v, x, y, oldX, oldY) -> {
                for (View window : windows) window.invalidate();
            });
        }
        grid(content);
        if (!activity.arranging && store.flag("floatingCube", true))
            new CubeWindow(activity).attach(activity.homeViewport);
    }

    void menu() {
        Sheet s = new Sheet(activity, "Start");
        if (!store.flag("lockTiles", false)) {
            s.add("+", "Add tile", editor::add);
            s.add("▦", "Widget", () -> activity.widgets.choose(activity.page(), null));
            s.add("▧", "Folder", () -> editor.addFolder(activity.page()));
            s.add("✥", "Arrange", activity::toggleArrange);
        }
        s.add("✎", "Customize", () -> activity.customize());
        s.add("☷", "Action Center", () -> activity.setScreen(MainActivity.Screen.ACTION_CENTER));
        if (!activity.defaultHomeActive()) s.add("⌂", "Default Home", activity::requestHome);
        s.show();
    }

    private void grid(LinearLayout content) {
        java.util.List<FlowStore.Tile> displayed = new java.util.ArrayList<>();
        for (FlowStore.Tile tile : activity.page().tiles) {
            if (!"clock".equals(tile.live) || store.flag("showClock", true)) displayed.add(tile);
        }
        int columns = FlowUi.clamp(store.number("columns", 4), 1, 5) * 2;
        int gap = ui.dp(FlowUi.clamp(store.number("gap", store.desktop() ? 8 : 5), 2, 24));
        int height = ui.dp(FlowUi.clamp(store.number("height", 102), 80, 172));
        int rowHeight = Math.max(ui.dp(36), (height - gap) / 2);
        ArrayList<TileLayout.Span> spans = new ArrayList<>();
        for (FlowStore.Tile tile : displayed) {
            if (FlowStore.WIDGET.equals(tile.type)
                    || FlowStore.WIDGET_PLACEHOLDER.equals(tile.type)) {
                int width =
                        tile.size == 1
                                ? Math.min(2, columns)
                                : tile.size == 2 ? Math.min(4, columns) : columns;
                spans.add(new TileLayout.Span(width, tile.size == 4 ? 5 : 3));
            } else {
                spans.add(
                        TileLayout.span(
                                tile.size == 0 && tile.wide ? TileLayout.FULL : tile.size,
                                columns));
            }
        }
        TileBoard board = new TileBoard(activity, columns, gap, rowHeight, spans);
        board.restore(displayed);
        board.onEditing =
                () -> {
                    activity.arranging = true;

                    if (activity.cubeView != null) activity.cubeView.setVisibility(View.GONE);
                };
        board.setOnLongClickListener(
                v -> {
                    menu();
                    return true;
                });
        for (int i = 0; i < spans.size(); i++) {
            FlowStore.Tile tile = displayed.get(i);
            TileLayout.Span span = spans.get(i);
            View view = tile(tile, span.rows() == 1, span.columns());
            board.addView(view);
            if (!store.flag("lockTiles", false)) board.enableDragging(view, tile);
        }

        content.addView(board, new LinearLayout.LayoutParams(FILL, WRAP));
        if (!store.flag("lockTiles", false)) {
            TextView add = ui.command("+", editor::add);
            add.setContentDescription("Add tile");
            add.setBackground(ui.surface(ui.panel, 24, 0));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48));
            lp.gravity = Gravity.END; lp.topMargin = ui.dp(10);
            content.addView(add, lp);
        }
    }

    private View folderPreview(FlowStore.Tile tile, int size, int color) {
        LinearLayout preview = ui.column();
        preview.setGravity(Gravity.CENTER);
        int mini = Math.max(9, (size - 2) / 2);
        for (int i = 0; i < Math.min(4, tile.items.size()); i += 2) {
            LinearLayout pair = ui.row();
            pair.addView(activity.appIcon(tile.items.get(i), mini, color, "@bare-original"));
            if (i + 1 < tile.items.size()) {
                ui.gapHorizontal(pair, 2);
                pair.addView(activity.appIcon(tile.items.get(i + 1), mini, color, "@bare-original"));
            }
            preview.addView(pair);
            if (i == 0 && tile.items.size() > 2) ui.gap(preview, 2);
        }
        return preview;
    }

    private View tile(FlowStore.Tile tile, boolean compact, int columns) {
        if (FlowStore.ROTATING.equals(tile.type)) {
            RotatingApps view = new RotatingApps(activity, tile);
            activity.tileViews.put(view, tile);
            return view;
        }
        int shell = store.desktop()
                ? FlowUi.blend(ui.panel, ui.accent, ui.light ? .10f : .18f)
                : ui.accent;
        boolean adaptive = store.flag("appColors", false);
        if (adaptive && FlowStore.APP.equals(tile.type))
            shell = activity.appStyle.color(activity, activity.face(tile));
        if (store.flag("tintedTiles", false)) shell = FlowUi.blend(shell, ui.accent, .24f);
        if (store.number("defaultTileColor", 0) != 0) shell = store.number("defaultTileColor", 0);
        if (tile.color != 0) shell = tile.color;
        boolean styled = tile.color == 0 && store.number("defaultTileColor", 0) == 0 && !adaptive;
        int finish = store.number("tileFinish", 0);
        if (styled && store.flag("vividTiles", false)) shell = VIVID[Math.floorMod((tile.app + tile.live + tile.label + tile.type).hashCode(), VIVID.length)];
        if (finish == 2) shell = FlowUi.blend(shell, 0xff17130e, .3f);
        int windowTint = styled ? 0 : (shell & 0x00ffffff) | 0x66000000;
        if (finish == 1) shell = 0xff111111;
        final int shellColor = shell;
        int fg = FlowUi.readable(shell);
        int corner = FlowUi.clamp(store.number("corner", store.desktop() ? 14 : 3), 0, 34);
        boolean widgetTile = FlowStore.WIDGET.equals(tile.type);
        FrameLayout frame =
                widgetTile && !store.flag("lockTiles", false)
                        ? new WidgetFrame(activity)
                        : new FrameLayout(activity);
        int opacity = FlowUi.clamp(store.number("tileOpacity", store.desktop() ? 88 : 100), 30, 100) * 255 / 100;
        if (store.number("tileFinish", 0) == 2) opacity = FlowUi.clamp(store.number("glassTint", 50), 12, 88) * 255 / 100;
        int surface = (shell & 0xffffff) | (opacity << 24);
        frame.setBackground(
                ui.surface(
                        surface,
                        compact ? Math.min(corner, 12) : corner,
                        (store.flag("tileOutline", false) && tile.color == 0)
                                ? FlowUi.blend(shell, ui.text, .22f)
                                : 0));
        frame.setClipToOutline(true);
        if (widgetTile) {
            frame.setBackground(null);
            frame.setClipToOutline(false);
        } else if (store.desktop()) frame.setElevation(ui.dp(2));
        if (!widgetTile && finish == 1 && panorama != null) {
            View window = new PanoramaWindow(activity, panorama, activity.homeViewport, store);
            if(windowTint!=0) window.setForeground(new android.graphics.drawable.ColorDrawable(windowTint));
            windows.add(window);
            frame.addView(window, new FrameLayout.LayoutParams(FILL, FILL));
        }
        activity.tileViews.put(frame, tile);
        String label = activity.tileName(tile);
        boolean locked = store.flag("lockTiles", false);
        if (FlowStore.LIVE.equals(tile.type)) {
            frame.addView(
                    LiveFlipTile.supported(tile) && tile.liveFlip != 0
                            ? new LiveFlipTile(activity, tile, fg, compact)
                            : LiveTile.content(activity, tile, fg, compact),
                    new FrameLayout.LayoutParams(FILL, FILL));
            frame.setContentDescription(label);
            frame.setFocusable(true);
            frame.setOnClickListener(
                    view -> {
                        if (activity.arranging && activity.selectedTile == tile) editor.edit(tile);
                        else if (activity.arranging) ((TileBoard) view.getParent()).select(view);
                        else editor.open(tile);
                    });
            if (!locked)
                frame.setOnLongClickListener(
                        view -> {
                            editor.edit(tile);
                            return true;
                        });
            return frame;
        }
        if (FlowStore.WIDGET.equals(tile.type)) {
            View widget = activity.widgets.view(tile);
            frame.addView(widget, new FrameLayout.LayoutParams(FILL, FILL));
            if (!locked)
                frame.setOnLongClickListener(
                        v -> {
                            editor.edit(tile);
                            return true;
                        });
            return frame;
        }

        boolean showIcon =
                compact || !FlowStore.APP.equals(tile.type) || store.flag("showTileIcons", true);
        boolean showLabel =
                !compact
                        && (!FlowStore.APP.equals(tile.type)
                                || store.flag("showLabels", true)
                                || !showIcon);
        LinearLayout stack = ui.column();
        stack.setGravity(Gravity.CENTER);
        stack.setPadding(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(4));
        frame.addView(stack, new FrameLayout.LayoutParams(FILL, FILL));
        if (FlowStore.NOTE.equals(tile.type) && !compact && !tile.body.isEmpty()) {
            TextView excerpt = ui.label(tile.body, 15, fg);
            excerpt.setMaxLines(columns >= 4 ? 4 : 3);
            excerpt.setPadding(ui.dp(6),ui.dp(3),ui.dp(5),0);
            excerpt.setEllipsize(TextUtils.TruncateAt.END);
            excerpt.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            View rule=new View(activity);rule.setBackgroundColor(fg);
            LinearLayout.LayoutParams line=new LinearLayout.LayoutParams(ui.dp(22),ui.dp(2));line.gravity=Gravity.START;line.leftMargin=ui.dp(6);
            stack.addView(rule,line);
            stack.addView(excerpt, new LinearLayout.LayoutParams(FILL, 0, 1));
        } else if (showIcon) {
            int preferred = FlowStore.FOLDER.equals(tile.type) ? ui.dp(84) : ui.dp(FlowUi.clamp(tile.iconDp > 0 ? tile.iconDp : store.number("iconSize", 36), 20, 64));
            FrameLayout iconWell =
                    new FrameLayout(activity) {
                        int size;

                        @Override
                        protected void onMeasure(int w, int h) {
                            int side =
                                    Math.min(
                                            preferred,
                                            Math.min(MeasureSpec.getSize(w), MeasureSpec.getSize(h))
                                                    - ui.dp(3));
                            side = Math.max(1, side);
                            if (size != side) {
                                size = side;
                                int dp =
                                        Math.max(
                                                1,
                                                Math.round(
                                                        side
                                                                / getResources()
                                                                        .getDisplayMetrics()
                                                                        .density));
                                View icon;
                                if (FlowStore.APP.equals(tile.type))
                                    icon = activity.tileIcon(tile, dp, shellColor);
                                else if (FlowStore.FOLDER.equals(tile.type)
                                        && !tile.items.isEmpty())
                                    icon = folderPreview(tile, dp, shellColor);
                                else
                                    icon =
                                            ui.symbol(
                                                    FlowStore.FOLDER.equals(tile.type)
                                                            ? "folder"
                                                            : FlowStore.NOTE.equals(tile.type)
                                                                    ? "note"
                                                                    : "apps",
                                                    fg,
                                                    dp);
                                removeAllViewsInLayout();
                                addViewInLayout(
                                        icon,
                                        0,
                                        new FrameLayout.LayoutParams(side, side, Gravity.CENTER),
                                        true);
                            }
                            super.onMeasure(w, h);
                        }
                    };
            stack.addView(iconWell, new LinearLayout.LayoutParams(FILL, 0, 1));
        }
        if (showLabel) {
            int labelSp = FlowUi.clamp(store.number("labelSize", 12), 11, 20);
            TextView title = ui.label(label, labelSp, fg);
            title.setMaxLines(1);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setGravity(Gravity.CENTER_VERTICAL | (store.desktop() ? Gravity.CENTER_HORIZONTAL : Gravity.START));
            if (!store.desktop()) title.setPadding(ui.dp(4), 0, ui.dp(4), 0);
            title.setAutoSizeTextTypeUniformWithConfiguration(
                    8,
                    Math.max(9, Math.round(labelSp * ui.textScale)),
                    1,
                    android.util.TypedValue.COMPLEX_UNIT_SP);
            title.setLayoutParams(
                    new LinearLayout.LayoutParams(
                            FILL,
                            ui.dp(
                                    Math.min(
                                            25,
                                            Math.max(
                                                    18, Math.round(labelSp * ui.textScale + 5))))));
            stack.addView(title);
        }
        if (!compact
                && store.flag("gestures", true)
                && store.flag("tileGestures", true)
                && tile.swipes.length() > 0) {
            FrameLayout.LayoutParams arrow =
                    new FrameLayout.LayoutParams(ui.dp(13), ui.dp(13), Gravity.TOP | Gravity.START);
            arrow.setMargins(ui.dp(5), ui.dp(5), ui.dp(5), 0);
            frame.addView(ui.symbol("swipe", fg, 13), arrow);
        }
        if (FlowStore.APP.equals(tile.type)) {
            TextView badge = ui.label("", compact ? 9 : 11, ui.ink);
            FlowUi.weight(badge, 720);
            badge.setGravity(Gravity.CENTER);
            badge.setMinWidth(ui.dp(compact ? 15 : 21));
            badge.setPadding(ui.dp(3), ui.dp(1), ui.dp(3), ui.dp(1));
            badge.setBackground(ui.rounded(ui.accent, 12, 0));
            badge.setTag("notificationCount");
            badge.setVisibility(View.GONE);
            badge.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            FrameLayout.LayoutParams placement =
                    new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP | Gravity.END);
            placement.setMargins(ui.dp(3), ui.dp(3), ui.dp(3), ui.dp(3));
            frame.addView(badge, placement);
        }
        frame.setContentDescription(label);
        frame.setFocusable(true);
        frame.setOnClickListener(
                view -> {
                    if (activity.arranging && activity.selectedTile == tile) editor.edit(tile);
                    else if (activity.arranging) ((TileBoard) view.getParent()).select(view);
                    else editor.open(tile);
                });
        if (!locked)
            frame.setOnLongClickListener(
                    view -> {
                        editor.edit(tile);
                        return true;
                    });
        return frame;
    }
}
