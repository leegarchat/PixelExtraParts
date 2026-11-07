/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.wm.shell.transition;

import static android.util.RotationUtils.deltaRotation;
import static android.view.WindowManager.LayoutParams.ROTATION_ANIMATION_CROSSFADE;
import static android.view.WindowManager.LayoutParams.ROTATION_ANIMATION_JUMPCUT;
import static android.view.WindowManagerPolicyConstants.SCREEN_FREEZE_LAYER_BASE;

import static com.android.internal.policy.TransitionAnimation.MAX_ANIMATION_DURATION;
import static com.android.wm.shell.transition.DefaultSurfaceAnimator.buildSurfaceAnimation;
import static com.android.wm.shell.transition.DefaultSurfaceAnimator.buildWindowAnimation;
import static com.android.wm.shell.transition.DefaultSurfaceAnimator.createAnimator;
import static com.android.wm.shell.transition.Transitions.TAG;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.annotation.NonNull;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.provider.Settings;
import android.util.Slog;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.SurfaceControl.Transaction;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.AnimationUtils;
import android.window.ScreenCapture.ScreenCaptureParams;
import android.window.ScreenCaptureInternal;
import android.window.TransitionInfo;

import com.android.internal.R;
import com.android.internal.policy.TransitionAnimation;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.shared.TransactionPool;

import java.util.ArrayList;
import java.util.function.Consumer;

/**
 * This class handles the rotation animation when the device is rotated.
 *
 * <p>
 * The screen rotation animation is composed of 3 different part:
 * <ul>
 * <li> The screenshot: <p>
 *     A screenshot of the whole screen prior the change of orientation is taken to hide the
 *     element resizing below. The screenshot is then animated to rotate and cross-fade to
 *     the new orientation with the content in the new orientation.
 *
 * <li> The windows on the display: <p>y
 *      Once the device is rotated, the screen and its content are in the new orientation. The
 *      animation first rotate the new content into the old orientation to then be able to
 *      animate to the new orientation
 *
 * <li> The Background color frame: <p>
 *      To have the animation seem more seamless, we add a color transitioning background behind the
 *      exiting and entering layouts. We compute the brightness of the start and end
 *      layouts and transition from the two brightness values as grayscale underneath the animation
 * </ul>
 */
class ScreenRotationAnimation {
    static final int FLAG_HAS_WALLPAPER = 1;

    private final Context mContext;
    private final TransactionPool mTransactionPool;
    private final float[] mTmpFloats = new float[9];
    /** The leash of the changing window container. */
    private final SurfaceControl mSurfaceControl;
    private final SurfaceControl mRootLeash;

    private final int mAnimHint;
    private final int mStartWidth;
    private final int mStartHeight;
    private final int mEndWidth;
    private final int mEndHeight;
    private final int mStartRotation;
    private final int mEndRotation;

    /** This layer contains the actual screenshot that is to be faded out. */
    private SurfaceControl mScreenshotLayer;
    /**
     * Only used for screen rotation and not custom animations. Layered behind all other layers
     * to avoid showing any "empty" spots
     */
    private SurfaceControl mBackColorSurface;
    /** The leash using to animate screenshot layer. */
    private final SurfaceControl mAnimLeash;
    /**
     * The container with background color for {@link #mSurfaceControl}. It is only created if
     * {@link #mSurfaceControl} may be translucent. E.g. visible wallpaper with alpha < 1 (dimmed).
     * That prevents flickering of alpha blending.
     */
    private SurfaceControl mBackEffectSurface;

    /**
     * A layer placed between the snapshot and the enter surface to reveal the new content.
     * <pre>
     * Layer from top to bottom:
     *  mScreenshotLayer: fade out
     *  mColorOverlay: fade out
     *  mSurfaceControl: alpha 1
     *  mBackColorSurface: alpha 1
     *
     * Timeline:
     *  S=snapshot
     *  O=color overlay
     *  anim=rotate & alpha 1 to 0 animation
     *  |---------------total 283ms-----------------|
     *  |>-116ms S anim--|
     *  |----83ms----|>--------200ms O anim---------|
     * </pre>
     */
    private SurfaceControl mColorOverlay;

