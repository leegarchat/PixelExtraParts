package org.pixel.extra.parts.overscroll

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.pixel.extra.parts.R
import org.pixel.extra.parts.ui.ExpandableGroupCard
import org.pixel.extra.parts.ui.SettingSlider
import org.pixel.extra.parts.ui.SettingSwitchRow
import org.pixel.extra.parts.ui.SettingSwitchRowVariant

// Per-package overrides: one row per app with filter/ignore switches and an
// effect-scale slider, plus an add-app picker dialog. Every edit is written
// through OverscrollStore immediately; the hook re-reads the serialized
// config on the next gesture.
@Composable
fun OverscrollAppsSection(
    apps: List<OverscrollAppConfig>,
    masterEnabled: Boolean,
    onAdd: (String) -> Unit,
    onUpdate: (OverscrollAppConfig) -> Unit,
    onDelete: (OverscrollAppConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }
    val iconCache = remember { mutableStateMapOf<String, ImageBitmap?>() }

    ExpandableGroupCard(
        title = stringResource(R.string.os_apps_title),
        chevronPainter = painterResource(R.drawable.ic_chevron_down),
        subtitle = stringResource(R.string.os_apps_empty).takeIf { apps.isEmpty() },
        keyTag = "overscroll_apps",
        modifier = modifier.alpha(if (masterEnabled) 1f else 0.5f),
    ) {
        Button(
            onClick = { showPicker = true },
            enabled = masterEnabled,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Text(text = stringResource(R.string.os_app_add))
        }
        apps.forEachIndexed { index, app ->
            if (index > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
            AppOverrideRow(
                item = app,
                iconCache = iconCache,
                enabled = masterEnabled,
                onUpdate = onUpdate,
                onDelete = { onDelete(app) },
            )
        }
    }
    if (showPicker) {
        AppPickerDialog(
            onDismiss = { showPicker = false },
            onPick = {
                showPicker = false
                onAdd(it)
            },
        )
    }
}

@Composable
private fun AppOverrideRow(
    item: OverscrollAppConfig,
    iconCache: MutableMap<String, ImageBitmap?>,
    enabled: Boolean,
    onUpdate: (OverscrollAppConfig) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var label by remember(item.pkg) { mutableStateOf(item.pkg) }

    LaunchedEffect(item.pkg) {
        if (!iconCache.containsKey(item.pkg)) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val pm = context.packageManager
                    val info = pm.getApplicationInfo(item.pkg, 0)
                    label = pm.getApplicationLabel(info)?.toString()?.takeIf { it.isNotBlank() } ?: item.pkg
                    iconCache[item.pkg] = pm.getApplicationIcon(info).toBitmap(96, 96).asImageBitmap()
                }
            }
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    val pm = context.packageManager
                    val info = pm.getApplicationInfo(item.pkg, 0)
                    label = pm.getApplicationLabel(info)?.toString()?.takeIf { it.isNotBlank() } ?: item.pkg
                }
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val icon = iconCache[item.pkg]
            if (icon != null) {
                Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(40.dp))
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.pkg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDelete()
                },
                enabled = enabled,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.os_app_remove),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        SettingSwitchRow(
            title = stringResource(R.string.os_app_filter),
            summary = null,
            checked = item.filter,
            onCheckedChange = { onUpdate(item.copy(filter = it)) },
            enabled = enabled,
            variant = SettingSwitchRowVariant.Card,
        )
        SettingSwitchRow(
            title = stringResource(R.string.os_app_ignore),
            summary = null,
            checked = item.ignore,
            onCheckedChange = { onUpdate(item.copy(ignore = it)) },
            enabled = enabled,
            variant = SettingSwitchRowVariant.Card,
        )
        SettingSlider(
            title = stringResource(R.string.os_app_scale),
            value = item.scale,
            onValueChange = { onUpdate(item.copy(scale = it)) },
            valueRange = 0f..6f,
            step = 0.1f,
            defaultValue = 1f,
            resetLabel = stringResource(R.string.os_reset_label),
            enabled = enabled,
            valueLabel = { String.format(Locale.US, "%.1f", it) },
        )
    }
}

private data class PickableApp(val label: String, val pkg: String)

// Installed-package picker: label search over every installed package (the
// hook matches overrides by package name, so non-launchable packages stay
// selectable). Icons resolve per visible row with a dialog-level cache.
@Composable
private fun AppPickerDialog(
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    var query by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<PickableApp>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val iconCache = remember { mutableStateMapOf<String, ImageBitmap?>() }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            @Suppress("DEPRECATION")
            val installed = runCatching {
                pm.getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES)
            }.getOrDefault(emptyList())
            val items = installed.map {
                PickableApp(
                    label = runCatching { it.loadLabel(pm)?.toString() }.getOrNull()
                        ?.takeIf { name -> name.isNotBlank() } ?: it.packageName,
                    pkg = it.packageName,
                )
            }.sortedBy { it.label.lowercase(Locale.getDefault()) }
            apps = items
            loading = false
        }
    }

    val filtered = remember(query, apps) {
        val needle = query.trim()
        if (needle.isBlank()) {
            apps
        } else {
            apps.filter {
                it.label.contains(needle, ignoreCase = true) ||
                    it.pkg.contains(needle, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(horizontal = 24.dp),
        shape = RoundedCornerShape(22.dp),
        title = {
            Text(
                text = stringResource(R.string.os_app_select_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(text = stringResource(R.string.os_app_search_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (loading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(filtered, key = { it.pkg }) { app ->
                            AppPickerRow(
                                context = context,
                                app = app,
                                iconCache = iconCache,
                                onPick = { onPick(app.pkg) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.os_action_cancel))
            }
        },
    )
}

@Composable
private fun AppPickerRow(
    context: Context,
    app: PickableApp,
    iconCache: MutableMap<String, ImageBitmap?>,
    onPick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(app.pkg) {
        if (!iconCache.containsKey(app.pkg)) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val pm = context.packageManager
                    val info = pm.getApplicationInfo(app.pkg, 0)
                    iconCache[app.pkg] = pm.getApplicationIcon(info).toBitmap(96, 96).asImageBitmap()
                }
            }
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onPick()
                },
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = iconCache[app.pkg]
        if (icon != null) {
            Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(40.dp))
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = app.pkg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
