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
import org.pixel.customparts.utils.runShellCommandForResult
import androidx.compose.ui.res.stringResource

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

private const val XPOSED_MODULE_LIST_KEY = "pixelparts_xposed_modules"

data class XposedModuleUiModel(
    /** Engine key (matches pixel_addon_<keyId>_enabled/scope keys). */
    val keyId: String,
    val id: String,
    val packageName: String,
    val label: String,
    val apkPath: String,
    val versionName: String,
    val minApi: Int,
    val scopes: Set<String>,
    val isLegacy: Boolean,
    val enabled: Boolean,
    val scopeMode: Int,
    val customTargets: Set<String>
)

private fun readModuleRegistry(context: Context): Set<String> {
    val raw = try {
        Settings.Global.getString(context.contentResolver, XPOSED_MODULE_LIST_KEY)
    } catch (_: Throwable) { null }
    if (raw.isNullOrBlank()) return emptySet()
    return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
}

internal fun addModuleToRegistry(context: Context, apkPath: String) {
    try {
        val cur = readModuleRegistry(context).toMutableSet()
        if (cur.add(apkPath)) {
            Settings.Global.putString(context.contentResolver, XPOSED_MODULE_LIST_KEY,
                cur.joinToString(","))
        }
    } catch (_: Throwable) {}
}

internal fun removeModuleFromRegistry(context: Context, apkPath: String) {
    try {
        val cur = readModuleRegistry(context).toMutableSet()
        if (cur.remove(apkPath)) {
            Settings.Global.putString(context.contentResolver, XPOSED_MODULE_LIST_KEY,
                cur.joinToString(","))
        }
    } catch (_: Throwable) {}
}

/**
 * Detect installed LSPosed modules: manifest meta-data `xposedminversion`
 * (legacy) or META-INF/xposed entries (modern). Mirrors Vector's
 * ModuleDetection (manifest + zip-walk), without its TSV cache.
 */
