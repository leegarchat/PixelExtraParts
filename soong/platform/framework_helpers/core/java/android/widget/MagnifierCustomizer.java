package android.widget;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;

/**
 * Magnifier customization: zoom/size/shape/vertical-offset overrides driven by
 * Settings.Global, with runtime re-read support.
 *
 * <p>Works on the "helper computes, {@link Magnifier} applies" pattern: this class
 * never touches {@code Magnifier} or {@code Builder} internals (their fields are
 * private), it only maps current values plus Settings into plain result structs.
 * The patched {@code Magnifier} keeps the field declarations, the constructor /
 * show / setZoom injection points and the {@code dismiss()} side effect.
 *
 * @hide
 */
public final class MagnifierCustomizer {
    private MagnifierCustomizer() {
    }

    private static final String TAG = "Magnifier";

    // Settings keys shared verbatim with the app.
    private static final String KEY_ENABLED = "magnifier_custom_enabled";
    private static final String KEY_ZOOM = "magnifier_custom_zoom";
    private static final String KEY_SIZE = "magnifier_custom_size";
    private static final String KEY_SHAPE = "magnifier_custom_shape";
    private static final String KEY_OFFSET_Y = "magnifier_custom_offset_y";

    // Shape constants.
    private static final int SHAPE_DEFAULT = 0;
    private static final int SHAPE_SQUARE = 1;
    private static final int SHAPE_CIRCLE = 2;

    // Default values.
    private static final float DEFAULT_ZOOM = 1.25f;
    private static final float DEFAULT_SIZE = 1.0f;

    /** Throttle for re-reading Settings on every show(). @hide */
    public static final long SETTINGS_READ_INTERVAL_MS = 250;

    /** Result of {@link #computeInitial}. @hide */
    public static final class InitialSpec {
        /** @hide */ public int width;
        /** @hide */ public int height;
        /** @hide */ public float cornerRadius;
        /** @hide */ public float zoom;
        /** @hide */ public int sourceWidth;
        /** @hide */ public int sourceHeight;
        /** @hide */ public int offsetDeltaPx;
    }

    /** Result of {@link #computeRuntime}. @hide */
    public static final class RuntimeDelta {
        /** @hide */ public int width;
        /** @hide */ public int height;
        /** @hide */ public float cornerRadius;
        /** @hide */ public float zoom;
        /** @hide */ public int offsetDeltaPx;
        /** @hide */ public boolean dismiss;
    }

    /**
     * Computes zoom/size/shape/offset overrides for {@code Magnifier(Builder)}
     * construction. Never throws: on any failure returns the best-effort spec
     * (partially applied, mirroring the order of the original inline code) and
     * returns {@code null} only when {@code context} is {@code null}.
     *
     * @hide
     */
    public static InitialSpec computeInitial(Context context, int width, int height,
            float cornerRadius, float zoom, boolean fishEye, int sourceWidth, int sourceHeight) {
        if (context == null) {
            return null;
        }
        final InitialSpec spec = new InitialSpec();
        spec.width = width;
        spec.height = height;
        spec.cornerRadius = cornerRadius;
        spec.zoom = zoom;
        spec.sourceWidth = sourceWidth;
        spec.sourceHeight = sourceHeight;
        spec.offsetDeltaPx = 0;
        try {
            int enabled = Settings.Global.getInt(
                    context.getContentResolver(), KEY_ENABLED, 0);
            if (enabled != 1) {
                return spec;
            }

            float customZoom = Settings.Global.getFloat(
                    context.getContentResolver(), KEY_ZOOM, DEFAULT_ZOOM);
            float sizeScale = Settings.Global.getFloat(
                    context.getContentResolver(), KEY_SIZE, DEFAULT_SIZE);
            int shape = Settings.Global.getInt(
                    context.getContentResolver(), KEY_SHAPE, SHAPE_DEFAULT);
            int offsetYDp = Settings.Global.getInt(
                    context.getContentResolver(), KEY_OFFSET_Y, 0);

            // --- Zoom override ---
            if (customZoom > 0f) {
                spec.zoom = customZoom;
            }

            // --- Size scale: enlarge window dimensions ---
            if (Math.abs(sizeScale - 1.0f) > 0.01f && sizeScale > 0f) {
                spec.width = Math.max(1, Math.round(spec.width * sizeScale));
                spec.height = Math.max(1, Math.round(spec.height * sizeScale));
            }

            // --- Shape: square or circle ---
            if (shape == SHAPE_SQUARE || shape == SHAPE_CIRCLE) {
                int side = Math.max(spec.width, spec.height);
                spec.width = side;
                spec.height = side;

                if (shape == SHAPE_CIRCLE) {
                    spec.cornerRadius = side / 2.0f;
                }
            }

            // --- Vertical offset (dp → px) ---
            if (offsetYDp != 0) {
                float density = context.getResources()
                        .getDisplayMetrics().density;
                spec.offsetDeltaPx = Math.round(offsetYDp * density);
            }

            // --- Recompute source dimensions for consistency ---
            if (spec.width > 0 && spec.height > 0 && spec.zoom > 0f) {
                spec.sourceWidth = fishEye
                        ? spec.width : Math.round(spec.width / spec.zoom);
                spec.sourceHeight = Math.round(spec.height / spec.zoom);
            }
        } catch (Exception e) {
            Log.e(TAG, "[PixelParts] Magnifier applyCustomParams failed", e);
        }
        return spec;
    }

