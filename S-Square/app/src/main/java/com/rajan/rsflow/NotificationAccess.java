package com.rajan.rsflow;

import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

final class NotificationAccess {
    private NotificationAccess() {}

    static void open(MainActivity activity) {
        if (Build.VERSION.SDK_INT >= 30) {
            ComponentName listener = new ComponentName(activity, BadgeListener.class);
            Intent details =
                    new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                            .putExtra(
                                    Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                                    listener.flattenToString());
            try {
                activity.startActivity(details);
                return;
            } catch (RuntimeException unavailable) {
                // Some Android builds provide only the list of notification listeners.
            }
        }
        activity.startSafely(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
    }

    static void help(MainActivity activity) {
        activity.dialog()
                .setTitle("Notification access")
                .setMessage(
                        "If Android reports a restricted setting for this installation, open App"
                            + " info. Where available, open the ⋮ menu and choose Allow restricted"
                            + " settings for S-Square. Then return and enable notification access."
                            + " Android controls both steps.")
                .setPositiveButton(
                        "Open app info",
                        (dialog, which) ->
                                activity.startSafely(
                                        new Intent(
                                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                Uri.fromParts(
                                                        "package",
                                                        activity.getPackageName(),
                                                        null))))
                .setNeutralButton("Notification access", (dialog, which) -> open(activity))
                .setNegativeButton("Close", null)
                .show();
    }
}
