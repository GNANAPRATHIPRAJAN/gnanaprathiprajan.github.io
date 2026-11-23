package com.rajan.rsflow;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.util.LruCache;

import java.text.Collator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

final class AppCatalog {
    static final class App {
        final String key;
        final String label;
        final ResolveInfo info;
        private final String searchLabel, searchPackage;

        App(ResolveInfo info, PackageManager manager) {
            this.info = info;
            key = new ComponentName(info.activityInfo.packageName, info.activityInfo.name)
                    .flattenToShortString();
            label = info.loadLabel(manager).toString();
            searchLabel = label.toLowerCase(Locale.ROOT);
            searchPackage = info.activityInfo.packageName.toLowerCase(Locale.ROOT);
        }

        boolean matchesWord(String word) { return searchLabel.contains(word) || searchPackage.contains(word); }

        boolean matches(String[] words) {
            for (String word : words) if (!matchesWord(word)) return false;
            return true;
        }
    }

    private List<App> ordered = java.util.Collections.emptyList();
    private final Context context;
    private final PackageManager manager;
    private final java.util.HashMap<String, Long> installedAt = new java.util.HashMap<>();
    private final LinkedHashMap<String, App> lookup = new LinkedHashMap<>();
    private final LruCache<String, Drawable.ConstantState> icons = new LruCache<>(96);

    AppCatalog(Context context) {
        this.context = context;
        manager = context.getPackageManager();
    }

    void refresh() {
        lookup.clear();
        installedAt.clear();
        icons.evictAll();
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> activities = manager.queryIntentActivities(launcher, 0);
        ArrayList<App> all = new ArrayList<>();
        for (ResolveInfo resolve : activities) {
            if (resolve.activityInfo != null &&
                    !context.getPackageName().equals(resolve.activityInfo.packageName)) {
                all.add(new App(resolve, manager));
            }
        }
        Collator collator = Collator.getInstance();
        all.sort((a, b) -> collator.compare(a.label, b.label));
        for (App app : all) lookup.put(app.key, app);
        ordered = java.util.Collections.unmodifiableList(new ArrayList<>(lookup.values()));
    }

    List<App> list() { return ordered; }
    App find(String key) {
        App exact = lookup.get(key);
        if (exact != null || key.isEmpty() || key.indexOf('/') >= 0) return exact;
        for (App app : lookup.values()) if (app.info.activityInfo.packageName.equals(key)) return app;
        return null;
    }

    long firstInstall(App app) {
        return installedAt.computeIfAbsent(app.info.activityInfo.packageName, name -> {
            try { return manager.getPackageInfo(name, 0).firstInstallTime; }
            catch (PackageManager.NameNotFoundException e) { return 0L; }
        });
    }

    Drawable icon(String key) {
        App app = find(key);
        if (app == null) return null;
        Drawable.ConstantState cached = icons.get(key);
        if (cached != null) return cached.newDrawable(context.getResources()).mutate();
        try {
            Drawable result = app.info.loadIcon(manager);
            if (result != null && result.getConstantState() != null) icons.put(key, result.getConstantState());
            return result;
        } catch (RuntimeException ignored) {
            return manager.getDefaultActivityIcon();
        }
    }

    Intent launchIntent(String key) {
        App app = find(key);
        if (app == null) return null;
        ComponentName component = ComponentName.unflattenFromString(app.key);
        if (component == null) return null;
        return Intent.makeMainActivity(component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
    }
}
