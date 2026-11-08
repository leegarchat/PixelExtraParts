package org.pixel.extra.parts.icons

import android.content.Context
import android.os.PowerManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pixel.extra.parts.R
import java.util.Locale

// Pack and app dialogs: partial-apply multi-select, apply-mode picker,
// pack-contents preview, completion with reboot, clear-all confirm, and the
// per-app icon chooser with detected/all sections and ranked search.
@Composable
internal fun IconsPartialApplyDialog(
    pack: IconPackInfo,
    iconMap: IconMapSnapshot,
    onDismiss: () -> Unit,
    onApply: (Set<String>) -> Unit,
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
                .toSet(),
        )
    }

    LaunchedEffect(pack.packageName) {
        entries = withContext(Dispatchers.IO) {
            PacksManager.loadIconPackSelectionEntries(context, pack.packageName)
        }
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

    IconsDialog(
        title = stringResource(R.string.icons_supported_apps),
        subtitle = pack.label,
        onDismiss = onDismiss,
        footer = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.icons_btn_cancel)) }
            Button(onClick = { onApply(selectedPackages) }) {
                Text(stringResource(R.string.icons_apply_selected), maxLines = 1)
            }
        },
    ) {
        IconsSearchField(
            query = query,
            hint = stringResource(R.string.icons_dialog_search),
            onQueryChange = { query = it },
        )
        Spacer(Modifier.height(12.dp))
        when {
            loading -> IconsLoadingContent()
            visibleEntries.isEmpty() -> IconsEmptyState(stringResource(R.string.icons_no_supported))
            else -> LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
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
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectableIconEntryRow(
    entry: IconPackEntry,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (checked) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PackBitmapIcon(entry.iconPackPackage, entry.drawableName, 42.dp)
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = entry.appPackageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
internal fun IconsApplyModeDialog(
    pack: IconPackInfo,
    onDismiss: () -> Unit,
    onApply: (IconApplyMode) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.icons_apply_mode_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = pack.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconsApplyModeButton(
                    text = stringResource(R.string.icons_apply_mode_all),
                    onClick = { onApply(IconApplyMode.ALL_PACK) },
                )
                IconsApplyModeButton(
                    text = stringResource(R.string.icons_apply_mode_system),
                    onClick = { onApply(IconApplyMode.SYSTEM_ONLY) },
                )
                IconsApplyModeButton(
                    text = stringResource(R.string.icons_apply_mode_user),
                    onClick = { onApply(IconApplyMode.USER_ONLY) },
                )
                IconsApplyModeButton(
                    text = stringResource(R.string.icons_apply_mode_installed),
                    onClick = { onApply(IconApplyMode.INSTALLED_ONLY) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.icons_btn_cancel)) }
        },
    )
}

@Composable
private fun IconsApplyModeButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun IconsApplyCompletedDialog(
    result: IconApplyResult,
    onDismiss: () -> Unit,
    onRebooted: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.icons_finished_title)) },
        text = {
            Text(
                stringResource(
                    R.string.icons_finished_msg,
                    result.applied,
                    result.removed,
                    result.skipped,
                    result.failed,
                ),
            )
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.icons_btn_ok))
            }
        },
        dismissButton = {
            Button(
                onClick = {
                    onRebooted()
                    scope.launch(Dispatchers.IO) { rebootSystem(context) }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Text(stringResource(R.string.icons_reboot))
            }
        },
    )
}

private fun rebootSystem(context: Context) {
    runCatching {
        val power = context.getSystemService(PowerManager::class.java) ?: return
        power.reboot("PixelParts icons")
    }
}

@Composable
internal fun IconsClearAllDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            androidx.compose.material3.Icon(
                Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error,
            )
        },
        title = { Text(stringResource(R.string.icons_clear_all_title)) },
        text = { Text(stringResource(R.string.icons_clear_all_msg)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text(stringResource(R.string.icons_clear_all), maxLines = 1)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.icons_btn_cancel))
            }
        },
    )
}

