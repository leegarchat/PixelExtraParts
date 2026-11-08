package org.pixel.extra.parts.overscroll

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import org.pixel.extra.parts.R
import org.pixel.extra.parts.ui.GroupCard
import org.pixel.extra.parts.ui.edgeOutline

// Profile library card: built-in presets first, then user-saved profiles.
// Tapping a row applies it and marks it active; the save button snapshots
// the current Settings.Global state under a new name.
@Composable
fun OverscrollProfilesSection(
    presets: List<OverscrollStoredProfile>,
    saved: List<OverscrollStoredProfile>,
    activeName: String?,
    onApply: (OverscrollStoredProfile) -> Unit,
    onSave: (String) -> Unit,
    onDelete: (OverscrollStoredProfile) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSaveDialog by remember { mutableStateOf(false) }
    GroupCard(
        title = stringResource(R.string.os_profiles_title),
        modifier = modifier,
    ) {
        if (presets.isEmpty() && saved.isEmpty()) {
            Text(
                text = stringResource(R.string.os_profiles_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (presets.isNotEmpty()) {
            ProfilesRowList(
                profiles = presets,
                activeName = activeName,
                onApply = onApply,
                onDelete = null,
            )
        }
        if (saved.isNotEmpty()) {
            ProfilesRowList(
                profiles = saved,
                activeName = activeName,
                onApply = onApply,
                onDelete = onDelete,
            )
        }
        val haptics = LocalHapticFeedback.current
        Button(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                showSaveDialog = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.os_profile_save))
        }
    }
    if (showSaveDialog) {
        OverscrollSaveDialog(
            onDismiss = { showSaveDialog = false },
            onConfirm = {
                showSaveDialog = false
                onSave(it)
            },
        )
    }
}

@Composable
private fun ProfilesRowList(
    profiles: List<OverscrollStoredProfile>,
    activeName: String?,
    onApply: (OverscrollStoredProfile) -> Unit,
    onDelete: ((OverscrollStoredProfile) -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        profiles.forEach { profile ->
            OverscrollProfileRow(
                profile = profile,
                isActive = profile.name == activeName,
                onApply = { onApply(profile) },
                onDelete = onDelete?.let { { it(profile) } },
            )
        }
    }
}

@Composable
private fun OverscrollProfileRow(
    profile: OverscrollStoredProfile,
    isActive: Boolean,
    onApply: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    val haptics = LocalHapticFeedback.current
    val bgColor by animateColorAsState(
        targetValue = if (isActive) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = tween(200),
        label = "OverscrollProfileBg",
    )
    val contentColor by animateColorAsState(
        targetValue = if (isActive) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = tween(200),
        label = "OverscrollProfileContent",
    )
    val rowShape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(bgColor)
            .border(1.dp, bgColor.edgeOutline(), rowShape)
            .clickable(
                role = Role.Button,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onApply()
                },
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (profile.isPreset) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                        shape = CircleShape,
                    ) {
                        Text(
                            text = stringResource(R.string.os_profile_preset),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Text(
                text = stringResource(
                    if (isActive) R.string.os_profile_active else R.string.os_profile_apply_hint,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.7f),
            )
        }
        if (onDelete != null) {
            IconButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDelete()
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.os_profile_delete),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun OverscrollSaveDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    val haptics = LocalHapticFeedback.current
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(horizontal = 24.dp),
        shape = RoundedCornerShape(22.dp),
        title = {
            Text(
                text = stringResource(R.string.os_save_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(text = stringResource(R.string.os_save_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isNotEmpty()) {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onConfirm(trimmed)
                    }
                },
                colors = ButtonDefaults.buttonColors(),
            ) {
                Text(text = stringResource(R.string.os_action_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onDismiss()
                },
            ) {
                Text(text = stringResource(R.string.os_action_cancel))
            }
        },
    )
}
