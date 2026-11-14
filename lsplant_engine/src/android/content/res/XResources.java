package android.content.res;

import android.util.DisplayMetrics;

/**
 * Xposed-compatible Resources subclass (classic
 * {@code de.robv.android.xposed} API surface).
 *
 * <p>Kept minimal: our engine never replaces app Resources, it only
 * needs the type to exist so modules (e.g. YukiHookAPI's
 * {@code YukiResources}) can link against it.
 */
public class XResources extends Resources {
    public XResources(AssetManager assets, DisplayMetrics metrics, Configuration config) {
        super(assets, metrics, config);
    }

    /**
     * Creates a new {@link XResources} object wrapping the given asset
     * manager. Mirrors the classic API.
     */
    public static XResources createInstance(String path, Resources origRes) {
        try {
            AssetManager assets = AssetManager.class.getDeclaredConstructor().newInstance();
            java.lang.reflect.Method addAssetPath = AssetManager.class
                    .getDeclaredMethod("addAssetPath", String.class);
            addAssetPath.setAccessible(true);
            int cookie = (Integer) addAssetPath.invoke(assets, path);
            if (cookie == 0) {
                throw new RuntimeException("addAssetPath failed for " + path);
            }
            DisplayMetrics metrics = origRes != null ? origRes.getDisplayMetrics()
                    : new DisplayMetrics();
            Configuration config = origRes != null ? origRes.getConfiguration()
                    : new Configuration();
            return new XResources(assets, metrics, config);
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
}