    // The current active animation to move from the old to the new rotated
    // state.  Which animation is run here will depend on the old and new
    // rotations.
    private Animation mRotateExitAnimation;
    private Animation mRotateEnterAnimation;
    private Animation mRotateAlphaAnimation;

    /** Intensity of light/whiteness of the layout before rotation occurs. */
    private float mStartLuma;
    /** Intensity of light/whiteness of the layout after rotation occurs. */
    private float mEndLuma;
    private final TransitionInfo.Change mChange;

    ScreenRotationAnimation(Context context, TransactionPool pool, Transaction t,
            TransitionInfo.Change change, SurfaceControl rootLeash, int animHint, int flags) {
        mContext = context;
        mTransactionPool = pool;
        mAnimHint = animHint;
        mChange = change;

        mSurfaceControl = change.getLeash();
        mRootLeash = rootLeash;
        mStartWidth = change.getStartAbsBounds().width();
        mStartHeight = change.getStartAbsBounds().height();
        mEndWidth = change.getEndAbsBounds().width();
        mEndHeight = change.getEndAbsBounds().height();
        mStartRotation = change.getStartRotation();
        mEndRotation = change.getEndRotation();

        mAnimLeash = new SurfaceControl.Builder()
                .setParent(rootLeash)
                .setEffectLayer()
                .setCallsite("ShellRotationAnimation")
                .setName("Animation leash of screenshot rotation")
                .build();
        final boolean isRotationChange = mStartRotation != mEndRotation;

        try {
            if (change.getSnapshot() != null) {
                mScreenshotLayer = change.getSnapshot();
                t.reparent(mScreenshotLayer, mAnimLeash);
                mStartLuma = change.getSnapshotLuma();
            } else {
                ScreenCaptureInternal.LayerCaptureArgs args =
                        new ScreenCaptureInternal.LayerCaptureArgs.Builder(mSurfaceControl)
                                .setSecureContentPolicy(
                                        ScreenCaptureParams.SECURE_CONTENT_POLICY_CAPTURE)
                                .setProtectedContentPolicy(
                                        ScreenCaptureParams.PROTECTED_CONTENT_POLICY_CAPTURE)
                                .setSourceCrop(new Rect(0, 0, mStartWidth, mStartHeight))
                                .setPreserveDisplayColors(true)
                                .build();
                ScreenCaptureInternal.ScreenshotHardwareBuffer screenshotBuffer =
                        ScreenCaptureInternal.captureLayers(args);
                if (screenshotBuffer == null) {
                    Slog.w(TAG, "Unable to take screenshot of display");
                    return;
                }

                mScreenshotLayer = new SurfaceControl.Builder()
                        .setParent(mAnimLeash)
                        .setBLASTLayer()
                        .setSecure(screenshotBuffer.containsSecureLayers())
                        .setOpaque(true)
                        .setCallsite("ShellRotationAnimation")
                        .setName("RotationLayer")
                        .build();

                TransitionAnimation.configureScreenshotLayer(t, mScreenshotLayer, screenshotBuffer);
                final HardwareBuffer hardwareBuffer = screenshotBuffer.getHardwareBuffer();
                t.show(mScreenshotLayer);
                if (!isCustomRotate()) {
                    mStartLuma = TransitionAnimation.getBorderLuma(hardwareBuffer,
                            screenshotBuffer.getColorSpace(), mSurfaceControl);
                }
                hardwareBuffer.close();
            }
            if (isRotationChange && (flags & FLAG_HAS_WALLPAPER) != 0) {
                mBackEffectSurface = new SurfaceControl.Builder()
                        .setCallsite("ShellRotationAnimation").setParent(rootLeash)
                        .setEffectLayer().setOpaque(true).setName("BackEffect").build();
                t.reparent(mSurfaceControl, mBackEffectSurface);
                t.setColor(mBackEffectSurface, new float[]{mStartLuma, mStartLuma, mStartLuma});
                t.show(mBackEffectSurface);
            }

            t.setLayer(mAnimLeash, SCREEN_FREEZE_LAYER_BASE);
            t.show(mAnimLeash);
            // Crop the real content in case it contains a larger child layer, e.g. wallpaper.
            t.setCrop(getEnterSurface(), new Rect(0, 0, mEndWidth, mEndHeight));

            if (isRotationChange && !isCustomRotate()) {
                final float[] color = new float[]{mStartLuma, mStartLuma, mStartLuma};
                mBackColorSurface = new SurfaceControl.Builder()
                        .setParent(rootLeash)
                        .setColorLayer()
                        .setOpaque(true)
                        .setCallsite("ShellRotationAnimation")
                        .setName("BackColorSurface")
                        .build();

                t.setLayer(mBackColorSurface, -1);
                t.setColor(mBackColorSurface, color);
                t.show(mBackColorSurface);

                mColorOverlay = new SurfaceControl.Builder()
                        .setCallsite("ShellRotationAnimation").setParent(rootLeash)
                        .setColorLayer().setOpaque(true).setName("ColorOverlay").build();
                t.setColor(mColorOverlay, color);
                t.setLayer(mColorOverlay, SCREEN_FREEZE_LAYER_BASE - 1);
                t.setWindowCrop(mColorOverlay, mEndWidth, mEndHeight);
                t.show(mColorOverlay);
            }

        } catch (Surface.OutOfResourcesException e) {
            Slog.w(TAG, "Unable to allocate freeze surface", e);
        }

        setScreenshotTransform(t);
    }

