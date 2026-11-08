package org.pixel.extra.parts.icons

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pixel.extra.parts.R
import org.pixel.extra.parts.ui.GroupCard
import org.pixel.extra.parts.ui.SettingSlider
import org.pixel.extra.parts.ui.edgeOutline
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

private const val DEFAULT_CUSTOM_SHAPE_SIDES = 6
private const val DEFAULT_CUSTOM_SHAPE_ROUNDNESS = 4.5f
private const val DEFAULT_CUSTOM_SHAPE_INSET = 0f
private const val DEFAULT_CUSTOM_SHAPE_WAVE = 0.12f
private const val DEFAULT_CUSTOM_SHAPE_ROTATION = 0f
private const val DEFAULT_CUSTOM_SHAPE_SCALE = 1f
private const val DEFAULT_CUSTOM_SHAPE_OFFSET = 0f
private const val DEFAULT_CUSTOM_SHAPE_NAME = "PixelParts custom"

// Shape hub: parametric builder (superellipse/polygon/flower + transforms),
// SVG import, and the full option list (default, installed overlays, built-in
// presets, fabricated custom). Builds register a fabricated overlay, so no
// compiler backend is needed.
@Composable
internal fun IconsShapeSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var options by remember { mutableStateOf<List<ShapeOverlayManager.ShapeOption>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isBusy by remember { mutableStateOf(false) }
    var customName by rememberSaveable { mutableStateOf(DEFAULT_CUSTOM_SHAPE_NAME) }
    var mode by rememberSaveable { mutableIntStateOf(ShapeOverlayManager.MODE_SUPERELLIPSE) }
    var sides by rememberSaveable { mutableIntStateOf(DEFAULT_CUSTOM_SHAPE_SIDES) }
    var roundness by rememberSaveable { mutableStateOf(DEFAULT_CUSTOM_SHAPE_ROUNDNESS) }
    var inset by rememberSaveable { mutableStateOf(DEFAULT_CUSTOM_SHAPE_INSET) }
    var wave by rememberSaveable { mutableStateOf(DEFAULT_CUSTOM_SHAPE_WAVE) }
    var rotation by rememberSaveable { mutableStateOf(DEFAULT_CUSTOM_SHAPE_ROTATION) }
    var scaleX by rememberSaveable { mutableStateOf(DEFAULT_CUSTOM_SHAPE_SCALE) }
    var scaleY by rememberSaveable { mutableStateOf(DEFAULT_CUSTOM_SHAPE_SCALE) }
    var offsetX by rememberSaveable { mutableStateOf(DEFAULT_CUSTOM_SHAPE_OFFSET) }
    var offsetY by rememberSaveable { mutableStateOf(DEFAULT_CUSTOM_SHAPE_OFFSET) }
    var importedPath by rememberSaveable { mutableStateOf("") }
    var compilePhase by remember { mutableStateOf<ShapeCompilePhase?>(null) }
    var compileProgress by remember { mutableStateOf(0f) }
    var compileLog by remember { mutableStateOf<List<String>>(emptyList()) }
    var compileError by remember { mutableStateOf<String?>(null) }
    var compileCancelFlag by remember { mutableStateOf<AtomicBoolean?>(null) }

    val params = ShapeOverlayManager.CustomParams(mode, sides, roundness, inset, wave, rotation, scaleX, scaleY, offsetX, offsetY)
    val generatedPath = remember(params) { ShapeOverlayManager.generatePath(params) }

    suspend fun refreshOptions(showLoading: Boolean = true) {
        if (showLoading) isLoading = true
        options = withContext(Dispatchers.IO) { ShapeOverlayManager.loadOptions(context) }
        isLoading = false
    }

    fun applyOption(option: ShapeOverlayManager.ShapeOption) {
        if (isBusy) return
        scope.launch {
            isBusy = true
            val success = withContext(Dispatchers.IO) { ShapeOverlayManager.applyOption(context, option) }
            Toast.makeText(
                context,
                context.getString(
                    if (success) R.string.icons_shape_apply_success else R.string.icons_shape_apply_failed,
                ),
                Toast.LENGTH_SHORT,
            ).show()
            if (success) PacksManager.requestIconReload(context)
            refreshOptions()
            isBusy = false
        }
    }

    fun buildShape(label: String, pathData: String) {
        if (isBusy || isLoading || pathData.isBlank()) return
        isBusy = true
        compilePhase = ShapeCompilePhase.COMPILING
        compileProgress = 0.05f
        compileLog = emptyList()
        compileError = null
        compileCancelFlag = null
        val cancelled = AtomicBoolean(false)
        compileCancelFlag = cancelled
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                ShapeOverlayManager.compileAndInstallCustom(
                    context = context,
                    label = label,
                    pathData = pathData,
                    logCallback = { line -> compileLog = compileLog + line },
                    installLogCallback = { line -> compileLog = compileLog + line },
                )
            }
            if (cancelled.get()) {
                compilePhase = ShapeCompilePhase.CANCELLED
            } else if (result.success && result.packageName != null) {
                compileLog = result.log
                compilePhase = ShapeCompilePhase.SUCCESS
                compileProgress = 1f
                PacksManager.requestIconReload(context)
                refreshOptions(showLoading = false)
            } else {
                compilePhase = ShapeCompilePhase.ERROR
                compileError = result.error ?: context.getString(R.string.icons_shape_compile_error)
                compileLog = result.log
            }
            isBusy = false
        }
    }

    fun deleteOption(option: ShapeOverlayManager.ShapeOption) {
        if (isBusy) return
        scope.launch {
            isBusy = true
            val success = withContext(Dispatchers.IO) { ShapeOverlayManager.deleteCustomOverlay(context, option) }
            Toast.makeText(
                context,
                context.getString(
                    if (success) R.string.icons_shape_delete_success else R.string.icons_shape_delete_failed,
                ),
                Toast.LENGTH_SHORT,
            ).show()
            if (success) PacksManager.requestIconReload(context)
            refreshOptions(showLoading = false)
            isBusy = false
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val path = withContext(Dispatchers.IO) { ShapeOverlayManager.readCustomPath(context, uri) }
                importedPath = path
                Toast.makeText(
                    context,
                    context.getString(
                        if (ShapeOverlayManager.isValidPath(path)) {
                            R.string.icons_shape_import_success
                        } else {
                            R.string.icons_custom_invalid
                        },
                    ),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    LaunchedEffect(Unit) { refreshOptions() }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GroupCard(title = stringResource(R.string.icons_custom_section)) {
            ShapeBuilder(
                mode = mode,
                sides = sides,
                roundness = roundness,
                inset = inset,
                wave = wave,
                rotation = rotation,
                scaleX = scaleX,
                scaleY = scaleY,
                offsetX = offsetX,
                offsetY = offsetY,
                pathData = generatedPath,
                customName = customName,
                importedPath = importedPath,
                isBusy = isBusy || isLoading,
                onModeChange = { mode = it },
                onSidesChange = { sides = it },
                onRoundnessChange = { roundness = it },
                onInsetChange = { inset = it },
                onWaveChange = { wave = it },
                onRotationChange = { rotation = it },
                onScaleXChange = { scaleX = it },
                onScaleYChange = { scaleY = it },
                onOffsetXChange = { offsetX = it },
                onOffsetYChange = { offsetY = it },
                onNameChange = { customName = it },
                onCompileGenerated = { buildShape(customName, generatedPath) },
                onPickFile = { importLauncher.launch("*/*") },
                onCompileImported = { buildShape(customName, importedPath) },
            )
        }
        GroupCard(title = stringResource(R.string.icons_shape_presets)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = { scope.launch { refreshOptions() } },
                    enabled = !isBusy,
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.icons_shape_refresh))
                }
            }
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                options.forEach { option ->
                    ShapeOptionCard(
                        option = option,
                        isBusy = isBusy,
                        onApply = { applyOption(option) },
                        onBuild = if (option.source == ShapeOverlayManager.ShapeSource.BUILTIN_PRESET) {
                            { buildShape(option.label, option.pathData) }
                        } else {
                            null
                        },
                        onDelete = if (option.source == ShapeOverlayManager.ShapeSource.CUSTOM_OVERLAY) {
                            { deleteOption(option) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }

    if (compilePhase != null) {
        ShapeCompileDialog(
            phase = compilePhase!!,
            progress = compileProgress,
            log = compileLog,
            error = compileError,
            onCancel = {
                compileCancelFlag?.set(true)
                isBusy = false
                compilePhase = ShapeCompilePhase.CANCELLED
            },
            onDismiss = { compilePhase = null },
        )
    }
}

private enum class ShapeCompilePhase {
    COMPILING,
    SUCCESS,
    ERROR,
    CANCELLED,
}

@Composable
private fun ShapeCompileDialog(
    phase: ShapeCompilePhase,
    progress: Float,
    log: List<String>,
    error: String?,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val terminal = phase != ShapeCompilePhase.COMPILING
    AlertDialog(
        onDismissRequest = { if (terminal) onDismiss() },
        title = {
            Text(
                stringResource(
                    when (phase) {
                        ShapeCompilePhase.COMPILING -> R.string.icons_shape_compile_compiling
                        ShapeCompilePhase.SUCCESS -> R.string.icons_shape_compile_success
                        ShapeCompilePhase.ERROR -> R.string.icons_shape_compile_error
                        ShapeCompilePhase.CANCELLED -> R.string.icons_shape_compile_cancelled
                    },
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(
                    progress = progress.coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                log.takeLast(6).forEach { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        confirmButton = {
            if (terminal) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.icons_shape_compile_ok)) }
            } else {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.icons_shape_compile_cancel)) }
            }
        },
    )
}

