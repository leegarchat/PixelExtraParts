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
import org.pixel.customparts.R
import org.pixel.customparts.activities.LauncherManager
import org.pixel.customparts.ui.GenericSwitchRow
import org.pixel.customparts.ui.SettingsGroupCard
import org.pixel.customparts.ui.SliderSetting
import org.pixel.customparts.utils.dynamicStringResource

@Composable
fun GridSizerSection(
    context: Context,
    scope: CoroutineScope,
    onInfoClick: (String, String, String?) -> Unit,
    onShowBottomRestartChange: (Boolean) -> Unit
) {
    val keyHomeEnable = LauncherManager.KEY_HOME_ENABLE
    val keyHomeCols = LauncherManager.KEY_HOME_COLS
    val keyHomeRows = LauncherManager.KEY_HOME_ROWS
    val keyHomeHideText = LauncherManager.KEY_HOME_HIDE_TEXT
    val keyMenuEnable = LauncherManager.KEY_MENU_ENABLE
    val keyMenuCols = LauncherManager.KEY_MENU_COLS
    val keyMenuSearchCols = LauncherManager.KEY_MENU_SEARCH_COLS
    val keyMenuHideText = LauncherManager.KEY_MENU_HIDE_TEXT
    val keyMenuRowHeight = LauncherManager.KEY_MENU_ROW_HEIGHT
    val videoKeyHomeEnable = LauncherManager.KEY_HOME_ENABLE_BASE
    val videoKeyMenuEnable = LauncherManager.KEY_MENU_ENABLE_BASE
    
    var homeEnabled by remember { mutableStateOf(Settings.Secure.getInt(context.contentResolver, keyHomeEnable, 0) == 1) }
    var menuEnabled by remember { mutableStateOf(Settings.Secure.getInt(context.contentResolver, keyMenuEnable, 0) == 1) }
    var homeHideText by remember { mutableStateOf(Settings.Secure.getInt(context.contentResolver, keyHomeHideText, 0) == 1) }
    var menuHideText by remember { mutableStateOf(Settings.Secure.getInt(context.contentResolver, keyMenuHideText, 0) == 1) }
    
    val initHomeCols = remember { Settings.Secure.getInt(context.contentResolver, keyHomeCols, 5) }
    var homeCols by remember { mutableIntStateOf(initHomeCols) }
    var baselineHomeCols by remember { mutableIntStateOf(initHomeCols) }
    
    val initHomeRows = remember { Settings.Secure.getInt(context.contentResolver, keyHomeRows, 6) }
    var homeRows by remember { mutableIntStateOf(initHomeRows) }
    var baselineHomeRows by remember { mutableIntStateOf(initHomeRows) }
    
    val initMenuCols = remember { Settings.Secure.getInt(context.contentResolver, keyMenuCols, 4) }
    var menuCols by remember { mutableIntStateOf(initMenuCols) }
    var baselineMenuCols by remember { mutableIntStateOf(initMenuCols) }

    val initMenuSearchCols = remember { 
        val stored = Settings.Secure.getInt(context.contentResolver, keyMenuSearchCols, 0)
        if (stored == 0) 4 else stored
    }
    var menuSearchCols by remember { mutableIntStateOf(initMenuSearchCols) }
    var baselineMenuSearchCols by remember { mutableIntStateOf(initMenuSearchCols) }
    
    val initMenuRowHeight = remember { Settings.Secure.getInt(context.contentResolver, keyMenuRowHeight, 100) }
    var menuRowHeight by remember { mutableIntStateOf(initMenuRowHeight) }
    var baselineMenuRowHeight by remember { mutableIntStateOf(initMenuRowHeight) }
    
    val isHomeModified = homeCols != baselineHomeCols || homeRows != baselineHomeRows
    val isMenuModified = menuCols != baselineMenuCols || 
                        menuSearchCols != baselineMenuSearchCols || 
                        menuRowHeight != baselineMenuRowHeight

    val onApplyAndRestart = {
        scope.launch {
            LauncherManager.restartLauncher(context)
            withContext(Dispatchers.Main) {
                baselineHomeCols = homeCols
                baselineHomeRows = homeRows
                baselineMenuCols = menuCols
                baselineMenuSearchCols = menuSearchCols 
                baselineMenuRowHeight = menuRowHeight
            }
        }
    }

    LaunchedEffect(isHomeModified, isMenuModified) {
        onShowBottomRestartChange(isHomeModified || isMenuModified)
    }

    SettingsGroupCard(title = dynamicStringResource(R.string.grid_group_homepage)) {
        
        GenericSwitchRow(
            title = dynamicStringResource(R.string.grid_lbl_home_enable),
            checked = homeEnabled,
            onCheckedChange = { checked ->
                homeEnabled = checked
                scope.launch(Dispatchers.IO) { 
                    Settings.Secure.putInt(context.contentResolver, keyHomeEnable, if (checked) 1 else 0)
                    
                    if (!checked) {
                        homeHideText = false
                        Settings.Secure.putInt(context.contentResolver, keyHomeHideText, 0)
                    }
                    
                    LauncherManager.restartLauncher(context)
                }
            },
            infoText = dynamicStringResource(R.string.grid_desc_home_enable),
            videoResName = videoKeyHomeEnable,
            onInfoClick = onInfoClick
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        SliderSetting(
            title = dynamicStringResource(R.string.grid_lbl_columns),
            value = homeCols,
            range = 1..15,
            unit = "",
            enabled = homeEnabled,
            onValueChange = { 
                homeCols = it
                scope.launch(Dispatchers.IO) { Settings.Secure.putInt(context.contentResolver, keyHomeCols, it) }
            },
            onDefault = {
                homeCols = 5
                scope.launch(Dispatchers.IO) { Settings.Secure.putInt(context.contentResolver, keyHomeCols, 5) }
            },
            infoText = dynamicStringResource(R.string.grid_desc_home_cols),
            videoResName = keyHomeCols,
            onInfoClick = onInfoClick
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        SliderSetting(
            title = dynamicStringResource(R.string.grid_lbl_rows),
            value = homeRows,
            range = 1..15,
            unit = "",
            enabled = homeEnabled,
            onValueChange = { 
                homeRows = it
                scope.launch(Dispatchers.IO) { Settings.Secure.putInt(context.contentResolver, keyHomeRows, it) }
            },
            onDefault = {
                homeRows = 6
                scope.launch(Dispatchers.IO) { Settings.Secure.putInt(context.contentResolver, keyHomeRows, 6) }
            },
            infoText = dynamicStringResource(R.string.grid_desc_home_rows),
            videoResName = keyHomeRows,
            onInfoClick = onInfoClick
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        GenericSwitchRow(
            title = dynamicStringResource(R.string.grid_lbl_hide_text),
            checked = homeHideText,
            enabled = homeEnabled,
            onCheckedChange = { checked ->
                homeHideText = checked
                scope.launch(Dispatchers.IO) { 
                    Settings.Secure.putInt(context.contentResolver, keyHomeHideText, if (checked) 1 else 0)
                    LauncherManager.restartLauncher(context)
                }
            },
            infoText = dynamicStringResource(R.string.grid_desc_home_hide_text),
            videoResName = keyHomeHideText,
            onInfoClick = onInfoClick
        )

        AnimatedVisibility(
            visible = isHomeModified,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Button(
                    onClick = { onApplyAndRestart() },
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
        
        if (!isHomeModified) {
            Spacer(Modifier.height(8.dp))
        }
    }

    Spacer(Modifier.height(16.dp)) 

    SettingsGroupCard(title = dynamicStringResource(R.string.grid_group_drawer)) {
        GenericSwitchRow(
            title = dynamicStringResource(R.string.grid_lbl_drawer_enable),
            checked = menuEnabled,
            onCheckedChange = { checked ->
                menuEnabled = checked
                scope.launch(Dispatchers.IO) { 
                    Settings.Secure.putInt(context.contentResolver, keyMenuEnable, if (checked) 1 else 0)
                    if (!checked) {
                        menuHideText = false
                        Settings.Secure.putInt(context.contentResolver, keyMenuHideText, 0)
                    }
                    
                    LauncherManager.restartLauncher(context)
                }
            },
            infoText = dynamicStringResource(R.string.grid_desc_drawer_enable),
            videoResName = videoKeyMenuEnable,
            onInfoClick = onInfoClick
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        SliderSetting(
            title = dynamicStringResource(R.string.grid_lbl_drawer_cols),
            value = menuCols,
            range = 1..15,
            unit = "",
            enabled = menuEnabled,
            onValueChange = { 
                menuCols = it
                scope.launch(Dispatchers.IO) { Settings.Secure.putInt(context.contentResolver, keyMenuCols, it) }
                
                if (it < menuSearchCols) {
                    menuSearchCols = it
                    scope.launch(Dispatchers.IO) { Settings.Secure.putInt(context.contentResolver, keyMenuSearchCols, it) }
                }
                if (it >= 2 * menuSearchCols) {
                    val requiredSearch = (it / 2) + 1
                    menuSearchCols = requiredSearch
                    scope.launch(Dispatchers.IO) { Settings.Secure.putInt(context.contentResolver, keyMenuSearchCols, requiredSearch) }
                }
            },
            onDefault = {
                menuCols = 4
                scope.launch(Dispatchers.IO) { Settings.Secure.putInt(context.contentResolver, keyMenuCols, 4) }
                
                if (menuSearchCols > 4) {
                    menuSearchCols = 4
                    scope.launch(Dispatchers.IO) { Settings.Secure.putInt(context.contentResolver, keyMenuSearchCols, 4) }
                }
            },
            infoText = dynamicStringResource(R.string.grid_desc_drawer_cols),
            videoResName = keyMenuCols,
            onInfoClick = onInfoClick
        )
        
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        SliderSetting(
            title = dynamicStringResource(R.string.grid_lbl_search_cols),
            value = menuSearchCols,
            range = 1..15,
            unit = "",
            enabled = menuEnabled,
            onValueChange = { 
                menuSearchCols = it
                scope.launch(Dispatchers.IO) { Settings.Secure.putInt(context.contentResolver, keyMenuSearchCols, it) }
                
                if (it > menuCols) {
                    menuCols = it
                    scope.launch(Dispatchers.IO) { Settings.Secure.putInt(context.contentResolver, keyMenuCols, it) }
                }
                
                if (menuCols >= 2 * it) {
                    val forcedMenu = (it * 2) - 1
                    val safeMenu = if (forcedMenu < 1) 1 else forcedMenu
                    
                    menuCols = safeMenu
                    scope.launch(Dispatchers.IO) { Settings.Secure.putInt(context.contentResolver, keyMenuCols, safeMenu) }
                }
            },
            onDefault = {
                val def = 4
                menuSearchCols = def
                scope.launch(Dispatchers.IO) { Settings.Secure.putInt(context.contentResolver, keyMenuSearchCols, def) }
                
                if (def > menuCols) {
                    menuCols = def
                    scope.launch(Dispatchers.IO) { Settings.Secure.putInt(context.contentResolver, keyMenuCols, def) }
                }
            },
            infoText = dynamicStringResource(R.string.grid_desc_search_cols),
            videoResName = keyMenuSearchCols,
            onInfoClick = onInfoClick
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        SliderSetting(
            title = dynamicStringResource(R.string.grid_lbl_row_height),
            value = menuRowHeight,
            range = 30..700,
            unit = "%",
            enabled = menuEnabled,
            onValueChange = { 
                menuRowHeight = it
                scope.launch(Dispatchers.IO) { Settings.Secure.putInt(context.contentResolver, keyMenuRowHeight, it) }
            },
            onDefault = {
                menuRowHeight = 100
                scope.launch(Dispatchers.IO) { Settings.Secure.putInt(context.contentResolver, keyMenuRowHeight, 100) }
            },
            infoText = dynamicStringResource(R.string.grid_desc_row_height),
            videoResName = keyMenuRowHeight,
            onInfoClick = onInfoClick
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        GenericSwitchRow(
            title = dynamicStringResource(R.string.grid_lbl_drawer_hide_text),
            checked = menuHideText,
            enabled = menuEnabled,
            onCheckedChange = { checked ->
                menuHideText = checked
                scope.launch(Dispatchers.IO) { 
                    Settings.Secure.putInt(context.contentResolver, keyMenuHideText, if (checked) 1 else 0)
                    LauncherManager.restartLauncher(context)
                }
            },
            infoText = dynamicStringResource(R.string.grid_desc_drawer_hide_text),
            videoResName = keyMenuHideText,
            onInfoClick = onInfoClick
        )
        
        AnimatedVisibility(
            visible = isMenuModified,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Button(
                    onClick = { onApplyAndRestart() },
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

        if (!isMenuModified) {
            Spacer(Modifier.height(8.dp))
        }
    }
}