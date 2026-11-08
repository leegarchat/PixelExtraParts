package android.app;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Slog;
import android.util.SparseArray;


/**
 * Custom activity transition helper.
 * Reads transition mode from Settings.Global and applies custom animations
 * from the PixelCustomParts module or custom theme APKs.
 * Native equivalent of ActivityTransitionHook from the Xposed module.
 *
 * <p>Mode numbering (type × 10 + direction):</p>
 * <ul>
 *   <li>0 = Disabled</li>
 *   <li>-1 = Custom theme APK</li>
 *   <li>-2 = No animation</li>
 *   <li>10-13 = Slide (Right, Left, Top, Bottom)</li>
 *   <li>20-23 = Card Stack</li>
 *   <li>30-33 = Train</li>
 *   <li>40-43 = iOS Parallax</li>
 *   <li>50 = Fade</li>
 *   <li>60 = Zoom</li>
 *   <li>70-73 = Modal</li>
 *   <li>80-83 = Depth</li>
 *   <li>90-93 = Pivot</li>
 * </ul>
 * @hide
 */
public final class CustomTransitionHelper {
    private static final String TAG = "CustomTransition";

    // Settings.Global keys shared verbatim with the app.
    private static final String KEY_OPEN = "activity_open_transition";
    private static final String KEY_CLOSE = "activity_close_transition";
    private static final String KEY_CUSTOM_PACKAGE =
            "activity_transition_custom_package";
    private static final String KEY_CUSTOM_OPEN_PACKAGE =
            "activity_open_custom_package";
    private static final String KEY_CUSTOM_CLOSE_PACKAGE =
            "activity_close_custom_package";

    private static final String ACTION_REVIEW = "android.provider.action.REVIEW";
    private static final String ACTION_REVIEW_SECURE =
        "android.provider.action.REVIEW_SECURE";
    private static final String EXTRA_USE_SYSTEM_ACTIVITY_ANIMATION =
        "org.pixel.customparts.extra.USE_SYSTEM_ACTIVITY_ANIMATION";

    // Mode constants
    static final int MODE_DISABLED     = 0;
    static final int MODE_CUSTOM       = -1;
    static final int MODE_NO_ANIMATION = -2;

    // Module packages to try (native app first, then Xposed fallback)
    private static final String[] MODULE_PACKAGES = {
            "org.pixel.customparts",
            "org.pixel.customparts.xposed"
    };

    // Custom theme anim resource names
    private static final String ANIM_CUSTOM_OPEN_ENTER  = "custom_open_enter";
    private static final String ANIM_CUSTOM_OPEN_EXIT   = "custom_open_exit";
    private static final String ANIM_CUSTOM_CLOSE_ENTER = "custom_close_enter";
    private static final String ANIM_CUSTOM_CLOSE_EXIT  = "custom_close_exit";

    /** Holds four resource name/ID pairs for a single animation mode. */
    private static final class AnimDef {
        final String openEnterName, openExitName;
        final String closeEnterName, closeExitName;
        int oe, ox, ce, cx; // resolved resource IDs

        AnimDef(String oe, String ox, String ce, String cx) {
            this.openEnterName = oe;  this.openExitName = ox;
            this.closeEnterName = ce; this.closeExitName = cx;
        }
    }

