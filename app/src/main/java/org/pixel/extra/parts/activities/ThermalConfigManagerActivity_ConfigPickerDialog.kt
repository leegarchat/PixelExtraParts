package org.pixel.extra.parts.activities

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.pixel.extra.parts.R
import org.pixel.extra.parts.dynamicDarkColorScheme
import org.pixel.extra.parts.dynamicLightColorScheme
import org.pixel.extra.parts.services.ThermalManagerTileService
import org.pixel.extra.parts.ui.legacy.REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING
import org.pixel.extra.parts.ui.legacy.RebootBubble
import org.pixel.extra.parts.ui.legacy.RebootBubbleMenuAction
import org.pixel.extra.parts.ui.legacy.SettingsGroupCard
import org.pixel.extra.parts.ui.TopBarBlurOverlay
import org.pixel.extra.parts.ui.recordLayer
import org.pixel.extra.parts.ui.rememberGraphicsLayerRecordingState
import org.pixel.extra.parts.utils.ThermalConfigChoice
import org.pixel.extra.parts.utils.ThermalProfileController
import org.pixel.extra.parts.utils.ThermalProfileMap
import org.pixel.extra.parts.utils.TileUtils
import java.io.File
import java.text.DecimalFormat
import java.text.Normalizer
import java.util.Locale
import androidx.compose.ui.res.stringResource


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConfigPickerDialog(
    title: String,
    selectedConfig: String,
    includeFollowGlobal: Boolean,
    choices: List<ThermalConfigChoice>,
    onDismiss: () -> Unit,
    onSelect: (ThermalConfigChoice) -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val selectedChoiceId = selectedProfileChoiceId(selectedConfig, includeFollowGlobal)
    val orderedChoices = remember(choices, selectedConfig, includeFollowGlobal) {
        orderedProfileChoices(choices, selectedConfig, includeFollowGlobal)
    }
    val filteredChoices = remember(orderedChoices, searchQuery, context) {
        val tokens = searchQuery.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) {
            orderedChoices
        } else {
            orderedChoices.filter { choice ->
                val haystack = profileChoiceSearchText(context, choice)
                tokens.all { token -> haystack.contains(token) }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentWindowInsets = WindowInsets.navigationBars,
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = title,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Rounded.Close, stringResource(R.string.btn_close))
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            scrolledContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            ) { innerPadding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = innerPadding.calculateTopPadding() + 12.dp,
                        end = 16.dp,
                        bottom = innerPadding.calculateBottomPadding() + REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        ThermalCompactSearchField(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            hint = stringResource(R.string.thermal_manager_profile_search_hint)
                        )
                    }

                    if (filteredChoices.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.thermal_manager_profile_search_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp)
                            )
                        }
                    } else {
                        items(filteredChoices, key = { it.id }) { choice ->
                            ProfilePickerChoiceRow(
                                choice = choice,
                                selected = choice.id == selectedChoiceId,
                                onClick = { onSelect(choice) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ProfilePickerChoiceRow(
    choice: ThermalConfigChoice,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = profileChoiceTitle(choice),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                ProfileTypeBadge(choice)
                choice.fileName?.takeIf { it.isNotBlank() }?.let { fileName ->
                    Text(
                        text = fileName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

internal fun selectedProfileChoiceId(selectedConfig: String, includeFollowGlobal: Boolean): String {
    val normalized = ThermalProfileController.normalizeConfigId(selectedConfig)
    return when {
        includeFollowGlobal && normalized.isBlank() -> ThermalProfileController.FOLLOW_GLOBAL_ID
        normalized.isBlank() -> ThermalProfileController.STOCK_CONFIG_ID
        else -> normalized.substringAfterLast('/')
    }
}

internal fun orderedProfileChoices(
    choices: List<ThermalConfigChoice>,
    selectedConfig: String,
    includeFollowGlobal: Boolean
): List<ThermalConfigChoice> {
    val selectedId = selectedProfileChoiceId(selectedConfig, includeFollowGlobal)
    val usedIds = mutableSetOf<String>()
    val ordered = mutableListOf<ThermalConfigChoice>()

    fun addChoice(choice: ThermalConfigChoice?) {
        if (choice != null && usedIds.add(choice.id)) {
            ordered += choice
        }
    }

    addChoice(choices.firstOrNull { it.id == selectedId })

    if (includeFollowGlobal) {
        addChoice(choices.firstOrNull { it.id == ThermalProfileController.FOLLOW_GLOBAL_ID })
    }

    addChoice(choices.firstOrNull { it.id == ThermalProfileController.STOCK_CONFIG_ID })

    choices
        .filterNot { it.id in usedIds }
        .filterNot { isSystemProfileChoice(it) }
        .sortedWith(compareBy<ThermalConfigChoice> { it.label.lowercase(Locale.ROOT) }.thenBy { it.fileName.orEmpty() })
        .forEach(::addChoice)

    choices
        .filterNot { it.id in usedIds }
        .filter { isSystemProfileChoice(it) }
        .sortedWith(compareBy<ThermalConfigChoice> { it.label.lowercase(Locale.ROOT) }.thenBy { it.fileName.orEmpty() })
        .forEach(::addChoice)

    return ordered
}

internal fun isSystemProfileChoice(choice: ThermalConfigChoice): Boolean {
    return choice.builtIn || choice.source == ThermalProfileController.PROFILE_SOURCE_SYSTEM
}

internal fun profileChoiceSearchText(context: Context, choice: ThermalConfigChoice): String {
    val title = when (choice.id) {
        ThermalProfileController.FOLLOW_GLOBAL_ID -> context.getString(
            R.string.thermal_manager_follow_global
        )
        ThermalProfileController.STOCK_CONFIG_ID -> context.getString(
            R.string.thermal_manager_stock_profile
        )
        else -> choice.label
    }
    val source = if (isSystemProfileChoice(choice)) {
        context.getString(R.string.thermal_manager_profile_system)
    } else {
        context.getString(R.string.thermal_manager_profile_user)
    }
    return listOfNotNull(
        choice.id,
        title,
        choice.label,
        choice.fileName,
        choice.propertyValue,
        source,
        if (isSystemProfileChoice(choice)) "system" else "user"
    ).joinToString(" ").lowercase(Locale.ROOT)
}

@Composable
internal fun profileChoiceTitle(choice: ThermalConfigChoice): String {
    return when (choice.id) {
        ThermalProfileController.FOLLOW_GLOBAL_ID -> stringResource(R.string.thermal_manager_follow_global)
        ThermalProfileController.STOCK_CONFIG_ID -> stringResource(R.string.thermal_manager_stock_profile)
        else -> choice.label
    }
}

internal fun choiceForConfig(
    configId: String,
    choicesById: Map<String, ThermalConfigChoice>,
    includeFollowGlobal: Boolean
): ThermalConfigChoice {
    val normalized = ThermalProfileController.normalizeConfigId(configId)
    val id = when {
        includeFollowGlobal && normalized.isBlank() -> ThermalProfileController.FOLLOW_GLOBAL_ID
        normalized.isBlank() -> ThermalProfileController.STOCK_CONFIG_ID
        else -> normalized.substringAfterLast('/')
    }
    choicesById[id]?.let { return it }

    return when {
        id == ThermalProfileController.FOLLOW_GLOBAL_ID -> ThermalConfigChoice(
            id = id,
            label = id,
            fileName = null,
            propertyValue = null,
            builtIn = true,
            source = ThermalProfileController.PROFILE_SOURCE_SYSTEM
        )
        id == ThermalProfileController.STOCK_CONFIG_ID -> ThermalConfigChoice(
            id = id,
            label = id,
            fileName = "thermal_info_config.json",
            propertyValue = "thermal_info_config.json",
            builtIn = true,
            source = ThermalProfileController.PROFILE_SOURCE_SYSTEM
        )
        else -> ThermalConfigChoice(
            id = id,
            label = ThermalProfileController.displayName(id),
            fileName = id,
            propertyValue = null,
            builtIn = false,
            source = ThermalProfileController.PROFILE_SOURCE_USER
        )
    }
}

@Composable
internal fun EditorHeaderCard(
    source: String,
    profileName: String,
    onProfileNameChange: (String) -> Unit,
    fileNameInput: String,
    onFileNameInputChange: (String) -> Unit,
    selectedTab: ThermalEditorTab,
    onTabSelected: (ThermalEditorTab) -> Unit,
    onSave: () -> Unit,
    onSaveAndApply: () -> Unit
) {
    SettingsGroupCard(title = stringResource(R.string.thermal_manager_editor_title)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.thermal_manager_source, source),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = profileName,
                onValueChange = onProfileNameChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.thermal_manager_profile_name)) }
            )
            OutlinedTextField(
                value = fileNameInput,
                onValueChange = onFileNameInputChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.thermal_manager_file_name_optional)) },
                supportingText = { Text(stringResource(R.string.thermal_manager_file_name_help)) }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ThermalEditorTab.values().forEach { tab ->
                    val selected = tab == selectedTab
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onTabSelected(tab) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(tab.labelRes),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onSave,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.thermal_manager_save),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Button(
                    onClick = onSaveAndApply,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.thermal_manager_save_apply),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
internal fun ThermalParamTreeCard(
    branches: List<ThermalParamTreeNode>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    visibleCount: Int,
    totalCount: Int,
    expandedNodeIds: Set<String>,
    forceExpanded: Boolean,
    onToggleNode: (String) -> Unit,
    onParamClick: (ThermalConfigParam) -> Unit,
    onInfoClick: (ThermalConfigParam) -> Unit
) {
    SettingsGroupCard(title = stringResource(R.string.thermal_manager_editor_tree_title)) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThermalCompactSearchField(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                hint = stringResource(R.string.thermal_manager_search_hint)
            )
            Text(
                text = stringResource(R.string.thermal_manager_search_count, visibleCount, totalCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        if (branches.isEmpty()) {
            Text(
                text = stringResource(R.string.thermal_manager_editor_tree_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        } else {
            Column(modifier = Modifier.padding(bottom = 6.dp)) {
                branches.forEach { node ->
                    ThermalTreeNodeRow(
                        node = node,
                        depth = 0,
                        expandedNodeIds = expandedNodeIds,
                        forceExpanded = forceExpanded,
                        onToggleNode = onToggleNode,
                        onParamClick = onParamClick,
                        onInfoClick = onInfoClick
                    )
                }
            }
        }
    }
}

@Composable
internal fun ThermalTreeNodeRow(
    node: ThermalParamTreeNode,
    depth: Int,
    expandedNodeIds: Set<String>,
    forceExpanded: Boolean,
    onToggleNode: (String) -> Unit,
    onParamClick: (ThermalConfigParam) -> Unit,
    onInfoClick: (ThermalConfigParam) -> Unit
) {
    val expanded = forceExpanded || node.id in expandedNodeIds
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleNode(node.id) }
                .padding(start = (12 + depth * 14).dp, end = 10.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = node.title,
                style = if (depth == 0) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                color = if (depth == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = node.leafCount.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.fillMaxWidth()) {
                node.children.forEach { child ->
                    ThermalTreeNodeRow(
                        node = child,
                        depth = depth + 1,
                        expandedNodeIds = expandedNodeIds,
                        forceExpanded = forceExpanded,
                        onToggleNode = onToggleNode,
                        onParamClick = onParamClick,
                        onInfoClick = onInfoClick
                    )
                }
                node.params.forEach { param ->
                    ThermalTreeParamRow(
                        param = param,
                        depth = depth + 1,
                        onClick = { onParamClick(param) },
                        onInfoClick = { onInfoClick(param) }
                    )
                }
            }
        }
    }
}

@Composable
internal fun ThermalTreeParamRow(
    param: ThermalConfigParam,
    depth: Int,
    onClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = (20 + depth * 14).dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(end = 6.dp)
                .size(16.dp)
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = param.key,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(6.dp))
                FieldBadge(param)
            }
            Text(
                text = param.value.previewValue(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onInfoClick, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Rounded.Info,
                contentDescription = stringResource(R.string.thermal_manager_info),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
internal fun ThermalParamEditDialog(
    param: ThermalConfigParam,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var draftValue by remember(param.id, param.value) { mutableStateOf(param.value) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
        title = {
            Text(
                text = param.key,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = param.help,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = param.displayPath,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FieldBadge(param)
                if (param.kind == ThermalValueKind.BOOLEAN) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = draftValue,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Switch(
                            checked = draftValue.equals("true", ignoreCase = true),
                            onCheckedChange = { draftValue = it.toString() }
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = draftValue,
                        onValueChange = { draftValue = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = if (param.kind == ThermalValueKind.ARRAY) 132.dp else 56.dp),
                        singleLine = param.kind != ThermalValueKind.ARRAY,
                        minLines = if (param.kind == ThermalValueKind.ARRAY) 5 else 1,
                        maxLines = if (param.kind == ThermalValueKind.ARRAY) 12 else 1,
                        label = { Text(param.valueLabel) }
                    )
                }
            }
        },
        confirmButton = {
            IconButton(onClick = { onSave(draftValue) }) {
                Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.btn_save))
            }
        },
        dismissButton = {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.btn_cancel))
            }
        }
    )
}

