package org.pixel.customparts.ui.addons

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.PersistableBundle
import android.provider.Settings
import android.telephony.CarrierConfigManager
import android.telephony.SubscriptionManager
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile
import java.net.URL
import java.util.Locale
import org.pixel.customparts.R
import org.pixel.customparts.dynamicDarkColorScheme
import org.pixel.customparts.dynamicLightColorScheme
import org.pixel.customparts.activities.AddonPageActivity
import org.pixel.customparts.core.ApkModulesGate
import org.pixel.customparts.ui.ColorPickerDialog
import org.pixel.customparts.ui.ExpandableWarningCard
import org.pixel.customparts.utils.HookKeyCompat
import org.pixel.customparts.utils.PixelPartsTileRefresher
import org.pixel.customparts.utils.TileUtils
import org.pixel.customparts.utils.dynamicStringResource
import org.pixel.customparts.utils.runShellCommandForResult

private const val TAG = "AddonManagerUI"
private const val ADDON_DIR = "/data/pixelparts/addons"
private const val SYSTEM_ADDON_DIR = "/system_ext/etc/pixelparts/addons"
private const val ADDON_PREFIX = "pixel_addon_"
private const val INJECT_PREFIX = "pixel_extra_parts_inject_package_"
private const val SAFE_MODE_SETTING = "pixel_addon_safe_mode"
private const val IGNORED_PACKAGE = "android" // system_server — can't hook reliably
private const val DYNAMIC_ADDON_TILE_COUNT = 40

// =====================================================================
// Data model
// =====================================================================

data class AddonUiModel(
    val id: String,
    val entryClass: String,
    val name: String,
    val author: String,
    val description: String,
    val version: String,
    val jarPath: String,
    val defaultTargets: Set<String>,
    val enabled: Boolean,
    val scopeMode: Int,          // 0=default, 1=custom, 2=merge
    val customTargets: Set<String>,
    val settings: List<AddonSettingDef> = emptyList(),
    val isSystem: Boolean = false,
    /** True when addon has no entryClass — it only provides settings UI via main[]/settings[] */
    val settingsOnly: Boolean = false,
    val iconBitmap: Bitmap? = null,
    val backgroundBitmap: Bitmap? = null,
    val backgroundMode: String = "gradient",  // "cover", "gradient"
    val backgroundAlpha: Int = 50,             // 0–100, intensity of background image
    val backgroundGradientSteps: List<Int> = listOf(0, 100), // gradient opacity stops (0–100 each)
    val backgroundBlur: Boolean = false,       // enable blur on background
    val backgroundBlurRadius: Int = 25,        // blur radius in dp
    val cardColor: String = "",                // custom card background color (hex, e.g. "#FF5722")
    val backgroundScope: String = "full",      // "full" = background extends with settings, "header" = header only
    val accentColor: String = "",
    val updateUrl: String = "",
    val hasDataOverride: Boolean = false,
    val systemJarPath: String = "",
    val dataOverrideJarPath: String = "",
    /** Top-level main[] entries for this addon (tree already built). Empty if addon has no main[]. */
    val mainEntries: List<AddonMainEntry> = emptyList()
)

/** A single setting definition parsed from addon.json "settings" array */
data class AddonSettingDef(
    val key: String,
    val title: String,
    val description: String = "",
    val category: String = "",
    val categoryTitle: String = "",
    val categoryDescription: String = "",
    val titleSizeSp: Float = 0f,
    val descriptionSizeSp: Float = 0f,
    val type: SettingType,
    val provider: SettingProvider = SettingProvider.GLOBAL,
    val defaultInt: Int = 0,
    val defaultFloat: Float = 0f,
    val defaultString: String = "",
    val defaultBool: Boolean = false,
    val min: Float = 0f,
    val max: Float = 100f,
    val step: Float = 1f,
    val unit: String = "",
    val options: List<SelectOption> = emptyList(),
    val mimeType: String = "*/*",
    val storage: SettingStorage = SettingStorage.SETTINGS,
    val children: List<AddonSettingDef> = emptyList(),
    val groupMode: GroupMode = GroupMode.INLINE,
    val defaultExpanded: Boolean = false,
    val closeButtonPosition: CloseButtonPosition = CloseButtonPosition.END,
    val visualType: VisualType = VisualType.TEXT,
    val imagePath: String = "",
    val sizeDp: Int = 12,
    val thicknessDp: Int = 1,
    val color: String = "",
    val surfaceAlpha: Float = -1f,
    val colorFormat: ColorOutputFormat = ColorOutputFormat.HEX_RGB,
    val allowAlpha: Boolean = false,
    val accentColor: String = "",
    val exclusiveGroup: String = "",
    val exclusiveWith: List<String> = emptyList(),
    val enabledIfAll: List<String> = emptyList(),
    val enabledIfAny: List<String> = emptyList(),
    val disabledIfAll: List<String> = emptyList(),
    val disabledIfAny: List<String> = emptyList(),
    val forceValueWhenDisabled: String = "",
    // Icon fields
    val icon: String = "",              // Material icon name (iconType=app) or JAR path (iconType=file)
    val iconType: String = "",          // "app" = Material3 icon, "file" = from JAR
    val iconShape: String = "none",     // "none", "circle", "rounded", "custom"
    val iconShapePath: String = "",     // path to custom shape svg/xml inside JAR (iconShape=custom)
    val iconSize: Int = 24,             // icon size inside shape in dp
    // App list fields
    val showSelected: Boolean = true,   // whether to show selected items chips below the button
    // Dynamic options from file
    val optionsSource: String = "",     // "file" = load options from external JSON
    val optionsPath: String = "",       // path relative to addon data dir (e.g. "dir/array.json")
    val appPickerMode: String = "modal", // "modal" or "activity" for APP_LIST settings
    // Dynamic QS tile fields
    val tileConfigurable: Boolean = false,
    val tileTargets: List<TileTargetOption> = emptyList(),
    val tileActivities: List<SelectOption> = emptyList(),
    val command: String = "",
    val commandOn: String = "",
    val commandOff: String = "",
    val showOutput: Boolean = true,
    val settingsOn: List<SettingsAssignmentDef> = emptyList(),
    val settingsOff: List<SettingsAssignmentDef> = emptyList(),
    val binderOn: List<BinderActionDef> = emptyList(),
    val binderOff: List<BinderActionDef> = emptyList()
)

