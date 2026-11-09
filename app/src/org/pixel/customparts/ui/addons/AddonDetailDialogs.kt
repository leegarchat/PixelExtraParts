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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddonDetailDialog(
    addon: AddonUiModel,
    onDismiss: () -> Unit,
    onSave: (AddonUiModel) -> Unit
) {
    var enabled by remember { mutableStateOf(addon.enabled) }
    var scopeMode by remember { mutableIntStateOf(addon.scopeMode) }
    var customTargets by remember { mutableStateOf(addon.customTargets) }
    var showAppPicker by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = true)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Extension, null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            addon.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${addon.author} • v${addon.version}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // ID and entryClass info
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "ID: ",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            addon.id,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (addon.entryClass.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Class: ",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                addon.entryClass,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                if (addon.description.isNotEmpty()) {
                    Text(
                        addon.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                // Scope mode
                Text(
                    stringResource(R.string.addon_target_scope),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))

                val scopeLabels = listOf(
                    stringResource(R.string.addon_scope_default),
                    stringResource(R.string.addon_scope_custom),
                    stringResource(R.string.addon_scope_merge)
                )
                scopeLabels.forEachIndexed { index, label ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { scopeMode = index }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = scopeMode == index,
                            onClick = { scopeMode = index }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                // Default targets (read-only)
                if (addon.defaultTargets.isNotEmpty() && (scopeMode == 0 || scopeMode == 2)) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.addon_targets_default),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    for (pkg in addon.defaultTargets) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.Apps, null,
                                Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                pkg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.weight(1f))
                            if (pkg in BUILTIN_WHITELIST) {
                                Text(
                                    stringResource(R.string.addon_builtin_badge),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // Custom targets — "Select apps" button + chips
                if (scopeMode == 1 || scopeMode == 2) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.addon_targets_custom),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (customTargets.isNotEmpty()) {
                        Text(
                            stringResource(R.string.addon_apps_selected, customTargets.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(
                        onClick = { showAppPicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Rounded.Checklist, null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.addon_select_apps))
                    }

                    // Chips for selected custom targets
                    if (customTargets.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            for (pkg in customTargets.sorted()) {
                                InputChip(
                                    selected = true,
                                    onClick = { customTargets = customTargets - pkg },
                                    label = {
                                        Text(
                                            pkg.substringAfterLast("."),
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1
                                        )
                                    },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Rounded.Close, null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    },
                                    modifier = Modifier.height(28.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.addon_btn_cancel)) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        onSave(
                            addon.copy(
                                enabled = enabled,
                                scopeMode = scopeMode,
                                customTargets = customTargets
                            )
                        )
                    }) { Text(stringResource(R.string.addon_btn_save)) }
                }
            }
        }
    }

    // Full-screen app picker dialog (LSPosed-style)
    if (showAppPicker) {
        AppPickerDialog(
            defaultTargets = addon.defaultTargets,
            selectedPackages = customTargets,
            onDismiss = { showAppPicker = false },
            onConfirm = { selected ->
                customTargets = selected
                showAppPicker = false
            }
        )
    }
}

