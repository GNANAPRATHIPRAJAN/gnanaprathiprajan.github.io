package com.rajan.rsflow;

import android.app.Dialog;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

/** Keeps a dismissed menu reachable while its child picker is open. */
final class DialogTrail {
    private final MainActivity activity;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Dialog visible, justClosed;
    private MainActivity.Screen visibleScreen;
    private Dialog externalParent;

    DialogTrail(MainActivity activity) {
        this.activity = activity;
    }

    void rememberPickerOrigin() {
        externalParent = current();
    }

    void finishPicker(boolean cancelled) {
        Dialog parent = externalParent;
        externalParent = null;
        if (cancelled) restore(parent);
    }

    private Dialog current() {
        return activity.screen == visibleScreen ? (visible != null ? visible : justClosed) : null;
    }

    private void restore(Dialog parent) {
        if (parent != null && !activity.isDestroyed() && !activity.isFinishing() && !parent.isShowing())
            parent.show();
    }

    void register(Dialog dialog) {
        Dialog parent = current();
        MainActivity.Screen origin = activity.screen;
        dialog.setOnCancelListener(ignored -> {
            if (activity.screen == origin) restore(parent);
        });
        dialog.getWindow().getDecorView().addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            public void onViewAttachedToWindow(View view) {
                justClosed = null;
                visible = dialog;
                visibleScreen = origin;
            }
            public void onViewDetachedFromWindow(View view) {
                if (visible != dialog) return;
                visible = parent != null && parent.isShowing() ? parent : null;
                justClosed = dialog;
                main.post(() -> { if (justClosed == dialog) justClosed = null; });
            }
        });
    }
}
