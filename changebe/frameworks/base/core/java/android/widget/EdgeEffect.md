# Манифест интеграции iOS Overscroll в EdgeEffect.java

Этот файл описывает, как модифицировать исходный код `EdgeEffect.java` для внедрения нативной физики пружины.

## ШАГ 0: Добавить имопрты.

```
// ============================================================================================
// [CustomNativeParts] START: IMPORTS
// ============================================================================================
import android.provider.Settings; // [New] Для чтения настроек
import android.text.TextUtils;    // [New] Для разбора конфига пакетов
import android.util.DisplayMetrics; // [New] Для получения размеров экрана
import android.util.Log;            // [New] (Опционально) Для отладки
import android.view.WindowManager;  // [New] Для получения размеров экрана
import java.lang.reflect.Field; // [New] Для рефлексии RenderNode
// ============================================================================================
// [CustomNativeParts] END: IMPORTS
// ============================================================================================

```

## ШАГ 1: Добавление полей и констант

**Куда вставлять:** В начало класса `EdgeEffect`, сразу после стандартных полей (после `mPaint`, `mBounds` и т.д.), но перед конструкторами.

```
    // ============================================================================================
    // [CustomNativeParts] START: FIELDS & CONSTANTS
    // ============================================================================================
    private SpringDynamics mSpring;
    private float mSmoothOffsetY = 0f;
    private float mSmoothScale = 1.0f;
    private float mSmoothZoom = 1.0f;
    private float mSmoothHScale = 1.0f;
    private android.graphics.Matrix mMatrix = new android.graphics.Matrix();
    private float[] mPoints = new float[4];
    private float mLastDelta = 0f;
    private float mTargetFingerX = 0.5f;
    private boolean mFirstTouch = true;
  
    private float mScreenHeight = 2200f;
    private float mScreenWidth = 1080f;
  
    // Config cache
    private float mCfgScale = 1.0f;
    private boolean mCfgFilter = false;
    private boolean mCfgIgnore = false;
    private Context mContext;
  
    // Constants
    private static final String KEY_ENABLED = "overscroll_enabled"; 
    private static final String KEY_PACKAGES_CONFIG = "overscroll_packages_config";
    private static final float FILTER_THRESHOLD = 0.08f;
  
    // Physics Constants
    private static final String KEY_PULL_COEFF = "overscroll_pull";
    private static final String KEY_STIFFNESS = "overscroll_stiffness";
    private static final String KEY_DAMPING = "overscroll_damping";
    private static final String KEY_FLING = "overscroll_fling";
    private static final String KEY_PHYSICS_MIN_VEL = "overscroll_physics_min_vel_v2";
    private static final String KEY_PHYSICS_MIN_VAL = "overscroll_physics_min_val_v2";
  
    // Render Node Reflection Cache
    private static java.lang.reflect.Field sCanvasNodeField;
    // ============================================================================================
    // [CustomNativeParts] END: FIELDS & CONSTANTS
    // ============================================================================================

```

## ШАГ 2: Конструктор

**Куда вставлять:** В **конец** метода `public EdgeEffect(@NonNull Context context, @Nullable AttributeSet attrs)`.

```
        // ============================================================================================
        // [CustomNativeParts] START: INIT
        // ============================================================================================
        mContext = context;
        mSpring = new SpringDynamics();
        initScreenMetrics(context);
        initConfig(context);
        // ============================================================================================
        // [CustomNativeParts] END: INIT
        // ============================================================================================

```

## ШАГ 3: Метод `isFinished()`

**Куда вставлять:** В **самое начало** метода `public boolean isFinished()`.

```
    public boolean isFinished() {
        // ============================================================================================
        // [CustomNativeParts] START: IS_FINISHED HOOK
        // ============================================================================================
        if (isBounceEnabled()) {
            float minVal = getFloatSetting(KEY_PHYSICS_MIN_VAL, 4.0f);
            boolean physicsDone = !mSpring.isRunning() && Math.abs(mSpring.mValue) < minVal;
            boolean visualDone = Math.abs(mSmoothOffsetY) < minVal;
      
            if (physicsDone && visualDone) {
                resetCustomState();
                mState = STATE_IDLE;
                mDistance = 0f;
                return true;
            }
            return false; // Анимация активна, блокируем стандартную проверку
        }
        // ============================================================================================
        // [CustomNativeParts] END: IS_FINISHED HOOK
        // ============================================================================================

        // ... далее идет стандартный код Google ...

```

## ШАГ 4: Метод `finish()`

**Куда вставлять:** В **самое начало** метода `public void finish()`.

