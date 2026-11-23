package com.rajan.rsflow;

import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;

/** Two attached pages share a continuous horizontal offset, including during a cancelled swipe. */
final class StartPager extends FrameLayout {
    private final MainActivity activity;
    private final View home, drawer;
    private ValueAnimator settle;
    float position;

    StartPager(MainActivity activity, View home, View drawer, boolean showApps) {
        super(activity);
        this.activity = activity;
        this.home = home;
        this.drawer = drawer;
        addView(home, new LayoutParams(-1, -1));
        addView(drawer, new LayoutParams(-1, -1));
        place(showApps ? 1 : 0);
    }

    boolean motion() {
        return activity.store.number("motion", 1) != 0 && ValueAnimator.areAnimatorsEnabled();
    }

    static boolean destination(boolean apps, float dx, int threshold) {
        return apps ? dx < threshold : dx <= -threshold;
    }

    void place(float fraction) {
        position = Math.max(0, Math.min(1, fraction));
        home.setVisibility(position == 1 ? INVISIBLE : VISIBLE);
        drawer.setVisibility(position == 0 ? INVISIBLE : VISIBLE);
        home.setTranslationX(-getWidth() * position);
        drawer.setTranslationX(getWidth() * (1 - position));
        home.setImportantForAccessibility(
                position < .5f
                        ? IMPORTANT_FOR_ACCESSIBILITY_AUTO
                        : IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        drawer.setImportantForAccessibility(
                position >= .5f
                        ? IMPORTANT_FOR_ACCESSIBILITY_AUTO
                        : IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    }

    void settle(boolean apps) {
        stop();
        float end = apps ? 1 : 0;
        if (!motion() || position == end) {
            place(end);
            return;
        }
        settle = ValueAnimator.ofFloat(position, end);
        settle.setDuration(
                (long)
                        (105
                                + (activity.store.number("motion", 1) == 2 ? 205 : 145)
                                        * Math.abs(end - position)));
        settle.setInterpolator(new PathInterpolator(.2f, 0f, .05f, 1f));
        settle.addUpdateListener(a -> place((float) a.getAnimatedValue()));
        settle.start();
    }

    void stop() {
        if (settle != null) {
            settle.cancel();
            settle = null;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        place(position);
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }
}