@Composable
internal fun IconsPackPreviewDialog(pack: IconPackInfo, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var loading by remember(pack.packageName) { mutableStateOf(true) }
    var items by remember(pack.packageName) { mutableStateOf<List<IconPackPreviewItem>>(emptyList()) }
    var query by rememberSaveable(pack.packageName) { mutableStateOf("") }

    LaunchedEffect(pack.packageName) {
        items = withContext(Dispatchers.IO) {
            PacksManager.loadPreviewItems(context, pack.packageName)
        }
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
                    .thenBy { it.label.lowercase(Locale.getDefault()) },
            )
        }
    }

    IconsDialog(
        title = stringResource(R.string.icons_pack_contents),
        subtitle = pack.label,
        onDismiss = onDismiss,
        footer = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.icons_btn_close)) } },
    ) {
        IconsSearchField(
            query = query,
            hint = stringResource(R.string.icons_dialog_search),
            onQueryChange = { query = it },
        )
        Spacer(Modifier.height(12.dp))
        when {
            loading -> IconsLoadingContent()
            visibleItems.isEmpty() -> IconsEmptyState(stringResource(R.string.icons_no_pack_icons))
            else -> {
                val rows = remember(visibleItems) { visibleItems.chunked(4) }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(rows) { rowItems ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowItems.forEach { item ->
                                IconPreviewCell(item = item, modifier = Modifier.weight(1f))
                            }
                            repeat(4 - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IconPreviewCell(item: IconPackPreviewItem, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PackBitmapIcon(item.iconPackPackage, item.drawableName, 44.dp)
        Spacer(Modifier.height(6.dp))
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private data class IconsChooserItem(
    val iconPackPackage: String,
    val iconPackLabel: String,
    val drawableName: String,
    val label: String,
    val detected: Boolean,
    val appPackageName: String?,
)

@Composable
internal fun IconsChooserDialog(
    app: InstalledIconApp,
    iconMap: IconMapSnapshot,
    iconPacks: List<IconPackInfo>,
    onDismiss: () -> Unit,
    onApply: (AppIconCandidate) -> Unit,
) {
    val context = LocalContext.current
    var loading by remember(app.packageName, iconPacks) { mutableStateOf(true) }
    var detectedItems by remember(app.packageName, iconPacks) { mutableStateOf<List<IconsChooserItem>>(emptyList()) }
    var allItems by remember(app.packageName, iconPacks) { mutableStateOf<List<IconsChooserItem>>(emptyList()) }
    var query by rememberSaveable(app.packageName) { mutableStateOf("") }

    LaunchedEffect(app.packageName, iconPacks) {
        loading = true
        val packLabels = iconPacks.associate { it.packageName to it.label }
        val detected = withContext(Dispatchers.IO) {
            PacksManager.loadCandidatesForApp(context, app.packageName)
        }.map { candidate ->
            IconsChooserItem(
                iconPackPackage = candidate.iconPackPackage,
                iconPackLabel = candidate.iconPackLabel,
                drawableName = candidate.drawableName,
                label = candidate.drawableName,
                detected = true,
                appPackageName = app.packageName,
            )
        }
        val detectedIds = detected.map { it.iconPackPackage to it.drawableName }.toSet()
        val allPackItems = withContext(Dispatchers.IO) {
            iconPacks.flatMap { pack ->
                PacksManager.loadPreviewItems(context, pack.packageName).map { item ->
                    IconsChooserItem(
                        iconPackPackage = item.iconPackPackage,
                        iconPackLabel = packLabels[item.iconPackPackage] ?: pack.label,
                        drawableName = item.drawableName,
                        label = item.label,
                        detected = false,
                        appPackageName = item.appPackageName,
                    )
                }
            }
        }.filterNot { it.iconPackPackage to it.drawableName in detectedIds }
        detectedItems = detected
        allItems = allPackItems
        loading = false
    }

    val filteredDetected = remember(detectedItems, query) { filterChooserItems(detectedItems, query) }
    val filteredAll = remember(allItems, query) { filterChooserItems(allItems, query) }
    val appliedSource = iconMap.sources[app.packageName]

    IconsDialog(
        title = stringResource(R.string.icons_choose_pack),
        subtitle = app.label,
        onDismiss = onDismiss,
        footer = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.icons_btn_close)) } },
    ) {
        IconsSearchField(
            query = query,
            hint = stringResource(R.string.icons_dialog_search),
            onQueryChange = { query = it },
        )
        Spacer(Modifier.height(12.dp))
        when {
            loading -> IconsLoadingContent()
            filteredDetected.isEmpty() && filteredAll.isEmpty() ->
                IconsEmptyState(stringResource(R.string.icons_no_pack_icons))
            else -> LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (filteredDetected.isNotEmpty()) {
                    item(key = "detected_header") {
                        Text(
                            text = stringResource(R.string.icons_detected),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    items(filteredDetected, key = { it.iconPackPackage + it.drawableName }) { item ->
                        IconsChooserRow(
                            item = item,
                            applied = appliedSource?.iconPackPackage == item.iconPackPackage &&
                                appliedSource.drawableName == item.drawableName,
                            onApply = {
                                onDismiss()
                                onApply(AppIconCandidate(item.iconPackPackage, item.iconPackLabel, item.drawableName))
                            },
                        )
                    }
                }
                if (filteredAll.isNotEmpty()) {
                    item(key = "all_header") {
                        Text(
                            text = stringResource(R.string.icons_all_pack_icons),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    items(filteredAll, key = { "all_" + it.iconPackPackage + it.drawableName }) { item ->
                        IconsChooserRow(
                            item = item,
                            applied = appliedSource?.iconPackPackage == item.iconPackPackage &&
                                appliedSource.drawableName == item.drawableName,
                            onApply = {
                                onDismiss()
                                onApply(AppIconCandidate(item.iconPackPackage, item.iconPackLabel, item.drawableName))
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun filterChooserItems(items: List<IconsChooserItem>, query: String): List<IconsChooserItem> {
    val needle = query.trim().lowercase(Locale.getDefault())
    if (needle.isEmpty()) return items
    return items.filter { item ->
        item.label.lowercase(Locale.getDefault()).contains(needle) ||
            item.iconPackLabel.lowercase(Locale.getDefault()).contains(needle) ||
            item.drawableName.lowercase(Locale.US).contains(needle) ||
            item.appPackageName.orEmpty().lowercase(Locale.US).contains(needle)
    }.sortedWith(
        compareBy<IconsChooserItem> { chooserSearchRank(it, needle) }
            .thenByDescending { it.detected }
            .thenBy { it.drawableName.lowercase(Locale.US) }
            .thenBy { it.iconPackLabel.lowercase(Locale.getDefault()) },
    )
}

private fun chooserSearchRank(item: IconsChooserItem, needle: String): Int {
    return listOf(
        searchableRank(item.drawableName, needle),
        searchableRank(item.label, needle) + 4,
        searchableRank(item.appPackageName.orEmpty(), needle) + 8,
        searchableRank(item.iconPackLabel, needle) + 12,
    ).minOrNull() ?: Int.MAX_VALUE
}

private fun previewSearchRank(item: IconPackPreviewItem, needle: String): Int {
    return listOf(
        searchableRank(item.drawableName, needle),
        searchableRank(item.label, needle) + 4,
        searchableRank(item.appPackageName.orEmpty(), needle) + 8,
    ).minOrNull() ?: Int.MAX_VALUE
}

private fun searchableRank(value: String, needle: String): Int {
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
private fun IconsChooserRow(
    item: IconsChooserItem,
    applied: Boolean,
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
            PackBitmapIcon(item.iconPackPackage, item.drawableName, 42.dp)
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.iconPackLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Button(
                onClick = onApply,
                enabled = !applied,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
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

internal fun startIconsOperation(context: Context, start: () -> Boolean, onStarted: () -> Unit) {
    if (!start()) {
        android.widget.Toast.makeText(
            context,
            context.getString(R.string.icons_operation_running),
            android.widget.Toast.LENGTH_SHORT,
        ).show()
    } else {
        onStarted()
    }
}