```
    public void finish() {
        // ============================================================================================
        // [CustomNativeParts] START: FINISH HOOK
        // ============================================================================================
        if (isBounceEnabled()) {
            mSpring.cancel();
            mSpring.mValue = 0;
            mSpring.mVelocity = 0;
            resetCustomState();
            mState = STATE_IDLE;
            mDistance = 0f;
            return;
        }
        // ============================================================================================
        // [CustomNativeParts] END: FINISH HOOK
        // ============================================================================================

        // ... далее идет стандартный код Google ...

```

## ШАГ 5: Метод `onPull()`

**Куда вставлять:** В **самое начало** метода `public void onPull(float deltaDistance, float displacement)`.

```
    public void onPull(float deltaDistance, float displacement) {
        // ============================================================================================
        // [CustomNativeParts] START: ON_PULL HOOK
        // ============================================================================================
        if (isBounceEnabled()) {
            // Compose Scale Fix
            if (isComposeCaller()) {
                float composeDivisor = getFloatSetting("overscroll_compose_scale", 3.33f);
                if (composeDivisor < 0.01f) composeDivisor = 1.0f;
                deltaDistance /= composeDivisor;
            }

            if (mCfgFilter && Math.abs(deltaDistance) > FILTER_THRESHOLD) return;

            float correctedDelta = (Math.abs(mCfgScale) > 0.001f) ? deltaDistance / mCfgScale : deltaDistance;
            float inputSmoothFactor = getFloatSetting("overscroll_input_smooth", 0.5f);
      
            if (mFirstTouch) {
                mLastDelta = correctedDelta;
                mFirstTouch = false;
            }

            mTargetFingerX = displacement;

            boolean directionChanged = (correctedDelta > 0 && mLastDelta < 0) || (correctedDelta < 0 && mLastDelta > 0);
            float filteredDelta = directionChanged ? correctedDelta : (correctedDelta * (1.0f - inputSmoothFactor) + mLastDelta * inputSmoothFactor);
            mLastDelta = filteredDelta;

            mState = STATE_PULL; // Используем стандартное поле mState
            mSpring.cancel();

            float currentTranslation = mSpring.mValue;
            // Логика Xposed: используем эффективный размер от mHeight/mWidth или screenHeight
            float effectiveSize = Math.max(Math.abs(mHeight), Math.abs(mWidth));
            if (effectiveSize < 1f) effectiveSize = mScreenHeight;

            float rawMove = filteredDelta * effectiveSize;
            float pullCoeff = getFloatSetting(KEY_PULL_COEFF, 0.5f);
            float resExponent = getFloatSetting("overscroll_res_exponent", 4.0f);

            boolean isPullingAway = (currentTranslation > 0 && rawMove > 0) || (currentTranslation < 0 && rawMove < 0);
            float change;

            if (pullCoeff >= 1.0f) {
                change = rawMove * pullCoeff;
            } else {
                if (isPullingAway) {
                    float ratio = Math.min(Math.abs(currentTranslation) / mScreenHeight, 1f);
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
            mDistance = nextTranslation / effectiveSize;
            return;
        }
        // ============================================================================================
        // [CustomNativeParts] END: ON_PULL HOOK
        // ============================================================================================

        // ... далее идет стандартный код Google ...

```

## ШАГ 6: Метод `onRelease()`

**Куда вставлять:** В **самое начало** метода `public void onRelease()`.

```
    public void onRelease() {
        // ============================================================================================
        // [CustomNativeParts] START: ON_RELEASE HOOK
        // ============================================================================================
        if (isBounceEnabled()) {
            if (mSpring.mValue != 0) {
                float stiffness = getFloatSetting(KEY_STIFFNESS, 450f);
                float damping = getFloatSetting(KEY_DAMPING, 0.7f);
                float minVel = getFloatSetting(KEY_PHYSICS_MIN_VEL, 80.0f);
                float minVal = getFloatSetting(KEY_PHYSICS_MIN_VAL, 4.0f);

                mSpring.setParams(stiffness, damping, minVel, minVal);
                mSpring.setTargetValue(0);
                mSpring.setVelocity(0);
                mSpring.start();
          
                mState = STATE_RECEDE;
            } else {
                mState = STATE_IDLE;
                mDistance = 0f;
            }

            mTargetFingerX = 0.5f;
            mLastDelta = 0f;
            mFirstTouch = true;
            return;
        }
        // ============================================================================================
        // [CustomNativeParts] END: ON_RELEASE HOOK
        // ============================================================================================

        // ... далее идет стандартный код Google ...

```

