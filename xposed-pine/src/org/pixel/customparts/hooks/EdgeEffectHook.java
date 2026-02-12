package org.pixel.customparts.hooks;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import android.widget.EdgeEffect;

import java.lang.reflect.Method;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;











public class EdgeEffectHook {
    private static final String TAG = "PixelPartsOverscroll";

    
    
    

    
    private static String sKeySuffix = "_xposed";

    






    public static void configure(boolean useGlobal, String suffix) {
        if (!useGlobal) {
            Log.w(TAG, "Settings.Secure is no longer supported. Forcing Settings.Global.");
        }
        sKeySuffix = suffix;
    }

    private static String resolveKey(String key) {
        String base = key.replaceAll("_(xposed|pine)$", "");
        return base + sKeySuffix;
    }

    
    
    

    private static final String FIELD_SPRING = "mCustomSpring";
    private static final String FIELD_CONTEXT = "mCustomContext";
    private static final String FIELD_CFG_SCALE = "mCfgScale";
    private static final String FIELD_CFG_IGNORE = "mCfgIgnore";
    private static final String FIELD_CFG_FILTER = "mCfgFilter";
    private static final String FIELD_SMOOTH_OFFSET_Y = "mCustomSmoothOffsetY";
    private static final String FIELD_SMOOTH_SCALE = "mCustomSmoothScale";
    private static final String FIELD_LAST_DELTA = "mCustomLastDelta";
    private static final String FIELD_TARGET_FINGER_X = "mCustomTargetFingerX";
    private static final String FIELD_CURRENT_FINGER_X = "mCustomCurrentFingerX";
    private static final String FIELD_SCREEN_HEIGHT = "mCustomScreenHeight";
    private static final String FIELD_SCREEN_WIDTH = "mCustomScreenWidth";
    private static final String FIELD_FIRST_TOUCH = "mCustomFirstTouch";
    private static final String FIELD_SMOOTH_ZOOM = "mCustomSmoothZoom";
    private static final String FIELD_SMOOTH_H_SCALE = "mCustomSmoothHScale";
    private static final String FIELD_MATRIX = "mCustomMatrix";
    private static final String FIELD_POINTS = "mCustomPoints";

    
    
    

    private static final String KEY_ENABLED = "overscroll_enabled";
    private static final String KEY_PACKAGES_CONFIG = "overscroll_packages_config";
    private static final String KEY_PULL_COEFF = "overscroll_pull";
    private static final String KEY_STIFFNESS = "overscroll_stiffness";
    private static final String KEY_DAMPING = "overscroll_damping";
    private static final String KEY_FLING = "overscroll_fling";
    private static final String KEY_PHYSICS_MIN_VEL = "overscroll_physics_min_vel";
    private static final String KEY_PHYSICS_MIN_VAL = "overscroll_physics_min_val";
    private static final String KEY_INPUT_SMOOTH_FACTOR = "overscroll_input_smooth";
    private static final String KEY_RESISTANCE_EXPONENT = "overscroll_res_exponent";
    private static final String KEY_LERP_MAIN_IDLE = "overscroll_lerp_main_idle";
    private static final String KEY_LERP_MAIN_RUN = "overscroll_lerp_main_run";
    private static final String KEY_COMPOSE_SCALE = "overscroll_compose_scale";
    private static final String KEY_SCALE_MODE = "overscroll_scale_mode";
    private static final String KEY_SCALE_INTENSITY = "overscroll_scale_intensity";
    private static final String KEY_SCALE_LIMIT_MIN = "overscroll_scale_limit_min";
    private static final String KEY_ZOOM_MODE = "overscroll_zoom_mode";
    private static final String KEY_ZOOM_INTENSITY = "overscroll_zoom_intensity";
    private static final String KEY_ZOOM_LIMIT_MIN = "overscroll_zoom_limit_min";
    private static final String KEY_ZOOM_ANCHOR_X = "overscroll_zoom_anchor_x";
    private static final String KEY_ZOOM_ANCHOR_Y = "overscroll_zoom_anchor_y";
    private static final String KEY_H_SCALE_MODE = "overscroll_h_scale_mode";
    private static final String KEY_H_SCALE_INTENSITY = "overscroll_h_scale_intensity";
    private static final String KEY_H_SCALE_LIMIT_MIN = "overscroll_h_scale_limit_min";
    private static final String KEY_SCALE_ANCHOR_Y = "overscroll_scale_anchor_y";
    private static final String KEY_H_SCALE_ANCHOR_X = "overscroll_h_scale_anchor_x";
    private static final String KEY_SCALE_ANCHOR_X_HORIZ = "overscroll_scale_anchor_x_horiz";
    private static final String KEY_H_SCALE_ANCHOR_Y_HORIZ = "overscroll_h_scale_anchor_y_horiz";
    private static final String KEY_ZOOM_ANCHOR_X_HORIZ = "overscroll_zoom_anchor_x_horiz";
    private static final String KEY_ZOOM_ANCHOR_Y_HORIZ = "overscroll_zoom_anchor_y_horiz";
    private static final String KEY_SCALE_INTENSITY_HORIZ = "overscroll_scale_intensity_horiz";
    private static final String KEY_ZOOM_INTENSITY_HORIZ = "overscroll_zoom_intensity_horiz";
    private static final String KEY_H_SCALE_INTENSITY_HORIZ = "overscroll_h_scale_intensity_horiz";
    private static final String KEY_INVERT_ANCHOR = "overscroll_invert_anchor";
    private static final float FILTER_THRESHOLD = 0.08f;

