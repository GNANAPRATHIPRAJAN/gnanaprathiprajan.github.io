package com.rajan.rsflow;

import android.app.*;
import android.content.*;
import android.graphics.drawable.*;
import android.os.Build;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;

final class ControlCenter {
    private final MainActivity a;
    private final FlowUi ui;
    private final NoticeRows rows = new NoticeRows();
    private LinearLayout summary;

    ControlCenter(MainActivity a) {
        this.a = a;
        ui = a.ui;
    }

    private static final String[] NAMES = {
        "Internet",
        "Bluetooth",
        "Volume",
        "Display",
        "Battery",
        "Wallpaper",
        "Android settings",
        "Apps"
    };
    private static final String[] ICONS = {
        "wifi", "bluetooth", "music", "sun", "battery", "picture", "gear", "apps"
    };
    private static final String[] ACTIONS = {
        Settings.ACTION_WIFI_SETTINGS,
        Settings.ACTION_BLUETOOTH_SETTINGS,
        Settings.ACTION_SOUND_SETTINGS,
        Settings.ACTION_DISPLAY_SETTINGS,
        Settings.ACTION_BATTERY_SAVER_SETTINGS,
        "",
        Settings.ACTION_SETTINGS,
        ""
    };
    private LinearLayout controls;
    private ListView list;

    void build() {
        a.header("Action Center", "Home", a::goHome);
        list = new ListView(a);
        list.setDivider(null);
        list.setCacheColorHint(0);
        list.setClipToPadding(false);
        list.setPadding(ui.dp(18), ui.dp(7), ui.dp(18), ui.dp(18));
        LinearLayout body = ui.column();
        LinearLayout heading = ui.row();
        TextClock clock = new TextClock(a);
        clock.setTextColor(ui.dim);
        clock.setTextSize(13 * ui.textScale);
        clock.setTypeface(ui.font);
        clock.setFormat12Hour("MMM d  ·  h:mm a");
        clock.setFormat24Hour("MMM d  ·  HH:mm");
        clock.setSingleLine(true);
        heading.addView(clock, new LinearLayout.LayoutParams(0, -2, 1));
        heading.addView(ui.button("Edit shortcuts", false, this::editShortcuts));
        body.addView(heading);
        ui.gap(body, 9);
        controls = ui.column();
        body.addView(controls);
        drawShortcuts();
        ui.gap(body, 12);
        summary = ui.column();
        body.addView(summary);
        list.addHeaderView(body, null, false);
        list.setAdapter(rows);
        list.setItemsCanFocus(true);
        a.root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        BadgeListener.reconnect(a, false);
    }

    private java.util.ArrayList<Integer> shortcuts() {
        java.util.ArrayList<Integer> result = new java.util.ArrayList<>();
        for (char ch : a.store.option("centerShortcuts", "012345").toCharArray()) {
            int index = ch - '0';
            if (index >= 0 && index < NAMES.length && !result.contains(index)) result.add(index);
        }
        return result;
    }

    private void editShortcuts() {
        java.util.ArrayList<Integer> selection = shortcuts();
        boolean[] checks = new boolean[NAMES.length];
        for (int id : selection) checks[id] = true;
        AlertDialog.Builder options =
                a.dialog()
                        .setTitle("Action Center shortcuts")
                        .setMultiChoiceItems(
                                NAMES,
                                checks,
                                (d, i, on) -> {
                                    if (on) selection.add(i);
                                    else selection.remove(Integer.valueOf(i));
                                })
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton(
                                "Save",
                                (d, w) -> {
                                    StringBuilder order = new StringBuilder();
                                    for (int id : selection) order.append(id);
                                    a.store.set("centerShortcuts", order.toString());
                                    drawShortcuts();
                                })
                        .setNeutralButton(
                                "Reset",
                                (d, w) -> {
                                    a.store.set("centerShortcuts", "012345");
                                    drawShortcuts();
                                });
        options.show();
    }

