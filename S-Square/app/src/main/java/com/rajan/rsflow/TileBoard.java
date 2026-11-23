package com.rajan.rsflow;

import android.animation.ValueAnimator;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;

import java.util.ArrayList;
import java.util.List;

final class TileBoard extends ViewGroup {
    private final MainActivity activity;
    private final List<TileLayout.Cell> cells;
    private final List<TileLayout.Span> spans;
    private final int columns, gap;
    private int rowHeight;
    private View held, armed;
    private List<TileLayout.Cell> before;
    private int sourceIndex, nextX, nextY, lastDropRow;
    private float pickupX, pickupY;
    private float downX, downY, lastX, lastY;

    private boolean dragging;
    private final int[] location = new int[2];
    private final java.util.Map<View, ImageButton> edits = new java.util.LinkedHashMap<>();
    private final Runnable edgeScroll =
            new Runnable() {
                public void run() {
                    if (!dragging || held == null) return;
                    android.widget.ScrollView scroll = activity.contentScroll;
                    scroll.getLocationOnScreen(location);
                    float relative = lastY - location[1];
                    int zone = activity.ui.dp(48);
                    float edge =
                            relative < zone
                                    ? relative - zone
                                    : Math.max(0, relative - scroll.getHeight() + zone);
                    int step =
                            Math.round(Math.max(-1, Math.min(1, edge / zone)) * activity.ui.dp(12));
                    if (step != 0) {
                        scroll.scrollBy(0, step);
                        position();
                    }
                    postOnAnimation(this);
                }
            };
    Runnable onEditing;