    private static final WeakHashMap<Object, Boolean> sComposeCache = new WeakHashMap<>();
    private static Method sSetTranslationX, sSetTranslationY, sSetScaleX, sSetScaleY, sSetPivotX, sSetPivotY;
    private static boolean sReflectionInited = false;

    



    public static void initWithClassLoader(ClassLoader classLoader) {
        Class<?> edgeClass = XposedHelpers.findClass("android.widget.EdgeEffect", classLoader);
        hookEdgeEffect(edgeClass);
    }

    private static void hookEdgeEffect(Class<?> edgeClass) {
        
        XposedHelpers.findAndHookConstructor(edgeClass, Context.class, AttributeSet.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                initInstance(param.thisObject, (Context) param.args[0]);
            }
        });

        XposedHelpers.findAndHookConstructor(edgeClass, Context.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                initInstance(param.thisObject, (Context) param.args[0]);
            }
        });

        
        XposedHelpers.findAndHookMethod(edgeClass, "isFinished", new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                EdgeEffect thiz = (EdgeEffect) param.thisObject;
                Context ctx = (Context) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_CONTEXT);

                if (!isBounceEnabled(ctx, thiz)) {
                    return XposedBridge.invokeOriginalMethod(param.method, thiz, param.args);
                }

                SpringDynamics mSpring = (SpringDynamics) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_SPRING);
                Float smoothY = (Float) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_SMOOTH_OFFSET_Y);
                float minVal = getFloatSetting(ctx, KEY_PHYSICS_MIN_VAL, 4.0f);

                if (mSpring != null) {
                    boolean physicsDone = !mSpring.isRunning() && Math.abs(mSpring.mValue) < minVal;
                    boolean visualDone = (smoothY == null || Math.abs(smoothY) < minVal);
                    boolean fullyFinished = physicsDone && visualDone;

                    if (physicsDone && !visualDone && smoothY != null) {
                        float diff = Math.abs(smoothY);
                        if (diff < minVal * 3) {
                            XposedHelpers.setAdditionalInstanceField(thiz, FIELD_SMOOTH_OFFSET_Y, 0f);
                            fullyFinished = true;
                        }
                    }

                    if (fullyFinished) {
                        ensureReset(thiz);
                        XposedHelpers.setIntField(thiz, "mState", 0);
                        XposedHelpers.setFloatField(thiz, "mDistance", 0f);
                    }
                    return fullyFinished;
                }
                return true;
            }
        });

        
        XposedHelpers.findAndHookMethod(edgeClass, "finish", new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                EdgeEffect thiz = (EdgeEffect) param.thisObject;
                Context ctx = (Context) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_CONTEXT);

                if (!isBounceEnabled(ctx, thiz)) {
                    return XposedBridge.invokeOriginalMethod(param.method, thiz, param.args);
                }

                SpringDynamics mSpring = (SpringDynamics) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_SPRING);
                if (mSpring != null) {
                    mSpring.cancel();
                    mSpring.mValue = 0;
                    mSpring.mVelocity = 0;
                }

                XposedHelpers.setIntField(thiz, "mState", 0);
                XposedHelpers.setFloatField(thiz, "mDistance", 0f);
                resetState(thiz);
                return null;
            }
        });

        
        XC_MethodReplacement onPullHook = new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                EdgeEffect thiz = (EdgeEffect) param.thisObject;
                Context ctx = (Context) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_CONTEXT);

                if (!isBounceEnabled(ctx, thiz)) {
                    return XposedBridge.invokeOriginalMethod(param.method, thiz, param.args);
                }

                float deltaDistance = (float) param.args[0];
                float displacement = (param.args.length > 1) ? (float) param.args[1] : 0.5f;

                if (isComposeCaller(thiz)) {
                    float composeDivisor = getFloatSetting(ctx, KEY_COMPOSE_SCALE, 3.33f);
                    if (composeDivisor < 0.01f) composeDivisor = 1.0f;
                    deltaDistance /= composeDivisor;
                }

                SpringDynamics mSpring = (SpringDynamics) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_SPRING);
                if (mSpring == null) return deltaDistance;

                Float cfgScaleObj = (Float) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_CFG_SCALE);
                Boolean cfgFilterObj = (Boolean) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_CFG_FILTER);
                float cfgScale = (cfgScaleObj != null) ? cfgScaleObj : 1.0f;
                boolean cfgFilter = (cfgFilterObj != null) ? cfgFilterObj : false;

                if (cfgFilter && Math.abs(deltaDistance) > FILTER_THRESHOLD) return deltaDistance;
                float correctedDelta = (Math.abs(cfgScale) > 0.001f) ? deltaDistance / cfgScale : deltaDistance;

                float inputSmoothFactor = getFloatSetting(ctx, KEY_INPUT_SMOOTH_FACTOR, 0.5f);
                Float lastDeltaObj = (Float) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_LAST_DELTA);
                float lastDelta = (lastDeltaObj != null) ? lastDeltaObj : 0f;
                Boolean firstTouchObj = (Boolean) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_FIRST_TOUCH);
                boolean isFirstTouch = (firstTouchObj != null) ? firstTouchObj : true;

                if (isFirstTouch) {
                    lastDelta = correctedDelta;
                    XposedHelpers.setAdditionalInstanceField(thiz, FIELD_FIRST_TOUCH, false);
                }

                boolean directionChanged = (correctedDelta > 0 && lastDelta < 0) || (correctedDelta < 0 && lastDelta > 0);
                float filteredDelta = directionChanged ? correctedDelta : (correctedDelta * (1.0f - inputSmoothFactor) + lastDelta * inputSmoothFactor);
                XposedHelpers.setAdditionalInstanceField(thiz, FIELD_LAST_DELTA, filteredDelta);
                XposedHelpers.setAdditionalInstanceField(thiz, FIELD_TARGET_FINGER_X, displacement);

                XposedHelpers.setIntField(thiz, "mState", 1);
                mSpring.cancel();

                float currentTranslation = mSpring.mValue;

                float mHeight = XposedHelpers.getFloatField(thiz, "mHeight");
                float mWidth = XposedHelpers.getFloatField(thiz, "mWidth");
                Float screenHObj = (Float) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_SCREEN_HEIGHT);
                float screenHeight = (screenHObj != null) ? screenHObj : 2200f;
                float effectiveSize = Math.max(Math.abs(mHeight), Math.abs(mWidth));
                if (effectiveSize < 1f) effectiveSize = screenHeight;

                float rawMove = filteredDelta * effectiveSize;
                float pullCoeff = getFloatSetting(ctx, KEY_PULL_COEFF, 0.5f);
                float resExponent = getFloatSetting(ctx, KEY_RESISTANCE_EXPONENT, 4.0f);

                boolean isPullingAway = (currentTranslation > 0 && rawMove > 0) || (currentTranslation < 0 && rawMove < 0);
                float change;

                if (pullCoeff >= 1.0f) {
                    change = rawMove * pullCoeff;
                } else {
                    if (isPullingAway) {
                        float ratio = Math.min(Math.abs(currentTranslation) / screenHeight, 1f);
                        float resistance = (float) Math.pow(1.0f - ratio, resExponent);
                        change = rawMove * resistance;
                    } else {
                        change = rawMove;
                    }
                }

                float nextTranslation = currentTranslation + change;
                if ((currentTranslation > 0 && nextTranslation < 0) || (currentTranslation < 0 && nextTranslation > 0)) {
                    nextTranslation = 0f;
                }

                mSpring.mValue = nextTranslation;
                XposedHelpers.setFloatField(thiz, "mDistance", nextTranslation / effectiveSize);

                return deltaDistance;
            }
        };

        XposedHelpers.findAndHookMethod(edgeClass, "onPull", float.class, float.class, onPullHook);
        XposedHelpers.findAndHookMethod(edgeClass, "onPull", float.class, onPullHook);

        
        XposedHelpers.findAndHookMethod(edgeClass, "onRelease", new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                EdgeEffect thiz = (EdgeEffect) param.thisObject;
                Context ctx = (Context) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_CONTEXT);

                if (!isBounceEnabled(ctx, thiz)) {
                    return XposedBridge.invokeOriginalMethod(param.method, thiz, param.args);
                }

                SpringDynamics mSpring = (SpringDynamics) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_SPRING);
                if (mSpring != null && Math.abs(mSpring.mValue) > 0.5f) {
                    float stiffness = getFloatSetting(ctx, KEY_STIFFNESS, 450f);
                    float damping = getFloatSetting(ctx, KEY_DAMPING, 0.7f);
                    float minVel = getFloatSetting(ctx, KEY_PHYSICS_MIN_VEL, 80.0f);
                    float minVal = getFloatSetting(ctx, KEY_PHYSICS_MIN_VAL, 4.0f);

                    mSpring.setParams(stiffness, damping, minVel, minVal);
                    mSpring.setTargetValue(0);
                    mSpring.setVelocity(0);
                    mSpring.start();
                    XposedHelpers.setIntField(thiz, "mState", 3);
                } else {
                    XposedHelpers.setIntField(thiz, "mState", 0);
                    XposedHelpers.setFloatField(thiz, "mDistance", 0f);
                }

                XposedHelpers.setAdditionalInstanceField(thiz, FIELD_TARGET_FINGER_X, 0.5f);
                XposedHelpers.setAdditionalInstanceField(thiz, FIELD_LAST_DELTA, 0f);
                XposedHelpers.setAdditionalInstanceField(thiz, FIELD_FIRST_TOUCH, true);
                return null;
            }
        });

        
        XposedHelpers.findAndHookMethod(edgeClass, "onAbsorb", int.class, new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                EdgeEffect thiz = (EdgeEffect) param.thisObject;
                Context ctx = (Context) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_CONTEXT);

                if (!isBounceEnabled(ctx, thiz)) {
                    return XposedBridge.invokeOriginalMethod(param.method, thiz, param.args);
                }

                int velocity = (int) param.args[0];
                XposedHelpers.setIntField(thiz, "mState", 3);
                SpringDynamics mSpring = (SpringDynamics) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_SPRING);

                if (mSpring != null) {
                    mSpring.cancel();

                    float flingMult = getFloatSetting(ctx, KEY_FLING, 0.6f);
                    float stiffness = getFloatSetting(ctx, KEY_STIFFNESS, 450f);
                    float damping = getFloatSetting(ctx, KEY_DAMPING, 0.7f);
                    float minVel = getFloatSetting(ctx, KEY_PHYSICS_MIN_VEL, 80.0f);
                    float minVal = getFloatSetting(ctx, KEY_PHYSICS_MIN_VAL, 4.0f);

                    float velocityPx = velocity * flingMult;
                    if (flingMult > 1.0f) stiffness /= flingMult;

                    Float screenHObj = (Float) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_SCREEN_HEIGHT);
                    float screenHeight = (screenHObj != null) ? screenHObj : 2200f;
                    float maxVel = screenHeight * 10f;

                    if (Math.abs(velocityPx) > maxVel) velocityPx = Math.signum(velocityPx) * maxVel;

                    mSpring.setParams(stiffness, damping, minVel, minVal);
                    mSpring.setTargetValue(0);
                    mSpring.setVelocity(velocityPx);
                    mSpring.start();
                }
                XposedHelpers.setAdditionalInstanceField(thiz, FIELD_TARGET_FINGER_X, 0.5f);
                XposedHelpers.setAdditionalInstanceField(thiz, FIELD_LAST_DELTA, 0f);
                XposedHelpers.setAdditionalInstanceField(thiz, FIELD_FIRST_TOUCH, true);
                return null;
            }
        });

        
        XposedHelpers.findAndHookMethod(edgeClass, "draw", Canvas.class, new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                EdgeEffect thiz = (EdgeEffect) param.thisObject;
                Canvas canvas = (Canvas) param.args[0];
                Context ctx = (Context) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_CONTEXT);

                if (!isBounceEnabled(ctx, thiz)) return XposedBridge.invokeOriginalMethod(param.method, thiz, param.args);
                if (!canvas.isHardwareAccelerated()) return false;

                SpringDynamics mSpring = (SpringDynamics) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_SPRING);
                if (mSpring == null) return false;

                if (mSpring.isRunning()) mSpring.doFrame(System.nanoTime());

                Object renderNode = null;
                try {
                    renderNode = XposedHelpers.getObjectField(canvas, "mNode");
                } catch (Throwable t) { return false; }
                if (renderNode == null) return false;

                ensureReflection();

                Matrix matrix = (Matrix) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_MATRIX);
                float[] vecCache = (float[]) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_POINTS);

                canvas.getMatrix(matrix);
                vecCache[0] = 0; vecCache[1] = 1;
                matrix.mapVectors(vecCache);

                float vx = vecCache[0];
                float vy = vecCache[1];

                if (Math.abs(vx) > Math.abs(vy)) {
                    vx = Math.signum(vx);
                    vy = 0f;
                } else {
                    vy = Math.signum(vy);
                    vx = 0f;
                }

                boolean isVertical = (vy != 0);

                float lerpMainIdle = getFloatSetting(ctx, KEY_LERP_MAIN_IDLE, 0.4f);
                float lerpMainRun = getFloatSetting(ctx, KEY_LERP_MAIN_RUN, 0.7f);
                float lerpFactorMain = mSpring.isRunning() ? lerpMainRun : lerpMainIdle;

                float targetOffset = mSpring.mValue;
                Float currentOffsetObj = (Float) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_SMOOTH_OFFSET_Y);
                float currentOffset = (currentOffsetObj != null) ? currentOffsetObj : 0f;
                float newOffset = currentOffset + (targetOffset - currentOffset) * lerpFactorMain;

                float minVal = getFloatSetting(ctx, KEY_PHYSICS_MIN_VAL, 4.0f);
                if (Math.abs(targetOffset - newOffset) < 0.5f) newOffset = targetOffset;
                if (Math.abs(targetOffset) < 0.1f && Math.abs(newOffset) < minVal) newOffset = 0f;

                XposedHelpers.setAdditionalInstanceField(thiz, FIELD_SMOOTH_OFFSET_Y, newOffset);

                Float scrH = (Float) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_SCREEN_HEIGHT);
                Float scrW = (Float) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_SCREEN_WIDTH);
                float screenHeight = (scrH != null) ? scrH : 2200f;
                float screenWidth = (scrW != null) ? scrW : 1080f;

                float maxDistance = isVertical ? screenHeight : screenWidth;
                float ratio = (maxDistance > 0) ? Math.min(Math.abs(newOffset) / maxDistance, 1.0f) : 0f;
                boolean isActive = Math.abs(newOffset) > 1.0f;

                boolean zoomActive = getIntSetting(ctx, KEY_ZOOM_MODE, 0) != 0;
                boolean scaleActive = getIntSetting(ctx, KEY_SCALE_MODE, 0) != 0;
                boolean hScaleActive = getIntSetting(ctx, KEY_H_SCALE_MODE, 0) != 0;

                float targetScaleV = 1f, targetScaleZ = 1f, targetScaleH = 1f;

                if (isActive) {
                    String keyScaleInt = isVertical ? KEY_SCALE_INTENSITY : KEY_SCALE_INTENSITY_HORIZ;
                    String keyZoomInt = isVertical ? KEY_ZOOM_INTENSITY : KEY_ZOOM_INTENSITY_HORIZ;
                    String keyHScaleInt = isVertical ? KEY_H_SCALE_INTENSITY : KEY_H_SCALE_INTENSITY_HORIZ;

                    targetScaleV = calcScale(ctx, KEY_SCALE_MODE, keyScaleInt, KEY_SCALE_LIMIT_MIN, ratio);
                    targetScaleZ = calcScale(ctx, KEY_ZOOM_MODE, keyZoomInt, KEY_ZOOM_LIMIT_MIN, ratio);
                    targetScaleH = calcScale(ctx, KEY_H_SCALE_MODE, keyHScaleInt, KEY_H_SCALE_LIMIT_MIN, ratio);
                }

                float newScaleV = lerp(getF(thiz, FIELD_SMOOTH_SCALE, 1f), targetScaleV, lerpFactorMain);
                float newScaleZ = lerp(getF(thiz, FIELD_SMOOTH_ZOOM, 1f), targetScaleZ, lerpFactorMain);
                float newScaleH = lerp(getF(thiz, FIELD_SMOOTH_H_SCALE, 1f), targetScaleH, lerpFactorMain);

                XposedHelpers.setAdditionalInstanceField(thiz, FIELD_SMOOTH_SCALE, newScaleV);
                XposedHelpers.setAdditionalInstanceField(thiz, FIELD_SMOOTH_ZOOM, newScaleZ);
                XposedHelpers.setAdditionalInstanceField(thiz, FIELD_SMOOTH_H_SCALE, newScaleH);

                boolean isResting = Math.abs(newOffset) < 0.1f && Math.abs(newScaleV - 1f) < 0.001f;
                if (isResting && !mSpring.isRunning()) {
                    safeResetRenderNode(renderNode);
                    return false;
                }

                float mHeight = XposedHelpers.getFloatField(thiz, "mHeight");
                float mWidth = XposedHelpers.getFloatField(thiz, "mWidth");

                float canvasW = (float) canvas.getWidth();
                float canvasH = (float) canvas.getHeight();

                float effectiveSize = Math.max(Math.abs(mHeight), Math.abs(mWidth));
                if (effectiveSize < 1f) effectiveSize = screenHeight;
                XposedHelpers.setFloatField(thiz, "mDistance", newOffset / effectiveSize);

                try {
                    if (sSetTranslationX != null) {
                        sSetTranslationX.invoke(renderNode, newOffset * vx);
                        sSetTranslationY.invoke(renderNode, newOffset * vy);

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

                        if (isVertical) {
                            if (zoomActive) {
                                ax = getFloatSetting(ctx, KEY_ZOOM_ANCHOR_X, 0.5f);
                                ay = getFloatSetting(ctx, KEY_ZOOM_ANCHOR_Y, 0.5f);
                            } else if (scaleActive) {
                                ax = 0.5f;
                                ay = getFloatSetting(ctx, KEY_SCALE_ANCHOR_Y, 0.5f);
                            } else if (hScaleActive) {
                                ax = getFloatSetting(ctx, KEY_H_SCALE_ANCHOR_X, 0.5f);
                                ay = 0.5f;
                            }
                        } else {
                            if (zoomActive) {
                                ax = getFloatSetting(ctx, KEY_ZOOM_ANCHOR_X_HORIZ, 0.5f);
                                ay = getFloatSetting(ctx, KEY_ZOOM_ANCHOR_Y_HORIZ, 0.5f);
                            } else if (scaleActive) {
                                ax = getFloatSetting(ctx, KEY_SCALE_ANCHOR_X_HORIZ, 0.5f);
                                ay = 0.5f;
                            } else if (hScaleActive) {
                                ax = 0.5f;
                                ay = getFloatSetting(ctx, KEY_H_SCALE_ANCHOR_Y_HORIZ, 0.5f);
                            }
                        }

                        boolean invertAnchor = getIntSetting(ctx, KEY_INVERT_ANCHOR, 1) == 1;

                        float pivotX, pivotY;

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

                        sSetPivotX.invoke(renderNode, pivotX);
                        sSetPivotY.invoke(renderNode, pivotY);
                        sSetScaleX.invoke(renderNode, finalScaleX);
                        sSetScaleY.invoke(renderNode, finalScaleY);
                    }
                    XposedHelpers.callMethod(renderNode, "stretch", 0f, 0f, mWidth, mHeight);
                } catch (Throwable t) {}

                return true;
            }
        });
    }

    
    
    

    private static boolean isComposeCaller(Object thiz) {
        if (sComposeCache.containsKey(thiz)) return Boolean.TRUE.equals(sComposeCache.get(thiz));
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
        sComposeCache.put(thiz, isCompose);
        return isCompose;
    }

    private static void initInstance(Object thiz, Context context) {
        XposedHelpers.setAdditionalInstanceField(thiz, FIELD_CONTEXT, context);
        XposedHelpers.setAdditionalInstanceField(thiz, FIELD_SPRING, new SpringDynamics());
        XposedHelpers.setAdditionalInstanceField(thiz, FIELD_SMOOTH_OFFSET_Y, 0f);
        XposedHelpers.setAdditionalInstanceField(thiz, FIELD_SMOOTH_SCALE, 1.0f);
        XposedHelpers.setAdditionalInstanceField(thiz, FIELD_SMOOTH_ZOOM, 1.0f);
        XposedHelpers.setAdditionalInstanceField(thiz, FIELD_SMOOTH_H_SCALE, 1.0f);
        XposedHelpers.setAdditionalInstanceField(thiz, FIELD_MATRIX, new Matrix());
        XposedHelpers.setAdditionalInstanceField(thiz, FIELD_POINTS, new float[4]);
        XposedHelpers.setAdditionalInstanceField(thiz, FIELD_LAST_DELTA, 0f);
        XposedHelpers.setAdditionalInstanceField(thiz, FIELD_TARGET_FINGER_X, 0.5f);
        XposedHelpers.setAdditionalInstanceField(thiz, FIELD_CURRENT_FINGER_X, 0.5f);
        XposedHelpers.setAdditionalInstanceField(thiz, FIELD_FIRST_TOUCH, true);

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
        XposedHelpers.setAdditionalInstanceField(thiz, FIELD_SCREEN_HEIGHT, screenHeight);
        XposedHelpers.setAdditionalInstanceField(thiz, FIELD_SCREEN_WIDTH, screenWidth);

        
        float myScale = 1.0f;
        boolean myFilter = false;
        boolean myIgnore = false;
        try {
            String pkgName = context.getPackageName();
            String configString = getStringSetting(context, KEY_PACKAGES_CONFIG);
            if (!TextUtils.isEmpty(configString) && pkgName != null) {
                String[] apps = configString.split(" ");
                for (String appConfig : apps) {
                    String[] parts = appConfig.split(":");
                    if (parts.length >= 3 && parts[0].equals(pkgName)) {
                        myFilter = Integer.parseInt(parts[1]) == 1;
                        myScale = Float.parseFloat(parts[2]);
                        if (parts.length >= 4) myIgnore = parts[3].equals("1");
                        break;
                    }
                }
            }
        } catch (Exception ignored) {}
        XposedHelpers.setAdditionalInstanceField(thiz, FIELD_CFG_SCALE, myScale);
        XposedHelpers.setAdditionalInstanceField(thiz, FIELD_CFG_FILTER, myFilter);
        XposedHelpers.setAdditionalInstanceField(thiz, FIELD_CFG_IGNORE, myIgnore);
    }

    
    
    

    private static void ensureReset(Object thiz) { resetState(thiz); }

    private static void resetState(Object thiz) {
        XposedHelpers.setAdditionalInstanceField(thiz, FIELD_SMOOTH_OFFSET_Y, 0f);
        XposedHelpers.setAdditionalInstanceField(thiz, FIELD_SMOOTH_SCALE, 1.0f);
        XposedHelpers.setAdditionalInstanceField(thiz, FIELD_SMOOTH_ZOOM, 1.0f);
        XposedHelpers.setAdditionalInstanceField(thiz, FIELD_SMOOTH_H_SCALE, 1.0f);
        XposedHelpers.setAdditionalInstanceField(thiz, FIELD_TARGET_FINGER_X, 0.5f);
        XposedHelpers.setAdditionalInstanceField(thiz, FIELD_CURRENT_FINGER_X, 0.5f);
        XposedHelpers.setAdditionalInstanceField(thiz, FIELD_FIRST_TOUCH, true);
    }

    
    
    

    private static void ensureReflection() {
        if (sReflectionInited) return;
        try {
            Class<?> rnClass = null;
            try { rnClass = Class.forName("android.graphics.RenderNode"); } catch (ClassNotFoundException e) {
                try { rnClass = Class.forName("android.view.RenderNode"); } catch (ClassNotFoundException ignored) {}
            }
            if (rnClass != null) {
                sSetTranslationX = rnClass.getMethod("setTranslationX", float.class);
                sSetTranslationY = rnClass.getMethod("setTranslationY", float.class);
                sSetScaleX = rnClass.getMethod("setScaleX", float.class);
                sSetScaleY = rnClass.getMethod("setScaleY", float.class);
                sSetPivotX = rnClass.getMethod("setPivotX", float.class);
                sSetPivotY = rnClass.getMethod("setPivotY", float.class);
            }
            sReflectionInited = true;
        } catch (Exception ignored) {}
    }

    private static void safeResetRenderNode(Object renderNode) {
        if (renderNode == null) return;
        ensureReflection();
        try {
            if (sSetTranslationX != null) {
                sSetTranslationX.invoke(renderNode, 0f);
                sSetTranslationY.invoke(renderNode, 0f);
                sSetScaleX.invoke(renderNode, 1f);
                sSetScaleY.invoke(renderNode, 1f);
            }
            XposedHelpers.callMethod(renderNode, "stretch", 0f, 0f, 0f, 0f);
        } catch (Throwable ignored) {}
    }

    
    
    

    private static float calcScale(Context ctx, String modeKey, String intKey, String limKey, float ratio) {
        int mode = getIntSetting(ctx, modeKey, 0);
        float intensity = getFloatSetting(ctx, intKey, 0.0f);
        float limit = getFloatSetting(ctx, limKey, 0.3f);
        if (mode == 0 || intensity <= 0) return 1.0f;
        if (mode == 1) return Math.max(1.0f - (ratio * intensity), limit);
        if (mode == 2) return 1.0f + (ratio * intensity);
        return 1.0f;
    }

    private static float lerp(float start, float end, float factor) {
        return start + (end - start) * factor;
    }

    private static float getF(Object obj, String field, float def) {
        Float val = (Float) XposedHelpers.getAdditionalInstanceField(obj, field);
        return (val != null) ? val : def;
    }

    
    
    

    private static boolean isBounceEnabled(Context ctx, Object thiz) {
        if (ctx == null) return true;
        try {
            if (getIntSetting(ctx, KEY_ENABLED, 1) != 1) return false;
            if (thiz != null) {
                Boolean ignored = (Boolean) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_CFG_IGNORE);
                if (ignored != null && ignored) return false;
            }
            return true;
        } catch (Exception ignored) { return true; }
    }

    private static float getFloatSetting(Context ctx, String key, float def) {
        if (ctx == null) return def;
        try {
            String resolved = resolveKey(key);
            return Settings.Global.getFloat(ctx.getContentResolver(), resolved, def);
        } catch (Exception ignored) { return def; }
    }

    private static int getIntSetting(Context ctx, String key, int def) {
        if (ctx == null) return def;
        try {
            String resolved = resolveKey(key);
            return Settings.Global.getInt(ctx.getContentResolver(), resolved, def);
        } catch (Exception ignored) { return def; }
    }

    private static String getStringSetting(Context ctx, String key) {
        if (ctx == null) return null;
        try {
            String resolved = resolveKey(key);
            return Settings.Global.getString(ctx.getContentResolver(), resolved);
        } catch (Exception ignored) { return null; }
    }

    
    
    

    public static class SpringDynamics {
        private float mStiffness = 450.0f;
        private float mDampingRatio = 0.7f;
        private float mMinVel = 1.0f;
        private float mMinVal = 0.5f;

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
            float dt = deltaTimeNanos / 1_000_000_000.0f;

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
}
