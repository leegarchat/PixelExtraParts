package android.app;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageItemInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.StrictMode;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.LruCache;
import android.util.Slog;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import org.json.JSONException;
import org.json.JSONObject;

public final class PixelPartsIconHelper {

    private static final String TAG = "PixelPartsIcon";

    private static final String PIXEL_PARTS_ICON_RELOAD_ACTION =
            "com.pixelparts.intent.action.RELOAD_ICONS";
    private static final String PIXEL_PARTS_ICON_ENABLED_SETTING =
            "pixelparts_app_icons_enabled";
    private static final File PIXEL_PARTS_ICON_ROOT =
            new File("/data/pixelparts/IconsManager");
    private static final File PIXEL_PARTS_ICON_MAP =
            new File(PIXEL_PARTS_ICON_ROOT, "icon_map.json");
    private static final String[] PIXEL_PARTS_ICON_DENSITY_FOLDERS = {
            "mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"
    };
    private static final ConcurrentHashMap<String, String> sPixelPartsIconMap =
            new ConcurrentHashMap<>();
    private static final LruCache<String, Drawable.ConstantState> sPixelPartsIconCache =
            new LruCache<>(160);
    private static final Object sPixelPartsIconLock = new Object();
    private static boolean sPixelPartsIconMapLoaded;
    private static boolean sPixelPartsIconReceiverRegistered;

    static {
        synchronized (sPixelPartsIconLock) {
            loadPixelPartsIconMapLocked();
        }
    }


    /** @hide */
    public static Drawable loadPixelPartsIconOverride(@NonNull Context context,
            @NonNull PackageItemInfo itemInfo, @Nullable ApplicationInfo appInfo) {
        final String packageName = resolvePixelPartsIconPackageName(itemInfo, appInfo);
        if (TextUtils.isEmpty(packageName)) {
            return null;
        }

        return loadPixelPartsIconOverride(
                context,
                packageName,
                context.getResources().getDisplayMetrics().densityDpi);
    }

    /** @hide */
    public static Drawable loadPixelPartsIconOverride(@NonNull Context context,
            @NonNull String packageName, int densityDpi) {
        if (TextUtils.isEmpty(packageName)) {
            return null;
        }

        if (!arePixelPartsIconsEnabled(context)) {
            return null;
        }

        maybeRegisterPixelPartsIconReloadReceiver(context);

        final String iconFileName = getPixelPartsIconFileName(packageName);
        if (TextUtils.isEmpty(iconFileName)) {
            return null;
        }

        final File iconFile = findPixelPartsIconFile(iconFileName, densityDpi);
        if (iconFile == null) {
            return null;
        }

        final String cacheKey = packageName + "|" + densityDpi + "|"
                + iconFile.getAbsolutePath() + "|"
                + iconFile.lastModified();
        Drawable.ConstantState cachedState;
        synchronized (sPixelPartsIconCache) {
            cachedState = sPixelPartsIconCache.get(cacheKey);
        }
        if (cachedState != null) {
            return cachedState.newDrawable(context.getResources());
        }

        final StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
        try (InputStream input = new FileInputStream(iconFile)) {
            final Bitmap bitmap = BitmapFactory.decodeStream(input);
            if (bitmap == null) {
                return null;
            }

            final BitmapDrawable drawable = new BitmapDrawable(context.getResources(), bitmap);
            drawable.setTargetDensity(context.getResources().getDisplayMetrics());
            final Drawable.ConstantState state = drawable.getConstantState();
            if (state == null) {
                return drawable;
            }
            synchronized (sPixelPartsIconCache) {
                sPixelPartsIconCache.put(cacheKey, state);
            }
            return state.newDrawable(context.getResources());
        } catch (IOException e) {
            Slog.w(TAG, "Unable to load PixelParts icon override for " + packageName, e);
            return null;
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
        }
    }

    /** @hide */
    public static boolean hasPixelPartsIconOverride(@NonNull Context context,
            @NonNull String packageName, int densityDpi) {
        if (TextUtils.isEmpty(packageName) || !arePixelPartsIconsEnabled(context)) {
            return false;
        }

        maybeRegisterPixelPartsIconReloadReceiver(context);

        final String iconFileName = getPixelPartsIconFileName(packageName);
        return !TextUtils.isEmpty(iconFileName)
                && findPixelPartsIconFile(iconFileName, densityDpi) != null;
    }

    private static String resolvePixelPartsIconPackageName(@NonNull PackageItemInfo itemInfo,
            @Nullable ApplicationInfo appInfo) {
        if (!TextUtils.isEmpty(itemInfo.packageName)) {
            return itemInfo.packageName;
        }
        return appInfo != null ? appInfo.packageName : null;
    }