    private void drawShortcuts() {
        controls.removeAllViews();
        java.util.List<Integer> order = shortcuts();
        int cols =
                a.getResources().getConfiguration().screenWidthDp >= 600 && ui.textScale <= 1.22f
                        ? 4
                        : 2;
        for (int start = 0; start < order.size(); start += cols) {
            LinearLayout row = ui.row();
            controls.addView(row);
            for (int j = 0; j < cols; j++) {
                if (j > 0) ui.gapHorizontal(row, 9);
                int pos = start + j;
                if (pos >= order.size()) {
                    row.addView(new View(a), new LinearLayout.LayoutParams(0, 1, 1));
                    continue;
                }
                int id = order.get(pos);
                LinearLayout button = ui.column();
                button.setOrientation(LinearLayout.HORIZONTAL);
                button.setGravity(Gravity.CENTER_VERTICAL);
                button.setPadding(ui.dp(12), ui.dp(9), ui.dp(6), ui.dp(9));
                button.setBackground(ui.surface(ui.panel, 16, 0));
                button.addView(
                        ui.symbol(ICONS[id], ui.accent, 24),
                        new LinearLayout.LayoutParams(ui.dp(24), ui.dp(24)));
                ui.gapHorizontal(button, 9);
                TextView label = ui.label(NAMES[id], 12, ui.text);
                label.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                label.setMaxLines(2);
                label.setEllipsize(TextUtils.TruncateAt.END);
                button.addView(label, new LinearLayout.LayoutParams(-1, -2));
                button.setContentDescription(NAMES[id]);
                button.setMinimumHeight(ui.dp(58));
                button.setFocusable(true);
                button.setOnClickListener(v -> openShortcut(id));
                row.addView(button, new LinearLayout.LayoutParams(0, -2, 1));
            }
            ui.gap(controls, 8);
        }
    }

    private void openShortcut(int index) {
        if (index == 5) {
            new Customize(a).wallpaper();
            return;
        }
        if (index == 7) {
            a.setScreen(MainActivity.Screen.APPS);
            return;
        }
        if (Build.VERSION.SDK_INT >= 29 && (index == 0 || index == 2)) {
            try {
                a.startActivity(
                        new Intent(
                                index == 0
                                        ? Settings.Panel.ACTION_INTERNET_CONNECTIVITY
                                        : Settings.Panel.ACTION_VOLUME));
                return;
            } catch (RuntimeException ignored) {
            }
        }
        a.startSafely(new Intent(ACTIONS[index]));
    }

    private void pin() {
        if (Build.VERSION.SDK_INT < 33) {
            a.toast("Edit Android Quick Settings and add S-Square Center");
            return;
        }
        StatusBarManager m = a.getSystemService(StatusBarManager.class);
        if (m != null)
            try {
                m.requestAddTileService(
                        new ComponentName(a, CenterShortcut.class),
                        "S-Square Center",
                        Icon.createWithResource(a, R.drawable.ic_metro_settings),
                        a.getMainExecutor(),
                        v -> {});
            } catch (RuntimeException e) {
                a.toast("Edit Android Quick Settings and add S-Square Center");
            }
    }

    private void notificationOptions() {
        a.dialog()
                .setTitle("Notification controls")
                .setItems(
                        new String[] {
                            "Reconnect notification feed",
                            "Android notification access",
                            "Pin to Quick Settings",
                            "Send a test notification",
                            "Android blocked access?"
                        },
                        (d, i) -> {
                            if (i == 0) {
                                BadgeListener.reconnect(a, true);
                                updateNotices();
                            }
                            if (i == 1) access();
                            if (i == 2) pin();
                            if (i == 3) a.testNotification();
                            if (i == 4) NotificationAccess.help(a);
                        })
                .show();
    }

    private void access() {
        NotificationAccess.open(a);
    }

    void clear() {
        if (summary != null) summary.removeAllViews();
        rows.items = new StatusBarNotification[0];
        rows.notifyDataSetChanged();
    }

