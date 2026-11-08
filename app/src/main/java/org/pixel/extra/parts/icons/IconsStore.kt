package org.pixel.extra.parts.icons

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.core.graphics.PathParser
import org.pixel.extra.parts.R

// Fail-safe Settings.Global bridge plus snapshot state for the Icons screen.
// Every accessor catches binder failures and falls back to the default, so a
// denied write never crashes the UI. Storage scope is Settings.Global for all
// keys (launcher_current_icon_pack and pixel_launcher_native_search included).
object IconsStore {
    const val TINT_MODE_OFF = 0
    const val TINT_MODE_CUSTOM = 1
    const val TINT_MODE_AUTO = 2

    const val SCALE_MIN = 0f
    const val SCALE_MAX = 200f
    const val SCALE_DEFAULT = 72f

    const val DEFAULT_BACKGROUND_TINT_COLOR = 0x00000000
    const val DEFAULT_FOREGROUND_TINT_COLOR = -0x1

    // Fixed swatch palette for the skeleton tint pickers. Integer constants
    // are data, not user-visible text, so no string resources are involved.
    val TINT_SWATCHES: List<Int> = listOf(
        0x00000000,
        0xFF000000.toInt(),
        0xFFFFFFFF.toInt(),
        0xFFE53935.toInt(),
        0xFFFB8C00.toInt(),
        0xFFFFEB3B.toInt(),
        0xFF43A047.toInt(),
        0xFF1E88E5.toInt(),
        0xFF8E24AA.toInt(),
    )

    private val PACK_THEME_ACTIONS = listOf(
        "org.adw.launcher.THEMES",
        "com.teslacoilsw.launcher.THEME",
        "com.novalauncher.THEME",
    )

    fun getInt(context: Context, key: String, defaultValue: Int): Int {
        return runCatching {
            Settings.Global.getInt(context.contentResolver, key, defaultValue)
        }.getOrDefault(defaultValue)
    }

    fun putInt(context: Context, key: String, value: Int) {
        runCatching {
            Settings.Global.putInt(context.contentResolver, key, value)
        }
    }

    fun getFloat(context: Context, key: String, defaultValue: Float): Float {
        return runCatching {
            Settings.Global.getFloat(context.contentResolver, key, defaultValue)
        }.getOrDefault(defaultValue)
    }

    fun putFloat(context: Context, key: String, value: Float) {
        runCatching {
            Settings.Global.putFloat(context.contentResolver, key, value)
        }
    }

    fun getString(context: Context, key: String, defaultValue: String? = null): String? {
        return runCatching {
            Settings.Global.getString(context.contentResolver, key)
        }.getOrNull() ?: defaultValue
    }

    fun putString(context: Context, key: String, value: String?) {
        runCatching {
            Settings.Global.putString(context.contentResolver, key, value)
        }
    }

    fun isEnabled(context: Context, key: String, defaultValue: Boolean = false): Boolean {
        return getInt(context, key, if (defaultValue) 1 else 0) != 0
    }

    fun putEnabled(context: Context, key: String, enabled: Boolean) {
        putInt(context, key, if (enabled) 1 else 0)
    }

    fun normalizeTintMode(mode: Int): Int {
        return if (mode == TINT_MODE_CUSTOM || mode == TINT_MODE_AUTO) mode else TINT_MODE_OFF
    }

    fun normalizeScale(value: Float): Float {
        return value.coerceIn(SCALE_MIN, SCALE_MAX)
    }

    fun isValidShapePath(pathData: String): Boolean {
        if (pathData.isBlank()) return false
        return runCatching {
            PathParser.createPathFromPathData(pathData.trim())
            true
        }.getOrDefault(false)
    }

    // Full snapshot of every Icons key, read once per screen entry.
    data class IconsState(
        val moduleEnabled: Boolean = false,
        val launcherOnly: Boolean = false,
        val systemStretch: Boolean = false,
        val systemRemove: Boolean = false,
        val systemScale: Float = SCALE_DEFAULT,
        val notificationStretch: Boolean = false,
        val notificationRemove: Boolean = false,
        val notificationScale: Float = SCALE_DEFAULT,
        val launcherStretch: Boolean = false,
        val launcherRemove: Boolean = false,
        val launcherScale: Float = SCALE_DEFAULT,
        val backgroundTintMode: Int = TINT_MODE_OFF,
        val backgroundTintColor: Int = DEFAULT_BACKGROUND_TINT_COLOR,
        val foregroundTintMode: Int = TINT_MODE_OFF,
        val foregroundTintColor: Int = DEFAULT_FOREGROUND_TINT_COLOR,
        val customPath: String = "",
        val customName: String = "",
        val workspaceMatchAllApps: Boolean = false,
        val ignoreCustomSettings: Boolean = false,
        val allAppsFollowWorkspace: Boolean = false,
        val allAppsThemedIcons: Boolean = false,
        val suggestionsThemedIcons: Boolean = false,
        val nativeSearch: Boolean = true,
        val currentIconPack: String? = null,
    )

