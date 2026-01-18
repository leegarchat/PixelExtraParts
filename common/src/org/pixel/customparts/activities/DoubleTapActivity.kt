package org.pixel.customparts.activities

import android.content.Context
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pixel.customparts.AppConfig
import org.pixel.customparts.R
import org.pixel.customparts.ui.ModuleStatus
import org.pixel.customparts.dynamicDarkColorScheme
import org.pixel.customparts.dynamicLightColorScheme
import org.pixel.customparts.ui.ExpandableWarningCard
import org.pixel.customparts.ui.GenericSwitchRow
import org.pixel.customparts.ui.InfoDialog
import org.pixel.customparts.ui.SettingsGroupCard
import org.pixel.customparts.ui.SliderSetting
import org.pixel.customparts.utils.dynamicStringResource

class DoubleTapActivity : ComponentActivity() {
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
                    DoubleTapScreen(onBack = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoubleTapScreen(onBack: () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var infoDialogTitle by remember { mutableStateOf<String?>(null) }
    var infoDialogText by remember { mutableStateOf<String?>(null) }
    var showXposedInactiveDialog by remember { mutableStateOf(false) }
    var infoDialogVideo by remember { mutableStateOf<String?>(null) }
    var dt2wEnabled by remember { mutableStateOf(DoubleTapManager.isDt2wEnabled(context)) }
    var dt2wTimeout by remember { mutableIntStateOf(DoubleTapManager.getDt2wTimeout(context)) }
    
    if (showXposedInactiveDialog) {
        AlertDialog(
            onDismissRequest = { showXposedInactiveDialog = false },
            title = { Text(dynamicStringResource(R.string.os_dialog_xposed_title)) },
            text = { Text(dynamicStringResource(R.string.os_dialog_xposed_msg)) },
            confirmButton = {
                TextButton(onClick = { showXposedInactiveDialog = false }) {
                    Text(dynamicStringResource(R.string.btn_ok))
                }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            TopAppBar(
                title = { Text(
                        dynamicStringResource(R.string.dt_title_activity),
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
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SettingsGroupCard(title = dynamicStringResource(R.string.dt_sec_wake)) {
                    ExpandableWarningCard(
                        title = dynamicStringResource(R.string.dt2w_info_title),
                        text = dynamicStringResource(R.string.dt2w_info_desc),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    
                    val dt2wDesc = if (AppConfig.IS_XPOSED) dynamicStringResource(R.string.dt2w_desc_xposed) else dynamicStringResource(R.string.dt2w_desc_native)

                    GenericSwitchRow(
                        title = dynamicStringResource(R.string.dt2w_title),
                        checked = dt2wEnabled,
                        onCheckedChange = { checked ->
                            if (checked && AppConfig.IS_XPOSED && !ModuleStatus.isModuleActive()) {
                                showXposedInactiveDialog = true
                                dt2wEnabled = false
                            } else {
                                dt2wEnabled = checked
                                scope.launch { DoubleTapManager.setDt2wEnabled(context, checked) }
                            }
                        },
                        videoResName = "dt2w_hook",
                        infoText = dt2wDesc,
                        onInfoClick = { t, s, v ->
                            infoDialogTitle = t
                            infoDialogText = s
                            infoDialogVideo = v
                        }
                    )
                    
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    
                    SliderSetting(
                        title = dynamicStringResource(R.string.dt2w_timeout_title),
                        value = dt2wTimeout,
                        range = 50..1000,
                        unit = "ms",
                        enabled = dt2wEnabled,
                        onValueChange = { dt2wTimeout = it; scope.launch { DoubleTapManager.setDt2wTimeout(context, it) } },
                        onDefault = { dt2wTimeout = 200; scope.launch { DoubleTapManager.setDt2wTimeout(context, 200) } },
                        infoText = dynamicStringResource(R.string.dt2w_timeout_desc),
                        onInfoClick = { t, s, v -> 
                            infoDialogTitle = t
                            infoDialogText = s
                            infoDialogVideo = v
                        }
                    )
                }
            }

            item {
                Dt2sUiSection(
                    context = context,
                    scope = scope,
                    showXposedDialog = { showXposedInactiveDialog = true },
                    onInfoClick = { t, s, v ->
                        infoDialogTitle = t
                        infoDialogText = s
                        infoDialogVideo = v
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
}

@Composable
fun Dt2sUiSection(
    context: Context,
    scope: CoroutineScope,
    showXposedDialog: () -> Unit,
    onInfoClick: (String, String, String?) -> Unit
) {
    var dt2sEnabled by remember { mutableStateOf(DoubleTapManager.isDt2sEnabled(context)) }
    var dt2sTimeout by remember { mutableIntStateOf(DoubleTapManager.getDt2sTimeout(context)) }
    var dt2sSlop by remember { mutableIntStateOf(DoubleTapManager.getDt2sSlop(context)) }
    
    val systemSlop = remember { android.view.ViewConfiguration.get(context).scaledDoubleTapSlop }

    LaunchedEffect(Unit) {
        if (dt2sSlop == 0) dt2sSlop = systemSlop
    }

    SettingsGroupCard(title = dynamicStringResource(R.string.dt_sec_sleep)) {
        ExpandableWarningCard(
            title = dynamicStringResource(R.string.dt2s_info_title),
            text = dynamicStringResource(R.string.dt2s_info_desc),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        val dt2sDesc = if (AppConfig.IS_XPOSED) dynamicStringResource(R.string.dt2s_desc_xposed) else dynamicStringResource(R.string.dt2s_desc_native)

        GenericSwitchRow(
            title = dynamicStringResource(R.string.dt2s_title),
            checked = dt2sEnabled,
            onCheckedChange = { checked ->
                if (checked && AppConfig.IS_XPOSED && !ModuleStatus.isModuleActive()) {
                    showXposedDialog()
                    dt2sEnabled = false 
                } else {
                    dt2sEnabled = checked
                    scope.launch { DoubleTapManager.setDt2sEnabled(context, checked) }
                }
            },
            videoResName = "dt2s_hook",
            infoText = dt2sDesc,
            onInfoClick = onInfoClick
        )

        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        SliderSetting(
            title = dynamicStringResource(R.string.dt2s_timeout_title),
            value = dt2sTimeout,
            range = 50..1000,
            unit = "ms",
            enabled = dt2sEnabled,
            onValueChange = { dt2sTimeout = it; scope.launch { DoubleTapManager.setDt2sTimeout(context, it) } },
            onDefault = { dt2sTimeout = 150; scope.launch { DoubleTapManager.setDt2sTimeout(context, 150) } },
            infoText = dynamicStringResource(R.string.dt2s_timeout_title), 
            onInfoClick = onInfoClick
        )
    }
}

object DoubleTapManager {
    val KEY_DT2W_ENABLE: String
        get() = if (AppConfig.IS_XPOSED) "doze_double_tap_hook_xposed" else "doze_double_tap_hook"

    val KEY_DT2S_ENABLE: String
        get() = if (AppConfig.IS_XPOSED) "launcher_dt2s_enabled_xposed" else "launcher_dt2s_enabled"
    
    private const val KEY_DT2W_TIMEOUT = "doze_double_tap_timeout"
    private const val KEY_DT2S_TIMEOUT = "launcher_dt2s_timeout"
    private const val KEY_DT2S_SLOP = "launcher_dt2s_slop"

    fun isDt2wEnabled(context: Context): Boolean {
        return Settings.Secure.getInt(context.contentResolver, KEY_DT2W_ENABLE, 0) == 1
    }

    suspend fun setDt2wEnabled(context: Context, enabled: Boolean) = withContext(Dispatchers.IO) {
        Settings.Secure.putInt(context.contentResolver, KEY_DT2W_ENABLE, if (enabled) 1 else 0)
    }

    fun getDt2wTimeout(context: Context): Int {
        return Settings.Secure.getInt(context.contentResolver, KEY_DT2W_TIMEOUT, 200)
    }

    suspend fun setDt2wTimeout(context: Context, value: Int) = withContext(Dispatchers.IO) {
        Settings.Secure.putInt(context.contentResolver, KEY_DT2W_TIMEOUT, value)
    }


    fun isDt2sEnabled(context: Context): Boolean {
        return Settings.Secure.getInt(context.contentResolver, KEY_DT2S_ENABLE, 0) == 1
    }

    suspend fun setDt2sEnabled(context: Context, enabled: Boolean) = withContext(Dispatchers.IO) {
        Settings.Secure.putInt(context.contentResolver, KEY_DT2S_ENABLE, if (enabled) 1 else 0)
    }

    fun getDt2sTimeout(context: Context): Int {
        return Settings.Secure.getInt(context.contentResolver, KEY_DT2S_TIMEOUT, 150)
    }

    suspend fun setDt2sTimeout(context: Context, value: Int) = withContext(Dispatchers.IO) {
        Settings.Secure.putInt(context.contentResolver, KEY_DT2S_TIMEOUT, value)
    }

    fun getDt2sSlop(context: Context): Int {
        return Settings.Secure.getInt(context.contentResolver, KEY_DT2S_SLOP, 0)
    }

    suspend fun setDt2sSlop(context: Context, value: Int) = withContext(Dispatchers.IO) {
        Settings.Secure.putInt(context.contentResolver, KEY_DT2S_SLOP, value)
    }
}