package org.pixel.extra.parts.icons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pixel.extra.parts.R
import org.pixel.extra.parts.ui.GroupCard
import org.pixel.extra.parts.ui.SettingSwitchRow
import org.pixel.extra.parts.ui.SettingSwitchRowVariant
import org.pixel.extra.parts.ui.sectionContentPadding

// Icons hub: engine switches, full adaptive-shape studio, per-area
// stretch/remove/scale with tint swatches, themed-icon toggles, the live
// icon-pack pipeline (packs, progress, dialogs) and the per-app list.
@Composable
fun IconsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ui by remember { mutableStateOf<IconsStore.IconsState?>(null) }
    var dashboard by remember { mutableStateOf(PacksManager.getCachedDashboardState()) }
    var isLoading by remember { mutableStateOf(dashboard == null) }
    var loadFailed by remember { mutableStateOf(false) }
    var packsExpanded by remember { mutableStateOf(true) }
    var appsExpanded by remember { mutableStateOf(true) }
    var showSystemApps by remember { mutableStateOf(true) }
    var showHiddenApps by remember { mutableStateOf(false) }
    var appSearchQuery by remember { mutableStateOf("") }
    var partialPack by remember { mutableStateOf<IconPackInfo?>(null) }
    var previewPack by remember { mutableStateOf<IconPackInfo?>(null) }
    var expandedAppPackage by remember { mutableStateOf<String?>(null) }
    var applyModePack by remember { mutableStateOf<IconPackInfo?>(null) }
    var confirmClearAllIcons by remember { mutableStateOf(false) }
    var activeProgress by remember { mutableStateOf(PacksManager.getActiveProgress()) }
    var completionResult by remember { mutableStateOf<IconApplyResult?>(null) }
    var handledProgressTaskId by remember { mutableStateOf<String?>(null) }

    suspend fun loadDashboard(showSpinner: Boolean = true, forceRefresh: Boolean = false) {
        if (showSpinner) isLoading = true
        loadFailed = false
        runCatching { PacksManager.loadDashboardState(context, forceRefresh) }
            .onSuccess { dashboard = it }
            .onFailure { loadFailed = true }
        isLoading = false
    }

    fun startIconOperation(start: () -> Boolean) {
        startIconsOperation(context, start) {
            activeProgress = PacksManager.getActiveProgress()
        }
    }

    LaunchedEffect(Unit) {
        ui = withContext(Dispatchers.IO) { IconsStore.loadState(context) }
        loadDashboard(showSpinner = dashboard == null)
    }

    LaunchedEffect(Unit) {
        while (true) {
            val progress = PacksManager.getActiveProgress()
            activeProgress = progress
            if (progress?.completed == true && handledProgressTaskId != progress.taskId) {
                handledProgressTaskId = progress.taskId
                completionResult = progress.result
                loadDashboard(showSpinner = false, forceRefresh = true)
            }
            delay(500)
        }
    }

    val state = ui
    fun update(next: IconsStore.IconsState) {
        ui = next
    }

    val packs = dashboard?.iconPacks.orEmpty()
    val visibleApps = remember(dashboard?.installedApps, showSystemApps, showHiddenApps, appSearchQuery) {
        filterVisibleApps(
            dashboard?.installedApps.orEmpty(),
            showSystemApps,
            showHiddenApps,
            appSearchQuery,
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = sectionContentPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "__section_header__") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.tab_icons),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.icons_header_sub),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state == null) {
            return@LazyColumn
        }

        item(key = "engine") {
            GroupCard(title = stringResource(R.string.icons_group_master)) {
                SettingSwitchRow(
                    title = stringResource(R.string.icons_master_title),
                    summary = stringResource(R.string.icons_master_sub),
                    checked = state.moduleEnabled,
                    variant = SettingSwitchRowVariant.Card,
                    onCheckedChange = { enabled ->
                        IconsStore.putEnabled(context, IconsKeys.APP_ICONS_ENABLED, enabled)
                        update(state.copy(moduleEnabled = enabled))
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingSwitchRow(
                    title = stringResource(R.string.icons_launcher_only_title),
                    summary = stringResource(R.string.icons_launcher_only_sub),
                    checked = state.launcherOnly,
                    enabled = state.moduleEnabled,
                    variant = SettingSwitchRowVariant.Card,
                    disabledTitleColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    onCheckedChange = { enabled ->
                        IconsStore.putEnabled(context, IconsKeys.APP_ICONS_LAUNCHER_ENABLED, enabled)
                        update(state.copy(launcherOnly = enabled))
                    },
                )
            }
        }

        item(key = "shape") {
            IconsShapeSection()
        }

        item(key = "appearance") {
            GroupCard(title = stringResource(R.string.icons_group_appearance)) {
                IconsAreaBlock(
                    areaTitle = stringResource(R.string.icons_area_system),
                    stretch = state.systemStretch,
                    remove = state.systemRemove,
                    scale = state.systemScale,
                    onStretchChange = { enabled ->
                        IconsStore.putEnabled(context, IconsKeys.APP_ICONS_SYSTEM_STRETCH_SHAPE, enabled)
                        if (enabled) {
                            IconsStore.putEnabled(context, IconsKeys.APP_ICONS_SYSTEM_REMOVE_SHAPE, false)
                        }
                        update(
                            state.copy(
                                systemStretch = enabled,
                                systemRemove = if (enabled) false else state.systemRemove,
                            )
                        )
                    },
                    onRemoveChange = { enabled ->
                        IconsStore.putEnabled(context, IconsKeys.APP_ICONS_SYSTEM_REMOVE_SHAPE, enabled)
                        if (enabled) {
                            IconsStore.putEnabled(context, IconsKeys.APP_ICONS_SYSTEM_STRETCH_SHAPE, false)
                        }
                        update(
                            state.copy(
                                systemRemove = enabled,
                                systemStretch = if (enabled) false else state.systemStretch,
                            )
                        )
                    },
                    onScaleChange = { value ->
                        val coerced = IconsStore.normalizeScale(value)
                        IconsStore.putFloat(context, IconsKeys.APP_ICONS_SYSTEM_SHAPE_SCALE, coerced)
                        update(state.copy(systemScale = coerced))
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                IconsAreaBlock(
                    areaTitle = stringResource(R.string.icons_area_notification),
                    stretch = state.notificationStretch,
                    remove = state.notificationRemove,
                    scale = state.notificationScale,
                    onStretchChange = { enabled ->
                        IconsStore.putEnabled(
                            context, IconsKeys.APP_ICONS_NOTIFICATION_STRETCH_SHAPE, enabled
                        )
                        if (enabled) {
                            IconsStore.putEnabled(
                                context, IconsKeys.APP_ICONS_NOTIFICATION_REMOVE_SHAPE, false
                            )
                        }
                        update(
                            state.copy(
                                notificationStretch = enabled,
                                notificationRemove = if (enabled) false else state.notificationRemove,
                            )
                        )
                    },
                    onRemoveChange = { enabled ->
                        IconsStore.putEnabled(
                            context, IconsKeys.APP_ICONS_NOTIFICATION_REMOVE_SHAPE, enabled
                        )
                        if (enabled) {
                            IconsStore.putEnabled(
                                context, IconsKeys.APP_ICONS_NOTIFICATION_STRETCH_SHAPE, false
                            )
                        }
                        update(
                            state.copy(
                                notificationRemove = enabled,
                                notificationStretch = if (enabled) false else state.notificationStretch,
                            )
                        )
                    },
                    onScaleChange = { value ->
                        val coerced = IconsStore.normalizeScale(value)
                        IconsStore.putFloat(
                            context, IconsKeys.APP_ICONS_NOTIFICATION_SHAPE_SCALE, coerced
                        )
                        update(state.copy(notificationScale = coerced))
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                IconsAreaBlock(
                    areaTitle = stringResource(R.string.icons_area_launcher),
                    stretch = state.launcherStretch,
                    remove = state.launcherRemove,
                    scale = state.launcherScale,
                    onStretchChange = { enabled ->
                        IconsStore.putEnabled(
                            context, IconsKeys.APP_ICONS_LAUNCHER_STRETCH_SHAPE, enabled
                        )
                        if (enabled) {
                            IconsStore.putEnabled(
                                context, IconsKeys.APP_ICONS_LAUNCHER_REMOVE_SHAPE, false
                            )
                        }
                        update(
                            state.copy(
                                launcherStretch = enabled,
                                launcherRemove = if (enabled) false else state.launcherStretch,
                            )
                        )
                    },
                    onRemoveChange = { enabled ->
                        IconsStore.putEnabled(
                            context, IconsKeys.APP_ICONS_LAUNCHER_REMOVE_SHAPE, enabled
                        )
                        if (enabled) {
                            IconsStore.putEnabled(
                                context, IconsKeys.APP_ICONS_LAUNCHER_STRETCH_SHAPE, false
                            )
                        }
                        update(
                            state.copy(
                                launcherRemove = enabled,
                                launcherStretch = if (enabled) false else state.launcherStretch,
                            )
                        )
                    },
                    onScaleChange = { value ->
                        val coerced = IconsStore.normalizeScale(value)
                        IconsStore.putFloat(context, IconsKeys.APP_ICONS_LAUNCHER_SHAPE_SCALE, coerced)
                        update(state.copy(launcherScale = coerced))
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                IconsTintBlock(
                    title = stringResource(R.string.icons_tint_bg_title),
                    summary = stringResource(R.string.icons_tint_bg_sub),
                    mode = state.backgroundTintMode,
                    selectedColor = state.backgroundTintColor,
                    onModeChange = { mode ->
                        IconsStore.putInt(
                            context, IconsKeys.APP_ICONS_SHAPE_BACKGROUND_TINT_MODE, mode
                        )
                        update(state.copy(backgroundTintMode = mode))
                    },
                    onColorChange = { color ->
                        IconsStore.putInt(
                            context, IconsKeys.APP_ICONS_SHAPE_BACKGROUND_TINT_MODE,
                            IconsStore.TINT_MODE_CUSTOM,
                        )
                        IconsStore.putInt(
                            context, IconsKeys.APP_ICONS_SHAPE_BACKGROUND_TINT_COLOR, color
                        )
                        update(
                            state.copy(
                                backgroundTintMode = IconsStore.TINT_MODE_CUSTOM,
                                backgroundTintColor = color,
                            )
                        )
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                IconsTintBlock(
                    title = stringResource(R.string.icons_tint_fg_title),
                    summary = stringResource(R.string.icons_tint_fg_sub),
                    mode = state.foregroundTintMode,
                    selectedColor = state.foregroundTintColor,
                    onModeChange = { mode ->
                        IconsStore.putInt(
                            context, IconsKeys.APP_ICONS_SHAPE_FOREGROUND_TINT_MODE, mode
                        )
                        update(state.copy(foregroundTintMode = mode))
                    },
                    onColorChange = { color ->
                        IconsStore.putInt(
                            context, IconsKeys.APP_ICONS_SHAPE_FOREGROUND_TINT_MODE,
                            IconsStore.TINT_MODE_CUSTOM,
                        )
                        IconsStore.putInt(
                            context, IconsKeys.APP_ICONS_SHAPE_FOREGROUND_TINT_COLOR, color
                        )
                        update(
                            state.copy(
                                foregroundTintMode = IconsStore.TINT_MODE_CUSTOM,
                                foregroundTintColor = color,
                            )
                        )
                    },
                )
            }
        }

        item(key = "themed") {
            GroupCard(title = stringResource(R.string.icons_group_themed)) {
                SettingSwitchRow(
                    title = stringResource(R.string.icons_follow_workspace_title),
                    summary = stringResource(R.string.icons_follow_workspace_sub),
                    checked = state.allAppsFollowWorkspace,
                    variant = SettingSwitchRowVariant.Card,
                    onCheckedChange = { enabled ->
                        IconsStore.putEnabled(
                            context, IconsKeys.ICON_SHAPE_ALL_APPS_FOLLOW_WORKSPACE, enabled
                        )
                        update(state.copy(allAppsFollowWorkspace = enabled))
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingSwitchRow(
                    title = stringResource(R.string.icons_match_workspace_title),
                    summary = stringResource(R.string.icons_match_workspace_sub),
                    checked = state.workspaceMatchAllApps,
                    variant = SettingSwitchRowVariant.Card,
                    onCheckedChange = { enabled ->
                        IconsStore.putEnabled(
                            context, IconsKeys.ICON_SHAPE_WORKSPACE_MATCH_ALL_APPS, enabled
                        )
                        update(state.copy(workspaceMatchAllApps = enabled))
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingSwitchRow(
                    title = stringResource(R.string.icons_ignore_custom_title),
                    summary = stringResource(R.string.icons_ignore_custom_sub),
                    checked = state.ignoreCustomSettings,
                    variant = SettingSwitchRowVariant.Card,
                    onCheckedChange = { enabled ->
                        IconsStore.putEnabled(
                            context, IconsKeys.ICON_SHAPE_IGNORE_CUSTOM_SETTINGS, enabled
                        )
                        update(state.copy(ignoreCustomSettings = enabled))
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingSwitchRow(
                    title = stringResource(R.string.icons_themed_all_apps_title),
                    summary = stringResource(R.string.icons_themed_all_apps_sub),
                    checked = state.allAppsThemedIcons,
                    variant = SettingSwitchRowVariant.Card,
                    onCheckedChange = { enabled ->
                        IconsStore.putEnabled(
                            context, IconsKeys.ICON_SHAPE_ALL_APPS_THEMED_ICONS, enabled
                        )
                        update(state.copy(allAppsThemedIcons = enabled))
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingSwitchRow(
                    title = stringResource(R.string.icons_themed_suggestions_title),
                    summary = stringResource(R.string.icons_themed_suggestions_sub),
                    checked = state.suggestionsThemedIcons,
                    variant = SettingSwitchRowVariant.Card,
                    onCheckedChange = { enabled ->
                        IconsStore.putEnabled(
                            context, IconsKeys.ICON_SHAPE_SUGGESTIONS_THEMED_ICONS, enabled
                        )
                        update(state.copy(suggestionsThemedIcons = enabled))
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingSwitchRow(
                    title = stringResource(R.string.icons_native_search_title),
                    summary = stringResource(R.string.icons_native_search_sub),
                    checked = state.nativeSearch,
                    variant = SettingSwitchRowVariant.Card,
                    onCheckedChange = { enabled ->
                        IconsStore.putEnabled(context, IconsKeys.PIXEL_LAUNCHER_NATIVE_SEARCH, enabled)
                        update(state.copy(nativeSearch = enabled))
                    },
                )
            }
        }

        when {
            isLoading -> item(key = "packs_loading") {
                IconsLoadingContent()
            }
            loadFailed -> item(key = "packs_failed") {
                IconsEmptyState(stringResource(R.string.icons_load_failed))
            }
            else -> {
                if (activeProgress != null) {
                    item(key = "packs_progress") {
                        IconsProgressCard(progress = activeProgress!!)
                    }
                }
                item(key = "packs") {
                    IconsPacksSection(
                        packs = packs,
                        expanded = packsExpanded,
                        busy = activeProgress?.completed == false,
                        onExpandChange = { packsExpanded = it },
                        onApplyAll = { pack -> applyModePack = pack },
                        onApplyPartial = { partialPack = it },
                        onViewPack = { previewPack = it },
                        onApplyUpdate = { pack ->
                            startIconOperation {
                                PacksManager.startApplyAll(context, pack.packageName, IconApplyMode.INSTALLED_ONLY)
                            }
                        },
                        onCancelAll = { pack ->
                            startIconOperation { PacksManager.startRemoveIconPack(context, pack.packageName) }
                        },
                    )
                }
                item(key = "apps") {
                    IconsAppsSection(
                        apps = visibleApps,
                        iconMap = dashboard?.iconMap ?: IconMapSnapshot.EMPTY,
                        iconPacks = packs,
                        expanded = appsExpanded,
                        busy = activeProgress?.completed == false,
                        showSystemApps = showSystemApps,
                        showHiddenApps = showHiddenApps,
                        searchQuery = appSearchQuery,
                        expandedAppPackage = expandedAppPackage,
                        onExpandChange = { appsExpanded = it },
                        onShowSystemChange = { showSystemApps = it },
                        onShowHiddenChange = { showHiddenApps = it },
                        onSearchChange = { appSearchQuery = it },
                        onAppExpandChange = { packageName, next ->
                            expandedAppPackage = if (next) packageName else null
                        },
                        onRestore = {
                            expandedAppPackage = null
                            startIconOperation {
                                PacksManager.startRemovePackageIcon(context, it.packageName)
                            }
                        },
                        onApply = { app, candidate ->
                            expandedAppPackage = null
                            startIconOperation {
                                PacksManager.startApplySingle(
                                    context,
                                    candidate.iconPackPackage,
                                    app.packageName,
                                    candidate.drawableName,
                                )
                            }
                        },
                        onImport = { app, uri ->
                            expandedAppPackage = null
                            startIconOperation {
                                PacksManager.startImportPackageIcon(context, app.packageName, uri)
                            }
                        },
                        onSaveShapeOverrides = { _, _ ->
                            scope.launch { loadDashboard(showSpinner = false, forceRefresh = true) }
                        },
                    )
                }
                item(key = "clear_all") {
                    GroupCard(title = stringResource(R.string.icons_clear_all)) {
                        Text(
                            text = stringResource(R.string.icons_clear_all_msg),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = { confirmClearAllIcons = true },
                            enabled = activeProgress?.completed != false,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.icons_clear_all))
                        }
                    }
                }
            }
        }
    }

    val mapSnapshot = dashboard?.iconMap
    if (partialPack != null && mapSnapshot != null) {
        IconsPartialApplyDialog(
            pack = partialPack!!,
            iconMap = mapSnapshot,
            onDismiss = { partialPack = null },
            onApply = { selectedPackages ->
                val pack = partialPack ?: return@IconsPartialApplyDialog
                partialPack = null
                startIconOperation { PacksManager.startApplyPartial(context, pack.packageName, selectedPackages) }
            },
        )
    }

    if (applyModePack != null) {
        IconsApplyModeDialog(
            pack = applyModePack!!,
            onDismiss = { applyModePack = null },
            onApply = { mode ->
                val pack = applyModePack ?: return@IconsApplyModeDialog
                applyModePack = null
                startIconOperation { PacksManager.startApplyAll(context, pack.packageName, mode) }
            },
        )
    }

    if (previewPack != null) {
        IconsPackPreviewDialog(
            pack = previewPack!!,
            onDismiss = { previewPack = null },
        )
    }

    if (confirmClearAllIcons) {
        IconsClearAllDialog(
            onDismiss = { confirmClearAllIcons = false },
            onConfirm = {
                confirmClearAllIcons = false
                startIconOperation { PacksManager.startClearAllIcons(context) }
            },
        )
    }

    if (completionResult != null) {
        IconsApplyCompletedDialog(
            result = completionResult!!,
            onDismiss = {
                completionResult = null
                PacksManager.clearCompletedProgress()
                activeProgress = PacksManager.getActiveProgress()
            },
            onRebooted = {
                completionResult = null
                PacksManager.clearCompletedProgress()
                activeProgress = PacksManager.getActiveProgress()
            },
        )
    }
}