    fun loadState(context: Context): IconsState {
        return IconsState(
            moduleEnabled = isEnabled(context, IconsKeys.APP_ICONS_ENABLED, false),
            launcherOnly = isEnabled(context, IconsKeys.APP_ICONS_LAUNCHER_ENABLED, false),
            systemStretch = isEnabled(context, IconsKeys.APP_ICONS_SYSTEM_STRETCH_SHAPE, false),
            systemRemove = isEnabled(context, IconsKeys.APP_ICONS_SYSTEM_REMOVE_SHAPE, false),
            systemScale = normalizeScale(
                getFloat(context, IconsKeys.APP_ICONS_SYSTEM_SHAPE_SCALE, SCALE_DEFAULT)
            ),
            notificationStretch = isEnabled(
                context, IconsKeys.APP_ICONS_NOTIFICATION_STRETCH_SHAPE, false
            ),
            notificationRemove = isEnabled(
                context, IconsKeys.APP_ICONS_NOTIFICATION_REMOVE_SHAPE, false
            ),
            notificationScale = normalizeScale(
                getFloat(context, IconsKeys.APP_ICONS_NOTIFICATION_SHAPE_SCALE, SCALE_DEFAULT)
            ),
            launcherStretch = isEnabled(
                context, IconsKeys.APP_ICONS_LAUNCHER_STRETCH_SHAPE, false
            ),
            launcherRemove = isEnabled(
                context, IconsKeys.APP_ICONS_LAUNCHER_REMOVE_SHAPE, false
            ),
            launcherScale = normalizeScale(
                getFloat(context, IconsKeys.APP_ICONS_LAUNCHER_SHAPE_SCALE, SCALE_DEFAULT)
            ),
            backgroundTintMode = normalizeTintMode(
                getInt(
                    context,
                    IconsKeys.APP_ICONS_SHAPE_BACKGROUND_TINT_MODE,
                    TINT_MODE_OFF
                )
            ),
            backgroundTintColor = getInt(
                context,
                IconsKeys.APP_ICONS_SHAPE_BACKGROUND_TINT_COLOR,
                DEFAULT_BACKGROUND_TINT_COLOR
            ),
            foregroundTintMode = normalizeTintMode(
                getInt(
                    context,
                    IconsKeys.APP_ICONS_SHAPE_FOREGROUND_TINT_MODE,
                    TINT_MODE_OFF
                )
            ),
            foregroundTintColor = getInt(
                context,
                IconsKeys.APP_ICONS_SHAPE_FOREGROUND_TINT_COLOR,
                DEFAULT_FOREGROUND_TINT_COLOR
            ),
            customPath = getString(context, IconsKeys.ICON_SHAPE_CUSTOM_PATH, "").orEmpty(),
            customName = getString(context, IconsKeys.ICON_SHAPE_CUSTOM_NAME, "").orEmpty(),
            workspaceMatchAllApps = isEnabled(
                context, IconsKeys.ICON_SHAPE_WORKSPACE_MATCH_ALL_APPS, false
            ),
            ignoreCustomSettings = isEnabled(
                context, IconsKeys.ICON_SHAPE_IGNORE_CUSTOM_SETTINGS, false
            ),
            allAppsFollowWorkspace = isEnabled(
                context, IconsKeys.ICON_SHAPE_ALL_APPS_FOLLOW_WORKSPACE, false
            ),
            allAppsThemedIcons = isEnabled(
                context, IconsKeys.ICON_SHAPE_ALL_APPS_THEMED_ICONS, false
            ),
            suggestionsThemedIcons = isEnabled(
                context, IconsKeys.ICON_SHAPE_SUGGESTIONS_THEMED_ICONS, false
            ),
            nativeSearch = isEnabled(context, IconsKeys.PIXEL_LAUNCHER_NATIVE_SEARCH, true),
            currentIconPack = getString(context, IconsKeys.LAUNCHER_CURRENT_ICON_PACK, null)
                ?.takeIf { it.isNotBlank() },
        )
    }