@Composable
private fun ShapeBuilder(
    mode: Int,
    sides: Int,
    roundness: Float,
    inset: Float,
    wave: Float,
    rotation: Float,
    scaleX: Float,
    scaleY: Float,
    offsetX: Float,
    offsetY: Float,
    pathData: String,
    customName: String,
    importedPath: String,
    isBusy: Boolean,
    onModeChange: (Int) -> Unit,
    onSidesChange: (Int) -> Unit,
    onRoundnessChange: (Float) -> Unit,
    onInsetChange: (Float) -> Unit,
    onWaveChange: (Float) -> Unit,
    onRotationChange: (Float) -> Unit,
    onScaleXChange: (Float) -> Unit,
    onScaleYChange: (Float) -> Unit,
    onOffsetXChange: (Float) -> Unit,
    onOffsetYChange: (Float) -> Unit,
    onNameChange: (String) -> Unit,
    onCompileGenerated: () -> Unit,
    onPickFile: () -> Unit,
    onCompileImported: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconsShapePreview(
                pathData = pathData,
                active = true,
                fillColor = MaterialTheme.colorScheme.primary.toArgb(),
                strokeColor = MaterialTheme.colorScheme.onPrimary.toArgb(),
                modifier = Modifier.size(88.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = customName,
                    onValueChange = onNameChange,
                    singleLine = true,
                    label = { Text(stringResource(R.string.icons_custom_name)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = onCompileGenerated, enabled = !isBusy) {
                    Text(stringResource(R.string.icons_custom_apply))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShapeModeChip(
                selected = mode == ShapeOverlayManager.MODE_SUPERELLIPSE,
                label = stringResource(R.string.icons_shape_mode_superellipse),
                onClick = { onModeChange(ShapeOverlayManager.MODE_SUPERELLIPSE) },
            )
            ShapeModeChip(
                selected = mode == ShapeOverlayManager.MODE_POLYGON,
                label = stringResource(R.string.icons_shape_mode_polygon),
                onClick = { onModeChange(ShapeOverlayManager.MODE_POLYGON) },
            )
            ShapeModeChip(
                selected = mode == ShapeOverlayManager.MODE_FLOWER,
                label = stringResource(R.string.icons_shape_mode_flower),
                onClick = { onModeChange(ShapeOverlayManager.MODE_FLOWER) },
            )
        }
        if (mode == ShapeOverlayManager.MODE_SUPERELLIPSE) {
            SettingSlider(
                title = stringResource(R.string.icons_shape_roundness),
                value = roundness,
                onValueChange = onRoundnessChange,
                valueRange = 1.5f..8f,
                step = 0.1f,
                defaultValue = DEFAULT_CUSTOM_SHAPE_ROUNDNESS,
                resetLabel = stringResource(R.string.icons_reset),
                valueLabel = { v -> String.format(Locale.US, "%.2f", v) },
            )
        } else {
            SettingSlider(
                title = stringResource(R.string.icons_shape_sides),
                value = sides.toFloat(),
                onValueChange = { onSidesChange(it.toInt().coerceIn(3, 12)) },
                valueRange = 3f..12f,
                step = 1f,
                defaultValue = DEFAULT_CUSTOM_SHAPE_SIDES.toFloat(),
                resetLabel = stringResource(R.string.icons_reset),
                valueLabel = { v -> v.toInt().toString() },
            )
        }
        SettingSlider(
            title = stringResource(R.string.icons_shape_inset),
            value = inset,
            onValueChange = onInsetChange,
            valueRange = 0f..24f,
            step = 0.5f,
            defaultValue = DEFAULT_CUSTOM_SHAPE_INSET,
            resetLabel = stringResource(R.string.icons_reset),
            valueLabel = { v -> String.format(Locale.US, "%.2f", v) },
        )
        if (mode == ShapeOverlayManager.MODE_FLOWER) {
            SettingSlider(
                title = stringResource(R.string.icons_shape_wave),
                value = wave,
                onValueChange = onWaveChange,
                valueRange = 0f..0.35f,
                step = 0.01f,
                defaultValue = DEFAULT_CUSTOM_SHAPE_WAVE,
                resetLabel = stringResource(R.string.icons_reset),
                valueLabel = { v -> String.format(Locale.US, "%.2f", v) },
            )
        }
        SettingSlider(
            title = stringResource(R.string.icons_shape_rotation),
            value = rotation,
            onValueChange = onRotationChange,
            valueRange = -180f..180f,
            step = 1f,
            defaultValue = DEFAULT_CUSTOM_SHAPE_ROTATION,
            resetLabel = stringResource(R.string.icons_reset),
            valueLabel = { v -> String.format(Locale.US, "%.0f°", v) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingSlider(
                title = stringResource(R.string.icons_shape_scale_x),
                value = scaleX,
                onValueChange = onScaleXChange,
                valueRange = 0.5f..1.5f,
                step = 0.05f,
                defaultValue = DEFAULT_CUSTOM_SHAPE_SCALE,
                resetLabel = stringResource(R.string.icons_reset),
                valueLabel = { v -> String.format(Locale.US, "%.2f", v) },
                modifier = Modifier.weight(1f),
            )
            SettingSlider(
                title = stringResource(R.string.icons_shape_scale_y),
                value = scaleY,
                onValueChange = onScaleYChange,
                valueRange = 0.5f..1.5f,
                step = 0.05f,
                defaultValue = DEFAULT_CUSTOM_SHAPE_SCALE,
                resetLabel = stringResource(R.string.icons_reset),
                valueLabel = { v -> String.format(Locale.US, "%.2f", v) },
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingSlider(
                title = stringResource(R.string.icons_shape_offset_x),
                value = offsetX,
                onValueChange = onOffsetXChange,
                valueRange = -25f..25f,
                step = 0.5f,
                defaultValue = DEFAULT_CUSTOM_SHAPE_OFFSET,
                resetLabel = stringResource(R.string.icons_reset),
                valueLabel = { v -> String.format(Locale.US, "%.2f", v) },
                modifier = Modifier.weight(1f),
            )
            SettingSlider(
                title = stringResource(R.string.icons_shape_offset_y),
                value = offsetY,
                onValueChange = onOffsetYChange,
                valueRange = -25f..25f,
                step = 0.5f,
                defaultValue = DEFAULT_CUSTOM_SHAPE_OFFSET,
                resetLabel = stringResource(R.string.icons_reset),
                valueLabel = { v -> String.format(Locale.US, "%.2f", v) },
                modifier = Modifier.weight(1f),
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onPickFile, enabled = !isBusy) {
                Icon(Icons.Rounded.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.icons_shape_import))
            }
            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = onCompileImported,
                enabled = !isBusy && ShapeOverlayManager.isValidPath(importedPath),
            ) {
                Text(stringResource(R.string.icons_shape_import_apply))
            }
        }
        AnimatedVisibility(
            visible = importedPath.isNotBlank() && ShapeOverlayManager.isValidPath(importedPath),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            IconsShapePreview(
                pathData = importedPath,
                active = false,
                fillColor = MaterialTheme.colorScheme.secondaryContainer.toArgb(),
                strokeColor = MaterialTheme.colorScheme.outline.toArgb(),
                modifier = Modifier.size(72.dp),
            )
        }
    }
}

@Composable
private fun ShapeModeChip(selected: Boolean, label: String, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label, maxLines = 1) })
}