    private boolean isCustomRotate() {
        return mAnimHint == ROTATION_ANIMATION_CROSSFADE || mAnimHint == ROTATION_ANIMATION_JUMPCUT;
    }

    /** Returns the surface which contains the real content to animate enter. */
    private SurfaceControl getEnterSurface() {
        return mBackEffectSurface != null ? mBackEffectSurface : mSurfaceControl;
    }

    private void setScreenshotTransform(SurfaceControl.Transaction t) {
        if (mScreenshotLayer == null) {
            return;
        }
        final Matrix matrix = new Matrix();
        final int delta = deltaRotation(mEndRotation, mStartRotation);
        if (delta != 0) {
            // Compute the transformation matrix that must be applied to the snapshot to make it
            // stay in the same original position with the current screen rotation.
            switch (delta) {
                case Surface.ROTATION_90:
                    matrix.setRotate(90, 0, 0);
                    matrix.postTranslate(mStartHeight, 0);
                    break;
                case Surface.ROTATION_180:
                    matrix.setRotate(180, 0, 0);
                    matrix.postTranslate(mStartWidth, mStartHeight);
                    break;
                case Surface.ROTATION_270:
                    matrix.setRotate(270, 0, 0);
                    matrix.postTranslate(0, mStartWidth);
                    break;
            }
        } else if ((mEndWidth > mStartWidth) == (mEndHeight > mStartHeight)
                && (mEndWidth != mStartWidth || mEndHeight != mStartHeight)) {
            // Display resizes without rotation change.
            final float scale = Math.max((float) mEndWidth / mStartWidth,
                    (float) mEndHeight / mStartHeight);
            matrix.setScale(scale, scale);
        }
        matrix.getValues(mTmpFloats);
        float x = mTmpFloats[Matrix.MTRANS_X];
        float y = mTmpFloats[Matrix.MTRANS_Y];
        t.setPosition(mScreenshotLayer, x, y);
        t.setMatrix(mScreenshotLayer,
                mTmpFloats[Matrix.MSCALE_X], mTmpFloats[Matrix.MSKEW_Y],
                mTmpFloats[Matrix.MSKEW_X], mTmpFloats[Matrix.MSCALE_Y]);
    }

