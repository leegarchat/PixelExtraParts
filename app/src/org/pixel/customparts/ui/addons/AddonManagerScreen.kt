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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AddonManagerSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var addons by remember { mutableStateOf<List<AddonUiModel>>(emptyList()) }
    var xposedModules by remember { mutableStateOf<List<XposedModuleUiModel>>(emptyList()) }
    var modulesTab by rememberSaveable { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedAddon by remember { mutableStateOf<AddonUiModel?>(null) }
    var showDeleteDialog by remember { mutableStateOf<AddonUiModel?>(null) }
    var safeModeActive by remember { mutableStateOf(isSafeModeActive(context)) }
    var highlightedAddonId by remember { mutableStateOf<String?>(null) }
    val bringIntoViewRequesters = remember { mutableMapOf<String, BringIntoViewRequester>() }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            addons = scanAddons(context)
            syncWhitelist(context, addons)
            xposedModules = scanInstalledModules(context)
        }
        isLoading = false
    }

    fun refreshAddons() {
        scope.launch {
            isLoading = true
            withContext(Dispatchers.IO) {
                addons = scanAddons(context)
                syncWhitelist(context, addons)
                xposedModules = scanInstalledModules(context)
            }
            isLoading = false
        }
    }

    val importSuccessMsg = stringResource(R.string.addon_import_success)
    val importFailedMsg = stringResource(R.string.addon_import_failed)

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val success = withContext(Dispatchers.IO) { importAddonJar(context, uri) }
                Toast.makeText(
                    context,
                    if (success) importSuccessMsg else importFailedMsg,
                    Toast.LENGTH_SHORT
                ).show()
                if (success) refreshAddons()
            }
        }
    }

    val systemAddons = addons.filter { it.isSystem }
    val userAddons = addons.filter { !it.isSystem }
    val activeCount = addons.count { it.enabled }

    Column(modifier = modifier.fillMaxWidth()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.addon_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    stringResource(R.string.addon_count, userAddons.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    stringResource(R.string.addon_active_count, activeCount, addons.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = { refreshAddons() }) {
                Icon(Icons.Rounded.Refresh, stringResource(R.string.menu_refresh), tint = MaterialTheme.colorScheme.primary)
            }
        }

        // Import button card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { filePickerLauncher.launch("application/java-archive") }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Add, null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.addon_import),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        stringResource(R.string.addon_import_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
                Icon(
                    Icons.Rounded.FileOpen, null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // === Tabs: JAR addons / APK modules ===
        // TEMPORARY: APK-modules tab hidden behind ApkModulesGate (code kept).
        PrimaryTabRow(selectedTabIndex = modulesTab) {
            Tab(selected = modulesTab == 0, onClick = { modulesTab = 0 },
                text = { Text(stringResource(R.string.addon_tab_jars)) })
            if (ApkModulesGate.ENABLED) {
                Tab(selected = modulesTab == 1, onClick = { modulesTab = 1 },
                    text = { Text(stringResource(R.string.addon_tab_modules)) })
            }
        }

        Spacer(Modifier.height(12.dp))

        if (modulesTab == 0) {
        // === Active apps section ===
        if (!isLoading) {
            val activeAppsMap = remember(addons) { buildActiveAppsMap(addons) }
            if (activeAppsMap.isNotEmpty()) {
                ActiveAppsSection(
                    activeAppsMap = activeAppsMap,
                    onAddonClick = { addonId ->
                        scope.launch {
                            highlightedAddonId = addonId
                            bringIntoViewRequesters[addonId]?.bringIntoView()
                            delay(1500)
                            highlightedAddonId = null
                        }
                    },
                    onDisableAddon = { addonId ->
                        val addon = addons.find { it.id == addonId } ?: return@ActiveAppsSection
                        writeAddonEnabled(context, addonId, false)
                        val updatedList = addons.map { if (it.id == addonId) it.copy(enabled = false) else it }
                        scope.launch(Dispatchers.IO) {
                            val otherTargets = updatedList
                                .filter { it.id != addonId && it.enabled }
                                .flatMap { getEffectiveTargets(it) }
                                .toSet()
                            for (pkg in getEffectiveTargets(addon)) {
                                if (pkg !in BUILTIN_WHITELIST && pkg !in otherTargets) {
                                    removeFromWhitelist(context, pkg)
                                }
                            }
                        }
                        addons = updatedList
                    }
                )
                Spacer(Modifier.height(12.dp))
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
        }

        if (!isLoading) {
            // === Safe mode banner ===
            if (safeModeActive) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Warning, null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.addon_safe_mode_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.addon_safe_mode_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                exitAddonSafeMode(context)
                                safeModeActive = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) {
                            Text(stringResource(R.string.addon_safe_mode_exit))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // === System addons section ===
            SectionHeader(stringResource(R.string.addon_section_system))

            if (systemAddons.isEmpty()) {
                EmptySectionCard(
                    title = stringResource(R.string.addon_system_empty_title),
                    description = stringResource(R.string.addon_system_empty_desc)
                )
            } else {
                for (addon in systemAddons) {
                    val requester = remember { BringIntoViewRequester() }
                    bringIntoViewRequesters[addon.id] = requester
                    AddonCard(
                        addon = addon,
                        isSystem = true,
                        isHighlighted = highlightedAddonId == addon.id,
                        bringIntoViewRequester = requester,
                        onToggle = { enabled ->
                            writeAddonEnabled(context, addon.id, enabled)
                            val updatedList = addons.map { if (it.id == addon.id) it.copy(enabled = enabled) else it }
                            scope.launch(Dispatchers.IO) {
                                if (enabled) {
                                    for (pkg in getEffectiveTargets(addon)) {
                                        if (pkg !in BUILTIN_WHITELIST) addToWhitelist(context, pkg)
                                    }
                                } else {
                                    val otherTargets = updatedList
                                        .filter { it.id != addon.id && it.enabled }
                                        .flatMap { getEffectiveTargets(it) }
                                        .toSet()
                                    for (pkg in getEffectiveTargets(addon)) {
                                        if (pkg !in BUILTIN_WHITELIST && pkg !in otherTargets) {
                                            removeFromWhitelist(context, pkg)
                                        }
                                    }
                                }
                            }
                            addons = updatedList
                        },
                        onClick = { selectedAddon = addon },
                        onDelete = { if (addon.hasDataOverride) showDeleteDialog = addon },
                        onRefresh = ::refreshAddons
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            Spacer(Modifier.height(16.dp))

            // === User addons section ===
            SectionHeader(stringResource(R.string.addon_section_user))

            if (userAddons.isEmpty()) {
                EmptySectionCard(
                    title = stringResource(R.string.addon_user_empty_title),
                    description = stringResource(R.string.addon_user_empty_desc)
                )
            } else {
                for (addon in userAddons) {
                    val requester = remember { BringIntoViewRequester() }
                    bringIntoViewRequesters[addon.id] = requester
                    AddonCard(
                        addon = addon,
                        isSystem = false,
                        isHighlighted = highlightedAddonId == addon.id,
                        bringIntoViewRequester = requester,
                        onToggle = { enabled ->
                            writeAddonEnabled(context, addon.id, enabled)
                            val updatedList = addons.map { if (it.id == addon.id) it.copy(enabled = enabled) else it }
                            scope.launch(Dispatchers.IO) {
                                if (enabled) {
                                    for (pkg in getEffectiveTargets(addon)) {
                                        if (pkg !in BUILTIN_WHITELIST) addToWhitelist(context, pkg)
                                    }
                                } else {
                                    val otherTargets = updatedList
                                        .filter { it.id != addon.id && it.enabled }
                                        .flatMap { getEffectiveTargets(it) }
                                        .toSet()
                                    for (pkg in getEffectiveTargets(addon)) {
                                        if (pkg !in BUILTIN_WHITELIST && pkg !in otherTargets) {
                                            removeFromWhitelist(context, pkg)
                                        }
                                    }
                                }
                            }
                            addons = updatedList
                        },
                        onClick = { selectedAddon = addon },
                        onDelete = { showDeleteDialog = addon },
                        onRefresh = ::refreshAddons
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    // Detail dialog
    selectedAddon?.let { addon ->
        AddonDetailDialog(
            addon = addon,
            onDismiss = { selectedAddon = null },
            onSave = { updatedAddon ->
                val oldTargets = getEffectiveTargets(addon)
                val newTargets = getEffectiveTargets(updatedAddon)

                writeAddonEnabled(context, updatedAddon.id, updatedAddon.enabled)
                writeScopeMode(context, updatedAddon.id, updatedAddon.scopeMode)
                writeCustomTargets(context, updatedAddon.id, updatedAddon.customTargets)

                val updatedAddons = addons.map { if (it.id == updatedAddon.id) updatedAddon else it }

                scope.launch(Dispatchers.IO) {
                    // Add new targets to whitelist
                    for (pkg in newTargets) {
                        if (pkg !in BUILTIN_WHITELIST) addToWhitelist(context, pkg)
                    }
                    // Remove targets that were dropped — but only if no other addon uses them
                    val removedTargets = oldTargets - newTargets
                    if (removedTargets.isNotEmpty()) {
                        val otherAddonsTargets = updatedAddons
                            .filter { it.id != updatedAddon.id && it.enabled }
                            .flatMap { getEffectiveTargets(it) }
                            .toSet()
                        for (pkg in removedTargets) {
                            if (pkg !in BUILTIN_WHITELIST && pkg !in otherAddonsTargets) {
                                removeFromWhitelist(context, pkg)
                            }
                        }
                    }
                }
                addons = updatedAddons
                selectedAddon = null
            }
        )
    }

    // Delete dialog
    showDeleteDialog?.let { addon ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            icon = { Icon(Icons.Rounded.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.addon_delete_title)) },
            text = {
                Text(
                    stringResource(
                        if (addon.hasDataOverride) R.string.addon_delete_data_update_confirm else R.string.addon_delete_confirm,
                        addon.name
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { deleteAddon(context, addon, addons) }
                            showDeleteDialog = null
                            refreshAddons()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text(stringResource(R.string.addon_btn_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text(stringResource(R.string.addon_btn_cancel)) }
            }
        )
    }
    }
    // TEMPORARY: APK-modules tab content disabled behind ApkModulesGate.
    if (modulesTab == 1 && ApkModulesGate.ENABLED) {
        // === APK modules tab ===
        XposedModulesSection(
            modules = xposedModules,
            isLoading = isLoading,
            onToggleModule = { id, enabled ->
                val mod = xposedModules.find { it.id == id } ?: return@XposedModulesSection
                writeAddonEnabled(context, mod.keyId, enabled)
                scope.launch(Dispatchers.IO) {
                    if (enabled) {
                        // Self-injection: module process must be injected
                        // for the module to see itself as active.
                        addToWhitelist(context, mod.packageName)
                        addModuleToRegistry(context, mod.apkPath)
                    } else {
                        removeModuleFromRegistry(context, mod.apkPath)
                        if (mod.packageName !in BUILTIN_WHITELIST) {
                            removeFromWhitelist(context, mod.packageName)
                        }
                    }
                }
                xposedModules = xposedModules.map {
                    if (it.id == id) it.copy(enabled = enabled) else it
                }
                scope.launch(Dispatchers.IO) {
                    // Re-sync injection whitelist so scope targets of modules
                    // (and removals) take effect without reopening the manager.
                    try {
                        syncWhitelist(context, emptyList())
                    } catch (_: Throwable) {}
                }
            },
            onScopeSaved = {
                scope.launch(Dispatchers.IO) {
                    xposedModules = scanInstalledModules(context)
                }
            }
        )
    }
}

// =====================================================================
// Addon Card — with expandable settings panel
// =====================================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AddonCard(
    addon: AddonUiModel,
    isSystem: Boolean = false,
    isHighlighted: Boolean = false,
    bringIntoViewRequester: BringIntoViewRequester? = null,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val effectiveTargets = getEffectiveTargets(addon)
    val contentAlpha = if (addon.enabled) 1f else 0.5f
    var settingsExpanded by remember { mutableStateOf(false) }
    val onHeaderClick = {
        settingsExpanded = !settingsExpanded
    }

    // Pulsing white highlight like Android system settings
    var highlightVisible by remember { mutableStateOf(false) }
    LaunchedEffect(isHighlighted) {
        if (isHighlighted) {
            highlightVisible = true
        } else {
            highlightVisible = false
        }
    }
    val infiniteTransition = rememberInfiniteTransition(label = "highlightPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 300),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Parse custom card color
    val cardContainerColor = remember(addon.cardColor) {
        if (addon.cardColor.isNotEmpty()) {
            try { Color(android.graphics.Color.parseColor(addon.cardColor)) }
            catch (_: Throwable) { null }
        } else null
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = cardContainerColor ?: MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (bringIntoViewRequester != null) Modifier.bringIntoViewRequester(bringIntoViewRequester) else Modifier)
    ) {
        // Composable to render background layers
        @Composable
        fun BackgroundLayers(modifier: Modifier = Modifier) {
            if (addon.backgroundBitmap != null) {
                val bgImageBitmap = remember(addon.id) { addon.backgroundBitmap.asImageBitmap() }
                val bgAlphaFloat = (addon.backgroundAlpha / 100f).coerceIn(0f, 1f) *
                    if (addon.enabled) 1f else 0.4f
                Image(
                    bitmap = bgImageBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = modifier
                        .alpha(bgAlphaFloat)
                        .then(
                            if (addon.backgroundBlur && addon.backgroundBlurRadius > 0)
                                Modifier.blur(addon.backgroundBlurRadius.dp)
                            else Modifier
                        )
                )
                if (addon.backgroundMode == "gradient" && addon.backgroundGradientSteps.size >= 2) {
                    val surfaceColor = cardContainerColor ?: MaterialTheme.colorScheme.surface
                    val gradientColors = addon.backgroundGradientSteps.map { opacity ->
                        surfaceColor.copy(alpha = (opacity / 100f).coerceIn(0f, 1f))
                    }
                    Box(modifier = modifier.background(Brush.verticalGradient(colors = gradientColors)))
                }
            }
        }

        if (addon.backgroundScope == "header") {
            // Background covers only the header, not the expandable settings
            Box {
                Column {
                    Box {
                        BackgroundLayers(Modifier.matchParentSize())
                        Column(modifier = Modifier.padding(16.dp)) {
                            AddonCardHeader(addon, contentAlpha, effectiveTargets, settingsExpanded,
                                onToggle, onClick, onDelete, isSystem, onHeaderClick)
                        }
                    }
                    // Settings panel outside background
                    AddonCardSettings(addon, settingsExpanded, onRefresh)
                }
                // Highlight overlay on top of everything
                if (highlightVisible) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.White.copy(alpha = pulseAlpha))
                    )
                }
            }
        } else {
            // "full" — background extends to the entire card including settings
            Box {
                BackgroundLayers(Modifier.matchParentSize())
                Column(modifier = Modifier.padding(16.dp)) {
                    AddonCardHeader(addon, contentAlpha, effectiveTargets, settingsExpanded,
                        onToggle, onClick, onDelete, isSystem, onHeaderClick)
                    AddonCardSettings(addon, settingsExpanded, onRefresh)
                }
                // Highlight overlay on top of everything
                if (highlightVisible) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.White.copy(alpha = pulseAlpha))
                    )
                }
            }
        }
    }
}

@Composable
private fun AddonCardHeader(
    addon: AddonUiModel,
    contentAlpha: Float,
    effectiveTargets: Set<String>,
    settingsExpanded: Boolean,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    isSystem: Boolean,
    onHeaderClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onHeaderClick() }
    ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Custom icon or default Extension icon
                if (addon.iconBitmap != null) {
                    val iconImageBitmap = remember(addon.id) { addon.iconBitmap.asImageBitmap() }
                    Image(
                        bitmap = iconImageBitmap,
                        contentDescription = null,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (addon.enabled) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Extension, null,
                        modifier = Modifier.size(20.dp),
                        tint = if (addon.enabled) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                }

                Spacer(Modifier.width(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .alpha(contentAlpha)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            addon.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "v${addon.version}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    Text(
                        addon.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        addon.id,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Switch(
                    checked = addon.enabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clickable(onClick = {}) // consume click to prevent header toggle
                )
            }

            if (addon.description.isNotEmpty()) {
                Text(
                    addon.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .alpha(contentAlpha)
                )
            }

            if (effectiveTargets.isNotEmpty() && !addon.settingsOnly) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(contentAlpha),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Outlined.FilterAlt, null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Text(
                        effectiveTargets.joinToString(", ") { it.substringAfterLast(".") },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (addon.settingsOnly) {
                    // Settings-only addon: show info chip instead of scope
                    SuggestionChip(
                        onClick = onHeaderClick,
                        label = { Text("Settings UI", style = MaterialTheme.typography.labelSmall) },
                        icon = {
                            Icon(
                                if (settingsExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                null,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        modifier = Modifier.height(26.dp)
                    )
                } else {
                val scopeLabel = when (addon.scopeMode) {
                    0 -> stringResource(R.string.addon_scope_label_default)
                    1 -> stringResource(R.string.addon_scope_label_custom)
                    2 -> stringResource(R.string.addon_scope_label_merge)
                    else -> stringResource(R.string.addon_scope_label_default)
                }

                SuggestionChip(
                    onClick = onClick,
                    label = { Text(scopeLabel, style = MaterialTheme.typography.labelSmall) },
                    icon = { Icon(Icons.Outlined.Tune, null, modifier = Modifier.size(14.dp)) },
                    modifier = Modifier.height(26.dp)
                )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    if (!isSystem || addon.hasDataOverride) {
                        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Outlined.Delete, stringResource(R.string.addon_btn_remove),
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
    }
}

@Composable
private fun AddonCardSettings(
    addon: AddonUiModel,
    settingsExpanded: Boolean,
    onRefresh: () -> Unit
) {
            val context = LocalContext.current
            val importFailedMsg = stringResource(R.string.addon_import_failed)
            var settingsRevision by remember(addon.id) { mutableIntStateOf(0) }
            val allSettings = remember(addon.settings) { flattenSettings(addon.settings) }
            fun onSettingChanged(setting: AddonSettingDef, booleanValue: Boolean?) {
                if (booleanValue == true) {
                    applyExclusiveSettingLogic(context, addon, setting, allSettings)
                }
                settingsRevision++
            }

            val settingsImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) {
                    val ok = importAddonSettings(context, uri, addon)
                    if (!ok) Toast.makeText(context, importFailedMsg, Toast.LENGTH_SHORT).show()
                    settingsRevision++
                }
            }
            val settingsExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
                if (uri != null) exportAddonSettings(context, uri, addon)
            }

            val hasInlineSettings = addon.settings.isNotEmpty() || addon.updateUrl.isNotBlank()
            val hasMainEntries = addon.mainEntries.isNotEmpty()

            // ---- Panel: no inline settings but has main[] activity pages ----
            AnimatedVisibility(
                visible = settingsExpanded && !hasInlineSettings && hasMainEntries,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .padding(
                            top = 8.dp,
                            start = if (addon.backgroundScope == "header") 16.dp else 0.dp,
                            end = if (addon.backgroundScope == "header") 16.dp else 0.dp,
                            bottom = if (addon.backgroundScope == "header") 16.dp else 0.dp
                        )
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    // Info message
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.addon_no_inline_settings_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                    // Link buttons for each top-level main entry
                    val accent = parseOptionalColor(addon.accentColor) ?: MaterialTheme.colorScheme.primary
                    addon.mainEntries.forEach { entry ->
                        OutlinedButton(
                            onClick = {
                                AddonPageActivity.start(
                                    context,
                                    addonId = addon.id,
                                    pageId = entry.leafId,
                                    title = entry.title,
                                    includeTargetActivityEntries = true
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                accent.copy(alpha = 0.5f)
                            )
                        ) {
                            AddonMainEntryIcon(
                                entry = entry,
                                containerSize = 28.dp,
                                fallbackTint = accent,
                                fallbackContainer = accent.copy(alpha = 0.12f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                entry.title,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium.withAddonTextSize(entry.titleSizeSp),
                                color = accent
                            )
                            Icon(
                                Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = accent.copy(alpha = 0.7f)
                            )
                        }
                    }

                    AddonSettingsActions(
                        addon = addon,
                        onImport = { settingsImportLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                        onExport = { settingsExportLauncher.launch("${sanitizeFileSegment(addon.id)}_settings.json") },
                        onRefresh = onRefresh
                    )
                }
            }

            // ---- Panel: manifest-only card without runtime entry/settings ----
            AnimatedVisibility(
                visible = settingsExpanded && !hasInlineSettings && !hasMainEntries,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .padding(
                            top = 8.dp,
                            start = if (addon.backgroundScope == "header") 16.dp else 0.dp,
                            end = if (addon.backgroundScope == "header") 16.dp else 0.dp,
                            bottom = if (addon.backgroundScope == "header") 16.dp else 0.dp
                        )
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.addon_no_inline_settings_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }

                    AddonSettingsActions(
                        addon = addon,
                        onImport = { settingsImportLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                        onExport = { settingsExportLauncher.launch("${sanitizeFileSegment(addon.id)}_settings.json") },
                        onRefresh = onRefresh
                    )
                }
            }

            // ---- Expandable settings panel (normal inline settings) ----
            AnimatedVisibility(
                visible = settingsExpanded && hasInlineSettings,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier
                    .padding(top = 8.dp, start = if (addon.backgroundScope == "header") 16.dp else 0.dp,
                             end = if (addon.backgroundScope == "header") 16.dp else 0.dp,
                             bottom = if (addon.backgroundScope == "header") 16.dp else 0.dp)
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    val settingCategories = remember(addon.settings) { buildAddonSettingCategories(addon.settings) }
                    settingCategories.forEachIndexed { categoryIndex, category ->
                        val categoryTitle = category.title.ifBlank {
                            if (settingCategories.size == 1) stringResource(R.string.addon_settings_title) else ""
                        }
                        if (categoryTitle.isNotBlank()) {
                            Text(
                                categoryTitle,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = parseOptionalColor(addon.accentColor) ?: MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        if (category.description.isNotBlank()) {
                            Text(
                                category.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        for (setting in category.settings) {
                            key(setting.key) {
                                val settingModifier = if (setting.type == SettingType.GROUP && setting.groupMode != GroupMode.INLINE) {
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp)
                                } else {
                                    Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                                }
                                AddonSettingControl(
                                    setting = setting,
                                    addon = addon,
                                    modifier = settingModifier,
                                    allSettings = allSettings,
                                    dependencyRevision = settingsRevision,
                                    onSettingChanged = ::onSettingChanged
                                )
                            }
                        }
                        if (categoryIndex < settingCategories.lastIndex) {
                            Spacer(Modifier.height(8.dp))
                        }
                    }

                    AddonSettingsActions(
                        addon = addon,
                        onImport = { settingsImportLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                        onExport = { settingsExportLauncher.launch("${sanitizeFileSegment(addon.id)}_settings.json") },
                        onRefresh = onRefresh
                    )
                }
            }
}

// =====================================================================
// Single setting control — dispatches by type
// =====================================================================
