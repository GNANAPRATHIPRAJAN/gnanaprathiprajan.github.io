package com.rajan.rsflow;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

final class RotatingApps extends FrameLayout {
    private final MainActivity a;
    private final FlowStore.Tile tile;
    private final FrameLayout stage;
    private final Rect visible = new Rect();
    private final Runnable tick = this::tick;
    private ValueAnimator animation;
    private String current;
    private long nextTurn;
    private boolean touching, horizontal;
    private float startX, startY;

    RotatingApps(MainActivity a, FlowStore.Tile tile) {
        super(a);
        this.a = a;
        this.tile = tile;
        stage = new FrameLayout(a);
        addView(stage, new LayoutParams(-1, -1));
        setClipToOutline(true);
        setBackground(a.ui.rounded(a.ui.base, a.store.number("corner", 4), 0));
        current =
                tile.items.contains(tile.face)
                        ? tile.face
                        : tile.items.isEmpty() ? "" : tile.items.get(0);
        drawFront();
        if (!a.store.flag("lockTiles", false)) {
            TextView plus = a.ui.command("+", () -> new TileEditor(a).addFace(tile));
            plus.setTextSize(21);
            plus.setPadding(0, 0, 0, 0);
            plus.setContentDescription("Add app to rotating tile");
            plus.setBackground(a.ui.surface(FlowUi.blend(a.ui.panel, a.ui.accent, .22f), 18, 0));
            LayoutParams lp =
                    new LayoutParams(a.ui.dp(34), a.ui.dp(34), Gravity.TOP | Gravity.START);
            addView(plus, lp);
        }
        setFocusable(true);
        setOnClickListener(
                v -> {
                    if (a.arranging) ((TileBoard) getParent()).select(this);
                    else if (!current.isEmpty()) a.launch(current);
                    else if (!a.store.flag("lockTiles", false)) new TileEditor(a).addFace(tile);
                });
    }

    private LinearLayout face(String key) {
        LinearLayout face = a.ui.column();
        int color =
                tile.color != 0
                        ? tile.color
                        : key.isEmpty() ? a.ui.accent : a.appStyle.color(a, key);
        face.setBackgroundColor(color);
        face.setGravity(Gravity.CENTER);
        face.setCameraDistance(6000 * getResources().getDisplayMetrics().density);
        if (!key.isEmpty()) {
            face.addView(a.appIcon(key, 38, color));
            TextView name = a.ui.label(a.appName(key), 12, FlowUi.readable(color));
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            name.setGravity(Gravity.CENTER);
            face.addView(name);
        }
        return face;
    }

    private void drawFront() {
        stage.removeAllViews();
        stage.addView(face(current), new LayoutParams(-1, -1));
        setContentDescription(
                current.isEmpty() ? "Rotating apps" : "Rotating apps, " + a.appName(current));
    }

    private void tick() {
        if (isShown()
                && hasWindowFocus()
                && !touching
                && !a.arranging
                && getGlobalVisibleRect(visible)
                && tile.liveFlip != 0
                && tile.items.size() > 1) turn(1, false);
        else {
            nextTurn = 0;
            schedule();
        }
    }

    private void schedule() {
        removeCallbacks(tick);
        if (isAttachedToWindow() && isShown() && tile.items.size() > 1 && tile.liveFlip != 0)
            postDelayed(
                    tick,
                    nextTurn == 0
                            ? 2000
                            : Math.max(1, nextTurn - android.os.SystemClock.uptimeMillis()));
    }

    void turn(int step, boolean manual) {
        if (animation != null || tile.items.size() < 2) return;
        nextTurn = android.os.SystemClock.uptimeMillis() + 2000;
        String next =
                tile.items.get(
                        Math.floorMod(tile.items.indexOf(current) + step, tile.items.size()));
        if (manual) {
            tile.face = next;
            a.store.save();
        }
        removeCallbacks(tick);
        if (a.store.number("motion", 1) == 0 || !ValueAnimator.areAnimatorsEnabled()) {
            current = next;
            drawFront();
            schedule();
            return;
        }
        View outgoing = stage.getChildAt(0), incoming = face(next);
        stage.addView(incoming, new LayoutParams(-1, -1));
        int width = getWidth();
        outgoing.setPivotX(step > 0 ? width : 0);
        incoming.setPivotX(step > 0 ? 0 : width);
        animation = ValueAnimator.ofFloat(0, 1);
        animation.setDuration(350);
        animation.setInterpolator(new android.view.animation.PathInterpolator(.3f, 0, .15f, 1));
        animation.addUpdateListener(
                anim -> {
                    float p = (float) anim.getAnimatedValue();
                    outgoing.setTranslationX(-step * width * p);
                    incoming.setTranslationX(step * width * (1 - p));
                    outgoing.setRotationY(-step * 90 * p);
                    incoming.setRotationY(step * 90 * (1 - p));
                });
        animation.addListener(
                new AnimatorListenerAdapter() {
                    public void onAnimationEnd(Animator anim) {
                        animation = null;
                        current = next;
                        drawFront();
                        schedule();
                    }
                });
        animation.start();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            touching = true;
            horizontal = false;
            startX = e.getX();
            startY = e.getY();
            removeCallbacks(tick);
            if (animation != null) animation.end();
        }
        if (action == MotionEvent.ACTION_MOVE && !a.arranging && !horizontal) {
            float dx = e.getX() - startX;
            if (Math.abs(dx) > ViewConfiguration.get(a).getScaledTouchSlop()
                    && Math.abs(dx) > 1.5f * Math.abs(e.getY() - startY)) {
                MotionEvent cancel = MotionEvent.obtain(e);
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                super.dispatchTouchEvent(cancel);
                cancel.recycle();
                horizontal = true;
                getParent().requestDisallowInterceptTouchEvent(true);
            }
        }
        boolean handled = horizontal || super.dispatchTouchEvent(e);
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (horizontal
                    && action == MotionEvent.ACTION_UP
                    && Math.abs(e.getX() - startX) > a.ui.dp(20))
                turn(e.getX() < startX ? 1 : -1, true);
            touching = horizontal = false;
            nextTurn = android.os.SystemClock.uptimeMillis() + 2000;
            schedule();
        }
        return handled;
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent e) {
        if (!a.arranging
                && e.getActionMasked() == MotionEvent.ACTION_SCROLL
                && tile.items.size() > 1) {
            float d = e.getAxisValue(MotionEvent.AXIS_VSCROLL);
            if (d == 0) d = e.getAxisValue(MotionEvent.AXIS_HSCROLL);
            if (d != 0) {
                turn(d > 0 ? -1 : 1, true);
                return true;
            }
        }
        return super.onGenericMotionEvent(e);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        schedule();
    }

    @Override
    protected void onVisibilityChanged(View v, int visibility) {
        super.onVisibilityChanged(v, visibility);
        if (tile != null) schedule();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animation != null) animation.end();
        removeCallbacks(tick);
    }
}
