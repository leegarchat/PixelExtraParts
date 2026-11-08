package org.pixel.customparts.addon.systemui.hooks;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.provider.Settings;
import android.util.Log;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Rotation animation hook (HOOK replacement for the WM Shell source patch).
 *
 * Intercepts AnimationUtils.loadAnimation() for stock rotation animations
 * (rotation_animation_*, screen_rotate_*) and substitutes the user-selected
 * style from the PixelExtraParts module resources or a custom theme APK.
 * Faithful port of ScreenRotationAnimation.RotationAnimationHelper.
 */
public class RotationAnimationHook extends BaseSystemUIHook {
    private static final String TAG = "RotationAnimationHook";

    private static final String KEY_ROTATION_STYLE = "rotation_animation_style";
    private static final String KEY_ROTATION_CUSTOM_PACKAGE = "rotation_animation_custom_package";

    private static final int STYLE_DEFAULT = 0;
    private static final int STYLE_CUSTOM_APK = -1;

    private static final String ANIM_ROTATE_EXIT = "custom_rotate_exit";
    private static final String ANIM_ROTATE_ENTER = "custom_rotate_enter";
    private static final String ANIM_ROTATE_ALPHA = "custom_rotate_alpha";

    private static final String[] MODULE_PACKAGES = {
            "org.pixel.customparts",
            "org.pixel.customparts.xposed"
    };

    private static final class AnimDef {
        final String exitName, enterName, alphaName;
        int exitId, enterId, alphaId;

        AnimDef(String exit, String enter, String alpha) {
            this.exitName = exit;
            this.enterName = enter;
            this.alphaName = alpha;
        }
    }

    private static final android.util.SparseArray<AnimDef> STYLES = new android.util.SparseArray<>();
    static {
        STYLES.put(1, new AnimDef("wp_exit", "wp_enter", "wp_alpha"));
        STYLES.put(2, new AnimDef("win10_11_exit", "win10_11_enter", "win10_11_alpha"));
        STYLES.put(3, new AnimDef("cube_exit", "cube_enter", "cube_alpha"));
        STYLES.put(4, new AnimDef("zoom_rotate_exit", "zoom_rotate_enter", "zoom_rotate_alpha"));
        STYLES.put(5, new AnimDef("slide_rotate_exit", "slide_rotate_enter", "slide_rotate_alpha"));
        STYLES.put(6, new AnimDef("flip_exit", "flip_enter", "flip_alpha"));
        STYLES.put(7, new AnimDef("fade_rotate_exit", "fade_rotate_enter", "fade_rotate_alpha"));
        STYLES.put(8, new AnimDef("bounce_exit", "bounce_enter", "bounce_alpha"));
    }

    private static volatile boolean sResolved;
    private static volatile Context sModuleCtx;
    private static volatile String sModulePackage;
    private static volatile Context sCustomThemeCtx;
    private static volatile String sCustomThemePackage;

    private static final ThreadLocal<Boolean> sInHook =
            new ThreadLocal<Boolean>() {
                @Override
                protected Boolean initialValue() {
                    return Boolean.FALSE;
                }
            };

    @Override
    public String getHookId() {
        return "RotationAnimationHook";
    }

    @Override
    public int getPriority() {
        return 50;
    }