## ШАГ 7: Метод `onAbsorb()`

**Куда вставлять:** В **самое начало** метода `public void onAbsorb(int velocity)`.

```
    public void onAbsorb(int velocity) {
        // ============================================================================================
        // [CustomNativeParts] START: ON_ABSORB HOOK
        // ============================================================================================
        if (isBounceEnabled()) {
            // КРИТИЧНО: Принудительно ставим STATE_RECEDE для совместимости с Compose
            mState = STATE_RECEDE; 
            mSpring.cancel();

            float flingMult = getFloatSetting(KEY_FLING, 0.6f);
            float stiffness = getFloatSetting(KEY_STIFFNESS, 450f);
            float damping = getFloatSetting(KEY_DAMPING, 0.7f);
            float minVel = getFloatSetting(KEY_PHYSICS_MIN_VEL, 80.0f);
            float minVal = getFloatSetting(KEY_PHYSICS_MIN_VAL, 4.0f);

            float velocityPx = velocity * flingMult;
            if (flingMult > 1.0f) stiffness /= flingMult;

            // Логика Xposed: maxVel строго зависит от высоты экрана, игнорируя размер View
            float maxVel = mScreenHeight * 10f; 
      
            if (Math.abs(velocityPx) > maxVel) velocityPx = Math.signum(velocityPx) * maxVel;

            mSpring.setParams(stiffness, damping, minVel, minVal);
            mSpring.setTargetValue(0);
            mSpring.setVelocity(velocityPx);
            mSpring.start();

            mTargetFingerX = 0.5f;
            mLastDelta = 0f;
            mFirstTouch = true;
            return;
        }
        // ============================================================================================
        // [CustomNativeParts] END: ON_ABSORB HOOK
        // ============================================================================================

        // ... далее идет стандартный код Google ...

```

## ШАГ 8: Метод `draw()`

**Куда вставлять:** В **самое начало** метода `public boolean draw(Canvas canvas)`.

