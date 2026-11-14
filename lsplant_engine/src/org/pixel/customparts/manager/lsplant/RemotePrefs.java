package org.pixel.customparts.manager.lsplant;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

/**
 * Cross-process SharedPreferences for Xposed modules.
 *
 * <p>A module's UI process and its target packages run in different
 * processes (and different UIDs): the module's own
 * {@code shared_prefs} dir is unreadable from a target app (SELinux
 * app_data isolation), so a plain {@code getSharedPreferences} bridge
 * would silently read defaults in the target. This facade persists to
 * {@code /data/pixelparts/rprefs/<modulePkg>.xml} (groups as sections)
 * instead — a dir any injected process may use (see sepolicy
 * {@code pixelparts_rprefs_file}). Both sides see the same values.
 *
 * <p>MAC constraint (core neverallow): untrusted apps may not
 * create/unlink files outside their sandbox — so the file must already
 * exist. The manager app (system_app) creates it at module-enable and
 * boot time (see {@code ensureModulePrefsFile}); injected processes
 * only open/read/write. If the file is missing, reads return defaults
 * and writes fail loudly in logcat (never silently wrong).
 *
 * <p>Limitations: {@link OnSharedPreferenceChangeListener} is stored
 * but never fired cross-process; {@link Editor#apply()} is
 * synchronous (write-through, like commit).
 */
public final class RemotePrefs implements SharedPreferences {
    private static final String TAG = "RemotePrefs";
    private static final String DIR = "/data/pixelparts/rprefs";

    private static final Map<String, RemotePrefs> CACHE = new HashMap<>();

    private final File file;
    private final String group;
    private final Map<String, Object> values = new HashMap<>();
    private final List<OnSharedPreferenceChangeListener> listeners = new ArrayList<>();
    private boolean loaded = false;
    private long loadedMtime = 0L;

    private RemotePrefs(File file, String group) {
        this.file = file;
        this.group = group;
    }

    /** Facade for a module's preference group, shared across processes. */
    public static synchronized SharedPreferences forModule(Context context,
            String modulePackage, String group) {
        return forModule(modulePackage, group);
    }

    /** Context-free variant (plain files — no package visibility needed). */
    public static synchronized SharedPreferences forModule(
            String modulePackage, String group) {
        if (group == null) group = "default";
        String safeGroup = group.replaceAll("[^A-Za-z0-9_.-]", "_");
        if (safeGroup.isEmpty()) safeGroup = "default";
        String pkg = modulePackage != null ? modulePackage : "unknown";
        String key = pkg + "/" + safeGroup;
        RemotePrefs prefs = CACHE.get(key);
        if (prefs == null) {
            prefs = new RemotePrefs(new File(DIR, pkg + ".xml"), safeGroup);
            CACHE.put(key, prefs);
        }
        return prefs;
    }