    @Override
    protected void onInit(ClassLoader classLoader) {
        try {
            Class<?> animationUtils = XposedHelpers.findClass(
                    "android.view.animation.AnimationUtils", classLoader);
            XposedBridge.hookAllMethods(animationUtils, "loadAnimation", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (Boolean.TRUE.equals(sInHook.get())) {
                        return;
                    }
                    try {
                        if (param.args == null || param.args.length < 2
                                || !(param.args[0] instanceof Context)
                                || !(param.args[1] instanceof Integer)) {
                            return;
                        }
                        Context context = (Context) param.args[0];
                        int resId = (Integer) param.args[1];
                        Animation replacement = maybeCustomRotation(context, resId);
                        if (replacement != null) {
                            param.setResult(replacement);
                        }
                    } catch (Throwable t) {
                        Log.e(TAG, "rotation hook failed", t);
                    }
                }
            });
            log("AnimationUtils.loadAnimation hooked");
        } catch (Throwable t) {
            logError("Unable to hook AnimationUtils.loadAnimation", t);
        }
    }

    private Animation maybeCustomRotation(Context context, int resId) {
        int style = getRotationStyle(context);
        if (style == STYLE_DEFAULT) {
            return null;
        }
        String entryName;
        try {
            entryName = context.getResources().getResourceEntryName(resId);
        } catch (Throwable ignored) {
            return null;
        }
        if (entryName == null
                || (!entryName.startsWith("rotation_animation_")
                        && !entryName.startsWith("screen_rotate_"))) {
            return null;
        }
        boolean isExit = entryName.endsWith("_exit");
        boolean isEnter = entryName.endsWith("_enter");
        boolean isAlpha = entryName.contains("alpha");
        if (!isExit && !isEnter && !isAlpha) {
            return null;
        }
        observeThemeKeysOnce(context);
        sInHook.set(Boolean.TRUE);
        try {
            if (style == STYLE_CUSTOM_APK) {
                String animName = isExit ? ANIM_ROTATE_EXIT
                        : isEnter ? ANIM_ROTATE_ENTER : ANIM_ROTATE_ALPHA;
                return loadFromCustomTheme(context, animName);
            }
            AnimDef def = STYLES.get(style);
            if (def == null || !ensureResources(context)) {
                return null;
            }
            int targetId = isExit ? def.exitId : isEnter ? def.enterId : def.alphaId;
            if (targetId == 0) {
                return null;
            }
            return AnimationUtils.loadAnimation(sModuleCtx, targetId);
        } catch (Throwable t) {
            Log.e(TAG, "maybeCustomRotation failed", t);
            return null;
        } finally {
            sInHook.set(Boolean.FALSE);
        }
    }

    private void observeThemeKeysOnce(Context context) {
        observeExactSettingOnce(context, KEY_ROTATION_CUSTOM_PACKAGE, new Runnable() {
            @Override
            public void run() {
                sCustomThemeCtx = null;
                sCustomThemePackage = null;
            }
        });
    }

    private static int getRotationStyle(Context context) {
        try {
            return Settings.Global.getInt(
                    context.getContentResolver(), KEY_ROTATION_STYLE, STYLE_DEFAULT);
        } catch (Throwable t) {
            return STYLE_DEFAULT;
        }
    }

    private static String getCustomPackage(Context context) {
        try {
            return Settings.Global.getString(
                    context.getContentResolver(), KEY_ROTATION_CUSTOM_PACKAGE);
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean ensureResources(Context anyContext) {
        if (sResolved) {
            return sModuleCtx != null;
        }
        synchronized (RotationAnimationHook.class) {
            if (sResolved) {
                return sModuleCtx != null;
            }
            for (String pkg : MODULE_PACKAGES) {
                try {
                    Context mc = anyContext.createPackageContext(pkg,
                            Context.CONTEXT_IGNORE_SECURITY);
                    Resources res = mc.getResources();
                    int test = res.getIdentifier("slide_in_right", "anim", pkg);
                    if (test == 0) {
                        continue;
                    }
                    sModuleCtx = mc;
                    sModulePackage = pkg;
                    for (int i = 0; i < STYLES.size(); i++) {
                        AnimDef def = STYLES.valueAt(i);
                        def.exitId = res.getIdentifier(def.exitName, "anim", pkg);
                        def.enterId = res.getIdentifier(def.enterName, "anim", pkg);
                        def.alphaId = res.getIdentifier(def.alphaName, "anim", pkg);
                    }
                    sResolved = true;
                    Log.i(TAG, "resources resolved via " + pkg);
                    return true;
                } catch (PackageManager.NameNotFoundException ignored) {
                } catch (Throwable t) {
                    Log.e(TAG, "ensureResources failed for " + pkg, t);
                }
            }
            sResolved = true;
            return false;
        }
    }

    private static Animation loadFromCustomTheme(Context context, String animName) {
        Context themeCtx = resolveCustomThemeContext(context);
        if (themeCtx == null || sCustomThemePackage == null) {
            return null;
        }
        int resId = themeCtx.getResources().getIdentifier(animName, "anim", sCustomThemePackage);
        if (resId == 0) {
            Log.w(TAG, "custom theme missing resource: " + animName);
            return null;
        }
        return AnimationUtils.loadAnimation(themeCtx, resId);
    }

    private static Context resolveCustomThemeContext(Context anyContext) {
        String pkg = getCustomPackage(anyContext);
        if (pkg == null || pkg.isEmpty()) {
            return null;
        }
        if (sCustomThemeCtx != null && pkg.equals(sCustomThemePackage)) {
            return sCustomThemeCtx;
        }
        try {
            Context themeCtx = anyContext.createPackageContext(pkg,
                    Context.CONTEXT_IGNORE_SECURITY);
            sCustomThemeCtx = themeCtx;
            sCustomThemePackage = pkg;
            return themeCtx;
        } catch (Throwable t) {
            sCustomThemeCtx = null;
            sCustomThemePackage = null;
            Log.w(TAG, "custom rotation theme not visible: " + pkg);
            return null;
        }
    }
}