@Composable
internal fun FieldBadge(param: ThermalConfigParam) {
    val color = when {
        param.throttlingField -> MaterialTheme.colorScheme.errorContainer
        param.knownField -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val textColor = when {
        param.throttlingField -> MaterialTheme.colorScheme.onErrorContainer
        param.knownField -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = color,
        contentColor = textColor,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = if (param.throttlingField) {
                stringResource(R.string.thermal_manager_badge_throttle)
            } else if (param.knownField) {
                stringResource(R.string.thermal_manager_badge_known)
            } else {
                stringResource(R.string.thermal_manager_badge_unknown)
            },
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}

internal class ThermalConfigStore(private val context: Context) {
    fun readVendorConfig(): String = loadConfigWithIncludes(File(VENDOR_THERMAL_CONFIG), mutableSetOf()).toString(4)

    fun listSavedConfigs(): List<SavedThermalConfig> {
        val dir = ensureConfigDir()
        return dir.listFiles { file -> file.isFile && file.name.endsWith(".json", ignoreCase = true) }
            ?.filterNot {
                it.name == ThermalProfileController.MAP_FILE_NAME ||
                    it.name == ThermalProfileController.PROFILE_METADATA_FILE_NAME ||
                    it.name.equals(STOCK_THERMAL_CONFIG_FILE_NAME, ignoreCase = true)
            }
            ?.sortedByDescending { it.lastModified() }
            ?.map { file ->
                val metadata = ThermalProfileController.profileMetadata(file.name)
                val source = metadata?.source ?: if (ThermalProfileController.isVendorPreset(file.name)) {
                    ThermalProfileController.PROFILE_SOURCE_SYSTEM
                } else {
                    ThermalProfileController.PROFILE_SOURCE_USER
                }
                SavedThermalConfig(
                    name = file.name,
                    displayName = metadata?.displayName?.takeIf { it.isNotBlank() } ?: file.name.removeSuffix(".json"),
                    source = source,
                    file = file,
                    sizeText = formatBytes(file.length()),
                    locked = source == ThermalProfileController.PROFILE_SOURCE_SYSTEM
                )
            }
            .orEmpty()
    }

    fun saveConfig(fileName: String, json: String, displayName: String): File {
        val safeFileName = avoidReservedCustomConfigName(fileName)
        if (ThermalProfileController.isVendorPreset(safeFileName)) {
            error(context.getString(R.string.thermal_manager_vendor_read_only))
        }

        val dir = ensureConfigDir()
        val file = File(dir, safeFileName)
        writeTextAtomically(file, json)
        file.setReadable(true, false)
        file.setWritable(true, true)
        ThermalProfileController.writeUserProfileMetadata(file.name, displayName, file.absolutePath)
        return file
    }

    fun readUri(uri: Uri): String = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        ?: error(context.getString(R.string.thermal_manager_error_read_selected_file))

    fun writeUri(uri: Uri, text: String) {
        context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(text) }
            ?: error(context.getString(R.string.thermal_manager_error_open_export_target))
    }

    fun displayName(uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index)
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun ensureConfigDir(): File {
        val dir = File(ThermalProfileController.CONFIG_DIR)
        if (!dir.exists() && !dir.mkdirs()) {
            error(context.getString(R.string.thermal_manager_error_create_dir, ThermalProfileController.CONFIG_DIR))
        }
        dir.setReadable(true, false)
        dir.setExecutable(true, false)
        return dir
    }

    private fun writeTextAtomically(file: File, text: String) {
        val temp = File(file.parentFile, ".${file.name}.${System.nanoTime()}.tmp")
        temp.writeText(text)
        temp.setReadable(true, false)
        temp.setWritable(true, true)
        if (!temp.renameTo(file)) {
            temp.delete()
            error(context.getString(R.string.thermal_manager_error_save, file.name))
        }
    }

    private fun loadConfigWithIncludes(file: File, loadedPaths: MutableSet<String>): JSONObject {
        val normalizedPath = file.absolutePath
        if (!loadedPaths.add(normalizedPath)) {
            error(context.getString(R.string.thermal_manager_error_circular_include, normalizedPath))
        }

        val root = parseThermalJsonObject(context, file.readText())
        val includes = root.optJSONArray("Include") ?: return root
        for (index in 0 until includes.length()) {
            val includeName = includes.optString(index).takeIf { it.isNotBlank() } ?: continue
            val includeFile = if (includeName.startsWith('/')) {
                File(includeName)
            } else {
                File(File(VENDOR_THERMAL_CONFIG).parentFile, includeName)
            }
            val included = loadConfigWithIncludes(includeFile, loadedPaths)
            mergeArrayByName(root, included, "Sensors")
            mergeArrayByName(root, included, "CoolingDevices")
            mergeArrayByName(root, included, "PowerRails")
            mergeStats(root, included)
            mergeLogInfo(root, included)
        }
        return root
    }
}

internal fun parseThermalJsonObject(context: Context, text: String): JSONObject {
    val parsed = parseThermalJsonValue(text)
    return parsed as? JSONObject ?: error(context.getString(R.string.thermal_manager_error_root_json_object))
}

internal fun parseThermalJsonValue(text: String): Any {
    return JSONTokener(cleanThermalJson(text)).nextValue()
}

internal fun cleanThermalJson(text: String): String {
    return removeTrailingJsonCommas(insertMissingJsonArrayCommas(removeJsonComments(text)))
}