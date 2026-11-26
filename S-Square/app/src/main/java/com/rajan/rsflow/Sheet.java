package com.rajan.rsflow;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.*;
import android.widget.*;

final class Sheet {
    static final int[] COLORS = {
        0xff3058a4, 0xff8b3a62, 0xff276a62, 0xff634899, 0xff92502d, 0xff236077
    };
    private final Dialog d;
    private final FlowUi u;
    private final MainActivity a;
    private final LinearLayout grid;
    private LinearLayout row;
    private int count;
    private final int cols;

    Sheet(MainActivity a, String title) {
        this.a = a;
        u = a.ui;
        d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        cols = a.getResources().getConfiguration().screenWidthDp < 330 || u.textScale > 1.2 ? 2 : 3;
        LinearLayout panel = u.column();
        panel.setPadding(u.dp(14), u.dp(10), u.dp(14), u.dp(12));
        panel.setBackground(u.rounded(FlowUi.blend(u.panel,u.accent,.08f), a.store.desktop()?24:10, FlowUi.blend(u.outline,u.accent,.38f)));
        TextView heading = u.label(title, 21, u.text);
        heading.setPadding(u.dp(3), u.dp(6), u.dp(3), u.dp(11));
        if (android.os.Build.VERSION.SDK_INT >= 28) heading.setAccessibilityHeading(true);
        panel.addView(heading);
        ScrollView scroll =
                new ScrollView(a) {
                    @Override
                    protected void onMeasure(int width, int height) {
                        int cap = u.dp(a.getResources().getConfiguration().screenHeightDp * .68f);
                        int limit =
                                MeasureSpec.getMode(height) == MeasureSpec.UNSPECIFIED
                                        ? cap
                                        : Math.min(cap, MeasureSpec.getSize(height));
                        super.onMeasure(
                                width, MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST));
                    }
                };
        scroll.setFillViewport(false);
        grid = u.column();
        scroll.addView(grid);
        panel.addView(scroll);
        d.setContentView(panel);
        d.setCanceledOnTouchOutside(true);
    }

    void category(String name, Runnable click) {
        ImageView icon = new ImageView(a);
        icon.setImageResource(AppStyle.categoryArt(name));
        icon.setColorFilter(u.ink); int p=u.dp(7);icon.setPadding(p,p,p,p);
        icon.setBackground(u.rounded(u.accent,a.store.desktop()?12:4,0));
        add(icon,name,click,null);
    }

    void add(String mark, String label, Runnable click) {
        TextView v = u.label(mark, 22, -1);
        v.setGravity(Gravity.CENTER);
        v.setTextColor(u.ink);
        v.setBackground(u.rounded(u.accent, a.store.desktop()?12:4, 0));
        add(v, label, click, null);
    }

    void add(View icon, String label, Runnable click, Runnable hold) {
        if (count % cols == 0) {
            row = u.row();
            row.setGravity(Gravity.TOP);
            grid.addView(row);
        }
        LinearLayout key = u.column();
        key.setGravity(Gravity.CENTER);
        key.setPadding(u.dp(5), u.dp(9), u.dp(5), u.dp(9));
        key.setBackground(u.surface(FlowUi.blend(u.panel, u.accent, .16f), a.store.desktop()?14:6, 0));
        key.addView(icon, new LinearLayout.LayoutParams(u.dp(34), u.dp(34)));
        u.gap(key, 4);
        TextView l = u.label(label, 13, u.text);
        l.setGravity(Gravity.CENTER);
        l.setMaxLines(2);
        l.setEllipsize(android.text.TextUtils.TruncateAt.END);
        l.setMinHeight(u.dp(u.textScale > 1.2 ? 42 : 32));
        key.addView(l, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(u.dp(3), u.dp(3), u.dp(3), u.dp(3));
        row.addView(key, lp);
        key.setFocusable(true);
        key.setContentDescription(label);
        key.setOnClickListener(
                v -> {
                    d.dismiss();
                    click.run();
                });
        if (hold != null)
            key.setOnLongClickListener(
                    v -> {
                        d.dismiss();
                        hold.run();
                        return true;
                    });
        count++;
    }

    void show() {
        Window w = d.getWindow();
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        w.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        WindowManager.LayoutParams lp = w.getAttributes();
        lp.width = u.dp(Math.min(520, a.getResources().getConfiguration().screenWidthDp - 28));
        lp.y = u.dp(24);
        w.setAttributes(lp);
        d.show();
        if (a.store.number("motion", 1) != 0
                && android.animation.ValueAnimator.areAnimatorsEnabled()) {
            View v = w.getDecorView();
            v.setTranslationY(u.dp(20));
            v.animate().translationY(0).setDuration(150).start();
        }
    }
}
