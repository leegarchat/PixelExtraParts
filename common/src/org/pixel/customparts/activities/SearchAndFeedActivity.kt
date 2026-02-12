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
import org.pixel.customparts.dynamicDarkColorScheme
import org.pixel.customparts.dynamicLightColorScheme
import org.pixel.customparts.R
import org.pixel.customparts.ui.InfoDialog
import org.pixel.customparts.ui.RebootBubble
import org.pixel.customparts.ui.launcher.Dt2sUiSection
import org.pixel.customparts.ui.launcher.NativeSearchSection
import org.pixel.customparts.ui.launcher.SearchWidgetSection
import org.pixel.customparts.utils.dynamicStringResource

class SearchAndFeedActivity : ComponentActivity() {
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
                    SearchAndFeedScreen(onBack = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchAndFeedScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var infoDialogTitle by remember { mutableStateOf<String?>(null) }
    var infoDialogText by remember { mutableStateOf<String?>(null) }
    var infoDialogVideo by remember { mutableStateOf<String?>(null) }
    var showXposedWarning by remember { mutableStateOf(false) }

    var needsRestart by remember { mutableStateOf(false) }
    var nativeSearchEnabled by remember { mutableStateOf(LauncherManager.isNativeSearchEnabled(context)) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        floatingActionButton = { RebootBubble() },
        topBar = {
            TopAppBar(
                title = { Text(dynamicStringResource(R.string.search_feed_screen_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, dynamicStringResource(R.string.nav_back)) }
                },
                actions = {
                    AnimatedVisibility(
                        visible = needsRestart,
                        enter = fadeIn() + expandHorizontally(),
                        exit = fadeOut() + shrinkHorizontally()
                    ) {
                        Button(
                            onClick = { scope.launch { LauncherManager.restartLauncher(context); needsRestart = false } },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(dynamicStringResource(R.string.btn_restart))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Dt2sUiSection(
                    context = context,
                    scope = scope,
                    showXposedDialog = { showXposedWarning = true },
                    onInfoClick = { t, s, v ->
                        infoDialogTitle = t
                        infoDialogText = s
                        infoDialogVideo = v
                    },
                    onSettingChanged = { needsRestart = true }
                )
            }

            item {
                NativeSearchSection(
                    context = context,
                    scope = scope,
                    nativeSearchEnabled = nativeSearchEnabled,
                    onNativeSearchChanged = { checked ->
                        nativeSearchEnabled = checked
                        scope.launch { LauncherManager.setNativeSearchEnabled(context, checked) }
                        needsRestart = true
                    },
                    onInfoClick = { t, s, v ->
                        infoDialogTitle = t; infoDialogText = s; infoDialogVideo = v
                    },
                    onSettingChanged = { needsRestart = true }
                )
            }

            item {
                SearchWidgetSection(
                    context = context,
                    scope = scope,
                    showXposedDialog = { showXposedWarning = true },
                    onInfoClick = { t, s, v ->
                        infoDialogTitle = t; infoDialogText = s; infoDialogVideo = v
                    },
                    onSettingChanged = { needsRestart = true }
                )
            }
        }
    }

    if (infoDialogTitle != null && infoDialogText != null) {
        InfoDialog(
            title = infoDialogTitle!!,
            text = infoDialogText!!,
            videoResName = infoDialogVideo,
            onDismiss = { infoDialogTitle = null; infoDialogText = null; infoDialogVideo = null }
        )
    }
    
    
    if (showXposedWarning) {
        
         AlertDialog(
            onDismissRequest = { showXposedWarning = false },
            title = { Text(dynamicStringResource(R.string.xposed_required_title)) },
            text = { Text(dynamicStringResource(R.string.xposed_required_message)) },
            confirmButton = { TextButton(onClick = { showXposedWarning = false }) { Text(dynamicStringResource(R.string.btn_ok)) } }
        )
    }
}
