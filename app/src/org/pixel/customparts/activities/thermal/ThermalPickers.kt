package org.pixel.customparts.activities

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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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
import org.pixel.customparts.R
import org.pixel.customparts.dynamicDarkColorScheme
import org.pixel.customparts.dynamicLightColorScheme
import org.pixel.customparts.services.ThermalManagerTileService
import org.pixel.customparts.ui.REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING
import org.pixel.customparts.ui.RebootBubble
import org.pixel.customparts.ui.RebootBubbleMenuAction
import org.pixel.customparts.ui.SettingsGroupCard
import org.pixel.customparts.ui.TopBarBlurOverlay
import org.pixel.customparts.ui.TopStatusVeil
import org.pixel.customparts.ui.recordLayer
import org.pixel.customparts.ui.rememberGraphicsLayerRecordingState
import org.pixel.customparts.utils.ThermalConfigChoice
import org.pixel.customparts.utils.ThermalProfileController
import org.pixel.customparts.utils.ThermalProfileMap
import org.pixel.customparts.utils.TileUtils
import java.io.File
import java.text.DecimalFormat
import java.text.Normalizer
import java.util.Locale
import androidx.compose.ui.res.stringResource

private const val VENDOR_THERMAL_CONFIG = "/vendor/etc/thermal_info_config.json"
private const val STOCK_THERMAL_CONFIG_FILE_NAME = "thermal_info_config.json"
private const val RESERVED_CUSTOM_SUFFIX = "_custom"
private const val EXTRA_OPEN_CREATE_PROFILE = "org.pixel.customparts.extra.OPEN_THERMAL_PROFILE_CREATOR"

private val FILE_NAME_TRANSLIT = mapOf(
    'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e",
    'ё' to "e", 'ж' to "zh", 'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k",
    'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r",
    'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f", 'х' to "h", 'ц' to "c",
    'ч' to "ch", 'ш' to "sh", 'щ' to "sch", 'ъ' to "", 'ы' to "y", 'ь' to "",
    'э' to "e", 'ю' to "yu", 'я' to "ya", 'і' to "i", 'ї' to "yi", 'є' to "ye",
    'ґ' to "g"
)

@Composable
internal fun ThermalSystemAppsFilterRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.surface.thermalEdgeOutline(),
                RoundedCornerShape(18.dp)
            )
            .clickable { onCheckedChange(!checked) },
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
                    text = stringResource(R.string.thermal_manager_show_system_title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.thermal_manager_show_system_summary),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
    }
}

@Composable
internal fun ThermalCompactSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    hint: String
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.surface.thermalEdgeOutline(),
                RoundedCornerShape(18.dp)
            ),
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
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.btn_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun ThermalManagedAppRow(
    app: ThermalManagedApp,
    selectedConfig: String,
    globalConfig: String,
    choicesById: Map<String, ThermalConfigChoice>,
    onClick: () -> Unit
) {
    val globalChoice = choiceForConfig(
        globalConfig.ifBlank { ThermalProfileController.STOCK_CONFIG_ID },
        choicesById,
        includeFollowGlobal = false
    )
    val selectedChoice = if (selectedConfig.isBlank()) {
        globalChoice
    } else {
        choiceForConfig(selectedConfig, choicesById, includeFollowGlobal = true)
    }
    val isGlobalSelection = selectedConfig.isBlank()
    val targetContainerColor = if (selectedConfig.isNotBlank()) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
    } else if (app.isSystem) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.07f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val containerColor by animateColorAsState(
        targetValue = targetContainerColor,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioNoBouncy
        ),
        label = "appRowBg"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, containerColor.thermalEdgeOutline(), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (app.icon != null) {
                        Image(
                            bitmap = app.icon.toBitmap().asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    if (app.isSystem) {
                        ThermalSystemAppBadge()
                        Spacer(Modifier.width(6.dp))
                    }

                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                }
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProfileTypeBadge(selectedChoice)
                    Spacer(Modifier.width(6.dp))
                    SelectionTypeBadge(isGlobalSelection = isGlobalSelection)
                }
                Text(
                    text = profileChoiceTitle(selectedChoice),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
    }
}

@Composable
private fun ThermalSystemAppBadge() {
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.38f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.launcher_hidden_apps_system_badge),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun SelectionTypeBadge(isGlobalSelection: Boolean) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(
                if (isGlobalSelection) MaterialTheme.colorScheme.tertiaryContainer
                else MaterialTheme.colorScheme.primaryContainer
            )
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isGlobalSelection) {
                stringResource(R.string.thermal_manager_selection_global)
            } else {
                stringResource(R.string.thermal_manager_selection_selected)
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (isGlobalSelection) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

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
private fun ProfilePickerChoiceRow(
    choice: ThermalConfigChoice,
    selected: Boolean,
    onClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val targetContainerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val containerColor by animateColorAsState(
        targetValue = targetContainerColor,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioNoBouncy
        ),
        label = "pickerRowBg"
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, containerColor.thermalEdgeOutline(), RoundedCornerShape(14.dp))
            .clickable(onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            }),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
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

private fun selectedProfileChoiceId(selectedConfig: String, includeFollowGlobal: Boolean): String {
    val normalized = ThermalProfileController.normalizeConfigId(selectedConfig)
    return when {
        includeFollowGlobal && normalized.isBlank() -> ThermalProfileController.FOLLOW_GLOBAL_ID
        normalized.isBlank() -> ThermalProfileController.STOCK_CONFIG_ID
        else -> normalized.substringAfterLast('/')
    }
}

private fun orderedProfileChoices(
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

private fun isSystemProfileChoice(choice: ThermalConfigChoice): Boolean {
    return choice.builtIn || choice.source == ThermalProfileController.PROFILE_SOURCE_SYSTEM
}

private fun profileChoiceSearchText(context: Context, choice: ThermalConfigChoice): String {
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