```
    public boolean draw(Canvas canvas) {
        // ============================================================================================
        // [CustomNativeParts] START: DRAW HOOK
        // ============================================================================================
        if (isBounceEnabled()) {
            if (!canvas.isHardwareAccelerated()) return false;

            if (mSpring.isRunning()) {
                mSpring.doFrame(System.nanoTime());
            }

            android.graphics.RenderNode renderNode = getCanvasRenderNode(canvas);
            if (renderNode == null) return false;

            // Привязка к векторам (Vector Snapping)
            mPoints[0] = 0; mPoints[1] = 0;
            mPoints[2] = 0; mPoints[3] = 1;
      
            try {
                canvas.getMatrix(mMatrix);
                mMatrix.mapPoints(mPoints);
            } catch (Exception ignored) {}

            float vx = mPoints[2] - mPoints[0];
            float vy = mPoints[3] - mPoints[1];
      
            if (Math.abs(vx) > Math.abs(vy)) {
                vx = Math.signum(vx); vy = 0f;
            } else {
                vy = Math.signum(vy); vx = 0f;
            }

            boolean isVertical = (vy != 0);

            float lerpMainIdle = getFloatSetting("overscroll_lerp_main_idle", 0.4f);
            float lerpMainRun = getFloatSetting("overscroll_lerp_main_run", 0.7f);
            float lerpFactorMain = mSpring.isRunning() ? lerpMainRun : lerpMainIdle;

            float targetOffset = mSpring.mValue;
            float currentOffset = mSmoothOffsetY;
            float newOffset = currentOffset + (targetOffset - currentOffset) * lerpFactorMain;
      
            if (Math.abs(targetOffset - newOffset) < 0.5f) newOffset = targetOffset;
            mSmoothOffsetY = newOffset;

            float maxDistance = isVertical ? mScreenHeight : mScreenWidth;
            float ratio = (maxDistance > 0) ? Math.min(Math.abs(newOffset) / maxDistance, 1.0f) : 0f;
            boolean isActive = Math.abs(newOffset) > 1.0f;

            // SCALING
            float targetScaleV = 1f, targetScaleZ = 1f, targetScaleH = 1f;
      
            if (isActive) {
                String keyScaleInt = isVertical ? "overscroll_scale_intensity" : "overscroll_scale_intensity_horiz";
                String keyZoomInt = isVertical ? "overscroll_zoom_intensity" : "overscroll_zoom_intensity_horiz";
                String keyHScaleInt = isVertical ? "overscroll_h_scale_intensity" : "overscroll_h_scale_intensity_horiz";

                targetScaleV = calcScale("overscroll_scale_mode", keyScaleInt, "overscroll_scale_limit_min", ratio);
                targetScaleZ = calcScale("overscroll_zoom_mode", keyZoomInt, "overscroll_zoom_limit_min", ratio);
                targetScaleH = calcScale("overscroll_h_scale_mode", keyHScaleInt, "overscroll_h_scale_limit_min", ratio);
            }

            mSmoothScale = lerp(mSmoothScale, targetScaleV, lerpFactorMain);
            mSmoothZoom = lerp(mSmoothZoom, targetScaleZ, lerpFactorMain);
            mSmoothHScale = lerp(mSmoothHScale, targetScaleH, lerpFactorMain);

            boolean isResting = Math.abs(newOffset) < 0.1f 
                    && Math.abs(mSmoothScale - 1f) < 0.001f 
                    && Math.abs(mSmoothZoom - 1f) < 0.001f
                    && Math.abs(mSmoothHScale - 1f) < 0.001f;

            if (isResting && !mSpring.isRunning()) {
                safeResetRenderNode(renderNode);
                return false;
            }
      
            float effectiveSize = Math.max(Math.abs(mHeight), Math.abs(mWidth));
            if (effectiveSize < 1f) effectiveSize = mScreenHeight;
            mDistance = newOffset / effectiveSize;

            try {
                renderNode.setTranslationX(newOffset * vx);
                renderNode.setTranslationY(newOffset * vy);

                float axisMainScale = mSmoothScale * mSmoothZoom;
                float axisCrossScale = mSmoothHScale * mSmoothZoom;
          
                float finalScaleX, finalScaleY;
                if (isVertical) {
                    finalScaleX = axisCrossScale; 
                    finalScaleY = axisMainScale;  
                } else {
                    finalScaleX = axisMainScale;  
                    finalScaleY = axisCrossScale; 
                }

                // ANCHOR LOGIC
                float ax = 0.5f, ay = 0.5f;
                boolean zoomActive = getIntSetting("overscroll_zoom_mode", 0) != 0;
                boolean scaleActive = getIntSetting("overscroll_scale_mode", 0) != 0;
                boolean hScaleActive = getIntSetting("overscroll_h_scale_mode", 0) != 0;

                if (isVertical) {
                    if (zoomActive) {
                        ax = getFloatSetting("overscroll_zoom_anchor_x", 0.5f);
                        ay = getFloatSetting("overscroll_zoom_anchor_y", 0.5f);
                    } else if (scaleActive) {
                        ax = 0.5f;
                        ay = getFloatSetting("overscroll_scale_anchor_y", 0.5f);
                    } else if (hScaleActive) {
                        ax = getFloatSetting("overscroll_h_scale_anchor_x", 0.5f);
                        ay = 0.5f;
                    }
                } else {
                    if (zoomActive) {
                        ax = getFloatSetting("overscroll_zoom_anchor_x_horiz", 0.5f);
                        ay = getFloatSetting("overscroll_zoom_anchor_y_horiz", 0.5f);
                    } else if (scaleActive) {
                        ax = getFloatSetting("overscroll_scale_anchor_x_horiz", 0.5f);
                        ay = 0.5f;
                    } else if (hScaleActive) {
                        ax = 0.5f;
                        ay = getFloatSetting("overscroll_h_scale_anchor_y_horiz", 0.5f);
                    }
                }

                boolean invertAnchor = getIntSetting("overscroll_invert_anchor", 1) == 1;
          
                float canvasW = (float) canvas.getWidth();
                float canvasH = (float) canvas.getHeight();
                float pX, pY;

                if (isVertical) {
                    pX = canvasW * ax;
                    if (vy > 0) { pY = canvasH * ay; } 
                    else { pY = canvasH * (invertAnchor ? (1.0f - ay) : ay); }
                } else {
                    pY = canvasH * ay;
                    if (vx > 0) { pX = canvasW * ax; } 
                    else { pX = canvasW * (invertAnchor ? (1.0f - ax) : ax); }
                }

                renderNode.setPivotX(pX);
                renderNode.setPivotY(pY);
                renderNode.setScaleX(finalScaleX);
                renderNode.setScaleY(finalScaleY);
          
                renderNode.setRotationX(0f);
                renderNode.setRotationY(0f);
                renderNode.setRotationZ(0f);
          
                try {
                   renderNode.getClass().getMethod("stretch", float.class, float.class, float.class, float.class)
                       .invoke(renderNode, 0f, 0f, mWidth, mHeight);
                } catch (Exception ignored) {}

            } catch (Throwable t) {}

            return true;
        }
        // ============================================================================================
        // [CustomNativeParts] END: DRAW HOOK
        // ============================================================================================

        // ... далее идет стандартный код Google ...

```

