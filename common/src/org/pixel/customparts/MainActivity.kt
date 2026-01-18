package org.pixel.customparts

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pixel.customparts.activities.*
import org.pixel.customparts.utils.RootUtils
import org.pixel.customparts.utils.RemoteStringsManager
import org.pixel.customparts.utils.dynamicStringResource

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val darkTheme = isSystemInDarkTheme()
            val context = LocalContext.current
            
            LaunchedEffect(Unit) {
                try {
                    RemoteStringsManager.initialize(context)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            var rootState by remember { mutableStateOf(if (AppConfig.NEEDS_ROOT_ACCESS) 0 else 1) }

            LaunchedEffect(Unit) {
                if (AppConfig.NEEDS_ROOT_ACCESS) {
                    withContext(Dispatchers.IO) {
                        try {
                            if (RootUtils.hasRootAccess()) {
                                RootUtils.grantPermissions(context)
                                rootState = 1
                            } else {
                                rootState = 2
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            rootState = 2
                        }
                    }
                }
            }

            val colorScheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

            MaterialTheme(colorScheme = colorScheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when (rootState) {
                        0 -> LoadingScreen() 
                        1 -> MainDashboard()
                        2 -> NoRootDialog { finishAffinity() }
                    }
                }
            }
        }
    }
}

@Composable
fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun NoRootDialog(onExit: () -> Unit) {
    AlertDialog(
        onDismissRequest = { },
        icon = { Icon(Icons.Rounded.Security, contentDescription = null) },
        title = { Text(text = dynamicStringResource(R.string.main_root_title)) },
        text = { Text(dynamicStringResource(R.string.main_root_desc)) },
        confirmButton = {
            TextButton(onClick = onExit) { Text(dynamicStringResource(R.string.btn_exit)) }
        },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboard() {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val isCollapsed by remember {
        derivedStateOf { scrollBehavior.state.collapsedFraction > 0.5f }
    }
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(dynamicStringResource(R.string.main_title), fontWeight = FontWeight.Bold)
    
                        AnimatedVisibility(
                            visible = !isCollapsed,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Text(
                                dynamicStringResource(R.string.main_desc),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { activity?.finish() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, dynamicStringResource(R.string.btn_exit))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            val success = RemoteStringsManager.forceRefresh(context)
                            // val message = RemoteStringsManager.getString(context, R.string.donate_page_updated)
                            val message = if (success) RemoteStringsManager.getString(context, R.string.refresh_strings) else RemoteStringsManager.getString(context, R.string.error_network)
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            
                            if (success) {
                                activity?.recreate()
                            }
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = dynamicStringResource(R.string.menu_refresh)
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(300.dp),
            verticalItemSpacing = 8.dp,
            contentPadding = PaddingValues(top = innerPadding.calculateTopPadding() + 16.dp, bottom = 24.dp, start = 16.dp, end = 16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            
            item(span = StaggeredGridItemSpan.FullLine) {
                MenuNavigationCard(
                    title = dynamicStringResource(R.string.donate_title),
                    subtitle = dynamicStringResource(R.string.donate_desc_short),
                    icon = Icons.Rounded.Favorite,
                    iconContainerColor = MaterialTheme.colorScheme.primary,
                    iconContentColor = MaterialTheme.colorScheme.onPrimary,
                    onClick = { context.startActivity(Intent(context, DonateActivity::class.java)) }
                )
            }

            item(span = StaggeredGridItemSpan.FullLine) {
                Spacer(Modifier.height(8.dp))
                SectionHeader(dynamicStringResource(R.string.main_header_gesture))
            }
            
            item(span = StaggeredGridItemSpan.FullLine) {
                MenuNavigationCard(
                    title = dynamicStringResource(R.string.dt_title_activity),
                    subtitle = dynamicStringResource(R.string.dt_desc_activity),
                    icon = Icons.Rounded.TouchApp,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = { context.startActivity(Intent(context, DoubleTapActivity::class.java)) }
                )
            }

            item(span = StaggeredGridItemSpan.FullLine) {
                MenuNavigationCard(
                    title = dynamicStringResource(R.string.os_title_activity),
                    subtitle = dynamicStringResource(R.string.os_desc_activity),
                    icon = Icons.Rounded.Animation,
                    iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    iconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = { context.startActivity(Intent(context, OverscrollActivity::class.java)) }
                )
            }

            item(span = StaggeredGridItemSpan.FullLine) {
                Spacer(Modifier.height(8.dp))
                SectionHeader(dynamicStringResource(R.string.main_header_system))
            }

            item(span = StaggeredGridItemSpan.FullLine) {
                MenuNavigationCard(
                    title = dynamicStringResource(R.string.launcher_title_activity),
                    subtitle = dynamicStringResource(R.string.launcher_desc_activity),
                    icon = Icons.Rounded.Home,
                    iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    iconContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    onClick = { context.startActivity(Intent(context, LauncherActivity::class.java)) }
                )
            }

            if (AppConfig.ENABLE_THERMALS) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    MenuNavigationCard(
                        title = dynamicStringResource(R.string.thermal_title_activity),
                        subtitle = dynamicStringResource(R.string.thermal_desc_activity),
                        icon = Icons.Rounded.Thermostat,
                        iconContainerColor = MaterialTheme.colorScheme.errorContainer,
                        iconContentColor = MaterialTheme.colorScheme.onErrorContainer,
                        onClick = { context.startActivity(Intent(context, ThermalActivity::class.java)) }
                    )
                }
            }

            item(span = StaggeredGridItemSpan.FullLine) {
                Spacer(Modifier.height(8.dp))
                SectionHeader(dynamicStringResource(R.string.main_header_network))
            }

            item(span = StaggeredGridItemSpan.FullLine) {
                MenuNavigationCard(
                    title = dynamicStringResource(R.string.ims_title_activity),
                    subtitle = dynamicStringResource(R.string.ims_desc_activity),
                    icon = Icons.Rounded.NetworkCell,
                    iconContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    iconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { context.startActivity(Intent(context, ImsActivity::class.java)) }
                )
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp, top = 4.dp)
    )
}

@Composable
fun MenuNavigationCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconContainerColor: Color,
    iconContentColor: Color,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconContainerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconContentColor
                )
            }
            Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}