package org.pixel.customparts.ui.launcher

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.pixel.customparts.R
import org.pixel.customparts.activities.LauncherManager
import org.pixel.customparts.ui.GenericSwitchRow
import org.pixel.customparts.ui.SettingsGroupCard
import org.pixel.customparts.utils.dynamicStringResource

@Composable
fun NativeSearchSection(
    context: Context,
    scope: CoroutineScope,
    nativeSearchEnabled: Boolean,
    onNativeSearchChanged: (Boolean) -> Unit,
    onInfoClick: (String, String, String?) -> Unit
) {
    var feedDisabled by remember { 
        mutableStateOf(Settings.Secure.getInt(context.contentResolver, LauncherManager.KEY_DISABLE_GOOGLE_FEED, 0) == 1) 
    }
    SettingsGroupCard(title = dynamicStringResource(R.string.misc_group_title)) {
        GenericSwitchRow(
            title = dynamicStringResource(R.string.launcher_search_title),
            checked = nativeSearchEnabled,
            onCheckedChange = onNativeSearchChanged,
            summary = null,
            infoText = dynamicStringResource(R.string.launcher_search_desc),
            videoResName = "search_fix",
            onInfoClick = onInfoClick
        )

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            modifier = Modifier.padding(vertical = 4.dp)
        )
        GenericSwitchRow(
            title = dynamicStringResource(R.string.launcher_feed_title),
            checked = feedDisabled,
            onCheckedChange = { checked ->
                feedDisabled = checked
                scope.launch(Dispatchers.IO) {
                    Settings.Secure.putInt(
                        context.contentResolver, 
                        LauncherManager.KEY_DISABLE_GOOGLE_FEED, 
                        if (checked) 1 else 0
                    )
                    LauncherManager.restartLauncher(context)
                }
            },
            summary = null,
            infoText = dynamicStringResource(R.string.launcher_feed_desc),
            videoResName = null,
            onInfoClick = onInfoClick
        )
    }
}