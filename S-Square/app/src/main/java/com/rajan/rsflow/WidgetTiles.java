package com.rajan.rsflow;

import android.app.Activity;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import java.text.Collator;
import java.util.ArrayList;

final class WidgetTiles {
    private static final int BIND = 101, CONFIGURE = 102, HOST = 0x5253;
    private final MainActivity activity;
    private final AppWidgetHost host;
    private final AppWidgetManager manager;
    private int pending = -1;
    private int page = -1;
    private int replace = -1;
    private ComponentName provider;

    WidgetTiles(MainActivity activity, Bundle state) {
        this.activity = activity;
        host = new AppWidgetHost(activity, HOST);
        manager = AppWidgetManager.getInstance(activity);
        if (state != null) {
            pending = state.getInt("pendingWidget", -1);
            page = state.getInt("widgetPage", -1);
            replace = state.getInt("replaceWidget", -1);
            String component = state.getString("widgetProvider", "");
            provider = ComponentName.unflattenFromString(component);
        }
    }

    void savePending(Bundle state) {
        state.putInt("pendingWidget", pending);
        state.putInt("widgetPage", page);
        state.putInt("replaceWidget", replace);
        state.putString("widgetProvider", provider == null ? "" : provider.flattenToShortString());
    }

    void start() {
        try { host.startListening(); } catch (RuntimeException ignored) { }
    }

    void stop() {
        try {
            host.stopListening();
        } catch (RuntimeException ignored) { }
    }

    View view(FlowStore.Tile tile) {
        AppWidgetProviderInfo info = manager == null ? null : manager.getAppWidgetInfo(tile.widgetId);
        if (info != null) {
            try {
                AppWidgetHostView widget = host.createView(activity, tile.widgetId, info);
                widget.post(() -> {
                    int width = (int) (widget.getWidth() / activity.getResources().getDisplayMetrics().density);
                    int height = (int) (widget.getHeight() / activity.getResources().getDisplayMetrics().density);
                    if (width > 0 && height > 0) {
                        try { widget.updateAppWidgetSize(new Bundle(), width, height, width, height); }
                        catch (RuntimeException ignored) { }
                    }
                });
                return widget;
            } catch (RuntimeException ignored) { }
        }
        TextView unavailable = activity.ui.label("Widget unavailable · tap to replace", 14, activity.ui.dim);
        unavailable.setGravity(Gravity.CENTER);
        unavailable.setOnClickListener(view -> choose(activity.page(), tile));
        return unavailable;
    }

    void choose(FlowStore.Page destination, FlowStore.Tile previous) {
        if (pending > 0) { activity.toast("A widget is already being added"); return; }
        if (previous == null && destination.tiles.size() >= FlowStore.MAX_TILES) {
            activity.toast("This page is full");
            return;
        }
        if (manager == null) { activity.toast("Android widgets are unavailable on this device"); return; }
        ArrayList<AppWidgetProviderInfo> available;
        try { available = new ArrayList<>(manager.getInstalledProviders()); }
        catch (RuntimeException ignored) { activity.toast("Could not list Android widgets"); return; }
        if (available.isEmpty()) {
            activity.dialog().setTitle("Android widgets").setMessage("There are no widgets from the apps installed on this device.")
                    .setPositiveButton("Close", null).show();
            return;
        }
        Collator sorter = Collator.getInstance();
        available.sort((a, b) -> sorter.compare(label(a), label(b)));
        String[] labels = new String[available.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = label(available.get(i));
        activity.dialog().setTitle("Add an Android widget").setItems(labels, (dialog, index) ->
                bind(destination, previous, available.get(index))).setNegativeButton("Cancel", null).show();
    }

    private String label(AppWidgetProviderInfo info) {
        String name = info.loadLabel(activity.getPackageManager());
        return name == null || name.isEmpty() ? info.provider.getPackageName() : name;
    }

    private void bind(FlowStore.Page destination, FlowStore.Tile previous, AppWidgetProviderInfo info) {
        try {
            pending = host.allocateAppWidgetId();
            page = activity.store.pages.indexOf(destination);
            replace = previous == null ? -1 : destination.tiles.indexOf(previous);
            provider = info.provider;
            if (manager.bindAppWidgetIdIfAllowed(pending, provider)) {
                configure();
            } else {
                Intent bind = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pending)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider);
                activity.startActivityForResult(bind, BIND);
            }
        } catch (RuntimeException ignored) {
            cancel();
            activity.toast("Android did not allow this widget to bind");
        }
    }

    private void configure() {
        AppWidgetProviderInfo info = manager.getAppWidgetInfo(pending);
        if (info == null || !info.provider.equals(provider)) { cancel(); return; }
        if (info.configure == null || (android.os.Build.VERSION.SDK_INT >= 31 &&
                (info.widgetFeatures & AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL) != 0)) {
            finish(info);
            return;
        }
        try { host.startAppWidgetConfigureActivityForResult(activity, pending, 0, CONFIGURE, null); }
        catch (RuntimeException ignored) {
            cancel();
            activity.toast("The widget’s configuration screen is unavailable");
        }
    }

    boolean onResult(int code, int result, Intent data) {
        if (code != BIND && code != CONFIGURE) return false;
        if (pending < 0) return true;
        if (result != Activity.RESULT_OK) { cancel(); return true; }
        try {
            if (code == BIND) configure();
            else {
                AppWidgetProviderInfo info = manager.getAppWidgetInfo(pending);
                if (info != null && info.provider.equals(provider)) finish(info);
                else cancel();
            }
        } catch (RuntimeException ignored) { cancel(); }
        return true;
    }

    private void finish(AppWidgetProviderInfo info) {
        if (page < 0 || page >= activity.store.pages.size()) { cancel(); return; }
        FlowStore.Page target = activity.store.pages.get(page);
        FlowStore.Tile tile = new FlowStore.Tile(FlowStore.WIDGET);
        tile.widgetId = pending;
        tile.label = label(info);
        tile.wide = true;
        tile.app = provider.flattenToShortString();
        if (replace >= 0 && replace < target.tiles.size()) {
            FlowStore.Tile previous = target.tiles.set(replace, tile);
            tile.color = previous.color;
            tile.size = previous.size;
            tile.gridX=previous.gridX;tile.gridY=previous.gridY;tile.gridColumns=previous.gridColumns;
            if (!previous.label.isEmpty()) tile.label = previous.label;
            activity.store.save();
            delete(previous);
        } else if (!activity.addTile(tile, target)) {
            cancel();
            return;
        }
        reset();
        activity.refresh();
    }

    private void cancel() { deleteId(pending); reset(); }

    private void reset() { pending = -1; page = -1; replace = -1; provider = null; }

    void delete(FlowStore.Tile tile) { if (FlowStore.WIDGET.equals(tile.type)) deleteId(tile.widgetId); }

    void deleteId(int id) {
        if (id > 0) try { host.deleteAppWidgetId(id); } catch (RuntimeException ignored) { }
    }
}