    /**
     * Returns true if any animations were added to `animations`.
     */
    @NonNull
    WindowAnimation buildAnimation(@NonNull Consumer<WindowAnimation> finishCallback,
            float animationScale, @NonNull ShellExecutor mainExecutor) {
        if (mScreenshotLayer == null) {
            // Can't do animation.
            return null;
        }

        // TODO : Found a way to get right end luma and re-enable color frame animation.
        // End luma value is very not stable so it will cause more flicker is we run background
        // color frame animation.
        //mEndLuma = getLumaOfSurfaceControl(mEndBounds, mSurfaceControl);

        final ArrayList<Animator> siblings = new ArrayList<>();

        final boolean customRotate = isCustomRotate();
        if (customRotate) {
            // Evolution X: try custom rotation animation first
            Animation customExit = RotationAnimationHelper.loadCustomExitAnimation(mContext, 0);
            Animation customEnter = RotationAnimationHelper.loadCustomEnterAnimation(mContext, 0);
            Animation customAlpha = RotationAnimationHelper.loadCustomAlphaAnimation(mContext);

            if (customExit != null && customEnter != null) {
                mRotateExitAnimation = customExit;
                mRotateEnterAnimation = customEnter;
                mRotateAlphaAnimation = customAlpha != null ? customAlpha
                        : AnimationUtils.loadAnimation(mContext, R.anim.screen_rotate_alpha);
            } else {
                mRotateExitAnimation = AnimationUtils.loadAnimation(mContext,
                        mAnimHint == ROTATION_ANIMATION_JUMPCUT ? R.anim.rotation_animation_jump_exit
                                : R.anim.rotation_animation_xfade_exit);
                mRotateEnterAnimation = AnimationUtils.loadAnimation(mContext,
                        R.anim.rotation_animation_enter);
                mRotateAlphaAnimation = AnimationUtils.loadAnimation(mContext,
                        R.anim.screen_rotate_alpha);
            }
        } else {
            // Figure out how the screen has moved from the original rotation.
            int delta = deltaRotation(mEndRotation, mStartRotation);

            // Evolution X: try custom rotation animation first
            Animation customExit = RotationAnimationHelper.loadCustomExitAnimation(mContext, delta);
            Animation customEnter = RotationAnimationHelper.loadCustomEnterAnimation(mContext, delta);

            if (customExit != null && customEnter != null) {
                mRotateExitAnimation = customExit;
                mRotateEnterAnimation = customEnter;
            } else {
                switch (delta) { /* Counter-Clockwise Rotations */
                    case Surface.ROTATION_0:
                        mRotateExitAnimation = AnimationUtils.loadAnimation(mContext,
                                R.anim.screen_rotate_0_exit);
                        mRotateEnterAnimation = AnimationUtils.loadAnimation(mContext,
                                R.anim.rotation_animation_enter);
                        break;
                    case Surface.ROTATION_90:
                        mRotateExitAnimation = AnimationUtils.loadAnimation(mContext,
                                R.anim.screen_rotate_plus_90_exit);
                        mRotateEnterAnimation = AnimationUtils.loadAnimation(mContext,
                                R.anim.screen_rotate_plus_90_enter);
                        break;
                    case Surface.ROTATION_180:
                        mRotateExitAnimation = AnimationUtils.loadAnimation(mContext,
                                R.anim.screen_rotate_180_exit);
                        mRotateEnterAnimation = AnimationUtils.loadAnimation(mContext,
                                R.anim.screen_rotate_180_enter);
                        break;
                    case Surface.ROTATION_270:
                        mRotateExitAnimation = AnimationUtils.loadAnimation(mContext,
                                R.anim.screen_rotate_minus_90_exit);
                        mRotateEnterAnimation = AnimationUtils.loadAnimation(mContext,
                                R.anim.screen_rotate_minus_90_enter);
                        break;
                }
            }

            if (mColorOverlay != null) {
                final var enterAnimations = ((AnimationSet) mRotateEnterAnimation).getAnimations();
                for (int i = enterAnimations.size() - 1; i >= 0; i--) {
                    final Animation anim = enterAnimations.get(i);
                    if (!(anim instanceof AlphaAnimation)) continue;
                    enterAnimations.remove(i);
                    final AlphaAnimation fadeOut = new AlphaAnimation(1, 0);
                    fadeOut.setInterpolator(anim.getInterpolator());
                    fadeOut.setStartOffset(anim.getStartOffset());
                    fadeOut.setDuration(anim.getDuration());
                    fadeOut.scaleCurrentDuration(animationScale);
                    siblings.add(createAnimator(fadeOut, mColorOverlay, mTransactionPool,
                            null /* position */, 0 /* cornerRadius */, null /* clipRect */));
                    break;
                }
            }
        }

        mRotateExitAnimation.initialize(mEndWidth, mEndHeight, mStartWidth, mStartHeight);
        mRotateExitAnimation.restrictDuration(MAX_ANIMATION_DURATION);
        mRotateExitAnimation.scaleCurrentDuration(animationScale);
        mRotateEnterAnimation.initialize(mEndWidth, mEndHeight, mStartWidth, mStartHeight);
        mRotateEnterAnimation.restrictDuration(MAX_ANIMATION_DURATION);
        mRotateEnterAnimation.scaleCurrentDuration(animationScale);

        final WindowAnimation mainAnim;
        if (customRotate) {
            mRotateAlphaAnimation.initialize(mEndWidth, mEndHeight, mStartWidth, mStartHeight);
            mRotateAlphaAnimation.restrictDuration(MAX_ANIMATION_DURATION);
            mRotateAlphaAnimation.scaleCurrentDuration(animationScale);

            siblings.add(buildScreenshotAlphaAnimation());
            mainAnim = startDisplayRotation();
        } else {
            mainAnim = startDisplayRotation();
            siblings.add(startScreenshotRotationAnimation());
            if (mBackEffectSurface != null && mStartLuma > 0.1f) {
                // Animate from the color of background to black for smooth alpha blending.
                siblings.add(buildLumaAnimation(mStartLuma, 0f /* endLuma */,
                        mBackEffectSurface, animationScale));
            }
        }

        final WindowAnimation composite =
                new MultiPartWindowAnimation(mainAnim, siblings);
        composite.addFinishCallback(finishCallback, mainExecutor);
        return composite;
    }

