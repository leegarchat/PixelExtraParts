package org.pixel.extra.parts.hiddenapps

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlinx.coroutines.launch
import org.pixel.extra.parts.R
import org.pixel.extra.parts.ui.edgeOutline
import org.pixel.extra.parts.ui.sectionContentPadding

// Launcher hide list: launchable packages plus already-hidden ones, with a
// search field, a system-apps filter and per-row hide switches. Hidden state is
// applied through HiddenAppsRepository (PackageManager hidden setting first,
// root `pm hide/unhide` as fallback); a launcher restart applies the change.
@Composable
fun HiddenAppsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    var isLoading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var reloadToken by remember { mutableStateOf(0) }
    var apps by remember { mutableStateOf<List<HiddenAppEntry>>(emptyList()) }
    var hidden by remember { mutableStateOf<Set<String>>(emptySet()) }
    var busy by remember { mutableStateOf<Set<String>>(emptySet()) }
    var needsRestart by remember { mutableStateOf(false) }
    var query by rememberSaveable(key = HiddenAppsKeys.SAVE_QUERY) { mutableStateOf("") }
    var showSystem by rememberSaveable(key = HiddenAppsKeys.SAVE_SHOW_SYSTEM) { mutableStateOf(true) }

    LaunchedEffect(reloadToken) {
        isLoading = true
        loadFailed = false
        val result = HiddenAppsRepository.loadAll(context)
        apps = result.apps
        hidden = result.hidden
        loadFailed = result.apps.isEmpty()
        isLoading = false
    }

    val visibleApps = remember(apps, showSystem, query) {
        val needle = query.trim().lowercase(Locale.getDefault())
        val scoped = if (showSystem) apps else apps.filterNot { it.isSystem }
        if (needle.isEmpty()) {
            scoped
        } else {
            scoped.filter {
                it.label.lowercase(Locale.getDefault()).contains(needle) ||
                    it.packageName.lowercase(Locale.US).contains(needle)
            }
        }
    }

    fun toggle(packageName: String, hide: Boolean) {
        if (busy.contains(packageName)) return
        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
        hidden = if (hide) hidden + packageName else hidden - packageName
        busy = busy + packageName
        scope.launch {
            val applied = HiddenAppsRepository.setHidden(context, packageName, hide)
            busy = busy - packageName
            if (applied) {
                needsRestart = true
            } else {
                hidden = if (hide) hidden - packageName else hidden + packageName
                Toast.makeText(
                    context,
                    context.getString(R.string.hidden_apps_apply_error),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = sectionContentPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "__hidden_apps_header__") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.tab_hidden_apps),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.hidden_apps_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (needsRestart) {
            item(key = "__hidden_apps_restart__") {
                RestartHintCard(
                    onRestart = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        scope.launch {
                            if (HiddenAppsRepository.restartLauncher()) {
                                needsRestart = false
                            } else {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.hidden_apps_apply_error),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    },
                )
            }
        }

        item(key = "__hidden_apps_controls__") {
            ControlsCard(
                query = query,
                onQueryChange = { query = it },
                showSystem = showSystem,
                onShowSystemChange = {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    showSystem = it
                },
                hiddenCount = hidden.size,
            )
        }

        when {
            isLoading -> {
                item(key = "__hidden_apps_loading__") {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.surfaceContainerLow.edgeOutline(),
                                shape = RoundedCornerShape(20.dp),
                            ),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(148.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }

            loadFailed -> {
                item(key = "__hidden_apps_error__") {
                    MessageCard(
                        text = stringResource(R.string.hidden_apps_error),
                        actionText = stringResource(R.string.hidden_apps_retry),
                        onAction = {
                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                            reloadToken += 1
                        },
                    )
                }
            }

            visibleApps.isEmpty() -> {
                item(key = "__hidden_apps_empty__") {
                    MessageCard(
                        text = stringResource(R.string.hidden_apps_empty),
                        actionText = null,
                        onAction = {},
                    )
                }
            }

            else -> {
                items(visibleApps, key = { it.packageName }) { app ->
                    val isHidden = hidden.contains(app.packageName)
                    AppHideRow(
                        app = app,
                        checked = isHidden,
                        enabled = !busy.contains(app.packageName),
                        onToggle = { toggle(app.packageName, !isHidden) },
                    )
                }
            }
        }
    }
}

// Warning card with a wide restart action; shown after a successful change.
@Composable
private fun RestartHintCard(onRestart: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val container = MaterialTheme.colorScheme.errorContainer
    Surface(
        color = container,
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = container.edgeOutline(), shape = shape),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.hidden_apps_restart_hint),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Surface(
                color = MaterialTheme.colorScheme.error,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.error.edgeOutline(),
                        shape = RoundedCornerShape(14.dp),
                    )
                    .clickable(role = Role.Button, onClick = onRestart),
            ) {
                Text(
                    text = stringResource(R.string.hidden_apps_restart),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                )
            }
        }
    }
}

// Level-1 group card: search field, divider, system-apps switch, hidden count.
@Composable
private fun ControlsCard(
    query: String,
    onQueryChange: (String) -> Unit,
    showSystem: Boolean,
    onShowSystemChange: (Boolean) -> Unit,
    hiddenCount: Int,
) {
    val shape = RoundedCornerShape(20.dp)
    val container = MaterialTheme.colorScheme.surfaceContainerLow
    Surface(
        color = container,
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = container.edgeOutline(), shape = shape),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SearchField(query = query, onQueryChange = onQueryChange)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(role = Role.Switch) { onShowSystemChange(!showSystem) }
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.hidden_apps_show_system),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.hidden_apps_show_system_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = showSystem,
                    onCheckedChange = null,
                )
            }
            Text(
                text = stringResource(R.string.hidden_apps_hidden_count, hiddenCount),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// Compact search surface with a leading icon and a clear button.
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(14.dp)
    val container = MaterialTheme.colorScheme.surfaceContainerHighest
    Surface(
        color = container,
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = container.edgeOutline(), shape = shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.hidden_apps_search_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onQueryChange("")
                    },
                    modifier = Modifier.size(30.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.hidden_apps_clear_search),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

// One app row: icon, label/package with an optional system badge, hide switch.
@Composable
private fun AppHideRow(
    app: HiddenAppEntry,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val container = MaterialTheme.colorScheme.surfaceContainerLow
    Surface(
        color = container,
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = container.edgeOutline(), shape = shape)
            .clip(shape)
            .clickable(role = Role.Switch, enabled = enabled, onClick = onToggle),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (app.icon != null) {
                Image(
                    bitmap = app.icon,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_archive),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (app.isSystem) {
                        SystemBadge()
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
            )
        }
    }
}

// Small capsule marking system packages.
@Composable
private fun SystemBadge() {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = CircleShape,
    ) {
        Text(
            text = stringResource(R.string.hidden_apps_system_badge),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

// Centered message card with an optional retry action.
@Composable
private fun MessageCard(
    text: String,
    actionText: String?,
    onAction: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val container = MaterialTheme.colorScheme.surfaceContainerLow
    Surface(
        color = container,
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = container.edgeOutline(), shape = shape),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (actionText != null) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.secondaryContainer.edgeOutline(),
                            shape = RoundedCornerShape(14.dp),
                        )
                        .clickable(role = Role.Button, onClick = onAction),
                ) {
                    Text(
                        text = actionText,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}