    private static String getPixelPartsIconFileName(String packageName) {
        synchronized (sPixelPartsIconLock) {
            if (!sPixelPartsIconMapLoaded) {
                loadPixelPartsIconMapLocked();
            }
            return sPixelPartsIconMap.get(packageName);
        }
    }

    private static boolean arePixelPartsIconsEnabled(@NonNull Context context) {
        return Settings.Global.getInt(
                context.getContentResolver(), PIXEL_PARTS_ICON_ENABLED_SETTING, 1) != 0;
    }

    private static void loadPixelPartsIconMapLocked() {
        sPixelPartsIconMapLoaded = true;
        sPixelPartsIconMap.clear();

        if (!PIXEL_PARTS_ICON_MAP.isFile()) {
            return;
        }

        final StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
        try (InputStream input = new FileInputStream(PIXEL_PARTS_ICON_MAP)) {
            final JSONObject root = new JSONObject(
                    new String(input.readAllBytes(), StandardCharsets.UTF_8));
            final JSONObject icons = root.optJSONObject("icons") != null
                    ? root.optJSONObject("icons") : root;
            final Iterator<String> packages = icons.keys();
            while (packages.hasNext()) {
                final String packageName = packages.next();
                final Object value = icons.opt(packageName);
                if (!(value instanceof String)) {
                    continue;
                }

                final String fileName = normalizePixelPartsIconFileName((String) value);
                if (!TextUtils.isEmpty(packageName) && fileName != null) {
                    sPixelPartsIconMap.put(packageName, fileName);
                }
            }
        } catch (IOException | JSONException e) {
            Slog.w(TAG, "Unable to read PixelParts icon map", e);
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
        }
    }

    private static String normalizePixelPartsIconFileName(String value) {
        if (value == null) {
            return null;
        }

        String fileName = value.trim();
        if (TextUtils.isEmpty(fileName) || fileName.contains("/") || fileName.contains("\\")
                || fileName.contains("..")) {
            return null;
        }

        if (!fileName.endsWith(".png")) {
            fileName += ".png";
        }
        return fileName.length() > ".png".length() ? fileName : null;
    }

    private static File findPixelPartsIconFile(String fileName, int densityDpi) {
        final String densityFolder = pixelPartsIconDensityFolder(densityDpi);
        final File currentDensityFile = new File(new File(PIXEL_PARTS_ICON_ROOT, densityFolder),
                fileName);
        if (currentDensityFile.isFile()) {
            return currentDensityFile;
        }

        for (String fallbackDensity : PIXEL_PARTS_ICON_DENSITY_FOLDERS) {
            if (fallbackDensity.equals(densityFolder)) {
                continue;
            }
            final File fallbackFile = new File(new File(PIXEL_PARTS_ICON_ROOT, fallbackDensity),
                    fileName);
            if (fallbackFile.isFile()) {
                return fallbackFile;
            }
        }

        return null;
    }

    private static String pixelPartsIconDensityFolder(int densityDpi) {
        if (densityDpi <= DisplayMetrics.DENSITY_MEDIUM) {
            return "mdpi";
        } else if (densityDpi <= DisplayMetrics.DENSITY_HIGH) {
            return "hdpi";
        } else if (densityDpi <= DisplayMetrics.DENSITY_XHIGH) {
            return "xhdpi";
        } else if (densityDpi <= DisplayMetrics.DENSITY_XXHIGH) {
            return "xxhdpi";
        }
        return "xxxhdpi";
    }

    private static void maybeRegisterPixelPartsIconReloadReceiver(@NonNull Context context) {
        synchronized (sPixelPartsIconLock) {
            if (sPixelPartsIconReceiverRegistered) {
                return;
            }
            sPixelPartsIconReceiverRegistered = true;
        }

        final Context receiverContext = context.getApplicationContext() != null
                ? context.getApplicationContext() : context;
        try {
            receiverContext.registerReceiver(
                    new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            if (intent != null
                                    && PIXEL_PARTS_ICON_RELOAD_ACTION.equals(intent.getAction())) {
                                reloadPixelPartsIcons();
                            }
                        }
                    },
                    new IntentFilter(PIXEL_PARTS_ICON_RELOAD_ACTION),
                    Context.RECEIVER_EXPORTED);
        } catch (Throwable t) {
            synchronized (sPixelPartsIconLock) {
                sPixelPartsIconReceiverRegistered = false;
            }
            Slog.w(TAG, "Unable to register PixelParts icon reload receiver", t);
        }
    }

    private static void reloadPixelPartsIcons() {
        synchronized (sPixelPartsIconLock) {
            sPixelPartsIconMapLoaded = false;
            loadPixelPartsIconMapLocked();
        }
        synchronized (sPixelPartsIconCache) {
            sPixelPartsIconCache.evictAll();
        }
    }
}
