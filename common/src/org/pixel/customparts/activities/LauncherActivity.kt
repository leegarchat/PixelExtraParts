package org.pixel.customparts.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.pixel.customparts.R
import org.pixel.customparts.dynamicDarkColorScheme
import org.pixel.customparts.dynamicLightColorScheme
import org.pixel.customparts.ui.InfoDialog
import org.pixel.customparts.ui.launcher.*
import org.pixel.customparts.utils.dynamicStringResource

class LauncherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val darkTheme = isSystemInDarkTheme()
            val context = LocalContext.current
            val colorScheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LauncherScreen(onBack = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var infoDialogTitle by remember { mutableStateOf<String?>(null) }
    var infoDialogText by remember { mutableStateOf<String?>(null) }
    var infoDialogVideo by remember { mutableStateOf<String?>(null) }
    var showXposedWarning by remember { mutableStateOf(false) }
    
    var nativeSearchEnabled by remember { mutableStateOf(LauncherManager.isNativeSearchEnabled(context)) }
    
    var refreshKey by remember { mutableIntStateOf(0) }
    
    var isClearAllRestartVisible by remember { mutableStateOf(false) }
    var isSearchRestartVisible by remember { mutableStateOf(false) }
    var isGridRestartVisible by remember { mutableStateOf(false) }
    var isIconPackRestartVisible by remember { mutableStateOf(false) }
    
    val isBottomRestartVisible = isClearAllRestartVisible || isSearchRestartVisible || isGridRestartVisible || isIconPackRestartVisible

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            TopAppBar(
                title = { Text(
                        dynamicStringResource(R.string.launcher_settings_title),
                        fontWeight = FontWeight.Bold
                    ) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                AnimatedVisibility(
                    visible = !isBottomRestartVisible,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Button(
                        onClick = { 
                            scope.launch { LauncherManager.restartLauncher(context) }
                        },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer, 
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Restart Launcher") 
                    }
                }
            }

            item {
                NativeSearchSection(
                    context = context,
                    scope = scope,
                    nativeSearchEnabled = nativeSearchEnabled,
                    onNativeSearchChanged = { checked: Boolean ->
                        nativeSearchEnabled = checked
                        scope.launch { LauncherManager.setNativeSearchEnabled(context, checked) }
                    },
                    onInfoClick = { t: String, s: String, v: String? ->
                        infoDialogTitle = t
                        infoDialogText = s
                        infoDialogVideo = v
                    }
                )
            }

            item {
                SearchWidgetSection(
                    context = context,
                    scope = scope,
                    showXposedDialog = { showXposedWarning = true },
                    onInfoClick = { t: String, s: String, v: String? ->
                        infoDialogTitle = t
                        infoDialogText = s
                        infoDialogVideo = v
                    },
                    onShowBottomRestartChange = { isVisible: Boolean ->
                        isSearchRestartVisible = isVisible
                    }
                )
            }

            item {
                GridSizerSection(
                    context = context,
                    scope = scope,
                    onInfoClick = { t: String, s: String, v: String? ->
                        infoDialogTitle = t
                        infoDialogText = s
                        infoDialogVideo = v
                    },
                    onShowBottomRestartChange = { isVisible: Boolean ->
                        isGridRestartVisible = isVisible
                    }
                )
            }

            item {
                ClearAllSection(
                    context = context,
                    scope = scope,
                    refreshKey = refreshKey,
                    onSettingChanged = { refreshKey++ },
                    onInfo = { t: String, s: String, v: String? ->
                        infoDialogTitle = t
                        infoDialogText = s
                        infoDialogVideo = v
                    },
                    onShowBottomRestartChange = { isVisible: Boolean ->
                        isClearAllRestartVisible = isVisible
                    },
                    onShowXposedDialog = { showXposedWarning = true }
                )
            }
            
            item {
                Dt2sUiSection(
                    context = context,
                    scope = scope,
                    onInfoClick = { t: String, s: String, v: String? ->
                        infoDialogTitle = t
                        infoDialogText = s
                        infoDialogVideo = v
                    },
                    showXposedDialog = {
                        showXposedWarning = true
                    }
                )
            }
        }
    }

    if (infoDialogTitle != null && infoDialogText != null) {
        InfoDialog(
            title = infoDialogTitle!!,
            text = infoDialogText!!,
            videoResName = infoDialogVideo,
            onDismiss = {
                infoDialogTitle = null
                infoDialogText = null
                infoDialogVideo = null
            }
        )
    }
    
    if (showXposedWarning) {
        AlertDialog(
            onDismissRequest = { showXposedWarning = false },
            title = { Text(dynamicStringResource(R.string.os_dialog_xposed_title)) },
            text = { Text(dynamicStringResource(R.string.os_dialog_xposed_msg)) },
            confirmButton = {
                TextButton(onClick = { showXposedWarning = false }) {
                    Text("OK")
                }
            }
        )
    }
}