    /** All 30 built-in modes. Key = mode constant, value = anim definition. */
    private static final SparseArray<AnimDef> MODES = new SparseArray<>();
    static {
        // ── Slide ────────────────────────────────────────────────
        MODES.put(10, new AnimDef("slide_in_right", "no_anim",
                "no_anim", "slide_out_right"));
        MODES.put(11, new AnimDef("slide_in_left", "no_anim",
                "no_anim", "train_exit"));
        MODES.put(12, new AnimDef("slide_in_top", "no_anim",
                "no_anim", "slide_out_top"));
        MODES.put(13, new AnimDef("slide_up_enter", "no_anim",
                "no_anim", "slide_down_exit"));
        // ── Card Stack ───────────────────────────────────────────
        MODES.put(20, new AnimDef("slide_in_right", "card_stack_exit",
                "card_stack_enter", "slide_out_right"));
        MODES.put(21, new AnimDef("slide_in_left", "card_stack_exit_left",
                "card_stack_enter_left", "train_exit"));
        MODES.put(22, new AnimDef("slide_in_top", "card_stack_exit_top",
                "card_stack_enter_top", "slide_out_top"));
        MODES.put(23, new AnimDef("slide_up_enter", "card_stack_exit_bottom",
                "card_stack_enter_bottom", "slide_down_exit"));
        // ── Train ────────────────────────────────────────────────
        MODES.put(30, new AnimDef("slide_in_right", "train_exit",
                "slide_in_left", "slide_out_right"));
        MODES.put(31, new AnimDef("slide_in_left", "slide_out_right",
                "slide_in_right", "train_exit"));
        MODES.put(32, new AnimDef("slide_in_top", "slide_down_exit",
                "slide_up_enter", "slide_out_top"));
        MODES.put(33, new AnimDef("slide_up_enter", "slide_out_top",
                "slide_in_top", "slide_down_exit"));
        // ── iOS Parallax ─────────────────────────────────────────
        MODES.put(40, new AnimDef("slide_in_right", "ios_open_exit",
                "ios_close_enter", "slide_out_right"));
        MODES.put(41, new AnimDef("slide_in_left", "ios_open_exit_left",
                "ios_close_enter_left", "train_exit"));
        MODES.put(42, new AnimDef("slide_in_top", "ios_open_exit_top",
                "ios_close_enter_top", "slide_out_top"));
        MODES.put(43, new AnimDef("slide_up_enter", "ios_open_exit_bottom",
                "ios_close_enter_bottom", "slide_down_exit"));
        // ── Fade ─────────────────────────────────────────────────
        MODES.put(50, new AnimDef("fade_in", "fade_out",
                "fade_in", "fade_out"));
        // ── Zoom ─────────────────────────────────────────────────
        MODES.put(60, new AnimDef("zoom_enter", "zoom_exit",
                "zoom_close_enter", "zoom_close_exit"));
        // ── Modal ────────────────────────────────────────────────
        MODES.put(70, new AnimDef("slide_in_right", "slide_up_exit_right",
                "slide_up_close_enter_right", "slide_out_right"));
        MODES.put(71, new AnimDef("slide_in_left", "slide_up_exit_left",
                "slide_up_close_enter_left", "train_exit"));
        MODES.put(72, new AnimDef("slide_in_top", "slide_up_exit_top",
                "slide_up_close_enter_top", "slide_out_top"));
        MODES.put(73, new AnimDef("slide_up_enter", "slide_up_exit",
                "slide_up_close_enter", "slide_down_exit"));
        // ── Depth ────────────────────────────────────────────────
        MODES.put(80, new AnimDef("slide_in_right", "depth_exit",
                "depth_close_enter", "slide_out_right"));
        MODES.put(81, new AnimDef("slide_in_left", "depth_exit",
                "depth_close_enter", "train_exit"));
        MODES.put(82, new AnimDef("slide_in_top", "depth_exit",
                "depth_close_enter", "slide_out_top"));
        MODES.put(83, new AnimDef("slide_up_enter", "depth_exit",
                "depth_close_enter", "slide_down_exit"));
        // ── Pivot ────────────────────────────────────────────────
        MODES.put(90, new AnimDef("pivot_enter", "pivot_exit",
                "pivot_close_enter", "pivot_close_exit"));
        MODES.put(91, new AnimDef("pivot_enter_left", "pivot_exit_left",
                "pivot_close_enter_left", "pivot_close_exit_left"));
        MODES.put(92, new AnimDef("pivot_enter_top", "pivot_exit_top",
                "pivot_close_enter_top", "pivot_close_exit_top"));
        MODES.put(93, new AnimDef("pivot_enter_bottom", "pivot_exit_bottom",
                "pivot_close_enter_bottom", "pivot_close_exit_bottom"));
    }

    // ── Cached state ────────────────────────────────────────────
    private static volatile boolean sResolved;
    private static volatile Context sModuleCtx;
    private static volatile String  sModulePackage;
    private static volatile Context sCustomThemeCtx;
    private static volatile String  sCustomThemePackage;

    // Re-entrancy guard
    private static final ThreadLocal<Boolean> sInHook =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    // ── Settings helpers ────────────────────────────────────────

    private static int getOpenMode(Activity activity) {
        try {
            return Settings.Global.getInt(
                    activity.getContentResolver(), KEY_OPEN, MODE_DISABLED);
        } catch (Throwable t) {
            return MODE_DISABLED;
        }
    }

    private static int getCloseMode(Activity activity) {
        try {
            return Settings.Global.getInt(
                    activity.getContentResolver(), KEY_CLOSE, MODE_DISABLED);
        } catch (Throwable t) {
            return MODE_DISABLED;
        }
    }

