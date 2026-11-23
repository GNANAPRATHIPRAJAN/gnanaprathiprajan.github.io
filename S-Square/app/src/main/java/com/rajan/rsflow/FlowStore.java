package com.rajan.rsflow;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

final class FlowStore {
    static final String[] DIRECTIONS = {"up", "down", "left", "right"};
    static final int MAX_PAGES = 32;
    static final int MAX_TILES = 3200;
    static final int MAX_FOLDER_APPS = 100;
    static final String APP = "app",
            FOLDER = "folder",
            NOTE = "note",
            WIDGET = "widget",
            WIDGET_PLACEHOLDER = "widgetPlaceholder",
            LIVE = "live";
    final ArrayList<Page> pages = new ArrayList<>();
    final Set<String> hidden = new LinkedHashSet<>();
    final Set<String> hiddenPackages = new LinkedHashSet<>();
    JSONObject options = new JSONObject();
    private final SharedPreferences preferences;
    boolean initialized;
    int homePage;
    int activePage;

    static final class Page {
        String name;
        final ArrayList<Tile> tiles = new ArrayList<>();

        Page(String name) {
            this.name = name;
        }
    }

    static final class Tile {
        String type;
        String app = "";
        String label = "";
        String body = "";
        String icons = "";
        String imageId = "";
        int color;
        int widgetId = -1, shape = -1;
        String face = "", category = "";
        boolean wide;
        int size;
        int gridX = -1, gridY = -1, gridColumns;
        String live = "";
        int liveFlip;
        final ArrayList<String> items = new ArrayList<>();
        final JSONObject swipes = new JSONObject();

        Tile(String type) {
            this.type = type;
        }

        String swipe(String direction) {
            return swipes.optString(direction, "");
        }

        void setSwipe(String direction, String value) {
            if (value.isEmpty()) swipes.remove(direction);
            else put(swipes, direction, value);
        }
    }

    FlowStore(Context context) {
        preferences = context.getSharedPreferences("rs_flow", Context.MODE_PRIVATE);
        String stored = preferences.getString("state", "");
        if (stored.isEmpty()) {
            put(options, "iconPack", BuiltInIcons.DESKTOP);
            put(options, "shell", 1);
            put(options, "theme", 3);
            put(options, "accent", 0xffbad98b);
            put(options, "font", 0);
            put(options, "showDock", true);
        } else {
            try {
                read(new JSONObject(stored));
            } catch (JSONException ignored) {
                pages.clear();
            }
        }
        if (pages.isEmpty()) pages.add(new Page("Home"));
    }

    boolean desktop() {
        return number("shell", 0) == 1;
    }

    boolean flag(String key, boolean fallback) {
        return options.optBoolean(key, fallback);
    }

    int number(String key, int fallback) {
        return options.optInt(key, fallback);
    }

    String option(String key, String fallback) {
        return options.optString(key, fallback);
    }

    void set(String key, Object value) {
        put(options, key, value);
        save();
    }

    void applyMidnightStyle() {
        for (String key :
                new String[] {
                    "accent",
                    "defaultTileColor",
                    "icons",
                    "homeMargin",
                    "wallScale",
                    "wallDim",
                    "showClock",
                    "clockStyle",
                    "iconSize",
                    "labelSize",
                    "height",
                    "textScale",
                    "font",
                    "tintedTiles"
                }) options.remove(key);
        put(options, "theme", 0);
        put(options, "accent", FlowUi.ACCENTS[2]);
        put(options, "font", 0);
        put(options, "iconPack", BuiltInIcons.DESKTOP);
        put(options, "background", Wallpapers.MIDNIGHT);
        put(options, "showDock", true);
        put(options, "columns", 4);
        put(options, "appColors", false);
        put(options, "floatingCube", true);
        put(options, "tileOutline", false);
        put(options, "squareTiles", true);
        put(options, "gap", 7);
        put(options, "corner", 16);
        save();
    }