    private WindowAnimation startDisplayRotation() {
        return buildWindowAnimation(mRotateEnterAnimation, mChange,
                getEnterSurface(), null /* finishCallback */, mTransactionPool,
                null /* mainExecutor*/, null /* position */, 0 /* cornerRadius */,
                null /* clipRect */, null /* roundedBounds */);
    }

    private ValueAnimator startScreenshotRotationAnimation() {
        return createAnimator(mRotateExitAnimation, mAnimLeash, mTransactionPool,
                null /* position */, 0 /* cornerRadius */, null /* clipRect */);
    }

    private ValueAnimator buildScreenshotAlphaAnimation() {
        return createAnimator(mRotateAlphaAnimation, mAnimLeash, mTransactionPool,
                null /* position */, 0 /* cornerRadius */, null /* clipRect */);
    }

    private ValueAnimator buildLumaAnimation(float startLuma, float endLuma,
            SurfaceControl surface, float animationScale) {
        final long durationMillis = (long) (mContext.getResources().getInteger(
                R.integer.config_screen_rotation_color_transition) * animationScale);
        final LumaAnimation animation = new LumaAnimation(durationMillis);
        // Align the end with the enter animation.
        animation.setStartOffset(mRotateEnterAnimation.getDuration() - durationMillis);
        final LumaAnimationAdapter adapter = new LumaAnimationAdapter(surface, startLuma, endLuma);
        return buildSurfaceAnimation(animation, null /* finishCallback */,
                mTransactionPool, null /* mainExecutor */, adapter);
    }

