package de.robv.android.xposed;

import android.content.SharedPreferences;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.pixel.customparts.manager.lsplant.RemotePrefs;

/**
 * Read-only CharSequence-free SharedPreferences implementation for modules.
 *
 * <p>Mirrors the classic Xposed API surface
 * ({@code XSharedPreferences(String)}, {@code (String, String)},
 * {@code hasFileChanged()}, {@code reload()},
 * {@code makeWorldReadable()}). There is no world-readable app sandbox
 * on modern Android, so this is backed by {@link RemotePrefs} shared
 * files ({@code /data/pixelparts/rprefs/<pkg>.xml}) instead of
 * {@code /data/data/<pkg>/shared_prefs}: the module UI process and
 * every injected target process see the same values.
 *
 * <p>The manager app (system_app) pre-creates the file at module-enable
 * and boot; untrusted processes may not create files outside their
 * sandbox (core neverallow), so a missing file reads defaults and
 * {@code makeWorldReadable()} reports false.
 */
public class XSharedPreferences implements SharedPreferences {
    private static final String DIR = "/data/pixelparts/rprefs";

    private final String packageName;
    private final String prefFileName;
    private volatile SharedPreferences base;

    public XSharedPreferences(String packageName) {
        this(packageName, packageName + "_preferences");
    }

    public XSharedPreferences(String packageName, String prefFileName) {
        this.packageName = packageName;
        this.prefFileName = prefFileName != null ? prefFileName : "default";
        this.base = RemotePrefs.forModule(packageName, this.prefFileName);
    }

    /** Absolute backing file (for diagnostics). */
    public File getFile() {
        return new File(DIR, packageName + ".xml");
    }

    /**
     * Our backing files are created world-readable/writable by the
     * manager; reports whether the file exists.
     */
    public boolean makeWorldReadable() {
        try {
            return getFile().exists();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public boolean hasFileChanged() {
        try {
            SharedPreferences b = base;
            if (b instanceof RemotePrefs) {
                return ((RemotePrefs) b).hasFileChanged();
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    public void reload() {
        try {
            SharedPreferences b = base;
            if (b instanceof RemotePrefs) {
                ((RemotePrefs) b).reload();
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public Map<String, ?> getAll() {
        try {
            return base.getAll();
        } catch (Throwable t) {
            return new HashMap<>();
        }
    }

    @Override
    public String getString(String key, String defValue) {
        try {
            return base.getString(key, defValue);
        } catch (Throwable t) {
            return defValue;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Set<String> getStringSet(String key, Set<String> defValues) {
        try {
            return base.getStringSet(key, defValues);
        } catch (Throwable t) {
            return defValues;
        }
    }

    @Override
    public int getInt(String key, int defValue) {
        try {
            return base.getInt(key, defValue);
        } catch (Throwable t) {
            return defValue;
        }
    }

    @Override
    public long getLong(String key, long defValue) {
        try {
            return base.getLong(key, defValue);
        } catch (Throwable t) {
            return defValue;
        }
    }

    @Override
    public float getFloat(String key, float defValue) {
        try {
            return base.getFloat(key, defValue);
        } catch (Throwable t) {
            return defValue;
        }
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        try {
            return base.getBoolean(key, defValue);
        } catch (Throwable t) {
            return defValue;
        }
    }

    @Override
    public boolean contains(String key) {
        try {
            return base.contains(key);
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public Editor edit() {
        throw new UnsupportedOperationException("XSharedPreferences is read-only");
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(
            OnSharedPreferenceChangeListener listener) {
        // Never fired cross-process; accepted for API compatibility.
    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(
            OnSharedPreferenceChangeListener listener) {
    }
}