    void updateNotices() {
        LinearLayout parent = summary;
        if (parent == null) return;
        parent.removeAllViews();
        rows.items = BadgeListener.visible();
        rows.notifyDataSetChanged();
        StatusBarNotification[] all = rows.items;
        LinearLayout head = ui.row();
        parent.addView(head);
        head.addView(
                ui.label("Notifications" + (all.length > 0 ? "  " + all.length : ""), 19, ui.text),
                new LinearLayout.LayoutParams(0, -2, 1));
        TextView options = ui.command("⋯", this::notificationOptions);
        options.setContentDescription("Notification controls");
        head.addView(options);
        if (!BadgeListener.isConnected()) {
            boolean granted = BadgeListener.accessGranted(a);
            parent.addView(
                    ui.label(
                            granted
                                    ? "Access granted. Waiting for Android to connect the feed."
                                    : "Allow S-Square to read the active notification feed.",
                            14,
                            ui.dim));
            ui.gap(parent, 8);
            parent.addView(
                    ui.button(
                            granted ? "Reconnect" : "Enable notification access",
                            true,
                            () -> {
                                if (granted) BadgeListener.reconnect(a, true);
                                else access();
                            }));
            ui.gap(parent, 8);
            if (granted)
                parent.addView(ui.button("Check notification access", false, this::access));
            else if (Build.VERSION.SDK_INT >= 33)
                parent.addView(
                        ui.button(
                                "Android blocked access?",
                                false,
                                () -> NotificationAccess.help(a)));
            return;
        }
        boolean hasClearable = false;
        for (StatusBarNotification n : all) hasClearable |= n.isClearable();
        if (hasClearable)
            head.addView(
                    ui.command(
                            "Clear",
                            () ->
                                    a.dialog()
                                            .setTitle("Dismiss clearable notifications?")
                                            .setNegativeButton("Cancel", null)
                                            .setPositiveButton(
                                                    "Dismiss",
                                                    (d, w) -> {
                                                        for (StatusBarNotification n : all)
                                                            BadgeListener.dismiss(n);
                                                    })
                                            .show()));
        ui.gap(parent, 8);
        if (all.length == 0) {
            String state =
                    !BadgeListener.unlocked()
                            ? "Unlock the phone to read notifications."
                            : BadgeListener.queryFailed()
                                    ? "Android’s feed did not respond. Try Reconnect in"
                                            + " Notification controls."
                                    : "Connected. Android sent no active notifications.";
            parent.addView(ui.label(state, 14, ui.dim));
            if (BadgeListener.unlocked() && !BadgeListener.queryFailed()) {
                ui.gap(parent, 8);
                parent.addView(ui.button("Send a test notification", false, a::testNotification));
            }
            return;
        }
    }

    private final class NoticeRows extends BaseAdapter {
        StatusBarNotification[] items = new StatusBarNotification[0];

        public int getCount() {
            return items.length;
        }

        public StatusBarNotification getItem(int i) {
            return items[i];
        }

        public long getItemId(int i) {
            return i;
        }

        public View getView(int i, View reused, ViewGroup parent) {
            LinearLayout row = reused == null ? ui.column() : (LinearLayout) reused;
            row.removeAllViews();
            addCard(row, items[i]);
            return row;
        }
    }