    TileBoard(
            MainActivity activity,
            int columns,
            int gap,
            int rowHeight,
            List<TileLayout.Span> spans) {
        super(activity);
        this.activity = activity;
        this.columns = columns;
        this.spans = spans;
        setClipChildren(false);
        this.gap = gap;
        this.rowHeight = rowHeight;
        cells = TileLayout.place(columns, spans, activity.store.flag("denseTiles", true));
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        if (activity.store.flag("squareTiles", !activity.store.options.has("height"))) {
            rowHeight = Math.max(1, edge(width, 1) - gap);
        }
        int totalRows = 0;
        for (int index = 0; index < getChildCount(); index++) {
            TileLayout.Cell cell = cells.get(index);
            int cellWidth =
                    edge(width, cell.column() + cell.width()) - edge(width, cell.column()) - gap;
            int height = cell.height() * (rowHeight + gap) - gap;
            getChildAt(index)
                    .measure(
                            MeasureSpec.makeMeasureSpec(
                                    Math.max(0, cellWidth), MeasureSpec.EXACTLY),
                            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
            totalRows = Math.max(totalRows, cell.row() + cell.height());
        }
        setMeasuredDimension(width, Math.max(0, totalRows * (rowHeight + gap) - gap));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        for (int index = 0; index < getChildCount(); index++) {
            View child = getChildAt(index);
            TileLayout.Cell cell = cells.get(index);
            int x = edge(getWidth(), cell.column());
            if (rtl) x = getWidth() - x - child.getMeasuredWidth();
            int y = cell.row() * (rowHeight + gap);
            child.layout(x, y, x + child.getMeasuredWidth(), y + child.getMeasuredHeight());
        }
    }

    private int edge(int width, int column) {
        return (width + gap) * column / columns;
    }

    private boolean motion() {
        return activity.store.number("motion", 1) != 0 && ValueAnimator.areAnimatorsEnabled();
    }

    void enableDragging(View view, FlowStore.Tile tile) {
        ImageButton edit = new ImageButton(activity);
        edit.setImageResource(R.drawable.edit_pencil);
        edit.setColorFilter(activity.ui.ink);
        edit.setContentDescription("Edit " + activity.tileName(tile));
        edit.setBackground(activity.ui.surface(activity.ui.accent, 18, 0));
        int pad = activity.ui.dp(7);
        edit.setPadding(pad, pad, pad, pad);
        int diameter = activity.ui.dp(31);
        FrameLayout.LayoutParams layout =
                new FrameLayout.LayoutParams(diameter, diameter, Gravity.TOP | Gravity.END);
        layout.setMargins(0, activity.ui.dp(2), activity.ui.dp(2), 0);
        ((FrameLayout) view).addView(edit, layout);
        edit.setVisibility(activity.arranging && activity.selectedTile == tile ? VISIBLE : GONE);
        edit.setOnClickListener(v -> new TileEditor(activity).edit(tile));
        edits.put(view, edit);
        view.setOnLongClickListener(
                source -> {
                    lift(source);
                    return true;
                });
    }

    void restore(List<FlowStore.Tile> tiles) {
        ArrayList<TileLayout.Cell> anchors = new ArrayList<>();
        boolean anchored = false;
        for (int i = 0; i < tiles.size(); i++) {
            FlowStore.Tile t = tiles.get(i);
            TileLayout.Span span = spans.get(i);
            boolean valid =
                    t.gridColumns == columns
                            && t.gridX >= 0
                            && t.gridX + span.columns() <= columns
                            && t.gridY >= 0;
            anchors.add(
                    valid
                            ? new TileLayout.Cell(t.gridX, t.gridY, span.columns(), span.rows())
                            : null);
            anchored |= valid;
        }
        if (anchored) {
            cells.clear();
            cells.addAll(TileLayout.resolve(columns, spans, anchors, -1, null));
        }
    }

    void select(View source) {
        activity.selectedTile = activity.tileViews.get(source);
        for (java.util.Map.Entry<View, ImageButton> entry : edits.entrySet())
            entry.getValue().setVisibility(entry.getKey() == source ? VISIBLE : GONE);
    }

    private void lift(View source) {
        if (!activity.arranging && onEditing != null) onEditing.run();
        select(source);
        before = new ArrayList<>(cells);
        lastDropRow = 0;
        for (TileLayout.Cell c : before) lastDropRow = Math.max(lastDropRow, c.row() + c.height());
        sourceIndex = indexOfChild(source);
        source.getLocationOnScreen(location);
        pickupX = downX - location[0];
        pickupY = downY - location[1];
        nextX = before.get(sourceIndex).column();
        nextY = before.get(sourceIndex).row();
        armed = null;
        held = source;

        requestDisallowInterceptTouchEvent(true);
        source.animate().cancel();
        source.setTranslationZ(activity.ui.dp(10));
        if (motion()) source.animate().scaleX(1.035f).scaleY(1.035f).setDuration(115).start();
    }

    private boolean contains(View view, float x, float y) {
        view.getLocationOnScreen(location);
        return x >= location[0]
                && x < location[0] + view.getWidth()
                && y >= location[1]
                && y < location[1] + view.getHeight();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            downX = lastX = event.getRawX();
            downY = lastY = event.getRawY();
            armed = null;
            if (activity.arranging) {
                for (java.util.Map.Entry<View, ImageButton> entry : edits.entrySet()) {
                    if (contains(entry.getKey(), downX, downY)
                            && !contains(entry.getValue(), downX, downY)) {
                        armed = entry.getKey();
                        requestDisallowInterceptTouchEvent(true);
                        break;
                    }
                }
            }
        }
        if (action == MotionEvent.ACTION_MOVE
                && armed != null
                && held == null
                && Math.hypot(event.getRawX() - downX, event.getRawY() - downY)
                        > ViewConfiguration.get(activity).getScaledTouchSlop()) {
            MotionEvent cancel = MotionEvent.obtain(event);
            cancel.setAction(MotionEvent.ACTION_CANCEL);
            super.dispatchTouchEvent(cancel);
            cancel.recycle();
            lift(armed);
        }
        if (action == MotionEvent.ACTION_UP
                || action == MotionEvent.ACTION_CANCEL
                || action == MotionEvent.ACTION_POINTER_DOWN) armed = null;
        if (held != null) {
            if (action == MotionEvent.ACTION_MOVE) {
                lastX = event.getRawX();
                lastY = event.getRawY();
                if (!dragging
                        && Math.hypot(lastX - downX, lastY - downY)
                                > ViewConfiguration.get(activity).getScaledTouchSlop()) {
                    dragging = true;
                    postOnAnimation(edgeScroll);
                }
                if (dragging) position();
                return true;
            }
            if (action == MotionEvent.ACTION_UP
                    || action == MotionEvent.ACTION_CANCEL
                    || action == MotionEvent.ACTION_POINTER_DOWN) {
                MotionEvent cancel = MotionEvent.obtain(event);
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                super.dispatchTouchEvent(cancel);
                cancel.recycle();
                if (dragging && action == MotionEvent.ACTION_UP) {
                    lastX = event.getRawX();
                    lastY = event.getRawY();
                    position();
                }
                finishDrag(dragging && action == MotionEvent.ACTION_UP);
                return true;
            }
        }
        return super.dispatchTouchEvent(event);
    }

