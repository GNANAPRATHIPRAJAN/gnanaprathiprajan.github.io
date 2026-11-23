package com.rajan.rsflow;

import static com.rajan.rsflow.MainActivity.FILL;
import static com.rajan.rsflow.MainActivity.WRAP;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.os.BatteryManager;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextClock;
import android.widget.TextView;

final class LiveTile {
    static final String[] IDS = {"clock", "calendar", "battery", "photo", "people", "music"};
    static final String[] LABELS = {
        "Clock", "Calendar date", "Battery", "Photo gallery", "People", "Music"
    };

    static String name(String id) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return LABELS[i];
        return "Live tile";
    }

    static View content(
            MainActivity activity, FlowStore.Tile tile, int foreground, boolean compact) {
        FlowUi ui = activity.ui;
        FrameLayout tileView = new FrameLayout(activity);
        int inset = ui.dp(compact ? 3 : 8);
        TextView foot = ui.label(activity.tileName(tile), 13, foreground);
        if ("photo".equals(tile.live)) {
            View album = activity.media.gallery();
            if (album != null) tileView.addView(album, new FrameLayout.LayoutParams(FILL, FILL));
            else {
                ImageView image = new ImageView(activity);
                Bitmap photo = activity.files.tilePhoto();
                if (photo != null) image.setImageBitmap(photo);
                else image.setImageResource(R.drawable.flow_desktop_gallery);
                image.setScaleType(
                        photo != null
                                ? ImageView.ScaleType.CENTER_CROP
                                : ImageView.ScaleType.FIT_CENTER);
                int side = photo != null ? FILL : ui.dp(40);
                tileView.addView(image, new FrameLayout.LayoutParams(side, side, Gravity.CENTER));
            }
            foot.setText(tile.label.isEmpty() ? "Photos" : tile.label);
            foot.setTextColor(android.graphics.Color.WHITE);
            foot.setBackground(
                    new android.graphics.drawable.GradientDrawable(
                            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                            new int[] {0x00000000, 0xb3000000}));
        } else if ("people".equals(tile.live) || "music".equals(tile.live)) {
            ImageView icon = new ImageView(activity);
            icon.setImageResource(
                    "people".equals(tile.live)
                            ? R.drawable.flow_desktop_contacts
                            : R.drawable.flow_desktop_music);
            int size = ui.dp(compact ? 24 : 42);
            FrameLayout.LayoutParams artwork =
                    new FrameLayout.LayoutParams(size, size, Gravity.CENTER);
            artwork.bottomMargin = compact ? 0 : ui.dp(16);
            tileView.addView(icon, artwork);
        } else {
            boolean date = "calendar".equals(tile.live);
            TextView value;
            if ("battery".equals(tile.live)) value = new BatteryLevel(activity);
            else {
                TextClock clock = new TextClock(activity);
                int format = activity.store.number("clockFormat", 0);
                clock.setFormat12Hour(date ? "d" : (format == 2 ? "HH:mm" : "h:mm"));
                clock.setFormat24Hour(date ? "d" : (format == 1 ? "h:mm" : "HH:mm"));
                value = clock;
            }
            value.setTextColor(foreground);
            value.setTypeface(ui.font);
            FlowUi.weight(value, 300);
            value.setLetterSpacing(-.045f);
            value.setMaxLines(1);
            value.setIncludeFontPadding(false);
            boolean clockTile = "clock".equals(tile.live);
            int align = activity.store.number("clockAlign", 0);
            value.setGravity(
                    clockTile
                            ? (align == 1
                                    ? Gravity.CENTER
                                    : Gravity.CENTER_VERTICAL
                                            | (align == 2 ? Gravity.END : Gravity.START))
                            : Gravity.CENTER);
            int style = activity.store.number("clockStyle", 0);
            if (clockTile) FlowUi.weight(value, style == 2 ? 230 : 400);
            value.setAutoSizeTextTypeUniformWithConfiguration(
                    10,
                    Math.round((compact ? 25 : (clockTile && style == 1 ? 38 : 54)) * ui.textScale),
                    1,
                    android.util.TypedValue.COMPLEX_UNIT_SP);
            FrameLayout.LayoutParams face = new FrameLayout.LayoutParams(FILL, FILL);
            face.setMargins(inset, 0, inset, compact ? 0 : ui.dp(20));
            tileView.addView(value, face);
            if (!compact && tile.label.isEmpty() && (date || "clock".equals(tile.live))) {
                TextClock calendar = new TextClock(activity);
                String format = date ? "MMMM" : "EEEE, MMM d";
                calendar.setFormat12Hour(format);
                calendar.setFormat24Hour(format);
                calendar.setTextColor(foreground);
                calendar.setTextSize(13 * ui.textScale);
                calendar.setTypeface(ui.font);
                calendar.setIncludeFontPadding(false);
                foot = calendar;
            }
        }
        if ("music".equals(tile.live) && tile.label.isEmpty()) foot.setText("Flow Music");
        if (!compact
                && (!"clock".equals(tile.live)
                        || activity.store.flag("showDate", true)
                        || !tile.label.isEmpty())) {
            foot.setSingleLine(true);
            foot.setGravity(Gravity.CENTER);
            foot.setEllipsize(android.text.TextUtils.TruncateAt.END);
            foot.setPadding(inset, ui.dp(3), inset, ui.dp(5));
            tileView.addView(foot, new FrameLayout.LayoutParams(FILL, WRAP, Gravity.BOTTOM));
        }
        return tileView;
    }

    private static final class BatteryLevel extends TextView {
        private boolean registered;
        private final BroadcastReceiver changes =
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                        if (level >= 0 && scale > 0)
                            setText(Math.round(100f * level / scale) + "%");
                    }
                };

        BatteryLevel(Context context) {
            super(context);
            setText("—");
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent initial =
                    Build.VERSION.SDK_INT >= 33
                            ? getContext()
                                    .registerReceiver(
                                            changes, filter, Context.RECEIVER_NOT_EXPORTED)
                            : getContext().registerReceiver(changes, filter);
            registered = true;
            if (initial != null) changes.onReceive(getContext(), initial);
        }

        @Override
        protected void onDetachedFromWindow() {
            if (registered) getContext().unregisterReceiver(changes);
            registered = false;
            super.onDetachedFromWindow();
        }
    }
}
