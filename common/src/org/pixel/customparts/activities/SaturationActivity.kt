package org.pixel.customparts.activities

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.pixel.customparts.R
import org.pixel.customparts.dynamicDarkColorScheme
import org.pixel.customparts.dynamicLightColorScheme
import org.pixel.customparts.services.SaturationTileService
import org.pixel.customparts.ui.GenericSwitchRow
import org.pixel.customparts.ui.REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING
import org.pixel.customparts.ui.RebootBubble
import org.pixel.customparts.ui.SettingsGroupCard
import org.pixel.customparts.ui.SliderSetting
import org.pixel.customparts.ui.TopBarBlurOverlay
import org.pixel.customparts.ui.recordLayer
import org.pixel.customparts.ui.rememberGraphicsLayerRecordingState
import org.pixel.customparts.utils.SaturationController
import org.pixel.customparts.utils.TileUtils
import org.pixel.customparts.utils.dynamicStringResource

class SaturationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SaturationAppSurface { SaturationScreen(onBack = { finish() }) } }
    }
}

class SaturationTileDialogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SaturationAppSurface {
                SaturationSettingsDialog(onDismiss = { finish() })
            }
        }
    }
}

@Composable
private fun SaturationAppSurface(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colorScheme = if (isSystemInDarkTheme()) {
        dynamicDarkColorScheme(context)
    } else {
        dynamicLightColorScheme(context)
    }

    MaterialTheme(colorScheme = colorScheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaturationScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val blurState = rememberGraphicsLayerRecordingState()
    val listState = rememberLazyListState()
    val isScrolled by remember { derivedStateOf { listState.canScrollBackward } }

    var enabled by remember { mutableStateOf(SaturationController.isEnabled(context)) }
    var percent by remember { mutableIntStateOf(SaturationController.getPercent(context)) }

    fun updateEnabled(value: Boolean) {
        enabled = value
        if (!SaturationController.setEnabled(context, value)) {
            Toast.makeText(context, R.string.saturation_apply_failed, Toast.LENGTH_SHORT).show()
        }
    }

    fun updatePercent(value: Int) {
        percent = value.coerceIn(SaturationController.MIN_PERCENT, SaturationController.MAX_PERCENT)
        SaturationController.setPercent(context, percent)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets.navigationBars,
        floatingActionButton = { RebootBubble() },
        topBar = {
            TopAppBar(
                title = { Text(dynamicStringResource(R.string.saturation_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, dynamicStringResource(R.string.nav_back))
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
                state = listState,
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    SaturationPreviewCard(percent = if (enabled) percent else SaturationController.DEFAULT_PERCENT)
                }

                item {
                    SettingsGroupCard(title = dynamicStringResource(R.string.saturation_title)) {
                        SaturationControls(
                            enabled = enabled,
                            percent = percent,
                            onEnabledChange = ::updateEnabled,
                            onPercentChange = ::updatePercent,
                            onValueChangeFinished = {
                                if (enabled && !SaturationController.applyEffectiveSaturation(context)) {
                                    Toast.makeText(context, R.string.saturation_apply_failed, Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }

                item {
                    Button(
                        onClick = {
                            TileUtils.requestAddTileService(
                                context,
                                SaturationTileService::class.java,
                                R.string.saturation_title,
                                R.drawable.ic_saturation_tile
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(dynamicStringResource(R.string.saturation_add_tile))
                    }
                }

                item {
                    Text(
                        text = dynamicStringResource(R.string.saturation_footer_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }

            TopBarBlurOverlay(
                modifier = Modifier.fillMaxWidth(),
                topBarHeight = 64.dp + WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                blurState = blurState,
                isScrolled = isScrolled
            )
        }
    }
}

@Composable
private fun SaturationSettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(SaturationController.isEnabled(context)) }
    var percent by remember { mutableIntStateOf(SaturationController.getPercent(context)) }

    fun updateEnabled(value: Boolean) {
        enabled = value
        SaturationController.setEnabled(context, value)
    }

    fun updatePercent(value: Int) {
        percent = value.coerceIn(SaturationController.MIN_PERCENT, SaturationController.MAX_PERCENT)
        SaturationController.setPercent(context, percent)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Palette, null) },
        title = { Text(dynamicStringResource(R.string.saturation_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SaturationPreviewCard(
                    percent = if (enabled) percent else SaturationController.DEFAULT_PERCENT,
                    compact = true
                )
                SaturationControls(
                    enabled = enabled,
                    percent = percent,
                    compact = true,
                    onEnabledChange = ::updateEnabled,
                    onPercentChange = ::updatePercent,
                    onValueChangeFinished = { SaturationController.applyEffectiveSaturation(context) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(dynamicStringResource(R.string.btn_apply))
            }
        }
    )
}

@Composable
private fun SaturationControls(
    enabled: Boolean,
    percent: Int,
    compact: Boolean = false,
    onEnabledChange: (Boolean) -> Unit,
    onPercentChange: (Int) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        GenericSwitchRow(
            title = dynamicStringResource(R.string.saturation_enable_title),
            summary = if (compact) null else dynamicStringResource(R.string.saturation_enable_summary),
            checked = enabled,
            onCheckedChange = onEnabledChange
        )

        SliderSetting(
            title = dynamicStringResource(R.string.saturation_level_title),
            value = percent,
            range = SaturationController.MIN_PERCENT..SaturationController.MAX_PERCENT,
            unit = "%",
            enabled = enabled,
            valueText = "$percent%",
            onValueChange = onPercentChange,
            onValueChangeFinished = onValueChangeFinished,
            onDefault = {
                onPercentChange(SaturationController.DEFAULT_PERCENT)
                onValueChangeFinished()
            }
        )
    }
}

@Composable
private fun SaturationPreviewCard(percent: Int, compact: Boolean = false) {
    var page by remember { mutableIntStateOf(0) }
    val pages = 3

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(if (compact) 12.dp else 16.dp)) {
            if (!compact) {
                Text(
                    text = dynamicStringResource(R.string.saturation_preview_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(12.dp))
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(if (compact) 2.2f else 1.65f)
                    .clip(RoundedCornerShape(20.dp))
            ) {
                SaturationPreviewCanvas(page = page, percent = percent)

                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { page = (page - 1).coerceAtLeast(0) },
                        enabled = page > 0,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f))
                    ) {
                        Icon(Icons.Rounded.ChevronLeft, null)
                    }

                    IconButton(
                        onClick = { page = (page + 1).coerceAtMost(pages - 1) },
                        enabled = page < pages - 1,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f))
                    ) {
                        Icon(Icons.Rounded.ChevronRight, null)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(pages) { index ->
                    val dotColor by animateColorAsState(
                        targetValue = if (index == page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        label = "saturationDot"
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (index == page) 9.dp else 7.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                }
            }

            if (!compact) {
                Text(
                    text = "$percent%",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun SaturationPreviewCanvas(page: Int, percent: Int) {
    val primary = SaturationController.previewColor(Color(0xFF4E8CFF), percent)
    val secondary = SaturationController.previewColor(Color(0xFFFF6B7A), percent)
    val tertiary = SaturationController.previewColor(Color(0xFF2DCB8F), percent)
    val accent = SaturationController.previewColor(Color(0xFFFFC857), percent)
    val violet = SaturationController.previewColor(Color(0xFF8F6BFF), percent)

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.linearGradient(
                when (page) {
                    0 -> listOf(primary, tertiary, accent)
                    1 -> listOf(secondary, violet, primary)
                    else -> listOf(tertiary, accent, secondary)
                }
            )
        )

        when (page) {
            0 -> {
                drawCircle(Color.White.copy(alpha = 0.82f), radius = size.minDimension * 0.2f, center = center)
                drawCircle(violet.copy(alpha = 0.75f), radius = size.minDimension * 0.12f, center = center.copy(x = center.x * 0.72f))
                drawCircle(secondary.copy(alpha = 0.75f), radius = size.minDimension * 0.1f, center = center.copy(x = center.x * 1.28f))
            }
            1 -> {
                val stripeWidth = size.width / 6f
                listOf(primary, secondary, tertiary, accent, violet, Color.White.copy(alpha = 0.85f)).forEachIndexed { index, color ->
                    drawRect(color, topLeft = androidx.compose.ui.geometry.Offset(index * stripeWidth, 0f), size = androidx.compose.ui.geometry.Size(stripeWidth, size.height))
                }
                drawCircle(Color.Black.copy(alpha = 0.16f), radius = size.minDimension * 0.32f, center = center, style = Stroke(width = 8.dp.toPx()))
            }
            else -> {
                drawCircle(primary.copy(alpha = 0.86f), radius = size.minDimension * 0.28f, center = center.copy(x = size.width * 0.32f, y = size.height * 0.42f))
                drawCircle(secondary.copy(alpha = 0.82f), radius = size.minDimension * 0.24f, center = center.copy(x = size.width * 0.66f, y = size.height * 0.54f))
                drawCircle(accent.copy(alpha = 0.78f), radius = size.minDimension * 0.18f, center = center.copy(x = size.width * 0.52f, y = size.height * 0.27f))
                drawRect(Color.White.copy(alpha = 0.28f), topLeft = androidx.compose.ui.geometry.Offset(0f, size.height * 0.7f))
            }
        }
    }
}