package com.rajan.rsflow;

import android.animation.ValueAnimator;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

final class LiveFlipTile extends FrameLayout {
    private final MainActivity activity;
    private final View front, reverse;
    private final int mode;
    private boolean flipped;
    private final Runnable tick =
            () -> {
                if (isShown() && !isFlippedOffscreen()) flip();
                schedule();
            };

    LiveFlipTile(MainActivity activity, FlowStore.Tile tile, int foreground, boolean compact) {
        super(activity);
        this.activity = activity;
        this.mode = tile.liveFlip;
        front = LiveTile.content(activity, tile, foreground, compact);
        addView(front, new LayoutParams(-1, -1));
        FlowUi ui = activity.ui;
        LinearLayout details = ui.column();
        details.setGravity(Gravity.CENTER);
        details.setPadding(ui.dp(5), ui.dp(4), ui.dp(5), ui.dp(4));
        boolean photo = "photo".equals(tile.live);
        ImageView icon = new ImageView(activity);
        icon.setImageResource(
                photo ? R.drawable.flow_desktop_gallery : R.drawable.flow_desktop_music);
        details.addView(
                icon,
                new LinearLayout.LayoutParams(ui.dp(compact ? 20 : 32), ui.dp(compact ? 20 : 32)));
        if (!compact) {
            ui.gap(details, 4);
            TextView caption =
                    ui.label(photo ? "Open gallery" : activity.media.musicName(), 12, foreground);
            caption.setGravity(Gravity.CENTER);
            caption.setMaxLines(2);
            caption.setEllipsize(android.text.TextUtils.TruncateAt.END);
            details.addView(caption);
        }
        reverse = details;
        reverse.setVisibility(View.GONE);
        addView(reverse, new LayoutParams(-1, -1));
        if (mode == 1) {
            TextView turn =
                    ui.command(
                            "↻",
                            () -> {
                                if (!activity.arranging) flip();
                            });
            turn.setContentDescription("Flip " + activity.tileName(tile));
            turn.setTextColor(foreground);
            addView(turn, new LayoutParams(ui.dp(42), ui.dp(40), Gravity.TOP | Gravity.END));
        }
    }

    private boolean isFlippedOffscreen() {
        return activity.arranging
                || getWindowVisibility() != VISIBLE
                || !getLocalVisibleRect(new android.graphics.Rect());
    }

    private void schedule() {
        removeCallbacks(tick);
        if (mode == 2 && isAttachedToWindow() && getWindowVisibility() == VISIBLE)
            postDelayed(tick, 7000);
    }

    private void flip() {
        View current = flipped ? reverse : front;
        if (current.getRotationY() != 0) return;
        View next = flipped ? front : reverse;
        flipped = !flipped;
        boolean motion =
                activity.store.number("motion", 1) != 0 && ValueAnimator.areAnimatorsEnabled();
        if (!motion) {
            current.setVisibility(View.GONE);
            next.setVisibility(View.VISIBLE);
            return;
        }
        current.setCameraDistance(activity.ui.dp(1200));
        next.setCameraDistance(activity.ui.dp(1200));
        current.animate()
                .rotationY(88)
                .setDuration(125)
                .withEndAction(
                        () -> {
                            current.setVisibility(View.GONE);
                            current.setRotationY(0);
                            next.setRotationY(-88);
                            next.setVisibility(View.VISIBLE);
                            next.animate().rotationY(0).setDuration(145).start();
                        })
                .start();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        schedule();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (tick != null) schedule();
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(tick);
        front.animate().cancel();
        reverse.animate().cancel();
        super.onDetachedFromWindow();
    }

    static boolean supported(FlowStore.Tile tile) {
        return FlowStore.LIVE.equals(tile.type)
                && ("photo".equals(tile.live) || "music".equals(tile.live));
    }
}
