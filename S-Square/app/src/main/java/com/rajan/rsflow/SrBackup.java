package com.rajan.rsflow;

import android.content.ComponentName;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedHashSet;

final class SrBackup {
    static boolean supports(JSONObject input) {
        return input.optInt("version", -1) == 2 && input.optJSONObject("settings") != null
                && input.optJSONArray("tiles") != null && !input.has("pages");
    }

    static JSONObject convert(JSONObject input) throws JSONException {
        JSONObject settings = input.getJSONObject("settings");
        JSONArray source = input.getJSONArray("tiles");
        if (source.length() > FlowStore.MAX_TILES * FlowStore.MAX_PAGES) {
            throw new JSONException("Too many SRLauncher tiles");
        }
        JSONObject options = new JSONObject();
        options.put("theme", settings.optBoolean("darkMode", true) ? 0 : 1);
        options.put("accent", (int) settings.optLong("accentColorHex", 0xffb0a3d9L));
        options.put("defaultTileColor", options.getInt("accent"));
        options.put("tileOpacity", percent(settings, "tileOpacity", .8));
        options.put("gap", Math.round(settings.optDouble("tileGutterDp", 4)));
        options.put("corner", Math.round(settings.optDouble("tileCornerDp", 8)));
        options.put("wallDim", percent(settings, "wallpaperDim", .28));
        options.put("labelSize", settings.optInt("tileLabelSizeSp", 12));
        options.put("iconSize", Math.round(35 * settings.optDouble("iconScale", 1)));
        options.put("columns", Math.min(4, Math.max(1, settings.optInt("columnCount", 6) / 2)));
        options.put("showLabels", settings.optBoolean("showTileLabels", true));
        options.put("denseTiles", true);
        options.put("badges", settings.optBoolean("liveTilesEnabled", false));
        switch (settings.optString("selectedFontFamily")) {
            case "font_spacegrotesk", "font_space_grotesk" -> options.put("font", 1);
            case "font_dmsans" -> options.put("font", 2);
            default -> options.put("font", 0);
        }
        String pack = settings.optString("selectedIconPackId", "builtin_metro_white");
        switch (pack) {
            case "builtin_metro_white" -> options.put("iconPack", SrArtwork.PACK);
            case "builtin_fluent_glass" -> options.put("iconPack", BuiltInIcons.COLOR);
            case "builtin_system" -> options.put("iconPack", "");
            default -> {
                if (pack.matches("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+")) options.put("iconPack", pack);
            }
        }
        JSONArray pages = new JSONArray();
        JSONArray tiles = new JSONArray();
        for (int i = 0; i < source.length(); i++) {
            JSONObject tile = tile(source.getJSONObject(i));
            if (tile == null) continue;
            if (tiles.length() == FlowStore.MAX_TILES) {
                pages.put(page(pages.length(), tiles));
                tiles = new JSONArray();
            }
            tiles.put(tile);
        }
        if (tiles.length() > 0 || pages.length() == 0) pages.put(page(pages.length(), tiles));
        JSONArray hidden = settings.optJSONArray("hiddenPackages");
        return new JSONObject().put("version", 1).put("initialized", true)
                .put("pages", pages).put("options", options)
                .put("hiddenPackages", hidden == null ? new JSONArray() : hidden);
    }

    private static JSONObject page(int index, JSONArray tiles) throws JSONException {
        return new JSONObject().put("name", index == 0 ? "Home" : "Home " + (index + 1))
                .put("tiles", tiles);
    }

    private static JSONObject tile(JSONObject from) throws JSONException {
        String sourceType = from.optString("type", "APP");
        String type = switch (sourceType) {
            case "APP" -> FlowStore.APP;
            case "FOLDER" -> FlowStore.FOLDER;
            case "WIDGET" -> FlowStore.WIDGET_PLACEHOLDER;
            case "LIVE_CLOCK", "LIVE_BATTERY", "LIVE_CALENDAR", "LIVE_GALLERY", "LIVE_PEOPLE", "LIVE_MUSIC" -> FlowStore.LIVE;
            default -> "";
        };
        if (type.isEmpty()) return null;
        JSONObject to = new JSONObject().put("type", type).put("label", from.optString("label"));
        int size = switch (from.optString("size", "MEDIUM")) {
            case "SMALL" -> TileLayout.SMALL;
            case "WIDE" -> TileLayout.WIDE;
            case "LARGE" -> TileLayout.LARGE;
            default -> TileLayout.STANDARD;
        };
        if (FlowStore.WIDGET_PLACEHOLDER.equals(type)) {
            size = size == TileLayout.LARGE ? 4 : size == TileLayout.WIDE ? 2 : 1;
        }
        to.put("size", size).put("color", (int) from.optLong("customColorHex", 0));
        if (FlowStore.APP.equals(type)) {
            String app = component(from);
            if (app.isEmpty()) return null;
            to.put("app", app);
        } else if (FlowStore.FOLDER.equals(type)) {
            LinkedHashSet<String> children = new LinkedHashSet<>();
            folderApps(from.optJSONArray("folderChildren"), children, 0);
            to.put("items", new JSONArray(children));
        } else if (FlowStore.LIVE.equals(type)) {
            String live = switch (sourceType) {
                case "LIVE_BATTERY" -> "battery";
                case "LIVE_CALENDAR" -> "calendar";
                case "LIVE_GALLERY" -> "photo";
                case "LIVE_PEOPLE" -> "people";
                case "LIVE_MUSIC" -> "music";
                default -> "clock";
            };
            to.put("live", live).put("app", component(from));
        }
        return to;
    }

    private static void folderApps(JSONArray source, LinkedHashSet<String> output, int depth) {
        if (source == null || depth >= 8) return;
        for (int i = 0; i < source.length() && output.size() < FlowStore.MAX_FOLDER_APPS; i++) {
            JSONObject item = source.optJSONObject(i);
            if (item == null) continue;
            String key = component(item);
            if (!key.isEmpty()) output.add(key);
            folderApps(item.optJSONArray("folderChildren"), output, depth + 1);
        }
    }

    private static String component(JSONObject tile) {
        String pkg = tile.optString("packageName");
        if (!pkg.matches("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+")) return "";
        String className = tile.optString("activityName");
        if (className.isEmpty()) return pkg;
        if (className.startsWith(".")) className = pkg + className;
        if (!className.matches("[A-Za-z_$][A-Za-z0-9_$.]*")) return pkg;
        return new ComponentName(pkg, className).flattenToShortString();
    }

    private static long percent(JSONObject input, String key, double fallback) {
        return Math.round(100 * Math.max(0, Math.min(1, input.optDouble(key, fallback))));
    }
}
