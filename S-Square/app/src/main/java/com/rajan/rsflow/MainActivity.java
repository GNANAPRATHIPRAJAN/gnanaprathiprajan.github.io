package com.rajan.rsflow;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class MainActivity extends Activity {
    enum Screen {
        HOME,
        TILES,
        APPS,
        SETTINGS,
        ACTION_CENTER,
        LOCK_SCREEN
    }

    static final int FILL = ViewGroup.LayoutParams.MATCH_PARENT;
    static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    static final int PICK_PHOTO = 103,
            EXPORT = 104,
            IMPORT = 105,
            PICK_TILE_PHOTO = 106,
            PICK_ICON = 107,
            PICK_LOCK_PHOTO = 108;
    FlowStore store;
    MediaTiles media;
    AppCatalog apps;
    final AppStyle appStyle = new AppStyle();
    private String filter = "";

    String face(FlowStore.Tile t) {
        return store.flag("cubeFaces", true) && !t.face.isEmpty() && apps.find(t.face) != null
                ? t.face
                : t.app;
    }

    private String category(AppCatalog.App a) {
        for (FlowStore.Tile t : page().tiles)
            if (t.app.equals(a.key) && !t.category.isEmpty()) return t.category;
        return AppStyle.group(a);
    }

    FlowUi ui;
    IconPacks iconPacks, drawerPacks;
    private StartPager pager;
    private boolean paging, verticalScroll;
    private float pageOrigin;
    BuiltInIcons builtInIcons;
    WidgetTiles widgets;
    LocalFiles files;
    Wallpapers wallpapers;
    Screen screen = Screen.HOME;
    boolean arranging;
    FlowStore.Tile selectedTile, revealTile;
    LinearLayout root;
    View cubeView;
    FrameLayout homeViewport;
    ScrollView contentScroll;
    ListView drawerList;
    EditText drawerSearch;
    final Map<View, FlowStore.Tile> tileViews = new LinkedHashMap<>();
    private final Rect hit = new Rect();
    private FlowStore.Tile touchedTile;
    private float touchX, touchY;
    private boolean tracking, intercepted, moved;
    private Gesture.Route swipeCandidate = Gesture.Route.NONE;
    private boolean atHomeTop, atHomeBottom;
    private long lastTap;
    private float lastTapX, lastTapY;
    private boolean packageEventsRegistered;
    private boolean activeAppPicker;
    private ControlCenter center;
    private boolean started;
    private Screen lockReturn = Screen.HOME;
    private Screen settingsReturn = Screen.HOME;
    private boolean defaultHome;
    private android.app.Dialog startMenu;

    boolean defaultHomeActive() {
        return defaultHome;
    }

    void trackStartMenu(android.app.Dialog dialog) {
        startMenu = dialog;
        dialog.setOnDismissListener(
                ignored -> {
                    if (startMenu == dialog) startMenu = null;
                });
    }

    private void closeStartMenu() {
        if (startMenu != null) startMenu.dismiss();
    }

    void searchApps() {
        drawerQuery = "";
        setScreen(Screen.APPS);
        if (!store.flag("drawerSearch", true)
                || drawerSearch == null
                || drawerSearch.getParent() == null) return;
        EditText field = drawerSearch;
        field.requestFocus();
        field.post(
                () -> {
                    if (field.isShown())
                        getSystemService(InputMethodManager.class)
                                .showSoftInput(field, InputMethodManager.SHOW_IMPLICIT);
                });
    }

    private String drawerQuery = "";

    private final BroadcastReceiver packages =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    apps.refresh();
                    iconPacks.invalidate();
                    drawerPacks.invalidate();
                    appStyle.reset();
                    if (started) refresh();
                }
            };

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        store = new FlowStore(this);
        media = new MediaTiles(this, state);
        apps = new AppCatalog(this);
        iconPacks = new IconPacks(this);
        drawerPacks = new IconPacks(this);
        builtInIcons = new BuiltInIcons(this);
        widgets = new WidgetTiles(this, state);
        files = new LocalFiles(this, state);
        wallpapers = new Wallpapers(this);
        apps.refresh();
        seedHome();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= 33)
            registerReceiver(packages, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(packages, filter);
        packageEventsRegistered = true;
        if (state != null) {
            String previous = state.getString("screen", "HOME");
            try {
                screen = Screen.valueOf(previous);
                if (screen == Screen.TILES) screen = Screen.HOME;
                lockReturn = Screen.valueOf(state.getString("lockReturn", "HOME"));
                settingsReturn = Screen.valueOf(state.getString("settingsReturn", "HOME"));
            } catch (IllegalArgumentException ignored) {
            }
            drawerQuery = state.getString("query", "");
        } else {
            store.go(store.homePage);
        }
        if (CenterShortcut.accepts(getIntent())) screen = Screen.ACTION_CENTER;
        render(0);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (CenterShortcut.accepts(intent)) setScreen(Screen.ACTION_CENTER);
        else goHome();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("screen", screen.name());
        state.putString("lockReturn", lockReturn.name());
        state.putString("settingsReturn", settingsReturn.name());
        state.putString("query", drawerQuery);
        widgets.savePending(state);
        files.savePending(state);
        media.save(state);
    }

    @Override
    protected void onStart() {
        super.onStart();
        started = true;
        widgets.start();
        BadgeListener.observe(this::updateBadges);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (defaultHome != isDefault()) refresh();
        BadgeListener.enable(store.flag("badges", false));
        BadgeListener.reconnect(this, false);
        media.startUpdates();
    }

    @Override
    protected void onStop() {
        BadgeListener.observe(null);
        if (center != null) center.clear();
        widgets.stop();
        media.pauseUpdates();
        started = false;
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (packageEventsRegistered) unregisterReceiver(packages);
        if (media != null) media.close();
        if (files != null) files.close();
        closeStartMenu();
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        refresh();
    }

    private void seedHome() {
        if (store.initialized) return;
        FlowStore.Page home = store.pages.get(0);
        if (!home.tiles.isEmpty()) {
            store.initialized = true;
            store.save();
            return;
        }
        List<AppCatalog.App> remaining = new ArrayList<>(apps.list());
        List<AppCatalog.App> favorites = new ArrayList<>();
        String[][] categories = {
            {"dialer", "phone"},
            {"messaging", "messages", "sms"},
            {"camera"},
            {"settings"},
            {"chrome", "browser"},
            {"mail"},
            {"music"},
            {"files"},
            {"maps"}
        };
        for (String[] category : categories) {
            AppCatalog.App match = null;
            for (String hint : category) {
                for (AppCatalog.App candidate : remaining) {
                    if (candidate.matchesWord(hint)) {
                        match = candidate;
                        break;
                    }
                }
                if (match != null) break;
            }
            if (match != null) {
                favorites.add(match);
                remaining.remove(match);
            }
        }
        for (AppCatalog.App app : remaining) {
            if (favorites.size() >= 18) break;
            favorites.add(app);
        }
        home.tiles.add(live("clock", TileLayout.WIDE));
        home.tiles.add(live("battery", TileLayout.STANDARD));
        for (int i = 0; i < favorites.size(); i++) {
            if (i == 2) home.tiles.add(live("photo", TileLayout.WIDE));
            FlowStore.Tile tile = new FlowStore.Tile(FlowStore.APP);
            tile.app = favorites.get(i).key;
            tile.size = TileLayout.STANDARD;
            home.tiles.add(tile);
            if (i == 4) home.tiles.add(live("music", TileLayout.STANDARD));
        }
        if (favorites.size() < 3) home.tiles.add(live("photo", TileLayout.WIDE));
        if (favorites.size() < 5) home.tiles.add(live("music", TileLayout.STANDARD));
        store.initialized = true;
        store.save();
    }

    private FlowStore.Tile live(String kind, int size) {
        FlowStore.Tile tile = new FlowStore.Tile(FlowStore.LIVE);
        tile.live = kind;
        tile.liveFlip = LiveFlipTile.supported(tile) ? 2 : 0;
        tile.size = size;
        return tile;
    }

    FlowStore.Page page() {
        return store.pages.get(store.activePage);
    }

    boolean board() {
        return screen == Screen.HOME || screen == Screen.TILES;
    }

    void customize() {
        settingsReturn = screen;
        setScreen(Screen.SETTINGS);
    }

    void leaveSettings() {
        setScreen(settingsReturn);
    }

    void setScreen(Screen next) {
        if (next == Screen.TILES) next = Screen.HOME;
        if (pager != null && (next == Screen.APPS || next == Screen.HOME) && !arranging) {
            if (next == Screen.HOME) hideKeyboard();
            screen = next;
            pager.settle(next == Screen.APPS);
            return;
        }
        if (next == Screen.LOCK_SCREEN && screen != Screen.LOCK_SCREEN) lockReturn = screen;
        if (next != screen) {
            arranging = false;
            selectedTile = null;
        }
        if (next != Screen.APPS) hideKeyboard();
        if (screen != next) {
            screen = next;
            render(0);
            if (store.number("motion", 1) != 0
                    && android.animation.ValueAnimator.areAnimatorsEnabled()) {
                View incoming = pager == null ? root : pager;
                incoming.setAlpha(.86f);
                incoming.setTranslationY(ui.dp(next == Screen.ACTION_CENTER ? -18 : 12));
                incoming.animate()
                        .alpha(1f)
                        .translationY(0)
                        .setDuration(store.number("motion", 1) == 1 ? 165 : 225)
                        .setInterpolator(
                                new android.view.animation.PathInterpolator(.2f, 0f, 0f, 1f))
                        .start();
            }
        }
    }

    void goHome() {
        closeStartMenu();
        if (screen == Screen.HOME
                && !arranging
                && store.activePage == store.homePage
                && root != null) {
            if (contentScroll != null) contentScroll.scrollTo(0, 0);
            return;
        }
        boolean wasArranging = arranging;
        arranging = false;
        hideKeyboard();
        boolean usePager = pager != null && !wasArranging;
        store.go(store.homePage);
        drawerQuery = "";
        if (drawerSearch != null) drawerSearch.setText("");
        if (usePager) setScreen(Screen.HOME);
        else {
            screen = Screen.HOME;
            render(0);
        }
    }

    void toggleArrange() {
        selectedTile = null;
        arranging = !arranging;
        refresh();
    }

    boolean canRefreshLiveBoard() {
        return board() && !arranging && !paging;
    }

    void refresh() {
        int offset = contentScroll == null ? 0 : contentScroll.getScrollY();
        render(offset);
    }

    private void render(int offset) {
        closeStartMenu();
        ui = new FlowUi(this, store);
        if (pager != null) pager.stop();
        pager = null;
        paging = false;
        String homePack = store.option("iconPack", BuiltInIcons.DESKTOP);
        iconPacks.select(homePack);
        drawerPacks.select(store.option("drawerPack", homePack));
        boolean startPages = board() || screen == Screen.APPS;
        if (screen == Screen.ACTION_CENTER)
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        else getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        boolean systemWallpaper = startPages && Wallpapers.choice(store) == Wallpapers.SYSTEM;
        getWindow()
                .setBackgroundDrawable(
                        new ColorDrawable(systemWallpaper ? Color.TRANSPARENT : ui.base));
        getWindow().setStatusBarColor(ui.base);
        getWindow().setNavigationBarColor(ui.base);
        getWindow()
                .getDecorView()
                .setSystemUiVisibility(
                        (startPages && store.flag("fullHome", true)
                                        ? View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                                | View.SYSTEM_UI_FLAG_FULLSCREEN
                                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                        : 0)
                                | (ui.light
                                        ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                                                | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                                        : 0));
        FrameLayout background = new FrameLayout(this);
        background.setBackgroundColor(ui.base);

        root = ui.column();
        root.setFocusableInTouchMode(true);
        if (!startPages) background.addView(root, new FrameLayout.LayoutParams(FILL, FILL));
        homeViewport = null;
        contentScroll = null;
        drawerList = null;
        drawerSearch = null;
        tileViews.clear();
        cubeView = null;
        setContentView(background);
        defaultHome = isDefault();
        center = null;
        switch (screen) {
            case HOME:
            case TILES:
            case APPS:
                Screen requested = screen;
                screen = Screen.HOME;
                FrameLayout home = new FrameLayout(this);
                home.setBackgroundColor(ui.base);
                bindBackground(home);
                home.addView(root, new FrameLayout.LayoutParams(FILL, FILL));
                new HomeScreen(this).build();
                root = ui.column();
                root.setFocusableInTouchMode(true);
                root.setBackgroundColor(ui.base);
                buildDrawer();
                screen = requested;
                pager = new StartPager(this, home, root, screen == Screen.APPS);
                background.setBackgroundColor(Color.TRANSPARENT);
                background.addView(pager, new FrameLayout.LayoutParams(FILL, FILL));
                break;
            case SETTINGS:
                new Customize(this).build();
                break;
            case ACTION_CENTER:
                center = new ControlCenter(this);
                center.build();
                break;
            case LOCK_SCREEN:
                new LockScreenEditor(this).build();
                break;
        }

        updateBadges();
        if (contentScroll != null && offset > 0) {
            ScrollView scroll = contentScroll;
            scroll.post(() -> scroll.scrollTo(0, offset));
        }
        if (revealTile != null && startPages) {
            for (Map.Entry<View, FlowStore.Tile> entry : tileViews.entrySet()) {
                if (entry.getValue() != revealTile) continue;
                View added = entry.getKey();
                ScrollView boardScroll = contentScroll;
                added.post(
                        () -> {
                            if (!added.isAttachedToWindow() || boardScroll == null) return;
                            Rect bounds = new Rect(0, 0, added.getWidth(), added.getHeight());
                            boardScroll.offsetDescendantRectToMyCoords(added, bounds);
                            if (bounds.bottom > boardScroll.getScrollY() + boardScroll.getHeight()
                                    || bounds.top < boardScroll.getScrollY())
                                boardScroll.scrollTo(0, Math.max(0, bounds.top - ui.dp(12)));
                        });
                revealTile = null;
                break;
            }
        }
    }

    private void bindBackground(FrameLayout background) {
        int choice = Wallpapers.choice(store);
        if (choice == Wallpapers.SOLID)
            background.setBackgroundColor(store.number("solidColor", ui.base));
        if (choice == Wallpapers.SYSTEM) background.setBackgroundColor(Color.TRANSPARENT);
        if (choice == Wallpapers.PATTERN)
            background.setBackground(
                    new android.graphics.drawable.GradientDrawable(
                            android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                            new int[] {
                                FlowUi.blend(ui.accent, ui.base, .28f),
                                ui.base,
                                FlowUi.blend(ui.panel, ui.accent, .25f)
                            }));
        Bitmap photo = choice == Wallpapers.PHOTO ? files.wallpaper() : wallpapers.image(choice);
        if (photo == null) return;
        ImageView image = new ImageView(this);
        image.setImageBitmap(photo);
        image.setScaleType(
                store.number("wallScale", 0) == 1
                        ? ImageView.ScaleType.FIT_CENTER
                        : ImageView.ScaleType.CENTER_CROP);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        background.addView(image, new FrameLayout.LayoutParams(FILL, FILL));
        View shade = new View(this);
        int dim = FlowUi.clamp(store.number("wallDim", 20), 0, 95) * 255 / 100;
        shade.setBackgroundColor((ui.base & 0x00ffffff) | (dim << 24));
        background.addView(shade, new FrameLayout.LayoutParams(FILL, FILL));
    }

    void header(String title, String action, Runnable callback) {
        LinearLayout bar = ui.row();
        int margin = board() ? FlowUi.clamp(store.number("tilesMargin", 12), 6, 32) : 16;
        bar.setPadding(ui.dp(margin), ui.dp(12), ui.dp(margin), ui.dp(8));
        TextView label = ui.label(title, screen == Screen.ACTION_CENTER ? 28 : 32, ui.text);
        FlowUi.weight(label, store.desktop() ? 600 : 300);
        label.setLetterSpacing(-.035f);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        if (Build.VERSION.SDK_INT >= 28) label.setAccessibilityHeading(true);
        bar.addView(label, new LinearLayout.LayoutParams(0, WRAP, 1));
        if (action != null || screen != Screen.HOME) {
            TextView command =
                    ui.command(
                            action == null || action.equals("Home") ? "←" : action,
                            callback == null ? this::goHome : callback);
            if (action == null || action.equals("Home"))
                command.setContentDescription("Back to Start");
            bar.addView(command);
        }
        root.addView(bar, new LinearLayout.LayoutParams(FILL, WRAP));
    }

    LinearLayout scrollContent() {
        contentScroll = new ScrollView(this);
        contentScroll.setClipToPadding(false);
        contentScroll.setVerticalScrollBarEnabled(false);
        contentScroll.setFillViewport(true);
        LinearLayout column = ui.column();
        int safeWidth =
                Math.round(
                        getResources().getDisplayMetrics().widthPixels
                                / getResources().getDisplayMetrics().density);
        int margin = board() ? FlowUi.clamp(store.number("tilesMargin", 12), 6, 32) : 16;
        int side = ui.dp(Math.max(margin, (safeWidth - 740) / 2));
        column.setPadding(side, ui.dp(7), side, ui.dp(20));
        contentScroll.addView(column, new ScrollView.LayoutParams(FILL, WRAP));
        if (board()) {
            homeViewport = new FrameLayout(this);
            homeViewport.addView(contentScroll, new FrameLayout.LayoutParams(FILL, FILL));
            root.addView(homeViewport, new LinearLayout.LayoutParams(FILL, 0, 1));
        } else root.addView(contentScroll, new LinearLayout.LayoutParams(FILL, 0, 1));
        return column;
    }

    private void buildDrawer() {
        TextView title = ui.label("All apps", store.desktop() ? 32 : 44, ui.text);
        title.setPadding(ui.dp(22), ui.dp(15), ui.dp(22), ui.dp(12));
        FlowUi.weight(title, store.desktop() ? 620 : 300);
        if (Build.VERSION.SDK_INT >= 28) title.setAccessibilityHeading(true);
        LinearLayout heading = ui.row();
        heading.addView(title, new LinearLayout.LayoutParams(0, WRAP, 1));
        TextView back = ui.command("←", this::goHome);
        back.setContentDescription("Return to Start");
        heading.addView(back);
        root.addView(heading);
        LinearLayout searchBox = ui.row();
        searchBox.setPadding(ui.dp(20), ui.dp(3), ui.dp(20), ui.dp(11));
        drawerSearch = input("Search apps", drawerQuery, 120, false);
        searchBox.addView(drawerSearch, new LinearLayout.LayoutParams(0, WRAP, 1));
        root.addView(searchBox);
        if (!store.flag("drawerSearch", true)) drawerSearch.setVisibility(View.GONE);
        drawerList = new ListView(this);
        drawerList.setDivider(null);
        drawerList.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        drawerList.setPadding(ui.dp(20), 0, ui.dp(20), ui.dp(4));
        drawerList.setClipToPadding(false);
        AppRows adapter = new AppRows(false, true);
        drawerList.setAdapter(adapter);
        adapter.update(drawerQuery);
        ui.gapHorizontal(searchBox, 8);
        TextView categories =
                ui.button(
                        "▥",
                        !filter.isEmpty(),
                        () -> {
                            Sheet sheet = new Sheet(this, "App categories");
                            sheet.add(
                                    "▦",
                                    "All",
                                    () -> {
                                        filter = "";
                                        refresh();
                                    });
                            for (String g : AppStyle.NAMES)
                                sheet.add(
                                        "•",
                                        g,
                                        () -> {
                                            filter = g;
                                            refresh();
                                        });
                            sheet.show();
                        });
        categories.setContentDescription("Filter app categories");
        searchBox.addView(categories, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
        ui.gapHorizontal(searchBox, 8);
        TextView letters = ui.button("A–Z", false, () -> jumpToLetter(adapter));
        letters.setPadding(0, 0, 0, 0);
        letters.setSingleLine(true);
        letters.setAutoSizeTextTypeUniformWithConfiguration(
                9, 16, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        letters.setContentDescription("Jump to a letter");
        searchBox.addView(letters, new LinearLayout.LayoutParams(ui.dp(50), ui.dp(48)));
        drawerList.setOnItemClickListener(
                (parent, view, position, id) -> {
                    AppCatalog.App app = adapter.getItem(position);
                    if (app != null) launch(app.key);
                    else if (adapter.sections().containsKey(adapter.values.get(position).heading))
                        jumpToLetter(adapter);
                });
        drawerList.setOnItemLongClickListener(
                (parent, view, position, id) -> {
                    AppCatalog.App app = adapter.getItem(position);
                    if (app == null) return false;
                    appActions(app);
                    return true;
                });
        watch(
                drawerSearch,
                query -> {
                    drawerQuery = query;
                    adapter.update(query);
                });
        TextView empty = ui.label("No matching apps", 15, ui.dim);
        empty.setGravity(Gravity.CENTER);
        empty.setVisibility(View.GONE);
        FrameLayout results = new FrameLayout(this);
        results.addView(drawerList, new FrameLayout.LayoutParams(FILL, FILL));
        results.addView(empty, new FrameLayout.LayoutParams(FILL, FILL));
        drawerList.setEmptyView(empty);
        root.addView(results, new LinearLayout.LayoutParams(FILL, 0, 1));
    }

    private void jumpToLetter(AppRows adapter) {
        Map<String, Integer> sections = adapter.sections();
        if (sections.isEmpty()) return;
        hideKeyboard();
        ScrollView scroll = new ScrollView(this);
        LinearLayout grid = ui.column();
        grid.setPadding(ui.dp(14), ui.dp(6), ui.dp(14), ui.dp(8));
        scroll.addView(grid);
        AlertDialog jump =
                dialog().setTitle("Jump to")
                        .setView(scroll)
                        .setNegativeButton("Cancel", null)
                        .create();
        java.util.LinkedHashSet<String> letters = new java.util.LinkedHashSet<>();
        letters.add("#");
        for (char letter = 'A'; letter <= 'Z'; letter++) letters.add(String.valueOf(letter));
        letters.addAll(sections.keySet());
        LinearLayout row = null;
        int column = 0;
        for (String letter : letters) {
            if (column == 0) {
                row = ui.row();
                grid.addView(row);
                ui.gap(grid, 5);
            }
            if (column > 0) ui.gapHorizontal(row, 5);
            Integer position = sections.get(letter);
            TextView key =
                    ui.button(
                            letter,
                            position != null,
                            () -> {
                                jump.dismiss();
                                if (position != null) drawerList.setSelection(position);
                            });
            key.setEnabled(position != null);
            key.setPadding(0, 0, 0, 0);
            if (position == null) key.setAlpha(.42f);
            row.addView(key, new LinearLayout.LayoutParams(0, ui.dp(44), 1));
            column = (column + 1) % 5;
        }
        if (column > 0)
            for (; column < 5; column++) {
                ui.gapHorizontal(row, 5);
                row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
            }
        jump.show();
    }

    private void appActions(AppCatalog.App app) {
        ArrayList<String> names = new ArrayList<>();
        ArrayList<Runnable> actions = new ArrayList<>();
        if (!store.flag("lockTiles", false)) {
            names.add("Pin to Tiles");
            actions.add(
                    () -> {
                        if (addApp(app, page())) {
                            render(0);
                            setScreen(Screen.HOME);
                            toast("Pinned to Start");
                        }
                    });
        }
        names.add("Hide from app drawer");
        actions.add(
                () -> {
                    store.hidden.add(app.key);
                    store.save();
                    refresh();
                });
        names.add("Android app info");
        actions.add(
                () ->
                        startSafely(
                                new Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts(
                                                "package",
                                                app.info.activityInfo.packageName,
                                                null))));
        dialog().setTitle(app.label)
                .setItems(
                        names.toArray(new String[0]), (dialog, choice) -> actions.get(choice).run())
                .show();
    }

    void chooseApp(String title, Consumer<AppCatalog.App> selection, Runnable clear) {
        LinearLayout panel = ui.column();
        panel.setPadding(ui.dp(18), ui.dp(8), ui.dp(18), 0);
        EditText search = input("Search installed apps", "", 120, false);
        panel.addView(search);
        ListView list = new ListView(this);
        list.setDivider(null);
        AppRows adapter = new AppRows(true, false);
        list.setAdapter(adapter);
        adapter.update("");
        int height = Math.min(360, getResources().getConfiguration().screenHeightDp / 2);
        FrameLayout results = new FrameLayout(this);
        results.addView(list, new FrameLayout.LayoutParams(FILL, FILL));
        TextView empty = ui.label("No matching apps", 14, ui.dim);
        empty.setGravity(Gravity.CENTER);
        results.addView(empty, new FrameLayout.LayoutParams(FILL, FILL));
        list.setEmptyView(empty);
        panel.addView(results, new LinearLayout.LayoutParams(FILL, ui.dp(height)));
        watch(search, adapter::update);
        AlertDialog.Builder builder =
                dialog().setTitle(title).setView(panel).setNegativeButton("Cancel", null);
        if (clear != null) builder.setNeutralButton("Clear", (d, w) -> clear.run());
        AlertDialog dialog = builder.create();
        list.setOnItemClickListener(
                (parent, view, position, id) -> {
                    AppCatalog.App app = adapter.getItem(position);
                    dialog.dismiss();
                    selection.accept(app);
                });
        dialog.setOnDismissListener(d -> activeAppPicker = false);
        panel.setFocusableInTouchMode(true);
        panel.requestFocus();
        dialog.getWindow()
                .setSoftInputMode(
                        android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                                | android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        activeAppPicker = true;
        dialog.show();
    }

    private static final class AppRow {
        final AppCatalog.App app;
        final String heading;

        AppRow(AppCatalog.App app, String heading) {
            this.app = app;
            this.heading = heading;
        }
    }

    private final class AppRows extends BaseAdapter {
        private final ArrayList<AppRow> values = new ArrayList<>();
        private final LinkedHashMap<String, Integer> alphabet = new LinkedHashMap<>();
        private final boolean includeHidden, main;

        AppRows(boolean includeHidden, boolean main) {
            this.includeHidden = includeHidden;
            this.main = main;
        }

        void update(String query) {
            values.clear();
            alphabet.clear();
            ArrayList<AppCatalog.App> filtered = new ArrayList<>();
            String[] words = query.trim().toLowerCase(java.util.Locale.ROOT).split("\\s+");
            for (AppCatalog.App app : apps.list()) {
                if ((includeHidden || !store.isHidden(app))
                        && (!main || filter.isEmpty() || filter.equals(category(app)))
                        && app.matches(words)) filtered.add(app);
            }
            boolean grouped = main && query.trim().isEmpty();
            if (grouped && store.flag("recentApps", false)) addRecent(filtered);
            String previous = "";
            for (AppCatalog.App app : filtered) {
                String heading = heading(app.label);
                if (grouped && !heading.equals(previous)) {
                    alphabet.putIfAbsent(heading, values.size());
                    values.add(new AppRow(null, heading));
                    previous = heading;
                } else if (!grouped) {
                    alphabet.putIfAbsent(heading, values.size());
                }
                values.add(new AppRow(app, ""));
            }
            notifyDataSetChanged();
        }

        private void addRecent(ArrayList<AppCatalog.App> all) {
            long now = System.currentTimeMillis();
            long cutoff = now - java.util.concurrent.TimeUnit.DAYS.toMillis(7);
            ArrayList<AppCatalog.App> recent = new ArrayList<>();
            for (AppCatalog.App app : all) {
                long installed = apps.firstInstall(app);
                if (installed >= cutoff && installed <= now) recent.add(app);
            }
            recent.sort(
                    (left, right) ->
                            Long.compare(apps.firstInstall(right), apps.firstInstall(left)));
            if (!recent.isEmpty()) {
                values.add(new AppRow(null, "Recently installed"));
                for (int i = 0; i < Math.min(5, recent.size()); i++)
                    values.add(new AppRow(recent.get(i), ""));
            }
        }

        private String heading(String label) {
            String normalized =
                    java.text.Normalizer.normalize(label.trim(), java.text.Normalizer.Form.NFD);
            if (normalized.isEmpty()) return "#";
            int initial = normalized.codePointAt(0);
            return Character.isLetter(initial)
                    ? new String(Character.toChars(Character.toUpperCase(initial)))
                    : "#";
        }

        Map<String, Integer> sections() {
            return new LinkedHashMap<>(alphabet);
        }

        @Override
        public int getCount() {
            return values.size();
        }

        @Override
        public AppCatalog.App getItem(int position) {
            return values.get(position).app;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return !main;
        }

        @Override
        public boolean isEnabled(int position) {
            return getItem(position) != null || alphabet.containsKey(values.get(position).heading);
        }

        @Override
        public View getView(int position, View convert, ViewGroup parent) {
            LinearLayout row = convert == null ? ui.row() : (LinearLayout) convert;
            row.removeAllViews();
            row.setPadding(0, ui.dp(5), ui.dp(8), ui.dp(5));
            AppCatalog.App app = getItem(position);
            if (app == null) {
                String heading = values.get(position).heading;
                boolean letter = alphabet.containsKey(heading);
                TextView title = ui.label(heading, letter ? 24 : 19, ui.text);
                if (Build.VERSION.SDK_INT >= 28) title.setAccessibilityHeading(true);
                if (letter) {
                    title.setGravity(Gravity.CENTER);
                    title.setBackground(store.desktop() ? ui.rounded(ui.panel, 9, ui.outline)
                            : ui.rounded(Color.TRANSPARENT, 1, ui.text));
                    title.setTextColor(ui.text);
                    title.setContentDescription("Jump to letter, " + heading);
                    row.addView(title, new LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)));
                } else row.addView(title);
                return row;
            }
            row.setMinimumHeight(ui.dp(store.flag("drawerCompact", true) ? 48 : 60));
            int iconSize = FlowUi.clamp(store.number("drawerIconSize", 32), 22, 64);
            FrameLayout plate = new FrameLayout(MainActivity.this);
            plate.addView(
                    appIcon(app.key, iconSize, ui.base, "", -1, main),
                    new FrameLayout.LayoutParams(ui.dp(iconSize), ui.dp(iconSize), Gravity.CENTER));
            row.addView(
                    plate, new LinearLayout.LayoutParams(ui.dp(iconSize + 6), ui.dp(iconSize + 6)));
            ui.gapHorizontal(row, 12);
            TextView name =
                    ui.label(
                            app.label,
                            FlowUi.clamp(store.number("drawerLabelSize", 17), 12, 30),
                            ui.text);
            FlowUi.weight(name, 400);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.addView(name, new LinearLayout.LayoutParams(0, WRAP, 1));

            return row;
        }
    }

    View tileIcon(FlowStore.Tile tile, int size, int backing) {
        String key = face(tile);
        Bitmap image = key.equals(tile.app) ? media.portrait(tile.imageId) : null;
        if (image == null && key.equals(tile.app)) image = files.tileIcon(tile.imageId);
        if (image == null)
            return appIcon(key, size, backing, key.equals(tile.app) ? tile.icons : "", tile.shape);
        ImageView icon = new ImageView(this);
        icon.setImageBitmap(image);
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        icon.setBackground(
                ui.rounded(
                        Color.TRANSPARENT,
                        (tile.shape < 0 ? store.number("icons", 0) : tile.shape) == 2
                                ? size / 2
                                : Math.round(size * .20f),
                        0));
        icon.setClipToOutline(true);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        icon.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(size), ui.dp(size)));
        return icon;
    }

    View appIcon(String key, int size, int backing) {
        return appIcon(key, size, backing, "");
    }

    View appIcon(String key, int size, int backing, String override) {
        return appIcon(key, size, backing, override, -1);
    }

    private View appIcon(String key, int size, int backing, String override, int shape) {
        return appIcon(key, size, backing, override, shape, false);
    }

    private View appIcon(
            String key, int size, int backing, String override, int shape, boolean drawer) {
        String basePack = store.option("iconPack", BuiltInIcons.DESKTOP);
        String pack =
                override.isEmpty()
                        ? (drawer ? store.option("drawerPack", basePack) : basePack)
                        : override;
        FrameLayout badge = new FrameLayout(this);
        int style = FlowUi.clamp(shape < 0 ? store.number("icons", 0) : shape, 0, 4);
        int radius = style == 2 ? size / 2 : style == 1 ? 0 : Math.round(size * .23f);
        String builtInPack = builtInIcons.packOf(pack);
        BuiltInIcons.Symbol symbol =
                builtInPack.isEmpty() ? null : builtInIcons.forApp(pack, key, appName(key));
        Drawable art =
                SrArtwork.PACK.equals(pack)
                        ? SrArtwork.icon(this, key)
                        : symbol == null ? null : builtInIcons.art(builtInPack, symbol);
        badge.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(size), ui.dp(size)));
        badge.setBackground(ui.rounded(Color.TRANSPARENT, radius, 0));
        badge.setClipToOutline(style != 4);
        badge.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        ImageView icon = new ImageView(this);
        if (art != null) {
            if (!BuiltInIcons.colorful(builtInPack)) {
                int tint = FlowUi.readable(backing);
                if (BuiltInIcons.COLOR.equals(builtInPack) && style != 3) {
                    int accent =
                            FlowUi.ACCENTS[
                                    Math.floorMod(symbol.id.hashCode(), FlowUi.ACCENTS.length)];
                    tint = FlowUi.blend(tint, accent, .42f);
                }
                art.setTint(tint);
            }
            icon.setImageDrawable(art);
            if (style == 3 && BuiltInIcons.colorful(builtInPack)) {
                android.graphics.ColorMatrix saturation = new android.graphics.ColorMatrix();
                saturation.setSaturation(0);
                icon.setColorFilter(new android.graphics.ColorMatrixColorFilter(saturation));
            }
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int dimensions =
                    ui.dp(
                            Math.round(
                                    size
                                            * (style == 4 || BuiltInIcons.FLUENT.equals(builtInPack) || BuiltInIcons.METRO.equals(builtInPack) || BuiltInIcons.colorful(builtInPack)
                                                    ? .91f
                                                    : .74f)));
            badge.addView(
                    icon, new FrameLayout.LayoutParams(dimensions, dimensions, Gravity.CENTER));
            return badge;
        }
        Drawable drawable =
                override.isEmpty() && builtInPack.isEmpty()
                        ? (drawer ? drawerPacks : iconPacks).icon(key)
                        : null;
        if (drawable == null) drawable = apps.icon(key);
        if (drawable == null) {
            View fallback = ui.symbol("apps", style == 3 ? ui.ink : ui.text, size / 2);
            badge.addView(
                    fallback,
                    new FrameLayout.LayoutParams(ui.dp(size / 2), ui.dp(size / 2), Gravity.CENTER));
        } else {
            icon.setImageDrawable(drawable);
            if (style == 3) {
                android.graphics.ColorMatrix grayscale = new android.graphics.ColorMatrix();
                grayscale.setSaturation(0);
                icon.setColorFilter(new android.graphics.ColorMatrixColorFilter(grayscale));
            }
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int dimensions = ui.dp(Math.round(size * .86f));
            badge.addView(
                    icon, new FrameLayout.LayoutParams(dimensions, dimensions, Gravity.CENTER));
        }
        return badge;
    }

    EditText input(String hint, String value, int limit, boolean multiline) {
        EditText input = new EditText(this);
        input.setTypeface(ui.font);
        input.setTextSize(15 * ui.textScale);
        input.setTextColor(ui.text);
        input.setHintTextColor(ui.dim);
        input.setSingleLine(!multiline);
        input.setHint(hint);
        input.setContentDescription(hint);
        input.setFilters(new InputFilter[] {new InputFilter.LengthFilter(limit)});
        input.setText(value);
        input.setPadding(ui.dp(13), ui.dp(11), ui.dp(13), ui.dp(11));
        input.setBackground(ui.rounded(ui.panel, 13, ui.outline));
        return input;
    }

    static void watch(EditText edit, Consumer<String> changed) {
        edit.addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(
                            CharSequence text, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence text, int start, int before, int count) {
                        changed.accept(text.toString());
                    }

                    @Override
                    public void afterTextChanged(Editable text) {}
                });
    }

    void prompt(
            String title,
            String hint,
            String current,
            int length,
            boolean multiline,
            Consumer<String> callback) {
        LinearLayout panel = ui.column();
        panel.setPadding(ui.dp(18), ui.dp(8), ui.dp(18), 0);
        EditText field = input(hint, current, length, multiline);
        panel.addView(field);
        dialog().setTitle(title)
                .setView(panel)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(
                        "Save",
                        (dialog, which) -> callback.accept(field.getText().toString().trim()))
                .show();
    }

    boolean addApp(AppCatalog.App app, FlowStore.Page page) {
        FlowStore.Tile tile = new FlowStore.Tile(FlowStore.APP);
        tile.app = app.key;
        return addTile(tile, page);
    }

    boolean addTile(FlowStore.Tile tile, FlowStore.Page page) {
        if (page.tiles.size() >= FlowStore.MAX_TILES) {
            toast("This page is full");
            return false;
        }
        revealTile = tile;
        page.tiles.add(tile);
        store.save();
        return true;
    }

    String appName(String key) {
        AppCatalog.App entry = apps.find(key);
        return entry == null ? "Unavailable app" : entry.label;
    }

    String tileName(FlowStore.Tile tile) {
        if (FlowStore.APP.equals(tile.type) && !face(tile).equals(tile.app))
            return appName(face(tile));
        if (!tile.label.isEmpty()) return tile.label;
        return switch (tile.type) {
            case FlowStore.APP -> appName(tile.app);
            case FlowStore.FOLDER -> "Folder";
            case FlowStore.NOTE -> "Note";
            case FlowStore.LIVE -> LiveTile.name(tile.live);
            default -> "Widget";
        };
    }

    void launch(String key) {
        hideKeyboard();
        Intent intent = apps.launchIntent(key);
        if (intent == null) toast("This app is unavailable");
        else startSafely(intent);
    }

    void startSafely(Intent intent) {
        try {
            startActivity(intent);
        } catch (RuntimeException ignored) {
            toast("This action is unavailable on this device");
        }
    }

    boolean isDefault() {
        if (Build.VERSION.SDK_INT >= 29) {
            RoleManager role = getSystemService(RoleManager.class);
            return role != null && role.isRoleHeld(RoleManager.ROLE_HOME);
        }
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        android.content.pm.ResolveInfo result = getPackageManager().resolveActivity(intent, 0);
        return result != null
                && result.activityInfo != null
                && getPackageName().equals(result.activityInfo.packageName);
    }

    void requestHome() {
        if (Build.VERSION.SDK_INT >= 29) {
            RoleManager role = getSystemService(RoleManager.class);
            if (role != null
                    && role.isRoleAvailable(RoleManager.ROLE_HOME)
                    && !role.isRoleHeld(RoleManager.ROLE_HOME)) {
                try {
                    startActivityForResult(
                            role.createRequestRoleIntent(RoleManager.ROLE_HOME), 100);
                    return;
                } catch (RuntimeException ignored) {
                }
            }
        }
        startSafely(new Intent(Settings.ACTION_HOME_SETTINGS));
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code >= MediaTiles.GALLERY && code <= MediaTiles.MUSIC) {
            if (result == RESULT_OK && data != null) media.result(code, data);
            return;
        }
        if (widgets.onResult(code, result, data)) return;
        if (code == 100) refresh();
        if (code == PICK_PHOTO
                || code == PICK_TILE_PHOTO
                || code == PICK_ICON
                || code == PICK_LOCK_PHOTO
                || code == EXPORT
                || code == IMPORT) files.onResult(code, result, data);
    }

    void hideKeyboard() {
        if (drawerSearch != null) drawerSearch.clearFocus();
        View focus = getCurrentFocus();
        if (focus != null)
            getSystemService(InputMethodManager.class)
                    .hideSoftInputFromWindow(focus.getWindowToken(), 0);
        else if (Build.VERSION.SDK_INT >= 30
                && root != null
                && root.getWindowInsetsController() != null) {
            root.getWindowInsetsController().hide(WindowInsets.Type.ime());
        }
    }

    AlertDialog.Builder dialog() {
        return new AlertDialog.Builder(
                this, ui.light ? R.style.FlowPopupLight : R.style.FlowPopupDark);
    }

    void testNotification() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {android.Manifest.permission.POST_NOTIFICATIONS}, 209);
            return;
        }
        android.app.NotificationManager manager =
                getSystemService(android.app.NotificationManager.class);
        if (manager == null) return;
        if (!manager.areNotificationsEnabled()) {
            toast("Enable S-Square notifications in Android Settings");
            return;
        }
        String channel = "flow_center_test";
        manager.createNotificationChannel(
                new android.app.NotificationChannel(
                        channel,
                        "Action Center test",
                        android.app.NotificationManager.IMPORTANCE_LOW));
        manager.notify(
                1053,
                new android.app.Notification.Builder(this, channel)
                        .setSmallIcon(R.drawable.ic_metro_settings)
                        .setContentTitle("S-Square notification test")
                        .setContentText("Android delivered this notification.")
                        .setAutoCancel(true)
                        .build());
    }

    @Override
    public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == 209
                && results.length > 0
                && results[0] == android.content.pm.PackageManager.PERMISSION_GRANTED)
            testNotification();
        media.permissionsResult(request);
    }

    void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    void leaveLockEditor() {
        setScreen(lockReturn);
    }

    @Override
    public boolean onKeyUp(int keyCode, android.view.KeyEvent event) {
        if (keyCode == android.view.KeyEvent.KEYCODE_MENU && board()) {
            new HomeScreen(this).menu();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        if (screen == Screen.LOCK_SCREEN) leaveLockEditor();
        else if (screen == Screen.SETTINGS) leaveSettings();
        else if (arranging) toggleArrange();
        else if (screen != Screen.HOME || store.activePage != store.homePage) goHome();
        else if (!isDefault()) super.onBackPressed();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        float x = event.getX(), y = event.getY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> {
                paging = verticalScroll = false;
                touchX = x;
                touchY = y;
                intercepted = moved = false;
                swipeCandidate = Gesture.Route.NONE;
                atHomeTop = contentScroll == null || !contentScroll.canScrollVertically(-1);
                atHomeBottom = contentScroll == null || !contentScroll.canScrollVertically(1);
                touchedTile = board() ? tileAt(x, y) : null;
                tracking =
                        !activeAppPicker
                                && !keyboardOpen()
                                && !atSystemEdge(x, y)
                                && (screen == Screen.APPS || !hitView(cubeView, x, y));
            }
            case MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> {
                tracking = false;
                if (paging && pager != null) pager.settle(screen == Screen.APPS);
                paging = false;
                if (intercepted) return true;
            }
            case MotionEvent.ACTION_MOVE -> {
                if (!tracking) break;
                float dx = x - touchX, dy = y - touchY;
                if (!paging && Math.abs(dy) > ui.dp(10) && Math.abs(dy) > 1.4f * Math.abs(dx))
                    verticalScroll = true;
                moved |= Math.abs(dx) > ui.dp(12) || Math.abs(dy) > ui.dp(12);
                if (paging && pager != null) {
                    pager.place(
                            pageOrigin
                                    - dx / Math.max(1, pager.getWidth()));
                    return true;
                }
                if (!intercepted && pager != null && !arranging && pager.motion()) {
                    int slop = android.view.ViewConfiguration.get(this).getScaledPagingTouchSlop();
                    if (Math.abs(dy) > slop
                            && Math.abs(dy) > Math.abs(dx) * 1.4f
                            && (screen == Screen.APPS || (dy < 0 ? !atHomeBottom : !atHomeTop)))
                        tracking = false;
                    if (!tracking) break;
                    boolean horizontal = Math.abs(dx) > slop && Math.abs(dx) > 1.45f * Math.abs(dy);
                    if (horizontal && !verticalScroll) {
                        Gesture.Route route =
                                swipeRoute(dx < 0 ? "left" : "right", dx, dy);
                        if (route == Gesture.Route.HOME || route == Gesture.Route.DRAWER) {
                            MotionEvent cancel = MotionEvent.obtain(event);
                            cancel.setAction(MotionEvent.ACTION_CANCEL);
                            super.dispatchTouchEvent(cancel);
                            cancel.recycle();
                            intercepted = paging = true;
                            pager.stop();
                            pageOrigin = pager.position;
                            pager.place(
                                    pageOrigin
                                            - dx / Math.max(1, pager.getWidth()));
                            return true;
                        }
                    }
                }
                if (!intercepted) {
                    swipeCandidate = swipeRoute(direction(dx, dy), dx, dy);
                }
                if (!intercepted && swipeCandidate != Gesture.Route.NONE) {
                    MotionEvent cancel = MotionEvent.obtain(event);
                    cancel.setAction(MotionEvent.ACTION_CANCEL);
                    super.dispatchTouchEvent(cancel);
                    cancel.recycle();
                    intercepted = true;
                }
                if (intercepted) return true;
            }
            case MotionEvent.ACTION_UP -> {
                if (paging && pager != null) {
                    float dx = x - touchX;
                    int threshold =
                            ui.dp(FlowUi.clamp(store.number("gestureDistance", 60), 35, 105));
                    boolean appsPage = StartPager.destination(screen == Screen.APPS, dx, threshold);
                    if (!appsPage && screen == Screen.APPS) {
                        hideKeyboard();
                    }
                    screen = appsPage ? Screen.APPS : Screen.HOME;
                    pager.settle(appsPage);
                    paging = tracking = false;
                    return true;
                }
                if (tracking) {
                    float dx = x - touchX, dy = y - touchY;
                    String released = direction(dx, dy);
                    Gesture.Route chosen = swipeRoute(released, dx, dy);
                    if (chosen != Gesture.Route.NONE
                            && (!intercepted || chosen == swipeCandidate)) {
                        if (!intercepted) {
                            MotionEvent cancel = MotionEvent.obtain(event);
                            cancel.setAction(MotionEvent.ACTION_CANCEL);
                            super.dispatchTouchEvent(cancel);
                            cancel.recycle();
                        }
                        tracking = false;
                        swipe(chosen, released);
                        return true;
                    }
                }
                if (intercepted) {
                    tracking = false;
                    return true;
                }
                if (tracking
                        && !moved
                        && touchedTile == null
                        && blankAt(x, y)
                        && board()
                        && store.flag("doubleTap", false)) {
                    long now = event.getEventTime();
                    if (now - lastTap < 300
                            && Math.abs(x - lastTapX) < ui.dp(36)
                            && Math.abs(y - lastTapY) < ui.dp(36)) {
                        lastTap = 0;
                        customize();
                        return true;
                    }
                    lastTap = now;
                    lastTapX = x;
                    lastTapY = y;
                }
                tracking = false;
            }
            default -> {}
        }
        return super.dispatchTouchEvent(event);
    }

    private void updateBadges() {
        if (center != null) center.updateNotices();
        boolean enabled = store.flag("badges", false);
        for (Map.Entry<View, FlowStore.Tile> tile : tileViews.entrySet()) {
            TextView badge = tile.getKey().findViewWithTag("notificationCount");
            if (badge == null) continue;
            int count = enabled ? BadgeListener.count(face(tile.getValue())) : 0;
            badge.setText(count > 99 ? "99+" : Integer.toString(count));
            badge.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
            tile.getKey()
                    .setContentDescription(
                            count == 0
                                    ? tileName(tile.getValue())
                                    : tileName(tile.getValue())
                                            + ", "
                                            + count
                                            + " active notifications");
        }
    }

    private boolean keyboardOpen() {
        if (Build.VERSION.SDK_INT >= 30
                && getWindow().getDecorView().getRootWindowInsets() != null) {
            return getWindow()
                    .getDecorView()
                    .getRootWindowInsets()
                    .isVisible(WindowInsets.Type.ime());
        }
        return drawerSearch != null && drawerSearch.hasFocus();
    }

    private boolean atSystemEdge(float x, float y) {
        if (root == null) return true;
        return x < ui.dp(16)
                || x > getWindow().getDecorView().getWidth() - ui.dp(16)
                || y < ui.dp(26)
                || y > getWindow().getDecorView().getHeight() - ui.dp(25);
    }

    private boolean blankAt(float x, float y) {
        if (contentScroll == null) return false;
        int[] position = new int[2];
        contentScroll.getLocationInWindow(position);
        return y > position[1]
                && y < position[1] + contentScroll.getHeight()
                && tileAt(x, y) == null;
    }

    private FlowStore.Tile tileAt(float x, float y) {
        for (Map.Entry<View, FlowStore.Tile> entry : tileViews.entrySet()) {
            View view = entry.getKey();
            if (view.getGlobalVisibleRect(hit)) {
                int[] windowPos = new int[2];
                view.getLocationInWindow(windowPos);
                int left = windowPos[0];
                int top = windowPos[1];
                if (x >= left
                        && x < left + view.getWidth()
                        && y >= top
                        && y < top + view.getHeight()) {
                    if (contentScroll != null) {
                        int[] scrollPos = new int[2];
                        contentScroll.getLocationInWindow(scrollPos);
                        if (y < scrollPos[1] || y >= scrollPos[1] + contentScroll.getHeight())
                            continue;
                    }
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private String direction(float dx, float dy) {
        return Gesture.direction(
                dx, dy, ui.dp(FlowUi.clamp(store.number("gestureDistance", 60), 35, 105)));
    }

    private boolean hitView(View v, float x, float y) {
        if (v == null || !v.getGlobalVisibleRect(hit)) return false;
        int[] p = new int[2];
        v.getLocationInWindow(p);
        return x >= p[0] && x < p[0] + v.getWidth() && y >= p[1] && y < p[1] + v.getHeight();
    }

    private Gesture.Route swipeRoute(String direction, float dx, float dy) {
        if (direction.isEmpty() || arranging || !store.flag("gestures", true))
            return Gesture.Route.NONE;
        if (verticalScroll && (direction.equals("left") || direction.equals("right")))
            return Gesture.Route.NONE;
        if (screen == Screen.APPS) {
            return direction.equals("right") && store.flag("drawerHomeSwipe", true)
                    ? Gesture.Route.HOME
                    : Gesture.Route.NONE;
        }
        if (!board() || !hitView(homeViewport, touchX, touchY)) return Gesture.Route.NONE;

        boolean up = direction.equals("up");
        boolean vertical = up || direction.equals("down");
        boolean widget =
                touchedTile != null
                        && (FlowStore.WIDGET.equals(touchedTile.type)
                                || FlowStore.WIDGET_PLACEHOLDER.equals(touchedTile.type));
        if (widget && vertical) return Gesture.Route.NONE;

        boolean hasBinding = false;
        if (touchedTile != null
                && !widget
                && store.flag("tileGestures", true)
                && (!vertical || (up ? atHomeBottom : atHomeTop))) {
            boolean quickAssign =
                    up
                            && FlowStore.APP.equals(touchedTile.type)
                            && store.flag("quickAssign", false)
                            && !store.flag("lockTiles", false);
            hasBinding =
                    !touchedTile.swipe(direction).isEmpty() || returnFace(direction) || quickAssign;
        }
        return Gesture.homeRoute(
                direction,
                Gesture.Route.DRAWER,
                hasBinding,
                Math.max(Math.abs(dx), Math.abs(dy)) >= ui.dp(84),
                store.flag("appsSideSwipe", true),
                false,
                atHomeTop && touchedTile == null && store.flag("actionCenterSwipe", true));
    }

    private boolean returnFace(String dir) {
        String rev =
                switch (dir) {
                    case "left" -> "right";
                    case "right" -> "left";
                    case "up" -> "down";
                    default -> "up";
                };
        return store.flag("cubeFaces", true)
                && !touchedTile.face.isEmpty()
                && touchedTile.face.equals(touchedTile.swipe(rev));
    }

    private void flip(String key, String dir) {
        FlowStore.Tile t = touchedTile;
        t.face = key.equals(t.face) || key.equals(t.app) ? "" : key;
        store.save();
        refresh();
        if (store.number("motion", 1) == 0
                || !android.animation.ValueAnimator.areAnimatorsEnabled()) return;
        for (Map.Entry<View, FlowStore.Tile> e : tileViews.entrySet())
            if (e.getValue() == t) {
                View v = e.getKey();
                v.setCameraDistance(ui.dp(1250));
                v.setPivotX(v.getWidth() / 2f);
                v.post(
                        () -> {
                            v.setPivotX(v.getWidth() / 2f);
                            if (dir.equals("left") || dir.equals("right"))
                                v.setRotationY(dir.equals("left") ? 80 : -80);
                            else v.setRotationX(dir.equals("up") ? -80 : 80);
                            v.animate().rotationX(0).rotationY(0).setDuration(180).start();
                        });
                break;
            }
    }

    private void swipe(Gesture.Route chosen, String direction) {
        if (store.flag("haptics", true))
            getWindow()
                    .getDecorView()
                    .performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
        switch (chosen) {
            case DRAWER -> setScreen(Screen.APPS);
            case TILES -> setScreen(Screen.HOME);
            case CENTER -> setScreen(Screen.ACTION_CENTER);
            case HOME -> {
                drawerQuery = "";
                if (screen == Screen.APPS) setScreen(Screen.HOME);
                else goHome();
            }
            case TILE -> {
                String action = touchedTile.swipe(direction);
                if ("@home".equals(action)) goHome();
                else if ("@apps".equals(action)) setScreen(Screen.APPS);
                else if (!action.isEmpty()) {
                    if (FlowStore.APP.equals(touchedTile.type) && store.flag("cubeFaces", true))
                        flip(action, direction);
                    else launch(action);
                } else if (returnFace(direction)) flip(touchedTile.app, direction);
                else new TileEditor(this).assign(touchedTile, "up", false);
            }
            case NONE -> {}
        }
    }
}
