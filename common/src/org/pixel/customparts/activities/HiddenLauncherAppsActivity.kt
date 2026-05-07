package org.pixel.customparts.activities

import android.app.AxSandboxManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pixel.customparts.R
import org.pixel.customparts.dynamicDarkColorScheme
import org.pixel.customparts.dynamicLightColorScheme
import org.pixel.customparts.ui.GenericSwitchRow
import org.pixel.customparts.ui.REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING
import org.pixel.customparts.ui.RebootBubble
import org.pixel.customparts.ui.SettingsGroupCard
import org.pixel.customparts.ui.TopBarBlurOverlay
import org.pixel.customparts.ui.recordLayer
import org.pixel.customparts.ui.rememberGraphicsLayerRecordingState
import org.pixel.customparts.utils.dynamicStringResource
import java.util.Locale

class HiddenLauncherAppsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val darkTheme = isSystemInDarkTheme()
            val context = LocalContext.current
            val colorScheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HiddenLauncherAppsScreen(onBack = { finish() })
                }
            }
        }
    }
}

private data class HiddenLauncherApp(
    val label: String,
    val packageName: String,
    val icon: Drawable?,
    val isSystem: Boolean,
    val initiallyHidden: Boolean
)

private data class HiddenLauncherAppsLoadResult(
    val apps: List<HiddenLauncherApp>,
    val hiddenPackages: Set<String>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiddenLauncherAppsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val blurState = rememberGraphicsLayerRecordingState()
    val lazyListState = rememberLazyListState()
    val isScrolled by remember { derivedStateOf { lazyListState.canScrollBackward } }

    var needsRestart by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var apps by remember { mutableStateOf<List<HiddenLauncherApp>>(emptyList()) }
    var hiddenPackages by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showSystemApps by rememberSaveable { mutableStateOf(true) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val result = loadHiddenLauncherApps(context)
        apps = result.apps
        hiddenPackages = result.hiddenPackages
        loadFailed = result.apps.isEmpty() && result.hiddenPackages.isEmpty()
        isLoading = false
    }

    val visibleApps = remember(apps, showSystemApps, searchQuery) {
        val query = searchQuery.trim().lowercase(Locale.getDefault())
        val filteredApps = if (showSystemApps) apps else apps.filterNot { it.isSystem }
        if (query.isEmpty()) {
            filteredApps
        } else {
            filteredApps.filter { app ->
                app.label.lowercase(Locale.getDefault()).contains(query) ||
                    app.packageName.lowercase(Locale.US).contains(query)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        floatingActionButton = { RebootBubble() },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        dynamicStringResource(R.string.launcher_hidden_apps_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, dynamicStringResource(R.string.nav_back))
                    }
                },
                actions = {
                    AnimatedVisibility(
                        visible = needsRestart,
                        enter = fadeIn() + expandHorizontally(),
                        exit = fadeOut() + shrinkHorizontally()
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    LauncherManager.restartLauncher(context)
                                    needsRestart = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(16.dp))
                            Text(dynamicStringResource(R.string.btn_restart))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                )
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .recordLayer(blurState)
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = innerPadding.calculateTopPadding() + 16.dp,
                    end = 16.dp,
                    bottom = innerPadding.calculateBottomPadding() + REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    RestartWarningCard()
                }

                item {
                    SettingsGroupCard(title = dynamicStringResource(R.string.launcher_hidden_apps_controls_title)) {
                        GenericSwitchRow(
                            title = dynamicStringResource(R.string.launcher_hidden_apps_system_title),
                            checked = showSystemApps,
                            onCheckedChange = { showSystemApps = it },
                            summary = dynamicStringResource(R.string.launcher_hidden_apps_system_summary)
                        )
                    }
                }

                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text(dynamicStringResource(R.string.launcher_hidden_apps_search_hint)) }
                    )
                }

                when {
                    isLoading -> item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    loadFailed -> item {
                        EmptyStateText(dynamicStringResource(R.string.launcher_hidden_apps_error))
                    }

                    visibleApps.isEmpty() -> item {
                        EmptyStateText(dynamicStringResource(R.string.launcher_hidden_apps_empty))
                    }

                    else -> {
                        item {
                            Text(
                                text = dynamicStringResource(R.string.launcher_hidden_apps_list_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                            )
                        }

                        items(visibleApps, key = { it.packageName }) { app ->
                            val checked = hiddenPackages.contains(app.packageName)
                            HiddenLauncherAppRow(
                                app = app,
                                checked = checked,
                                onToggle = {
                                    val nextChecked = !checked
                                    hiddenPackages = if (nextChecked) {
                                        hiddenPackages + app.packageName
                                    } else {
                                        hiddenPackages - app.packageName
                                    }
                                    scope.launch {
                                        val applied = setPackageHidden(context, app.packageName, nextChecked)
                                        if (!applied) {
                                            hiddenPackages = if (nextChecked) {
                                                hiddenPackages - app.packageName
                                            } else {
                                                hiddenPackages + app.packageName
                                            }
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.launcher_hidden_apps_apply_error),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            needsRestart = true
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            TopBarBlurOverlay(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                topBarHeight = innerPadding.calculateTopPadding(),
                blurState = blurState,
                isScrolled = isScrolled
            )
        }
    }
}

@Composable
private fun RestartWarningCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
        )
    ) {
        Text(
            text = dynamicStringResource(R.string.launcher_hidden_apps_restart_warning),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun HiddenLauncherAppRow(
    app: HiddenLauncherApp,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (app.isSystem) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = if (checked) 0.42f else 0.22f)
            } else if (checked) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (app.icon != null) {
                Image(
                    bitmap = app.icon.toBitmap().asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(42.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (app.isSystem) {
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                text = dynamicStringResource(R.string.launcher_hidden_apps_system_badge),
                                maxLines = 1
                            )
                        },
                        enabled = false,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Checkbox(
                checked = checked,
                onCheckedChange = null,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
    }
}

@Composable
private fun EmptyStateText(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(20.dp)
        )
    }
}

private suspend fun loadHiddenLauncherApps(context: Context): HiddenLauncherAppsLoadResult =
    withContext(Dispatchers.IO) {
        val manager = context.getSystemService(AxSandboxManager::class.java)
        val packageManager = context.packageManager
        val hiddenPackages = runCatching {
            manager?.getHiddenPackages()?.toSet().orEmpty()
        }.getOrDefault(emptySet())
        val packageNames = (runCatching {
            manager?.getLockablePackages().orEmpty()
        }.getOrDefault(emptyList()).ifEmpty {
            loadLaunchablePackages(packageManager)
        } + hiddenPackages).distinct()

        val apps = packageNames.mapNotNull { packageName ->
            runCatching {
                val info = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
                val label = info.loadLabel(packageManager)?.toString()?.takeIf { it.isNotBlank() }
                    ?: packageName
                HiddenLauncherApp(
                    label = label,
                    packageName = packageName,
                    icon = info.loadIcon(packageManager),
                    isSystem = info.isSystemApplication(),
                    initiallyHidden = hiddenPackages.contains(packageName)
                )
            }.getOrNull()
        }.sortedWith(
            compareByDescending<HiddenLauncherApp> { it.initiallyHidden }
                .thenBy { it.label.lowercase(Locale.getDefault()) }
                .thenBy { it.packageName }
        )

        HiddenLauncherAppsLoadResult(apps, hiddenPackages)
    }

private fun loadLaunchablePackages(packageManager: PackageManager): List<String> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return packageManager.queryIntentActivities(intent, 0)
        .mapNotNull { it.activityInfo?.packageName }
        .distinct()
}

private suspend fun setPackageHidden(
    context: Context,
    packageName: String,
    hidden: Boolean
): Boolean = withContext(Dispatchers.IO) {
    val manager = context.getSystemService(AxSandboxManager::class.java) ?: return@withContext false
    runCatching {
        manager.setPackageHidden(packageName, hidden)
        manager.isPackageHidden(packageName) == hidden
    }.getOrDefault(false)
}

private fun ApplicationInfo.isSystemApplication(): Boolean {
    return (flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
        (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
}