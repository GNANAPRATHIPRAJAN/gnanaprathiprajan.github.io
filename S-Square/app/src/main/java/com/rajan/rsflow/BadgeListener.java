package com.rajan.rsflow;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.*;

public final class BadgeListener extends NotificationListenerService {
    private static BadgeListener service;
    private static boolean badges;
    private static boolean queryFailed;
    private static final Map<String, StatusBarNotification> callbacks = new HashMap<>();
    private static long lastRebind = -10000;

    static boolean accessGranted(Context context) {
        ComponentName listener = new ComponentName(context, BadgeListener.class);
        if (Build.VERSION.SDK_INT >= 27) {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            return manager != null && manager.isNotificationListenerAccessGranted(listener);
        }
        String value =
                Settings.Secure.getString(
                        context.getContentResolver(), "enabled_notification_listeners");
        if (value != null)
            for (String flat : value.split(":")) {
                if (listener.equals(ComponentName.unflattenFromString(flat))) return true;
            }
        return false;
    }

    static void reconnect(Context context, boolean manual) {
        long now = android.os.SystemClock.elapsedRealtime();
        if ((!manual && service != null) || now - lastRebind < 1500 || !accessGranted(context))
            return;
        lastRebind = now;
        try {
            NotificationListenerService.requestRebind(
                    new ComponentName(context, BadgeListener.class));
        } catch (RuntimeException ignored) {
        }
    }

    static boolean queryFailed() {
        return queryFailed;
    }

    private static final android.os.Handler main =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private static boolean pending;
    private static final Runnable refresh =
            () -> {
                pending = false;
                reload();
            };

    private static void schedule() {
        if (observer != null && !pending) {
            pending = true;
            main.postDelayed(refresh, 80);
        }
    }

    static boolean unlocked() {
        return service != null
                && !service.getSystemService(KeyguardManager.class).isKeyguardLocked();
    }

    private static Runnable observer;
    private static final Map<String, Integer> counts = new HashMap<>();

    static void observe(Runnable listener) {
        observer = listener;
        if (listener == null) {
            main.removeCallbacks(refresh);
            pending = false;
            counts.clear();
        }
    }

    static void enable(boolean value) {
        badges = value;
        reload();
    }

    static boolean isConnected() {
        return service != null;
    }

    static StatusBarNotification[] visible() {
        queryFailed = false;
        if (!unlocked()) return new StatusBarNotification[0];
        StatusBarNotification[] all = null;
        try {
            all = service.getActiveNotifications();
            if (all != null) {
                callbacks.clear();
                for (StatusBarNotification item : all) callbacks.put(item.getKey(), item);
            }
        } catch (RuntimeException ignored) {
        }
        if (all == null) {
            queryFailed = true;
            all = callbacks.values().toArray(new StatusBarNotification[0]);
        }
        Arrays.sort(all, (a, b) -> Long.compare(b.getPostTime(), a.getPostTime()));
        return all;
    }

    static int count(String key) {
        int slash = key.indexOf('/');
        return counts.getOrDefault(slash < 0 ? key : key.substring(0, slash), 0);
    }

    static void dismiss(StatusBarNotification n) {
        if (unlocked() && n.isClearable())
            try {
                service.cancelNotification(n.getKey());
            } catch (RuntimeException ignored) {
            }
    }

    static void reload() {
        counts.clear();
        if (badges && observer != null)
            for (StatusBarNotification n : visible()) {
                if (android.os.Process.myUserHandle().equals(n.getUser())
                        && !n.isOngoing()
                        && (n.getNotification().flags & Notification.FLAG_GROUP_SUMMARY) == 0)
                    counts.merge(n.getPackageName(), 1, Integer::sum);
            }
        if (observer != null) observer.run();
    }

    @Override
    public void onListenerConnected() {
        callbacks.clear();
        service = this;
        enable(new FlowStore(this).flag("badges", false));
    }

    @Override
    public void onListenerDisconnected() {
        if (service == this) {
            service = null;
            callbacks.clear();
            reload();
        }
    }

    @Override
    public void onDestroy() {
        onListenerDisconnected();
        super.onDestroy();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification n) {
        if (service != this) return;
        callbacks.put(n.getKey(), n);
        schedule();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification n) {
        if (service != this) return;
        callbacks.remove(n.getKey());
        schedule();
    }
}