enum class SettingType { INT, FLOAT, STRING, SELECT, SELECT_BUTTON, FILE, TOGGLE, SWITCH, CHECKBOX, APP_LIST, COLOR, GROUP, VISUAL, TILE, COMMAND_BUTTON }
enum class SettingProvider { GLOBAL, SYSTEM, SECURE }
enum class SettingStorage { SETTINGS, ADDON_FILE, INTERNAL_FILE, EXTERNAL_FILE }
enum class GroupMode { INLINE, EXPANDABLE, FULLSCREEN, CARD, IMMERSIVE_EXPAND }
enum class CloseButtonPosition { START, END }
enum class VisualType { TEXT, IMAGE, SPACER, DIVIDER, DASHED_DIVIDER, WARNING }
enum class ColorOutputFormat { HEX_RGB, HEX_ARGB, RGB_CSV, RGBA_CSV, INT }

data class SelectOption(val value: String, val label: String)

data class TileTargetOption(
    val key: String,
    val label: String,
    val mode: String = "toggle",
    val values: List<String> = emptyList(),
    val labels: List<String> = emptyList(),
    val pageId: String = ""
)

data class AddonUpdateInfo(
    val version: String,
    val downloadUrl: String,
    val changelog: String,
    val extraInfo: String
)

data class SettingsAssignmentDef(
    val key: String,
    val provider: SettingProvider = SettingProvider.GLOBAL,
    val value: String = "1",
    val valueType: AssignmentValueType = AssignmentValueType.INT
)

enum class AssignmentValueType { STRING, INT, FLOAT, BOOL }

data class BinderActionDef(
    val type: String,
    val targetSubIds: List<Int> = emptyList(),
    val useActiveSubscriptions: Boolean = true,
    val fallbackSubIds: List<Int> = emptyList(),
    val clear: Boolean = false,
    val values: List<BinderValueDef> = emptyList()
)

data class BinderValueDef(
    val key: String,
    val value: Any?,
    val valueType: BinderValueType = BinderValueType.BOOL
)

enum class BinderValueType { STRING, BOOL, INT, LONG, DOUBLE, INT_ARRAY, LONG_ARRAY, STRING_ARRAY, BOOL_ARRAY }

// =====================================================================
// Main-menu entry models (addon.json "main" array)
// =====================================================================

/**
 * A single entry in the addon's "main" array.
 * Represents one button that appears in the app's main menu (or nested inside another entry).
 *
 * addon.json schema:
 * {
 *   "main": [
 *     {
 *       "id": "my-settings",          // unique within this addon; used as path segment
 *       "title": "My Settings",
 *       "subtitle": "Short description shown under the button",
 *       "icon": "META-INF/icon.png",  // optional; path inside JAR
 *       "group": "system",            // main-menu group: "gesture"|"system"|"network" (default "system")
 *       "priority": 100,              // display order within the group (higher = first)
 *       "settings": [ ... ]           // same settings array as in the card
 *     },
 *     {
 *       "id": "main/my-settings/advanced",  // slash-path → nested under "my-settings"
 *       "title": "Advanced",
 *       "settings": [ ... ]
 *     }
 *   ]
 * }
 *
 * Path resolution rules:
                    val defaultSettingsTitle = dynamicStringResource(R.string.addon_settings_title)
 *  - "foo"            → top-level entry with id "foo"
                        val categoryTitle = category.title.ifBlank { if (settingCategories.size == 1) defaultSettingsTitle else "" }
 */
data class AddonMainEntry(
    val addonId: String,          // owning addon id
    val addonJarPath: String,
    val isSystemAddon: Boolean,
    val rawId: String,            // full raw id string from json (may contain slashes)
    val pathSegments: List<String>, // parsed path segments (rawId split by '/')
    val title: String,
    val subtitle: String,
    val icon: String,
    val iconType: String,
    val iconBitmap: android.graphics.Bitmap?,
    val iconShape: String = "circle",
    val iconSize: Int = 20,
    val iconColor: String = "",
    val iconBackground: String = "",
    val titleSizeSp: Float = 0f,
    val descriptionSizeSp: Float = 0f,
    val group: String,            // "gesture" | "system" | "network"
    val priority: Int,
    val targetActivity: String = "",
    val targetSlot: String = "",
    val settings: List<AddonSettingDef>,
    val children: MutableList<AddonMainEntry> = mutableListOf()
) {
    /** The leaf segment used as the logical id of this entry */
    val leafId: String get() = pathSegments.lastOrNull() ?: rawId
}

/** Flat list of all top-level AddonMainEntry items across all addons, tree already built */
data class AddonMainMenuModel(
    val entries: List<AddonMainEntry>  // only top-level entries; children are nested inside
)

/** Info about an installed app for the package picker */
data class AppInfoItem(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val isSystem: Boolean,
    val isLaunchable: Boolean
)

// =====================================================================
// Settings helpers
// =====================================================================