    private static String getCustomOpenPackage(Activity activity) {
        try {
            String pkg = Settings.Global.getString(
                    activity.getContentResolver(), KEY_CUSTOM_OPEN_PACKAGE);
            if (pkg != null && !pkg.isEmpty()) return pkg;
            return Settings.Global.getString(
                    activity.getContentResolver(), KEY_CUSTOM_PACKAGE);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String getCustomClosePackage(Activity activity) {
        try {
            String pkg = Settings.Global.getString(
                    activity.getContentResolver(), KEY_CUSTOM_CLOSE_PACKAGE);
            if (pkg != null && !pkg.isEmpty()) return pkg;
            return Settings.Global.getString(
                    activity.getContentResolver(), KEY_CUSTOM_PACKAGE);
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean shouldSkipOpenTransition(Intent intent) {
        if (intent == null) return false;
        if (intent.getBooleanExtra(EXTRA_USE_SYSTEM_ACTIVITY_ANIMATION, false)) return true;
        final String action = intent.getAction();
        return ACTION_REVIEW.equals(action) || ACTION_REVIEW_SECURE.equals(action);
    }

    private static boolean shouldSkipCloseTransition(Activity activity) {
        return activity != null && shouldSkipOpenTransition(activity.getIntent());
    }

    // ── Resource resolution ─────────────────────────────────────

    /**
     * Resolve ALL anim resource IDs from the installed module APK.
     * Iterates the MODES table and fills in the four IDs per mode.
     */
    private static boolean ensureResources(Context anyContext) {
        if (sResolved) return sModuleCtx != null;
        synchronized (CustomTransitionHelper.class) {
            if (sResolved) return sModuleCtx != null;
            for (String pkg : MODULE_PACKAGES) {
                try {
                    Context mc = anyContext.createPackageContext(pkg,
                            Context.CONTEXT_IGNORE_SECURITY);
                    Resources res = mc.getResources();
                    int test = res.getIdentifier("slide_in_right", "anim", pkg);
                    if (test == 0) continue;

                    sModuleCtx = mc;
                    sModulePackage = pkg;

                    for (int i = 0; i < MODES.size(); i++) {
                        AnimDef def = MODES.valueAt(i);
                        def.oe = res.getIdentifier(def.openEnterName,  "anim", pkg);
                        def.ox = res.getIdentifier(def.openExitName,   "anim", pkg);
                        def.ce = res.getIdentifier(def.closeEnterName, "anim", pkg);
                        def.cx = res.getIdentifier(def.closeExitName,  "anim", pkg);
                    }

                    sResolved = true;
                    Slog.i(TAG, "resources resolved via " + pkg);
                    return true;
                } catch (PackageManager.NameNotFoundException ignored) {
                } catch (Throwable t) {
                    Slog.e(TAG, "ensureResources failed for " + pkg, t);
                }
            }
            sResolved = true;
            return false;
        }
    }

    // ── Custom theme APK helpers ────────────────────────────────

    private static Context resolveCustomThemeContext(Context anyContext,
            String direction, Activity activity) {
        String pkg = "open".equals(direction)
                ? getCustomOpenPackage(activity)
                : getCustomClosePackage(activity);
        if (pkg == null || pkg.isEmpty()) {
            Slog.w(TAG, "custom " + direction + " transition has no package configured");
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
        } catch (PackageManager.NameNotFoundException e) {
            sCustomThemeCtx = null;
            sCustomThemePackage = null;
            Slog.w(TAG, "custom " + direction + " transition package is not visible: " + pkg);
            return null;
        } catch (Throwable t) {
            sCustomThemeCtx = null;
            sCustomThemePackage = null;
            Slog.e(TAG, "failed to resolve custom " + direction
                    + " transition package: " + pkg, t);
            return null;
        }
    }

    /** Reset cached custom theme context (e.g. after theme APK change). */
    static void invalidateCustomThemeCache() {
        sCustomThemeCtx = null;
        sCustomThemePackage = null;
    }

    private static int resolveThemeAnim(Context themeCtx, String animName) {
        if (themeCtx == null || sCustomThemePackage == null) return 0;
        return themeCtx.getResources()
                .getIdentifier(animName, "anim", sCustomThemePackage);
    }

    // ── Public API used by Activity methods ─────────────────────

    /**
     * Injects open-transition animation into the options Bundle.
     * Called before {@code execStartActivity} in
     * {@link #startActivityForResult(Intent, int, Bundle)}.
     */
    public static Bundle injectOpenTransition(Activity activity, Intent intent,
            Bundle options) {
        if (Boolean.TRUE.equals(sInHook.get())) return options;
        sInHook.set(Boolean.TRUE);
        try {
            if (shouldSkipOpenTransition(intent)) return options;

            int mode = getOpenMode(activity);
            if (mode == MODE_DISABLED) return options;

            if (mode == MODE_NO_ANIMATION) {
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                }
                return options;
            }

            if (intent != null
                    && (intent.getFlags() & Intent.FLAG_ACTIVITY_NO_ANIMATION) != 0) {
                return options;
            }

            if (mode != MODE_CUSTOM && !ensureResources(activity)) return options;

            Bundle animBundle = null;
            if (mode == MODE_CUSTOM) {
                Context themeCtx = resolveCustomThemeContext(
                        activity, "open", activity);
                if (themeCtx != null) {
                    int enterId = resolveThemeAnim(themeCtx,
                            ANIM_CUSTOM_OPEN_ENTER);
                    int exitId = resolveThemeAnim(themeCtx,
                            ANIM_CUSTOM_OPEN_EXIT);
                    if (enterId != 0 || exitId != 0) {
                        try {
                            ActivityOptions opts =
                                    ActivityOptions.makeCustomAnimation(
                                            themeCtx, enterId, exitId);
                            animBundle = opts.toBundle();
                        } catch (Throwable t) {
                            Slog.e(TAG, "custom open anim failed", t);
                        }
                    } else {
                        Slog.w(TAG, "custom open transition resources are missing from "
                                + sCustomThemePackage);
                    }
                }
            } else {
                AnimDef def = MODES.get(mode);
                if (def != null && def.oe != 0) {
                    try {
                        ActivityOptions opts =
                                ActivityOptions.makeCustomAnimation(
                                        sModuleCtx, def.oe, def.ox);
                        animBundle = opts.toBundle();
                    } catch (Throwable t) {
                        Slog.e(TAG, "open anim failed mode=" + mode, t);
                    }
                }
            }

            if (animBundle != null) {
                if (options != null) {
                    options.putAll(animBundle);
                } else {
                    options = animBundle;
                }
            }
            return options;
        } finally {
            sInHook.set(Boolean.FALSE);
        }
    }

    /**
     * Split-screen fallback: also apply open transition via
     * {@code overridePendingTransition}.
     * Called after {@code execStartActivity}.
     */
    public static void applyOpenTransitionFallback(Activity activity, IBinder token, Intent intent) {
        try {
            if (shouldSkipOpenTransition(intent)) return;

            int mode = getOpenMode(activity);
            if (mode == MODE_DISABLED || mode == MODE_NO_ANIMATION) return;
            if (mode != MODE_CUSTOM && !ensureResources(activity)) return;

            if (mode == MODE_CUSTOM) {
                Context themeCtx = resolveCustomThemeContext(
                        activity, "open", activity);
                if (themeCtx == null) return;
                int enterId = resolveThemeAnim(themeCtx,
                        ANIM_CUSTOM_OPEN_ENTER);
                int exitId = resolveThemeAnim(themeCtx,
                        ANIM_CUSTOM_OPEN_EXIT);
                if (enterId != 0 || exitId != 0) {
                    ActivityClient.getInstance().overridePendingTransition(
                            token, sCustomThemePackage,
                            enterId, exitId, 0);
                } else {
                    Slog.w(TAG, "custom open fallback resources are missing from "
                            + sCustomThemePackage);
                }
            } else {
                AnimDef def = MODES.get(mode);
                if (def == null || def.oe == 0) return;
                ActivityClient.getInstance().overridePendingTransition(
                        token, sModulePackage,
                        def.oe, def.ox, 0);
            }
        } catch (Throwable t) {
            Slog.e(TAG, "custom open transition fallback failed", t);
        }
    }

    /**
     * Applies close-transition override after
     * {@code finish()} / {@code finishAndRemoveTask()}.
     */
    public static void applyCloseTransition(Activity activity, IBinder token) {
        try {
            if (shouldSkipCloseTransition(activity)) return;
            int closeMode = getCloseMode(activity);
            if (closeMode == MODE_DISABLED) return;

            if (closeMode == MODE_NO_ANIMATION) {
                ActivityClient.getInstance().overridePendingTransition(
                        token, activity.getPackageName(), 0, 0, 0);
                return;
            }

            if (closeMode != MODE_CUSTOM && !ensureResources(activity)) return;

            if (closeMode == MODE_CUSTOM) {
                Context themeCtx = resolveCustomThemeContext(
                        activity, "close", activity);
                if (themeCtx == null) return;
                int enterId = resolveThemeAnim(themeCtx,
                        ANIM_CUSTOM_CLOSE_ENTER);
                int exitId = resolveThemeAnim(themeCtx,
                        ANIM_CUSTOM_CLOSE_EXIT);
                if (enterId != 0 || exitId != 0) {
                    ActivityClient.getInstance().overridePendingTransition(
                            token, sCustomThemePackage,
                            enterId, exitId, 0);
                } else {
                    Slog.w(TAG, "custom close transition resources are missing from "
                            + sCustomThemePackage);
                }
            } else {
                AnimDef def = MODES.get(closeMode);
                if (def == null || (def.ce == 0 && def.cx == 0)) return;
                ActivityClient.getInstance().overridePendingTransition(
                        token, sModulePackage,
                        def.ce, def.cx, 0);
            }
        } catch (Throwable t) {
            Slog.e(TAG, "custom close transition failed", t);
        }
    }
}
