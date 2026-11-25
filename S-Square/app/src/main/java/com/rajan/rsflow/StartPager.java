package com.rajan.rsflow;

import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;

/** Two attached pages share a continuous horizontal offset, including during a cancelled swipe. */
final class StartPager extends FrameLayout {
    private final MainActivity activity;
    private final View[] pages;
    private ValueAnimator settle;
    float position;

    StartPager(MainActivity activity, View home, View drawer, View third, int page) {
        super(activity);
        this.activity = activity;
        pages = third == null ? new View[] {home, drawer} : new View[] {home, drawer, third};
        for (View view : pages) addView(view, new LayoutParams(-1, -1));
        place(page);
    }

    boolean motion() {
        return activity.store.number("motion", 1) != 0 && ValueAnimator.areAnimatorsEnabled();
    }

    void place(float fraction) {
        position = Math.max(0, Math.min(pages.length - 1, fraction));
        for (int i = 0; i < pages.length; i++) {
            float distance = i - position;
            View view = pages[i];
            view.setVisibility(Math.abs(distance) >= 1 ? INVISIBLE : VISIBLE);
            view.setTranslationX(getWidth() * distance);
            view.setImportantForAccessibility(i == Math.round(position)
                    ? IMPORTANT_FOR_ACCESSIBILITY_AUTO : IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }
    }

    void settle(int page) {
        stop();
        float end = FlowUi.clamp(page, 0, pages.length - 1);
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
