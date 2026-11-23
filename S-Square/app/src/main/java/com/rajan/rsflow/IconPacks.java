package com.rajan.rsflow;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.Drawable;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

final class IconPacks {
    private static final String[] ACTIONS = {
        "org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.teslacoilsw.launcher.THEME",
        "com.anddoes.launcher.THEME", "com.fede.launcher.THEME_ICONPACK",
                "com.gau.go.launcherex.theme"
    };
    private final PackageManager manager;
    private final Map<String, String> icons = new LinkedHashMap<>();
    private String selected = "";
    private Resources resources;

    IconPacks(Context context) {
        manager = context.getPackageManager();
    }

    static String title(String key, Map<String, String> installed) {
        return switch (key) {
            case SrArtwork.PACK -> SrArtwork.LABEL + " · built in";
            case BuiltInIcons.ASTRA, BuiltInIcons.DESKTOP,
                            BuiltInIcons.GLASS,
                            BuiltInIcons.STICKER,
                            BuiltInIcons.LINE,
                            BuiltInIcons.COLOR,
                            BuiltInIcons.INK ->
                    BuiltInIcons.name(key) + " · built in";
            case "" -> "Original app icons";
            default -> installed.getOrDefault(key, "Currently unavailable");
        };
    }

    Map<String, String> available() {
        Map<String, String> result = new LinkedHashMap<>();
        for (String action : ACTIONS) {
            for (ResolveInfo entry : manager.queryIntentActivities(new Intent(action), 0)) {
                if (entry.activityInfo == null) continue;
                result.putIfAbsent(
                        entry.activityInfo.packageName, entry.loadLabel(manager).toString());
            }
        }
        return result;
    }

    void select(String packageName) {
        if (selected.equals(packageName)) return;
        selected = packageName;
        resources = null;
        icons.clear();
        if (packageName.isEmpty() || packageName.startsWith("@")) return;
        try {
            resources = manager.getResourcesForApplication(packageName);
            int id = resources.getIdentifier("appfilter", "xml", packageName);
            if (id != 0) {
                try (XmlResourceParser xml = resources.getXml(id)) {
                    read(xml);
                }
            } else {
                try (InputStream stream =
                        new BufferedInputStream(resources.getAssets().open("appfilter.xml"))) {
                    XmlPullParser parser = Xml.newPullParser();
                    parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false);
                    parser.setInput(stream, "UTF-8");
                    read(parser);
                }
            }
        } catch (Exception ignored) {
            resources = null;
            icons.clear();
        }
    }

    void invalidate() {
        selected = "\n";
    }

    private void read(XmlPullParser parser) throws Exception {
        for (int events = 0; events < 175000; events++) {
            int event = parser.next();
            if (event == XmlPullParser.END_DOCUMENT) return;
            if (event != XmlPullParser.START_TAG || !"item".equals(parser.getName())) continue;
            String component = parser.getAttributeValue(null, "component");
            String drawable = parser.getAttributeValue(null, "drawable");
            if (component == null
                    || component.length() > 525
                    || drawable == null
                    || drawable.length() > 110
                    || !drawable.matches("[a-zA-Z0-9_]+")
                    || !component.startsWith("ComponentInfo{")
                    || !component.endsWith("}")) continue;
            ComponentName name =
                    ComponentName.unflattenFromString(
                            component.substring(14, component.length() - 1));
            if (name != null) icons.putIfAbsent(name.flattenToShortString(), drawable);
        }
    }

    Drawable icon(String key) {
        if (resources == null) return null;
        String drawable = icons.get(key);
        if (drawable == null) return null;
        try {
            int id = resources.getIdentifier(drawable, "drawable", selected);
            if (id != 0) return resources.getDrawable(id, null).mutate();
        } catch (RuntimeException ignored) {
        }
        return null;
    }
}