    public void kill() {
        final Transaction t = mTransactionPool.acquire();
        if (mAnimLeash.isValid()) {
            t.remove(mAnimLeash);
        }

        if (mScreenshotLayer != null && mScreenshotLayer.isValid()) {
            t.remove(mScreenshotLayer);
        }
        if (mBackColorSurface != null && mBackColorSurface.isValid()) {
            t.remove(mBackColorSurface);
        }
        if (mColorOverlay != null && mColorOverlay.isValid()) {
            t.remove(mColorOverlay);
        }
        if (mBackEffectSurface != null && mBackEffectSurface.isValid()) {
            // Restore the content surface to transition root because it was moved to BackEffect.
            if (mSurfaceControl.isValid() && mRootLeash.isValid()) {
                t.reparent(mSurfaceControl, mRootLeash);
            }
            t.remove(mBackEffectSurface);
        }
        t.apply();
        mTransactionPool.release(t);
    }

    /** A no-op wrapper to provide animation duration. */
    private static class LumaAnimation extends Animation {
        LumaAnimation(long durationMillis) {
            setDuration(durationMillis);
        }
    }

    private static class LumaAnimationAdapter extends DefaultSurfaceAnimator.AnimationAdapter {
        final float[] mColorArray = new float[3];
        final float mStartLuma;
        final float mEndLuma;
        final AccelerateInterpolator mInterpolation;

        LumaAnimationAdapter(@NonNull SurfaceControl leash, float startLuma, float endLuma) {
            super(leash);
            mStartLuma = startLuma;
            mEndLuma = endLuma;
            // Make the initial progress color lighter if the background is light. That avoids
            // darker content when fading into the entering surface.
            final float factor = Math.min(3f, (Math.max(0.5f, mStartLuma) - 0.5f) * 10);
            Slog.d(TAG, "Luma=" + mStartLuma + " factor=" + factor);
            mInterpolation = factor > 0.5f ? new AccelerateInterpolator(factor) : null;
        }

        @Override
        void applyTransformation(ValueAnimator animator, long currentPlayTime) {
            final float fraction = mInterpolation != null
                    ? mInterpolation.getInterpolation(animator.getAnimatedFraction())
                    : animator.getAnimatedFraction();
            final float luma = mStartLuma + fraction * (mEndLuma - mStartLuma);
            mColorArray[0] = luma;
            mColorArray[1] = luma;
            mColorArray[2] = luma;
            mTransaction.setColor(mLeash, mColorArray);
        }
    }

    // BEGIN Evolution X: Custom rotation animation support ────────────────────

    /**
     * Helper for custom rotation animations.
     * Reads rotation animation style from Settings.Global and loads custom
     * animations from the PixelExtraParts module or custom theme APKs.
     *
     * @hide
     */
    static final class RotationAnimationHelper {
        private static final String TAG = "RotationAnimHelper";

        // Settings.Global key
        private static final String KEY_ROTATION_STYLE = "rotation_animation_style";
        private static final String KEY_ROTATION_CUSTOM_PACKAGE = "rotation_animation_custom_package";

        // Style constants
        static final int STYLE_DEFAULT = 0;
        static final int STYLE_CUSTOM_APK = -1;

        // Built-in styles
        static final int STYLE_WINDOWS_PHONE = 1;  // scale + fade (WP style)
        static final int STYLE_WIN10_11 = 2;        // sequential shrink/expand (Win10/11 style)
        static final int STYLE_CUBE = 3;            // 3D cube rotation
        static final int STYLE_ZOOM = 4;            // simple zoom in/out
        static final int STYLE_SLIDE = 5;           // slide from edges
        static final int STYLE_FLIP = 6;            // horizontal flip
        static final int STYLE_FADE = 7;            // simple crossfade
        static final int STYLE_BOUNCE = 8;          // bounce with overshoot

        // Module packages to try
        private static final String[] MODULE_PACKAGES = {
                "org.pixel.customparts",
                "org.pixel.customparts.xposed"
        };

        // Custom rotation anim resource names
        private static final String ANIM_ROTATE_EXIT = "custom_rotate_exit";
        private static final String ANIM_ROTATE_ENTER = "custom_rotate_enter";
        private static final String ANIM_ROTATE_ALPHA = "custom_rotate_alpha";

