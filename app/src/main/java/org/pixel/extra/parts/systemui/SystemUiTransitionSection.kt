package org.pixel.extra.parts.systemui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.pixel.extra.parts.R
import org.pixel.extra.parts.ui.ExpandableGroupCard
import org.pixel.extra.parts.ui.GroupCard
import org.pixel.extra.parts.ui.SettingSwitchRow
import org.pixel.extra.parts.ui.SettingSwitchRowVariant

// Hub group for activity transitions: current-status card with a disable-all
// reset, predictive-back toggle, the full 33-mode built-in catalog with live
// previews, and installed custom themes (apply open/close/both, uninstall).
// The theme compiler, file pickers and animation constructor need the
// firmware-side AnimThemeCompiler backend, which this tree does not ship, so
// creating new themes from here is intentionally out of scope.
@Composable
internal fun TransitionGroup(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var openMode by remember {
        mutableIntStateOf(
            readGlobalInt(context, SystemUiKeys.ACTIVITY_OPEN_TRANSITION, TRANSITION_MODE_DISABLED),
        )
    }
    var closeMode by remember {
        mutableIntStateOf(
            readGlobalInt(context, SystemUiKeys.ACTIVITY_CLOSE_TRANSITION, TRANSITION_MODE_DISABLED),
        )
    }
    var predictiveDisabled by remember {
        mutableStateOf(readGlobalInt(context, SystemUiKeys.DISABLE_PREDICTIVE_BACK, 0) != 0)
    }
    var installedThemes by remember { mutableStateOf<List<InstalledTransitionTheme>>(emptyList()) }

    fun refreshThemes() {
        scope.launch(Dispatchers.IO) {
            installedThemes = loadInstalledTransitionThemes(context, openMode, closeMode)
        }
    }

    LaunchedEffect(Unit) { refreshThemes() }
    LaunchedEffect(openMode, closeMode) { refreshThemes() }

    val customPackage = remember(installedThemes) {
        installedThemes.firstOrNull { it.isActiveOpen || it.isActiveClose }?.packageName
            ?: readGlobalString(context, SystemUiKeys.ACTIVITY_OPEN_CUSTOM_PACKAGE)
            ?: readGlobalString(context, SystemUiKeys.ACTIVITY_TRANSITION_CUSTOM_PACKAGE)
    }

    fun applyOpen(mode: Int) {
        applyTransitionOpen(context, mode)
        openMode = mode
    }

    fun applyClose(mode: Int) {
        applyTransitionClose(context, mode)
        closeMode = mode
        // Legacy behavior: any close animation disables predictive back.
        if (mode != TRANSITION_MODE_DISABLED && !predictiveDisabled) {
            predictiveDisabled = true
            writeGlobalInt(context, SystemUiKeys.DISABLE_PREDICTIVE_BACK, 1)
        }
    }

    val subtitle = stringResource(
        R.string.sysui_trans_status,
        transitionModeDisplayName(context, openMode),
        transitionModeDisplayName(context, closeMode),
    )

    ExpandableGroupCard(
        title = stringResource(R.string.sysui_trans_title),
        chevronPainter = painterResource(R.drawable.ic_chevron_down),
        subtitle = subtitle,
        keyTag = "systemui_transition",
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingSwitchRow(
                title = stringResource(R.string.sysui_trans_predict_title),
                summary = stringResource(R.string.sysui_trans_predict_desc),
                checked = predictiveDisabled,
                onCheckedChange = {
                    predictiveDisabled = it
                    writeGlobalInt(context, SystemUiKeys.DISABLE_PREDICTIVE_BACK, if (it) 1 else 0)
                },
                variant = SettingSwitchRowVariant.Card,
            )
            GroupCard(title = stringResource(R.string.sysui_trans_title)) {
                Text(
                    text = stringResource(R.string.sysui_trans_current_open, transitionModeDisplayName(context, openMode)),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = stringResource(R.string.sysui_trans_current_close, transitionModeDisplayName(context, closeMode)),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                if (openMode != TRANSITION_MODE_DISABLED || closeMode != TRANSITION_MODE_DISABLED) {
                    Button(
                        onClick = {
                            applyOpen(TRANSITION_MODE_DISABLED)
                            applyClose(TRANSITION_MODE_DISABLED)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        Text(stringResource(R.string.sysui_trans_reset))
                    }
                }
            }
            Text(
                text = stringResource(R.string.sysui_trans_modes_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp),
            )
            TransitionNoAnimRow(
                isActiveOpen = openMode == TRANSITION_MODE_NO_ANIMATION,
                isActiveClose = closeMode == TRANSITION_MODE_NO_ANIMATION,
                onApplyBoth = {
                    applyOpen(TRANSITION_MODE_NO_ANIMATION)
                    applyClose(TRANSITION_MODE_NO_ANIMATION)
                },
                onApplyOpen = { applyOpen(TRANSITION_MODE_NO_ANIMATION) },
                onApplyClose = { applyClose(TRANSITION_MODE_NO_ANIMATION) },
            )
            TRANSITION_BUILTIN_MODES.forEach { mode ->
                TransitionBuiltinRow(
                    mode = mode,
                    isActiveOpen = mode.modeId == openMode,
                    isActiveClose = mode.modeId == closeMode,
                    onApplyBoth = {
                        applyOpen(mode.modeId)
                        applyClose(mode.modeId)
                    },
                    onApplyOpen = { applyOpen(mode.modeId) },
                    onApplyClose = { applyClose(mode.modeId) },
                )
            }
            Text(
                text = stringResource(R.string.sysui_trans_themes_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (installedThemes.isEmpty()) {
                Text(
                    text = stringResource(R.string.sysui_trans_themes_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                installedThemes.forEach { theme ->
                    TransitionThemeRow(
                        theme = theme,
                        onApplyOpen = {
                            writeGlobalString(context, SystemUiKeys.ACTIVITY_OPEN_CUSTOM_PACKAGE, theme.packageName)
                            applyOpen(TRANSITION_MODE_CUSTOM)
                            refreshThemes()
                        },
                        onApplyClose = {
                            writeGlobalString(context, SystemUiKeys.ACTIVITY_CLOSE_CUSTOM_PACKAGE, theme.packageName)
                            applyClose(TRANSITION_MODE_CUSTOM)
                            refreshThemes()
                        },
                        onApplyBoth = {
                            activateTransitionTheme(context, theme.packageName)
                            applyOpen(TRANSITION_MODE_CUSTOM)
                            applyClose(TRANSITION_MODE_CUSTOM)
                            refreshThemes()
                        },
                        onUninstall = {
                            scope.launch(Dispatchers.IO) {
                                uninstallTransitionTheme(context, theme.packageName)
                                installedThemes = loadInstalledTransitionThemes(context, openMode, closeMode)
                            }
                        },
                    )
                }
            }
            Text(
                text = if (customPackage.isNullOrBlank()) {
                    stringResource(R.string.sysui_pkg_none)
                } else {
                    stringResource(R.string.sysui_pkg_current, customPackage.orEmpty())
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun uninstallTransitionTheme(context: Context, packageName: String) {
    // Clear every stored reference before removing the package.
    listOf(
        SystemUiKeys.ACTIVITY_TRANSITION_CUSTOM_PACKAGE,
        SystemUiKeys.ACTIVITY_OPEN_CUSTOM_PACKAGE,
        SystemUiKeys.ACTIVITY_CLOSE_CUSTOM_PACKAGE,
    ).forEach { key ->
        runCatching {
            val current = readGlobalString(context, key)
            if (packageName == current) writeGlobalString(context, key, null)
        }
    }
    runCatching {
        // Best-effort uninstall through the system delete flow.
        val intent = android.content.Intent(android.content.Intent.ACTION_DELETE)
        intent.data = android.net.Uri.parse("package:$packageName")
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

@Composable
private fun TransitionNoAnimRow(
    isActiveOpen: Boolean,
    isActiveClose: Boolean,
    onApplyBoth: () -> Unit,
    onApplyOpen: () -> Unit,
    onApplyClose: () -> Unit,
) {
    val active = isActiveOpen || isActiveClose
    GroupCard(
        title = stringResource(R.string.sysui_trans_no_anim_title),
    ) {
        Text(
            text = stringResource(R.string.sysui_trans_no_anim_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (isActiveOpen) {
            Text(
                text = stringResource(R.string.sysui_trans_preview_open),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (isActiveClose) {
            Text(
                text = stringResource(R.string.sysui_trans_preview_close),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(
                onClick = onApplyBoth,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = stringResource(R.string.sysui_trans_apply_both),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
            OutlinedButton(
                onClick = onApplyOpen,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = stringResource(R.string.sysui_trans_apply_open),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
            OutlinedButton(
                onClick = onApplyClose,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = stringResource(R.string.sysui_trans_apply_close),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
        }
        if (active) {
            Text(
                text = stringResource(R.string.sysui_trans_active),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
