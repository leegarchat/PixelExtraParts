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
import org.pixel.customparts.utils.RemoteStringsManager
import org.pixel.customparts.utils.ThermalConfigChoice
import org.pixel.customparts.utils.ThermalProfileController
import org.pixel.customparts.utils.ThermalProfileMap
import org.pixel.customparts.utils.TileUtils
import org.pixel.customparts.utils.dynamicStringResource
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

internal fun parseThermalJsonObject(context: Context, text: String): JSONObject {
    val parsed = parseThermalJsonValue(text)
    return parsed as? JSONObject ?: error(context.getString(R.string.thermal_manager_error_root_json_object))
}

internal fun parseThermalJsonValue(text: String): Any {
    return JSONTokener(cleanThermalJson(text)).nextValue()
}

private fun cleanThermalJson(text: String): String {
    return removeTrailingJsonCommas(insertMissingJsonArrayCommas(removeJsonComments(text)))
}

private fun removeJsonComments(text: String): String {
    val output = StringBuilder(text.length)
    var index = 0
    var inString = false
    var escaping = false

    while (index < text.length) {
        val char = text[index]
        if (inString) {
            output.append(char)
            if (escaping) {
                escaping = false
            } else if (char == '\\') {
                escaping = true
            } else if (char == '"') {
                inString = false
            }
            index++
            continue
        }

        if (char == '"') {
            inString = true
            output.append(char)
            index++
            continue
        }

        if (char == '/' && index + 1 < text.length) {
            val next = text[index + 1]
            if (next == '/') {
                index += 2
                while (index < text.length && text[index] != '\n' && text[index] != '\r') {
                    index++
                }
                continue
            }
            if (next == '*') {
                index += 2
                while (index + 1 < text.length && !(text[index] == '*' && text[index + 1] == '/')) {
                    if (text[index] == '\n' || text[index] == '\r') output.append(text[index])
                    index++
                }
                index = (index + 2).coerceAtMost(text.length)
                continue
            }
        }

        output.append(char)
        index++
    }

    return output.toString()
}

private fun removeTrailingJsonCommas(text: String): String {
    val output = StringBuilder(text.length)
    var index = 0
    var inString = false
    var escaping = false

    while (index < text.length) {
        val char = text[index]
        if (inString) {
            output.append(char)
            if (escaping) {
                escaping = false
            } else if (char == '\\') {
                escaping = true
            } else if (char == '"') {
                inString = false
            }
            index++
            continue
        }

        if (char == '"') {
            inString = true
            output.append(char)
            index++
            continue
        }

        if (char == ',') {
            var lookahead = index + 1
            while (lookahead < text.length && text[lookahead].isWhitespace()) {
                lookahead++
            }
            if (lookahead < text.length && (text[lookahead] == '}' || text[lookahead] == ']')) {
                index++
                continue
            }
        }

        output.append(char)
        index++
    }

    return output.toString()
}

private fun insertMissingJsonArrayCommas(text: String): String {
    val output = StringBuilder(text.length)
    val containerStack = mutableListOf<Char>()
    var index = 0
    var inString = false
    var escaping = false

    while (index < text.length) {
        val char = text[index]
        if (inString) {
            output.append(char)
            if (escaping) {
                escaping = false
            } else if (char == '\\') {
                escaping = true
            } else if (char == '"') {
                inString = false
            }
            index++
            continue
        }

        if (char == '"') {
            inString = true
            output.append(char)
            index++
            continue
        }

        when (char) {
            '[', '{' -> containerStack.add(char)
            ']' -> if (containerStack.lastOrNull() == '[') containerStack.removeAt(containerStack.lastIndex)
            '}' -> if (containerStack.lastOrNull() == '{') containerStack.removeAt(containerStack.lastIndex)
        }

        if (char.isWhitespace() && containerStack.lastOrNull() == '[' && shouldInsertArrayComma(text, index)) {
            output.append(',')
        }

        output.append(char)
        index++
    }

    return output.toString()
}

private fun shouldInsertArrayComma(text: String, whitespaceIndex: Int): Boolean {
    var previousIndex = whitespaceIndex - 1
    while (previousIndex >= 0 && text[previousIndex].isWhitespace()) previousIndex--
    if (previousIndex < 0 || !isJsonValueEnd(text[previousIndex])) return false

    var nextIndex = whitespaceIndex + 1
    while (nextIndex < text.length && text[nextIndex].isWhitespace()) nextIndex++
    if (nextIndex >= text.length || !isJsonValueStart(text[nextIndex])) return false

    return true
}

private fun isJsonValueStart(char: Char): Boolean {
    return char == '"' || char == '{' || char == '[' || char == '-' || char.isDigit() ||
        char == 't' || char == 'f' || char == 'n'
}

private fun isJsonValueEnd(char: Char): Boolean {
    return char == '"' || char == '}' || char == ']' || char.isDigit() ||
        char == 'e' || char == 'E' || char == 'l'
}

internal fun mergeArrayByName(target: JSONObject, source: JSONObject, key: String) {
    val sourceArray = source.optJSONArray(key) ?: return
    val targetArray = target.optJSONArray(key) ?: JSONArray().also { target.put(key, it) }
    val names = mutableSetOf<String>()
    for (index in 0 until targetArray.length()) {
        targetArray.optJSONObject(index)?.optString("Name")?.takeIf { it.isNotBlank() }?.let { names += it }
    }
    for (index in 0 until sourceArray.length()) {
        val item = sourceArray.optJSONObject(index) ?: continue
        val name = item.optString("Name")
        if (name.isBlank() || name !in names) {
            targetArray.put(JSONObject(item.toString()))
            if (name.isNotBlank()) names += name
        }
    }
}

internal fun mergeStats(target: JSONObject, source: JSONObject) {
    val sourceStats = source.optJSONObject("Stats") ?: return
    val targetStats = target.optJSONObject("Stats") ?: JSONObject(sourceStats.toString()).also {
        target.put("Stats", it)
        return
    }
    val targetSensors = targetStats.optJSONObject("Sensors") ?: JSONObject().also { targetStats.put("Sensors", it) }
    val sourceSensors = sourceStats.optJSONObject("Sensors") ?: return
    mergeArrayByName(targetSensors, sourceSensors, "RecordWithThreshold")
}

internal fun mergeLogInfo(target: JSONObject, source: JSONObject) {
    val sourceLogInfo = source.optJSONObject("LogInfo") ?: return
    val targetLogInfo = target.optJSONObject("LogInfo") ?: JSONObject(sourceLogInfo.toString()).also {
        target.put("LogInfo", it)
        return
    }
    mergeArrayByName(targetLogInfo, sourceLogInfo, "ExcludedPowerRailsLog")
}
