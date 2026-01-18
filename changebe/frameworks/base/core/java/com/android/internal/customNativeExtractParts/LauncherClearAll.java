package com.android.internal.customNativeExtractParts;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.provider.Settings;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.WeakHashMap;

public class LauncherClearAll {

    private static final String TAG = "LauncherClearAll";
    
    private static final String KEY_CLEAR_ALL_ENABLED = "launcher_clear_all";
    private static final String KEY_REPLACE_MODE = "launcher_replace_on_clear";
    private static final String KEY_BUTTON_MARGIN_BOTTOM = "launcher_clear_all_bottom_margin";
    private static final String KEY_FIX_BUTTON_ANIMATION = "launcher_fix_button";
    
    private static final String TAG_CUSTOM_BTN = "custom_clear_all_btn";
    private static final String TAG_TRANSFORMED_BTN = "transformed_clear_all";

    private static final WeakHashMap<Activity, Boolean> layoutListeners = new WeakHashMap<>();

    private static Method sDismissAllMethod;
    private static Method sDismissAllWithViewMethod;
    private static Field sNativeClearButtonField;
    private static Method sGetOverviewPanelMethod;
    private static boolean sReflectionInitialized = false;

    public static void attach(Activity activity) {
        if (layoutListeners.containsKey(activity)) return;

        final View decorView = activity.getWindow().getDecorView();
        decorView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                try {
                    if (isFixAnimationEnabled(activity)) {
                        int containerId = activity.getResources().getIdentifier("action_buttons", "id", activity.getPackageName());
                        if (containerId != 0) {
                            View container = decorView.findViewById(containerId);
                            if (container != null) applySimpleFadeInFix(container);
                        }
                    }

                    if (!isClearAllEnabled(activity)) return;

                    int containerId = activity.getResources().getIdentifier("action_buttons", "id", activity.getPackageName());
                    if (containerId == 0) return;

                    View container = decorView.findViewById(containerId);
                    if (container instanceof ViewGroup) {
                        processClearAllInjection((ViewGroup) container, activity);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error in GlobalLayoutListener", e);
                }
            }
        });

        layoutListeners.put(activity, true);
    }

    private static boolean isClearAllEnabled(Context context) {
        return Settings.Secure.getInt(context.getContentResolver(), KEY_CLEAR_ALL_ENABLED, 0) == 1;
    }

    private static boolean isFixAnimationEnabled(Context context) {
        return Settings.Secure.getInt(context.getContentResolver(), KEY_FIX_BUTTON_ANIMATION, 0) == 1;
    }

    private static void applySimpleFadeInFix(View view) {
        if ("animating".equals(view.getTag())) return;

        view.setAlpha(0f);
        view.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            private boolean animationStarted = false;

            @Override
            public boolean onPreDraw() {
                if (animationStarted) return true;

                if (view.getVisibility() == View.VISIBLE && view.isAttachedToWindow()) {
                    animationStarted = true;
                    view.clearAnimation();
                    view.setTag("animating"); 
                    
                    ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
                    animator.setDuration(400);
                    animator.addUpdateListener(animation -> {
                        float value = (float) animation.getAnimatedValue();
                        view.setAlpha(value);
                    });
                    animator.start();
                } else {
                    view.setAlpha(0f);
                }
                return true;
            }
        });
    }

    private static void processClearAllInjection(ViewGroup buttonContainer, Context context) {
        if (buttonContainer.findViewWithTag(TAG_CUSTOM_BTN) != null) return;
        
        ViewGroup parent = (ViewGroup) buttonContainer.getParent();
        if (parent == null) return;
        if (parent.findViewWithTag(TAG_CUSTOM_BTN) != null) return;

        for (int i = 0; i < buttonContainer.getChildCount(); i++) {
            if (TAG_TRANSFORMED_BTN.equals(buttonContainer.getChildAt(i).getTag())) return;
        }

        int mode = Settings.Secure.getInt(context.getContentResolver(), KEY_REPLACE_MODE, 0);

        try {
            updateOverviewActions(parent, buttonContainer, context, mode);
        } catch (Exception e) {
            Log.e(TAG, "Failed to inject/transform Clear All button", e);
        }
    }

    private static void updateOverviewActions(ViewGroup parent, ViewGroup buttonContainer, Context context, int mode) {
        int screenshotId = context.getResources().getIdentifier("action_screenshot", "id", context.getPackageName());
        int selectId = context.getResources().getIdentifier("action_select", "id", context.getPackageName());

        Button screenshotBtn = (screenshotId != 0) ? buttonContainer.findViewById(screenshotId) : null;
        Button selectBtn = (selectId != 0) ? buttonContainer.findViewById(selectId) : null;

        switch (mode) {
            case 1: 
                if (screenshotBtn != null) {
                    transformButtonToClearAll(context, screenshotBtn);
                } else {
                    addFloatingButton(parent, context, buttonContainer, selectBtn);
                }
                break;

            case 2: 
                if (selectBtn != null) {
                    transformButtonToClearAll(context, selectBtn);
                } else {
                    addFloatingButton(parent, context, buttonContainer, screenshotBtn);
                }
                break;

            case 0: 
            default:
                Button styleSource = (selectBtn != null) ? selectBtn : screenshotBtn;
                addFloatingButton(parent, context, buttonContainer, styleSource);
                break;
        }
    }

    private static void transformButtonToClearAll(Context context, Button button) {
        int stringId = context.getResources().getIdentifier("recents_clear_all", "string", context.getPackageName());
        if (stringId == 0) stringId = context.getResources().getIdentifier("clear_all", "string", "android");
        String text = (stringId != 0) ? context.getString(stringId) : "Clear All";
        
        button.setText(text);
        button.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
        button.setOnClickListener(v -> performClearAllClick(context, v));
        button.setTag(TAG_TRANSFORMED_BTN);
    }

    private static void addFloatingButton(ViewGroup parent, Context context, View containerForSync, Button styleSource) {
        Button clearButton = createClearAllButton(context, styleSource);
        syncStateWithSibling(clearButton, containerForSync);

        float marginFactor = 3.0f;
        try {
            float setVal = Settings.Secure.getFloat(context.getContentResolver(), KEY_BUTTON_MARGIN_BOTTOM);
            if (setVal > 0) marginFactor = setVal;
        } catch (Settings.SettingNotFoundException ignored) {}

        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        bottomParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        bottomParams.bottomMargin = (int) (getButtonSpacing(context) * marginFactor);
        
        clearButton.setLayoutParams(bottomParams);
        clearButton.setPadding(
                clearButton.getPaddingLeft(),
                clearButton.getPaddingTop() + 10,
                clearButton.getPaddingRight(),
                clearButton.getPaddingBottom() + 10
        );

        clearButton.clearAnimation();
        parent.addView(clearButton);
    }

    private static Button createClearAllButton(Context context, Button sourceBtn) {
        Context contextToUse = context;
        if (sourceBtn != null) {
            contextToUse = sourceBtn.getContext();
        } else {
            int themeStyleId = context.getResources().getIdentifier("ThemeControlHighlightWorkspaceColor", "style", context.getPackageName());
            if (themeStyleId != 0) {
                contextToUse = new ContextThemeWrapper(context, themeStyleId);
            }
        }

        int styleId = context.getResources().getIdentifier("OverviewActionButton", "style", context.getPackageName());
        
        Button btn = new Button(contextToUse, null, 0, styleId);
        btn.setTag(TAG_CUSTOM_BTN);
        btn.setId(View.generateViewId());

        int stringId = context.getResources().getIdentifier("recents_clear_all", "string", context.getPackageName());
        if (stringId == 0) stringId = context.getResources().getIdentifier("clear_all", "string", "android");
        btn.setText((stringId != 0) ? context.getString(stringId) : "Clear All");

        btn.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);

        if (sourceBtn != null) {
            btn.setTextColor(sourceBtn.getTextColors());
            if (sourceBtn.getBackground() != null) {
                btn.setBackground(sourceBtn.getBackground().getConstantState().newDrawable());
            }
            btn.setTypeface(sourceBtn.getTypeface());
            if (sourceBtn.getStateListAnimator() != null) {
                btn.setStateListAnimator(sourceBtn.getStateListAnimator().clone());
            }
            btn.setPadding(
                    sourceBtn.getPaddingLeft(),
                    sourceBtn.getPaddingTop(),
                    sourceBtn.getPaddingRight(),
                    sourceBtn.getPaddingBottom()
            );
        } else {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            lp.setMarginStart(getButtonSpacing(context));
            btn.setLayoutParams(lp);
        }

        btn.setOnClickListener(v -> performClearAllClick(context, v));

        return btn;
    }

    private static void performClearAllClick(Context context, View view) {
        if (!sReflectionInitialized) {
            initReflection(context);
        }

        Object recentsView = getRecentsViewCached(context);
        
        if (recentsView != null) {
            if (tryClickNativeClearAllCached(recentsView)) {
                return;
            }

            try {
                if (sDismissAllWithViewMethod != null) {
                    sDismissAllWithViewMethod.invoke(recentsView, view);
                    return;
                }
            } catch (Exception ignored) {}
            
            try {
                if (sDismissAllMethod != null) {
                    sDismissAllMethod.invoke(recentsView);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to clear tasks via cached methods", e);
            }
        } else {
            Log.e(TAG, "RecentsView is null - unable to perform action");
        }
    }

    private static void initReflection(Context context) {
        try {
            Object recentsView = getRecentsViewSlow(context);
            if (recentsView == null) return;
            
            Class<?> recentsClass = recentsView.getClass();

            sNativeClearButtonField = findField(recentsClass, "mClearAllButton");
            if (sNativeClearButtonField != null) sNativeClearButtonField.setAccessible(true);

            sDismissAllWithViewMethod = findMethod(recentsClass, "dismissAllTasks", View.class);
            if (sDismissAllWithViewMethod != null) sDismissAllWithViewMethod.setAccessible(true);

            sDismissAllMethod = findMethod(recentsClass, "dismissAllTasks");
            if (sDismissAllMethod != null) sDismissAllMethod.setAccessible(true);

            sReflectionInitialized = true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to init reflection cache", e);
        }
    }

    private static boolean tryClickNativeClearAllCached(Object recentsView) {
        try {
            if (sNativeClearButtonField != null) {
                View btn = (View) sNativeClearButtonField.get(recentsView);
                if (btn != null) {
                    btn.performClick();
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static Object getRecentsViewCached(Context context) {
        Activity activity = getActivityFromContext(context);
        if (activity != null) {
            try {
                if (sGetOverviewPanelMethod == null) {
                    sGetOverviewPanelMethod = findMethod(activity.getClass(), "getOverviewPanel");
                    if (sGetOverviewPanelMethod != null) sGetOverviewPanelMethod.setAccessible(true);
                }
                
                if (sGetOverviewPanelMethod != null) {
                    return sGetOverviewPanelMethod.invoke(activity);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to get overview panel via cache", e);
            }
        }
        return null;
    }

    private static Object getRecentsViewSlow(Context context) {
        Activity activity = getActivityFromContext(context);
        if (activity != null) {
            try {
                Method method = findMethod(activity.getClass(), "getOverviewPanel");
                if (method != null) {
                    method.setAccessible(true);
                    return method.invoke(activity);
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static Activity getActivityFromContext(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    private static Method findMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredMethod(methodName, parameterTypes);
            } catch (NoSuchMethodException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static Field findField(Class<?> clazz, String fieldName) {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static void syncStateWithSibling(View target, View source) {
        if (source == null) return;

        target.setAlpha(source.getAlpha());
        target.setTranslationY(source.getTranslationY());
        target.setTranslationX(source.getTranslationX());
        if (source.getVisibility() == View.VISIBLE) {
            target.setVisibility(View.VISIBLE);
        }

        ViewTreeObserver.OnPreDrawListener listener = new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (!source.isAttachedToWindow()) {
                    target.getViewTreeObserver().removeOnPreDrawListener(this);
                    return true;
                }

                if (target.getAlpha() != source.getAlpha()) {
                    target.setAlpha(source.getAlpha());
                }
                
                if (source instanceof ViewGroup && target.getVisibility() != source.getVisibility()) {
                    target.setVisibility(source.getVisibility());
                }

                if (target.getTranslationY() != source.getTranslationY()) {
                    target.setTranslationY(source.getTranslationY());
                }
                if (target.getTranslationX() != source.getTranslationX()) {
                    target.setTranslationX(source.getTranslationX());
                }
                return true;
            }
        };

        source.getViewTreeObserver().addOnPreDrawListener(listener);
    }

    private static int getButtonSpacing(Context context) {
        int id = context.getResources().getIdentifier("overview_actions_button_spacing", "dimen", context.getPackageName());
        if (id != 0) return context.getResources().getDimensionPixelSize(id);
        return 24;
    }
}