package org.pixel.customparts.hooks;

import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.graphics.Insets;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;

import org.pixel.customparts.core.BaseHook;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class GestureBarHook extends BaseHook {
    private static final String PACKAGE_SYSTEMUI = "com.android.systemui";
    private static final String PACKAGE_NEXUS_LAUNCHER = "com.google.android.apps.nexuslauncher";
    private static final String PACKAGE_PIXEL_LAUNCHER = "com.google.android.apps.pixel.launcher";
    private static final String PACKAGE_AOSP_LAUNCHER = "com.android.launcher3";

    private static final String KEY_ENABLED = "gesture_bar_enabled";
    private static final String KEY_WIDTH_PERCENT = "gesture_bar_width_percent";
    private static final String KEY_HEIGHT_DP = "gesture_bar_height_dp";
    private static final String KEY_OFFSET_X_DP = "gesture_bar_offset_x_dp";
    private static final String KEY_OFFSET_Y_DP = "gesture_bar_offset_y_dp";
    private static final String KEY_RESERVED_AREA_DP = "gesture_bar_reserved_area_dp";
    private static final String KEY_GESTURE_AREA_DP = "gesture_bar_gesture_area_dp";
    private static final String KEY_HIDE_ON_LAUNCHER = "gesture_bar_hide_on_launcher";
    private static final String KEY_HIDE_IN_APPS = "gesture_bar_hide_in_apps";
    private static final String KEY_LAUNCHER_HIDE_TIMEOUT_MS = "gesture_bar_launcher_hide_timeout_ms";
    private static final String KEY_APP_HIDE_TIMEOUT_MS = "gesture_bar_app_hide_timeout_ms";
    private static final String KEY_HIDE_ON_LOCKSCREEN = "gesture_bar_hide_on_lockscreen";
    private static final String KEY_REMOVE_RESERVED_AREA = "gesture_bar_remove_reserved_area";
    private static final String KEY_ALPHA_PERCENT = "gesture_bar_alpha_percent";
    private static final String KEY_FADE_IN_MS = "gesture_bar_fade_in_ms";
    private static final String KEY_FADE_OUT_MS = "gesture_bar_fade_out_ms";
    private static final String KEY_TINT_ENABLED = "gesture_bar_tint_enabled";
    private static final String KEY_TINT_COLOR = "gesture_bar_tint_color";

    private static final int DEFAULT_WIDTH_PERCENT = 28;
    private static final int DEFAULT_HEIGHT_DP = 4;
    private static final int DEFAULT_RESERVED_AREA_DP = 24;
    private static final int DEFAULT_GESTURE_AREA_DP = 24;
    private static final int DEFAULT_HIDE_TIMEOUT_MS = 3000;
    private static final int DEFAULT_FADE_MS = 220;
    private static final int DEFAULT_TINT_COLOR = 0xFFFFFFFF;
    private static final int MIN_FADE_MS = 20;
    private static final int MAX_FADE_MS = 1500;
    private static final int ACTIVITY_TYPE_HOME = 2;
    private static final int WINDOW_TYPE_NAVIGATION_BAR = 2019;
    private static final int WINDOW_TYPE_NAVIGATION_BAR_PANEL = 2024;
    private static final long VIEW_ALPHA_ANIMATION_MS = 220L;
    private static final long LAUNCHER_HIDE_FADE_MS = 220L;
    private static final String HOME_TASK_PACKAGE = "android.activity.home";
    private static final String EXTRA_APPLYING_GESTURAL_HEIGHT = "pixelparts_gesture_bar_applying_gestural_height";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<View, String> appliedViewSignatures = Collections.synchronizedMap(new WeakHashMap<View, String>());
    private final Map<WindowManager.LayoutParams, Integer> originalNavigationWindowHeights = Collections.synchronizedMap(new WeakHashMap<WindowManager.LayoutParams, Integer>());
    private final Map<View, Boolean> launcherHandleViews = Collections.synchronizedMap(new WeakHashMap<View, Boolean>());
    private final Map<Object, Boolean> rotationTouchHelpers = Collections.synchronizedMap(new WeakHashMap<Object, Boolean>());
    private final Map<View, Float> appliedViewAlphas = Collections.synchronizedMap(new WeakHashMap<View, Float>());
    private final Map<Object, Runnable> pendingLauncherHideRunnables = Collections.synchronizedMap(new WeakHashMap<Object, Runnable>());
    private final Map<Object, String> pendingLauncherHideKeys = Collections.synchronizedMap(new WeakHashMap<Object, String>());
    private final Map<Object, Boolean> fadingLauncherHideOwners = Collections.synchronizedMap(new WeakHashMap<Object, Boolean>());
    private final Map<Object, String> fadingLauncherHideKeys = Collections.synchronizedMap(new WeakHashMap<Object, String>());
    private final Map<Object, Boolean> activeLauncherHideOwners = Collections.synchronizedMap(new WeakHashMap<Object, Boolean>());
    private final Map<Object, String> activeLauncherHideKeys = Collections.synchronizedMap(new WeakHashMap<Object, String>());
    private final Map<Object, Boolean> fadingLauncherRestoreOwners = Collections.synchronizedMap(new WeakHashMap<Object, Boolean>());
    private final Map<Object, Object> launcherAlphaAnimationTokens = Collections.synchronizedMap(new WeakHashMap<Object, Object>());
    private final Map<Object, Float> appliedLauncherOwnerAlphas = Collections.synchronizedMap(new WeakHashMap<Object, Float>());

    private boolean settingsObserverRegistered;

    @Override
    public String getHookId() {
        return "GestureBarHook";
    }

    @Override
    public int getPriority() {
        return 66;
    }

    @Override
    public boolean isEnabled(Context context) {
        return isSettingEnabled(context, KEY_ENABLED, false);
    }

    @Override
    protected void onInit(ClassLoader classLoader) {
        hookLauncher(classLoader);
    }

    private void registerSettingsObserver(Context context) {
        if (context == null || settingsObserverRegistered) {
            return;
        }
        settingsObserverRegistered = true;
        final Context appContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        ContentObserver observer = new ContentObserver(mainHandler) {
            @Override
            public void onChange(boolean selfChange) {
                applyRegisteredGestureBarViews();
                applyRegisteredQuickstepGestureRegions();
            }
        };
        String[] keys = new String[] {
                KEY_ENABLED, KEY_WIDTH_PERCENT, KEY_HEIGHT_DP, KEY_OFFSET_X_DP, KEY_OFFSET_Y_DP,
                KEY_RESERVED_AREA_DP, KEY_GESTURE_AREA_DP, KEY_HIDE_ON_LAUNCHER, KEY_HIDE_IN_APPS,
                KEY_LAUNCHER_HIDE_TIMEOUT_MS, KEY_APP_HIDE_TIMEOUT_MS, KEY_HIDE_ON_LOCKSCREEN,
                KEY_REMOVE_RESERVED_AREA, KEY_ALPHA_PERCENT, KEY_FADE_IN_MS, KEY_FADE_OUT_MS,
                KEY_TINT_ENABLED, KEY_TINT_COLOR
        };
        for (String key : keys) {
            registerSettingUri(appContext, observer, key);
            registerSettingUri(appContext, observer, key + "_pine");
            registerSettingUri(appContext, observer, key + "_xposed");
        }
    }

    private void registerSettingUri(Context context, ContentObserver observer, String key) {
        try {
            context.getContentResolver().registerContentObserver(Settings.Global.getUriFor(key), false, observer);
        } catch (Throwable ignored) {
        }
    }

    private void hookLauncher(ClassLoader classLoader) {
        hookQuickstepSwipeRegion(classLoader);
        hookLauncherTaskbarInsets(classLoader);

        Class<?> stashedHandleViewClass = findClass("com.android.launcher3.taskbar.StashedHandleView", classLoader);
        if (stashedHandleViewClass != null) {
            hookAllConstructors(stashedHandleViewClass, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.thisObject instanceof View) {
                        View view = (View) param.thisObject;
                        applyLauncherHandleView(view, readConfig(view.getContext()));
                    }
                }
            });

            hookAllMethods(stashedHandleViewClass, "updateHandleColor", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.thisObject instanceof View) {
                        View view = (View) param.thisObject;
                        applyLauncherHandleView(view, readConfig(view.getContext()));
                    }
                }
            });
        }

        Class<?> stashedHandleControllerClass = findClass("com.android.launcher3.taskbar.StashedHandleViewController", classLoader);
        if (stashedHandleControllerClass != null) {
            hookAllMethods(stashedHandleControllerClass, "updateSamplingState", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    applyLauncherController(param.thisObject);
                }
            });

            hookAllMethods(stashedHandleControllerClass, "updateHandleColorOnConnectedDisplay", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    applyLauncherController(param.thisObject);
                }
            });

            hookAllMethods(stashedHandleControllerClass, "getNavHandleWidth", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Context context = param.args.length > 0 && param.args[0] instanceof Context ? (Context) param.args[0] : getContextFrom(param.thisObject);
                    Config config = readConfig(context);
                    if (config.enabled && config.widthPercent > 0 && context != null) {
                        param.setResult(desiredWidthPx(null, context, config));
                    }
                }
            });
        }

        Class<?> taskbarStashControllerClass = findClass("com.android.launcher3.taskbar.TaskbarStashController", classLoader);
        if (taskbarStashControllerClass != null) {
            hookAllMethods(taskbarStashControllerClass, "applyState", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    applyTaskbarStashController(param.thisObject);
                }
            });

            hookAllMethods(taskbarStashControllerClass, "createApplyStateAnimator", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    applyTaskbarStashController(param.thisObject);
                }
            });
        }

        Class<?> taskbarLauncherStateClass = findClass("com.android.launcher3.taskbar.TaskbarLauncherStateController", classLoader);
        if (taskbarLauncherStateClass != null) {
            hookAllMethods(taskbarLauncherStateClass, "applyState", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    applyLauncherState(param.thisObject);
                }
            });
        }
    }

    private void hookQuickstepSwipeRegion(ClassLoader classLoader) {
        Class<?> rotationTouchHelperClass = findClass("com.android.quickstep.RotationTouchHelper", classLoader);
        if (rotationTouchHelperClass == null) {
            return;
        }
        hookAllConstructors(rotationTouchHelperClass, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                registerRotationTouchHelper(param.thisObject);
                applyRotationTouchHelperGestureHeight(param.thisObject);
            }
        });
        hookAllMethods(rotationTouchHelperClass, "updateGestureTouchRegions", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                registerRotationTouchHelper(param.thisObject);
                applyRotationTouchHelperGestureHeight(param.thisObject);
            }
        });
        hookAllMethods(rotationTouchHelperClass, "onDisplayInfoChanged", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                registerRotationTouchHelper(param.thisObject);
                applyRotationTouchHelperGestureHeight(param.thisObject);
            }
        });
        hookAllMethods(rotationTouchHelperClass, "setGesturalHeight", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(final MethodHookParam param) {
                if (Boolean.TRUE.equals(XposedHelpers.getAdditionalInstanceField(param.thisObject, EXTRA_APPLYING_GESTURAL_HEIGHT))) {
                    return;
                }
                registerRotationTouchHelper(param.thisObject);
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        applyRotationTouchHelperGestureHeight(param.thisObject);
                    }
                });
            }
        });
    }

    private void hookLauncherTaskbarInsets(ClassLoader classLoader) {
        Class<?> taskbarInsetsControllerClass = findClass("com.android.launcher3.taskbar.TaskbarInsetsController", classLoader);
        if (taskbarInsetsControllerClass != null) {
            hookAllMethods(taskbarInsetsControllerClass, "getProvidedInsets", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Context context = getContextFrom(param.thisObject);
                        Config config = readConfig(context);
                        if (config.enabled && param.getResult() instanceof Object[]) {
                            adjustInsetsProviders(context, config, (Object[]) param.getResult(), Gravity.BOTTOM);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            });
            hookAllMethods(taskbarInsetsControllerClass, "setProviderInsets", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Context context = getContextFrom(param.thisObject);
                        Config config = readConfig(context);
                        if (!config.enabled || param.args.length < 1) {
                            return;
                        }
                        int gravity = param.args.length > 1 && param.args[1] instanceof Integer ? (Integer) param.args[1] : Gravity.BOTTOM;
                        adjustInsetsProvider(context, config, param.args[0], gravity);
                    } catch (Throwable ignored) {
                    }
                }
            });
            hookAllMethods(taskbarInsetsControllerClass, "onTaskbarOrBubblebarWindowHeightOrInsetsChanged", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        adjustTaskbarControllerInsets(param.thisObject);
                    } catch (Throwable ignored) {
                    }
                }
            });
        }

        Class<?> taskbarActivityContextClass = findClass("com.android.launcher3.taskbar.TaskbarActivityContext", classLoader);
        if (taskbarActivityContextClass != null) {
            hookAllMethods(taskbarActivityContextClass, "notifyUpdateLayoutParams", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        adjustTaskbarActivityContextInsets(param.thisObject);
                    } catch (Throwable ignored) {
                    }
                }
            });
            hookAllMethods(taskbarActivityContextClass, "getWindowLayoutParams", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Context context = param.thisObject instanceof Context ? (Context) param.thisObject : getContextFrom(param.thisObject);
                        Config config = readConfig(context);
                        if (config.enabled && param.getResult() instanceof WindowManager.LayoutParams) {
                            adjustLayoutParamsInsets(context, config, (WindowManager.LayoutParams) param.getResult());
                        }
                    } catch (Throwable ignored) {
                    }
                }
            });
        }
    }

    private void applyRegisteredGestureBarViews() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                synchronized (launcherHandleViews) {
                    for (View view : launcherHandleViews.keySet()) {
                        if (view == null) {
                            continue;
                        }
                        applyLauncherHandleView(view, readConfig(view.getContext()));
                        view.invalidate();
                    }
                }
            }
        });
    }

    private void applyRegisteredQuickstepGestureRegions() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                synchronized (rotationTouchHelpers) {
                    for (Object helper : rotationTouchHelpers.keySet()) {
                        applyRotationTouchHelperGestureHeight(helper);
                    }
                }
            }
        });
    }

    private String readFocusedPackageFromContext(Object owner, Context context) {
        if (context == null) {
            return null;
        }
        String packageName = readFocusedPackageFromActivityTaskManager(owner, context, true);
        if (packageName != null) {
            return packageName;
        }
        try {
            ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (activityManager == null) {
                return null;
            }
            List<ActivityManager.RunningTaskInfo> tasks = activityManager.getRunningTasks(5);
            if (tasks == null || tasks.isEmpty()) {
                return null;
            }
            String homeFallback = null;
            for (ActivityManager.RunningTaskInfo task : tasks) {
                String taskPackageName = extractPackageName(new Object[]{task}, true);
                if (taskPackageName != null) {
                    if (HOME_TASK_PACKAGE.equals(taskPackageName)) {
                        if (homeFallback == null) {
                            homeFallback = taskPackageName;
                        }
                        continue;
                    }
                    return taskPackageName;
                }
            }
            return homeFallback;
        } catch (Throwable ignored) {
        }
        return null;
    }

    private String readFocusedPackageFromActivityTaskManager(Object navigationBar, Context context, boolean preferRealActivity) {
        try {
            Class<?> activityTaskManagerClass = Class.forName("android.app.ActivityTaskManager");
            Object service = XposedHelpers.callStaticMethod(activityTaskManagerClass, "getService");
            Object focusedRootTask = XposedHelpers.callMethod(service, "getFocusedRootTaskInfo");
            String packageName = extractPackageName(new Object[]{focusedRootTask}, preferRealActivity);
            if (packageName != null && !HOME_TASK_PACKAGE.equals(packageName)) {
                return packageName;
            }
        } catch (Throwable ignored) {
        }
        try {
            Object activityTaskManager = context.getSystemService("activity_task");
            Object tasks = XposedHelpers.callMethod(activityTaskManager, "getTasks", 1);
            String packageName = extractPackageNameFromTaskList(tasks, preferRealActivity);
            if (packageName != null) {
                return packageName;
            }
        } catch (Throwable ignored) {
        }
        try {
            Class<?> activityTaskManagerClass = Class.forName("android.app.ActivityTaskManager");
            Object service = XposedHelpers.callStaticMethod(activityTaskManagerClass, "getService");
            Object tasks = XposedHelpers.callMethod(service, "getTasks", 1, false, false, -1);
            return extractPackageNameFromTaskList(tasks, preferRealActivity);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String extractPackageNameFromTaskList(Object tasks, boolean preferRealActivity) {
        if (!(tasks instanceof List)) {
            return null;
        }
        String homeFallback = null;
        for (Object task : (List<?>) tasks) {
            String packageName = extractPackageName(new Object[]{task}, preferRealActivity);
            if (packageName != null) {
                if (HOME_TASK_PACKAGE.equals(packageName)) {
                    if (homeFallback == null) {
                        homeFallback = packageName;
                    }
                    continue;
                }
                return packageName;
            }
        }
        return homeFallback;
    }

    private void adjustLayoutParamsInsets(Context context, Config config, WindowManager.LayoutParams params) {
        if (params == null || config == null || !config.enabled) {
            return;
        }
        Object providedInsets = getObjectField(params, "providedInsets");
        if (providedInsets instanceof Object[]) {
            adjustInsetsProviders(context, config, (Object[]) providedInsets, params.gravity);
            adjustNavigationWindowFrame(context, config, params);
        }
        Object rotationParams = getObjectField(params, "paramsForRotation");
        if (rotationParams instanceof WindowManager.LayoutParams[]) {
            WindowManager.LayoutParams[] rotationParamArray = (WindowManager.LayoutParams[]) rotationParams;
            for (WindowManager.LayoutParams rotationParam : rotationParamArray) {
                if (rotationParam == null) {
                    continue;
                }
                Object rotationInsets = getObjectField(rotationParam, "providedInsets");
                if (rotationInsets instanceof Object[]) {
                    adjustInsetsProviders(context, config, (Object[]) rotationInsets, rotationParam.gravity);
                    adjustNavigationWindowFrame(context, config, rotationParam);
                }
            }
        }
    }

    private void adjustInsetsProviders(Context context, Config config, Object[] providers, int gravity) {
        if (providers == null) {
            return;
        }
        for (Object provider : providers) {
            adjustInsetsProvider(context, config, provider, gravity);
        }
    }

    private void adjustInsetsProvider(Context context, Config config, Object provider, int gravity) {
        if (provider == null || config == null || !config.enabled) {
            return;
        }
        int type = getInsetsProviderType(provider, -1);
        int reservedPx = config.removeReservedArea || config.reservedAreaDp <= 0 ? 0 : dp(context, config.reservedAreaDp);
        int gesturePx = config.gestureAreaDp <= 0 ? 0 : dp(context, config.gestureAreaDp);
        if (type == WindowInsets.Type.navigationBars() || type == WindowInsets.Type.tappableElement()) {
            setInsetsSize(provider, insetsForGravity(reservedPx, gravity));
        } else if (type == WindowInsets.Type.mandatorySystemGestures()) {
            setInsetsSize(provider, insetsForGravity(gesturePx, gravity));
        }
    }

    private void adjustTaskbarControllerInsets(Object controller) {
        if (controller == null) {
            return;
        }
        Context context = getContextFrom(controller);
        Config config = readConfig(context);
        if (!config.enabled) {
            return;
        }
        Object layoutParams = getObjectField(controller, "windowLayoutParams");
        if (layoutParams instanceof WindowManager.LayoutParams) {
            adjustLayoutParamsInsets(context, config, (WindowManager.LayoutParams) layoutParams);
        }
    }

    private void adjustTaskbarActivityContextInsets(Object taskbarContext) {
        if (taskbarContext == null) {
            return;
        }
        Context context = taskbarContext instanceof Context ? (Context) taskbarContext : getContextFrom(taskbarContext);
        Config config = readConfig(context);
        if (!config.enabled) {
            return;
        }
        Object layoutParams = getObjectField(taskbarContext, "mWindowLayoutParams");
        if (layoutParams instanceof WindowManager.LayoutParams) {
            adjustLayoutParamsInsets(context, config, (WindowManager.LayoutParams) layoutParams);
        }
    }

    private void adjustNavigationWindowFrame(Context context, Config config, WindowManager.LayoutParams params) {
        if (params == null || config == null || !config.enabled || !isNavigationInsetsWindowType(params.type)) {
            return;
        }
        if ((params.gravity & Gravity.BOTTOM) != Gravity.BOTTOM || params.height <= 0) {
            return;
        }
        Integer originalHeight = originalNavigationWindowHeights.get(params);
        if (originalHeight == null || originalHeight <= 0) {
            originalHeight = params.height;
            originalNavigationWindowHeights.put(params, originalHeight);
        }
        int extraTop = config.offsetYDp < 0 ? dp(context, -config.offsetYDp) : 0;
        int handleHeight = Math.max(0, dp(context, config.heightDp));
        int gestureHeight = config.gestureAreaDp > 0 ? dp(context, config.gestureAreaDp) : 0;
        int visualHeight = Math.max(originalHeight, handleHeight) + extraTop;
        int targetHeight = Math.max(originalHeight, Math.max(visualHeight, gestureHeight));
        params.height = targetHeight;
    }

    private void registerRotationTouchHelper(Object helper) {
        if (helper == null) {
            return;
        }
        rotationTouchHelpers.put(helper, Boolean.TRUE);
        Context context = getContextFrom(helper);
        if (context != null) {
            registerSettingsObserver(context);
        }
    }

    private void applyRotationTouchHelperGestureHeight(Object helper) {
        if (helper == null) {
            return;
        }
        Context context = getContextFrom(helper);
        Config config = readConfig(context);
        int gestureHeightPx = config.enabled ? Math.max(0, dp(context, config.gestureAreaDp)) : -1;
        XposedHelpers.setAdditionalInstanceField(helper, EXTRA_APPLYING_GESTURAL_HEIGHT, Boolean.TRUE);
        try {
            XposedHelpers.callMethod(helper, "setGesturalHeight", gestureHeightPx);
        } catch (Throwable ignored) {
        } finally {
            XposedHelpers.setAdditionalInstanceField(helper, EXTRA_APPLYING_GESTURAL_HEIGHT, Boolean.FALSE);
        }
    }

    private void applyLauncherController(Object controller) {
        View view = getViewField(controller, "mStashedHandleView");
        disableParentClipping(view);
        Context context = view != null ? view.getContext() : getContextFrom(controller);
        Config config = readConfig(context);
        if (!config.enabled || view == null) {
            return;
        }
        applyLauncherHandleView(view, config);
        applyLauncherControllerAlpha(controller, config);
        int width = desiredWidthPx(view, view.getContext(), config);
        int height = Math.max(1, dp(view.getContext(), config.heightDp));
        setIntField(controller, "mStashedHandleWidth", width);
        setIntField(controller, "mStashedHandleHeight", height);
        setFloatField(controller, "mStashedHandleRadius", height / 2f);
        Object bounds = getObjectField(controller, "mStashedHandleBounds");
        if (bounds instanceof Rect) {
            ((Rect) bounds).set(0, 0, Math.max(1, view.getWidth() > 0 ? view.getWidth() : width), height);
        }
    }

    private void applyLauncherState(Object stateController) {
        Object controllers = getObjectField(stateController, "mControllers");
        Object stashedController = getObjectField(controllers, "stashedHandleViewController");
        View view = getViewField(stashedController, "mStashedHandleView");
        Context context = view != null ? view.getContext() : getContextFrom(stateController);
        Config config = readConfig(context);
        if (!config.enabled || view == null) {
            return;
        }
        applyLauncherController(stashedController);
        applyLauncherAlpha(view, config, false);
    }

    private void applyLauncherHandleView(View view, Config config) {
        if (view == null || !config.enabled) {
            return;
        }
        registerSettingsObserver(view.getContext());
        markLauncherHandle(view);
        int desiredWidth = Math.max(1, desiredWidthPx(view, view.getContext(), config));
        int desiredHeight = Math.max(1, dp(view.getContext(), config.heightDp));
        String signature = "launcher:" + desiredWidth + ':' + desiredHeight + ':' + config.offsetXDp + ':'
                + config.offsetYDp + ':' + config.tintEnabled + ':' + config.tintColor;
        boolean signatureChanged = !signature.equals(appliedViewSignatures.get(view));
        if (signatureChanged) {
            ViewGroup.LayoutParams params = view.getLayoutParams();
            if (params != null) {
                params.width = desiredWidth;
                params.height = desiredHeight;
                if (params instanceof FrameLayout.LayoutParams) {
                    ((FrameLayout.LayoutParams) params).gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
                }
                view.setLayoutParams(params);
            }
            appliedViewSignatures.put(view, signature);
        }
        view.setTranslationX(dpF(view.getContext(), config.offsetXDp));
        view.setTranslationY(dpF(view.getContext(), config.offsetYDp));
        disableParentClipping(view);
        view.setElevation(0f);
        view.setTranslationZ(0f);
        applyLauncherBackground(view, config, desiredHeight);
        applyLauncherAlpha(view, config, false, signatureChanged);
    }

    private void applyLauncherBackground(View view, Config config, int desiredHeight) {
        if (view == null || config == null || !config.tintEnabled) {
            return;
        }
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(config.tintColor);
        drawable.setCornerRadius(desiredHeight / 2f);
        view.setBackground(drawable);
    }

    private void applyLauncherAlpha(View view, Config config, boolean animate) {
        applyLauncherAlpha(view, config, animate, false);
    }

    private void applyLauncherAlpha(View view, Config config, boolean animate, boolean fadeFromZero) {
        if (view == null || !config.enabled) {
            return;
        }
        float targetAlpha = resolveLauncherTargetAlpha(view.getContext(), config, view);
        applyLauncherViewAlpha(view, config, targetAlpha, animate, fadeFromZero);
    }

    private void applyLauncherControllerAlpha(Object controller, Config config) {
        if (controller == null || config == null || !config.enabled) {
            return;
        }
        Object multiValueAlpha = getObjectField(controller, "mTaskbarStashedHandleAlpha");
        float targetAlpha = resolveLauncherTargetAlpha(getContextFrom(controller), config, controller);
        applyLauncherMultiValueAlpha(controller, multiValueAlpha, config, targetAlpha);
    }

    private void applyTaskbarStashController(Object controller) {
        Config config = readConfig(getContextFrom(controller));
        if (controller == null || config == null || !config.enabled) {
            return;
        }
        float targetAlpha = resolveLauncherTargetAlpha(getContextFrom(controller), config, controller);
        if (targetAlpha > 0f) {
            cancelPendingLauncherHide(controller);
        }
    }

    private void applyLauncherViewAlpha(final View view, final Config config, final float targetAlpha,
            boolean animate, boolean fadeFromZero) {
        DelayedHideDecision delayedHide = resolveDelayedLauncherHide(view.getContext(), config, view, targetAlpha);
        if (delayedHide != null) {
            cancelLauncherRestore(view);
            if (isLauncherHideInProgressFor(view, delayedHide.key) || isPendingLauncherHideRequest(view, delayedHide.key)) {
                return;
            }
            if (isLauncherHideInProgress(view)) {
                cancelPendingLauncherHide(view);
            }
            float visibleAlpha = launcherVisibleAlpha(config);
            if (fadeFromZero || shouldFadeViewAlphaFromZero(view, visibleAlpha)) {
                setViewAlphaFromZero(view, visibleAlpha, fadeInMs(config));
            } else {
                setViewAlpha(view, visibleAlpha, false);
            }
            scheduleDelayedLauncherHide(view, view.getContext(), delayedHide, new Runnable() {
                @Override
                public void run() {
                    setViewAlpha(view, 0f, true, fadeOutMs(config));
                }
            });
            return;
        }
        if (isLauncherRestoreInProgress(view)) {
            return;
        }
        boolean restoreFromHidden = isLauncherHideInProgress(view);
        cancelPendingLauncherHide(view);
        if (restoreFromHidden) {
            markLauncherRestoreInProgress(view);
        }
        if ((fadeFromZero || shouldFadeViewAlphaFromZero(view, targetAlpha)) && !restoreFromHidden) {
            setViewAlphaFromZero(view, targetAlpha, fadeInMs(config));
            return;
        }
        setViewAlpha(view, targetAlpha, animate || restoreFromHidden, fadeInMs(config));
    }

    private void applyLauncherMultiValueAlpha(final Object owner, final Object multiValueAlpha,
            final Config config, final float targetAlpha) {
        DelayedHideDecision delayedHide = resolveDelayedLauncherHide(getContextFrom(owner), config, owner, targetAlpha);
        if (delayedHide != null) {
            cancelLauncherRestore(owner);
            if (isLauncherHideInProgressFor(owner, delayedHide.key) || isPendingLauncherHideRequest(owner, delayedHide.key)) {
                return;
            }
            if (isLauncherHideInProgress(owner)) {
                cancelPendingLauncherHide(owner);
            }
            cancelLauncherAlphaAnimation(owner);
            float visibleAlpha = launcherVisibleAlpha(config);
            if (shouldFadeLauncherOwnerAlphaFromZero(owner, visibleAlpha)) {
                animateLauncherMultiValueAlpha(owner, multiValueAlpha, 0f, visibleAlpha, fadeInMs(config));
            } else {
                rememberLauncherOwnerAlpha(owner, visibleAlpha);
                setLauncherMultiValueAlpha(multiValueAlpha, visibleAlpha);
            }
            scheduleDelayedLauncherHide(owner, getContextFrom(owner), delayedHide, new Runnable() {
                @Override
                public void run() {
                    animateLauncherMultiValueAlpha(owner, multiValueAlpha,
                            launcherVisibleAlpha(config), 0f, fadeOutMs(config));
                }
            });
            return;
        }
        if (isLauncherRestoreInProgress(owner)) {
            return;
        }
        boolean restoreFromHidden = isLauncherHideInProgress(owner);
        cancelPendingLauncherHide(owner);
        if (restoreFromHidden) {
            markLauncherRestoreInProgress(owner);
            animateLauncherMultiValueAlpha(owner, multiValueAlpha, 0f, targetAlpha, fadeInMs(config));
        } else if (shouldFadeLauncherOwnerAlphaFromZero(owner, targetAlpha)) {
            cancelLauncherAlphaAnimation(owner);
            animateLauncherMultiValueAlpha(owner, multiValueAlpha, 0f, targetAlpha, fadeInMs(config));
        } else {
            cancelLauncherAlphaAnimation(owner);
            rememberLauncherOwnerAlpha(owner, targetAlpha);
            setLauncherMultiValueAlpha(multiValueAlpha, targetAlpha);
        }
    }

    private float launcherVisibleAlpha(Config config) {
        return config != null ? config.alphaPercent / 100f : 1f;
    }

    private DelayedHideDecision resolveDelayedLauncherHide(Context context, Config config,
            Object owner, float targetAlpha) {
        if (config == null || !config.enabled || targetAlpha > 0.01f
                || config.widthPercent <= 0 || config.heightDp <= 0 || config.alphaPercent <= 0) {
            return null;
        }
        Context effectiveContext = context != null ? context : getContextFrom(owner);
        if (config.hideOnLockscreen && isKeyguardShowing(effectiveContext)) {
            return null;
        }
        String packageName = readFocusedPackageFromContext(owner, effectiveContext);
        if (config.hideOnLauncher && isLauncherPackage(packageName)) {
            return new DelayedHideDecision("launcher", packageName, config.launcherHideTimeoutMs);
        }
        if (config.hideInApps && isAppPackage(packageName)) {
            return new DelayedHideDecision("app", packageName, config.appHideTimeoutMs);
        }
        return null;
    }

    private void scheduleDelayedLauncherHide(final Object owner, final Context context,
            final DelayedHideDecision delayedHide, final Runnable hideAction) {
        if (owner == null || delayedHide == null || hideAction == null) {
            return;
        }
        if (isLauncherHideInProgressFor(owner, delayedHide.key)) {
            return;
        }
        synchronized (pendingLauncherHideRunnables) {
            Runnable oldRunnable = pendingLauncherHideRunnables.get(owner);
            String oldKey = pendingLauncherHideKeys.get(owner);
            if (oldRunnable != null && delayedHide.key.equals(oldKey)) {
                return;
            }
            if (oldRunnable != null) {
                mainHandler.removeCallbacks(oldRunnable);
                pendingLauncherHideRunnables.remove(owner);
                pendingLauncherHideKeys.remove(owner);
            }
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    synchronized (pendingLauncherHideRunnables) {
                        pendingLauncherHideRunnables.remove(owner);
                        pendingLauncherHideKeys.remove(owner);
                    }
                    Context currentContext = context != null ? context : getContextFrom(owner);
                    Config latestConfig = readConfig(currentContext);
                    String packageName = readFocusedPackageFromContext(owner, currentContext);
                    if (isDelayedHideStillValid(latestConfig, delayedHide, packageName)) {
                        fadingLauncherHideOwners.put(owner, Boolean.TRUE);
                        fadingLauncherHideKeys.put(owner, delayedHide.key);
                        activeLauncherHideOwners.remove(owner);
                        activeLauncherHideKeys.remove(owner);
                        hideAction.run();
                        mainHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (Boolean.TRUE.equals(fadingLauncherHideOwners.get(owner))
                                        && delayedHide.key.equals(fadingLauncherHideKeys.get(owner))) {
                                    fadingLauncherHideOwners.remove(owner);
                                    fadingLauncherHideKeys.remove(owner);
                                    activeLauncherHideOwners.put(owner, Boolean.TRUE);
                                    activeLauncherHideKeys.put(owner, delayedHide.key);
                                }
                            }
                        }, fadeOutMs(latestConfig));
                    } else {
                        fadingLauncherHideOwners.remove(owner);
                        fadingLauncherHideKeys.remove(owner);
                        activeLauncherHideOwners.remove(owner);
                        activeLauncherHideKeys.remove(owner);
                    }
                }
            };
            pendingLauncherHideRunnables.put(owner, runnable);
            pendingLauncherHideKeys.put(owner, delayedHide.key);
            mainHandler.postDelayed(runnable, delayedHide.delayMs);
        }
    }

    private boolean isDelayedHideStillValid(Config config, DelayedHideDecision delayedHide, String packageName) {
        if (config == null || delayedHide == null || !config.enabled
                || config.widthPercent <= 0 || config.heightDp <= 0 || config.alphaPercent <= 0
                || !sameString(delayedHide.packageName, packageName)) {
            return false;
        }
        if ("launcher".equals(delayedHide.scope)) {
            return config.hideOnLauncher && isLauncherPackage(packageName);
        }
        if ("app".equals(delayedHide.scope)) {
            return config.hideInApps && isAppPackage(packageName);
        }
        return false;
    }

    private void cancelPendingLauncherHide(Object owner) {
        if (owner == null) {
            return;
        }
        Runnable runnable;
        synchronized (pendingLauncherHideRunnables) {
            runnable = pendingLauncherHideRunnables.remove(owner);
            pendingLauncherHideKeys.remove(owner);
        }
        if (runnable != null) {
            mainHandler.removeCallbacks(runnable);
        }
        fadingLauncherHideOwners.remove(owner);
        fadingLauncherHideKeys.remove(owner);
        activeLauncherHideOwners.remove(owner);
        activeLauncherHideKeys.remove(owner);
        cancelLauncherAlphaAnimation(owner);
    }

    private void markLauncherRestoreInProgress(final Object owner) {
        if (owner == null) {
            return;
        }
        fadingLauncherRestoreOwners.put(owner, Boolean.TRUE);
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                fadingLauncherRestoreOwners.remove(owner);
            }
        }, LAUNCHER_HIDE_FADE_MS);
    }

    private void cancelLauncherRestore(Object owner) {
        if (owner != null) {
            fadingLauncherRestoreOwners.remove(owner);
        }
    }

    private boolean isLauncherRestoreInProgress(Object owner) {
        return owner != null && Boolean.TRUE.equals(fadingLauncherRestoreOwners.get(owner));
    }

    private boolean isLauncherHideInProgress(Object owner) {
        return owner != null
                && (Boolean.TRUE.equals(activeLauncherHideOwners.get(owner))
                || Boolean.TRUE.equals(fadingLauncherHideOwners.get(owner)));
    }

    private boolean isLauncherHideInProgressFor(Object owner, String key) {
        return owner != null && key != null
                && (key.equals(activeLauncherHideKeys.get(owner))
                || key.equals(fadingLauncherHideKeys.get(owner)));
    }

    private boolean isPendingLauncherHideRequest(Object owner, String key) {
        return owner != null && key != null && key.equals(pendingLauncherHideKeys.get(owner));
    }

    private void animateLauncherMultiValueAlpha(final Object owner, final Object multiValueAlpha,
            float fromAlpha, float targetAlpha, long durationMs) {
        rememberLauncherOwnerAlpha(owner, targetAlpha);
        animateLauncherAlphaValue(owner, fromAlpha, targetAlpha, durationMs, new AlphaValueApplier() {
            @Override
            public void apply(float alpha) {
                setLauncherMultiValueAlpha(multiValueAlpha, alpha);
            }
        });
    }

    private void animateLauncherAlphaValue(final Object owner, float fromAlpha, float targetAlpha,
            long durationMs, final AlphaValueApplier applier) {
        if (applier == null) {
            return;
        }
        if (owner == null) {
            applier.apply(targetAlpha);
            return;
        }
        final Object token = new Object();
        launcherAlphaAnimationTokens.put(owner, token);
        final long startTime = System.currentTimeMillis();
        final long duration = Math.max(1L, durationMs);
        final float startAlpha = clampFloat(fromAlpha, 0f, 1f);
        final float endAlpha = clampFloat(targetAlpha, 0f, 1f);
        Runnable animationFrame = new Runnable() {
            @Override
            public void run() {
                if (launcherAlphaAnimationTokens.get(owner) != token) {
                    return;
                }
                float progress = Math.min(1f, (System.currentTimeMillis() - startTime) / (float) duration);
                float easedProgress = progress * progress * (3f - 2f * progress);
                applier.apply(startAlpha + ((endAlpha - startAlpha) * easedProgress));
                if (progress >= 1f) {
                    launcherAlphaAnimationTokens.remove(owner);
                } else {
                    mainHandler.postDelayed(this, 16L);
                }
            }
        };
        animationFrame.run();
    }

    private void cancelLauncherAlphaAnimation(Object owner) {
        if (owner != null) {
            launcherAlphaAnimationTokens.remove(owner);
        }
    }

    private boolean shouldFadeLauncherOwnerAlphaFromZero(Object owner, float targetAlpha) {
        if (owner == null || targetAlpha <= 0.01f) {
            return false;
        }
        Float lastTarget = appliedLauncherOwnerAlphas.get(owner);
        return lastTarget == null || lastTarget <= 0.01f;
    }

    private void rememberLauncherOwnerAlpha(Object owner, float alpha) {
        if (owner != null) {
            appliedLauncherOwnerAlphas.put(owner, clampFloat(alpha, 0f, 1f));
        }
    }

    private float resolveLauncherTargetAlpha(Context context, Config config, Object owner) {
        Context effectiveContext = context != null ? context : getContextFrom(owner);
        if (config == null || config.widthPercent <= 0 || config.heightDp <= 0 || config.alphaPercent <= 0) {
            return 0f;
        }
        if (config.hideOnLockscreen && isKeyguardShowing(effectiveContext)) {
            return 0f;
        }
        if (config.hideOnLauncher) {
            String packageName = readFocusedPackageFromContext(owner, effectiveContext);
            if (isLauncherPackage(packageName)) {
                return 0f;
            }
            if (config.hideInApps && isAppPackage(packageName)) {
                return 0f;
            }
            return config.alphaPercent / 100f;
        }
        if (config.hideInApps) {
            String packageName = readFocusedPackageFromContext(owner, effectiveContext);
            if (isAppPackage(packageName)) {
                return 0f;
            }
        }
        return config.alphaPercent / 100f;
    }

    private long fadeInMs(Config config) {
        return config != null ? config.fadeInMs : VIEW_ALPHA_ANIMATION_MS;
    }

    private long fadeOutMs(Config config) {
        return config != null ? config.fadeOutMs : LAUNCHER_HIDE_FADE_MS;
    }

    private void setLauncherMultiValueAlpha(Object multiValueAlpha, float alpha) {
        if (multiValueAlpha == null) {
            return;
        }
        try {
            Object alphaProperty = XposedHelpers.callMethod(multiValueAlpha, "get", 5);
            setAlphaProperty(alphaProperty, alpha);
        } catch (Throwable ignored) {
        }
    }

    private void setAlphaProperty(Object alphaProperty, float alpha) {
        if (alphaProperty == null) {
            return;
        }
        try {
            XposedHelpers.callMethod(alphaProperty, "setValue", clampFloat(alpha, 0f, 1f));
        } catch (Throwable ignored) {
        }
    }

    private void markLauncherHandle(View view) {
        if (view != null) {
            launcherHandleViews.put(view, Boolean.TRUE);
        }
    }

    private Config readConfig(Context context) {
        Config config = new Config();
        if (context == null) {
            return config;
        }
        config.enabled = isSettingEnabled(context, KEY_ENABLED, false);
        config.widthPercent = clamp(getIntSetting(context, KEY_WIDTH_PERCENT, DEFAULT_WIDTH_PERCENT), 0, 100);
        config.heightDp = clamp(getIntSetting(context, KEY_HEIGHT_DP, DEFAULT_HEIGHT_DP), 0, 64);
        config.offsetXDp = clamp(getIntSetting(context, KEY_OFFSET_X_DP, 0), -400, 400);
        config.offsetYDp = clamp(getIntSetting(context, KEY_OFFSET_Y_DP, 0), -200, 200);
        config.reservedAreaDp = clamp(getIntSetting(context, KEY_RESERVED_AREA_DP, DEFAULT_RESERVED_AREA_DP), 0, 160);
        config.gestureAreaDp = clamp(getIntSetting(context, KEY_GESTURE_AREA_DP, DEFAULT_GESTURE_AREA_DP), 0, 160);
        config.hideOnLauncher = isSettingEnabled(context, KEY_HIDE_ON_LAUNCHER, false);
        config.hideInApps = isSettingEnabled(context, KEY_HIDE_IN_APPS, false);
        config.launcherHideTimeoutMs = clamp(getIntSetting(context, KEY_LAUNCHER_HIDE_TIMEOUT_MS, DEFAULT_HIDE_TIMEOUT_MS), 0, 5000);
        config.appHideTimeoutMs = clamp(getIntSetting(context, KEY_APP_HIDE_TIMEOUT_MS, DEFAULT_HIDE_TIMEOUT_MS), 0, 5000);
        config.hideOnLockscreen = isSettingEnabled(context, KEY_HIDE_ON_LOCKSCREEN, false);
        config.removeReservedArea = isSettingEnabled(context, KEY_REMOVE_RESERVED_AREA, false);
        config.alphaPercent = clamp(getIntSetting(context, KEY_ALPHA_PERCENT, 100), 0, 100);
        config.fadeInMs = clamp(getIntSetting(context, KEY_FADE_IN_MS, DEFAULT_FADE_MS), MIN_FADE_MS, MAX_FADE_MS);
        config.fadeOutMs = clamp(getIntSetting(context, KEY_FADE_OUT_MS, DEFAULT_FADE_MS), MIN_FADE_MS, MAX_FADE_MS);
        config.tintEnabled = isSettingEnabled(context, KEY_TINT_ENABLED, false);
        config.tintColor = getIntSetting(context, KEY_TINT_COLOR, DEFAULT_TINT_COLOR);
        return config;
    }

    private int desiredWidthPx(View view, Context context, Config config) {
        int parentWidth = view != null ? parentWidth(view) : 0;
        if (parentWidth <= 0 && context != null) {
            parentWidth = context.getResources().getDisplayMetrics().widthPixels;
        }
        if (parentWidth <= 0) {
            return 1;
        }
        return Math.max(1, Math.round(parentWidth * (config.widthPercent / 100f)));
    }

    private int parentWidth(View view) {
        if (view == null) {
            return 0;
        }
        Object parent = view.getParent();
        if (parent instanceof View && ((View) parent).getWidth() > 0) {
            return ((View) parent).getWidth();
        }
        View root = view.getRootView();
        return root != null ? root.getWidth() : 0;
    }

    private void setViewAlphaFromZero(View view, float alpha, long durationMs) {
        if (view == null) {
            return;
        }
        float target = clampFloat(alpha, 0f, 1f);
        view.animate().cancel();
        appliedViewAlphas.remove(view);
        view.setVisibility(target > 0f ? View.VISIBLE : View.INVISIBLE);
        view.setAlpha(0f);
        setViewAlpha(view, target, target > 0f, durationMs);
    }

    private boolean shouldFadeViewAlphaFromZero(View view, float targetAlpha) {
        if (view == null || targetAlpha <= 0.01f) {
            return false;
        }
        Float lastTarget = appliedViewAlphas.get(view);
        return lastTarget != null && lastTarget <= 0.01f && view.getAlpha() <= 0.05f;
    }

    private void setViewAlpha(final View view, final float alpha, boolean animate) {
        setViewAlpha(view, alpha, animate, VIEW_ALPHA_ANIMATION_MS);
    }

    private void setViewAlpha(final View view, final float alpha, boolean animate, long durationMs) {
        if (view == null) {
            return;
        }
        float target = clampFloat(alpha, 0f, 1f);
        Float lastTarget = appliedViewAlphas.get(view);
        if (lastTarget != null
                && Math.abs(lastTarget - target) < 0.01f
                && Math.abs(view.getAlpha() - target) < 0.02f
                && view.getVisibility() == (target > 0f ? View.VISIBLE : View.INVISIBLE)) {
            return;
        }
        appliedViewAlphas.put(view, target);
        view.animate().cancel();
        if (!animate) {
            view.setAlpha(target);
            view.setVisibility(target > 0f ? View.VISIBLE : View.INVISIBLE);
            return;
        }
        if (target > 0f) {
            view.setVisibility(View.VISIBLE);
        }
        view.animate()
                .alpha(target)
                .setDuration(durationMs)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        view.setVisibility(target > 0f ? View.VISIBLE : View.INVISIBLE);
                    }
                })
                .start();
    }

    private boolean isKeyguardShowing(Context context) {
        if (context == null) {
            return false;
        }
        try {
            Object service = context.getSystemService(Context.KEYGUARD_SERVICE);
            if (service instanceof KeyguardManager) {
                KeyguardManager keyguardManager = (KeyguardManager) service;
                try {
                    if (keyguardManager.inKeyguardRestrictedInputMode()) {
                        return true;
                    }
                } catch (Throwable ignored) {
                }
                if (keyguardManager.isKeyguardLocked()) {
                    return true;
                }
                try {
                    return keyguardManager.isDeviceLocked();
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            Object service = context.getSystemService(Context.POWER_SERVICE);
            if (service instanceof PowerManager && !((PowerManager) service).isInteractive()) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private boolean isLauncherPackage(String packageName) {
        return HOME_TASK_PACKAGE.equals(packageName)
                || PACKAGE_NEXUS_LAUNCHER.equals(packageName)
                || PACKAGE_PIXEL_LAUNCHER.equals(packageName)
                || PACKAGE_AOSP_LAUNCHER.equals(packageName);
    }

    private boolean isAppPackage(String packageName) {
        return packageName != null
                && !PACKAGE_SYSTEMUI.equals(packageName)
                && !isLauncherPackage(packageName);
    }

    private String extractPackageName(Object[] args, boolean preferRealActivity) {
        if (args == null) {
            return null;
        }
        String homeFallback = null;
        for (Object arg : args) {
            if (arg instanceof ComponentName) {
                return ((ComponentName) arg).getPackageName();
            }
            ComponentName topActivity = getComponentField(arg, "topActivity");
            if (topActivity != null) {
                return topActivity.getPackageName();
            }
            ComponentName baseActivity = getComponentField(arg, "baseActivity");
            if (baseActivity != null) {
                return baseActivity.getPackageName();
            }
            ComponentName baseIntentComponent = getBaseIntentComponent(arg);
            if (baseIntentComponent != null) {
                return baseIntentComponent.getPackageName();
            }
            if (isHomeTask(arg)) {
                if (!preferRealActivity) {
                    return HOME_TASK_PACKAGE;
                }
                homeFallback = HOME_TASK_PACKAGE;
            }
        }
        return homeFallback;
    }

    private boolean isHomeTask(Object object) {
        if (object == null) {
            return false;
        }
        try {
            Object activityType = XposedHelpers.callMethod(object, "getActivityType");
            if (activityType instanceof Integer && (Integer) activityType == ACTIVITY_TYPE_HOME) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        if (getIntField(object, "topActivityType", -1) == ACTIVITY_TYPE_HOME) {
            return true;
        }
        Object configuration = getObjectField(object, "configuration");
        Object windowConfiguration = getObjectField(configuration, "windowConfiguration");
        if (windowConfiguration != null) {
            try {
                Object activityType = XposedHelpers.callMethod(windowConfiguration, "getActivityType");
                return activityType instanceof Integer && (Integer) activityType == ACTIVITY_TYPE_HOME;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    private ComponentName getBaseIntentComponent(Object object) {
        Object baseIntent = getObjectField(object, "baseIntent");
        if (baseIntent instanceof Intent) {
            return ((Intent) baseIntent).getComponent();
        }
        return null;
    }

    private ComponentName getComponentField(Object object, String fieldName) {
        Object value = getObjectField(object, fieldName);
        return value instanceof ComponentName ? (ComponentName) value : null;
    }

    private Context getContextFrom(Object object) {
        if (object instanceof View) {
            return ((View) object).getContext();
        }
        Object context = getObjectField(object, "mContext");
        if (context instanceof Context) {
            return (Context) context;
        }
        context = getObjectField(object, "mWindowContext");
        if (context instanceof Context) {
            return (Context) context;
        }
        context = getObjectField(object, "context");
        if (context instanceof Context) {
            return (Context) context;
        }
        Object activity = getObjectField(object, "mActivity");
        if (activity instanceof Context) {
            return (Context) activity;
        }
        Object controllers = getObjectField(object, "mControllers");
        Object taskbarContext = getObjectField(controllers, "taskbarActivityContext");
        if (taskbarContext instanceof Context) {
            return (Context) taskbarContext;
        }
        Object view = getObjectField(object, "mView");
        if (view instanceof View) {
            return ((View) view).getContext();
        }
        return null;
    }

    private View getViewField(Object object, String fieldName) {
        Object value = getObjectField(object, fieldName);
        return value instanceof View ? (View) value : null;
    }

    private Object getObjectField(Object object, String fieldName) {
        if (object == null) {
            return null;
        }
        try {
            return XposedHelpers.getObjectField(object, fieldName);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private int getIntField(Object object, String fieldName, int defaultValue) {
        try {
            return XposedHelpers.getIntField(object, fieldName);
        } catch (Throwable ignored) {
            return defaultValue;
        }
    }

    private void setIntField(Object object, String fieldName, int value) {
        try {
            XposedHelpers.setIntField(object, fieldName, value);
        } catch (Throwable ignored) {
        }
    }

    private void setFloatField(Object object, String fieldName, float value) {
        try {
            XposedHelpers.setFloatField(object, fieldName, value);
        } catch (Throwable ignored) {
        }
    }

    private boolean isNavigationInsetsWindowType(int type) {
        return type == WINDOW_TYPE_NAVIGATION_BAR || type == WINDOW_TYPE_NAVIGATION_BAR_PANEL;
    }

    private int getInsetsProviderType(Object provider, int defaultValue) {
        if (provider == null) {
            return defaultValue;
        }
        try {
            Object type = XposedHelpers.callMethod(provider, "getType");
            return type instanceof Integer ? (Integer) type : defaultValue;
        } catch (Throwable ignored) {
            return defaultValue;
        }
    }

    private Insets insetsForGravity(int inset, int gravity) {
        int safeInset = Math.max(0, inset);
        if ((gravity & Gravity.BOTTOM) == Gravity.BOTTOM) {
            return safeInset > 0 ? Insets.of(0, 0, 0, safeInset) : Insets.NONE;
        }
        boolean start = (gravity & Gravity.START) == Gravity.START || (gravity & Gravity.LEFT) == Gravity.LEFT;
        if (start) {
            return safeInset > 0 ? Insets.of(safeInset, 0, 0, 0) : Insets.NONE;
        }
        return safeInset > 0 ? Insets.of(0, 0, safeInset, 0) : Insets.NONE;
    }

    private void disableParentClipping(View view) {
        View current = view;
        for (int depth = 0; depth < 6 && current != null; depth++) {
            ViewParent parent = current.getParent();
            if (!(parent instanceof ViewGroup)) {
                return;
            }
            ViewGroup group = (ViewGroup) parent;
            group.setClipChildren(false);
            group.setClipToPadding(false);
            current = group;
        }
    }

    private void setInsetsSize(Object provider, Insets insets) {
        if (provider == null || insets == null) {
            return;
        }
        try {
            XposedHelpers.callMethod(provider, "setInsetsSize", insets);
        } catch (Throwable ignored) {
        }
    }

    private Class<?> findClass(String name, ClassLoader classLoader) {
        try {
            return XposedHelpers.findClass(name, classLoader);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void hookAllConstructors(Class<?> clazz, XC_MethodHook hook) {
        try {
            XposedBridge.hookAllConstructors(clazz, hook);
        } catch (Throwable throwable) {
            logError("Failed to hook constructors for " + clazz.getName(), throwable);
        }
    }

    private void hookAllMethods(Class<?> clazz, String methodName, XC_MethodHook hook) {
        try {
            XposedBridge.hookAllMethods(clazz, methodName, hook);
        } catch (Throwable throwable) {
            logError("Failed to hook " + clazz.getName() + '#' + methodName, throwable);
        }
    }

    private int dp(Context context, int value) {
        return Math.round(dpF(context, value));
    }

    private float dpF(Context context, int value) {
        if (context == null) {
            return value;
        }
        return value * context.getResources().getDisplayMetrics().density;
    }

    private boolean sameString(String first, String second) {
        return first == null ? second == null : first.equals(second);
    }

    private String hideKey(String scope, String packageName) {
        return scope + ':' + (packageName != null ? packageName : "null");
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private float clampFloat(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private interface AlphaValueApplier {
        void apply(float alpha);
    }

    private final class DelayedHideDecision {
        final String scope;
        final String packageName;
        final int delayMs;
        final String key;

        DelayedHideDecision(String scope, String packageName, int delayMs) {
            this.scope = scope;
            this.packageName = packageName;
            this.delayMs = delayMs;
            this.key = hideKey(scope, packageName);
        }
    }

    private static final class Config {
        boolean enabled;
        int widthPercent;
        int heightDp;
        int offsetXDp;
        int offsetYDp;
        int reservedAreaDp;
        int gestureAreaDp;
        boolean hideOnLauncher;
        boolean hideInApps;
        int launcherHideTimeoutMs;
        int appHideTimeoutMs;
        boolean hideOnLockscreen;
        boolean removeReservedArea;
        int alphaPercent;
        int fadeInMs = DEFAULT_FADE_MS;
        int fadeOutMs = DEFAULT_FADE_MS;
        boolean tintEnabled;
        int tintColor = DEFAULT_TINT_COLOR;
    }
}
