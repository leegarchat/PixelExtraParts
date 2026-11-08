package org.pixel.extra.parts.activities

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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
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
import org.pixel.extra.parts.R
import org.pixel.extra.parts.dynamicDarkColorScheme
import org.pixel.extra.parts.dynamicLightColorScheme
import org.pixel.extra.parts.services.ThermalManagerTileService
import org.pixel.extra.parts.ui.legacy.REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING
import org.pixel.extra.parts.ui.legacy.RebootBubble
import org.pixel.extra.parts.ui.legacy.RebootBubbleMenuAction
import org.pixel.extra.parts.ui.legacy.SettingsGroupCard
import org.pixel.extra.parts.ui.TopBarBlurOverlay
import org.pixel.extra.parts.ui.recordLayer
import org.pixel.extra.parts.ui.rememberGraphicsLayerRecordingState
import org.pixel.extra.parts.utils.ThermalConfigChoice
import org.pixel.extra.parts.utils.ThermalProfileController
import org.pixel.extra.parts.utils.ThermalProfileMap
import org.pixel.extra.parts.utils.TileUtils
import java.io.File
import java.text.DecimalFormat
import java.text.Normalizer
import java.util.Locale
import androidx.compose.ui.res.stringResource


internal fun removeJsonComments(text: String): String {
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

internal fun removeTrailingJsonCommas(text: String): String {
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

internal fun insertMissingJsonArrayCommas(text: String): String {
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

internal fun shouldInsertArrayComma(text: String, whitespaceIndex: Int): Boolean {
    var previousIndex = whitespaceIndex - 1
    while (previousIndex >= 0 && text[previousIndex].isWhitespace()) previousIndex--
    if (previousIndex < 0 || !isJsonValueEnd(text[previousIndex])) return false

    var nextIndex = whitespaceIndex + 1
    while (nextIndex < text.length && text[nextIndex].isWhitespace()) nextIndex++
    if (nextIndex >= text.length || !isJsonValueStart(text[nextIndex])) return false

    return true
}

internal fun isJsonValueStart(char: Char): Boolean {
    return char == '"' || char == '{' || char == '[' || char == '-' || char.isDigit() ||
        char == 't' || char == 'f' || char == 'n'
}

internal fun isJsonValueEnd(char: Char): Boolean {
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

internal data class SavedThermalConfig(
    val name: String,
    val displayName: String,
    val source: String,
    val file: File,
    val sizeText: String,
    val locked: Boolean
)

internal data class ImportedThermalConfig(
    val file: File,
    val displayName: String,
    val json: String
)

internal data class ThermalManagedApp(
    val label: String,
    val packageName: String,
    val icon: Drawable?,
    val isSystem: Boolean
)

internal data class ThermalParamTreeNode(
    val id: String,
    val title: String,
    val children: List<ThermalParamTreeNode> = emptyList(),
    val params: List<ThermalConfigParam> = emptyList()
) {
    val leafCount: Int
        get() = params.size + children.sumOf { it.leafCount }
}

internal sealed interface ProfilePickerTarget {
    object Global : ProfilePickerTarget
    object TileQueue : ProfilePickerTarget
    data class Package(val app: ThermalManagedApp) : ProfilePickerTarget
}

internal suspend fun loadThermalManagedApps(context: Context): List<ThermalManagedApp> =
    withContext(Dispatchers.IO) {
        val packageManager = context.packageManager
        val assignedPackages = ThermalProfileController.readProfileMap().packageConfigs.keys
        val installedPackages = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .map { it.packageName }
        val assignedSet = assignedPackages.toSet()

        (installedPackages + assignedPackages).distinct().mapNotNull { packageName ->
            runCatching {
                val info = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
                val label = info.loadLabel(packageManager)?.toString()?.takeIf { it.isNotBlank() }
                    ?: packageName
                ThermalManagedApp(
                    label = label,
                    packageName = packageName,
                    icon = info.loadIcon(packageManager),
                    isSystem = info.isSystemApplication()
                )
            }.getOrNull()
        }.sortedWith(
            compareByDescending<ThermalManagedApp> { assignedSet.contains(it.packageName) }
                .thenBy { it.label.lowercase(Locale.getDefault()) }
                .thenBy { it.packageName }
        )
    }

internal fun ApplicationInfo.isSystemApplication(): Boolean {
    return (flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
        (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
}

internal data class ThermalConfigParam(
    val id: String,
    val key: String,
    val section: String,
    val owner: String,
    val targetName: String?,
    val displayPath: String,
    val path: List<JsonPathToken>,
    val kind: ThermalValueKind,
    val value: String,
    val valueLabel: String,
    val help: String,
    val knownField: Boolean,
    val throttlingField: Boolean,
    val searchText: String
)

internal enum class ThermalValueKind {
    STRING,
    NUMBER,
    BOOLEAN,
    ARRAY,
    NULL
}

internal enum class ThermalEditorTab(val labelRes: Int) {
    SOC(R.string.thermal_manager_editor_tab_soc),
    BATTERY(R.string.thermal_manager_editor_tab_battery),
    ALL(R.string.thermal_manager_editor_tab_all)
}

internal val THERMAL_EDITOR_SOC_TARGETS = setOf(
    "VIRTUAL-SKIN",
    "VIRTUAL-SKIN-HINT",
    "VIRTUAL-SKIN-CPU-LIGHT-ODPM",
    "VIRTUAL-SKIN-CPU-MID",
    "VIRTUAL-SKIN-CPU-HIGH",
    "VIRTUAL-SKIN-CPU-GPU",
    "VIRTUAL-SKIN-SOC",
    "VIRTUAL-SKIN-GPU",
    "VIRTUAL-SKIN-SPEAKER"
)

internal val THERMAL_EDITOR_BATTERY_TARGETS = setOf(
    "VIRTUAL-SKIN-CHARGE-PERSIST",
    "VIRTUAL-SKIN-CHARGE-WIRED",
    "VIRTUAL-SKIN-CHARGE",
    "VIRTUAL-SKIN-CHARGE-WLC"
)

internal val ThermalConfigParam.treeOwner: String
    get() = targetName ?: owner

internal fun ThermalConfigParam.matchesThermalTargets(targets: Set<String>): Boolean {
    val target = targetName?.uppercase(Locale.US) ?: return false
    return target in targets
}

internal sealed interface JsonPathToken {
    data class Key(val value: String) : JsonPathToken
    data class Index(val value: Int) : JsonPathToken
}

internal fun buildEditableParams(context: Context, root: JSONObject): List<ThermalConfigParam> {
    val params = mutableListOf<ThermalConfigParam>()
    collectParams(context, root, emptyList(), null, null, null, params)
    return params.sortedWith(compareBy<ThermalConfigParam> { sectionRank(it.section) }.thenBy { it.owner }.thenBy { it.displayPath })
}

internal fun buildThermalParamTree(context: Context, params: List<ThermalConfigParam>): List<ThermalParamTreeNode> {
    return params
        .groupBy { it.section }
        .toList()
        .sortedWith(compareBy<Pair<String, List<ThermalConfigParam>>> { sectionRank(it.first) }.thenBy { it.first })
        .map { (section, sectionParams) ->
            val owners = sectionParams
                .groupBy { it.treeOwner }
                .toList()
                .sortedBy { it.first.lowercase(Locale.ROOT) }
                .map { (owner, ownerParams) ->
                    ThermalParamTreeNode(
                        id = "section:$section/owner:$owner",
                        title = owner,
                        params = ownerParams.sortedBy { it.key.lowercase(Locale.ROOT) }
                    )
                }
            ThermalParamTreeNode(
                id = "section:$section",
                title = section.toSectionLabel(context),
                children = owners
            )
        }
}

internal fun defaultExpandedTreeNodeIds(params: List<ThermalConfigParam>): Set<String> {
    return params.map { "section:${it.section}" }.toSet()
}

internal fun collectParams(
    context: Context,
    value: Any?,
    path: List<JsonPathToken>,
    sectionHint: String?,
    ownerHint: String?,
    targetHint: String?,
    out: MutableList<ThermalConfigParam>
) {
    when (value) {
        is JSONObject -> {
            val section = sectionHint ?: path.firstKeyOrNull() ?: "Root"
            val objectName = value.optString("Name").takeIf { it.isNotBlank() }
            val owner = objectName
                ?: value.optString("CdevRequest").takeIf { it.isNotBlank() }
                ?: value.optString("Mode").takeIf { it.isNotBlank() }
                ?: ownerHint
            val targetName = objectName ?: targetHint
            value.keyList().forEach { key ->
                collectParams(context, value.opt(key), path + JsonPathToken.Key(key), section, owner, targetName, out)
            }
        }

        is JSONArray -> {
            if (value.isPrimitiveArray()) {
                addParam(context, value, path, sectionHint, ownerHint, targetHint, out)
            } else {
                for (index in 0 until value.length()) {
                    collectParams(context, value.opt(index), path + JsonPathToken.Index(index), sectionHint, ownerHint, targetHint, out)
                }
            }
        }

        else -> addParam(context, value, path, sectionHint, ownerHint, targetHint, out)
    }
}

internal fun addParam(
    context: Context,
    value: Any?,
    path: List<JsonPathToken>,
    sectionHint: String?,
    ownerHint: String?,
    targetHint: String?,
    out: MutableList<ThermalConfigParam>
) {
    val key = path.lastKeyOrNull() ?: return
    if (key == "Name" && path.size > 2) return

    val section = sectionHint ?: path.firstKeyOrNull() ?: "Root"
    val owner = ownerHint ?: section
    val kind = value.toThermalValueKind()
    val displayPath = path.toDisplayPath()
    val help = helpForField(context, key, displayPath)
    val known = isKnownThermalField(key, displayPath)
    val throttling = isThrottlingField(key, displayPath)
    val textValue = value.toEditorText()
    val valueLabel = when (kind) {
        ThermalValueKind.ARRAY -> context.getString(R.string.thermal_manager_value_json_array)
        ThermalValueKind.BOOLEAN -> context.getString(R.string.thermal_manager_value_boolean)
        ThermalValueKind.NUMBER -> context.getString(R.string.thermal_manager_value_number)
        ThermalValueKind.NULL -> context.getString(R.string.thermal_manager_value_null)
        ThermalValueKind.STRING -> context.getString(R.string.thermal_manager_value_text)
    }
    out += ThermalConfigParam(
        id = displayPath,
        key = key,
        section = section,
        owner = owner,
        targetName = targetHint,
        displayPath = displayPath,
        path = path,
        kind = kind,
        value = textValue,
        valueLabel = valueLabel,
        help = help,
        knownField = known,
        throttlingField = throttling,
        searchText = listOfNotNull(section, section.toSectionLabel(context), owner, targetHint, key, displayPath, textValue, help)
            .joinToString(" ")
            .lowercase(Locale.ROOT)
    )
}

internal fun resolveConfigFileName(displayName: String, fileNameInput: String): String? {
    val source = fileNameInput.trim().ifBlank { displayName.trim() }
    return sanitizeConfigName(transliterateForFileName(source))
}

internal fun sanitizeConfigName(rawName: String): String? {
    val withoutJson = rawName.trim().removeSuffix(".json")
    if (withoutJson.isBlank() || withoutJson == "." || withoutJson == "..") return null

    val cleaned = buildString(withoutJson.length) {
        withoutJson.forEach { char ->
            when {
                char in 'a'..'z' || char in '0'..'9' -> append(char)
                char in 'A'..'Z' -> append(char.lowercaseChar())
                char == '.' || char == '-' -> append(char)
                char == '_' || char.isWhitespace() -> append('_')
            }
        }
    }
        .replace(Regex("_+"), "_")
        .trim('_', '.', '-')

    if (cleaned.isBlank() || cleaned == "." || cleaned == "..") return null
    return avoidReservedCustomConfigName("$cleaned.json")
}

internal fun avoidReservedCustomConfigName(fileName: String): String {
    val trimmed = fileName.trim()
    val normalizedFileName = if (trimmed.endsWith(".json", ignoreCase = true)) trimmed else "$trimmed.json"
    val baseName = normalizedFileName.substringBeforeLast(".")
    val stockBaseName = STOCK_THERMAL_CONFIG_FILE_NAME.removeSuffix(".json")
    return if (baseName.equals(stockBaseName, ignoreCase = true)) {
        "$baseName$RESERVED_CUSTOM_SUFFIX.json"
    } else {
        normalizedFileName
    }
}

internal fun transliterateForFileName(rawName: String): String {
    val normalized = Normalizer.normalize(rawName, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
    return buildString(normalized.length) {
        normalized.forEach { char ->
            append(FILE_NAME_TRANSLIT[char.lowercaseChar()] ?: char)
        }
    }
}

internal fun String.previewValue(): String {
    val compact = replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
    return if (compact.length <= 96) compact else compact.take(93) + "..."
}

internal fun parseEditedValue(context: Context, param: ThermalConfigParam): Any? {
    val text = param.value.trim()
    return when (param.kind) {
        ThermalValueKind.BOOLEAN -> when (text.lowercase(Locale.ROOT)) {
            "true" -> true
            "false" -> false
            else -> error(context.getString(R.string.thermal_manager_error_expected_bool, param.displayPath))
        }

        ThermalValueKind.NUMBER -> {
            if (text.isBlank()) error(context.getString(R.string.thermal_manager_error_empty_number, param.displayPath))
            if (text.contains('.') || text.contains('e', ignoreCase = true)) text.toDouble() else text.toLong()
        }

        ThermalValueKind.ARRAY -> {
            val parsed = parseThermalJsonValue(text)
            if (parsed !is JSONArray) error(context.getString(R.string.thermal_manager_error_expected_json_array, param.displayPath))
            if (requiresSeverityArray(param.key) && parsed.length() != 7) {
                error(context.getString(R.string.thermal_manager_error_severity_array_size, param.displayPath))
            }
            parsed
        }

        ThermalValueKind.NULL -> if (text.equals("null", ignoreCase = true)) JSONObject.NULL else text
        ThermalValueKind.STRING -> text
    }
}

internal fun setJsonValue(root: JSONObject, path: List<JsonPathToken>, value: Any?) {
    var cursor: Any = root
    path.dropLast(1).forEach { token ->
        cursor = when (token) {
            is JsonPathToken.Key -> (cursor as JSONObject).get(token.value)
            is JsonPathToken.Index -> (cursor as JSONArray).get(token.value)
        }
    }
    when (val token = path.last()) {
        is JsonPathToken.Key -> (cursor as JSONObject).put(token.value, value)
        is JsonPathToken.Index -> (cursor as JSONArray).put(token.value, value)
    }
}

internal fun validateImportedConfig(context: Context, reference: JSONObject, imported: JSONObject): List<String> {
    val errors = mutableListOf<String>()
    validateObjectKeys(context, reference, imported, context.getString(R.string.thermal_manager_json_root), errors)
    return errors
}

internal fun validateObjectKeys(context: Context, reference: JSONObject, imported: JSONObject, prefix: String, errors: MutableList<String>) {
    reference.keyList().forEach { key ->
        if (!imported.has(key)) {
            errors += context.getString(R.string.thermal_manager_import_missing_key, "$prefix.$key")
            return@forEach
        }
        val referenceValue = reference.opt(key)
        val importedValue = imported.opt(key)
        when {
            referenceValue is JSONObject && importedValue is JSONObject -> {
                validateObjectKeys(context, referenceValue, importedValue, "$prefix.$key", errors)
            }

            referenceValue is JSONArray && importedValue is JSONArray -> {
                validateArrayKeys(context, referenceValue, importedValue, "$prefix.$key", errors)
            }
        }
    }
}

internal fun validateArrayKeys(context: Context, reference: JSONArray, imported: JSONArray, prefix: String, errors: MutableList<String>) {
    val identityKey = reference.firstIdentityKey()
    if (identityKey != null) {
        for (index in 0 until reference.length()) {
            val referenceObject = reference.optJSONObject(index) ?: continue
            val identity = referenceObject.optString(identityKey).takeIf { it.isNotBlank() } ?: continue
            val importedObject = imported.findObjectBy(identityKey, identity)
            if (importedObject == null) {
                errors += context.getString(R.string.thermal_manager_import_missing_entry, "$prefix[$identityKey=$identity]")
            } else {
                validateObjectKeys(context, referenceObject, importedObject, "$prefix[$identity]", errors)
            }
        }
    } else {
        val referenceObject = reference.firstObjectOrNull() ?: return
        val importedObject = imported.firstObjectOrNull()
        if (importedObject == null) {
            errors += context.getString(R.string.thermal_manager_import_missing_object_schema, "$prefix[]")
        } else {
            validateObjectKeys(context, referenceObject, importedObject, "$prefix[]", errors)
        }
    }
}

internal fun helpForField(context: Context, key: String, path: String): String {
    return FIELD_HELP_RES[key]?.let(context::getString)
        ?: when {
            path.contains("PIDInfo") -> context.getString(R.string.thermal_manager_field_help_pidinfo_fallback)
            path.contains("BindedCdevInfo") -> context.getString(R.string.thermal_manager_field_help_binded_cdev_fallback)
            path.contains("Profile") -> context.getString(R.string.thermal_manager_field_help_profile_fallback)
            path.contains("Stats") -> context.getString(R.string.thermal_manager_field_help_stats_fallback)
            else -> context.getString(R.string.thermal_manager_field_help_unknown)
        }
}

internal fun isKnownThermalField(key: String, path: String): Boolean {
    return FIELD_HELP_RES.containsKey(key) || path.contains("PIDInfo") || path.contains("BindedCdevInfo") || path.contains("Profile") || path.contains("Stats")
}

internal fun isThrottlingField(key: String, path: String): Boolean {
    return key in THROTTLING_FIELDS || key.startsWith("K_") || path.contains("PIDInfo") || path.contains("BindedCdevInfo")
}

internal fun requiresSeverityArray(key: String): Boolean {
    return key in setOf(
        "HotThreshold",
        "HotHysteresis",
        "ColdThreshold",
        "ColdHysteresis",
        "LimitInfo",
        "LimitInfoFrequency",
        "CdevCeiling",
        "CdevCeilingFrequency",
        "CdevWeightForPID",
        "K_Po",
        "K_Pu",
        "K_I",
        "K_Io",
        "K_Iu",
        "K_D",
        "I_Max",
        "S_Power",
        "MinAllocPower",
        "MaxAllocPower",
        "I_Cutoff"
    )
}

internal fun Any?.toThermalValueKind(): ThermalValueKind = when (this) {
    is JSONArray -> ThermalValueKind.ARRAY
    is Boolean -> ThermalValueKind.BOOLEAN
    is Number -> ThermalValueKind.NUMBER
    null, JSONObject.NULL -> ThermalValueKind.NULL
    else -> ThermalValueKind.STRING
}

internal fun Any?.toEditorText(): String = when (this) {
    is JSONArray -> this.toString()
    JSONObject.NULL, null -> "null"
    else -> toString()
}

internal fun JSONArray.isPrimitiveArray(): Boolean {
    for (index in 0 until length()) {
        if (opt(index) is JSONObject || opt(index) is JSONArray) return false
    }
    return true
}

internal fun JSONArray.firstIdentityKey(): String? {
    val first = firstObjectOrNull() ?: return null
    return listOf("Name", "CdevRequest", "Mode", "LoggingName").firstOrNull { first.optString(it).isNotBlank() }
}

internal fun JSONArray.findObjectBy(key: String, value: String): JSONObject? {
    for (index in 0 until length()) {
        val candidate = optJSONObject(index) ?: continue
        if (candidate.optString(key) == value) return candidate
    }
    return null
}

internal fun JSONArray.firstObjectOrNull(): JSONObject? {
    for (index in 0 until length()) {
        val candidate = optJSONObject(index)
        if (candidate != null) return candidate
    }
    return null
}

internal fun JSONObject.keyList(): List<String> {
    val keys = mutableListOf<String>()
    val iterator = keys()
    while (iterator.hasNext()) keys += iterator.next()
    return keys
}

internal fun List<JsonPathToken>.firstKeyOrNull(): String? = firstOrNull { it is JsonPathToken.Key }?.let { (it as JsonPathToken.Key).value }

internal fun List<JsonPathToken>.lastKeyOrNull(): String? = lastOrNull { it is JsonPathToken.Key }?.let { (it as JsonPathToken.Key).value }

internal fun List<JsonPathToken>.toDisplayPath(): String = buildString {
    this@toDisplayPath.forEach { token ->
        when (token) {
            is JsonPathToken.Key -> {
                if (isNotEmpty()) append('.')
                append(token.value)
            }

            is JsonPathToken.Index -> append('[').append(token.value).append(']')
        }
    }
}

internal fun String.toSectionLabel(context: Context): String = when (this) {
    "Root" -> context.getString(R.string.thermal_manager_section_root)
    "Sensors" -> context.getString(R.string.thermal_manager_section_sensors)
    "CoolingDevices" -> context.getString(R.string.thermal_manager_section_cooling_devices)
    "PowerRails" -> context.getString(R.string.thermal_manager_section_power_rails)
    "Stats" -> context.getString(R.string.thermal_manager_section_stats)
    "LogInfo" -> context.getString(R.string.thermal_manager_section_log_info)
    "Include" -> context.getString(R.string.thermal_manager_section_includes)
    else -> this
}

internal fun sectionRank(section: String): Int = when (section) {
    "Sensors" -> 0
    "CoolingDevices" -> 1
    "PowerRails" -> 2
    "Stats" -> 3
    "LogInfo" -> 4
    "Include" -> 5
    else -> 10
}

internal fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val formatter = DecimalFormat("0.#")
    val kib = bytes / 1024.0
    if (kib < 1024) return "${formatter.format(kib)} KiB"
    return "${formatter.format(kib / 1024.0)} MiB"
}

internal val THROTTLING_FIELDS = setOf(
    "HotThreshold",
    "HotHysteresis",
    "ColdThreshold",
    "ColdHysteresis",
    "PollingDelay",
    "PassiveDelay",
    "SendCallback",
    "SendPowerHint",
    "PIDInfo",
    "BindedCdevInfo",
    "CdevRequest",
    "LimitInfo",
    "LimitInfoFrequency",
    "CdevCeiling",
    "CdevCeilingFrequency",
    "CdevWeightForPID",
    "MaxReleaseStep",
    "MaxThrottleStep",
    "BindedPowerRail",
    "PowerThreshold",
    "ReleaseLogic",
    "Disabled"
)

internal val FIELD_HELP_RES = mapOf(
    "Type" to R.string.thermal_manager_field_help_type,
    "HotThreshold" to R.string.thermal_manager_field_help_hot_threshold,
    "HotHysteresis" to R.string.thermal_manager_field_help_hot_hysteresis,
    "ColdThreshold" to R.string.thermal_manager_field_help_cold_threshold,
    "ColdHysteresis" to R.string.thermal_manager_field_help_cold_hysteresis,
    "Multiplier" to R.string.thermal_manager_field_help_multiplier,
    "PollingDelay" to R.string.thermal_manager_field_help_polling_delay,
    "PassiveDelay" to R.string.thermal_manager_field_help_passive_delay,
    "VirtualSensor" to R.string.thermal_manager_field_help_virtual_sensor,
    "Formula" to R.string.thermal_manager_field_help_formula,
    "Combination" to R.string.thermal_manager_field_help_combination,
    "Coefficient" to R.string.thermal_manager_field_help_coefficient,
    "CoefficientType" to R.string.thermal_manager_field_help_coefficient_type,
    "CombinationType" to R.string.thermal_manager_field_help_combination_type,
    "Offset" to R.string.thermal_manager_field_help_offset,
    "TriggerSensor" to R.string.thermal_manager_field_help_trigger_sensor,
    "SendCallback" to R.string.thermal_manager_field_help_send_callback,
    "SendPowerHint" to R.string.thermal_manager_field_help_send_power_hint,
    "Hidden" to R.string.thermal_manager_field_help_hidden,
    "Version" to R.string.thermal_manager_field_help_version,
    "Profile" to R.string.thermal_manager_field_help_profile,
    "Mode" to R.string.thermal_manager_field_help_mode,
    "PIDInfo" to R.string.thermal_manager_field_help_pid_info,
    "K_Po" to R.string.thermal_manager_field_help_k_po,
    "K_Pu" to R.string.thermal_manager_field_help_k_pu,
    "K_I" to R.string.thermal_manager_field_help_k_i,
    "K_Io" to R.string.thermal_manager_field_help_k_io,
    "K_Iu" to R.string.thermal_manager_field_help_k_iu,
    "K_D" to R.string.thermal_manager_field_help_k_d,
    "I_Max" to R.string.thermal_manager_field_help_i_max,
    "S_Power" to R.string.thermal_manager_field_help_s_power,
    "MinAllocPower" to R.string.thermal_manager_field_help_min_alloc_power,
    "MaxAllocPower" to R.string.thermal_manager_field_help_max_alloc_power,
    "I_Cutoff" to R.string.thermal_manager_field_help_i_cutoff,
    "I_Default" to R.string.thermal_manager_field_help_i_default,
    "I_Default_Pct" to R.string.thermal_manager_field_help_i_default_pct,
    "I_Trend" to R.string.thermal_manager_field_help_i_trend,
    "TranCycle" to R.string.thermal_manager_field_help_tran_cycle,
    "BindedCdevInfo" to R.string.thermal_manager_field_help_binded_cdev_info,
    "CdevRequest" to R.string.thermal_manager_field_help_cdev_request,
    "LimitInfo" to R.string.thermal_manager_field_help_limit_info,
    "LimitInfoFrequency" to R.string.thermal_manager_field_help_limit_info_frequency,
    "CdevCeiling" to R.string.thermal_manager_field_help_cdev_ceiling,
    "CdevCeilingFrequency" to R.string.thermal_manager_field_help_cdev_ceiling_frequency,
    "CdevWeightForPID" to R.string.thermal_manager_field_help_cdev_weight_for_pid,
    "MaxReleaseStep" to R.string.thermal_manager_field_help_max_release_step,
    "MaxThrottleStep" to R.string.thermal_manager_field_help_max_throttle_step,
    "BindedPowerRail" to R.string.thermal_manager_field_help_binded_power_rail,
    "PowerThreshold" to R.string.thermal_manager_field_help_power_threshold,
    "ReleaseLogic" to R.string.thermal_manager_field_help_release_logic,
    "Disabled" to R.string.thermal_manager_field_help_disabled,
    "WritePath" to R.string.thermal_manager_field_help_write_path,
    "State2Power" to R.string.thermal_manager_field_help_state2_power,
    "PowerRail" to R.string.thermal_manager_field_help_power_rail,
    "PowerSampleDelay" to R.string.thermal_manager_field_help_power_sample_delay,
    "PowerSampleCount" to R.string.thermal_manager_field_help_power_sample_count,
    "VirtualRails" to R.string.thermal_manager_field_help_virtual_rails,
    "RecordWithDefaultThreshold" to R.string.thermal_manager_field_help_record_with_default_threshold,
    "RecordWithThreshold" to R.string.thermal_manager_field_help_record_with_threshold,
    "DefaultThresholdEnableAll" to R.string.thermal_manager_field_help_default_threshold_enable_all,
    "Thresholds" to R.string.thermal_manager_field_help_thresholds,
    "LoggingName" to R.string.thermal_manager_field_help_logging_name,
    "Abnormality" to R.string.thermal_manager_field_help_abnormality,
    "Outlier" to R.string.thermal_manager_field_help_outlier,
    "Stuck" to R.string.thermal_manager_field_help_stuck,
    "Monitor" to R.string.thermal_manager_field_help_monitor,
    "TempRange" to R.string.thermal_manager_field_help_temp_range,
    "TempStuck" to R.string.thermal_manager_field_help_temp_stuck,
    "MinPollingCount" to R.string.thermal_manager_field_help_min_polling_count,
    "MinStuckDuration" to R.string.thermal_manager_field_help_min_stuck_duration,
    "RecordVotePerSensor" to R.string.thermal_manager_field_help_record_vote_per_sensor,
    "ExcludedPowerInfo" to R.string.thermal_manager_field_help_excluded_power_info,
    "PredictorInfo" to R.string.thermal_manager_field_help_predictor_info,
    "PredictionWeight" to R.string.thermal_manager_field_help_prediction_weight,
    "Include" to R.string.thermal_manager_field_help_include
)
