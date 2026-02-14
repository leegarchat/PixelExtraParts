/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.widget;

import android.animation.ValueAnimator;
import android.annotation.ColorInt;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.compat.Compatibility;
import android.compat.annotation.ChangeId;
import android.compat.annotation.EnabledSince;
import android.compat.annotation.UnsupportedAppUsage;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RecordingCanvas;
import android.graphics.Rect;
import android.graphics.RenderNode;
import android.os.Build;
import android.util.AttributeSet;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

// =========================================================================================
// [CUSTOM INJECTION START] - Imports
// =========================================================================================
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import java.util.WeakHashMap;
// =========================================================================================
// [CUSTOM INJECTION END]
// =========================================================================================

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * This class performs the graphical effect used at the edges of scrollable widgets
 * when the user scrolls beyond the content bounds in 2D space.
 *
 * <p>EdgeEffect is stateful. Custom widgets using EdgeEffect should create an
 * instance for each edge that should show the effect, feed it input data using
 * the methods {@link #onAbsorb(int)}, {@link #onPull(float)}, and {@link #onRelease()},
 * and draw the effect using {@link #draw(Canvas)} in the widget's overridden
 * {@link android.view.View#draw(Canvas)} method. If {@link #isFinished()} returns
 * false after drawing, the edge effect's animation is not yet complete and the widget
 * should schedule another drawing pass to continue the animation.</p>
 *
 * <p>When drawing, widgets should draw their main content and child views first,
 * usually by invoking <code>super.draw(canvas)</code> from an overridden <code>draw</code>
 * method. (This will invoke onDraw and dispatch drawing to child views as needed.)
 * The edge effect may then be drawn on top of the view's content using the
 * {@link #draw(Canvas)} method.</p>
 */
public class EdgeEffect {
    /**
     * This sets the edge effect to use stretch instead of glow.
     *
     * @hide
     */
    @ChangeId
    @EnabledSince(targetSdkVersion = Build.VERSION_CODES.BASE)
    public static final long USE_STRETCH_EDGE_EFFECT_BY_DEFAULT = 171228096L;

    /**
     * The default blend mode used by {@link EdgeEffect}.
     */
    public static final BlendMode DEFAULT_BLEND_MODE = BlendMode.SRC_ATOP;

    /**
     * Completely disable edge effect
     */
    private static final int TYPE_NONE = -1;

    /**
     * Use a color edge glow for the edge effect.
     */
    private static final int TYPE_GLOW = 0;

    /**
     * Use a stretch for the edge effect.
     */
    private static final int TYPE_STRETCH = 1;

    /**
     * The velocity threshold before the spring animation is considered settled.
     * The idea here is that velocity should be less than 0.1 pixel per second.
     */
    private static final double VELOCITY_THRESHOLD = 0.01;

    /**
     * The speed at which we should start linearly interpolating to the destination.
     * When using a spring, as it gets closer to the destination, the speed drops off exponentially.
     * Instead of landing very slowly, a better experience is achieved if the final
     * destination is arrived at quicker.
     */
    private static final float LINEAR_VELOCITY_TAKE_OVER = 200f;

    /**
     * The value threshold before the spring animation is considered close enough to
     * the destination to be settled. This should be around 0.01 pixel.
     */
    private static final double VALUE_THRESHOLD = 0.001;

    /**
     * The maximum distance at which we should start linearly interpolating to the destination.
     * When using a spring, as it gets closer to the destination, the speed drops off exponentially.
     * Instead of landing very slowly, a better experience is achieved if the final
     * destination is arrived at quicker.
     */
    private static final double LINEAR_DISTANCE_TAKE_OVER = 8.0;

    /**
     * The natural frequency of the stretch spring.
     */
    private static final double NATURAL_FREQUENCY = 24.657;

    /**
     * The damping ratio of the stretch spring.
     */
    private static final double DAMPING_RATIO = 0.98;

    /**
     * The variation of the velocity for the stretch effect when it meets the bound.
     * if value is > 1, it will accentuate the absorption of the movement.
     */
    private static final float ON_ABSORB_VELOCITY_ADJUSTMENT = 13f;

    /** @hide */
    @IntDef({TYPE_NONE, TYPE_GLOW, TYPE_STRETCH})
    @Retention(RetentionPolicy.SOURCE)
    public @interface EdgeEffectType {
    }

    private static final float LINEAR_STRETCH_INTENSITY = 0.016f;

    private static final float EXP_STRETCH_INTENSITY = 0.016f;

    private static final float SCROLL_DIST_AFFECTED_BY_EXP_STRETCH = 0.33f;

    @SuppressWarnings("UnusedDeclaration")
    private static final String TAG = "EdgeEffect";

    // Time it will take the effect to fully recede in ms
    private static final int RECEDE_TIME = 600;

    // Time it will take before a pulled glow begins receding in ms
    private static final int PULL_TIME = 167;

    // Time it will take in ms for a pulled glow to decay to partial strength before release
    private static final int PULL_DECAY_TIME = 2000;

    private static final float MAX_ALPHA = 0.15f;
    private static final float GLOW_ALPHA_START = .09f;

    private static final float MAX_GLOW_SCALE = 2.f;

    private static final float PULL_GLOW_BEGIN = 0.f;

    // Minimum velocity that will be absorbed
    private static final int MIN_VELOCITY = 100;
    // Maximum velocity, clamps at this value
    private static final int MAX_VELOCITY = 10000;

    private static final float EPSILON = 0.001f;

    private static final double ANGLE = Math.PI / 6;
    private static final float SIN = (float) Math.sin(ANGLE);
    private static final float COS = (float) Math.cos(ANGLE);
    private static final float RADIUS_FACTOR = 0.6f;

    private float mGlowAlpha;
    @UnsupportedAppUsage(maxTargetSdk = Build.VERSION_CODES.R, trackingBug = 170729553)
    private float mGlowScaleY;
    private float mDistance;
    private float mVelocity; // only for stretch animations

    private float mGlowAlphaStart;
    private float mGlowAlphaFinish;
    private float mGlowScaleYStart;
    private float mGlowScaleYFinish;

    private long mStartTime;
    private float mDuration;

    private final Interpolator mInterpolator = new DecelerateInterpolator();

    private static final int STATE_IDLE = 0;
    private static final int STATE_PULL = 1;
    private static final int STATE_ABSORB = 2;
    private static final int STATE_RECEDE = 3;
    private static final int STATE_PULL_DECAY = 4;

    private static final float PULL_DISTANCE_ALPHA_GLOW_FACTOR = 0.8f;

    private static final int VELOCITY_GLOW_FACTOR = 6;

    private int mState = STATE_IDLE;

    private float mPullDistance;

    private final Rect mBounds = new Rect();
    private float mWidth;
    private float mHeight;
    @UnsupportedAppUsage(maxTargetSdk = Build.VERSION_CODES.P, trackingBug = 123769450)
    private final Paint mPaint = new Paint();
    private float mRadius;
    private float mBaseGlowScale;
    private float mDisplacement = 0.5f;
    private float mTargetDisplacement = 0.5f;

    /**
     * Current edge effect type, consumers should always query
     * {@link #getCurrentEdgeEffectBehavior()} instead of this parameter
     * directly in case animations have been disabled (ex. for accessibility reasons)
     */
    private @EdgeEffectType int mEdgeEffectType = TYPE_GLOW;
    private Matrix mTmpMatrix = null;
    private float[] mTmpPoints = null;

    // =========================================================================================
    // [CUSTOM INJECTION START] - Custom Fields
    // =========================================================================================
    private SpringDynamics mCustomSpring;
    private Context mCustomContext; 
    
    // Animation state
    private float mCustomSmoothOffsetY = 0f;
    private float mCustomSmoothScale = 1.0f;
    private float mCustomSmoothZoom = 1.0f;
    private float mCustomSmoothHScale = 1.0f;
    private float mCustomLastDelta = 0f;
    private float mCustomTargetFingerX = 0.5f;
    private float mCustomCurrentFingerX = 0.5f;
    private boolean mCustomFirstTouch = true;
    
    private float mCustomScreenHeight = 2200f;
    private float mCustomScreenWidth = 1080f;
    
    private float mCfgScale = 1.0f;
    private boolean mCfgFilter = false;
    private boolean mCfgIgnore = false;

    // Caches and helpers
    private final Matrix mCustomMatrix = new Matrix();
    private final float[] mCustomPoints = new float[4];
    private static final WeakHashMap<Object, Boolean> sComposeCache = new WeakHashMap<>();

    // Settings Keys (Pine)
    private static final String KEY_ENABLED = "overscroll_enabled_pine";
    private static final String KEY_PACKAGES_CONFIG = "overscroll_packages_config_pine";
    private static final String KEY_PULL_COEFF = "overscroll_pull_pine";
    private static final String KEY_STIFFNESS = "overscroll_stiffness_pine";
    private static final String KEY_DAMPING = "overscroll_damping_pine";
    private static final String KEY_FLING = "overscroll_fling_pine";
    private static final String KEY_PHYSICS_MIN_VEL = "overscroll_physics_min_vel_pine";
    private static final String KEY_PHYSICS_MIN_VAL = "overscroll_physics_min_val_pine";
    private static final String KEY_ANIMATION_SPEED = "overscroll_anim_speed_pine";
    private static final String KEY_INPUT_SMOOTH_FACTOR = "overscroll_input_smooth_pine";
    private static final String KEY_RESISTANCE_EXPONENT = "overscroll_res_exponent_pine";
    private static final String KEY_LERP_MAIN_IDLE = "overscroll_lerp_main_idle_pine";
    private static final String KEY_LERP_MAIN_RUN = "overscroll_lerp_main_run_pine";
    private static final String KEY_COMPOSE_SCALE = "overscroll_compose_scale_pine";
    private static final String KEY_SCALE_MODE = "overscroll_scale_mode_pine";
    private static final String KEY_SCALE_INTENSITY = "overscroll_scale_intensity_pine";
    private static final String KEY_SCALE_LIMIT_MIN = "overscroll_scale_limit_min_pine";
    private static final String KEY_ZOOM_MODE = "overscroll_zoom_mode_pine";
    private static final String KEY_ZOOM_INTENSITY = "overscroll_zoom_intensity_pine";
    private static final String KEY_ZOOM_LIMIT_MIN = "overscroll_zoom_limit_min_pine";
    private static final String KEY_ZOOM_ANCHOR_X = "overscroll_zoom_anchor_x_pine";
    private static final String KEY_ZOOM_ANCHOR_Y = "overscroll_zoom_anchor_y_pine";
    private static final String KEY_H_SCALE_MODE = "overscroll_h_scale_mode_pine";
    private static final String KEY_H_SCALE_INTENSITY = "overscroll_h_scale_intensity_pine";
    private static final String KEY_H_SCALE_LIMIT_MIN = "overscroll_h_scale_limit_min_pine";
    private static final String KEY_SCALE_ANCHOR_Y = "overscroll_scale_anchor_y_pine";
    private static final String KEY_H_SCALE_ANCHOR_X = "overscroll_h_scale_anchor_x_pine";
    private static final String KEY_SCALE_ANCHOR_X_HORIZ = "overscroll_scale_anchor_x_horiz_pine";
    private static final String KEY_H_SCALE_ANCHOR_Y_HORIZ = "overscroll_h_scale_anchor_y_horiz_pine";
    private static final String KEY_ZOOM_ANCHOR_X_HORIZ = "overscroll_zoom_anchor_x_horiz_pine";
    private static final String KEY_ZOOM_ANCHOR_Y_HORIZ = "overscroll_zoom_anchor_y_horiz_pine";
    private static final String KEY_SCALE_INTENSITY_HORIZ = "overscroll_scale_intensity_horiz_pine";
    private static final String KEY_ZOOM_INTENSITY_HORIZ = "overscroll_zoom_intensity_horiz_pine";
    private static final String KEY_H_SCALE_INTENSITY_HORIZ = "overscroll_h_scale_intensity_horiz_pine";
    private static final String KEY_INVERT_ANCHOR = "overscroll_invert_anchor_pine";
    
    private static final float FILTER_THRESHOLD = 0.08f;

    // [OPTIMIZATION] CACHED SETTINGS VARIABLES
    // Эти переменные хранят значения настроек, чтобы не читать базу данных в каждом кадре draw()
    private boolean mCachedEnabled = false;
    private float mCachedPullCoeff;
    private float mCachedStiffness;
    private float mCachedDamping;
    private float mCachedFling;
    private float mCachedMinVel;
    private float mCachedMinVal;
    private float mCachedInputSmooth;
    private float mCachedAnimSpeedPercent;
    private float mCachedAnimSpeedMul;
    private float mCachedResExponent;
    private float mCachedLerpIdle;
    private float mCachedLerpRun;
    private float mCachedComposeScale;
    private boolean mCachedInvertAnchor;
    
    // Cached Visuals
    private int mCachedScaleMode;
    private float mCachedScaleInt;
    private float mCachedScaleIntHoriz;
    private float mCachedScaleLimit;
    private float mCachedScaleAnchorY;
    private float mCachedScaleAnchorXHoriz;

    private int mCachedZoomMode;
    private float mCachedZoomInt;
    private float mCachedZoomIntHoriz;
    private float mCachedZoomLimit;
    private float mCachedZoomAnchorX;
    private float mCachedZoomAnchorY;
    private float mCachedZoomAnchorXHoriz;
    private float mCachedZoomAnchorYHoriz;

    private int mCachedHScaleMode;
    private float mCachedHScaleInt;
    private float mCachedHScaleIntHoriz;
    private float mCachedHScaleLimit;
    private float mCachedHScaleAnchorX;
    private float mCachedHScaleAnchorYHoriz;
    // =========================================================================================
    // [CUSTOM INJECTION END]
    // =========================================================================================

    /**
     * Construct a new EdgeEffect with a theme appropriate for the provided context.
     * @param context Context used to provide theming and resource information for the EdgeEffect
     */
    public EdgeEffect(Context context) {
        this(context, null);
        // CUSTOM INJECTION
        initCustomInstance(context);
    }

    /**
     * Construct a new EdgeEffect with a theme appropriate for the provided context.
     * @param context Context used to provide theming and resource information for the EdgeEffect
     * @param attrs The attributes of the XML tag that is inflating the view
     */
    public EdgeEffect(@NonNull Context context, @Nullable AttributeSet attrs) {
        final TypedArray a = context.obtainStyledAttributes(
                attrs, com.android.internal.R.styleable.EdgeEffect);
        final int themeColor = a.getColor(
                com.android.internal.R.styleable.EdgeEffect_colorEdgeEffect, 0xff666666);
        mEdgeEffectType = Compatibility.isChangeEnabled(USE_STRETCH_EDGE_EFFECT_BY_DEFAULT)
                ? TYPE_STRETCH : TYPE_GLOW;
        a.recycle();

        mPaint.setAntiAlias(true);
        mPaint.setColor((themeColor & 0xffffff) | 0x33000000);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setBlendMode(DEFAULT_BLEND_MODE);
        // CUSTOM INJECTION
        initCustomInstance(context);
    }

    @EdgeEffectType
    private int getCurrentEdgeEffectBehavior() {
        if (!ValueAnimator.areAnimatorsEnabled()) {
            return TYPE_NONE;
        } else {
            return mEdgeEffectType;
        }
    }

    /**
     * Set the size of this edge effect in pixels.
     *
     * @param width Effect width in pixels
     * @param height Effect height in pixels
     */
    public void setSize(int width, int height) {
        final float r = width * RADIUS_FACTOR / SIN;
        final float y = COS * r;
        final float h = r - y;
        final float or = height * RADIUS_FACTOR / SIN;
        final float oy = COS * or;
        final float oh = or - oy;

        mRadius = r;
        mBaseGlowScale = h > 0 ? Math.min(oh / h, 1.f) : 1.f;

        mBounds.set(mBounds.left, mBounds.top, width, (int) Math.min(height, h));

        mWidth = width;
        mHeight = height;
    }

    /**
     * Reports if this EdgeEffect's animation is finished.
     */
    public boolean isFinished() {
        // =========================================================================================
        // [CUSTOM INJECTION START] - isFinished Logic
        // =========================================================================================
        if (mCachedEnabled && !mCfgIgnore) {
            float minVal = mCachedMinVal; // [OPTIMIZATION] Use cached

            if (mCustomSpring != null) {
                mCustomSpring.setSpeedMultiplier(mCachedAnimSpeedMul);
                if (mCustomSpring.isRunning()) {
                    mCustomSpring.doFrame(System.nanoTime());
                }

                float smooth = mCustomSmoothOffsetY;
                smooth += (mCustomSpring.mValue - smooth) * 0.35f;
                if (Math.abs(mCustomSpring.mValue) < 0.1f && Math.abs(smooth) < minVal * 2f) {
                    smooth = 0f;
                }
                mCustomSmoothOffsetY = smooth;

                boolean physicsDone = !mCustomSpring.isRunning() && Math.abs(mCustomSpring.mValue) < minVal;
                boolean visualDone = (Math.abs(smooth) < minVal);
                boolean fullyFinished = physicsDone && visualDone;

                if (physicsDone && !visualDone) {
                    float diff = Math.abs(smooth);
                    if (diff < minVal * 3) {
                        mCustomSmoothOffsetY = 0f;
                        fullyFinished = true;
                    }
                }

                if (fullyFinished) {
                    resetCustomState();
                    mState = STATE_IDLE;
                    mDistance = 0f;
                }
                return fullyFinished;
            }
            return true;
        }
        // =========================================================================================
        // [CUSTOM INJECTION END]
        // =========================================================================================
        return mState == STATE_IDLE;
    }

    /**
     * Immediately finish the current animation.
     */
    public void finish() {
        // =========================================================================================
        // [CUSTOM INJECTION START] - finish Logic
        // =========================================================================================
        if (mCachedEnabled && !mCfgIgnore) {
            if (mCustomSpring != null) {
                mCustomSpring.cancel();
                mCustomSpring.mValue = 0;
                mCustomSpring.mVelocity = 0;
            }
            resetCustomState();
        }
        // =========================================================================================
        // [CUSTOM INJECTION END]
        // =========================================================================================
        mState = STATE_IDLE;
        mDistance = 0;
        mVelocity = 0;
    }

    /**
     * A view should call this when content is pulled away from an edge by the user.
     */
    public void onPull(float deltaDistance) {
        onPull(deltaDistance, 0.5f);
    }

    /**
     * A view should call this when content is pulled away from an edge by the user.
     */
    public void onPull(float deltaDistance, float displacement) {
        // =========================================================================================
        // [CUSTOM INJECTION START] - onPull Logic
        // =========================================================================================
        updateSettings(); // [OPTIMIZATION] Update settings cache at start of gesture

        if (mCachedEnabled && !mCfgIgnore) {
            if (isComposeCaller()) {
                float composeDivisor = mCachedComposeScale;
                if (composeDivisor < 0.01f) composeDivisor = 1.0f;
                deltaDistance /= composeDivisor;
            }

            if (mCustomSpring == null) return;
            mCustomSpring.setSpeedMultiplier(mCachedAnimSpeedMul);

            if (mCfgFilter && Math.abs(deltaDistance) > FILTER_THRESHOLD) return;
            
            float correctedDelta = (Math.abs(mCfgScale) > 0.001f) ? deltaDistance / mCfgScale : deltaDistance;

            float inputSmoothFactor = mCachedInputSmooth;
            
            if (mCustomFirstTouch) {
                mCustomLastDelta = correctedDelta;
                mCustomFirstTouch = false;
            }

            boolean directionChanged = (correctedDelta > 0 && mCustomLastDelta < 0) || (correctedDelta < 0 && mCustomLastDelta > 0);
            float filteredDelta = directionChanged ? correctedDelta : (correctedDelta * (1.0f - inputSmoothFactor) + mCustomLastDelta * inputSmoothFactor);
            
            mCustomLastDelta = filteredDelta;
            mCustomTargetFingerX = displacement;

            mState = STATE_PULL;
            mCustomSpring.cancel();

            float currentTranslation = mCustomSpring.mValue;
            float effectiveSize = Math.max(Math.abs(mHeight), Math.abs(mWidth));
            if (effectiveSize < 1f) effectiveSize = mCustomScreenHeight;

            float rawMove = filteredDelta * effectiveSize;
            float pullCoeff = mCachedPullCoeff;
            float resExponent = mCachedResExponent;

            boolean isPullingAway = (currentTranslation > 0 && rawMove > 0) || (currentTranslation < 0 && rawMove < 0);
            float change;

            if (pullCoeff >= 1.0f) {
                change = rawMove * pullCoeff;
            } else {
                if (isPullingAway) {
                    float ratio = Math.min(Math.abs(currentTranslation) / mCustomScreenHeight, 1f);
                    float resistance = (float) Math.pow(1.0f - ratio, resExponent);
                    change = rawMove * resistance;
                } else {
                    change = rawMove;
                }
            }

            float nextTranslation = currentTranslation + change;
            
            // Предотвращение пересечения нуля
            if ((currentTranslation > 0 && nextTranslation < 0) || (currentTranslation < 0 && nextTranslation > 0)) {
                nextTranslation = 0f;
            }

            mCustomSpring.mValue = nextTranslation;
            mDistance = nextTranslation / effectiveSize;
            return;
        }
        // =========================================================================================
        // [CUSTOM INJECTION END]
        // =========================================================================================

        int edgeEffectBehavior = getCurrentEdgeEffectBehavior();
        if (edgeEffectBehavior == TYPE_NONE) {
            finish();
            return;
        }
        final long now = AnimationUtils.currentAnimationTimeMillis();
        mTargetDisplacement = displacement;
        if (mState == STATE_PULL_DECAY && now - mStartTime < mDuration
                && edgeEffectBehavior == TYPE_GLOW) {
            return;
        }
        if (mState != STATE_PULL) {
            if (edgeEffectBehavior == TYPE_STRETCH) {
                mPullDistance = mDistance;
            } else {
                mGlowScaleY = Math.max(PULL_GLOW_BEGIN, mGlowScaleY);
            }
        }
        mState = STATE_PULL;

        mStartTime = now;
        mDuration = PULL_TIME;

        mPullDistance += deltaDistance;
        if (edgeEffectBehavior == TYPE_STRETCH) {
            mPullDistance = Math.min(1f, mPullDistance);
        }
        mDistance = Math.max(0f, mPullDistance);
        mVelocity = 0;

        if (mPullDistance == 0) {
            mGlowScaleY = mGlowScaleYStart = 0;
            mGlowAlpha = mGlowAlphaStart = 0;
        } else {
            final float absdd = Math.abs(deltaDistance);
            mGlowAlpha = mGlowAlphaStart = Math.min(MAX_ALPHA,
                    mGlowAlpha + (absdd * PULL_DISTANCE_ALPHA_GLOW_FACTOR));

            final float scale = (float) (Math.max(0, 1 - 1 /
                    Math.sqrt(Math.abs(mPullDistance) * mBounds.height()) - 0.3d) / 0.7d);

            mGlowScaleY = mGlowScaleYStart = scale;
        }

        mGlowAlphaFinish = mGlowAlpha;
        mGlowScaleYFinish = mGlowScaleY;
        if (edgeEffectBehavior == TYPE_STRETCH && mDistance == 0) {
            mState = STATE_IDLE;
        }
    }

    public float onPullDistance(float deltaDistance, float displacement) {
        int edgeEffectBehavior = getCurrentEdgeEffectBehavior();
        if (edgeEffectBehavior == TYPE_NONE) {
            return 0f;
        }
        float finalDistance = Math.max(0f, deltaDistance + mDistance);
        float delta = finalDistance - mDistance;
        if (delta == 0f && mDistance == 0f) {
            return 0f; // No pull, don't do anything.
        }

        if (mState != STATE_PULL && mState != STATE_PULL_DECAY && edgeEffectBehavior == TYPE_GLOW) {
            mPullDistance = mDistance;
            mState = STATE_PULL;
        }
        onPull(delta, displacement);
        return delta;
    }

    public float getDistance() {
        return mDistance;
    }

    public void onRelease() {
        // =========================================================================================
        // [CUSTOM INJECTION START] - onRelease Logic
        // =========================================================================================
        if (mCachedEnabled && !mCfgIgnore) {
            mPullDistance = 0;
            if (mCustomSpring != null && Math.abs(mCustomSpring.mValue) > 0.5f) {
                mCustomSpring.setSpeedMultiplier(mCachedAnimSpeedMul);
                // [OPTIMIZATION] Use cached values
                float stiffness = mCachedStiffness;
                float damping = mCachedDamping;
                float minVel = mCachedMinVel;
                float minVal = mCachedMinVal;

                mCustomSpring.setParams(stiffness, damping, minVel, minVal);
                mCustomSpring.setTargetValue(0);
                mCustomSpring.setVelocity(0);
                mCustomSpring.start();
                mState = STATE_RECEDE;
            } else {
                mState = STATE_IDLE;
                mDistance = 0f;
            }

            mCustomTargetFingerX = 0.5f;
            mCustomLastDelta = 0f;
            mCustomFirstTouch = true;
            return;
        }
        // =========================================================================================
        // [CUSTOM INJECTION END]
        // =========================================================================================

        mPullDistance = 0;

        if (mState != STATE_PULL && mState != STATE_PULL_DECAY) {
            return;
        }

        mState = STATE_RECEDE;
        mGlowAlphaStart = mGlowAlpha;
        mGlowScaleYStart = mGlowScaleY;

        mGlowAlphaFinish = 0.f;
        mGlowScaleYFinish = 0.f;
        mVelocity = 0.f;

        mStartTime = AnimationUtils.currentAnimationTimeMillis();
        mDuration = RECEDE_TIME;
    }

    public void onAbsorb(int velocity) {
        // =========================================================================================
        // [CUSTOM INJECTION START] - onAbsorb Logic
        // =========================================================================================
        updateSettings(); // [OPTIMIZATION] Update settings cache at start of gesture

        if (mCachedEnabled && !mCfgIgnore) {
            mPullDistance = 0;
            mState = STATE_RECEDE;

            if (mCustomSpring != null) {
                mCustomSpring.setSpeedMultiplier(mCachedAnimSpeedMul);
                mCustomSpring.cancel();

                // [OPTIMIZATION] Use cached values
                float flingMult = mCachedFling;
                float stiffness = mCachedStiffness;
                float damping = mCachedDamping;
                float minVel = mCachedMinVel;
                float minVal = mCachedMinVal;

                float velocityPx = velocity * flingMult;
                if (flingMult > 1.0f) stiffness /= flingMult;

                float maxVel = mCustomScreenHeight * 10f;
                if (Math.abs(velocityPx) > maxVel) velocityPx = Math.signum(velocityPx) * maxVel;

                mCustomSpring.setParams(stiffness, damping, minVel, minVal);
                mCustomSpring.setTargetValue(0);
                mCustomSpring.setVelocity(velocityPx);
                mCustomSpring.start();
            }
            
            mCustomTargetFingerX = 0.5f;
            mCustomLastDelta = 0f;
            mCustomFirstTouch = true;
            return;
        }
        // =========================================================================================
        // [CUSTOM INJECTION END]
        // =========================================================================================

        int edgeEffectBehavior = getCurrentEdgeEffectBehavior();
        if (edgeEffectBehavior == TYPE_STRETCH) {
            mState = STATE_RECEDE;
            mVelocity = velocity * ON_ABSORB_VELOCITY_ADJUSTMENT;
            mStartTime = AnimationUtils.currentAnimationTimeMillis();
        } else if (edgeEffectBehavior == TYPE_GLOW) {
            mState = STATE_ABSORB;
            mVelocity = 0;
            velocity = Math.min(Math.max(MIN_VELOCITY, Math.abs(velocity)), MAX_VELOCITY);

            mStartTime = AnimationUtils.currentAnimationTimeMillis();
            mDuration = 0.15f + (velocity * 0.02f);

            mGlowAlphaStart = GLOW_ALPHA_START;
            mGlowScaleYStart = Math.max(mGlowScaleY, 0.f);

            mGlowScaleYFinish = Math.min(0.025f + (velocity * (velocity / 100) * 0.00015f) / 2,
                    1.f);
            mGlowAlphaFinish = Math.max(
                    mGlowAlphaStart,
                    Math.min(velocity * VELOCITY_GLOW_FACTOR * .00001f, MAX_ALPHA));
            mTargetDisplacement = 0.5f;
        } else {
            finish();
        }
    }

    public void setColor(@ColorInt int color) {
        mPaint.setColor(color);
    }

    public void setBlendMode(@Nullable BlendMode blendmode) {
        mPaint.setBlendMode(blendmode);
    }

    @ColorInt
    public int getColor() {
        return mPaint.getColor();
    }

    @Nullable
    public BlendMode getBlendMode() {
        return mPaint.getBlendMode();
    }

    public boolean draw(Canvas canvas) {
        // =========================================================================================
        // [CUSTOM INJECTION START] - draw Logic
        // =========================================================================================
        if (mCachedEnabled && !mCfgIgnore) {
            if (!canvas.isHardwareAccelerated()) {
                finish();
                return false;
            }
            if (mCustomSpring == null) {
                finish();
                return false;
            }

            mCustomSpring.setSpeedMultiplier(mCachedAnimSpeedMul);
            if (mCustomSpring.isRunning()) mCustomSpring.doFrame(System.nanoTime());

            if (!(canvas instanceof RecordingCanvas)) {
                finish();
                return false;
            }
            RecordingCanvas recordingCanvas = (RecordingCanvas) canvas;
            RenderNode renderNode = recordingCanvas.mNode;
            
            if (renderNode == null) {
                finish();
                return false;
            }

            canvas.getMatrix(mCustomMatrix);
            mCustomPoints[0] = 0; mCustomPoints[1] = 1;
            mCustomMatrix.mapVectors(mCustomPoints);

            float vx = mCustomPoints[0];
            float vy = mCustomPoints[1];

            if (Math.abs(vx) > Math.abs(vy)) {
                vx = Math.signum(vx);
                vy = 0f;
            } else {
                vy = Math.signum(vy);
                vx = 0f;
            }

            boolean isVertical = (vy != 0);

            // [OPTIMIZATION] Use cached values
            float lerpMainIdle = mCachedLerpIdle;
            float lerpMainRun = mCachedLerpRun;
            float lerpFactorMain = mCustomSpring.isRunning() ? lerpMainRun : lerpMainIdle;
            lerpFactorMain = Math.min(1.0f, lerpFactorMain * mCachedAnimSpeedMul);

            float targetOffset = mCustomSpring.mValue;
            float currentOffset = mCustomSmoothOffsetY;
            float newOffset = currentOffset + (targetOffset - currentOffset) * lerpFactorMain;

            float minVal = mCachedMinVal;
            if (Math.abs(targetOffset - newOffset) < 0.5f) newOffset = targetOffset;
            if (Math.abs(targetOffset) < 0.1f && Math.abs(newOffset) < minVal) newOffset = 0f;

            mCustomSmoothOffsetY = newOffset;

            float maxDistance = isVertical ? mCustomScreenHeight : mCustomScreenWidth;
            float ratio = (maxDistance > 0) ? Math.min(Math.abs(newOffset) / maxDistance, 1.0f) : 0f;
            boolean isActive = Math.abs(newOffset) > 1.0f;

            float targetScaleV = 1f, targetScaleZ = 1f, targetScaleH = 1f;

            if (isActive) {
                // [OPTIMIZATION] Use cached values instead of lookups
                if (isVertical) {
                    targetScaleV = calcScale(mCachedScaleMode, mCachedScaleInt, mCachedScaleLimit, ratio);
                    targetScaleZ = calcScale(mCachedZoomMode, mCachedZoomInt, mCachedZoomLimit, ratio);
                    targetScaleH = calcScale(mCachedHScaleMode, mCachedHScaleInt, mCachedHScaleLimit, ratio);
                } else {
                    targetScaleV = calcScale(mCachedScaleMode, mCachedScaleIntHoriz, mCachedScaleLimit, ratio);
                    targetScaleZ = calcScale(mCachedZoomMode, mCachedZoomIntHoriz, mCachedZoomLimit, ratio);
                    targetScaleH = calcScale(mCachedHScaleMode, mCachedHScaleIntHoriz, mCachedHScaleLimit, ratio);
                }
            }

            float newScaleV = lerp(mCustomSmoothScale, targetScaleV, lerpFactorMain);
            float newScaleZ = lerp(mCustomSmoothZoom, targetScaleZ, lerpFactorMain);
            float newScaleH = lerp(mCustomSmoothHScale, targetScaleH, lerpFactorMain);

            mCustomSmoothScale = newScaleV;
            mCustomSmoothZoom = newScaleZ;
            mCustomSmoothHScale = newScaleH;

            boolean isResting = Math.abs(newOffset) < 0.1f && Math.abs(newScaleV - 1f) < 0.001f;
            if (isResting && !mCustomSpring.isRunning()) {
                renderNode.setTranslationX(0f);
                renderNode.setTranslationY(0f);
                renderNode.setScaleX(1f);
                renderNode.setScaleY(1f);
                
                float stretchW = (mWidth > 0) ? (float)mWidth : 1.0f;
                float stretchH = (mHeight > 0) ? (float)mHeight : 1.0f;
                renderNode.stretch(0f, 0f, stretchW, stretchH); // [FIX] Passing correct W/H
                resetCustomState();
                mState = STATE_IDLE;
                mDistance = 0f;
                mVelocity = 0f;
                if (mCustomSpring != null) {
                    mCustomSpring.cancel();
                    mCustomSpring.mValue = 0f;
                    mCustomSpring.mVelocity = 0f;
                }
                return false;
            }

            float effectiveSize = Math.max(Math.abs(mHeight), Math.abs(mWidth));
            if (effectiveSize < 1f) effectiveSize = mCustomScreenHeight;
            mDistance = newOffset / effectiveSize;

            renderNode.setTranslationX(newOffset * vx);
            renderNode.setTranslationY(newOffset * vy);

            float axisMainScale = newScaleV * newScaleZ;
            float axisCrossScale = newScaleH * newScaleZ;

            float finalScaleX, finalScaleY;

            if (isVertical) {
                finalScaleX = axisCrossScale;
                finalScaleY = axisMainScale;
            } else {
                finalScaleX = axisMainScale;
                finalScaleY = axisCrossScale;
            }

            float ax = 0.5f;
            float ay = 0.5f;
            
            // [OPTIMIZATION] Use cached boolean checks
            boolean zoomActive = mCachedZoomMode != 0;
            boolean scaleActive = mCachedScaleMode != 0;
            boolean hScaleActive = mCachedHScaleMode != 0;

            if (isVertical) {
                if (zoomActive) {
                    ax = mCachedZoomAnchorX;
                    ay = mCachedZoomAnchorY;
                } else if (scaleActive) {
                    ax = 0.5f;
                    ay = mCachedScaleAnchorY;
                } else if (hScaleActive) {
                    ax = mCachedHScaleAnchorX;
                    ay = 0.5f;
                }
            } else {
                if (zoomActive) {
                    ax = mCachedZoomAnchorXHoriz;
                    ay = mCachedZoomAnchorYHoriz;
                } else if (scaleActive) {
                    ax = mCachedScaleAnchorXHoriz;
                    ay = 0.5f;
                } else if (hScaleActive) {
                    ax = 0.5f;
                    ay = mCachedHScaleAnchorYHoriz;
                }
            }

            boolean invertAnchor = mCachedInvertAnchor;
            float pivotX, pivotY;
            float canvasW = (float) canvas.getWidth();
            float canvasH = (float) canvas.getHeight();

            if (isVertical) {
                pivotX = canvasW * ax;
                if (vy > 0) {
                    pivotY = canvasH * ay;
                } else {
                    pivotY = canvasH * (invertAnchor ? (1.0f - ay) : ay);
                }
            } else {
                pivotY = canvasH * ay;
                if (vx > 0) {
                    pivotX = canvasW * ax;
                } else {
                    pivotX = canvasW * (invertAnchor ? (1.0f - ax) : ax);
                }
            }

            renderNode.setPivotX(pivotX);
            renderNode.setPivotY(pivotY);
            renderNode.setScaleX(finalScaleX);
            renderNode.setScaleY(finalScaleY);
            
            float stretchW = (mWidth > 0) ? (float)mWidth : 1.0f;
            float stretchH = (mHeight > 0) ? (float)mHeight : 1.0f;
            renderNode.stretch(0f, 0f, stretchW, stretchH); // [FIX] Passing correct W/H

            boolean continueAnim = mCustomSpring.isRunning()
                    || Math.abs(newOffset) >= mCachedMinVal
                    || Math.abs(newScaleV - 1f) >= 0.001f
                    || Math.abs(newScaleZ - 1f) >= 0.001f
                    || Math.abs(newScaleH - 1f) >= 0.001f;

            if (!continueAnim) {
                renderNode.setTranslationX(0f);
                renderNode.setTranslationY(0f);
                renderNode.setScaleX(1f);
                renderNode.setScaleY(1f);
                renderNode.stretch(0f, 0f, stretchW, stretchH);
                resetCustomState();
                mState = STATE_IDLE;
                mDistance = 0f;
                mVelocity = 0f;
                if (mCustomSpring != null) {
                    mCustomSpring.cancel();
                    mCustomSpring.mValue = 0f;
                    mCustomSpring.mVelocity = 0f;
                }
            }

            return continueAnim;
        }
        // =========================================================================================
        // [CUSTOM INJECTION END]
        // =========================================================================================

        int edgeEffectBehavior = getCurrentEdgeEffectBehavior();
        if (edgeEffectBehavior == TYPE_GLOW) {
            update();
            final int count = canvas.save();

            final float centerX = mBounds.centerX();
            final float centerY = mBounds.height() - mRadius;

            canvas.scale(1.f, Math.min(mGlowScaleY, 1.f) * mBaseGlowScale, centerX, 0);

            final float displacement = Math.max(0, Math.min(mDisplacement, 1.f)) - 0.5f;
            float translateX = mBounds.width() * displacement / 2;

            canvas.clipRect(mBounds);
            canvas.translate(translateX, 0);
            mPaint.setAlpha((int) (0xff * mGlowAlpha));
            canvas.drawCircle(centerX, centerY, mRadius, mPaint);
            canvas.restoreToCount(count);
        } else if (edgeEffectBehavior == TYPE_STRETCH && canvas instanceof RecordingCanvas) {
            if (mState == STATE_RECEDE) {
                updateSpring();
            }
            if (mDistance != 0f) {
                RecordingCanvas recordingCanvas = (RecordingCanvas) canvas;
                if (mTmpMatrix == null) {
                    mTmpMatrix = new Matrix();
                    mTmpPoints = new float[12];
                }
                //noinspection deprecation
                recordingCanvas.getMatrix(mTmpMatrix);

                mTmpPoints[0] = 0;
                mTmpPoints[1] = 0; // top-left
                mTmpPoints[2] = mWidth;
                mTmpPoints[3] = 0; // top-right
                mTmpPoints[4] = mWidth;
                mTmpPoints[5] = mHeight; // bottom-right
                mTmpPoints[6] = 0;
                mTmpPoints[7] = mHeight; // bottom-left
                mTmpPoints[8] = mWidth * mDisplacement;
                mTmpPoints[9] = 0; // drag start point
                mTmpPoints[10] = mWidth * mDisplacement;
                mTmpPoints[11] = mHeight * mDistance; // drag point
                mTmpMatrix.mapPoints(mTmpPoints);

                RenderNode renderNode = recordingCanvas.mNode;

                float left = renderNode.getLeft()
                    + min(mTmpPoints[0], mTmpPoints[2], mTmpPoints[4], mTmpPoints[6]);
                float top = renderNode.getTop()
                    + min(mTmpPoints[1], mTmpPoints[3], mTmpPoints[5], mTmpPoints[7]);
                float right = renderNode.getLeft()
                    + max(mTmpPoints[0], mTmpPoints[2], mTmpPoints[4], mTmpPoints[6]);
                float bottom = renderNode.getTop()
                    + max(mTmpPoints[1], mTmpPoints[3], mTmpPoints[5], mTmpPoints[7]);
                // assume rotations of increments of 90 degrees
                float x = mTmpPoints[10] - mTmpPoints[8];
                float width = right - left;
                float vecX = dampStretchVector(Math.max(-1f, Math.min(1f, x / width)));

                float y = mTmpPoints[11] - mTmpPoints[9];
                float height = bottom - top;
                float vecY = dampStretchVector(Math.max(-1f, Math.min(1f, y / height)));

                boolean hasValidVectors = Float.isFinite(vecX) && Float.isFinite(vecY);
                if (right > left && bottom > top && mWidth > 0 && mHeight > 0 && hasValidVectors) {
                    renderNode.stretch(
                        vecX, // horizontal stretch intensity
                        vecY, // vertical stretch intensity
                        mWidth, // max horizontal stretch in pixels
                        mHeight // max vertical stretch in pixels
                    );
                }
            }
        } else {
            // Animations have been disabled or this is TYPE_STRETCH and drawing into a Canvas
            // that isn't a Recording Canvas, so no effect can be shown. Just end the effect.
            mState = STATE_IDLE;
            mDistance = 0;
            mVelocity = 0;
        }

        boolean oneLastFrame = false;
        if (mState == STATE_RECEDE && mDistance == 0 && mVelocity == 0) {
            mState = STATE_IDLE;
            oneLastFrame = true;
        }

        return mState != STATE_IDLE || oneLastFrame;
    }

    private float min(float f1, float f2, float f3, float f4) {
        float min = Math.min(f1, f2);
        min = Math.min(min, f3);
        return Math.min(min, f4);
    }

    private float max(float f1, float f2, float f3, float f4) {
        float max = Math.max(f1, f2);
        max = Math.max(max, f3);
        return Math.max(max, f4);
    }

    /**
     * Return the maximum height that the edge effect will be drawn at given the original
     * {@link #setSize(int, int) input size}.
     * @return The maximum height of the edge effect
     */
    public int getMaxHeight() {
        return (int) mHeight;
    }

    private void update() {
        final long time = AnimationUtils.currentAnimationTimeMillis();
        final float t = Math.min((time - mStartTime) / mDuration, 1.f);

        final float interp = mInterpolator.getInterpolation(t);

        mGlowAlpha = mGlowAlphaStart + (mGlowAlphaFinish - mGlowAlphaStart) * interp;
        mGlowScaleY = mGlowScaleYStart + (mGlowScaleYFinish - mGlowScaleYStart) * interp;
        if (mState != STATE_PULL) {
            mDistance = calculateDistanceFromGlowValues(mGlowScaleY, mGlowAlpha);
        }
        mDisplacement = (mDisplacement + mTargetDisplacement) / 2;

        if (t >= 1.f - EPSILON) {
            switch (mState) {
                case STATE_ABSORB:
                    mState = STATE_RECEDE;
                    mStartTime = AnimationUtils.currentAnimationTimeMillis();
                    mDuration = RECEDE_TIME;

                    mGlowAlphaStart = mGlowAlpha;
                    mGlowScaleYStart = mGlowScaleY;

                    // After absorb, the glow should fade to nothing.
                    mGlowAlphaFinish = 0.f;
                    mGlowScaleYFinish = 0.f;
                    break;
                case STATE_PULL:
                    mState = STATE_PULL_DECAY;
                    mStartTime = AnimationUtils.currentAnimationTimeMillis();
                    mDuration = PULL_DECAY_TIME;

                    mGlowAlphaStart = mGlowAlpha;
                    mGlowScaleYStart = mGlowScaleY;

                    // After pull, the glow should fade to nothing.
                    mGlowAlphaFinish = 0.f;
                    mGlowScaleYFinish = 0.f;
                    break;
                case STATE_PULL_DECAY:
                    mState = STATE_RECEDE;
                    break;
                case STATE_RECEDE:
                    mState = STATE_IDLE;
                    break;
            }
        }
    }

    private void updateSpring() {
        final long time = AnimationUtils.currentAnimationTimeMillis();
        final float deltaT = (time - mStartTime) / 1000f; // Convert from millis to seconds
        if (deltaT < 0.001f) {
            return; // Must have at least 1 ms difference
        }
        mStartTime = time;

        if (Math.abs(mVelocity) <= LINEAR_VELOCITY_TAKE_OVER
                && Math.abs(mDistance * mHeight) < LINEAR_DISTANCE_TAKE_OVER
                && Math.signum(mVelocity) == -Math.signum(mDistance)
        ) {
            // This is close. The spring will slowly reach the destination. Instead, we
            // will interpolate linearly so that it arrives at its destination quicker.
            mVelocity = Math.signum(mVelocity) * LINEAR_VELOCITY_TAKE_OVER;

            float targetDistance = mDistance + (mVelocity * deltaT / mHeight);
            if (Math.signum(targetDistance) != Math.signum(mDistance)) {
                // We have arrived
                mDistance = 0;
                mVelocity = 0;
            } else {
                mDistance = targetDistance;
            }
            return;
        }
        final double mDampedFreq = NATURAL_FREQUENCY * Math.sqrt(1 - DAMPING_RATIO * DAMPING_RATIO);

        // We're always underdamped, so we can use only those equations:
        double cosCoeff = mDistance * mHeight;
        double sinCoeff = (1 / mDampedFreq) * (DAMPING_RATIO * NATURAL_FREQUENCY
                * mDistance * mHeight + mVelocity);
        double distance = Math.pow(Math.E, -DAMPING_RATIO * NATURAL_FREQUENCY * deltaT)
                * (cosCoeff * Math.cos(mDampedFreq * deltaT)
                + sinCoeff * Math.sin(mDampedFreq * deltaT));
        double velocity = distance * (-NATURAL_FREQUENCY) * DAMPING_RATIO
                + Math.pow(Math.E, -DAMPING_RATIO * NATURAL_FREQUENCY * deltaT)
                * (-mDampedFreq * cosCoeff * Math.sin(mDampedFreq * deltaT)
                + mDampedFreq * sinCoeff * Math.cos(mDampedFreq * deltaT));
        mDistance = (float) distance / mHeight;
        mVelocity = (float) velocity;
        if (mDistance > 1f) {
            mDistance = 1f;
            mVelocity = 0f;
        }
        if (isAtEquilibrium()) {
            mDistance = 0;
            mVelocity = 0;
        }
    }

    /**
     * @return The estimated pull distance as calculated from mGlowScaleY.
     */
    private float calculateDistanceFromGlowValues(float scale, float alpha) {
        if (scale >= 1f) {
            // It should asymptotically approach 1, but not reach there.
            // Here, we're just choosing a value that is large.
            return 1f;
        }
        if (scale > 0f) {
            float v = 1f / 0.7f / (mGlowScaleY - 1f);
            return v * v / mBounds.height();
        }
        return alpha / PULL_DISTANCE_ALPHA_GLOW_FACTOR;
    }

    /**
     * @return true if the spring used for calculating the stretch animation is
     * considered at rest or false if it is still animating.
     */
    private boolean isAtEquilibrium() {
        double displacement = mDistance * mHeight; // in pixels
        double velocity = mVelocity;

        // Don't allow displacement to drop below 0. We don't want it stretching the opposite
        // direction if it is flung that way. We also want to stop the animation as soon as
        // it gets very close to its destination.
        return displacement < 0 || (Math.abs(velocity) < VELOCITY_THRESHOLD
                && displacement < VALUE_THRESHOLD);
    }

    private float dampStretchVector(float normalizedVec) {
        float sign = normalizedVec > 0 ? 1f : -1f;
        float overscroll = Math.abs(normalizedVec);
        float linearIntensity = LINEAR_STRETCH_INTENSITY * overscroll;
        double scalar = Math.E / SCROLL_DIST_AFFECTED_BY_EXP_STRETCH;
        double expIntensity = EXP_STRETCH_INTENSITY * (1 - Math.exp(-overscroll * scalar));
        return sign * (float) (linearIntensity + expIntensity);
    }
    
    // =========================================================================================
    // [CUSTOM INJECTION START] - Helper Methods
    // =========================================================================================
    private void initCustomInstance(Context context) {
        mCustomContext = context;
        mCustomSpring = new SpringDynamics();
        
        // Defaults
        float screenHeight = 2200f;
        float screenWidth = 1080f;
        try {
            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (wm != null) {
                DisplayMetrics dm = new DisplayMetrics();
                wm.getDefaultDisplay().getMetrics(dm);
                screenHeight = dm.heightPixels;
                screenWidth = dm.widthPixels;
            }
        } catch (Exception ignored) {}
        mCustomScreenHeight = screenHeight;
        mCustomScreenWidth = screenWidth;

        // Package config check
        updatePackageConfig();
    }

    private void updatePackageConfig() {
        if (mCustomContext == null) return;
        try {
            String pkgName = mCustomContext.getPackageName();
            String configString = getStringSetting(KEY_PACKAGES_CONFIG);
            if (!TextUtils.isEmpty(configString) && pkgName != null) {
                String[] apps = configString.split(" ");
                for (String appConfig : apps) {
                    String[] parts = appConfig.split(":");
                    if (parts.length >= 3 && parts[0].equals(pkgName)) {
                        mCfgFilter = Integer.parseInt(parts[1]) == 1;
                        mCfgScale = Float.parseFloat(parts[2]);
                        if (parts.length >= 4) mCfgIgnore = parts[3].equals("1");
                        break;
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    // [OPTIMIZATION] New method to batch-update settings
    private void updateSettings() {
        if (mCustomContext == null) return;
        
        // Read main toggle first
        mCachedEnabled = getIntSetting(KEY_ENABLED, 1) == 1;
        
        if (mCachedEnabled) {
             mCachedPullCoeff = getFloatSetting(KEY_PULL_COEFF, 0.5f);
             mCachedStiffness = getFloatSetting(KEY_STIFFNESS, 450f);
             mCachedDamping = getFloatSetting(KEY_DAMPING, 0.7f);
             mCachedFling = getFloatSetting(KEY_FLING, 0.6f);
             mCachedMinVel = getFloatSetting(KEY_PHYSICS_MIN_VEL, 8.0f);
             mCachedMinVal = getFloatSetting(KEY_PHYSICS_MIN_VAL, 0.6f);
             mCachedInputSmooth = getFloatSetting(KEY_INPUT_SMOOTH_FACTOR, 0.5f);
             mCachedAnimSpeedPercent = getFloatSetting(KEY_ANIMATION_SPEED, 100.0f);
             if (mCachedAnimSpeedPercent < 1.0f) mCachedAnimSpeedPercent = 1.0f;
             if (mCachedAnimSpeedPercent > 300.0f) mCachedAnimSpeedPercent = 300.0f;
             mCachedAnimSpeedMul = mCachedAnimSpeedPercent / 100.0f;
             mCachedResExponent = getFloatSetting(KEY_RESISTANCE_EXPONENT, 4.0f);
             mCachedLerpIdle = getFloatSetting(KEY_LERP_MAIN_IDLE, 0.4f);
             mCachedLerpRun = getFloatSetting(KEY_LERP_MAIN_RUN, 0.7f);
             mCachedComposeScale = getFloatSetting(KEY_COMPOSE_SCALE, 3.33f);
             mCachedInvertAnchor = getIntSetting(KEY_INVERT_ANCHOR, 1) == 1;
             
             // Visuals
             mCachedScaleMode = getIntSetting(KEY_SCALE_MODE, 0);
             mCachedScaleInt = getFloatSetting(KEY_SCALE_INTENSITY, 0.0f);
             mCachedScaleIntHoriz = getFloatSetting(KEY_SCALE_INTENSITY_HORIZ, 0.0f);
             mCachedScaleLimit = getFloatSetting(KEY_SCALE_LIMIT_MIN, 0.3f);
             mCachedScaleAnchorY = getFloatSetting(KEY_SCALE_ANCHOR_Y, 0.5f);
             mCachedScaleAnchorXHoriz = getFloatSetting(KEY_SCALE_ANCHOR_X_HORIZ, 0.5f);

             mCachedZoomMode = getIntSetting(KEY_ZOOM_MODE, 0);
             mCachedZoomInt = getFloatSetting(KEY_ZOOM_INTENSITY, 0.0f);
             mCachedZoomIntHoriz = getFloatSetting(KEY_ZOOM_INTENSITY_HORIZ, 0.0f);
             mCachedZoomLimit = getFloatSetting(KEY_ZOOM_LIMIT_MIN, 0.3f);
             mCachedZoomAnchorX = getFloatSetting(KEY_ZOOM_ANCHOR_X, 0.5f);
             mCachedZoomAnchorY = getFloatSetting(KEY_ZOOM_ANCHOR_Y, 0.5f);
             mCachedZoomAnchorXHoriz = getFloatSetting(KEY_ZOOM_ANCHOR_X_HORIZ, 0.5f);
             mCachedZoomAnchorYHoriz = getFloatSetting(KEY_ZOOM_ANCHOR_Y_HORIZ, 0.5f);

             mCachedHScaleMode = getIntSetting(KEY_H_SCALE_MODE, 0);
             mCachedHScaleInt = getFloatSetting(KEY_H_SCALE_INTENSITY, 0.0f);
             mCachedHScaleIntHoriz = getFloatSetting(KEY_H_SCALE_INTENSITY_HORIZ, 0.0f);
             mCachedHScaleLimit = getFloatSetting(KEY_H_SCALE_LIMIT_MIN, 0.3f);
             mCachedHScaleAnchorX = getFloatSetting(KEY_H_SCALE_ANCHOR_X, 0.5f);
             mCachedHScaleAnchorYHoriz = getFloatSetting(KEY_H_SCALE_ANCHOR_Y_HORIZ, 0.5f);
        }
    }

    private void resetCustomState() {
        mCustomSmoothOffsetY = 0f;
        mCustomSmoothScale = 1.0f;
        mCustomSmoothZoom = 1.0f;
        mCustomSmoothHScale = 1.0f;
        mCustomTargetFingerX = 0.5f;
        mCustomCurrentFingerX = 0.5f;
        mCustomFirstTouch = true;
    }

    private boolean isComposeCaller() {
        if (sComposeCache.containsKey(this)) return Boolean.TRUE.equals(sComposeCache.get(this));
        boolean isCompose = false;
        try {
            StackTraceElement[] stack = Thread.currentThread().getStackTrace();
            for (StackTraceElement element : stack) {
                if (element.getClassName().startsWith("androidx.compose")) {
                    isCompose = true;
                    break;
                }
            }
        } catch (Exception ignored) {}
        sComposeCache.put(this, isCompose);
        return isCompose;
    }

    private float getFloatSetting(String key, float def) {
        if (mCustomContext == null) return def;
        try {
            return Settings.Global.getFloat(mCustomContext.getContentResolver(), key, def);
        } catch (Exception e1) {
            try {
                int intValue = Settings.Global.getInt(mCustomContext.getContentResolver(), key, (int)(def * 100f));
                return intValue / 100f;
            } catch (Exception e2) {
                return def;
            }
        }
    }

    private int getIntSetting(String key, int def) {
        if (mCustomContext == null) return def;
        try {
            return Settings.Global.getInt(mCustomContext.getContentResolver(), key, def);
        } catch (Exception ignored) { return def; }
    }

    private String getStringSetting(String key) {
        if (mCustomContext == null) return null;
        try {
            return Settings.Global.getString(mCustomContext.getContentResolver(), key);
        } catch (Exception ignored) { return null; }
    }

    // [OPTIMIZATION] Changed args to accept raw values instead of keys
    private float calcScale(int mode, float intensity, float limit, float ratio) {
        if (mode == 0 || intensity <= 0) return 1.0f;
        if (mode == 1) return Math.max(1.0f - (ratio * intensity), limit);
        if (mode == 2) return 1.0f + (ratio * intensity);
        return 1.0f;
    }

    private float lerp(float start, float end, float factor) {
        return start + (end - start) * factor;
    }

    private static class SpringDynamics {
        private float mStiffness = 450.0f;
        private float mDampingRatio = 0.7f;
        private float mMinVel = 1.0f;
        private float mMinVal = 0.5f;
        private float mSpeedMultiplier = 1.0f;

        public float mValue;
        public float mVelocity;
        public float mTargetValue = 0f;
        private boolean mIsRunning = false;
        private long mLastFrameTimeNanos = 0;

        public void setParams(float stiffness, float damping, float minVel, float minVal) {
            mStiffness = stiffness > 0 ? stiffness : 0.1f;
            mDampingRatio = damping >= 0 ? damping : 0;
            mMinVel = minVel;
            mMinVal = minVal;
        }

        public void setSpeedMultiplier(float speedMultiplier) {
            if (speedMultiplier < 0.01f) speedMultiplier = 0.01f;
            mSpeedMultiplier = speedMultiplier;
        }

        public void setTargetValue(float targetValue) { mTargetValue = targetValue; }
        public void setVelocity(float velocity) { mVelocity = velocity; }
        public boolean isRunning() { return mIsRunning; }
        public void cancel() { mIsRunning = false; }

        public void start() {
            if (mIsRunning) return;
            mIsRunning = true;
            mLastFrameTimeNanos = System.nanoTime();
        }

        public void doFrame(long frameTimeNanos) {
            if (!mIsRunning) return;
            long deltaTimeNanos = frameTimeNanos - mLastFrameTimeNanos;
            if (deltaTimeNanos > 100_000_000) deltaTimeNanos = 16_000_000;
            mLastFrameTimeNanos = frameTimeNanos;
            float dt = (deltaTimeNanos / 1_000_000_000.0f) * mSpeedMultiplier;

            float displacement = mValue - mTargetValue;
            float dampingCoefficient = 2 * mDampingRatio * (float) Math.sqrt(mStiffness);
            float force = -mStiffness * displacement - dampingCoefficient * mVelocity;

            if (Float.isNaN(force) || Float.isInfinite(force)) force = 0;

            mVelocity += force * dt;
            mValue += mVelocity * dt;

            if (Float.isNaN(mValue) || Float.isInfinite(mValue)) {
                mValue = mTargetValue;
                mVelocity = 0;
                cancel();
                return;
            }

            if (Math.abs(mVelocity) < mMinVel && Math.abs(mValue - mTargetValue) < mMinVal) {
                mValue = mTargetValue;
                mVelocity = 0;
                cancel();
            }
        }
    }
    // =========================================================================================
    // [CUSTOM INJECTION END]
    // =========================================================================================
}