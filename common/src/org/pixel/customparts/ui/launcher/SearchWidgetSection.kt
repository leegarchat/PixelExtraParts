package org.pixel.customparts.ui.launcher

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pixel.customparts.AppConfig
import org.pixel.customparts.R
import org.pixel.customparts.activities.LauncherManager
import org.pixel.customparts.ui.GenericSwitchRow
import org.pixel.customparts.ui.SettingsGroupCard
import org.pixel.customparts.ui.SliderSetting
import org.pixel.customparts.ui.ModuleStatus
import org.pixel.customparts.utils.dynamicStringResource

@Composable
fun SearchWidgetSection(
    context: Context,
    scope: CoroutineScope,
    showXposedDialog: () -> Unit,
    onInfoClick: (String, String, String?) -> Unit,
    onShowBottomRestartChange: (Boolean) -> Unit
) {
    val keyDockEnable = LauncherManager.KEY_DOCK_ENABLE
    val keyHideSearch = LauncherManager.KEY_HIDE_SEARCH
    val keyHideDock = LauncherManager.KEY_HIDE_DOCK
    val keyPaddingHomepage = LauncherManager.KEY_PADDING_HOMEPAGE
    val keyPaddingDock = LauncherManager.KEY_PADDING_DOCK
    val keyPaddingSearch = LauncherManager.KEY_PADDING_SEARCH
    val keyPaddingDots = LauncherManager.KEY_PADDING_DOTS
    val videoKeyHideSearch = LauncherManager.KEY_HIDE_SEARCH_BASE
    val videoKeyHideDock = "launcher_hidden_dock"
    
    var dockCustomizationEnabled by remember { mutableStateOf(Settings.Secure.getInt(context.contentResolver, keyDockEnable, 0) == 1) }
    var hideSearchEnabled by remember { mutableStateOf(Settings.Secure.getInt(context.contentResolver, keyHideSearch, 0) == 1) }
    var hideDockEnabled by remember { mutableStateOf(Settings.Secure.getInt(context.contentResolver, keyHideDock, 0) == 1) }
    
    val initPaddingHomepage = remember { Settings.Secure.getInt(context.contentResolver, keyPaddingHomepage, 165) }
    var paddingHomepage by remember { mutableIntStateOf(initPaddingHomepage) }
    var baselinePaddingHomepage by remember { mutableIntStateOf(initPaddingHomepage) }
    
    val initPaddingDock = remember { Settings.Secure.getInt(context.contentResolver, keyPaddingDock, 0) }
    var paddingDock by remember { mutableIntStateOf(initPaddingDock) }
    var baselinePaddingDock by remember { mutableIntStateOf(initPaddingDock) }
    
    val initPaddingSearch = remember { Settings.Secure.getInt(context.contentResolver, keyPaddingSearch, 0) }
    var paddingSearch by remember { mutableIntStateOf(initPaddingSearch) }
    var baselinePaddingSearch by remember { mutableIntStateOf(initPaddingSearch) }
    
    val initPaddingDots = remember { Settings.Secure.getInt(context.contentResolver, keyPaddingDots, 0) }
    var paddingDots by remember { mutableIntStateOf(initPaddingDots) }
    var baselinePaddingDots by remember { mutableIntStateOf(initPaddingDots) }
    
    val isAnyPaddingModified = paddingHomepage != baselinePaddingHomepage || 
                                paddingDock != baselinePaddingDock || 
                                paddingSearch != baselinePaddingSearch ||
                                paddingDots != baselinePaddingDots
    
    LaunchedEffect(isAnyPaddingModified) {
        onShowBottomRestartChange(isAnyPaddingModified)
    }

    SettingsGroupCard(title = dynamicStringResource(R.string.search_widget_group_title)) {
        GenericSwitchRow(
            title = dynamicStringResource(R.string.search_widget_enable_dock),
            checked = dockCustomizationEnabled,
            onCheckedChange = { checked ->
                if (checked && AppConfig.IS_XPOSED && !ModuleStatus.isModuleActive()) {
                    showXposedDialog()
                    dockCustomizationEnabled = false
                } else {
                    dockCustomizationEnabled = checked
                    scope.launch(Dispatchers.IO) {
                        Settings.Secure.putInt(context.contentResolver, keyDockEnable, if (checked) 1 else 0)
                        LauncherManager.restartLauncher(context)
                    }
                }
            },
            infoText = dynamicStringResource(R.string.search_widget_desc_dock),
            videoResName = null,
            onInfoClick = onInfoClick
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        
        GenericSwitchRow(
            title = dynamicStringResource(R.string.search_widget_hide_search),
            checked = hideSearchEnabled,
            enabled = dockCustomizationEnabled,
            onCheckedChange = { checked ->
                hideSearchEnabled = checked
                scope.launch(Dispatchers.IO) { 
                    Settings.Secure.putInt(context.contentResolver, keyHideSearch, if (checked) 1 else 0)
                    LauncherManager.restartLauncher(context)
                }
            },
            infoText = dynamicStringResource(R.string.search_widget_desc_hide_search),
            videoResName = videoKeyHideSearch,
            onInfoClick = onInfoClick
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        
        GenericSwitchRow(
            title = dynamicStringResource(R.string.search_widget_hide_dock),
            checked = hideDockEnabled,
            enabled = dockCustomizationEnabled,
            onCheckedChange = { checked ->
                hideDockEnabled = checked
                scope.launch(Dispatchers.IO) { 
                    Settings.Secure.putInt(context.contentResolver, keyHideDock, if (checked) 1 else 0)
                    LauncherManager.restartLauncher(context)
                }
            },
            infoText = dynamicStringResource(R.string.search_widget_desc_hide_dock),
            videoResName = videoKeyHideDock,
            onInfoClick = onInfoClick
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        
        SliderSetting(
            title = dynamicStringResource(R.string.search_widget_padding_home),
            value = paddingHomepage,
            range = -100..600,
            unit = "px",
            enabled = dockCustomizationEnabled,
            onValueChange = {
                paddingHomepage = it
                scope.launch(Dispatchers.IO) { 
                    Settings.Secure.putInt(context.contentResolver, keyPaddingHomepage, it)
                }
            },
            onDefault = {
                paddingHomepage = 165
                scope.launch(Dispatchers.IO) {
                    Settings.Secure.putInt(context.contentResolver, keyPaddingHomepage, 165)
                }
            },
            infoText = dynamicStringResource(R.string.search_widget_desc_padding_home),
            videoResName = keyPaddingHomepage,
            onInfoClick = onInfoClick
        )
        
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        
        SliderSetting(
            title = dynamicStringResource(R.string.search_widget_padding_dock),
            value = paddingDock,
            range = -120..60,
            unit = "px",
            enabled = dockCustomizationEnabled && !hideDockEnabled,
            onValueChange = {
                paddingDock = it
                scope.launch(Dispatchers.IO) {
                    Settings.Secure.putInt(context.contentResolver, keyPaddingDock, it)
                }
            },
            onDefault = {
                paddingDock = 0
                scope.launch(Dispatchers.IO) { 
                    Settings.Secure.putInt(context.contentResolver, keyPaddingDock, 0)
                }
            },
            infoText = dynamicStringResource(R.string.search_widget_desc_padding_dock),
            videoResName = keyPaddingDock,
            onInfoClick = onInfoClick
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        
        SliderSetting(
            title = dynamicStringResource(R.string.search_widget_padding_search),
            value = paddingSearch,
            range = -260..100,
            unit = "px",
            enabled = dockCustomizationEnabled && !hideSearchEnabled,
            onValueChange = {
                paddingSearch = it
                scope.launch(Dispatchers.IO) { 
                    Settings.Secure.putInt(context.contentResolver, keyPaddingSearch, it)
                }
            },
            onDefault = {
                paddingSearch = 0
                scope.launch(Dispatchers.IO) { 
                    Settings.Secure.putInt(context.contentResolver, keyPaddingSearch, 0)
                }
            },
            infoText = dynamicStringResource(R.string.search_widget_desc_padding_search),
            videoResName = keyPaddingSearch,
            onInfoClick = onInfoClick
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // Dots Padding
        SliderSetting(
            title = dynamicStringResource(R.string.search_widget_padding_dots),
            value = paddingDots,
            range = -300..900,
            unit = "px",
            enabled = dockCustomizationEnabled,
            onValueChange = { 
                paddingDots = it
                scope.launch(Dispatchers.IO) { 
                    Settings.Secure.putInt(context.contentResolver, keyPaddingDots, it)
                }
            },
            onDefault = {
                paddingDots = -30
                scope.launch(Dispatchers.IO) { 
                    Settings.Secure.putInt(context.contentResolver, keyPaddingDots, -30)
                }
            },
            infoText = dynamicStringResource(R.string.search_widget_desc_padding_dots),
            videoResName = keyPaddingDots,
            onInfoClick = onInfoClick
        )

        AnimatedVisibility(
            visible = isAnyPaddingModified,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            LauncherManager.restartLauncher(context)
                            withContext(Dispatchers.Main) {
                                baselinePaddingHomepage = paddingHomepage
                                baselinePaddingDock = paddingDock
                                baselinePaddingSearch = paddingSearch
                                baselinePaddingDots = paddingDots
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

        if (!isAnyPaddingModified) {
            Spacer(Modifier.height(8.dp))
        }
    }
}