    /**
     * Creates the module's prefs file if absent (manager app only —
     * system_app may create here; call at module-enable and boot).
     * World-readable/writable so any injected UID can use it.
     */
    public static void ensureModulePrefsFile(String modulePackage) {
        if (modulePackage == null || modulePackage.isEmpty()) return;
        try {
            File dir = new File(DIR);
            dir.mkdirs();
            File f = new File(dir, modulePackage + ".xml");
            if (!f.exists()) {
                FileOutputStream out = null;
                try {
                    out = new FileOutputStream(f);
                    XmlSerializer ser =
                            XmlPullParserFactory.newInstance().newSerializer();
                    ser.setOutput(out, "UTF-8");
                    ser.startDocument("UTF-8", true);
                    ser.startTag(null, "rprefs");
                    ser.endTag(null, "rprefs");
                    ser.endDocument();
                    ser.flush();
                    out.getFD().sync();
                } finally {
                    if (out != null) {
                        try {
                            out.close();
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
            f.setReadable(true, false);
            f.setWritable(true, false);
        } catch (Throwable t) {
            Log.w(TAG, "Failed to ensure prefs file for " + modulePackage, t);
        }
    }

    private synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        loadedMtime = file.exists() ? file.lastModified() : 0L;
        if (!file.exists()) {
            Log.w(TAG, "Prefs file missing (manager did not create it?): " + file);
            return;
        }
        FileInputStream in = null;
        try {
            XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
            in = new FileInputStream(file);
            parser.setInput(in, "UTF-8");
            int event = parser.getEventType();
            String wantGroup = null;
            String name = null;
            StringBuilder text = null;
            Set<String> set = null;
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    String tag = parser.getName();
                    if ("group".equals(tag)) {
                        wantGroup = group.equals(parser.getAttributeValue(null, "name"))
                                ? group : null;
                    } else if (wantGroup != null) {
                        if ("string".equals(tag)) {
                            // A nested <string> inside <set> has no name —
                            // it is a set item, not a new entry.
                            if (set == null) {
                                name = parser.getAttributeValue(null, "name");
                            }
                            text = new StringBuilder();
                        } else if ("int".equals(tag)) {
                            values.put(parser.getAttributeValue(null, "name"),
                                    Integer.parseInt(parser.getAttributeValue(null, "value")));
                        } else if ("long".equals(tag)) {
                            values.put(parser.getAttributeValue(null, "name"),
                                    Long.parseLong(parser.getAttributeValue(null, "value")));
                        } else if ("float".equals(tag)) {
                            values.put(parser.getAttributeValue(null, "name"),
                                    Float.parseFloat(parser.getAttributeValue(null, "value")));
                        } else if ("boolean".equals(tag)) {
                            values.put(parser.getAttributeValue(null, "name"),
                                    Boolean.parseBoolean(parser.getAttributeValue(null, "value")));
                        } else if ("set".equals(tag)) {
                            name = parser.getAttributeValue(null, "name");
                            set = new HashSet<>();
                        }
                    }
                } else if (event == XmlPullParser.TEXT) {
                    if (wantGroup != null) {
                        // Whitespace between elements is ignored: text only
                        // accumulates inside a <string> element.
                        if (text != null) text.append(parser.getText());
                    }
                } else if (event == XmlPullParser.END_TAG) {
                    String tag = parser.getName();
                    if ("group".equals(tag)) {
                        wantGroup = null;
                    } else if (wantGroup != null) {
                        if ("string".equals(tag)) {
                            if (set != null) {
                                set.add(text != null ? text.toString() : "");
                                text = null;
                            } else if (name != null) {
                                values.put(name, text != null ? text.toString() : "");
                                name = null;
                                text = null;
                            }
                        } else if ("set".equals(tag) && name != null) {
                            values.put(name, set != null ? set : new HashSet<String>());
                            name = null;
                            set = null;
                        }
                    }
                }
                event = parser.next();
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed to load " + file + ", starting empty", t);
            values.clear();
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /**
     * Persists ALL groups (read-modify-write the whole file in place —
     * truncate, no tmp/rename: untrusted apps may not create files here).
     */
    private synchronized boolean persistLocked() {
        if (!file.exists()) {
            Log.e(TAG, "Cannot persist, file missing (manager must create it): " + file);
            return false;
        }
        // Re-read other groups so a concurrent writer's groups survive.
        Map<String, Map<String, Object>> all = readAllGroups();
        all.put(group, new HashMap<>(values));
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(file, false);
            XmlSerializer ser = XmlPullParserFactory.newInstance().newSerializer();
            ser.setOutput(out, "UTF-8");
            ser.startDocument("UTF-8", true);
            ser.startTag(null, "rprefs");
            List<String> groups = new ArrayList<>(all.keySet());
            Collections.sort(groups);
            for (String g : groups) {
                ser.startTag(null, "group");
                ser.attribute(null, "name", g);
                writeMap(ser, all.get(g));
                ser.endTag(null, "group");
            }
            ser.endTag(null, "rprefs");
            ser.endDocument();
            ser.flush();
            out.getFD().sync();
            out.close();
            out = null;
            loadedMtime = file.exists() ? file.lastModified() : 0L;
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "Failed to persist " + file, t);
            return false;
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /** Reads every group from the file (no caching side effects). */
    private Map<String, Map<String, Object>> readAllGroups() {
        return readGroupedMap(file);
    }

    private static Map<String, Map<String, Object>> readGroupedMap(File f) {
        Map<String, Map<String, Object>> all = new HashMap<>();
        if (f == null || !f.exists()) return all;
        FileInputStream in = null;
        try {
            XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
            in = new FileInputStream(f);
            parser.setInput(in, "UTF-8");
            int event = parser.getEventType();
            String cur = null;
            String name = null;
            StringBuilder text = null;
            Set<String> set = null;
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    String tag = parser.getName();
                    if ("group".equals(tag)) {
                        cur = parser.getAttributeValue(null, "name");
                        if (cur != null && !all.containsKey(cur)) {
                            all.put(cur, new HashMap<String, Object>());
                        }
                    } else if (cur != null) {
                        if ("string".equals(tag)) {
                            // A nested <string> inside <set> has no name —
                            // it is a set item, not a new entry.
                            if (set == null) {
                                name = parser.getAttributeValue(null, "name");
                            }
                            text = new StringBuilder();
                        } else if ("int".equals(tag)) {
                            all.get(cur).put(parser.getAttributeValue(null, "name"),
                                    Integer.parseInt(parser.getAttributeValue(null, "value")));
                        } else if ("long".equals(tag)) {
                            all.get(cur).put(parser.getAttributeValue(null, "name"),
                                    Long.parseLong(parser.getAttributeValue(null, "value")));
                        } else if ("float".equals(tag)) {
                            all.get(cur).put(parser.getAttributeValue(null, "name"),
                                    Float.parseFloat(parser.getAttributeValue(null, "value")));
                        } else if ("boolean".equals(tag)) {
                            all.get(cur).put(parser.getAttributeValue(null, "name"),
                                    Boolean.parseBoolean(parser.getAttributeValue(null, "value")));
                        } else if ("set".equals(tag)) {
                            name = parser.getAttributeValue(null, "name");
                            set = new HashSet<>();
                        }
                    }
                } else if (event == XmlPullParser.TEXT) {
                    if (cur != null) {
                        // Whitespace between elements is ignored: text only
                        // accumulates inside a <string> element.
                        if (text != null) text.append(parser.getText());
                    }
                } else if (event == XmlPullParser.END_TAG) {
                    String tag = parser.getName();
                    if ("group".equals(tag)) {
                        cur = null;
                    } else if (cur != null) {
                        if ("string".equals(tag)) {
                            if (set != null) {
                                set.add(text != null ? text.toString() : "");
                                text = null;
                            } else if (name != null) {
                                all.get(cur).put(name,
                                        text != null ? text.toString() : "");
                                name = null;
                                text = null;
                            }
                        } else if ("set".equals(tag) && name != null) {
                            all.get(cur).put(name,
                                    set != null ? set : new HashSet<String>());
                            name = null;
                            set = null;
                        }
                    }
                }
                event = parser.next();
            }
        } catch (Throwable ignored) {
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Throwable ignored) {
                }
            }
        }
        return all;
    }

