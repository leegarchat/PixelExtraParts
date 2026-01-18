package org.pixel.customparts.activities

import android.content.Context
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.pixel.customparts.R
import org.pixel.customparts.ui.SettingsGroupCard
import org.pixel.customparts.utils.dynamicStringResource


@Composable
fun ProfilesSection(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    profiles: List<SavedProfile>,
    onProfilesChanged: (List<SavedProfile>) -> Unit,
    onProfileLoaded: () -> Unit,
    exportLauncher: ManagedActivityResultLauncher<String, android.net.Uri?>,
    importLauncher: ManagedActivityResultLauncher<Array<String>, android.net.Uri?>,
    refreshKey: Int 
) {
    var showSaveProfileDialog by remember { mutableStateOf(false) }
    var activeProfileName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(refreshKey) {
        withContext(Dispatchers.IO) {
            val savedName = OverscrollManager.getActiveProfileName(context)
            withContext(Dispatchers.Main) {
                activeProfileName = savedName
            }
        }
    }

    SettingsGroupCard(title = dynamicStringResource(R.string.os_group_profiles)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { exportLauncher.launch("overscroll_settings.json") },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
            ) {
                Icon(Icons.Outlined.FileUpload, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(dynamicStringResource(R.string.os_prof_btn_export))
            }
            Button(
                onClick = { importLauncher.launch(arrayOf("application/json")) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
            ) {
                Icon(Icons.Outlined.FileDownload, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(dynamicStringResource(R.string.os_prof_btn_import))
            }
            Button(
                onClick = { showSaveProfileDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(dynamicStringResource(R.string.os_prof_btn_save))
            }
        }
        
        if (profiles.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                dynamicStringResource(R.string.os_prof_header_list),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                profiles.forEachIndexed { index, profile ->
                    if (index > 0) {
                        Divider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    val isActive = profile.name == activeProfileName
                    
                    val backgroundColor by animateColorAsState(
                        targetValue = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                        label = "profileBg"
                    )
                    
                    val contentColor by animateColorAsState(
                        targetValue = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        label = "profileContent"
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(backgroundColor)
                            .clickable {
                                scope.launch { 
                                    activeProfileName = profile.name
                                    OverscrollManager.loadProfile(context, profile)
                                    onProfileLoaded()
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = profile.name, 
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                color = contentColor
                            )
                            
                            AnimatedVisibility(
                                visible = isActive,
                                enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
                                exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
                            ) {
                                Text(
                                    text = dynamicStringResource(R.string.os_prof_item_active),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = contentColor.copy(alpha = 0.8f)
                                )
                            }
                            AnimatedVisibility(visible = !isActive) {
                                Text(
                                    text = dynamicStringResource(R.string.os_prof_item_tap),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = contentColor.copy(alpha = 0.5f)
                                )
                            }
                        }
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isActive) {
                                Icon(
                                    Icons.Default.Check, 
                                    dynamicStringResource(R.string.status_active), 
                                    tint = contentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                            }

                            IconButton(
                                onClick = {
                                    scope.launch {
                                        OverscrollManager.deleteProfile(context, profile)
                                        onProfilesChanged(OverscrollManager.getSavedProfiles(context))
                                    }
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete, 
                                    dynamicStringResource(R.string.os_prof_delete_desc), 
                                    tint = if (isActive) contentColor.copy(alpha = 0.7f) else MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showSaveProfileDialog) {
        var profileName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSaveProfileDialog = false },
            title = { Text(dynamicStringResource(R.string.os_prof_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = profileName, 
                    onValueChange = { profileName = it },
                    label = { Text(dynamicStringResource(R.string.os_prof_dialog_hint)) }
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (profileName.isNotEmpty()) {
                        scope.launch {
                            OverscrollManager.saveProfile(context, profileName)
                            onProfilesChanged(OverscrollManager.getSavedProfiles(context))
                            showSaveProfileDialog = false
                        }
                    }
                }) { Text(dynamicStringResource(R.string.btn_save)) }
            },
            dismissButton = { TextButton(onClick = { showSaveProfileDialog = false }) { Text(dynamicStringResource(R.string.btn_cancel)) } }
        )
    }
}