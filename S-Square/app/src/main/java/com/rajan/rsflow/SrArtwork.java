package com.rajan.rsflow;

import android.content.Context;
import android.graphics.drawable.Drawable;

import java.util.Locale;

final class SrArtwork {
    static final String PACK = "@sr-mono";
    static final String LABEL = "SR Monochrome";
    static final String[] SWATCH_NAMES = {"SR Lavender", "SR Mint", "Cobalt", "Cyan", "Teal", "Emerald", "Indigo", "Violet",
            "Magenta", "Crimson", "Red", "Orange", "Amber", "Olive", "Steel", "Graphite"};
    static final int[] SWATCHES = {0xffb0a3d9, 0xffa5d9a3, 0xff0078d7, 0xff1ba1e2, 0xff00aba9, 0xff008a00, 0xff6a00ff, 0xffaa00ff,
            0xffd80073, 0xffa20025, 0xffe51400, 0xfffa6800, 0xfff0a30a, 0xff6d8764, 0xff647687, 0xff2b2b2b};
    private static final String[][] MATCHES = {
            {"phone", "dialer", "telecom", "phone"},
            {"messages", "messaging", "mms", "sms", "whatsapp", "telegram"},
            {"camera", "camera"}, {"gallery", "photos", "gallery"},
            {"browser", "chrome", "browser", "firefox", "emmx"},
            {"mail", "gmail", "mail", "outlook"}, {"music", "music", "spotify", "audio"},
            {"video", "youtube", "video", "netflix", "vlc"}, {"calendar", "calendar"},
            {"clock", "deskclock", "clock"}, {"settings", "settings"}, {"maps", "maps"},
            {"files", "files", "documentsui", "myfiles"}, {"store", "vending", "store"},
            {"calculator", "calculator"}, {"notes", "keep", "notes"}, {"weather", "weather"},
            {"contacts", "contacts"}, {"games", "game", "playgames"},
            {"social", "instagram", "facebook", "twitter", "reddit", "linkedin"},
            {"shopping", "amazon", "flipkart", "shop"}, {"bank", "pay", "bank", "wallet"},
            {"news", "news"}, {"health", "health", "fitness"},
            {"cloud", "drive", "cloud", "onedrive", "dropbox"},
            {"compass", "compass"}, {"terminal", "termux", "terminal"}
    };

    static Drawable icon(Context context, String component) {
        int separator = component.indexOf('/');
        String packageName = (separator >= 0 ? component.substring(0, separator) : component)
                .toLowerCase(Locale.ROOT);
        for (String[] match : MATCHES) for (int i = 1; i < match.length; i++) {
            if (packageName.contains(match[i])) {
                int id = context.getResources().getIdentifier("ic_metro_" + match[0],
                        "drawable", context.getPackageName());
                return id == 0 ? null : context.getDrawable(id).mutate();
            }
        }
        return null;
    }
}
