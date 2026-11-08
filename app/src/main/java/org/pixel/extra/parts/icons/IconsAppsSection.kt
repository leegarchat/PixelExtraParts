package org.pixel.extra.parts.icons

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pixel.extra.parts.R
import org.pixel.extra.parts.ui.SettingSlider
import org.pixel.extra.parts.ui.SettingSwitchRow
import org.pixel.extra.parts.ui.SettingSwitchRowVariant
import org.pixel.extra.parts.ui.edgeOutline
import java.util.Locale

// All-applications block: system/hidden filters, package search and one
// expandable card per app with the current icon, restore, custom import,
// pack chooser and per-app shape overrides.
@Composable
internal fun IconsAppsSection(
    apps: List<InstalledIconApp>,
    iconMap: IconMapSnapshot,
    iconPacks: List<IconPackInfo>,
    expanded: Boolean,
    busy: Boolean,
    showSystemApps: Boolean,
    showHiddenApps: Boolean,
    searchQuery: String,
    expandedAppPackage: String?,
    onExpandChange: (Boolean) -> Unit,
    onShowSystemChange: (Boolean) -> Unit,
    onShowHiddenChange: (Boolean) -> Unit,
    onSearchChange: (String) -> Unit,
    onAppExpandChange: (String?, Boolean) -> Unit,
    onRestore: (InstalledIconApp) -> Unit,
    onApply: (InstalledIconApp, AppIconCandidate) -> Unit,
    onImport: (InstalledIconApp, Uri) -> Unit,
    onSaveShapeOverrides: (InstalledIconApp, AppIconShapeOverrides?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        IconsExpandableHeader(
            title = stringResource(R.string.icons_apps_title),
            subtitle = stringResource(R.string.icons_app_count, apps.size),
            icon = Icons.Rounded.Apps,
            expanded = expanded,
            onExpandChange = onExpandChange,
        )
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingSwitchRow(
                    title = stringResource(R.string.icons_show_system_title),
                    summary = stringResource(R.string.icons_show_system_summary),
                    checked = showSystemApps,
                    onCheckedChange = onShowSystemChange,
                    variant = SettingSwitchRowVariant.Card,
                )
                SettingSwitchRow(
                    title = stringResource(R.string.icons_show_hidden_title),
                    summary = stringResource(R.string.icons_show_hidden_summary),
                    checked = showHiddenApps,
                    onCheckedChange = onShowHiddenChange,
                    variant = SettingSwitchRowVariant.Card,
                )
                IconsSearchField(
                    query = searchQuery,
                    hint = stringResource(R.string.hidden_apps_search_hint),
                    onQueryChange = onSearchChange,
                )
                if (apps.isEmpty()) {
                    IconsEmptyState(stringResource(R.string.icons_no_apps))
                } else {
                    apps.forEach { app ->
                        InstalledAppCard(
                            app = app,
                            iconMap = iconMap,
                            iconPacks = iconPacks,
                            expanded = expandedAppPackage == app.packageName,
                            busy = busy,
                            onExpandChange = { next -> onAppExpandChange(app.packageName, next) },
                            onRestore = { onRestore(app) },
                            onApply = { candidate -> onApply(app, candidate) },
                            onImportCustomIcon = { uri -> onImport(app, uri) },
                            onSaveShapeOverrides = { overrides -> onSaveShapeOverrides(app, overrides) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InstalledAppCard(
    app: InstalledIconApp,
    iconMap: IconMapSnapshot,
    iconPacks: List<IconPackInfo>,
    expanded: Boolean,
    busy: Boolean,
    onExpandChange: (Boolean) -> Unit,
    onRestore: () -> Unit,
    onApply: (AppIconCandidate) -> Unit,
    onImportCustomIcon: (Uri) -> Unit,
    onSaveShapeOverrides: (AppIconShapeOverrides?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
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
        candidates = withContext(Dispatchers.IO) {
            PacksManager.loadCandidatesForApp(context, app.packageName)
        }
        appliedBitmap = withContext(Dispatchers.IO) {
            PacksManager.loadAppliedIconBitmap(context, app.packageName, previewSizePx)
        }
        loading = false
    }

    val shape = RoundedCornerShape(18.dp)
    val container = MaterialTheme.colorScheme.surfaceContainerLow
    Surface(
        color = container,
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, container.edgeOutline(), shape)
            .clip(shape)
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                onExpandChange(!expanded)
            },
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PackDrawableIcon(drawable = app.icon, size = 42.dp)
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = app.appliedIconPackLabel?.let {
                            stringResource(R.string.icons_applied_from, it)
                        } ?: stringResource(R.string.icons_not_applied),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (app.appliedIconPackLabel != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Icon(
                    painter = androidx.compose.ui.res.painterResource(org.pixel.extra.parts.R.drawable.ic_chevron_down),
                    contentDescription = null,
                    modifier = Modifier.rotate(if (expanded) 180f else 0f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AppIconCurrentIconCard(app = app, appliedBitmap = appliedBitmap)
                    if (app.appliedIconFileName != null) {
                        OutlinedButton(onClick = onRestore, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.icons_restore), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("image/*", "application/xml", "text/xml")) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Apps, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.icons_import_icon), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = { chooserOpen = true },
                        enabled = !busy && iconPacks.any { it.installed },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.icons_choose_pack), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    PerAppShapeSettingsCard(
                        overrides = shapeOverrides,
                        onOverridesChange = { shapeOverrides = it },
                        onSave = {
                            scope.launch {
                                PacksManager.savePackageShapeOverrides(context, app.packageName, shapeOverrides.normalizedOrNull())
                                onSaveShapeOverrides(shapeOverrides.normalizedOrNull())
                            }
                        },
                        onReset = {
                            shapeOverrides = AppIconShapeOverrides()
                            scope.launch {
                                PacksManager.savePackageShapeOverrides(context, app.packageName, null)
                                onSaveShapeOverrides(null)
                            }
                        },
                    )
                    Text(
                        text = stringResource(R.string.icons_available_icons),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    when {
                        loading -> IconsLoadingContent()
                        candidates.isEmpty() -> IconsEmptyState(stringResource(R.string.icons_no_candidates))
                        else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val appliedSource = iconMap.sources[app.packageName]
                            candidates.forEach { candidate ->
                                val applied = appliedSource?.iconPackPackage == candidate.iconPackPackage &&
                                    appliedSource.drawableName == candidate.drawableName
                                AppIconCandidateRow(
                                    candidate = candidate,
                                    applied = applied,
                                    busy = busy,
                                    onApply = { onApply(candidate) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (chooserOpen) {
        IconsChooserDialog(
            app = app,
            iconMap = iconMap,
            iconPacks = iconPacks.filter { it.installed },
            onDismiss = { chooserOpen = false },
            onApply = { candidate ->
                chooserOpen = false
                onApply(candidate)
            },
        )
    }
}

@Composable
private fun AppIconCurrentIconCard(app: InstalledIconApp, appliedBitmap: Bitmap?) {
    val shape = RoundedCornerShape(18.dp)
    val container = MaterialTheme.colorScheme.surfaceContainerHigh
    Surface(
        color = container,
        shape = shape,
        modifier = Modifier.fillMaxWidth().border(1.dp, container.edgeOutline(), shape),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (appliedBitmap != null) {
                PackRenderedBitmap(bitmap = appliedBitmap, size = 56.dp)
            } else {
                PackDrawableIcon(drawable = app.icon, size = 56.dp)
            }
            Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                Text(
                    text = stringResource(R.string.icons_current_icon),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = app.appliedIconPackLabel?.let {
                        stringResource(R.string.icons_applied_from, it)
                    } ?: stringResource(R.string.icons_not_applied),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AppIconCandidateRow(
    candidate: AppIconCandidate,
    applied: Boolean = false,
    busy: Boolean = false,
    onApply: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PackBitmapIcon(candidate.iconPackPackage, candidate.drawableName, 42.dp)
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    text = candidate.iconPackLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = candidate.drawableName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Button(
                onClick = onApply,
                enabled = !applied && !busy,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = if (applied) {
                        stringResource(R.string.icons_pack_applied)
                    } else {
                        stringResource(R.string.icons_btn_apply)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PerAppShapeSettingsCard(
    overrides: AppIconShapeOverrides,
    onOverridesChange: (AppIconShapeOverrides) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(18.dp)
    val container = MaterialTheme.colorScheme.surfaceContainerHigh
    Surface(
        color = container,
        shape = shape,
        modifier = Modifier.fillMaxWidth().border(1.dp, container.edgeOutline(), shape),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        expanded = !expanded
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.icons_per_app_shape),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.icons_per_app_shape_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    painter = androidx.compose.ui.res.painterResource(org.pixel.extra.parts.R.drawable.ic_chevron_down),
                    contentDescription = null,
                    modifier = Modifier.rotate(if (expanded) 180f else 0f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PerAppShapeAreaEditor(
                        title = stringResource(R.string.icons_per_app_system),
                        config = overrides.system ?: IconShapeAreaConfig(),
                        onConfigChange = { onOverridesChange(overrides.copy(system = it)) },
                    )
                    PerAppShapeAreaEditor(
                        title = stringResource(R.string.icons_per_app_notification),
                        config = overrides.notification ?: IconShapeAreaConfig(),
                        onConfigChange = { onOverridesChange(overrides.copy(notification = it)) },
                    )
                    PerAppShapeAreaEditor(
                        title = stringResource(R.string.icons_per_app_launcher),
                        config = overrides.launcher ?: IconShapeAreaConfig(),
                        onConfigChange = { onOverridesChange(overrides.copy(launcher = it)) },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.icons_reset_individual), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Button(onClick = onSave, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.icons_save_individual), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PerAppShapeAreaEditor(
    title: String,
    config: IconShapeAreaConfig,
    onConfigChange: (IconShapeAreaConfig) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
            .padding(vertical = 6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        SettingSwitchRow(
            title = stringResource(R.string.icons_per_app_stretch),
            summary = stringResource(R.string.icons_per_app_stretch_sub),
            checked = config.stretchShape,
            onCheckedChange = { checked ->
                onConfigChange(config.copy(stretchShape = checked, removeShape = if (checked) false else config.removeShape))
            },
            variant = SettingSwitchRowVariant.Card,
        )
        SettingSwitchRow(
            title = stringResource(R.string.icons_per_app_remove),
            summary = stringResource(R.string.icons_per_app_remove_sub),
            checked = config.removeShape,
            onCheckedChange = { checked ->
                onConfigChange(config.copy(removeShape = checked, stretchShape = if (checked) false else config.stretchShape))
            },
            variant = SettingSwitchRowVariant.Card,
        )
        SettingSlider(
            title = stringResource(R.string.icons_per_app_scale),
            value = config.scalePercent,
            onValueChange = { onConfigChange(config.copy(scalePercent = it)) },
            enabled = config.stretchShape && !config.removeShape,
            valueRange = PacksManager.MIN_SHAPE_SCALE_PERCENT..PacksManager.MAX_SHAPE_SCALE_PERCENT,
            step = 1f,
            defaultValue = PacksManager.DEFAULT_SHAPE_SCALE_PERCENT,
            resetLabel = stringResource(R.string.icons_reset),
            valueLabel = { v -> stringResource(R.string.icons_scale_value, v) },
        )
        Text(
            text = stringResource(R.string.icons_scale_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}

internal fun filterVisibleApps(
    apps: List<InstalledIconApp>,
    showSystemApps: Boolean,
    showHiddenApps: Boolean,
    query: String,
): List<InstalledIconApp> {
    val filtered = apps.filter { app ->
        (showSystemApps || !app.isSystem) && (showHiddenApps || app.isLauncherVisible)
    }
    if (query.isBlank()) return filtered
    val needle = query.trim().lowercase(Locale.getDefault())
    return filtered.filter { app ->
        app.label.lowercase(Locale.getDefault()).contains(needle) ||
            app.packageName.lowercase(Locale.US).contains(needle) ||
            app.appliedIconPackLabel.orEmpty().lowercase(Locale.getDefault()).contains(needle)
    }
}
