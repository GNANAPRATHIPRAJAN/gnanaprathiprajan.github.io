package com.rajan.rsflow;

import android.app.PendingIntent;
import android.content.Intent;
import android.service.quicksettings.TileService;

public final class CenterShortcut extends TileService {
    private static final String OPEN = "com.rajan.rsflow.studio.CENTER";

    private static final String TICKET = "center_ticket";
    private static String pendingTicket;
    private static long expires;

    static boolean accepts(Intent intent) {
        if (intent == null
                || !OPEN.equals(intent.getAction())
                || pendingTicket == null
                || android.os.SystemClock.elapsedRealtime() > expires) return false;
        try {
            if (!pendingTicket.equals(intent.getStringExtra(TICKET))) return false;
        } catch (RuntimeException invalid) {
            return false;
        }
        pendingTicket = null;
        intent.removeExtra(TICKET);
        return true;
    }

    @Override
    public void onClick() {
        super.onClick();
        Runnable action =
                () -> {
                    pendingTicket = java.util.UUID.randomUUID().toString();
                    expires = android.os.SystemClock.elapsedRealtime() + 30_000L;
                    Intent i =
                            new Intent(this, MainActivity.class)
                                    .setAction(OPEN)
                                    .putExtra(TICKET, pendingTicket)
                                    .addFlags(
                                            Intent.FLAG_ACTIVITY_NEW_TASK
                                                    | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    if (android.os.Build.VERSION.SDK_INT >= 34)
                        startActivityAndCollapse(
                                PendingIntent.getActivity(
                                        this,
                                        71,
                                        i,
                                        PendingIntent.FLAG_IMMUTABLE
                                                | PendingIntent.FLAG_CANCEL_CURRENT
                                                | PendingIntent.FLAG_ONE_SHOT));
                    else startActivityAndCollapse(i);
                };
        if (isLocked()) unlockAndRun(action);
        else action.run();
    }
}
