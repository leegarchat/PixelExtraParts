package android.content.res;

import android.util.DisplayMetrics;

/**
 * Module Resources implementation (classic Xposed API surface
 * {@code android.content.res.XModuleResources}).
 *
 * <p>YukiHookAPI's {@code YukiResources.createModuleResources(path)}
 * instantiates this directly from the module APK path
 * ({@code new AssetManager()} + {@code addAssetPath}), no package
 * visibility needed — installed APKs are world-readable. Missing this
 * class made Yuki throw {@code IllegalStateException: Current Hook
 * Framework not support moduleAppResources} and skip all hooks.
 */
public class XModuleResources extends Resources {
    private XModuleResources(AssetManager assets, DisplayMetrics metrics, Configuration config) {
        super(assets, metrics, config);
    }

    /**
     * Creates module Resources from an APK file path.
     * Mirrors the classic API ({@code origRes} may be null).
     */
    public static XModuleResources createInstance(String path, XResources origRes) {
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        try {
            AssetManager assets = AssetManager.class.getDeclaredConstructor().newInstance();
            java.lang.reflect.Method addAssetPath = AssetManager.class
                    .getDeclaredMethod("addAssetPath", String.class);
            addAssetPath.setAccessible(true);
            int cookie = (Integer) addAssetPath.invoke(assets, path);
            if (cookie == 0) {
                throw new RuntimeException("addAssetPath failed for " + path);
            }
            // Never hand null metrics/config to Resources: Yuki reads
            // getDisplayMetrics()/getConfiguration() straight away.
            DisplayMetrics metrics = origRes != null ? origRes.getDisplayMetrics()
                    : new DisplayMetrics();
            Configuration config = origRes != null ? origRes.getConfiguration()
                    : new Configuration();
            return new XModuleResources(assets, metrics, config);
        } catch (RuntimeException e) {
            android.util.Log.e("XModuleResources", "createInstance failed for " + path, e);
            throw e;
        } catch (Throwable t) {
            android.util.Log.e("XModuleResources", "createInstance failed for " + path, t);
            throw new RuntimeException(t);
        }
    }

    public XResForwarder fwd(int id) {
        return new XResForwarder(this, id);
    }
}
