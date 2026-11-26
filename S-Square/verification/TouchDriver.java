package org.ssquare.uitest;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/** Test-only driver, installed separately on the disposable emulator. */
public final class TouchDriver extends Instrumentation {
    private Bundle args;
    public void onCreate(Bundle arguments) { super.onCreate(arguments); args = arguments; start(); }
    public void onStart() {
        Bundle result = new Bundle();
        try {
            UiAutomation ui = getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            if ("pinch".equals(args.getString("op"))) pinch(ui);
            else {
                String label = args.getString("label");
                boolean success = false;
                for (int i = 0; i < 10 && !success; i++) {
                    AccessibilityNodeInfo root = ui.getRootInActiveWindow();
                    if (root != null) success = click(root, label);
                    if (!success) SystemClock.sleep(120);
                }
                if (!success) throw new AssertionError("Missing clickable node " + label);
            }
            result.putString("result", "passed");
        } catch (Throwable failure) { result.putString("result", failure.toString()); }
        finish(-1, result);
    }
    private boolean click(AccessibilityNodeInfo node, String label) {
        if (node.isVisibleToUser() && (label.contentEquals(node.getText()==null?"":node.getText())
                || label.contentEquals(node.getContentDescription()==null?"":node.getContentDescription()))) {
            for (AccessibilityNodeInfo parent=node; parent!=null; parent=parent.getParent())
                if (parent.isClickable() && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
        }
        for (int i=0; i<node.getChildCount(); i++) {
            AccessibilityNodeInfo child=node.getChild(i);
            if (child!=null && click(child,label)) return true;
        }
        return false;
    }
    private void pinch(UiAutomation ui) {
        float x=Float.parseFloat(args.getString("x")), y=Float.parseFloat(args.getString("y"));
        long down=SystemClock.uptimeMillis();
        MotionEvent.PointerProperties[] ids=new MotionEvent.PointerProperties[2];
        MotionEvent.PointerCoords[] points=new MotionEvent.PointerCoords[2];
        for (int i=0;i<2;i++) {
            ids[i]=new MotionEvent.PointerProperties(); ids[i].id=i; ids[i].toolType=MotionEvent.TOOL_TYPE_FINGER;
            points[i]=new MotionEvent.PointerCoords(); points[i].pressure=1; points[i].size=1; points[i].y=y;
        }
        for(int frame=0;frame<13;frame++) {
            float spread=x*(.55f-.039f*Math.min(10,Math.max(0,frame-1)));
            points[0].x=x-spread; points[1].x=x+spread;
            int action=frame==0?MotionEvent.ACTION_DOWN:frame==1?MotionEvent.ACTION_POINTER_DOWN|256:
                    frame==11?MotionEvent.ACTION_POINTER_UP|256:frame==12?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE;
            MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,frame==0||frame==12?1:2,
                    ids,points,0,0,1,1,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);
            if(!ui.injectInputEvent(e,true)) throw new AssertionError("Touch injection failed");
            e.recycle(); SystemClock.sleep(12);
        }
    }
}
