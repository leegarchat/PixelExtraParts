package com.android.internal.customNativeExtractParts;

import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class SearchWidgetHook {

    private static final String TAG = "SearchWidgetHook";
    private static final String KEY_DOCK_ENABLE = "launcher_dock_enable";
    private static final String KEY_HIDE_SEARCH = "launcher_hidden_search";
    private static final String KEY_HIDE_DOCK = "launcher_hidden_dock";
    private static final String KEY_PADDING_HOMEPAGE = "launcher_padding_homepage";
    private static final String KEY_PADDING_DOCK = "launcher_padding_dock";
    private static final String KEY_PADDING_SEARCH = "launcher_padding_search";
    private static final String KEY_PADDING_DOTS = "launcher_padding_dots";
    
    private static int lastAppliedHash = 0;

    private static final Map<String, Field> sFieldCache = new HashMap<>();
    private static final Map<String, Method> sMethodCache = new HashMap<>();
    private static boolean sSettingsLoaded = false;
    private static boolean sIsDockEnabled;
    private static boolean sHideSearch;
    private static boolean sHideDock;
    private static int sPaddingHomepage;
    private static int sPaddingDock;
    private static int sPaddingSearch;
    private static int sPaddingDots;

    public static void attach(final Activity activity) {
        if (activity == null) return;
        View contentView = activity.findViewById(android.R.id.content);
        if (contentView != null) {
            contentView.post(() -> applySettings(activity));
        } else {
            applySettings(activity);
        }
    }

    private static void loadSettings(Context context) {
        try {
            sIsDockEnabled = isEnabled(context, KEY_DOCK_ENABLE);
            if (sIsDockEnabled) {
                sHideSearch = isEnabled(context, KEY_HIDE_SEARCH);
                sHideDock = isEnabled(context, KEY_HIDE_DOCK);
                sPaddingHomepage = getInt(context, KEY_PADDING_HOMEPAGE, 165);
                sPaddingDock = getInt(context, KEY_PADDING_DOCK, 0);
                sPaddingSearch = getInt(context, KEY_PADDING_SEARCH, 0);
                sPaddingDots = getInt(context, KEY_PADDING_DOTS, 0);
            }
            sSettingsLoaded = true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to load settings", e);
        }
    }

    private static void applySettings(Activity activity) {
        try {
            if (!sSettingsLoaded) {
                loadSettings(activity);
            }

            if (!sIsDockEnabled) return;

            int currentHash = (sHideSearch ? 1 : 0) + (sHideDock ? 1 : 0) * 2 + 
                              sPaddingHomepage * 4 + sPaddingDock * 8 + 
                              sPaddingSearch * 16 + sPaddingDots * 32;
            
            Object deviceProfile = getField(activity, "mDeviceProfile");
            if (deviceProfile == null) return;

            View workspace = (View) getField(activity, "mWorkspace");
            if (workspace == null) return;

            ViewGroup hotseat = (ViewGroup) getField(activity, "mHotseat");
            if (hotseat == null) return;

            if (lastAppliedHash != currentHash) {
                boolean dpChanged = false;
                Object paddingObj = getField(deviceProfile, "workspacePadding");
                if (paddingObj instanceof Rect) {
                    Rect rect = (Rect) paddingObj;
                    int desiredBottom;
                    if (sPaddingHomepage == -45) {
                        desiredBottom = rect.bottom;
                    } else {
                        desiredBottom = toPx(activity, sPaddingHomepage + 20);
                    }

                    if (rect.bottom != desiredBottom) {
                        rect.bottom = desiredBottom;
                        dpChanged = true;
                    }
                }

                if (dpChanged) {
                    triggerNativeUpdate(workspace, deviceProfile);
                    triggerNativeUpdate(hotseat, deviceProfile);
                }
                lastAppliedHash = currentHash;
            }

            View qsbView = findQsbView(hotseat, activity);
            View dockIconsView = findHotseatCellLayout(hotseat);
            View pageIndicator = findPageIndicator(activity);

            if (qsbView != null) {
                float searchTranslationY = (sPaddingSearch != 0) ? -1f * toPx(activity, sPaddingSearch) : 0f;
                int searchVisibility = sHideSearch ? View.GONE : View.VISIBLE;
                enforceViewProperties(qsbView, searchVisibility, searchTranslationY, sHideSearch);
            }

            if (dockIconsView != null) {
                float dockTranslationY = (sPaddingDock != 0) ? -1f * toPx(activity, sPaddingDock) : 0f;
                int dockVisibility = sHideDock ? View.GONE : View.VISIBLE;
                enforceViewProperties(dockIconsView, dockVisibility, dockTranslationY, sHideDock);
            }

            if (pageIndicator != null) {
                applyDotsMargin(pageIndicator, activity, sPaddingDots);
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to apply settings", e);
        }
    }

    private static void applyDotsMargin(final View view, Context context, int paddingDots) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (!(params instanceof ViewGroup.MarginLayoutParams)) return;

        final ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) params;

        int originalMarginTag = 0x7f010003; 
        Object originalTag = view.getTag(originalMarginTag);
        int originalBottomMargin;

        if (originalTag instanceof Integer) {
            originalBottomMargin = (Integer) originalTag;
        } else {
            originalBottomMargin = marginParams.bottomMargin;
            view.setTag(originalMarginTag, originalBottomMargin);
        }

        int diffDp = paddingDots - (-30);
        int diffPx = toPx(context, diffDp);
        final int newBottomMargin = originalBottomMargin + diffPx;

        if (marginParams.bottomMargin != newBottomMargin) {
            marginParams.bottomMargin = newBottomMargin;
            view.setLayoutParams(marginParams);
        }

        int listenerTag = 0x7f010004;
        if (view.getTag(listenerTag) == null) {
            View.OnLayoutChangeListener listener = (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                ViewGroup.LayoutParams p = v.getLayoutParams();
                if (p instanceof ViewGroup.MarginLayoutParams) {
                    ViewGroup.MarginLayoutParams mp = (ViewGroup.MarginLayoutParams) p;
                    if (mp.bottomMargin != newBottomMargin) {
                        mp.bottomMargin = newBottomMargin;
                        v.setLayoutParams(mp);
                    }
                }
            };
            view.addOnLayoutChangeListener(listener);
            view.setTag(listenerTag, listener);
        }
    }

    private static void enforceViewProperties(final View view, final int visibility, final float translationY, final boolean forceHeightZero) {
        applyProps(view, visibility, translationY, forceHeightZero);

        int listenerTag = 0x7f010002;
        View.OnLayoutChangeListener existingListener = (View.OnLayoutChangeListener) view.getTag(listenerTag);

        if (existingListener == null) {
            View.OnLayoutChangeListener listener = (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                applyProps(v, visibility, translationY, forceHeightZero);
            };
            view.addOnLayoutChangeListener(listener);
            view.setTag(listenerTag, listener);
        } else {
            view.removeOnLayoutChangeListener(existingListener);
            View.OnLayoutChangeListener newListener = (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                applyProps(v, visibility, translationY, forceHeightZero);
            };
            view.addOnLayoutChangeListener(newListener);
            view.setTag(listenerTag, newListener);
        }
    }

    private static void applyProps(View view, int visibility, float translationY, boolean forceHeightZero) {
        if (view.getVisibility() != visibility) {
            view.setVisibility(visibility);
        }
        if (view.getTranslationY() != translationY) {
            view.setTranslationY(translationY);
        }

        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params != null) {
            if (forceHeightZero) {
                if (params.height != 0) {
                    params.height = 0;
                    view.setLayoutParams(params);
                }
            } else if (visibility == View.VISIBLE) {
                if (params.height == 0) {
                    params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    view.setLayoutParams(params);
                }
            }
        }
    }


    private static View findQsbView(ViewGroup hotseat, Context context) {
        View qsb = (View) getField(hotseat, "mQsb");
        if (qsb != null) return qsb;

        int resId1 = context.getResources().getIdentifier("search_container_hotseat", "id", context.getPackageName());
        if (resId1 != 0) {
            View v = hotseat.findViewById(resId1);
            if (v != null) return v;
        }

        int resId2 = context.getResources().getIdentifier("qsb_container", "id", context.getPackageName());
        if (resId2 != 0) {
            View v = hotseat.findViewById(resId2);
            if (v != null) return v;
        }
        return null;
    }

    private static View findHotseatCellLayout(ViewGroup hotseat) {
        int resId = hotseat.getContext().getResources().getIdentifier("layout", "id", hotseat.getContext().getPackageName());
        if (resId != 0) {
            View v = hotseat.findViewById(resId);
            if (v != null) return v;
        }

        for (int i = 0; i < hotseat.getChildCount(); i++) {
            View child = hotseat.getChildAt(i);
            if (child instanceof ViewGroup) {
                if (child.getClass().getName().contains("CellLayout")) {
                    return child;
                }
            }
        }
        for (int i = 0; i < hotseat.getChildCount(); i++) {
            View child = hotseat.getChildAt(i);
            if (child instanceof ViewGroup) return child;
        }
        return null;
    }

    private static View findPageIndicator(Activity activity) {
        int resId = activity.getResources().getIdentifier("page_indicator", "id", activity.getPackageName());
        if (resId != 0) {
            return activity.findViewById(resId);
        }
        return null;
    }


    private static void triggerNativeUpdate(View view, Object deviceProfile) {
        try {
            Method onDpChanged = findMethod(view.getClass(), "onDeviceProfileChanged", deviceProfile.getClass());
            if (onDpChanged != null) {
                onDpChanged.setAccessible(true);
                onDpChanged.invoke(view, deviceProfile);
            }
        } catch (Exception ignored) {}
    }

    private static boolean isEnabled(Context context, String key) {
        return Settings.Secure.getInt(context.getContentResolver(), key, 0) == 1;
    }

    private static int getInt(Context context, String key, int def) {
        return Settings.Secure.getInt(context.getContentResolver(), key, def);
    }

    private static int toPx(Context context, int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }

    private static Object getField(Object obj, String fieldName) {
        if (obj == null) return null;
        try {
            Field field = getFieldObject(obj.getClass(), fieldName);
            if (field != null) {
                return field.get(obj);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static Field getFieldObject(Class<?> clazz, String fieldName) {
        String key = clazz.getName() + "." + fieldName;
        synchronized (sFieldCache) {
            if (sFieldCache.containsKey(key)) {
                return sFieldCache.get(key);
            }
        }

        Class<?> current = clazz;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                synchronized (sFieldCache) {
                    sFieldCache.put(key, field);
                }
                return field;
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        
        synchronized (sFieldCache) {
            sFieldCache.put(key, null);
        }
        return null;
    }

    private static Method findMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        StringBuilder keyBuilder = new StringBuilder(clazz.getName()).append(".").append(methodName);
        for (Class<?> p : parameterTypes) {
            keyBuilder.append("-").append(p.getName());
        }
        String key = keyBuilder.toString();

        synchronized (sMethodCache) {
            if (sMethodCache.containsKey(key)) {
                return sMethodCache.get(key);
            }
        }

        Class<?> current = clazz;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(methodName, parameterTypes);
                method.setAccessible(true);
                synchronized (sMethodCache) {
                    sMethodCache.put(key, method);
                }
                return method;
            } catch (NoSuchMethodException e) {
                current = current.getSuperclass();
            }
        }
        
        synchronized (sMethodCache) {
            sMethodCache.put(key, null);
        }
        return null;
    }
}