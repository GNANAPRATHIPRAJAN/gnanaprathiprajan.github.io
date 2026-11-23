package com.rajan.rsflow;

import android.view.*;
import android.widget.FrameLayout;

final class WidgetFrame extends FrameLayout {
  private float x, y;
  private boolean held;
  private final int slop;
  private final Runnable hold =
      () -> {
        if (!isAttachedToWindow()) return;
        held = true;
        MotionEvent e =
            MotionEvent.obtain(
                0, android.os.SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, x, y, 0);
        super.dispatchTouchEvent(e);
        e.recycle();
        performLongClick();
      };

  WidgetFrame(MainActivity a) {
    super(a);
    slop = ViewConfiguration.get(a).getScaledTouchSlop();
  }

  @Override
  public boolean dispatchTouchEvent(MotionEvent e) {
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN -> {
        held = false;
        x = e.getX();
        y = e.getY();
        removeCallbacks(hold);
        postDelayed(hold, ViewConfiguration.getLongPressTimeout());
      }
      case MotionEvent.ACTION_MOVE -> {
        if (Math.abs(x - e.getX()) > slop || Math.abs(y - e.getY()) > slop) removeCallbacks(hold);
      }
      case MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_DOWN ->
          removeCallbacks(hold);
    }
    if (!held) super.dispatchTouchEvent(e);
    return true;
  }

  @Override
  protected void onDetachedFromWindow() {
    removeCallbacks(hold);
    super.onDetachedFromWindow();
  }
}