    void applyStartStyle() {
        for (String key :
                new String[] {
                    "shell",
                    "theme",
                    "font",
                    "accent",
                    "background",
                    "defaultTileColor",
                    "tileOpacity",
                    "corner",
                    "gap",
                    "height",
                    "squareTiles",
                    "homeMargin",
                    "denseTiles",
                    "columns",
                    "iconSize",
                    "icons",
                    "labelSize",
                    "tintedTiles",
                    "featureTile",
                    "showClock",
                    "showDock",
                    "showHeader",
                    "showLabels",
                    "showTileIcons"
                }) options.remove(key);
        put(options, "iconPack", "");
        save();
    }

    boolean isHidden(AppCatalog.App app) {
        return hidden.contains(app.key)
                || hiddenPackages.contains(app.info.activityInfo.packageName);
    }

    void go(int page) {
        int next = Math.max(0, Math.min(page, pages.size() - 1));
        if (activePage == next) return;
        activePage = next;
        save();
    }

    void save() {
        preferences.edit().putString("state", json(false).toString()).apply();
    }

    JSONObject json(boolean portable) {
        JSONObject root = new JSONObject();
        put(root, "version", 1);
        put(root, "initialized", initialized);
        put(root, "homePage", homePage);
        put(root, "activePage", activePage);
        put(root, "options", options);
        put(root, "hidden", new JSONArray(hidden));
        put(root, "hiddenPackages", new JSONArray(hiddenPackages));
        JSONArray pageArray = new JSONArray();
        for (Page page : pages) {
            JSONObject entry = new JSONObject();
            put(entry, "name", page.name);
            JSONArray tileArray = new JSONArray();
            for (Tile tile : page.tiles) {
                JSONObject data = new JSONObject();
                String type = portable && WIDGET.equals(tile.type) ? WIDGET_PLACEHOLDER : tile.type;
                put(data, "type", type);
                put(data, "app", tile.app);
                put(data, "label", tile.label);
                put(data, "body", tile.body);
                put(data, "icons", tile.icons);
                put(data, "face", tile.face);
                put(data, "shape", tile.shape);
                put(data, "category", tile.category);
                if (!portable && !tile.imageId.isEmpty()) put(data, "imageId", tile.imageId);
                put(data, "color", tile.color);
                put(data, "wide", tile.wide);
                put(data, "size", tile.size);
                if (tile.gridColumns > 0) {
                    put(data, "gridColumns", tile.gridColumns);
                    put(data, "gridX", tile.gridX);
                    put(data, "gridY", tile.gridY);
                }
                put(data, "live", tile.live);
                put(data, "liveFlip", tile.liveFlip);
                put(data, "widgetId", portable ? -1 : tile.widgetId);
                put(data, "items", new JSONArray(tile.items));
                put(data, "swipes", tile.swipes);
                tileArray.put(data);
            }
            put(entry, "tiles", tileArray);
            pageArray.put(entry);
        }
        put(root, "pages", pageArray);
        return root;
    }

