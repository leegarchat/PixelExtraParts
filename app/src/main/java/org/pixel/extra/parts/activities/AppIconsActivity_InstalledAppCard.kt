package org.pixel.extra.parts.activities

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pixel.extra.parts.R
import org.pixel.extra.parts.SettingsKeys
import org.pixel.extra.parts.dynamicDarkColorScheme
import org.pixel.extra.parts.dynamicLightColorScheme
import org.pixel.extra.parts.icons.AppIconCandidate
import org.pixel.extra.parts.icons.AppIconShapeOverrides
import org.pixel.extra.parts.icons.IconApplyMode
import org.pixel.extra.parts.icons.IconApplyProgress
import org.pixel.extra.parts.icons.IconApplyResult
import org.pixel.extra.parts.icons.IconDashboardState
import org.pixel.extra.parts.icons.IconMapSnapshot
import org.pixel.extra.parts.icons.IconPackApplyStatus
import org.pixel.extra.parts.icons.IconPackEntry
import org.pixel.extra.parts.icons.IconPackInfo
import org.pixel.extra.parts.icons.IconPackManager
import org.pixel.extra.parts.icons.IconPackPreviewItem
import org.pixel.extra.parts.icons.IconShapeAreaConfig
import org.pixel.extra.parts.icons.InstalledIconApp
import org.pixel.extra.parts.ui.legacy.REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING
import org.pixel.extra.parts.ui.legacy.ColorPickerDialog
import org.pixel.extra.parts.ui.legacy.RebootBubble
import org.pixel.extra.parts.ui.legacy.RebootBubbleMenuAction
import org.pixel.extra.parts.ui.legacy.SettingsGroupCard
import org.pixel.extra.parts.ui.legacy.SliderSettingFloat
import org.pixel.extra.parts.ui.TopBarBlurOverlay
import org.pixel.extra.parts.ui.legacy.performRebootSystem
import org.pixel.extra.parts.ui.recordLayer
import org.pixel.extra.parts.ui.rememberGraphicsLayerRecordingState
import org.pixel.extra.parts.utils.SettingsCompat
import java.util.Locale
import androidx.compose.ui.res.stringResource