    private static void writeMap(XmlSerializer ser, Map<String, Object> map)
            throws Exception {
        if (map == null) return;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            Object v = e.getValue();
            if (v instanceof String) {
                ser.startTag(null, "string");
                ser.attribute(null, "name", e.getKey());
                ser.text((String) v);
                ser.endTag(null, "string");
            } else if (v instanceof Integer) {
                ser.startTag(null, "int");
                ser.attribute(null, "name", e.getKey());
                ser.attribute(null, "value", v.toString());
                ser.endTag(null, "int");
            } else if (v instanceof Long) {
                ser.startTag(null, "long");
                ser.attribute(null, "name", e.getKey());
                ser.attribute(null, "value", v.toString());
                ser.endTag(null, "long");
            } else if (v instanceof Float) {
                ser.startTag(null, "float");
                ser.attribute(null, "name", e.getKey());
                ser.attribute(null, "value", v.toString());
                ser.endTag(null, "float");
            } else if (v instanceof Boolean) {
                ser.startTag(null, "boolean");
                ser.attribute(null, "name", e.getKey());
                ser.attribute(null, "value", v.toString());
                ser.endTag(null, "boolean");
            } else if (v instanceof Set) {
                ser.startTag(null, "set");
                ser.attribute(null, "name", e.getKey());
                for (Object o : (Set<?>) v) {
                    if (o == null) continue;
                    ser.startTag(null, "string");
                    ser.text(o.toString());
                    ser.endTag(null, "string");
                }
                ser.endTag(null, "set");
            }
        }
    }

    /** Re-reads the file (cross-process writes); mirrors XSharedPreferences.reload. */
    public synchronized void reload() {
        loaded = false;
        values.clear();
        ensureLoaded();
    }

    /** True when the file changed on disk since the last load. */
    public synchronized boolean hasFileChanged() {
        try {
            return file.exists() && file.lastModified() != loadedMtime;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Imports a plain shared_prefs-format file (as written by the module
     * UI into its own data dir) into a shared group, then persists.
     * Called from the module's own (self) process only — the only place
     * whose UID may read the module's private files.
     */
    static synchronized boolean importFile(String modulePackage, String group,
            File src) {
        try {
            Map<String, Object> map = readPlainMap(src);
            if (map == null) return false;
            SharedPreferences p = forModule(modulePackage, group);
            if (!(p instanceof RemotePrefs)) return false;
            RemotePrefs rp = (RemotePrefs) p;
            synchronized (rp) {
                rp.ensureLoaded();
                // Full-file mirror: clear first so keys removed via
                // editor.remove()/clear() disappear instead of lingering.
                rp.values.clear();
                rp.values.putAll(map);
                return rp.persistLocked();
            }
        } catch (Throwable t) {
            Log.w(TAG, "Import failed for " + src, t);
            return false;
        }
    }

    /** Parses a plain {@code <map>} shared_prefs file (module's own format). */
    static Map<String, Object> readPlainMap(File f) {
        Map<String, Object> out = new HashMap<>();
        if (f == null || !f.exists()) return out;
        FileInputStream in = null;
        try {
            XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
            in = new FileInputStream(f);
            parser.setInput(in, "UTF-8");
            int event = parser.getEventType();
            String name = null;
            StringBuilder text = null;
            Set<String> set = null;
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    String tag = parser.getName();
                    if ("string".equals(tag)) {
                        // A nested <string> inside <set> has no name —
                        // it is a set item, not a new entry.
                        if (set == null) {
                            name = parser.getAttributeValue(null, "name");
                        }
                        text = new StringBuilder();
                    } else if ("int".equals(tag)) {
                        out.put(parser.getAttributeValue(null, "name"),
                                Integer.parseInt(parser.getAttributeValue(null, "value")));
                    } else if ("long".equals(tag)) {
                        out.put(parser.getAttributeValue(null, "name"),
                                Long.parseLong(parser.getAttributeValue(null, "value")));
                    } else if ("float".equals(tag)) {
                        out.put(parser.getAttributeValue(null, "name"),
                                Float.parseFloat(parser.getAttributeValue(null, "value")));
                    } else if ("boolean".equals(tag)) {
                        out.put(parser.getAttributeValue(null, "name"),
                                Boolean.parseBoolean(parser.getAttributeValue(null, "value")));
                    } else if ("set".equals(tag)) {
                        name = parser.getAttributeValue(null, "name");
                        set = new HashSet<>();
                    }
                } else if (event == XmlPullParser.TEXT) {
                    // Whitespace between elements is ignored: text only
                    // accumulates inside a <string> element.
                    if (text != null) text.append(parser.getText());
                } else if (event == XmlPullParser.END_TAG) {
                    String tag = parser.getName();
                    if ("string".equals(tag)) {
                        if (set != null) {
                            set.add(text != null ? text.toString() : "");
                            text = null;
                        } else if (name != null) {
                            out.put(name, text != null ? text.toString() : "");
                            name = null;
                            text = null;
                        }
                    } else if ("set".equals(tag) && name != null) {
                        out.put(name, set != null ? set : new HashSet<String>());
                        name = null;
                        set = null;
                    }
                }
                event = parser.next();
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed to read plain prefs " + f, t);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Throwable ignored) {
                }
            }
        }
        return out;
    }

    @Override
    public synchronized Map<String, ?> getAll() {
        ensureLoaded();
        return new HashMap<>(values);
    }

    @Override
    public synchronized String getString(String key, String defValue) {
        ensureLoaded();
        Object v = values.get(key);
        return v instanceof String ? (String) v : defValue;
    }

    @Override
    @SuppressWarnings("unchecked")
    public synchronized Set<String> getStringSet(String key, Set<String> defValues) {
        ensureLoaded();
        Object v = values.get(key);
        if (v instanceof Set) return new HashSet<>((Set<String>) v);
        return defValues;
    }

    @Override
    public synchronized int getInt(String key, int defValue) {
        ensureLoaded();
        Object v = values.get(key);
        return v instanceof Integer ? (Integer) v : defValue;
    }

    @Override
    public synchronized long getLong(String key, long defValue) {
        ensureLoaded();
        Object v = values.get(key);
        return v instanceof Long ? (Long) v : defValue;
    }

    @Override
    public synchronized float getFloat(String key, float defValue) {
        ensureLoaded();
        Object v = values.get(key);
        return v instanceof Float ? (Float) v : defValue;
    }

    @Override
    public synchronized boolean getBoolean(String key, boolean defValue) {
        ensureLoaded();
        Object v = values.get(key);
        return v instanceof Boolean ? (Boolean) v : defValue;
    }

    @Override
    public synchronized boolean contains(String key) {
        ensureLoaded();
        return values.containsKey(key);
    }

    @Override
    public synchronized Editor edit() {
        ensureLoaded();
        return new EditorImpl();
    }

    @Override
    public synchronized void registerOnSharedPreferenceChangeListener(
            OnSharedPreferenceChangeListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
        // NOTE: never fired cross-process by design.
    }

    @Override
    public synchronized void unregisterOnSharedPreferenceChangeListener(
            OnSharedPreferenceChangeListener listener) {
        listeners.remove(listener);
    }

    private final class EditorImpl implements Editor {
        private final Map<String, Object> staged = new HashMap<>();
        private final Set<String> removed = new HashSet<>();
        private boolean clearAll = false;

        @Override
        public Editor putString(String key, String value) {
            staged.put(key, value);
            removed.remove(key);
            return this;
        }

        @Override
        public Editor putStringSet(String key, Set<String> vals) {
            staged.put(key, vals != null ? new HashSet<>(vals) : null);
            removed.remove(key);
            return this;
        }

        @Override
        public Editor putInt(String key, int value) {
            staged.put(key, value);
            removed.remove(key);
            return this;
        }

        @Override
        public Editor putLong(String key, long value) {
            staged.put(key, value);
            removed.remove(key);
            return this;
        }

        @Override
        public Editor putFloat(String key, float value) {
            staged.put(key, value);
            removed.remove(key);
            return this;
        }

        @Override
        public Editor putBoolean(String key, boolean value) {
            staged.put(key, value);
            removed.remove(key);
            return this;
        }

        @Override
        public Editor remove(String key) {
            removed.add(key);
            staged.remove(key);
            return this;
        }

        @Override
        public Editor clear() {
            clearAll = true;
            staged.clear();
            removed.clear();
            return this;
        }

        @Override
        public boolean commit() {
            synchronized (RemotePrefs.this) {
                ensureLoaded();
                if (clearAll) {
                    values.clear();
                    clearAll = false;
                }
                for (String k : removed) values.remove(k);
                removed.clear();
                values.putAll(staged);
                staged.clear();
                return persistLocked();
            }
        }

        @Override
        public void apply() {
            // Synchronous write-through; cross-process listeners unsupported.
            commit();
        }
    }
}
