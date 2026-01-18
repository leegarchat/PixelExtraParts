package org.pixel.customparts.ui.launcher

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pixel.customparts.AppConfig
import org.pixel.customparts.R
import org.pixel.customparts.activities.ClearAllMode
import org.pixel.customparts.activities.LauncherManager
import org.pixel.customparts.ui.GenericSwitchRow
import org.pixel.customparts.ui.ModuleStatus
import org.pixel.customparts.ui.SettingsGroupCard
import org.pixel.customparts.ui.SliderSettingFloat
import org.pixel.customparts.utils.dynamicStringResource

@Composable
fun ClearAllSection(
    context: Context,
    scope: CoroutineScope,
    refreshKey: Int,
    onSettingChanged: () -> Unit,
    onInfo: (String, String, String?) -> Unit,
    onShowBottomRestartChange: (Boolean) -> Unit,
    onShowXposedDialog: () -> Unit
) {
    val keyEnabled = LauncherManager.KEY_CLEAR_ALL_ENABLED
    val keyMode = LauncherManager.KEY_CLEAR_ALL_MODE
    val keyMargin = LauncherManager.KEY_CLEAR_ALL_MARGIN
    
    var enabled by remember(refreshKey) { mutableStateOf(Settings.Secure.getInt(context.contentResolver, keyEnabled, 0) == 1) }
    var mode by remember(refreshKey) { mutableIntStateOf(Settings.Secure.getInt(context.contentResolver, keyMode, 0)) }
    val initialLoadedMargin = remember(refreshKey) { Settings.Secure.getFloat(context.contentResolver, keyMargin, 3.0f) }
    var margin by remember(refreshKey) { mutableFloatStateOf(initialLoadedMargin) }
    var baselineMargin by remember(refreshKey) { mutableFloatStateOf(initialLoadedMargin) }
    val isMarginModified = margin != baselineMargin
    
    LaunchedEffect(isMarginModified) {
        onShowBottomRestartChange(isMarginModified)
    }

    SettingsGroupCard(title = dynamicStringResource(R.string.launcher_clear_all_title)) {
        GenericSwitchRow(
            title = if (enabled) dynamicStringResource(R.string.os_status_active) else dynamicStringResource(R.string.os_status_disabled),
            checked = enabled,
            onCheckedChange = { checked ->
                if (checked && AppConfig.IS_XPOSED && !ModuleStatus.isModuleActive()) {
                    onShowXposedDialog()
                    enabled = false
                } else {
                    enabled = checked
                    scope.launch(Dispatchers.IO) {
                        Settings.Secure.putInt(context.contentResolver, keyEnabled, if (checked) 1 else 0)
                        LauncherManager.restartLauncher(context)
                        launch(Dispatchers.Main) { onSettingChanged() }
                    }
                }
            },
            videoResName = "launcher_clear_all",
            infoText = dynamicStringResource(R.string.launcher_clear_all_desc),
            onInfoClick = onInfo
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        val modes = listOf(
            ClearAllMode(0, R.string.launcher_ca_mode_float, Icons.Rounded.CleaningServices),
            ClearAllMode(1, R.string.launcher_ca_mode_screenshot, Icons.Rounded.PhotoCamera),
            ClearAllMode(2, R.string.launcher_ca_mode_select, Icons.Rounded.SelectAll)
        )

        Column(modifier = Modifier.padding(16.dp)) {
            modes.forEach { item ->
                val isSelected = item.id == mode
                val isRowEnabled = enabled
                
                val backgroundColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    label = "rowBg"
                )
                
                val contentAlpha = if (isRowEnabled) 1f else 0.4f

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(backgroundColor)
                        .clickable(enabled = isRowEnabled) {
                            if (mode != item.id) {
                                mode = item.id
                                scope.launch(Dispatchers.IO) {
                                    Settings.Secure.putInt(context.contentResolver, keyMode, item.id)
                                    LauncherManager.restartLauncher(context)
                                    launch(Dispatchers.Main) { onSettingChanged() }
                                }
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 14.dp)
                        .alpha(contentAlpha),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                item.icon,
                                null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = dynamicStringResource(item.labelRes),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    AnimatedVisibility(
                        visible = isSelected,
                        enter = scaleIn() + fadeIn(),
                        exit = scaleOut() + fadeOut()
                    ) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        val isSliderActive = enabled && (mode == 0)
        
        SliderSettingFloat(
            title = dynamicStringResource(R.string.launcher_ca_margin),
            value = margin,
            range = 0.1f..7.0f,
            unit = "x",
            enabled = isSliderActive,
            onValueChange = { 
                margin = it
                scope.launch(Dispatchers.IO) {
                    Settings.Secure.putFloat(context.contentResolver, keyMargin, it)
                }
            },
            onDefault = {
                val def = 3.0f
                margin = def
                scope.launch(Dispatchers.IO) {
                    Settings.Secure.putFloat(context.contentResolver, keyMargin, def)
                }
            },
            infoText = dynamicStringResource(R.string.launcher_ca_margin_desc),
            onInfoClick = onInfo
        )

        AnimatedVisibility(
            visible = isMarginModified,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            LauncherManager.restartLauncher(context)
                            withContext(Dispatchers.Main) {
                                baselineMargin = margin
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(dynamicStringResource(R.string.btn_restart_launcher))
                }
            }
        }

        if (!isMarginModified) {
            Spacer(Modifier.height(8.dp))
        }
    }
}