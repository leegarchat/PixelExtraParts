package org.pixel.customparts.activities

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.pixel.customparts.R
import org.pixel.customparts.dynamicDarkColorScheme
import org.pixel.customparts.dynamicLightColorScheme
import org.pixel.customparts.services.ThermalManagerTileService
import org.pixel.customparts.ui.REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING
import org.pixel.customparts.ui.RebootBubble
import org.pixel.customparts.ui.RebootBubbleMenuAction
import org.pixel.customparts.ui.SettingsGroupCard
import org.pixel.customparts.ui.TopBarBlurOverlay
import org.pixel.customparts.ui.TopStatusVeil
import org.pixel.customparts.ui.recordLayer
import org.pixel.customparts.ui.rememberGraphicsLayerRecordingState
import org.pixel.customparts.utils.ThermalConfigChoice
import org.pixel.customparts.utils.ThermalProfileController
import org.pixel.customparts.utils.ThermalProfileMap
import org.pixel.customparts.utils.TileUtils
import java.io.File
import java.text.DecimalFormat
import java.text.Normalizer
import java.util.Locale

private const val VENDOR_THERMAL_CONFIG = "/vendor/etc/thermal_info_config.json"
private const val STOCK_THERMAL_CONFIG_FILE_NAME = "thermal_info_config.json"
private const val RESERVED_CUSTOM_SUFFIX = "_custom"
private const val EXTRA_OPEN_CREATE_PROFILE = "org.pixel.customparts.extra.OPEN_THERMAL_PROFILE_CREATOR"

private val FILE_NAME_TRANSLIT = mapOf(
    'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e",
    'ё' to "e", 'ж' to "zh", 'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k",
    'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r",
    'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f", 'х' to "h", 'ц' to "c",
    'ч' to "ch", 'ш' to "sh", 'щ' to "sch", 'ъ' to "", 'ы' to "y", 'ь' to "",
    'э' to "e", 'ю' to "yu", 'я' to "ya", 'і' to "i", 'ї' to "yi", 'є' to "ye",
    'ґ' to "g"
)

internal class ThermalConfigStore(private val context: Context) {
    fun readVendorConfig(): String = loadConfigWithIncludes(File(VENDOR_THERMAL_CONFIG), mutableSetOf()).toString(4)

    fun listSavedConfigs(): List<SavedThermalConfig> {
        val dir = ensureConfigDir()
        return dir.listFiles { file -> file.isFile && file.name.endsWith(".json", ignoreCase = true) }
            ?.filterNot {
                it.name == ThermalProfileController.MAP_FILE_NAME ||
                    it.name == ThermalProfileController.PROFILE_METADATA_FILE_NAME ||
                    it.name.equals(STOCK_THERMAL_CONFIG_FILE_NAME, ignoreCase = true)
            }
            ?.sortedByDescending { it.lastModified() }
            ?.map { file ->
                val metadata = ThermalProfileController.profileMetadata(file.name)
                val source = metadata?.source ?: if (ThermalProfileController.isVendorPreset(file.name)) {
                    ThermalProfileController.PROFILE_SOURCE_SYSTEM
                } else {
                    ThermalProfileController.PROFILE_SOURCE_USER
                }
                SavedThermalConfig(
                    name = file.name,
                    displayName = metadata?.displayName?.takeIf { it.isNotBlank() } ?: file.name.removeSuffix(".json"),
                    source = source,
                    file = file,
                    sizeText = formatBytes(file.length()),
                    locked = source == ThermalProfileController.PROFILE_SOURCE_SYSTEM
                )
            }
            .orEmpty()
    }

    fun saveConfig(fileName: String, json: String, displayName: String): File {
        val safeFileName = avoidReservedCustomConfigName(fileName)
        if (ThermalProfileController.isVendorPreset(safeFileName)) {
            error(context.getString(R.string.thermal_manager_vendor_read_only))
        }

        val dir = ensureConfigDir()
        val file = File(dir, safeFileName)
        writeTextAtomically(file, json)
        file.setReadable(true, false)
        file.setWritable(true, true)
        ThermalProfileController.writeUserProfileMetadata(file.name, displayName, file.absolutePath)
        return file
    }

    fun readUri(uri: Uri): String = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        ?: error(context.getString(R.string.thermal_manager_error_read_selected_file))

    fun writeUri(uri: Uri, text: String) {
        context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(text) }
            ?: error(context.getString(R.string.thermal_manager_error_open_export_target))
    }

    fun displayName(uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index)
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun ensureConfigDir(): File {
        val dir = File(ThermalProfileController.CONFIG_DIR)
        if (!dir.exists() && !dir.mkdirs()) {
            error(context.getString(R.string.thermal_manager_error_create_dir, ThermalProfileController.CONFIG_DIR))
        }
        dir.setReadable(true, false)
        dir.setExecutable(true, false)
        return dir
    }

    private fun writeTextAtomically(file: File, text: String) {
        val temp = File(file.parentFile, ".${file.name}.${System.nanoTime()}.tmp")
        temp.writeText(text)
        temp.setReadable(true, false)
        temp.setWritable(true, true)
        if (!temp.renameTo(file)) {
            temp.delete()
            error(context.getString(R.string.thermal_manager_error_save, file.name))
        }
    }

    private fun loadConfigWithIncludes(file: File, loadedPaths: MutableSet<String>): JSONObject {
        val normalizedPath = file.absolutePath
        if (!loadedPaths.add(normalizedPath)) {
            error(context.getString(R.string.thermal_manager_error_circular_include, normalizedPath))
        }

        val root = parseThermalJsonObject(context, file.readText())
        val includes = root.optJSONArray("Include") ?: return root
        for (index in 0 until includes.length()) {
            val includeName = includes.optString(index).takeIf { it.isNotBlank() } ?: continue
            val includeFile = if (includeName.startsWith('/')) {
                File(includeName)
            } else {
                File(File(VENDOR_THERMAL_CONFIG).parentFile, includeName)
            }
            val included = loadConfigWithIncludes(includeFile, loadedPaths)
            mergeArrayByName(root, included, "Sensors")
            mergeArrayByName(root, included, "CoolingDevices")
            mergeArrayByName(root, included, "PowerRails")
            mergeStats(root, included)
            mergeLogInfo(root, included)
        }
        return root
    }
}