// =====================================================================
// Full-screen App Picker Dialog (LSPosed-style)
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppPickerDialog(
    defaultTargets: Set<String>,
    selectedPackages: Set<String>,
    launchableOnlyDefault: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    val context = LocalContext.current

    var allApps by remember { mutableStateOf<List<AppInfoItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf(selectedPackages) }
    var searchQuery by remember { mutableStateOf("") }
    var showSystemApps by remember { mutableStateOf(false) }
    var launchableOnly by remember { mutableStateOf(launchableOnlyDefault) }
    val initiallyPinnedPackages = remember(selectedPackages, defaultTargets) { selectedPackages + defaultTargets }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            allApps = loadInstalledApps(context.packageManager)
        }
        isLoading = false
    }

    val filteredApps = remember(allApps, searchQuery, showSystemApps, launchableOnly, selected, defaultTargets, initiallyPinnedPackages) {
        val query = searchQuery.trim().lowercase()
        allApps
            .filter { app ->
                if (launchableOnly && !app.isLaunchable &&
                    app.packageName !in selected &&
                    app.packageName !in defaultTargets
                ) return@filter false
                if (!showSystemApps && app.isSystem &&
                    app.packageName !in selected &&
                    app.packageName !in defaultTargets
                ) return@filter false
                if (query.isNotEmpty()) {
                    val matchesLabel = app.label.lowercase().contains(query)
                    val matchesPackage = app.packageName.lowercase().contains(query)
                    return@filter matchesLabel || matchesPackage
                }
                true
            }
            .sortedWith(
                compareByDescending<AppInfoItem> {
                    it.packageName in initiallyPinnedPackages
                }.thenBy { it.label.lowercase() }
            )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.btn_close))
                        }
                    },
                    title = {
                        Column {
                            Text(
                                stringResource(R.string.addon_select_apps),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                stringResource(R.string.addon_selected_count, selected.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    )
                )
            },
            bottomBar = {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.addon_btn_cancel)) }
                        Spacer(Modifier.width(12.dp))
                        Button(onClick = { onConfirm(selected) }) {
                            Text(stringResource(R.string.addon_confirm_count, selected.size))
                        }
                    }
                }
            }
        ) { padding ->
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // System apps filter
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showSystemApps = !showSystemApps },
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.addon_show_system),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1
                                    )
                                    Text(
                                        stringResource(R.string.addon_show_system_desc),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                                Switch(
                                    checked = showSystemApps,
                                    onCheckedChange = { showSystemApps = it },
                                    modifier = Modifier.padding(start = 12.dp)
                                )
                            }
                        }
                    }

                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { launchableOnly = !launchableOnly },
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.addon_launchable_only),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1
                                    )
                                    Text(
                                        stringResource(R.string.addon_launchable_only_desc),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                                Switch(
                                    checked = launchableOnly,
                                    onCheckedChange = { launchableOnly = it },
                                    modifier = Modifier.padding(start = 12.dp)
                                )
                            }
                        }
                    }

                    // Search field
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Rounded.Search, null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            stringResource(R.string.addon_search_apps),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                    androidx.compose.foundation.text.BasicTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(30.dp)) {
                                        Icon(Icons.Rounded.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }

                    // App list
                    items(items = filteredApps, key = { it.packageName }) { app ->
                        val isDefault = app.packageName in defaultTargets
                        val isChecked = app.packageName in selected

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .clickable {
                                    if (!isDefault) {
                                        selected = if (isChecked) selected - app.packageName
                                        else selected + app.packageName
                                    }
                                },
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isChecked || isDefault) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (app.icon != null) {
                                    val bitmap = remember(app.packageName) {
                                        try { app.icon.toBitmap(width = 80, height = 80).asImageBitmap() }
                                        catch (_: Throwable) { null }
                                    }
                                    if (bitmap != null) {
                                        Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
                                    } else {
                                        DefaultAppIcon()
                                    }
                                } else {
                                    DefaultAppIcon()
                                }

                                Spacer(Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            app.label,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (isDefault) {
                                            Spacer(Modifier.width(6.dp))
                                            Surface(
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    stringResource(R.string.addon_default_target_badge),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        if (app.isSystem) {
                                            Spacer(Modifier.width(6.dp))
                                            Surface(
                                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    stringResource(R.string.launcher_hidden_apps_system_badge),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        app.packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Checkbox(
                                    checked = isChecked || isDefault,
                                    onCheckedChange = null,
                                    enabled = !isDefault,
                                    modifier = Modifier.padding(start = 12.dp)
                                )
                            }
                        }
                    }

                    if (filteredApps.isEmpty() && !isLoading) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Text(
                                    if (searchQuery.isNotEmpty()) stringResource(R.string.addon_no_apps_match, searchQuery)
                                    else stringResource(R.string.addon_no_apps_available),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DefaultAppIcon() {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Rounded.Android, null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

class AddonAppPickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val addonId = intent.getStringExtra(EXTRA_PICKER_ADDON_ID).orEmpty()
        val addonJar = intent.getStringExtra(EXTRA_PICKER_ADDON_JAR).orEmpty()
        val isSystemAddon = intent.getBooleanExtra(EXTRA_PICKER_IS_SYSTEM, false)
        val settingKey = intent.getStringExtra(EXTRA_PICKER_KEY).orEmpty()
        val settingTitle = intent.getStringExtra(EXTRA_PICKER_TITLE).orEmpty().ifBlank { settingKey }
        val provider = runCatching {
            SettingProvider.valueOf(intent.getStringExtra(EXTRA_PICKER_PROVIDER).orEmpty())
        }.getOrDefault(SettingProvider.GLOBAL)
        val storage = runCatching {
            SettingStorage.valueOf(intent.getStringExtra(EXTRA_PICKER_STORAGE).orEmpty())
        }.getOrDefault(SettingStorage.ADDON_FILE)
        val launchableOnly = intent.getBooleanExtra(EXTRA_PICKER_LAUNCHABLE_ONLY, false)
        val setting = AddonSettingDef(
            key = settingKey,
            title = settingTitle,
            type = SettingType.APP_LIST,
            provider = provider,
            storage = storage
        )
        val addon = AddonUiModel(
            id = addonId,
            entryClass = "",
            name = settingTitle,
            author = "",
            description = "",
            version = "",
            jarPath = addonJar,
            defaultTargets = emptySet(),
            enabled = true,
            scopeMode = 0,
            customTargets = emptySet(),
            isSystem = isSystemAddon
        )

        setContent {
            val context = LocalContext.current
            val colorScheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            MaterialTheme(colorScheme = colorScheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer) {
                    AppPickerDialog(
                        defaultTargets = emptySet(),
                        selectedPackages = readStoredArray(context, setting, addon.id, addon.jarPath, addon.isSystem).toSet(),
                        launchableOnlyDefault = launchableOnly,
                        onDismiss = { finish() },
                        onConfirm = { packages ->
                            writeStoredArray(context, setting, addon.id, addon.jarPath, addon.isSystem, packages)
                            setResult(RESULT_OK)
                            finish()
                        }
                    )
                }
            }
        }
    }
}

// =====================================================================
// Xposed APK modules tab (LSPosed/Vector-format modules)
// =====================================================================