        /** Holds resource name/ID pairs for a rotation animation style. */
        private static final class AnimDef {
            final String exitName, enterName, alphaName;
            int exitId, enterId, alphaId;

            AnimDef(String exit, String enter, String alpha) {
                this.exitName = exit;
                this.enterName = enter;
                this.alphaName = alpha;
            }
        }

        // Built-in styles table
        private static final android.util.SparseArray<AnimDef> STYLES = new android.util.SparseArray<>();
        static {
            // Windows Phone style: scale + fade (simultaneous)
            STYLES.put(STYLE_WINDOWS_PHONE, new AnimDef(
                    "wp_exit", "wp_enter", "wp_alpha"));
            // Windows 10/11 style: sequential shrink/expand
            STYLES.put(STYLE_WIN10_11, new AnimDef(
                    "win10_11_exit", "win10_11_enter", "win10_11_alpha"));
            // Cube style: 3D-like cube rotation
            STYLES.put(STYLE_CUBE, new AnimDef(
                    "cube_exit", "cube_enter", "cube_alpha"));
            // Zoom style: simple zoom in/out
            STYLES.put(STYLE_ZOOM, new AnimDef(
                    "zoom_rotate_exit", "zoom_rotate_enter", "zoom_rotate_alpha"));
            // Slide style: slide out/in from edges
            STYLES.put(STYLE_SLIDE, new AnimDef(
                    "slide_rotate_exit", "slide_rotate_enter", "slide_rotate_alpha"));
            // Flip style: horizontal flip
            STYLES.put(STYLE_FLIP, new AnimDef(
                    "flip_exit", "flip_enter", "flip_alpha"));
            // Fade style: simple crossfade
            STYLES.put(STYLE_FADE, new AnimDef(
                    "fade_rotate_exit", "fade_rotate_enter", "fade_rotate_alpha"));
            // Bounce style: bounce with overshoot
            STYLES.put(STYLE_BOUNCE, new AnimDef(
                    "bounce_exit", "bounce_enter", "bounce_alpha"));
        }

        // Cached state
        private static volatile boolean sResolved;
        private static volatile Context sModuleCtx;
        private static volatile String sModulePackage;
        private static volatile Context sCustomThemeCtx;
        private static volatile String sCustomThemePackage;

        // Re-entrancy guard
        private static final ThreadLocal<Boolean> sInHook =
                ThreadLocal.withInitial(() -> Boolean.FALSE);

        // ── Settings ────────────────────────────────────────────────

        static int getRotationStyle(Context context) {
            try {
                return Settings.Global.getInt(
                        context.getContentResolver(), KEY_ROTATION_STYLE, STYLE_DEFAULT);
            } catch (Throwable t) {
                return STYLE_DEFAULT;
            }
        }

        static String getCustomPackage(Context context) {
            try {
                return Settings.Global.getString(
                        context.getContentResolver(), KEY_ROTATION_CUSTOM_PACKAGE);
            } catch (Throwable t) {
                return null;
            }
        }

        // ── Resource resolution ─────────────────────────────────────

