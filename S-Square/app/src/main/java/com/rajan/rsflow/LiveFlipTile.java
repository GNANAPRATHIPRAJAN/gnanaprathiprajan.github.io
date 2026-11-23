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
    private final int intervalMs, initialOffsetMs;
    private boolean firstTick = true;
    private boolean flipped;
    private final Runnable tick =
            () -> {
                firstTick = false;
                if (isShown() && !isFlippedOffscreen() && hasWindowFocus()) flip();
                schedule();
            };

    LiveFlipTile(MainActivity activity, FlowStore.Tile tile, int foreground, boolean compact) {
        super(activity);
        this.activity = activity;
        this.mode = tile.liveFlip;
        intervalMs = 1000 * FlowUi.clamp(activity.store.number("flipSeconds", 8), 3, 60);
        initialOffsetMs = "music".equals(tile.live) ? intervalMs / 3 : 0;
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
            postDelayed(tick, intervalMs + (firstTick ? initialOffsetMs : 0));
    }

    private ValueAnimator turn;
    private void flip() {
        if (turn != null) return;
        View surface = getParent() instanceof FrameLayout ? (View) getParent() : this;
        boolean target = !flipped;
        if (activity.store.number("motion", 1) == 0 || !ValueAnimator.areAnimatorsEnabled()) {
            showFace(target);
            return;
        }
        surface.setCameraDistance(activity.ui.dp(1400));
        turn = ValueAnimator.ofFloat(0, 1);
        turn.setDuration(330);
        turn.setInterpolator(new android.view.animation.PathInterpolator(.35f, 0, .22f, 1));
        turn.addUpdateListener(anim -> {
            if (activity.arranging || !hasWindowFocus()) {
                anim.cancel();
                return;
            }
            float position = (float) anim.getAnimatedValue();
            boolean second = position >= .5f;
            if (second) showFace(target);
            surface.setRotationX((second ? position - 1 : position) * 180);
        });
        turn.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                surface.setRotationX(0);
                turn = null;
            }
        });
        turn.start();
    }

    private void showFace(boolean back) {
        if (flipped == back) return;
        flipped = back;
        front.setVisibility(back ? GONE : VISIBLE);
        reverse.setVisibility(back ? VISIBLE : GONE);
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
        if (turn != null) turn.cancel();
        super.onDetachedFromWindow();
    }

    static boolean supported(FlowStore.Tile tile) {
        return FlowStore.LIVE.equals(tile.type)
                && ("photo".equals(tile.live) || "music".equals(tile.live));
    }
}