    void read(JSONObject root) throws JSONException {
        JSONArray input = root.getJSONArray("pages");
        if (root.optInt("version", -1) != 1 || input.length() < 1 || input.length() > MAX_PAGES) {
            throw new JSONException("Unsupported RS Flow backup");
        }
        ArrayList<Page> nextPages = new ArrayList<>();
        int total = 0;
        for (int i = 0; i < input.length(); i++) {
            JSONObject data = input.getJSONObject(i);
            Page page = new Page(clipped(data.optString("name", "Home"), 40));
            JSONArray list = data.getJSONArray("tiles");
            if ((total += list.length()) > MAX_TILES) throw new JSONException("Too many tiles");
            for (int j = 0; j < list.length(); j++) page.tiles.add(readTile(list.getJSONObject(j)));
            nextPages.add(page);
        }
        LinkedHashSet<String> nextHidden = new LinkedHashSet<>();
        JSONArray hiddenApps = root.optJSONArray("hidden");
        if (hiddenApps != null) {
            for (int i = 0; i < Math.min(2000, hiddenApps.length()); i++) {
                String app = clipped(hiddenApps.optString(i), 400);
                if (!app.isEmpty()) nextHidden.add(app);
            }
        }
        LinkedHashSet<String> nextHiddenPackages = new LinkedHashSet<>();
        JSONArray hiddenFromSr = root.optJSONArray("hiddenPackages");
        if (hiddenFromSr != null)
            for (int i = 0; i < Math.min(2000, hiddenFromSr.length()); i++) {
                String pkg = clipped(hiddenFromSr.optString(i), 250);
                if (pkg.matches("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+"))
                    nextHiddenPackages.add(pkg);
            }
        JSONObject nextOptions = root.optJSONObject("options");
        pages.clear();
        pages.addAll(nextPages);
        hidden.clear();
        hidden.addAll(nextHidden);
        hiddenPackages.clear();
        hiddenPackages.addAll(nextHiddenPackages);
        options = readOptions(nextOptions);
        initialized = root.optBoolean("initialized", true);
        homePage = index(root.optInt("homePage", 0), pages.size());
        if (pages.size() > 1) {
            Page one = new Page("Home");
            one.tiles.addAll(pages.get(homePage).tiles);
            for (int i = 0; i < pages.size(); i++)
                if (i != homePage) one.tiles.addAll(pages.get(i).tiles);
            pages.clear();
            pages.add(one);
        }
        activePage = homePage = 0;
    }

    Set<Integer> widgetIds() {
        HashSet<Integer> ids = new HashSet<>();
        for (Page page : pages)
            for (Tile tile : page.tiles) {
                if (WIDGET.equals(tile.type) && tile.widgetId > 0) ids.add(tile.widgetId);
            }
        return ids;
    }

    void clearForeignAssets() {
        for (Page page : pages)
            for (Tile tile : page.tiles) {
                tile.imageId = "";
                if (WIDGET.equals(tile.type)) {
                    tile.type = WIDGET_PLACEHOLDER;
                    tile.widgetId = -1;
                }
            }
    }

    private static JSONObject readOptions(JSONObject input) {
        JSONObject safe = new JSONObject();
        if (input == null) return safe;
        String[] booleans = {
            "featureTile",
            "tintedTiles",
            "showLabels",
            "showTileIcons",
            "showClock",
            "showDate",
            "gestures",
            "drawerHomeSwipe",
            "homeDefaultSwipe",
            "homeAppsSwipe",
            "pageSwipes",
            "tileGestures",
            "quickAssign",
            "doubleTap",
            "haptics",
            "showHeader",
            "showDock",
            "showPageTabs",
            "lockTiles",
            "appsSideSwipe",
            "restoreDrawerPage",
            "recentApps",
            "badges",
            "denseTiles",
            "squareTiles",
            "actionCenterSwipe",
            "fullHome",
            "cubeFaces",
            "appColors",
            "floatingCube",
            "tileShake",
            "drawerSearch",
            "drawerCompact",
            "tileOutline",
            "livePhotos"
        };
        String[] numbers = {
            "theme",
            "accent",
            "font",
            "background",
            "wallDim",
            "wallScale",
            "columns",
            "height",
            "corner",
            "cubePose",
            "widgetColumns",
            "tilesMargin",
            "lockClockStyle",
            "lockClockAlign",
            "lockClockFont",
            "lockClockSize",
            "lockClockY",
            "lockClockColor",
            "lockClockFormat",
            "cubePositionX",
            "cubePositionY",
            "gap",
            "iconSize",
            "labelSize",
            "clockStyle",
            "clockAlign",
            "clockFormat",
            "icons",
            "gestureDistance",
            "tileOpacity",
            "homeMargin",
            "defaultTileColor",
            "shell",
            "taskbarAlign",
            "taskbarPins",
            "startColumns",
            "startIconSize",
            "drawerIconSize",
            "drawerLabelSize",
            "textScale",
            "motion",
            "lockImage",
            "lockDim",
            "lockSolid",
            "lockZoom",
            "lockX",
            "lockY",
            "lockTextY",
            "lockTextSize",
            "lockFont",
            "lockInk",
            "solidColor"
        };
        for (String key : booleans) if (input.has(key)) put(safe, key, input.optBoolean(key));
        for (String key : numbers) if (input.has(key)) put(safe, key, input.optInt(key));
        for (String key :
                new String[] {
                    "centerShortcuts",
                    "drawerPack",
                    "cube0",
                    "cube1",
                    "cube2",
                    "cube3",
                    "cube4",
                    "cube5"
                }) if (input.has(key)) put(safe, key, clipped(input.optString(key), 400));
        if (input.has("lockText")) put(safe, "lockText", clipped(input.optString("lockText"), 64));
        if (input.has("iconPack")) put(safe, "iconPack", clipped(input.optString("iconPack"), 250));
        return safe;
    }