internal fun scanInstalledModules(context: Context): List<XposedModuleUiModel> {
    // TEMPORARY: APK-module support disabled via ApkModulesGate — code kept.
    if (!ApkModulesGate.ENABLED) return emptyList()
    val pm = context.packageManager
    val out = ArrayList<XposedModuleUiModel>()
    val apps = try {
        pm.getInstalledApplications(PackageManager.GET_META_DATA)
    } catch (_: Throwable) { return emptyList() }
    for (info in apps) {
        if (info.packageName == context.packageName) continue
        val legacyMeta = try {
            info.metaData?.containsKey("xposedminversion") == true
        } catch (_: Throwable) { false }
        var modern = false
        var minApi = 0
        val scopes = HashSet<String>()
        var entryCount = 0
        try {
            ZipFile(info.sourceDir).use { zip ->
                if (zip.getEntry("META-INF/xposed/java_init.list") != null) {
                    modern = true
                    zip.getEntry("META-INF/xposed/module.prop")?.let { e ->
                        zip.getInputStream(e).bufferedReader().useLines { lines ->
                            lines.forEach { l ->
                                val t = l.trim()
                                if (t.startsWith("minApiVersion=")) {
                                    minApi = t.substringAfter("=").toIntOrNull() ?: 0
                                }
                            }
                        }
                    }
                    zip.getEntry("META-INF/xposed/scope.list")?.let { e ->
                        zip.getInputStream(e).bufferedReader().useLines { lines ->
                            lines.forEach { l ->
                                val t = l.trim()
                                if (t.isNotEmpty() && !t.startsWith("#")) scopes.add(t)
                            }
                        }
                    }
                    entryCount = 1
                } else if (zip.getEntry("assets/xposed_init") != null) {
                    entryCount = 1
                }
            }
        } catch (_: Throwable) { continue }
        if (!legacyMeta && !modern && entryCount == 0) continue
        val pkg = info.packageName
        val keyId = pkg
        val label = try { pm.getApplicationLabel(info).toString() } catch (_: Throwable) { pkg }
        val version = try {
            pm.getPackageInfo(pkg, 0)?.versionName ?: ""
        } catch (_: Throwable) { "" }
        val enabled = readAddonEnabled(context, keyId, false)
        val scopeMode = readScopeMode(context, keyId)
        val customTargets = readCustomTargets(context, keyId)
        out.add(XposedModuleUiModel(
            keyId = keyId, id = pkg, packageName = pkg, label = label,
            apkPath = info.sourceDir, versionName = version, minApi = minApi,
            scopes = scopes, isLegacy = legacyMeta && !modern,
            enabled = enabled, scopeMode = scopeMode, customTargets = customTargets
        ))
    }
    return out.sortedBy { it.label.lowercase() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun XposedModulesSection(
    modules: List<XposedModuleUiModel>,
    isLoading: Boolean,
    onToggleModule: (String, Boolean) -> Unit,
    onScopeSaved: () -> Unit
) {
    val context = LocalContext.current
    var scopeModule by remember { mutableStateOf<XposedModuleUiModel?>(null) }
    var showAppPicker by remember { mutableStateOf(false) }
    var pickerSelection by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pickerMode by remember { mutableIntStateOf(1) }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxWidth().padding(32.dp),
            contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp))
        }
        return
    }

    SectionHeader(stringResource(R.string.addon_tab_modules))

    if (modules.isEmpty()) {
        EmptySectionCard(
            title = stringResource(R.string.addon_modules_empty_title),
            description = stringResource(R.string.addon_modules_empty_desc)
        )
        return
    }

    for (mod in modules) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(mod.label, style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold)
                        Text(mod.packageName, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (mod.versionName.isNotEmpty() || mod.minApi > 0) {
                            Text(
                                "v${mod.versionName}" + (if (mod.minApi > 0) " · API ${mod.minApi}" else "") +
                                    (if (mod.isLegacy) " · legacy" else ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                    Switch(checked = mod.enabled,
                        onCheckedChange = { onToggleModule(mod.id, it) },
                        modifier = Modifier.padding(start = 12.dp))
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val targetsLabel = when (mod.scopeMode) {
                        1 -> mod.customTargets.joinToString(", ").ifEmpty { "—" }
                        2 -> (mod.scopes + mod.customTargets).joinToString(", ").ifEmpty { "—" }
                        else -> mod.scopes.joinToString(", ").ifEmpty { "—" }
                    }
                    Text(targetsLabel, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        scopeModule = mod
                        pickerSelection = mod.customTargets
                        pickerMode = mod.scopeMode
                    }) {
                        Text(stringResource(R.string.addon_select_apps))
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    val target = scopeModule
    if (target != null) {
        AlertDialog(
            onDismissRequest = { scopeModule = null },
            title = { Text(target.label) },
            text = {
                Column {
                    val modes = listOf(0 to "Manifest", 1 to "Custom", 2 to "Merge")
                    for ((v, label) in modes) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { pickerMode = v }) {
                            RadioButton(selected = pickerMode == v,
                                onClick = { pickerMode = v })
                            Text(label)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(onClick = { showAppPicker = true }) {
                        Text(stringResource(R.string.addon_select_apps))
                    }
                    if (pickerSelection.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(pickerSelection.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    writeScopeMode(context, target.keyId, pickerMode)
                    writeCustomTargets(context, target.keyId, pickerSelection)
                    scopeModule = null
                    onScopeSaved()
                }) { Text(stringResource(R.string.addon_btn_save)) }
            },
            dismissButton = {
                TextButton(onClick = { scopeModule = null }) {
                    Text(stringResource(R.string.addon_btn_cancel))
                }
            }
        )
    }

    if (showAppPicker && target != null) {
        AppPickerDialog(
            defaultTargets = target.scopes,
            selectedPackages = pickerSelection,
            onDismiss = { showAppPicker = false },
            onConfirm = { pkgs ->
                pickerSelection = pkgs
                showAppPicker = false
            }
        )
    }
}