    private void addCard(LinearLayout parent, StatusBarNotification n) {
        Notification note = n.getNotification();
        String pkg = n.getPackageName(), name = pkg;
        try {
            name =
                    a.getPackageManager()
                            .getApplicationLabel(a.getPackageManager().getApplicationInfo(pkg, 0))
                            .toString();
        } catch (Exception ignored) {
        }
        SwipeNotice panel = new SwipeNotice(n);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(ui.dp(15), ui.dp(13), ui.dp(15), ui.dp(13));
        panel.setBackground(ui.surface(ui.panel, 19, 0));
        parent.addView(panel);
        ui.gap(parent, 10);
        LinearLayout row = ui.row();
        panel.addView(row);
        ImageView icon = new ImageView(a);
        try {
            icon.setImageDrawable(a.getPackageManager().getApplicationIcon(pkg));
        } catch (Exception unavailable) {
            try {
                icon.setImageDrawable(
                        note.getSmallIcon() == null
                                ? a.getDrawable(R.drawable.ic_metro_messages)
                                : note.getSmallIcon().loadDrawable(a));
                icon.setColorFilter(ui.text);
            } catch (RuntimeException ignored) {
                icon.setImageResource(R.drawable.ic_metro_messages);
            }
        }
        row.addView(icon, new LinearLayout.LayoutParams(ui.dp(29), ui.dp(29)));
        ui.gapHorizontal(row, 8);
        TextView appName = ui.label(name + (n.isOngoing() ? "  ·  Ongoing" : ""), 13, ui.dim);
        appName.setMaxLines(1);
        appName.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(appName, new LinearLayout.LayoutParams(0, -2, 1));
        ui.gapHorizontal(row, 6);
        row.addView(
                ui.label(
                        android.text.format.DateFormat.getTimeFormat(a)
                                .format(new java.util.Date(n.getPostTime())),
                        12,
                        ui.dim));
        ui.gap(panel, 6);
        try {
            if (note.extras != null) {
                line(panel, note.extras.getCharSequence(Notification.EXTRA_TITLE), 16, 2);
                CharSequence body = note.extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
                if (TextUtils.isEmpty(body)) {
                    CharSequence[] lines =
                            note.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
                    if (lines != null && lines.length != 0)
                        body =
                                TextUtils.join(
                                        "\n",
                                        java.util.Arrays.copyOf(lines, Math.min(lines.length, 5)));
                }
                line(
                        panel,
                        TextUtils.isEmpty(body)
                                ? note.extras.getCharSequence(Notification.EXTRA_TEXT)
                                : body,
                        14,
                        5);
            }
        } catch (RuntimeException invalid) {
            line(panel, "Preview unavailable", 14, 2);
        }
        if (note.contentIntent != null) {
            panel.setFocusable(true);
            panel.setOnClickListener(v -> send(note.contentIntent, null));
        }
        if (note.actions != null) {
            ActionButtons buttons = new ActionButtons();
            for (Notification.Action action : note.actions) {
                if (action == null
                        || action.actionIntent == null
                        || TextUtils.isEmpty(action.title)) continue;
                RemoteInput[] inputs = action.getRemoteInputs();
                if (inputs == null || inputs.length == 0)
                    buttons.addView(
                            actionButton(
                                    action.title.toString(),
                                    () -> send(action.actionIntent, null)));
                else
                    for (RemoteInput input : inputs) {
                        if (!input.getAllowFreeFormInput()
                                && (input.getChoices() == null || input.getChoices().length == 0))
                            continue;
                        buttons.addView(
                                actionButton(action.title.toString(), () -> reply(action, input)));
                        break;
                    }
            }
            if (buttons.getChildCount() > 0) {
                ui.gap(panel, 10);
                panel.addView(buttons);
            }
        }
    }

    private TextView actionButton(String title, Runnable run) {
        TextView button = ui.button(title, false, run);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(ui.dp(48));
        button.setPadding(ui.dp(14), ui.dp(7), ui.dp(14), ui.dp(7));
        button.setBackground(ui.surface(FlowUi.blend(ui.panel, ui.accent, .10f), 22, 0));
        return button;
    }

    private final class ActionButtons extends ViewGroup {
        ActionButtons() {
            super(a);
        }

        @Override
        protected void onMeasure(int w, int h) {
            int width = MeasureSpec.getSize(w), x = 0, y = 0, line = 0, gap = ui.dp(8);
            for (int i = 0; i < getChildCount(); i++) {
                View v = getChildAt(i);
                v.measure(
                        MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
                        MeasureSpec.UNSPECIFIED);
                if (x > 0 && x + v.getMeasuredWidth() > width) {
                    x = 0;
                    y += line + gap;
                    line = 0;
                }
                x += v.getMeasuredWidth() + gap;
                line = Math.max(line, v.getMeasuredHeight());
            }
            setMeasuredDimension(width, y + line);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int width = r - l, x = 0, y = 0, line = 0, gap = ui.dp(8);
            for (int i = 0; i < getChildCount(); i++) {
                View v = getChildAt(i);
                int w = v.getMeasuredWidth(), h = v.getMeasuredHeight();
                if (x > 0 && x + w > width) {
                    x = 0;
                    y += line + gap;
                    line = 0;
                }
                int left = getLayoutDirection() == LAYOUT_DIRECTION_RTL ? width - x - w : x;
                v.layout(left, y, left + w, y + h);
                x += w + gap;
                line = Math.max(line, h);
            }
        }
    }

    private final class SwipeNotice extends LinearLayout {
        final StatusBarNotification notice;
        float downX, downY;
        boolean sliding, vertical;