        private static boolean ensureResources(Context anyContext) {
            if (sResolved) return sModuleCtx != null;
            synchronized (RotationAnimationHelper.class) {
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

                        // Resolve built-in styles
                        for (int i = 0; i < STYLES.size(); i++) {
                            AnimDef def = STYLES.valueAt(i);
                            def.exitId = res.getIdentifier(def.exitName, "anim", pkg);
                            def.enterId = res.getIdentifier(def.enterName, "anim", pkg);
                            def.alphaId = res.getIdentifier(def.alphaName, "anim", pkg);
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

        private static Context resolveCustomThemeContext(Context anyContext) {
            String pkg = getCustomPackage(anyContext);
            if (pkg == null || pkg.isEmpty()) {
                Slog.w(TAG, "no custom rotation theme package configured");
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
                Slog.w(TAG, "custom rotation theme package not visible: " + pkg);
                return null;
            } catch (Throwable t) {
                sCustomThemeCtx = null;
                sCustomThemePackage = null;
                Slog.e(TAG, "failed to resolve custom rotation theme: " + pkg, t);
                return null;
            }
        }

        static void invalidateCustomThemeCache() {
            sCustomThemeCtx = null;
            sCustomThemePackage = null;
        }

        private static int resolveThemeAnim(Context themeCtx, String animName) {
            if (themeCtx == null || sCustomThemePackage == null) return 0;
            return themeCtx.getResources()
                    .getIdentifier(animName, "anim", sCustomThemePackage);
        }

        // ── Public API ──────────────────────────────────────────────

        /**
         * Loads custom exit animation for rotation.
         * Returns null if default behavior should be used.
         */
        static Animation loadCustomExitAnimation(Context context, int delta) {
            if (Boolean.TRUE.equals(sInHook.get())) return null;
            sInHook.set(Boolean.TRUE);
            try {
                int style = getRotationStyle(context);
                if (style == STYLE_DEFAULT) return null;

                if (style == STYLE_CUSTOM_APK) {
                    return loadFromCustomTheme(context, ANIM_ROTATE_EXIT);
                }

                AnimDef def = STYLES.get(style);
                if (def == null) return null;
                if (!ensureResources(context)) return null;
                if (def.exitId == 0) return null;

                return AnimationUtils.loadAnimation(sModuleCtx, def.exitId);
            } catch (Throwable t) {
                Slog.e(TAG, "loadCustomExitAnimation failed", t);
                return null;
            } finally {
                sInHook.set(Boolean.FALSE);
            }
        }

        /**
         * Loads custom enter animation for rotation.
         * Returns null if default behavior should be used.
         */
        static Animation loadCustomEnterAnimation(Context context, int delta) {
            if (Boolean.TRUE.equals(sInHook.get())) return null;
            sInHook.set(Boolean.TRUE);
            try {
                int style = getRotationStyle(context);
                if (style == STYLE_DEFAULT) return null;

                if (style == STYLE_CUSTOM_APK) {
                    return loadFromCustomTheme(context, ANIM_ROTATE_ENTER);
                }

                AnimDef def = STYLES.get(style);
                if (def == null) return null;
                if (!ensureResources(context)) return null;
                if (def.enterId == 0) return null;

                return AnimationUtils.loadAnimation(sModuleCtx, def.enterId);
            } catch (Throwable t) {
                Slog.e(TAG, "loadCustomEnterAnimation failed", t);
                return null;
            } finally {
                sInHook.set(Boolean.FALSE);
            }
        }

        /**
         * Loads custom alpha animation for rotation (used in custom rotate mode).
         * Returns null if default behavior should be used.
         */
        static Animation loadCustomAlphaAnimation(Context context) {
            if (Boolean.TRUE.equals(sInHook.get())) return null;
            sInHook.set(Boolean.TRUE);
            try {
                int style = getRotationStyle(context);
                if (style == STYLE_DEFAULT) return null;

                if (style == STYLE_CUSTOM_APK) {
                    return loadFromCustomTheme(context, ANIM_ROTATE_ALPHA);
                }

                AnimDef def = STYLES.get(style);
                if (def == null) return null;
                if (!ensureResources(context)) return null;
                if (def.alphaId == 0) return null;

                return AnimationUtils.loadAnimation(sModuleCtx, def.alphaId);
            } catch (Throwable t) {
                Slog.e(TAG, "loadCustomAlphaAnimation failed", t);
                return null;
            } finally {
                sInHook.set(Boolean.FALSE);
            }
        }

        private static Animation loadFromCustomTheme(Context context, String animName) {
            Context themeCtx = resolveCustomThemeContext(context);
            if (themeCtx == null) return null;
            int resId = resolveThemeAnim(themeCtx, animName);
            if (resId == 0) {
                Slog.w(TAG, "custom theme missing resource: " + animName);
                return null;
            }
            return AnimationUtils.loadAnimation(themeCtx, resId);
        }
    }

    // END Evolution X: Custom rotation animation support ──────────────────────
}