@Composable
private fun ShapeOptionCard(
    option: ShapeOverlayManager.ShapeOption,
    isBusy: Boolean,
    onApply: () -> Unit,
    onBuild: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    var showConfirmDelete by remember { mutableStateOf(false) }
    val cardBorder = if (option.source != ShapeOverlayManager.ShapeSource.DEFAULT) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    } else {
        null
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = cardBorder,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !isBusy && !option.active && onBuild == null) { onApply() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconsShapePreview(
                pathData = option.pathData,
                active = option.active,
                fillColor = (if (option.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer).toArgb(),
                strokeColor = (if (option.active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline).toArgb(),
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = shapeSourceLabel(option.source),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (option.active) {
                    Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.icons_shape_active))
                } else if (onBuild != null) {
                    Button(onClick = onBuild, enabled = !isBusy) {
                        Icon(Icons.Rounded.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.icons_custom_apply))
                    }
                } else {
                    TextButton(onClick = onApply, enabled = !isBusy) {
                        Text(stringResource(R.string.icons_shape_apply))
                    }
                }
                if (onDelete != null) {
                    if (!showConfirmDelete) {
                        IconButton(onClick = { showConfirmDelete = true }, enabled = !isBusy) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = stringResource(R.string.icons_shape_delete),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    } else {
                        IconButton(onClick = { showConfirmDelete = false; onDelete() }, enabled = !isBusy) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = stringResource(R.string.icons_shape_delete_confirm),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                        IconButton(onClick = { showConfirmDelete = false }, enabled = !isBusy) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun shapeSourceLabel(source: ShapeOverlayManager.ShapeSource): String {
    return when (source) {
        ShapeOverlayManager.ShapeSource.DEFAULT -> stringResource(R.string.icons_shape_source_default)
        ShapeOverlayManager.ShapeSource.INSTALLED_OVERLAY -> stringResource(R.string.icons_shape_source_installed)
        ShapeOverlayManager.ShapeSource.BUILTIN_PRESET -> stringResource(R.string.icons_shape_source_builtin)
        ShapeOverlayManager.ShapeSource.CUSTOM_OVERLAY -> stringResource(R.string.icons_shape_source_custom)
    }
}