## ШАГ 9: Вспомогательные методы и внутренний класс

**Куда вставлять:** В самый  **конец файла** , перед закрывающей скобкой класса `EdgeEffect`.

```
    // ============================================================================================
    // [CustomNativeParts] START: UTILS, HELPERS & SPRING CLASS
    // ============================================================================================

    private void resetCustomState() {
        mSmoothOffsetY = 0f;
        mSmoothScale = 1.0f;
        mSmoothZoom = 1.0f;
        mSmoothHScale = 1.0f;
        mTargetFingerX = 0.5f;
        mFirstTouch = true;
    }

    private boolean isBounceEnabled() {
        if (mContext == null) return true;
        try {
            if (mCfgIgnore) return false;
            return Settings.Secure.getInt(mContext.getContentResolver(), KEY_ENABLED, 1) == 1;
        } catch (Exception ignored) { return true; }
    }

    private float getFloatSetting(String key, float def) {
        if (mContext == null) return def;
        try { return Settings.Secure.getFloat(mContext.getContentResolver(), key, def); } 
        catch (Exception e) { return def; }
    }
  
    private int getIntSetting(String key, int def) {
        if (mContext == null) return def;
        try { return Settings.Secure.getInt(mContext.getContentResolver(), key, def); } 
        catch (Exception e) { return def; }
    }
  
    private float calcScale(String modeKey, String intKey, String limKey, float ratio) {
        int mode = getIntSetting(modeKey, 0);
        float intensity = getFloatSetting(intKey, 0.0f);
        float limit = getFloatSetting(limKey, 0.3f);
        if (mode == 0 || intensity <= 0) return 1.0f;
        if (mode == 1) return Math.max(1.0f - (ratio * intensity), limit);
        if (mode == 2) return 1.0f + (ratio * intensity);
        return 1.0f;
    }

    private float lerp(float start, float end, float factor) {
        return start + (end - start) * factor;
    }

    private void initScreenMetrics(Context context) {
        try {
            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (wm != null) {
                DisplayMetrics dm = new DisplayMetrics();
                wm.getDefaultDisplay().getMetrics(dm);
                mScreenHeight = dm.heightPixels;
                mScreenWidth = dm.widthPixels;
            }
        } catch (Exception ignored) {}
    }

    private void initConfig(Context context) {
        try {
            String pkgName = context.getPackageName();
            String configString = Settings.Secure.getString(context.getContentResolver(), KEY_PACKAGES_CONFIG);
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

    private boolean isComposeCaller() {
        try {
            StackTraceElement[] stack = Thread.currentThread().getStackTrace();
            for (StackTraceElement element : stack) {
                if (element.getClassName().startsWith("androidx.compose")) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private void safeResetRenderNode(android.graphics.RenderNode node) {
        if (node == null) return;
        try {
            node.setTranslationX(0f); node.setTranslationY(0f);
            node.setScaleX(1f); node.setScaleY(1f);
            node.setPivotX(0f); node.setPivotY(0f);
            node.setRotationX(0f); node.setRotationY(0f); node.setRotationZ(0f);
            try {
                node.getClass().getMethod("stretch", float.class, float.class, float.class, float.class)
                    .invoke(node, 0f, 0f, 0f, 0f);
            } catch (Exception ignored) {}
        } catch (Throwable ignored) {}
    }

    private android.graphics.RenderNode getCanvasRenderNode(Canvas canvas) {
        if (sCanvasNodeField != null) {
            try { return (android.graphics.RenderNode) sCanvasNodeField.get(canvas); } catch (Exception ignored) {}
        }
        Class<?> clazz = canvas.getClass();
        while (clazz != null && clazz != Object.class) {
            try {
                java.lang.reflect.Field f = clazz.getDeclaredField("mNode");
                f.setAccessible(true);
                sCanvasNodeField = f;
                return (android.graphics.RenderNode) f.get(canvas);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    // --- INTERNAL SPRING CLASS ---
    private static class SpringDynamics {
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

        public void start() {
            if (mIsRunning) return;
            mIsRunning = true;
            mLastFrameTimeNanos = System.nanoTime();
        }

        public void cancel() { mIsRunning = false; }

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
    // ============================================================================================
    // [CustomNativeParts] END: UTILS, HELPERS & SPRING CLASS
    // ============================================================================================


```
