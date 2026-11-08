package org.pixel.extra.parts.icons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.pixel.extra.parts.R
import org.pixel.extra.parts.ui.GroupCard
import org.pixel.extra.parts.ui.SettingSwitchRow
import org.pixel.extra.parts.ui.SettingSwitchRowVariant
import org.pixel.extra.parts.ui.sectionContentPadding

// Icons section skeleton: engine master switch, adaptive-shape presets plus a
// custom path editor, per-area stretch/remove/scale controls, tint modes with
// swatches, themed-icon toggles and the icon-pack picker. Reads and writes go
// straight to Settings.Global through IconsStore; the heavy apply pipeline
// (per-app export, progress, overlays) lands in a later stage.
@Composable
fun IconsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var ui by remember { mutableStateOf<IconsStore.IconsState?>(null) }
    var packs by remember { mutableStateOf<List<IconsStore.IconPackRef>>(emptyList()) }
    var draftName by remember { mutableStateOf<String?>(null) }
    var draftPath by remember { mutableStateOf<String?>(null) }
    var pathError by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) { IconsStore.loadState(context) }
        ui = loaded
        packs = withContext(Dispatchers.IO) { IconsStore.discoverIconPacks(context) }
    }

    val state = ui
    // Drafts mirror the stored custom shape once the snapshot arrives.
    LaunchedEffect(state) {
        if (state != null && draftName == null && draftPath == null) {
            draftName = state.customName
            draftPath = state.customPath
        }
    }

    fun update(next: IconsStore.IconsState) {
        ui = next
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
            // Snapshot still loading; rows appear on the next frame.
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
            GroupCard(title = stringResource(R.string.icons_group_shape)) {
                Text(
                    text = stringResource(R.string.icons_shape_presets),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                IconsPresetRow(
                    label = stringResource(R.string.icons_shape_system_default),
                    pathData = "",
                    selected = state.customPath.isBlank(),
                    onSelect = {
                        IconsStore.putString(context, IconsKeys.ICON_SHAPE_CUSTOM_PATH, null)
                        IconsStore.putString(context, IconsKeys.ICON_SHAPE_CUSTOM_NAME, null)
                        draftName = ""
                        draftPath = ""
                        pathError = false
                        update(state.copy(customPath = "", customName = ""))
                    },
                )
                IconsStore.SHAPE_PRESETS.forEach { preset ->
                    IconsPresetRow(
                        label = stringResource(preset.labelRes),
                        pathData = preset.pathData,
                        selected = state.customPath.trim() == preset.pathData,
                        onSelect = {
                            val label = context.getString(preset.labelRes)
                            IconsStore.putString(context, IconsKeys.ICON_SHAPE_CUSTOM_PATH, preset.pathData)
                            IconsStore.putString(context, IconsKeys.ICON_SHAPE_CUSTOM_NAME, label)
                            draftName = label
                            draftPath = preset.pathData
                            pathError = false
                            update(state.copy(customPath = preset.pathData, customName = label))
                        },
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Text(
                    text = stringResource(R.string.icons_custom_section),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                OutlinedTextField(
                    value = draftName.orEmpty(),
                    onValueChange = { draftName = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.icons_custom_name)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draftPath.orEmpty(),
                    onValueChange = {
                        draftPath = it
                        pathError = false
                    },
                    singleLine = false,
                    minLines = 2,
                    label = { Text(stringResource(R.string.icons_custom_path)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    isError = pathError,
                    supportingText = if (pathError) {
                        {
                            Text(
                                text = stringResource(R.string.icons_custom_invalid),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = {
                            IconsStore.putString(context, IconsKeys.ICON_SHAPE_CUSTOM_PATH, null)
                            IconsStore.putString(context, IconsKeys.ICON_SHAPE_CUSTOM_NAME, null)
                            draftName = ""
                            draftPath = ""
                            pathError = false
                            update(state.copy(customPath = "", customName = ""))
                        },
                    ) {
                        Text(stringResource(R.string.icons_custom_clear))
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            val path = draftPath.orEmpty().trim()
                            if (!IconsStore.isValidShapePath(path)) {
                                pathError = true
                                return@TextButton
                            }
                            val name = draftName.orEmpty().trim()
                            IconsStore.putString(context, IconsKeys.ICON_SHAPE_CUSTOM_PATH, path)
                            IconsStore.putString(context, IconsKeys.ICON_SHAPE_CUSTOM_NAME, name)
                            pathError = false
                            update(state.copy(customPath = path, customName = name))
                        },
                    ) {
                        Text(stringResource(R.string.icons_custom_apply))
                    }
                }
            }
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
                                launcherRemove = if (enabled) false else state.launcherRemove,
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

        item(key = "packs") {
            GroupCard(title = stringResource(R.string.icons_group_packs)) {
                IconsPackRow(
                    label = stringResource(R.string.icons_pack_system),
                    summary = stringResource(R.string.icons_pack_system_sub),
                    selected = state.currentIconPack == null,
                    onSelect = {
                        IconsStore.putString(context, IconsKeys.LAUNCHER_CURRENT_ICON_PACK, null)
                        update(state.copy(currentIconPack = null))
                    },
                )
                if (packs.isEmpty()) {
                    Text(
                        text = stringResource(R.string.icons_packs_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    packs.forEach { pack ->
                        IconsPackRow(
                            label = pack.label,
                            summary = pack.packageName,
                            selected = state.currentIconPack == pack.packageName,
                            onSelect = {
                                IconsStore.putString(
                                    context, IconsKeys.LAUNCHER_CURRENT_ICON_PACK, pack.packageName
                                )
                                update(state.copy(currentIconPack = pack.packageName))
                            },
                        )
                    }
                }
            }
        }
    }
}