        SwipeNotice(StatusBarNotification notice) {
            super(a);
            this.notice = notice;
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent e) {
            if (!notice.isClearable()) return super.dispatchTouchEvent(e);
            float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN -> {
                    downX = e.getRawX();
                    downY = e.getRawY();
                    sliding = vertical = false;
                }
                case MotionEvent.ACTION_MOVE -> {
                    if (!sliding && !vertical) {
                        int slop = ViewConfiguration.get(a).getScaledTouchSlop();
                        if (Math.abs(dy) > slop && Math.abs(dy) > Math.abs(dx)) vertical = true;
                        if (Math.abs(dx) > slop && Math.abs(dx) > 1.5f * Math.abs(dy)) {
                            MotionEvent cancel = MotionEvent.obtain(e);
                            cancel.setAction(MotionEvent.ACTION_CANCEL);
                            super.dispatchTouchEvent(cancel);
                            cancel.recycle();
                            getParent().requestDisallowInterceptTouchEvent(true);
                            sliding = true;
                        }
                    }
                    if (sliding) {
                        setTranslationX(dx);
                        setAlpha(Math.max(.38f, 1 - Math.abs(dx) / Math.max(1, getWidth())));
                        return true;
                    }
                }
                case MotionEvent.ACTION_UP,
                        MotionEvent.ACTION_CANCEL,
                        MotionEvent.ACTION_POINTER_DOWN -> {
                    if (sliding) {
                        sliding = false;
                        getParent().requestDisallowInterceptTouchEvent(false);
                        boolean dismiss =
                                e.getActionMasked() == MotionEvent.ACTION_UP
                                        && Math.abs(dx) >= Math.max(ui.dp(48), getWidth() * .28f);
                        if (dismiss) BadgeListener.dismiss(notice);
                        animate().translationX(0).alpha(1).setDuration(140).start();
                        return true;
                    }
                }
            }
            boolean handled = super.dispatchTouchEvent(e);
            return handled || e.getActionMasked() == MotionEvent.ACTION_DOWN;
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(
                android.view.accessibility.AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            if (notice.isClearable()) {
                info.setDismissable(true);
                info.addAction(
                        android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
                                .ACTION_DISMISS);
            }
        }

        @Override
        public boolean performAccessibilityAction(int action, android.os.Bundle args) {
            if (action == android.view.accessibility.AccessibilityNodeInfo.ACTION_DISMISS
                    && notice.isClearable()) {
                BadgeListener.dismiss(notice);
                return true;
            }
            return super.performAccessibilityAction(action, args);
        }
    }

    private void line(LinearLayout p, CharSequence s, int size, int lines) {
        if (TextUtils.isEmpty(s)) return;
        TextView t =
                ui.label(s.subSequence(0, Math.min(s.length(), 4000)).toString(), size, ui.text);
        t.setMaxLines(lines);
        t.setEllipsize(TextUtils.TruncateAt.END);
        p.addView(t);
    }

    private void reply(Notification.Action action, RemoteInput input) {
        if (!BadgeListener.unlocked()) return;
        if (!input.getAllowFreeFormInput()) {
            CharSequence[] choices = input.getChoices();
            secure(
                    a.dialog()
                            .setTitle(action.title)
                            .setItems(
                                    choices,
                                    (dialog, index) -> sendReply(action, input, choices[index]))
                            .create());
            return;
        }
        EditText field =
                a.input(
                        input.getLabel() == null ? "Message" : input.getLabel().toString(),
                        "",
                        4000,
                        true);
        LinearLayout box = ui.column();
        box.setPadding(ui.dp(18), ui.dp(8), ui.dp(18), 0);
        box.addView(field);
        secure(
                a.dialog()
                        .setTitle(action.title)
                        .setView(box)
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton(
                                "Send",
                                (dialog, button) -> {
                                    String text = field.getText().toString().trim();
                                    if (!text.isEmpty()) sendReply(action, input, text);
                                })
                        .create());
    }

    private void secure(AlertDialog dialog) {
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        dialog.show();
    }

    private void sendReply(Notification.Action action, RemoteInput input, CharSequence text) {
        android.os.Bundle result = new android.os.Bundle();
        result.putCharSequence(input.getResultKey(), text);
        Intent reply = new Intent();
        RemoteInput.addResultsToIntent(new RemoteInput[] {input}, reply, result);
        send(action.actionIntent, reply);
    }

    private void send(PendingIntent intent, Intent data) {
        if (!BadgeListener.unlocked()) return;
        try {
            intent.send(a, 0, data);
        } catch (PendingIntent.CanceledException | SecurityException ex) {
            a.toast("This notification action is unavailable");
        }
    }
}
