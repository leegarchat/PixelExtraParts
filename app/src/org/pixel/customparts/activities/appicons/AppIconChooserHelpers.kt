package org.pixel.customparts.activities

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp as lerpFloat
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import org.pixel.customparts.R
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import org.pixel.customparts.icons.AppIconCandidate
import org.pixel.customparts.icons.AppIconShapeOverrides
import org.pixel.customparts.icons.IconApplyMode
import org.pixel.customparts.icons.IconMapSnapshot
import org.pixel.customparts.icons.IconPackApplyStatus
import org.pixel.customparts.icons.IconPackEntry
import org.pixel.customparts.icons.IconPackInfo
import org.pixel.customparts.icons.IconPackManager
import org.pixel.customparts.icons.IconPackPreviewItem
import org.pixel.customparts.icons.InstalledIconApp
import org.pixel.customparts.ui.GraphicsLayerRecordingState
import org.pixel.customparts.ui.SliderTitleText
import org.pixel.customparts.ui.edgeOutline
import java.util.Locale
import kotlin.math.roundToInt

internal fun filterChooserItems(items: List<AppIconChooserItem>, query: String): List<AppIconChooserItem> {
    val needle = query.trim().lowercase(Locale.getDefault())
    if (needle.isEmpty()) return items
    return items.filter { item ->
        item.label.lowercase(Locale.getDefault()).contains(needle) ||
            item.iconPackLabel.lowercase(Locale.getDefault()).contains(needle) ||
            item.drawableName.lowercase(Locale.US).contains(needle) ||
            item.appPackageName.orEmpty().lowercase(Locale.US).contains(needle)
    }.sortedWith(
        compareBy<AppIconChooserItem> { chooserSearchRank(it, needle) }
            .thenByDescending { it.detected }
            .thenBy { it.drawableName.lowercase(Locale.US) }
            .thenBy { it.iconPackLabel.lowercase(Locale.getDefault()) }
    )
}

internal fun chooserSearchRank(item: AppIconChooserItem, needle: String): Int {
    return listOf(
        searchableRank(item.drawableName, needle),
        searchableRank(item.label, needle) + 4,
        searchableRank(item.appPackageName.orEmpty(), needle) + 8,
        searchableRank(item.iconPackLabel, needle) + 12
    ).minOrNull() ?: Int.MAX_VALUE
}

internal fun previewSearchRank(item: IconPackPreviewItem, needle: String): Int {
    return listOf(
        searchableRank(item.drawableName, needle),
        searchableRank(item.label, needle) + 4,
        searchableRank(item.appPackageName.orEmpty(), needle) + 8
    ).minOrNull() ?: Int.MAX_VALUE
}

internal fun searchableRank(value: String, needle: String): Int {
    if (value.isBlank()) return Int.MAX_VALUE
    val lower = value.lowercase(Locale.getDefault())
    val tokenIndex = lower
        .replace('.', '_')
        .replace('-', '_')
        .split('_')
        .indexOfFirst { it.startsWith(needle) }
    return when {
        lower == needle -> 0
        lower.startsWith(needle) -> 1
        tokenIndex >= 0 -> 10 + tokenIndex
        lower.contains(needle) -> 100 + lower.indexOf(needle)
        else -> Int.MAX_VALUE
    }
}

@Composable
internal fun AppIconChooserRow(
    item: AppIconChooserItem,
    applied: Boolean,
    onApply: () -> Unit
) {
    val bgColor = MaterialTheme.colorScheme.surface
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = BorderStroke(1.dp, bgColor.edgeOutline())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PreviewIcon(item.iconPackPackage, item.drawableName, 42.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.iconPackLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            StudioTabStyledApplyButton(
                isActive = applied,
                applyLabel = stringResource(R.string.btn_apply),
                activeLabel = stringResource(R.string.app_icons_applied_button),
                enabled = !applied,
                onClick = onApply
            )
        }
    }
}