    private void position() {
        getLocationOnScreen(location);
        float left = lastX - location[0] - pickupX, top = lastY - location[1] - pickupY;
        TileLayout.Cell origin = before.get(sourceIndex);
        if (getLayoutDirection() == LAYOUT_DIRECTION_RTL)
            left = getWidth() - left - held.getWidth();
        int col =
                Math.max(
                        0,
                        Math.min(
                                columns - origin.width(),
                                Math.round(left * columns / (getWidth() + gap))));
        int row = Math.max(0, Math.min(lastDropRow, Math.round(top / (rowHeight + gap))));
        if (col != nextX || row != nextY) {
            nextX = col;
            nextY = row;
            TileLayout.Cell spot = new TileLayout.Cell(col, row, origin.width(), origin.height());
            applyPreview(TileLayout.resolve(columns, spans, before, sourceIndex, spot));
        }
        held.setTranslationX(lastX - location[0] - pickupX - held.getLeft());
        held.setTranslationY(top - held.getTop());
    }

    private void applyPreview(List<TileLayout.Cell> next) {
        float[] xs = new float[getChildCount()], ys = new float[getChildCount()];
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            xs[i] = v.getX();
            ys[i] = v.getY();
        }
        List<TileLayout.Cell> previous = new ArrayList<>(cells);
        cells.clear();
        cells.addAll(next);
        onLayout(false, getLeft(), getTop(), getRight(), getBottom());
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (previous.get(i).equals(next.get(i))) continue;
            v.setTranslationX(xs[i] - v.getLeft());
            v.setTranslationY(ys[i] - v.getTop());
            if (v != held) rest(v);
        }
        requestLayout();
        invalidate();
    }

    @Override
    protected void dispatchDraw(android.graphics.Canvas canvas) {
        if (dragging && held != null) {
            android.graphics.Paint p = previewPaint;
            p.setColor((activity.ui.accent & 0xffffff) | 0x55000000);
            float radius = activity.ui.dp(10);
            canvas.drawRoundRect(
                    held.getLeft(),
                    held.getTop(),
                    held.getRight(),
                    held.getBottom(),
                    radius,
                    radius,
                    p);
        }
        super.dispatchDraw(canvas);
    }

    private final android.graphics.Paint previewPaint =
            new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

    private void finishDrag(boolean drop) {
        View source = held;
        if (drop && !cells.equals(before)) {
            for (int i = 0; i < getChildCount(); i++) {
                FlowStore.Tile t = activity.tileViews.get(getChildAt(i));
                TileLayout.Cell c = cells.get(i);
                t.gridX = c.column();
                t.gridY = c.row();
                t.gridColumns = columns;
            }
            activity.store.save();
        } else if (!cells.equals(before)) applyPreview(before);
        held = null;
        before = null;
        dragging = false;
        removeCallbacks(edgeScroll);
        requestDisallowInterceptTouchEvent(false);
        rest(source);
        invalidate();
    }

    private void rest(View v) {
        v.animate().cancel();
        if (motion())
            v.animate()
                    .translationX(0)
                    .translationY(0)
                    .translationZ(0)
                    .scaleX(1)
                    .scaleY(1)
                    .setDuration(175)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
        else {
            v.setTranslationX(0);
            v.setTranslationY(0);
            v.setTranslationZ(0);
            v.setScaleX(1);
            v.setScaleY(1);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(edgeScroll);
        super.onDetachedFromWindow();
    }
}
