package com.rajan.rsflow;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;

import java.util.Arrays;

final class CubeWindow {
    private static final String[] DEFAULTS = {
        "@apps", "@center", "@google", "@music", "@settings", "@home"
    };
    private static final String[] LABELS = {
        "All apps", "Action Center", "Google", "Flow Music", "Customize", "Phone"
    };
    private static final int[] ICONS = {
        R.drawable.flow_desktop_apps, R.drawable.flow_desktop_chat,
        R.drawable.cube_google, R.drawable.flow_desktop_music,
        R.drawable.flow_desktop_settings, R.drawable.flow_desktop_phone
    };
    private static final int[] FACE_TINTS = {
        0xff718de6, 0xff618ec4, 0xff5eab92, 0xffa689dd, 0xff87a9d4, 0xff87a88b
    };
    private final MainActivity activity;
    private final FlowUi ui;
    private final Cube cube;
    private CubePose pose;
    private FrameLayout viewport;
    private boolean moving;
    private int horizontal, vertical;

    CubeWindow(MainActivity activity) {
        this.activity = activity;
        ui = activity.ui;
        pose = CubePose.decode(activity.store.number("cubePose", CubePose.INITIAL.encode()));
        horizontal = FlowUi.clamp(activity.store.number("cubePositionX", 1000), 0, 1000);
        vertical = FlowUi.clamp(activity.store.number("cubePositionY", 910), 0, 1000);
        cube = new Cube();
        updateDescription();
    }