    /**
     * Re-reads Settings for real-time zoom/size/shape/offset updates.
     * Returns {@code null} when nothing may be applied (null context or an
     * internal failure); the caller keeps the throttle timestamp update and
     * owns the {@code dismiss()} side effect. Never throws.
     *
     * @hide
     */
    public static RuntimeDelta computeRuntime(Context context, int origWidth, int origHeight,
            float origCornerRadius, float origZoom, int currentWidth, int currentHeight,
            float currentCornerRadius, float currentZoom, boolean fishEye,
            int customOffsetYApplied) {
        if (context == null) {
            return null;
        }
        try {
            android.content.ContentResolver cr = context.getContentResolver();

            int enabled = Settings.Global.getInt(cr, KEY_ENABLED, 0);
            if (enabled != 1) {
                // Feature disabled: restore original dimensions/zoom.
                final RuntimeDelta delta = new RuntimeDelta();
                delta.offsetDeltaPx = -customOffsetYApplied;
                delta.zoom = currentZoom;
                delta.width = currentWidth;
                delta.height = currentHeight;
                delta.cornerRadius = currentCornerRadius;
                delta.dismiss = false;
                if (Math.abs(currentZoom - origZoom) > 0.01f) {
                    delta.zoom = origZoom;
                    delta.dismiss = true;
                }
                if (currentWidth != origWidth
                        || currentHeight != origHeight
                        || Math.abs(currentCornerRadius - origCornerRadius) > 0.01f) {
                    delta.width = origWidth;
                    delta.height = origHeight;
                    delta.cornerRadius = origCornerRadius;
                    delta.dismiss = true;
                }
                return delta;
            }

            final RuntimeDelta delta = new RuntimeDelta();
            delta.dismiss = false;

            // --- Zoom ---
            float newZoom = Settings.Global.getFloat(cr, KEY_ZOOM, DEFAULT_ZOOM);
            delta.zoom = currentZoom;
            if (newZoom > 0f && Math.abs(newZoom - currentZoom) > 0.01f) {
                delta.zoom = newZoom;
                delta.dismiss = true;
            }

            // --- Size & shape ---
            float newSizeScale = Settings.Global.getFloat(cr, KEY_SIZE, DEFAULT_SIZE);
            int newShape = Settings.Global.getInt(cr, KEY_SHAPE, SHAPE_DEFAULT);
            int newW = origWidth;
            int newH = origHeight;
            float newCR = origCornerRadius;

            if (Math.abs(newSizeScale - 1.0f) > 0.01f && newSizeScale > 0f) {
                newW = Math.max(1, Math.round(newW * newSizeScale));
                newH = Math.max(1, Math.round(newH * newSizeScale));
            }

            if (newShape == SHAPE_SQUARE || newShape == SHAPE_CIRCLE) {
                int side = Math.max(newW, newH);
                newW = side;
                newH = side;
                if (newShape == SHAPE_CIRCLE) {
                    newCR = side / 2.0f;
                }
            }

            delta.width = currentWidth;
            delta.height = currentHeight;
            delta.cornerRadius = currentCornerRadius;
            if (newW != currentWidth || newH != currentHeight
                    || Math.abs(newCR - currentCornerRadius) > 0.01f) {
                delta.width = newW;
                delta.height = newH;
                delta.cornerRadius = newCR;
                delta.dismiss = true;
            }

            // --- Vertical offset delta ---
            int newOffsetYDp = Settings.Global.getInt(cr, KEY_OFFSET_Y, 0);
            float density = context.getResources().getDisplayMetrics().density;
            int newOffsetYPx = Math.round(newOffsetYDp * density);
            delta.offsetDeltaPx = newOffsetYPx - customOffsetYApplied;

            return delta;
        } catch (Exception e) {
            Log.e(TAG, "[PixelParts] Magnifier applyCustomParamsRuntime failed", e);
            return null;
        }
    }

    /**
     * Overrides zoom in setZoom() if the custom magnifier is enabled.
     * Returns the (possibly overridden) zoom value. Never throws.
     *
     * @hide
     */
    public static float overrideZoom(Context context, float requestedZoom) {
        try {
            if (context == null) {
                return requestedZoom;
            }
            int enabled = Settings.Global.getInt(
                    context.getContentResolver(), KEY_ENABLED, 0);
            if (enabled != 1) {
                return requestedZoom;
            }
            float customZoom = Settings.Global.getFloat(
                    context.getContentResolver(), KEY_ZOOM, DEFAULT_ZOOM);
            if (customZoom > 0f && Math.abs(customZoom - requestedZoom) > 0.01f) {
                return customZoom;
            }
        } catch (Exception e) {
            Log.e(TAG, "[PixelParts] Magnifier customOverrideZoom failed", e);
        }
        return requestedZoom;
    }
}
