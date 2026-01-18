package com.android.internal.customNativeExtractParts;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.UserHandle; 
import android.provider.Settings;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import java.lang.reflect.Field;

public class LauncherDT2S {

    private static final String TAG = "LauncherDT2S_ver6";
    private static final String CLASS_WORKSPACE_PART = "Workspace";
    
    private static final String KEY_DT2S_ENABLED = "launcher_dt2s_enabled";
    private static final String KEY_DT2S_TIMEOUT = "launcher_dt2s_timeout";

    private static Field mDoubleTapTimeoutField;

    static {
        try {
            mDoubleTapTimeoutField = GestureDetector.class.getDeclaredField("mDoubleTapTimeout");
            mDoubleTapTimeoutField.setAccessible(true);
        } catch (Exception ignored) {}
    }

    public static void init() {}

    public static void attach(Activity activity) {
        try {
            View workspace = findWorkspaceView(activity.getWindow().getDecorView());
            if (workspace != null) {
                applySpyListener(activity, workspace);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error attaching hook", e);
        }
    }

    private static View findWorkspaceView(View root) {
        if (root == null) return null;
        if (root.getClass().getName().contains(CLASS_WORKSPACE_PART)) return root;
        
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View res = findWorkspaceView(group.getChildAt(i));
                if (res != null) return res;
            }
        }
        return null;
    }

    private static void applySpyListener(final Context context, View view) {
        final View.OnTouchListener originalListener = getExistingOnTouchListener(view);

        final GestureDetector gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (Settings.Secure.getInt(context.getContentResolver(), KEY_DT2S_ENABLED, 1) == 1) {
                    Log.d(TAG, "DoubleTap detected -> Sending Broadcast");
                    sendSleepBroadcast(context);
                    return true;
                }
                return false; 
            }

            @Override
            public boolean onDown(MotionEvent e) {
                return false; 
            }
        });

        gestureDetector.setIsLongpressEnabled(false);
        updateTimeout(context, gestureDetector);

        view.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    updateTimeout(context, gestureDetector);
                }

                boolean handledByDt2s = gestureDetector.onTouchEvent(event);

                if (handledByDt2s) {
                    return true;
                }

                if (originalListener != null) {
                    return originalListener.onTouch(v, event);
                }

                return false; 
            }
        });
        
        Log.i(TAG, "DT2S hooked successfully (Chain Mode). Original listener found: " + (originalListener != null));
    }

    private static View.OnTouchListener getExistingOnTouchListener(View view) {
        try {
            Field listenerInfoField = View.class.getDeclaredField("mListenerInfo");
            listenerInfoField.setAccessible(true);
            Object listenerInfo = listenerInfoField.get(view);
            
            if (listenerInfo != null) {
                Field listenerField = listenerInfo.getClass().getDeclaredField("mOnTouchListener");
                listenerField.setAccessible(true);
                return (View.OnTouchListener) listenerField.get(listenerInfo);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to get existing OnTouchListener", e);
        }
        return null;
    }

    private static void updateTimeout(Context context, GestureDetector detector) {
        if (mDoubleTapTimeoutField == null) return;
        try {
            int timeout = Settings.Secure.getInt(context.getContentResolver(), KEY_DT2S_TIMEOUT, 250);
            mDoubleTapTimeoutField.setInt(detector, timeout);
        } catch (Exception ignored) {}
    }

    private static void sendSleepBroadcast(Context context) {
        try {
            Intent intent = new Intent("org.pixel.customparts.ACTION_SLEEP");
            intent.setClassName("org.pixel.customparts", "org.pixel.customparts.services.SleepReceiver");
            
            intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);

            context.sendBroadcast(intent); 
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to send broadcast", e);
        }
    }
}