    void attach(FrameLayout viewport) {
        this.viewport = viewport;
        int size = ui.dp(80);
        viewport.addView(
                cube, new FrameLayout.LayoutParams(size, size, Gravity.TOP | Gravity.LEFT));
        viewport.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> place());
        cube.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> place());
        activity.cubeView = cube;
    }

    private int span(boolean alongX) {
        int available =
                alongX
                        ? viewport.getWidth() - cube.getWidth()
                        : viewport.getHeight() - cube.getHeight();
        return Math.max(0, available - 2 * ui.dp(8));
    }

    private void place() {
        cube.setTranslationX(ui.dp(8) + span(true) * horizontal / 1000f);
        cube.setTranslationY(ui.dp(8) + span(false) * vertical / 1000f);
    }

    private void move(float x, float y) {
        cube.setTranslationX(ui.dp(8) + Math.max(0, Math.min(span(true), x - ui.dp(8))));
        cube.setTranslationY(ui.dp(8) + Math.max(0, Math.min(span(false), y - ui.dp(8))));
    }

    private void finishMove() {
        int x = span(true), y = span(false);
        if (x > 0) horizontal = Math.round(1000 * (cube.getTranslationX() - ui.dp(8)) / x);
        if (y > 0) vertical = Math.round(1000 * (cube.getTranslationY() - ui.dp(8)) / y);
        FlowStore.put(activity.store.options, "cubePositionX", horizontal);
        FlowStore.put(activity.store.options, "cubePositionY", vertical);
        activity.store.save();
        moving = false;
        place();
        updateDescription();
    }

    private String key(int index) {
        return activity.store.option("cube" + index, DEFAULTS[index]);
    }

    private String label(int face) {
        String key = key(face);
        int shortcut = Arrays.asList(DEFAULTS).indexOf(key);
        if ("@photos".equals(key)) return "Gallery";
        return shortcut >= 0 ? LABELS[shortcut] : activity.appName(key);
    }

    private void updateDescription() {
        cube.setContentDescription(
                moving ? "Move shortcut cube" : label(pose.front()) + ", shortcut cube");
        cube.invalidate();
    }

    private void launch() {
        if (moving) {
            finishMove();
            return;
        }
        switch (key(pose.front())) {
            case "@apps" -> activity.setScreen(MainActivity.Screen.APPS);
            case "@center" -> activity.setScreen(MainActivity.Screen.ACTION_CENTER);
            case "@google" -> {
                AppCatalog.App google =
                        activity.apps.find("com.google.android.googlequicksearchbox");
                if (google != null) activity.launch(google.key);
                else activity.toast("Google app is unavailable on this device");
            }
            case "@photos" -> activity.media.openGallery();
            case "@music" -> activity.media.music();
            case "@settings" -> activity.customize();
            case "@home" -> activity.startSafely(new Intent(Intent.ACTION_DIAL));
            default -> activity.launch(key(pose.front()));
        }
    }

    private void edit() {
        if (activity.store.flag("lockTiles", false)) return;
        int face = pose.front();
        Sheet sheet = new Sheet(activity, "Floating cube");
        sheet.add("✥","Move cube", () -> { moving=true; updateDescription(); activity.toast("Drag the cube to place it"); });
        sheet.add("▦","Choose app", () -> activity.chooseApp("Choose an app", app -> assign(face, app.key), null));
        sheet.add("↗","Launcher shortcut", () -> activity.dialog().setTitle("Launcher shortcuts").setItems(LABELS,(picker,i)->assign(face,DEFAULTS[i])).show());
        sheet.add("▢","Corners", () -> selectSize("Corner radius", "cubeRadius", new int[]{0,5,10,15,24}));
        sheet.add("▣","Border width", () -> selectSize("Border width", "cubeBorder", new int[]{0,1,2,4,6}));
        sheet.add("◉","Border color", () -> new TileEditor(activity).colors("Cube border",activity.store.number("cubeBorderColor",ui.accent),color -> {
            activity.store.set("cubeBorderColor",color);cube.invalidate();
        }));
        sheet.add("−","Hide cube", () -> { activity.store.set("floatingCube",false);activity.refresh();});
        sheet.show();
    }

    private void selectSize(String title,String key,int[] sizes) {
        String[] labels = new String[sizes.length];
        for(int i=0;i<sizes.length;i++) labels[i] = sizes[i] + "%";
        activity.dialog().setTitle(title).setItems(labels,(dialog,i)->{
            activity.store.set(key,sizes[i]);cube.invalidate();
        }).show();
    }

    private void assign(int face, String key) {
        activity.store.set("cube" + face, key);
        cube.loadIcon(face);
        updateDescription();
    }

    private final class Cube extends View {
        private final Drawable[] art = new Drawable[6];
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix matrix = new Matrix();
        private final int slop = ViewConfiguration.get(activity).getScaledTouchSlop();
        private final Runnable hold =
                () -> {
                    held = true;
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    performLongClick();
                };
        private float touchX, touchY, originX, originY, turn;
        private boolean dragged, pitch, held;
        private ValueAnimator animation;

        Cube() {
            super(activity);
            for (int i = 0; i < art.length; i++) loadIcon(i);
            setFocusable(true);
            setOnClickListener(v -> launch());
            setOnLongClickListener(
                    v -> {
                        edit();
                        return true;
                    });
        }

        private void loadIcon(int face) {
            String key = key(face);
            int shortcut = Arrays.asList(DEFAULTS).indexOf(key);
            if ("@photos".equals(key)) {
                art[face] = activity.getDrawable(R.drawable.flow_desktop_gallery);
                return;
            }
            art[face] =
                    shortcut >= 0 ? activity.getDrawable(ICONS[shortcut]) : activity.apps.icon(key);
        }

        private final float[] flat = {0, 0, 100, 0, 100, 100, 0, 100};
        private final float[] projected = new float[8], out = new float[3];
        private final float[][][] sides = {
            {{-1, -1, 1}, {1, -1, 1}, {1, 1, 1}, {-1, 1, 1}},
            {{1, -1, 1}, {1, -1, -1}, {1, 1, -1}, {1, 1, 1}},
            {{-1, -1, -1}, {1, -1, -1}, {1, -1, 1}, {-1, -1, 1}},
            {{1, -1, -1}, {-1, -1, -1}, {-1, 1, -1}, {1, 1, -1}},
            {{-1, -1, -1}, {-1, -1, 1}, {-1, 1, 1}, {-1, 1, -1}},
            {{-1, 1, 1}, {1, 1, 1}, {1, 1, -1}, {-1, 1, -1}}
        };
        private final float[][] normals = {
            {0, 0, 1}, {1, 0, 0}, {0, -1, 0}, {0, 0, -1}, {-1, 0, 0}, {0, 1, 0}
        };

        private void transform(float[] point, float c, float sine) {
            float x = point[0], y = point[1], z = point[2];
            float x1 = pitch ? x : c * x + sine * z;
            float y1 = pitch ? c * y + sine * z : y;
            float z1 = pitch ? -sine * y + c * z : -sine * x + c * z;
            out[0] = .966f * x1 + .259f * z1;
            float z2 = -.259f * x1 + .966f * z1;
            out[1] = .966f * y1 + .259f * z2;
            out[2] = -.259f * y1 + .966f * z2;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float scale = Math.min(getWidth(), getHeight()) * .279f;
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float cosine = (float) Math.cos(Math.toRadians(turn));
            float sine = (float) Math.sin(Math.toRadians(turn));
            int[] faces = {
                pose.front(),
                pose.right(),
                pose.top(),
                CubePose.opposite(pose.front()),
                CubePose.opposite(pose.right()),
                CubePose.opposite(pose.top())
            };
            paint.setColor(0x55000000);
            canvas.drawOval(cx - scale, cy + 1.28f * scale, cx + scale, cy + 1.53f * scale, paint);
            for (int i = 0; i < sides.length; i++) {
                transform(normals[i], cosine, sine);
                if (out[2] <= .04f) continue;
                float lighting = out[2];
                for (int vertex = 0; vertex < 4; vertex++) {
                    transform(sides[i][vertex], cosine, sine);
                    float depth = scale / (1 - .09f * out[2]);
                    projected[2 * vertex] = cx + out[0] * depth;
                    projected[2 * vertex + 1] = cy + out[1] * depth;
                }
                if (!matrix.setPolyToPoly(flat, 0, projected, 0, 4)) continue;
                int face = faces[i];
                canvas.save();
                canvas.concat(matrix);
                paint.setColor(FlowUi.blend(ui.panel, FACE_TINTS[face], .52f + .32f * lighting));
                float radius = FlowUi.clamp(activity.store.number("cubeRadius", 10), 0, 24);
                canvas.drawRoundRect(0, 0, 100, 100, radius, radius, paint);
                int border = FlowUi.clamp(activity.store.number("cubeBorder",0),0,6);
                if(border>0) {
                    paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(border);
                    paint.setColor(activity.store.number("cubeBorderColor",ui.accent));
                    float edge=border/2f;
                    canvas.drawRoundRect(edge,edge,100-edge,100-edge,radius,radius,paint);
                    paint.setStyle(Paint.Style.FILL);
                }
                Drawable icon = art[face];
                if (icon != null) {
                    icon.setBounds(23, 23, 77, 77);
                    icon.draw(canvas);
                }
                canvas.restore();
            }
        }

        private void animateTurn(float target, boolean vertical) {
            if (animation != null && animation.isRunning()) return;
            pitch = vertical;
            if (activity.store.number("motion", 1) == 0 || !ValueAnimator.areAnimatorsEnabled()) {
                completeTurn(target);
                return;
            }
            animation = ValueAnimator.ofFloat(turn, target);
            animation.setDuration(175);
            animation.setInterpolator(new AccelerateDecelerateInterpolator());
            animation.addUpdateListener(
                    value -> {
                        turn = (float) value.getAnimatedValue();
                        invalidate();
                    });
            animation.addListener(
                    new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator finished) {
                            completeTurn(target);
                        }
                    });
            animation.start();
        }

        private void completeTurn(float target) {
            if (target != 0) {
                pose = pose.turn(target > 0 ? 1 : -1, pitch);
                activity.store.set("cubePose", pose.encode());
            }
            turn = 0;
            updateDescription();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (animation != null && animation.isRunning()) return true;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN -> {
                    touchX = event.getRawX();
                    touchY = event.getRawY();
                    originX = getTranslationX();
                    originY = getTranslationY();
                    dragged = false;
                    held = false;
                    getParent().requestDisallowInterceptTouchEvent(true);
                    if (!moving && !activity.store.flag("lockTiles", false))
                        postDelayed(hold, ViewConfiguration.getLongPressTimeout());
                }
                case MotionEvent.ACTION_MOVE -> {
                    if (held) break;
                    float dx = event.getRawX() - touchX, dy = event.getRawY() - touchY;
                    if (!dragged && Math.max(Math.abs(dx), Math.abs(dy)) > slop) {
                        dragged = true;
                        pitch = Math.abs(dy) > Math.abs(dx);
                        removeCallbacks(hold);
                    }
                    if (dragged) {
                        if (moving) move(originX + dx, originY + dy);
                        else {
                            turn =
                                    Math.max(
                                            -90,
                                            Math.min(90, (pitch ? dy : dx) * 102 / getWidth()));
                            invalidate();
                        }
                    }
                }
                case MotionEvent.ACTION_UP -> {
                    removeCallbacks(hold);
                    if (held) break;
                    if (moving) finishMove();
                    else if (dragged)
                        animateTurn(Math.abs(turn) >= 24 ? Math.copySign(90, turn) : 0, pitch);
                    else performClick();
                }
                case MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                    removeCallbacks(hold);
                    held = true;
                    turn = 0;
                    if (moving) place();
                    invalidate();
                }
                default -> {}
            }
            return true;
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setClassName(android.widget.Button.class.getName());
            info.addAction(
                    new AccessibilityNodeInfo.AccessibilityAction(R.id.cube_next, "Turn right"));
            info.addAction(
                    new AccessibilityNodeInfo.AccessibilityAction(R.id.cube_previous, "Turn left"));
            info.addAction(
                    new AccessibilityNodeInfo.AccessibilityAction(R.id.cube_top, "Turn down"));
            info.addAction(
                    new AccessibilityNodeInfo.AccessibilityAction(R.id.cube_bottom, "Turn up"));
        }

        @Override
        public boolean performAccessibilityAction(int action, Bundle args) {
            boolean vertical = action == R.id.cube_top || action == R.id.cube_bottom;
            if (vertical || action == R.id.cube_next || action == R.id.cube_previous) {
                animateTurn(
                        action == R.id.cube_next || action == R.id.cube_top ? 90 : -90, vertical);
                return true;
            }
            return super.performAccessibilityAction(action, args);
        }

        @Override
        protected void onDetachedFromWindow() {
            removeCallbacks(hold);
            if (animation != null) {
                animation.removeAllListeners();
                animation.cancel();
            }
            super.onDetachedFromWindow();
        }
    }
}