    // Built-in shape presets for the skeleton picker. Path data uses the same
    // 100x100 viewport convention as the legacy overlay manager.
    data class ShapePreset(
        val id: String,
        @StringRes val labelRes: Int,
        val pathData: String,
    )

    val SHAPE_PRESETS: List<ShapePreset> = listOf(
        ShapePreset(
            id = "squircle",
            labelRes = R.string.icons_preset_squircle,
            pathData = "M50,0 C10,0 0,10 0,50 0,90 10,100 50,100 90,100 100,90 100,50 100,10 90,0 50,0 Z",
        ),
        ShapePreset(
            id = "rounded",
            labelRes = R.string.icons_preset_rounded,
            pathData = "M50,0L88,0 C94.4,0 100,5.4 100 12 L100,88 C100,94.6 94.6 100 88 100 L12,100 C5.4,100 0,94.6 0,88 L0 12 C0 5.4 5.4 0 12 0 L50,0 Z",
        ),
        ShapePreset(
            id = "teardrop",
            labelRes = R.string.icons_preset_teardrop,
            pathData = "M50,0 C77.6,0 100,22.4 100,50 L100,88 C100,94.6 94.6,100 88,100 L50,100 C22.4 100 0 77.6 0 50C0 22.4 22.4 0 50 0 Z",
        ),
        ShapePreset(
            id = "hexagon",
            labelRes = R.string.icons_preset_hexagon,
            pathData = "M 50,0 L 100,25, 100,75, 50,100, 0,75, 0,25 Z",
        ),
        ShapePreset(
            id = "flower",
            labelRes = R.string.icons_preset_flower,
            pathData = "M50,0 C60.6,0 69.9,5.3 75.6,13.5 78.5,17.8 82.3,21.5 86.6,24.5 94.7,30.1 100,39.4 100,50 100,60.6 94.7,69.9 86.5,75.6 82.2,78.5 78.5,82.3 75.5,86.6 69.9,94.7 60.6,100 50,100 39.4,100 30.1,94.7 24.4,86.5 21.5,82.2 17.7,78.5 13.4,75.5 5.3,69.9 0,60.6 0,50 0,39.4 5.3,30.1 13.5,24.4 17.8,21.5 21.5,17.7 24.5,13.4 30.1,5.3 39.4,0 50,0 Z",
        ),
        ShapePreset(
            id = "pebble",
            labelRes = R.string.icons_preset_pebble,
            pathData = "M55,0 C25,0 0,25 0,50 0,78 28,100 55,100 85,100 100,85 100,58 100,30 86,0 55,0 Z",
        ),
    )

    data class IconPackRef(
        val packageName: String,
        val label: String,
    )

    // Lightweight pack discovery for the skeleton list: query the launcher
    // theme intents and keep package labels. Full per-app mapping, export and
    // progress tracking stay in the later apply stage.
    fun discoverIconPacks(context: Context): List<IconPackRef> {
        val pm: PackageManager = context.packageManager
        val found = linkedMapOf<String, String>()
        PACK_THEME_ACTIONS.forEach { action ->
            val intent = Intent(action)
            runCatching { pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY) }
                .getOrDefault(emptyList())
                .mapNotNullTo(mutableSetOf()) { it.activityInfo?.packageName }
                .forEach { packageName ->
                    if (packageName !in found) {
                        val label = runCatching {
                            pm.getApplicationInfo(packageName, 0)
                                .loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() }
                        }.getOrNull() ?: packageName
                        found[packageName] = label
                    }
                }
            runCatching { pm.queryBroadcastReceivers(intent, 0) }
                .getOrDefault(emptyList())
                .mapNotNullTo(mutableSetOf()) { it.activityInfo?.packageName }
                .forEach { packageName ->
                    if (packageName !in found) {
                        val label = runCatching {
                            pm.getApplicationInfo(packageName, 0)
                                .loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() }
                        }.getOrNull() ?: packageName
                        found[packageName] = label
                    }
                }
        }
        return found.map { (packageName, label) ->
            IconPackRef(packageName = packageName, label = label)
        }.sortedBy { it.label.lowercase() }
    }
}