    private static Tile readTile(JSONObject data) throws JSONException {
        String type = data.getString("type");
        if (!(APP.equals(type)
                || NOTE.equals(type)
                || FOLDER.equals(type)
                || WIDGET.equals(type)
                || WIDGET_PLACEHOLDER.equals(type)
                || LIVE.equals(type))) {
            throw new JSONException("Unsupported tile");
        }
        Tile tile = new Tile(type);
        tile.shape = Math.max(-1, Math.min(4, data.optInt("shape", -1)));
        tile.face = clipped(data.optString("face"), 400);
        tile.category = clipped(data.optString("category"), 40);
        tile.app = clipped(data.optString("app"), 400);
        tile.label = clipped(data.optString("label"), 60);
        tile.body = clipped(data.optString("body"), 500);
        tile.icons = clipped(data.optString("icons"), 80);
        String imageId = data.optString("imageId");
        if (validImageId(imageId)) tile.imageId = imageId;
        tile.wide = data.optBoolean("wide", false);
        tile.size = data.optInt("size", 0);
        tile.gridX = data.optInt("gridX", -1);
        tile.gridY = data.optInt("gridY", -1);
        tile.gridColumns = data.optInt("gridColumns", 0);
        if (tile.gridX < 0 || tile.gridX >= 10 || tile.gridY < 0 || tile.gridY > MAX_TILES * 5)
            tile.gridColumns = 0;
        tile.live = clipped(data.optString("live"), 24);
        tile.liveFlip = Math.max(0, Math.min(2, data.optInt("liveFlip", 0)));
        tile.color = data.optInt("color", 0);
        tile.widgetId = data.optInt("widgetId", -1);
        JSONArray items = data.optJSONArray("items");
        if (items != null)
            for (int i = 0; i < Math.min(MAX_FOLDER_APPS, items.length()); i++) {
                String app = clipped(items.optString(i), 400);
                if (!app.isEmpty() && !tile.items.contains(app)) tile.items.add(app);
            }
        JSONObject swipes = data.optJSONObject("swipes");
        if (swipes != null) {
            Iterator<String> keys = swipes.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                for (String allowed : DIRECTIONS) {
                    if (key.equals(allowed))
                        tile.setSwipe(key, clipped(swipes.optString(key), 400));
                }
            }
        }
        return tile;
    }

    static boolean validImageId(String value) {
        if (value.length() != 32) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c < '0' || c > '9') && (c < 'a' || c > 'f')) return false;
        }
        return true;
    }

    static String clipped(String value, int length) {
        return value.length() > length ? value.substring(0, length) : value;
    }

    private static int index(int value, int length) {
        return Math.min(length - 1, Math.max(0, value));
    }

    static void put(JSONObject object, String key, Object value) {
        try {
            object.put(key, value);
        } catch (JSONException e) {
            throw new IllegalArgumentException(key, e);
        }
    }
}
