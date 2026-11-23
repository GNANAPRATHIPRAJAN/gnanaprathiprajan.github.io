package com.rajan.rsflow;

import android.content.pm.ApplicationInfo;
import android.graphics.*;
import android.graphics.drawable.Drawable;

final class AppStyle {
  static final String[] NAMES = {
    "Other",
    "Communication",
    "Entertainment",
    "Games",
    "Banking",
    "Browsing",
    "Photos",
    "Productivity",
    "Shopping",
    "Travel"
  };
  private static final String[][] HINTS = {
    {},
    {"phone", "dialer", "messag", "contact", "whatsapp", "mail"},
    {"video", "music", "spotify", "youtube", "netflix"},
    {"game", "chess", "xbox"},
    {"bank", "wallet", "finance", "paytm", "phonepe"},
    {"browser", "chrome", "firefox", "opera"},
    {"camera", "gallery", "photos"},
    {"document", "office", "calendar", "notes"},
    {"amazon", "shop", "store"},
    {"maps", "travel", "uber"}
  };

  static String group(AppCatalog.App a) {
    if (a == null) return NAMES[0];
    for (int i = 1; i < NAMES.length; i++)
      for (String hint : HINTS[i]) if (a.matchesWord(hint)) return NAMES[i];
    return switch (a.info.activityInfo.applicationInfo.category) {
      case ApplicationInfo.CATEGORY_GAME -> NAMES[3];
      case ApplicationInfo.CATEGORY_VIDEO, ApplicationInfo.CATEGORY_AUDIO -> NAMES[2];
      case ApplicationInfo.CATEGORY_MAPS -> NAMES[9];
      case ApplicationInfo.CATEGORY_PRODUCTIVITY -> NAMES[7];
      default -> NAMES[0];
    };
  }

  private final android.util.LruCache<String, Integer> colors = new android.util.LruCache<>(256);

  void reset() {
    colors.evictAll();
  }

  int color(MainActivity a, String key) {
    Integer cached = colors.get(key);
    if (cached != null) return cached;
    int result = Sheet.COLORS[Math.floorMod(key.hashCode(), Sheet.COLORS.length)];
    Drawable icon = a.apps.icon(key);
    if (icon != null) {
      Bitmap b = Bitmap.createBitmap(18, 18, Bitmap.Config.ARGB_8888);
      Rect bounds = new Rect(icon.getBounds());
      try {
        icon.setBounds(0, 0, 18, 18);
        icon.draw(new Canvas(b));
        float[] hist = new float[12], hsv = new float[3];
        for (int y = 1; y < 18; y += 2)
          for (int x = 1; x < 18; x += 2) {
            int p = b.getPixel(x, y);
            if (Color.alpha(p) < 160) continue;
            Color.colorToHSV(p, hsv);
            if (hsv[1] > .25 && hsv[2] > .23) hist[Math.min(11, (int) (hsv[0] / 30))] += hsv[1];
          }
        int best = 0;
        for (int i = 1; i < 12; i++) if (hist[i] > hist[best]) best = i;
        if (hist[best] > 1) result = Color.HSVToColor(new float[] {best * 30 + 13, .62f, .55f});
      } catch (RuntimeException ignored) {
      } finally {
        icon.setBounds(bounds);
        b.recycle();
      }
    }
    colors.put(key, result);
    return result;
  }
}