@Composable
internal fun InstalledAppCard(
    app: InstalledIconApp,
    iconMap: IconMapSnapshot,
    iconPacks: List<IconPackInfo>,
    expanded: Boolean,
    busy: Boolean,
    onExpandChange: (Boolean) -> Unit,
    onRestore: () -> Unit,
    onApply: (AppIconCandidate) -> Unit,
    onImportCustomIcon: (Uri) -> Unit,
    onSaveShapeOverrides: (AppIconShapeOverrides?) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val previewSizePx = with(density) { 56.dp.roundToPx() }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImportCustomIcon(uri)
    }
    var loading by remember(app.packageName, iconMap) { mutableStateOf(false) }
    var candidates by remember(app.packageName, iconMap) { mutableStateOf<List<AppIconCandidate>>(emptyList()) }
    var appliedBitmap by remember(app.packageName, iconMap) { mutableStateOf<Bitmap?>(null) }
    var shapeOverrides by remember(app.packageName, iconMap) {
        mutableStateOf(iconMap.shapeOverrides[app.packageName] ?: app.shapeOverrides ?: AppIconShapeOverrides())
    }
    var chooserOpen by rememberSaveable(app.packageName) { mutableStateOf(false) }

    LaunchedEffect(expanded, app.packageName, iconMap) {
        if (!expanded) return@LaunchedEffect
        loading = true
        candidates = IconPackManager.loadCandidatesForApp(context, app.packageName)
        appliedBitmap = IconPackManager.loadAppliedIconBitmap(context, app.packageName, previewSizePx)
        loading = false
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onExpandChange(!expanded) },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DrawableIcon(drawable = app.icon, size = 42.dp, fallback = Icons.Rounded.Apps)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = app.appliedIconPackLabel?.let {
                            stringResource(R.string.app_icons_applied_from_format, it)
                        } ?: stringResource(R.string.app_icons_not_applied_to_app),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (app.appliedIconPackLabel != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.rotate(if (expanded) 180f else 0f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AppIconCurrentIconCard(app = app, appliedBitmap = appliedBitmap)

                    if (app.appliedIconFileName != null) {
                        OutlinedButton(
                            onClick = onRestore,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.app_icons_restore_default), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("image/*", "application/xml", "text/xml")) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Apps, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.app_icons_import_custom_icon), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    OutlinedButton(
                        onClick = { chooserOpen = true },
                        enabled = !busy && iconPacks.any { it.installed },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Search, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.app_icons_choose_from_pack), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    PerAppShapeSettingsCard(
                        overrides = shapeOverrides,
                        onOverridesChange = { shapeOverrides = it },
                        onSave = { onSaveShapeOverrides(shapeOverrides.normalizedOrNull()) },
                        onReset = {
                            shapeOverrides = AppIconShapeOverrides()
                            onSaveShapeOverrides(null)
                        }
                    )

                    Text(
                        text = stringResource(R.string.app_icons_available_icons_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )

                    when {
                        loading -> LoadingDialogContent()
                        candidates.isEmpty() -> AppIconsEmptyStateText(stringResource(R.string.app_icons_no_app_candidates))
                        else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val appliedSource = iconMap.sources[app.packageName]
                            candidates.forEach { candidate ->
                                val applied = appliedSource?.iconPackPackage == candidate.iconPackPackage &&
                                    appliedSource.drawableName == candidate.drawableName
                                AppIconCandidateRow(
                                    candidate = candidate,
                                    applied = applied,
                                    busy = busy,
                                    onApply = { onApply(candidate) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (chooserOpen) {
        AppIconChooserDialog(
            app = app,
            iconMap = iconMap,
            iconPacks = iconPacks.filter { it.installed },
            onDismiss = { chooserOpen = false },
            onApply = { item ->
                chooserOpen = false
                onApply(AppIconCandidate(item.iconPackPackage, item.iconPackLabel, item.drawableName))
            }
        )
    }
}

@Composable
internal fun AppIconCurrentIconCard(app: InstalledIconApp, appliedBitmap: Bitmap?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (appliedBitmap != null) {
                BitmapIcon(bitmap = appliedBitmap, size = 56.dp, fallback = Icons.Rounded.Apps)
            } else {
                DrawableIcon(drawable = app.icon, size = 56.dp, fallback = Icons.Rounded.Apps)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp)
            ) {
                Text(
                    text = stringResource(R.string.app_icons_current_icon_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = app.appliedIconPackLabel?.let {
                        stringResource(R.string.app_icons_applied_from_format, it)
                    } ?: stringResource(R.string.app_icons_not_applied_to_app),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun AppIconChooserDialog(
    app: InstalledIconApp,
    iconMap: IconMapSnapshot,
    iconPacks: List<IconPackInfo>,
    onDismiss: () -> Unit,
    onApply: (AppIconChooserItem) -> Unit
) {
    val context = LocalContext.current
    var loading by remember(app.packageName, iconPacks) { mutableStateOf(true) }
    var detectedItems by remember(app.packageName, iconPacks) { mutableStateOf<List<AppIconChooserItem>>(emptyList()) }
    var allItems by remember(app.packageName, iconPacks) { mutableStateOf<List<AppIconChooserItem>>(emptyList()) }
    var query by rememberSaveable(app.packageName) { mutableStateOf("") }

    LaunchedEffect(app.packageName, iconPacks) {
        loading = true
        val packLabels = iconPacks.associate { it.packageName to it.label }
        val detected = IconPackManager.loadCandidatesForApp(context, app.packageName)
            .map { candidate ->
                AppIconChooserItem(
                    iconPackPackage = candidate.iconPackPackage,
                    iconPackLabel = candidate.iconPackLabel,
                    drawableName = candidate.drawableName,
                    label = candidate.drawableName,
                    detected = true,
                    appPackageName = app.packageName
                )
            }
        val detectedIds = detected.map { it.iconPackPackage to it.drawableName }.toSet()
        val allPackItems = iconPacks.flatMap { pack ->
            IconPackManager.loadPreviewItems(context, pack.packageName).map { item ->
                AppIconChooserItem(
                    iconPackPackage = item.iconPackPackage,
                    iconPackLabel = packLabels[item.iconPackPackage] ?: pack.label,
                    drawableName = item.drawableName,
                    label = item.label,
                    detected = false,
                    appPackageName = item.appPackageName
                )
            }
        }.filterNot { it.iconPackPackage to it.drawableName in detectedIds }
        detectedItems = detected
        allItems = allPackItems
        loading = false
    }

    val filteredDetected = remember(detectedItems, query) {
        filterChooserItems(detectedItems, query)
    }
    val filteredAll = remember(allItems, query) {
        filterChooserItems(allItems, query)
    }
    val appliedSource = iconMap.sources[app.packageName]

    AppIconsDialog(
        title = stringResource(R.string.app_icons_choose_from_pack),
        subtitle = app.label,
        onDismiss = onDismiss,
        footer = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_close)) } }
    ) {
        AppIconsSearchField(
            query = query,
            hint = stringResource(R.string.app_icons_dialog_search_hint),
            onQueryChange = { query = it }
        )
        Spacer(Modifier.height(12.dp))
        when {
            loading -> LoadingDialogContent()
            filteredDetected.isEmpty() && filteredAll.isEmpty() -> AppIconsEmptyStateText(stringResource(R.string.app_icons_no_pack_icons))
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (filteredDetected.isNotEmpty()) {
                    item(key = "detected_header") {
                        AppIconChooserSectionTitle(stringResource(R.string.app_icons_detected_alternatives_title))
                    }
                    items(filteredDetected, key = { "detected:${it.iconPackPackage}:${it.drawableName}" }) { item ->
                        AppIconChooserRow(
                            item = item,
                            applied = appliedSource?.iconPackPackage == item.iconPackPackage &&
                                appliedSource.drawableName == item.drawableName,
                            onApply = { onApply(item) }
                        )
                    }
                }
                if (filteredAll.isNotEmpty()) {
                    item(key = "all_header") {
                        AppIconChooserSectionTitle(stringResource(R.string.app_icons_all_pack_icons_title))
                    }
                    items(filteredAll, key = { "all:${it.iconPackPackage}:${it.drawableName}" }) { item ->
                        AppIconChooserRow(
                            item = item,
                            applied = appliedSource?.iconPackPackage == item.iconPackPackage &&
                                appliedSource.drawableName == item.drawableName,
                            onApply = { onApply(item) }
                        )
                    }
                }
            }
        }
    }
}

internal fun filterChooserItems(items: List<AppIconChooserItem>, query: String): List<AppIconChooserItem> {
    val needle = query.trim().lowercase(Locale.getDefault())
    if (needle.isEmpty()) return items
    return items.filter { item ->
        item.label.lowercase(Locale.getDefault()).contains(needle) ||
            item.iconPackLabel.lowercase(Locale.getDefault()).contains(needle) ||
            item.drawableName.lowercase(Locale.US).contains(needle) ||
            item.appPackageName.orEmpty().lowercase(Locale.US).contains(needle)
    }.sortedWith(
        compareBy<AppIconChooserItem> { chooserSearchRank(it, needle) }
            .thenByDescending { it.detected }
            .thenBy { it.drawableName.lowercase(Locale.US) }
            .thenBy { it.iconPackLabel.lowercase(Locale.getDefault()) }
    )
}

internal fun chooserSearchRank(item: AppIconChooserItem, needle: String): Int {
    return listOf(
        searchableRank(item.drawableName, needle),
        searchableRank(item.label, needle) + 4,
        searchableRank(item.appPackageName.orEmpty(), needle) + 8,
        searchableRank(item.iconPackLabel, needle) + 12
    ).minOrNull() ?: Int.MAX_VALUE
}

internal fun previewSearchRank(item: IconPackPreviewItem, needle: String): Int {
    return listOf(
        searchableRank(item.drawableName, needle),
        searchableRank(item.label, needle) + 4,
        searchableRank(item.appPackageName.orEmpty(), needle) + 8
    ).minOrNull() ?: Int.MAX_VALUE
}

internal fun searchableRank(value: String, needle: String): Int {
    if (value.isBlank()) return Int.MAX_VALUE
    val lower = value.lowercase(Locale.getDefault())
    val tokenIndex = lower
        .replace('.', '_')
        .replace('-', '_')
        .split('_')
        .indexOfFirst { it.startsWith(needle) }
    return when {
        lower == needle -> 0
        lower.startsWith(needle) -> 1
        tokenIndex >= 0 -> 10 + tokenIndex
        lower.contains(needle) -> 100 + lower.indexOf(needle)
        else -> Int.MAX_VALUE
    }
}

@Composable
internal fun AppIconChooserSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
internal fun AppIconChooserRow(
    item: AppIconChooserItem,
    applied: Boolean,
    onApply: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PreviewIcon(item.iconPackPackage, item.drawableName, 42.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.iconPackLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Button(
                onClick = onApply,
                enabled = !applied,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = if (applied) {
                        stringResource(R.string.app_icons_applied_button)
                    } else {
                        stringResource(R.string.btn_apply)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun PartialApplyDialog(
    pack: IconPackInfo,
    iconMap: IconMapSnapshot,
    onDismiss: () -> Unit,
    onApply: (Set<String>) -> Unit
) {
    val context = LocalContext.current
    var loading by remember(pack.packageName) { mutableStateOf(true) }
    var entries by remember(pack.packageName) { mutableStateOf<List<IconPackEntry>>(emptyList()) }
    var query by rememberSaveable(pack.packageName) { mutableStateOf("") }
    var selectedPackages by remember(pack.packageName, iconMap) {
        mutableStateOf(
            iconMap.sources
                .filterValues { it.iconPackPackage == pack.packageName }
                .keys
                .toSet()
        )
    }

    LaunchedEffect(pack.packageName) {
        entries = IconPackManager.loadIconPackSelectionEntries(context, pack.packageName)
        loading = false
    }

    val visibleEntries = remember(entries, query) {
        val needle = query.trim().lowercase(Locale.getDefault())
        if (needle.isEmpty()) {
            entries
        } else {
            entries.filter { entry ->
                entry.label.lowercase(Locale.getDefault()).contains(needle) ||
                    entry.appPackageName.lowercase(Locale.US).contains(needle) ||
                    entry.drawableName.lowercase(Locale.US).contains(needle)
            }
        }
    }

    AppIconsDialog(
        title = stringResource(R.string.app_icons_supported_apps_title),
        subtitle = pack.label,
        onDismiss = onDismiss,
        footer = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
            Button(onClick = { onApply(selectedPackages) }) {
                Text(stringResource(R.string.app_icons_apply_selected), maxLines = 1)
            }
        }
    ) {
        AppIconsSearchField(
            query = query,
            hint = stringResource(R.string.app_icons_dialog_search_hint),
            onQueryChange = { query = it }
        )
        Spacer(Modifier.height(12.dp))
        when {
            loading -> LoadingDialogContent()
            visibleEntries.isEmpty() -> AppIconsEmptyStateText(stringResource(R.string.app_icons_no_supported_apps))
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(visibleEntries, key = { it.appPackageName + it.drawableName }) { entry ->
                    val checked = selectedPackages.contains(entry.appPackageName)
                    SelectableIconEntryRow(
                        entry = entry,
                        checked = checked,
                        onCheckedChange = { nextChecked ->
                            selectedPackages = if (nextChecked) {
                                selectedPackages + entry.appPackageName
                            } else {
                                selectedPackages - entry.appPackageName
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun ApplyModeDialog(
    pack: IconPackInfo,
    onDismiss: () -> Unit,
    onApply: (IconApplyMode) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_icons_apply_mode_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = pack.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                ApplyModeButton(
                    text = stringResource(R.string.app_icons_apply_mode_all_pack),
                    onClick = { onApply(IconApplyMode.ALL_PACK) }
                )
                ApplyModeButton(
                    text = stringResource(R.string.app_icons_apply_mode_system_only),
                    onClick = { onApply(IconApplyMode.SYSTEM_ONLY) }
                )
                ApplyModeButton(
                    text = stringResource(R.string.app_icons_apply_mode_user_only),
                    onClick = { onApply(IconApplyMode.USER_ONLY) }
                )
                ApplyModeButton(
                    text = stringResource(R.string.app_icons_apply_mode_installed_only),
                    onClick = { onApply(IconApplyMode.INSTALLED_ONLY) }
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } }
    )
}

@Composable
internal fun ApplyModeButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun IconApplyCompletedDialog(
    result: IconApplyResult,
    onDismiss: () -> Unit,
    onRebootSystem: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_icons_apply_finished_title)) },
        text = {
            Text(
                text = stringResource(
                    R.string.app_icons_apply_finished_message_format,
                    result.applied,
                    result.removed,
                    result.skipped,
                    result.failed
                )
            )
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.btn_ok))
            }
        },
        dismissButton = {
            Button(
                onClick = onRebootSystem,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text(stringResource(R.string.app_icons_reboot_system))
            }
        }
    )
}

@Composable
internal fun PackPreviewDialog(pack: IconPackInfo, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var loading by remember(pack.packageName) { mutableStateOf(true) }
    var items by remember(pack.packageName) { mutableStateOf<List<IconPackPreviewItem>>(emptyList()) }
    var query by rememberSaveable(pack.packageName) { mutableStateOf("") }

    LaunchedEffect(pack.packageName) {
        items = IconPackManager.loadPreviewItems(context, pack.packageName)
        loading = false
    }

    val visibleItems = remember(items, query) {
        val needle = query.trim().lowercase(Locale.getDefault())
        if (needle.isEmpty()) {
            items
        } else {
            items.filter { item ->
                item.label.lowercase(Locale.getDefault()).contains(needle) ||
                    item.drawableName.lowercase(Locale.US).contains(needle) ||
                    item.appPackageName.orEmpty().lowercase(Locale.US).contains(needle)
            }.sortedWith(
                compareBy<IconPackPreviewItem> { previewSearchRank(it, needle) }
                    .thenBy { it.drawableName.lowercase(Locale.US) }
                    .thenBy { it.label.lowercase(Locale.getDefault()) }
            )
        }
    }

    AppIconsDialog(
        title = stringResource(R.string.app_icons_pack_contents_title),
        subtitle = pack.label,
        onDismiss = onDismiss,
        footer = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_close)) } }
    ) {
        AppIconsSearchField(
            query = query,
            hint = stringResource(R.string.app_icons_dialog_search_hint),
            onQueryChange = { query = it }
        )
        Spacer(Modifier.height(12.dp))
        when {
            loading -> LoadingDialogContent()
            visibleItems.isEmpty() -> AppIconsEmptyStateText(stringResource(R.string.app_icons_no_pack_icons))
            else -> IconPreviewGrid(items = visibleItems, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
internal fun AppIconDialog(
    app: InstalledIconApp,
    iconMap: IconMapSnapshot,
    onDismiss: () -> Unit,
    onRestore: () -> Unit,
    onApply: (AppIconCandidate) -> Unit,
    onImportCustomIcon: (Uri) -> Unit,
    onSaveShapeOverrides: (AppIconShapeOverrides?) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val previewSizePx = with(density) { 56.dp.roundToPx() }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImportCustomIcon(uri)
    }
    var loading by remember(app.packageName) { mutableStateOf(true) }
    var candidates by remember(app.packageName) { mutableStateOf<List<AppIconCandidate>>(emptyList()) }
    var appliedBitmap by remember(app.packageName, iconMap) { mutableStateOf<Bitmap?>(null) }
    var shapeOverrides by remember(app.packageName, iconMap) {
        mutableStateOf(iconMap.shapeOverrides[app.packageName] ?: app.shapeOverrides ?: AppIconShapeOverrides())
    }

    LaunchedEffect(app.packageName, iconMap) {
        candidates = IconPackManager.loadCandidatesForApp(context, app.packageName)
        appliedBitmap = IconPackManager.loadAppliedIconBitmap(context, app.packageName, previewSizePx)
        loading = false
    }

    AppIconsDialog(
        title = app.label,
        subtitle = app.packageName,
        onDismiss = onDismiss,
        footer = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_close)) } }
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (appliedBitmap != null) {
                    BitmapIcon(bitmap = appliedBitmap, size = 56.dp, fallback = Icons.Rounded.Apps)
                } else {
                    DrawableIcon(drawable = app.icon, size = 56.dp, fallback = Icons.Rounded.Apps)
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 14.dp)
                ) {
                    Text(
                        text = stringResource(R.string.app_icons_current_icon_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = app.appliedIconPackLabel?.let {
                            stringResource(R.string.app_icons_applied_from_format, it)
                        } ?: stringResource(R.string.app_icons_not_applied_to_app),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (app.appliedIconFileName != null) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onRestore,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.app_icons_restore_default), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = { importLauncher.launch(arrayOf("image/*", "application/xml", "text/xml")) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Rounded.Apps, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.app_icons_import_custom_icon), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        Spacer(Modifier.height(12.dp))
        PerAppShapeSettingsCard(
            overrides = shapeOverrides,
            onOverridesChange = { shapeOverrides = it },
            onSave = { onSaveShapeOverrides(shapeOverrides.normalizedOrNull()) },
            onReset = {
                shapeOverrides = AppIconShapeOverrides()
                onSaveShapeOverrides(null)
            }
        )

        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.app_icons_available_icons_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))

        when {
            loading -> LoadingDialogContent()
            candidates.isEmpty() -> AppIconsEmptyStateText(stringResource(R.string.app_icons_no_app_candidates))
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(candidates, key = { it.iconPackPackage + it.drawableName }) { candidate ->
                    val appliedSource = iconMap.sources[app.packageName]
                    val applied = appliedSource?.iconPackPackage == candidate.iconPackPackage &&
                        appliedSource.drawableName == candidate.drawableName
                    AppIconCandidateRow(
                        candidate = candidate,
                        applied = applied,
                        busy = false,
                        onApply = { onApply(candidate) }
                    )
                }
            }
        }
    }
}