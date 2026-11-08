package org.pixel.extra.parts.icons

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import android.util.Xml
import org.json.JSONObject
import org.pixel.extra.parts.R
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Calendar
import androidx.core.graphics.drawable.toBitmap
import java.util.Locale
import java.util.zip.ZipFile
import kotlin.math.roundToInt

// Pack drawable normalization shared by the UI bitmap loaders.
internal fun Drawable.toNormalizedPackBitmap(targetPx: Int): Bitmap {
    val raw = toBitmap(
        width = targetPx.coerceAtLeast(1),
        height = targetPx.coerceAtLeast(1),
        config = Bitmap.Config.ARGB_8888,
    )
    val normalized = raw.normalizeVisiblePackArea(targetPx.coerceAtLeast(1))
    if (normalized !== raw) raw.recycle()
    return normalized
}

private val packNormalizePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)

private fun Bitmap.normalizeVisiblePackArea(targetPx: Int): Bitmap {
    val bounds = visiblePackAlphaBounds() ?: return this
    val visibleWidth = bounds.width().coerceAtLeast(1)
    val visibleHeight = bounds.height().coerceAtLeast(1)
    val minExpected = targetPx * 0.70f
    if (visibleWidth >= minExpected || visibleHeight >= minExpected) return this
    val output = Bitmap.createBitmap(targetPx, targetPx, Bitmap.Config.ARGB_8888)
    val targetVisiblePx = (targetPx * 0.88f).roundToInt().coerceIn(1, targetPx)
    val scale = minOf(
        targetVisiblePx / visibleWidth.toFloat(),
        targetVisiblePx / visibleHeight.toFloat(),
    )
    val scaledWidth = (visibleWidth * scale).roundToInt().coerceAtLeast(1)
    val scaledHeight = (visibleHeight * scale).roundToInt().coerceAtLeast(1)
    val dst = RectF(
        ((targetPx - scaledWidth) / 2f),
        ((targetPx - scaledHeight) / 2f),
        ((targetPx + scaledWidth) / 2f),
        ((targetPx + scaledHeight) / 2f),
    )
    Canvas(output).drawBitmap(this, bounds, dst, packNormalizePaint)
    return output
}

private fun Bitmap.visiblePackAlphaBounds(): Rect? {
    var left = width
    var top = height
    var right = -1
    var bottom = -1
    for (y in 0 until height) {
        for (x in 0 until width) {
            if ((getPixel(x, y) ushr 24) > 8) {
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
    }
    return if (right >= left && bottom >= top) Rect(left, top, right + 1, bottom + 1) else null
}

// Shared icon-pack backend: catalog parsing, per-density export and the
// icon_map.json store. Storage keys and file layout are byte-compatible with
// the legacy implementation, so existing exports survive the redesign.
internal object PacksBackend {
    const val DYNAMIC_ICON_CALENDAR = "calendar"
    const val DYNAMIC_ICON_CLOCK = "clock"

    private const val TAG = "PacksBackend"
    private const val ICON_DP = 48
    private const val ICON_MAP_VERSION = 2
    private val iconRoot = File("/data/pixelparts/IconsManager")
    private val iconMapFile = File(iconRoot, "icon_map.json")
    private val ioLock = Any()

    private val themeActions = listOf(
        "org.adw.launcher.THEMES",
        "com.teslacoilsw.launcher.THEME",
        "com.novalauncher.THEME",
    )

    private val densities = listOf(
        IconDensity("mdpi", 160),
        IconDensity("hdpi", 240),
        IconDensity("xhdpi", 320),
        IconDensity("xxhdpi", 480),
        IconDensity("xxxhdpi", 640),
    )

    fun readIconMap(): IconMapSnapshot = synchronized(ioLock) { readIconMapLocked() }

    fun readIconMapLocked(): IconMapSnapshot {
        if (!iconMapFile.isFile) return IconMapSnapshot.EMPTY
        return runCatching {
            FileInputStream(iconMapFile).use { input ->
                val root = JSONObject(input.bufferedReader(Charsets.UTF_8).use { it.readText() })
                val iconsObject = root.optJSONObject("icons") ?: root
                val icons = mutableMapOf<String, String>()
                iconsObject.keys().forEach { packageName ->
                    val value = iconsObject.opt(packageName)
                    if (value is String && packageName.isNotBlank()) {
                        normalizeFileName(value)?.let { icons[packageName] = it }
                    }
                }
                val sources = mutableMapOf<String, IconSource>()
                root.optJSONObject("sources")?.keys()?.forEach { packageName ->
                    val source = root.optJSONObject("sources")?.optJSONObject(packageName) ?: return@forEach
                    val packPackage = source.optString("iconPackPackage").takeIf { it.isNotBlank() }
                        ?: return@forEach
                    sources[packageName] = IconSource(
                        iconPackPackage = packPackage,
                        iconPackLabel = source.optString("iconPackLabel", packPackage),
                        drawableName = source.optString("drawableName"),
                        iconPackVersionCode = source.optLong("iconPackVersionCode", 0L),
                        updatedAt = source.optLong("updatedAt", 0L),
                        dynamicIcon = readDynamicIconInfo(source),
                    )
                }
                val packs = mutableMapOf<String, IconPackVersion>()
                root.optJSONObject("packs")?.keys()?.forEach { packageName ->
                    val pack = root.optJSONObject("packs")?.optJSONObject(packageName) ?: return@forEach
                    packs[packageName] = IconPackVersion(
                        packageName = packageName,
                        label = pack.optString("label", packageName),
                        versionCode = pack.optLong("versionCode", 0L),
                        appliedAt = pack.optLong("appliedAt", 0L),
                    )
                }
                val shapeOverrides = mutableMapOf<String, AppIconShapeOverrides>()
                root.optJSONObject("shapeOverrides")?.keys()?.forEach { packageName ->
                    val appObject = root.optJSONObject("shapeOverrides")?.optJSONObject(packageName)
                        ?: return@forEach
                    val overrides = AppIconShapeOverrides(
                        system = readShapeAreaConfig(appObject.optJSONObject(AppIconShapeArea.SYSTEM.jsonKey)),
                        notification = readShapeAreaConfig(appObject.optJSONObject(AppIconShapeArea.NOTIFICATION.jsonKey)),
                        launcher = readShapeAreaConfig(appObject.optJSONObject(AppIconShapeArea.LAUNCHER.jsonKey)),
                    ).normalizedOrNull()
                    if (overrides != null && packageName.isNotBlank()) {
                        shapeOverrides[packageName] = overrides
                    }
                }
                IconMapSnapshot(icons, sources, packs, shapeOverrides)
            }
        }.getOrElse {
            Log.w(TAG, "Unable to read icon map", it)
            IconMapSnapshot.EMPTY
        }
    }

    fun writeIconMapLocked(snapshot: IconMapSnapshot) {
        PacksManager.clearDashboardCache()
        ensureStorageDirs()
        val root = JSONObject()
        root.put("version", ICON_MAP_VERSION)
        root.put("generatedAt", System.currentTimeMillis())
        val icons = JSONObject()
        snapshot.icons.toSortedMap().forEach { (packageName, fileName) -> icons.put(packageName, fileName) }
        root.put("icons", icons)
        val sources = JSONObject()
        snapshot.sources.toSortedMap().forEach { (packageName, source) ->
            sources.put(packageName, JSONObject().apply {
                put("iconPackPackage", source.iconPackPackage)
                put("iconPackLabel", source.iconPackLabel)
                put("drawableName", source.drawableName)
                put("iconPackVersionCode", source.iconPackVersionCode)
                put("updatedAt", source.updatedAt)
                writeDynamicIconInfo(source.dynamicIcon)?.let { put("dynamicIcon", it) }
            })
        }
        root.put("sources", sources)
        val packs = JSONObject()
        snapshot.packs.toSortedMap().forEach { (packageName, pack) ->
            packs.put(packageName, JSONObject().apply {
                put("label", pack.label)
                put("versionCode", pack.versionCode)
                put("appliedAt", pack.appliedAt)
            })
        }
        root.put("packs", packs)
        val shapeOverrides = JSONObject()
        snapshot.shapeOverrides.toSortedMap().forEach { (packageName, overrides) ->
            overrides.normalizedOrNull()?.let { normalized ->
                val appObject = JSONObject()
                writeShapeAreaConfig(normalized.system)?.let { appObject.put(AppIconShapeArea.SYSTEM.jsonKey, it) }
                writeShapeAreaConfig(normalized.notification)?.let {
                    appObject.put(AppIconShapeArea.NOTIFICATION.jsonKey, it)
                }
                writeShapeAreaConfig(normalized.launcher)?.let {
                    appObject.put(AppIconShapeArea.LAUNCHER.jsonKey, it)
                }
                if (appObject.length() > 0) shapeOverrides.put(packageName, appObject)
            }
        }
        root.put("shapeOverrides", shapeOverrides)
        val tmpFile = File(iconRoot, "icon_map.json.tmp")
        tmpFile.writeText(root.toString(2), Charsets.UTF_8)
        tmpFile.setReadable(true, false)
        if (!tmpFile.renameTo(iconMapFile)) {
            tmpFile.copyTo(iconMapFile, overwrite = true)
            tmpFile.delete()
        }
        iconMapFile.setReadable(true, false)
    }

    fun savePackageShapeOverrides(context: Context, packageName: String, overrides: AppIconShapeOverrides?) {
        synchronized(ioLock) {
            val current = readIconMapLocked()
            val shapeOverrides = current.shapeOverrides.toMutableMap()
            val normalized = overrides?.normalizedOrNull()
            if (normalized == null) {
                shapeOverrides.remove(packageName)
            } else {
                shapeOverrides[packageName] = normalized
            }
            writeIconMapLocked(current.copy(shapeOverrides = shapeOverrides.toSortedMap()))
        }
        sendReloadBroadcast(context.applicationContext ?: context)
    }

    fun clearAllIconData(context: Context, progressTaskId: String?, onProgress: (IconApplyProgress) -> Unit): IconApplyResult {
        return synchronized(ioLock) {
            val current = readIconMapLocked()
            val packageNames = (current.icons.keys + current.sources.keys + current.shapeOverrides.keys).toSet()
            var processed = 0
            var removed = 0
            onProgress(IconApplyProgress(progressTaskId.orEmpty(), "", packageNames.size, processed, 0, 0, removed, 0, false))
            packageNames.forEach { _ ->
                removed++
                processed++
                onProgress(IconApplyProgress(progressTaskId.orEmpty(), "", packageNames.size, processed, 0, 0, removed, 0, false))
            }
            densities.forEach { density ->
                File(iconRoot, density.folder).listFiles()?.forEach { file ->
                    if (file.isFile) file.delete()
                }
            }
            if (iconMapFile.exists()) iconMapFile.delete()
            writeIconMapLocked(IconMapSnapshot.EMPTY)
            sendReloadBroadcast(context)
            IconApplyResult(packageNames.size, 0, 0, removed, 0)
        }
    }

    fun importPackageIcon(
        context: Context,
        packageName: String,
        uri: Uri,
        progressTaskId: String?,
        onProgress: (IconApplyProgress) -> Unit,
    ): IconApplyResult {
        return synchronized(ioLock) {
            onProgress(IconApplyProgress(progressTaskId.orEmpty(), "", 1, 0, 0, 0, 0, 0, false))
            val bitmap = decodeBitmapFromUri(context, uri)
            if (bitmap == null) {
                onProgress(IconApplyProgress(progressTaskId.orEmpty(), "", 1, 1, 0, 1, 0, 0, false))
                return@synchronized IconApplyResult(requested = 1, applied = 0, failed = 1)
            }
            ensureStorageDirs()
            val current = readIconMapLocked()
            val icons = current.icons.toMutableMap()
            val sources = current.sources.toMutableMap()
            val packs = current.packs.toMutableMap()
            val fileName = fileNameForPackage(packageName)
            val exported = exportBitmapForAllDensities(bitmap, fileName)
            if (!exported) {
                onProgress(IconApplyProgress(progressTaskId.orEmpty(), "", 1, 1, 0, 1, 0, 0, false))
                return@synchronized IconApplyResult(requested = 1, applied = 0, failed = 1)
            }
            val now = System.currentTimeMillis()
            icons[packageName] = fileName
            sources[packageName] = IconSource(
                iconPackPackage = PacksManager.CUSTOM_ICON_SOURCE_ID,
                iconPackLabel = context.getString(R.string.icons_custom_icon_source),
                drawableName = getDisplayName(context, uri) ?: uri.lastPathSegment.orEmpty().ifBlank { "custom" },
                iconPackVersionCode = 0L,
                updatedAt = now,
            )
            packs[PacksManager.CUSTOM_ICON_SOURCE_ID] = IconPackVersion(
                packageName = PacksManager.CUSTOM_ICON_SOURCE_ID,
                label = context.getString(R.string.icons_custom_icon_source),
                versionCode = 0L,
                appliedAt = now,
            )
            writeIconMapLocked(current.copy(
                icons = icons.toSortedMap(),
                sources = sources.toSortedMap(),
                packs = packs.toSortedMap(),
            ))
            sendReloadBroadcast(context)
            onProgress(IconApplyProgress(progressTaskId.orEmpty(), "", 1, 1, 1, 0, 0, 0, false))
            IconApplyResult(requested = 1, applied = 1, failed = 0)
        }
    }

    fun loadIconPacks(
        context: Context,
        packageManager: PackageManager,
        installedIndex: Map<String, InstalledAppRecord>,
        iconMap: IconMapSnapshot,
    ): List<IconPackInfo> {
        val discoveredPackages = discoverIconPackPackages(packageManager)
        val appliedPackPackages = iconMap.sources.values
            .map { it.iconPackPackage }
            .filterNot { it == PacksManager.CUSTOM_ICON_SOURCE_ID }
            .toSet()
        return (discoveredPackages + appliedPackPackages).distinct().mapNotNull { packageName ->
            val packageInfo = runCatching { packageManager.getPackageInfo(packageName, 0) }.getOrNull()
            if (packageInfo == null) {
                val storedPack = iconMap.packs[packageName]
                val appliedSources = iconMap.sources.values.filter { it.iconPackPackage == packageName }
                val fallbackSource = appliedSources.firstOrNull()
                if (storedPack == null && fallbackSource == null) return@mapNotNull null
                return@mapNotNull IconPackInfo(
                    packageName = packageName,
                    label = storedPack?.label?.takeIf { it.isNotBlank() }
                        ?: fallbackSource?.iconPackLabel?.takeIf { it.isNotBlank() }
                        ?: packageName,
                    icon = null,
                    versionCode = storedPack?.versionCode ?: fallbackSource?.iconPackVersionCode ?: 0L,
                    supportedIconCount = appliedSources.map { it.drawableName }.distinct().size,
                    supportedPackageCount = appliedSources.size,
                    appliedPackageCount = appliedSources.size,
                    status = IconPackApplyStatus.APPLIED_PARTIAL,
                    requiresUpdate = false,
                    installed = false,
                )
            }
            val appInfo = packageInfo.applicationInfo ?: return@mapNotNull null
            val label = appInfo.loadLabel(packageManager)?.toString()?.takeIf { it.isNotBlank() }
                ?: packageName
            val entries = parseIconPackEntries(context, packageName, installedIndex)
            val supportedPackages = entries.map { it.appPackageName }.distinct()
            val appliedPackages = supportedPackages.count {
                iconMap.sources[it]?.iconPackPackage == packageName
            }
            val status = when {
                supportedPackages.isEmpty() || appliedPackages == 0 -> IconPackApplyStatus.NOT_APPLIED
                appliedPackages >= supportedPackages.size -> IconPackApplyStatus.APPLIED_FULL
                else -> IconPackApplyStatus.APPLIED_PARTIAL
            }
            @Suppress("DEPRECATION")
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                packageInfo.versionCode.toLong()
            }
            val lastAppliedVersion = iconMap.packs[packageName]?.versionCode
            IconPackInfo(
                packageName = packageName,
                label = label,
                icon = appInfo.loadIcon(packageManager),
                versionCode = versionCode,
                supportedIconCount = entries.map { it.drawableName }.distinct().size,
                supportedPackageCount = supportedPackages.size,
                appliedPackageCount = appliedPackages,
                status = status,
                requiresUpdate = lastAppliedVersion != null && lastAppliedVersion != versionCode,
                installed = true,
            )
        }.sortedBy { it.label.lowercase(Locale.getDefault()) }
    }

    fun loadIconPackSelectionEntries(
        context: Context,
        iconPackPackage: String,
        installedIndex: Map<String, InstalledAppRecord>,
        iconMap: IconMapSnapshot,
    ): List<IconPackEntry> {
        val parsedEntries = parseIconPackEntries(context, iconPackPackage, installedIndex)
        val parsedPackages = parsedEntries.map { it.appPackageName }.toSet()
        val storedEntries = iconMap.sources
            .filterValues { it.iconPackPackage == iconPackPackage }
            .filterKeys { it !in parsedPackages }
            .map { (packageName, source) ->
                val installedApp = installedIndex[packageName]
                IconPackEntry(
                    iconPackPackage = iconPackPackage,
                    appPackageName = packageName,
                    componentName = null,
                    drawableName = source.drawableName,
                    label = installedApp?.label ?: packageName,
                    installed = installedApp != null,
                    isSystem = installedApp?.isSystem == true,
                    dynamicIcon = source.dynamicIcon,
                )
            }
        return (parsedEntries + storedEntries).sortedWith(
            compareBy<IconPackEntry> { it.label.lowercase(Locale.getDefault()) }
                .thenBy { it.appPackageName }
                .thenBy { it.drawableName },
        )
    }

    fun loadPreviewItems(
        context: Context,
        iconPackPackage: String,
        installedIndex: Map<String, InstalledAppRecord>,
    ): List<IconPackPreviewItem> {
        val parsedEntries = parseIconPackEntries(context, iconPackPackage, installedIndex)
        val packageInfo = runCatching { context.packageManager.getPackageInfo(iconPackPackage, 0) }.getOrNull()
        val items = linkedMapOf<String, IconPackPreviewItem>()
        parsedEntries.forEach { entry ->
            items.putIfAbsent(
                entry.drawableName,
                IconPackPreviewItem(iconPackPackage, entry.drawableName, entry.label, entry.appPackageName),
            )
        }
        packageInfo?.applicationInfo?.sourceDir
            ?.let { findDrawableResourceNames(it) }
            ?.forEach { drawableName ->
                items.putIfAbsent(
                    drawableName,
                    IconPackPreviewItem(iconPackPackage, drawableName, drawableName, null),
                )
            }
        return items.values.sortedWith(
            compareBy<IconPackPreviewItem> { it.label.lowercase(Locale.getDefault()) }
                .thenBy { it.drawableName },
        )
    }

    fun loadCandidatesForApp(
        context: Context,
        installedIndex: Map<String, InstalledAppRecord>,
        packageName: String,
    ): List<AppIconCandidate> {
        val packageManager = context.packageManager
        return discoverIconPackPackages(packageManager).flatMap { iconPackPackage ->
            val packageInfo = runCatching { packageManager.getPackageInfo(iconPackPackage, 0) }.getOrNull()
            val label = packageInfo?.applicationInfo?.loadLabel(packageManager)?.toString()
                ?.takeIf { it.isNotBlank() } ?: iconPackPackage
            parseIconPackEntries(context, iconPackPackage, installedIndex)
                .filter { it.appPackageName == packageName }
                .distinctBy { it.drawableName }
                .map { AppIconCandidate(iconPackPackage, label, it.drawableName) }
        }.sortedWith(
            compareBy<AppIconCandidate> { it.iconPackLabel.lowercase(Locale.getDefault()) }
                .thenBy { it.drawableName },
        )
    }

    fun discoverIconPackPackages(packageManager: PackageManager): List<String> {
        val packages = linkedSetOf<String>()
        themeActions.forEach { action ->
            val intent = Intent(action)
            runCatching { packageManager.queryIntentActivities(intent, 0) }.getOrDefault(emptyList())
                .mapNotNullTo(packages) { it.activityInfo?.packageName ?: it.resolvePackageName }
            runCatching { packageManager.queryBroadcastReceivers(intent, 0) }.getOrDefault(emptyList())
                .mapNotNullTo(packages) { it.activityInfo?.packageName ?: it.resolvePackageName }
            runCatching { packageManager.queryIntentServices(intent, 0) }.getOrDefault(emptyList())
                .mapNotNullTo(packages) { it.serviceInfo?.packageName ?: it.resolvePackageName }
        }
        return packages.toList()
    }

    fun parseIconPackEntries(
        context: Context,
        iconPackPackage: String,
        installedIndex: Map<String, InstalledAppRecord>,
    ): List<IconPackEntry> {
        val packContext = runCatching {
            context.createPackageContext(iconPackPackage, Context.CONTEXT_IGNORE_SECURITY)
        }.getOrNull() ?: return emptyList()
        val entries = mutableListOf<IconPackEntry>()
        runCatching {
            openAppFilterParser(packContext)?.useParser { parser ->
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG) {
                        when (parser.name) {
                            "item" -> parseStaticIconEntry(parser, iconPackPackage, installedIndex)
                                ?.let { entries += it }
                            "calendar" -> parseCalendarIconEntry(parser, packContext, iconPackPackage, installedIndex)
                                ?.let { entries += it }
                            "dynamic-clock", "dynamic_clock", "clock" ->
                                parseDynamicClockIconEntry(parser, iconPackPackage, installedIndex)
                                    ?.let { entries += it }
                        }
                    }
                    event = parser.next()
                }
            }
        }.onFailure { Log.w(TAG, "Unable to parse appfilter.xml for $iconPackPackage", it) }
        return entries.distinctBy { it.appPackageName + "|" + it.drawableName }
            .sortedWith(
                compareByDescending<IconPackEntry> { it.installed }
                    .thenBy { it.label.lowercase(Locale.getDefault()) }
                    .thenBy { it.appPackageName }
                    .thenBy { it.drawableName },
            )
    }

    fun singleSelectionEntries(
        context: Context,
        iconPackPackage: String,
        packageName: String,
        drawableName: String?,
        installedIndex: Map<String, InstalledAppRecord>,
    ): List<IconPackEntry> {
        val parsedEntries = parseIconPackEntries(context, iconPackPackage, installedIndex)
            .filter { it.appPackageName == packageName && (drawableName == null || it.drawableName == drawableName) }
        if (parsedEntries.isNotEmpty() || drawableName.isNullOrBlank()) return parsedEntries
        val installedApp = installedIndex[packageName]
        return listOf(
            IconPackEntry(
                iconPackPackage = iconPackPackage,
                appPackageName = packageName,
                componentName = null,
                drawableName = drawableName,
                label = installedApp?.label ?: packageName,
                installed = installedApp != null,
                isSystem = installedApp?.isSystem == true,
            ),
        )
    }

    fun filterEntriesForMode(entries: List<IconPackEntry>, mode: IconApplyMode): List<IconPackEntry> {
        return when (mode) {
            IconApplyMode.ALL_PACK -> entries
            IconApplyMode.SYSTEM_ONLY -> entries.filter { it.installed && it.isSystem }
            IconApplyMode.USER_ONLY -> entries.filter { it.installed && !it.isSystem }
            IconApplyMode.INSTALLED_ONLY -> entries.filter { it.installed }
        }
    }

    fun loadIconPackDrawable(
        packContext: Context,
        drawableName: String,
        dynamicIcon: DynamicIconInfo? = null,
    ): Drawable? {
        val cleanName = drawableName.substringBeforeLast('.').trim()
        if (cleanName.isBlank()) return null
        val resources = packContext.resources
        val resId = resources.getIdentifier(cleanName, "drawable", packContext.packageName)
            .takeIf { it != 0 }
            ?: resources.getIdentifier(cleanName, "mipmap", packContext.packageName).takeIf { it != 0 }
            ?: return null
        val drawable = runCatching { resources.getDrawable(resId, packContext.theme) }.getOrNull()
            ?: return null
        return if (dynamicIcon?.type == DYNAMIC_ICON_CLOCK && dynamicIcon.clockConfig != null) {
            DynamicPackClockDrawable(drawable, dynamicIcon.clockConfig)
        } else {
            drawable
        }
    }

    fun exportIconForAllDensities(
        context: Context,
        iconPackPackage: String,
        entry: IconPackEntry,
        fileName: String,
    ): Boolean {
        val packContext = context.createPackageContext(iconPackPackage, Context.CONTEXT_IGNORE_SECURITY)
        var exportedAny = false
        densities.forEach { density ->
            val drawable = loadIconPackDrawable(packContext, entry.drawableName, entry.dynamicIcon) ?: return@forEach
            val targetPx = (ICON_DP * density.dpi / 160f).roundToInt().coerceAtLeast(1)
            val bitmap = drawable.toNormalizedIconBitmap(targetPx)
            val outDir = File(iconRoot, density.folder).apply {
                mkdirs()
                setReadable(true, false)
                setExecutable(true, false)
            }
            val outFile = File(outDir, fileName)
            FileOutputStream(outFile).use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            outFile.setReadable(true, false)
            exportedAny = true
        }
        return exportedAny
    }

    fun Drawable.toNormalizedIconBitmap(targetPx: Int): Bitmap = toNormalizedPackBitmap(targetPx)

    fun fileNameForPackage(packageName: String): String {
        return packageName.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".png"
    }

    fun hasExportedIcon(fileName: String): Boolean {
        return densities.any { density -> File(File(iconRoot, density.folder), fileName).isFile }
    }

    fun deleteIconFiles(fileName: String) {
        normalizeFileName(fileName)?.let { safeName ->
            densities.forEach { density ->
                File(File(iconRoot, density.folder), safeName).delete()
            }
        }
    }

    fun findIconFile(fileName: String, densityDpi: Int): File? {
        val currentFolder = densityFolderForDpi(densityDpi)
        val current = File(File(iconRoot, currentFolder), fileName)
        if (current.isFile) return current
        densities.forEach { density ->
            val fallback = File(File(iconRoot, density.folder), fileName)
            if (fallback.isFile) return fallback
        }
        return null
    }

    fun ensureStorageDirs() {
        iconRoot.mkdirs()
        iconRoot.setReadable(true, false)
        iconRoot.setExecutable(true, false)
        densities.forEach { density ->
            File(iconRoot, density.folder).apply {
                mkdirs()
                setReadable(true, false)
                setExecutable(true, false)
            }
        }
    }

    fun sendReloadBroadcast(context: Context) {
        context.sendBroadcast(Intent(PacksManager.RELOAD_ACTION))
    }

    fun loadInstalledApplicationIndex(packageManager: PackageManager): Map<String, InstalledAppRecord> {
        val launcherPackages = loadLauncherVisiblePackages(packageManager)
        return runCatching { packageManager.getInstalledApplications(PackageManager.GET_META_DATA) }
            .getOrDefault(emptyList())
            .mapNotNull { info ->
                val packageName = info.packageName ?: return@mapNotNull null
                val label = info.loadLabel(packageManager)?.toString()?.takeIf { it.isNotBlank() }
                    ?: packageName
                InstalledAppRecord(
                    label = label,
                    packageName = packageName,
                    icon = loadOriginalApplicationIcon(packageManager, info),
                    isSystem = info.isSystemApplication(),
                    isLauncherVisible = launcherPackages.contains(packageName),
                )
            }.associateBy { it.packageName }
    }

    private fun parseStaticIconEntry(
        parser: XmlPullParser,
        iconPackPackage: String,
        installedIndex: Map<String, InstalledAppRecord>,
    ): IconPackEntry? {
        val component = parser.getAttributeValue(null, "component")
        val drawable = parser.getAttributeValue(null, "drawable")?.trim()?.takeIf { it.isNotBlank() }
        return buildIconPackEntry(iconPackPackage, parseComponentPackage(component), component, drawable, installedIndex)
    }

    private fun parseCalendarIconEntry(
        parser: XmlPullParser,
        packContext: Context,
        iconPackPackage: String,
        installedIndex: Map<String, InstalledAppRecord>,
    ): IconPackEntry? {
        val component = parser.getAttributeValue(null, "component")
        val prefix = parser.getAttributeValue(null, "prefix")?.trim()?.takeIf { it.isNotBlank() }
        val fallbackDrawable = parser.getAttributeValue(null, "drawable")?.trim()?.takeIf { it.isNotBlank() }
        val drawable = resolveCalendarDrawableName(packContext, prefix, fallbackDrawable)
        return buildIconPackEntry(
            iconPackPackage, parseComponentPackage(component), component, drawable, installedIndex,
            DynamicIconInfo(type = DYNAMIC_ICON_CALENDAR, calendarPrefix = prefix, calendarFallbackDrawable = fallbackDrawable),
        )
    }

    private fun parseDynamicClockIconEntry(
        parser: XmlPullParser,
        iconPackPackage: String,
        installedIndex: Map<String, InstalledAppRecord>,
    ): IconPackEntry? {
        val component = parser.getAttributeValue(null, "component")
        val drawable = parser.getAttributeValue(null, "drawable")?.trim()?.takeIf { it.isNotBlank() }
        val clockConfig = DynamicClockConfig(
            hourLayerIndex = parser.intAttribute("hourLayerIndex", "hourLayer", "hour", defaultValue = -1),
            minuteLayerIndex = parser.intAttribute("minuteLayerIndex", "minuteLayer", "minute", defaultValue = -1),
            secondLayerIndex = parser.intAttribute("secondLayerIndex", "secondLayer", "second", defaultValue = -1),
            defaultHour = parser.intAttribute("defaultHour", defaultValue = 0),
            defaultMinute = parser.intAttribute("defaultMinute", defaultValue = 0),
            defaultSecond = parser.intAttribute("defaultSecond", defaultValue = 0),
        )
        return buildIconPackEntry(
            iconPackPackage, parseComponentPackage(component), component, drawable, installedIndex,
            DynamicIconInfo(DYNAMIC_ICON_CLOCK, clockConfig),
        )
    }

    private fun buildIconPackEntry(
        iconPackPackage: String,
        appPackage: String?,
        component: String?,
        drawable: String?,
        installedIndex: Map<String, InstalledAppRecord>,
        dynamicIcon: DynamicIconInfo? = null,
    ): IconPackEntry? {
        if (appPackage.isNullOrBlank() || drawable.isNullOrBlank()) return null
        val installedApp = installedIndex[appPackage]
        return IconPackEntry(
            iconPackPackage = iconPackPackage,
            appPackageName = appPackage,
            componentName = component,
            drawableName = drawable,
            label = installedApp?.label ?: appPackage,
            installed = installedApp != null,
            isSystem = installedApp?.isSystem == true,
            dynamicIcon = dynamicIcon,
        )
    }

    private fun resolveCalendarDrawableName(packContext: Context, prefix: String?, fallbackDrawable: String?): String? {
        val day = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        val dayPadded = day.toString().padStart(2, '0')
        val candidates = buildList {
            prefix?.let { value ->
                add("$value$day")
                add("$value$dayPadded")
                add("${value}_$day")
                add("${value}_$dayPadded")
            }
            fallbackDrawable?.let { drawable ->
                add(drawable.replace("%02d", dayPadded).replace("%d", day.toString()))
                replaceTrailingCalendarNumber(drawable, day.toString(), dayPadded)?.let(::add)
                add(drawable)
            }
        }.distinct()
        return candidates.firstOrNull {
            val cleanName = it.substringBeforeLast('.').trim()
            if (cleanName.isBlank()) return@firstOrNull false
            val resources = packContext.resources
            resources.getIdentifier(cleanName, "drawable", packContext.packageName) != 0 ||
                resources.getIdentifier(cleanName, "mipmap", packContext.packageName) != 0
        } ?: candidates.firstOrNull()
    }

    private fun replaceTrailingCalendarNumber(value: String, day: String, dayPadded: String): String? {
        val extensionStart = value.lastIndexOf('.').takeIf { it > 0 } ?: value.length
        val numberStart = value.substring(0, extensionStart).indexOfLast { !it.isDigit() } + 1
        if (numberStart >= extensionStart) return null
        val replacement = if (extensionStart - numberStart >= 2) dayPadded else day
        return value.substring(0, numberStart) + replacement + value.substring(extensionStart)
    }

    private fun XmlPullParser.intAttribute(vararg names: String, defaultValue: Int): Int {
        return names.firstNotNullOfOrNull { name ->
            getAttributeValue(null, name)?.trim()?.toIntOrNull()
        } ?: defaultValue
    }

    private fun openAppFilterParser(context: Context): PackParserHolder? {
        runCatching {
            val input = context.assets.open("appfilter.xml")
            val parser = Xml.newPullParser()
            parser.setInput(input, null)
            return PackParserHolder(parser, input)
        }
        val resId = context.resources.getIdentifier("appfilter", "xml", context.packageName)
        if (resId != 0) {
            runCatching {
                val parser = context.resources.getXml(resId)
                return PackParserHolder(parser, parser)
            }
        }
        return null
    }

    private fun parseComponentPackage(component: String?): String? {
        if (component.isNullOrBlank()) return null
        val body = component.substringAfter("ComponentInfo{", component).substringBefore("}")
        return body.substringBefore("/").takeIf { it.isNotBlank() }
    }

    private fun decodeBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = false
                }.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input)?.copy(Bitmap.Config.ARGB_8888, false)
                }
            }
        }.getOrNull()
    }

    private fun exportBitmapForAllDensities(bitmap: Bitmap, fileName: String): Boolean {
        var exportedAny = false
        densities.forEach { density ->
            val targetPx = (ICON_DP * density.dpi / 160f).roundToInt().coerceAtLeast(1)
            val scaled = bitmap.toCenteredSquareBitmap(targetPx)
            val outDir = File(iconRoot, density.folder).apply {
                mkdirs()
                setReadable(true, false)
                setExecutable(true, false)
            }
            val outFile = File(outDir, fileName)
            FileOutputStream(outFile).use { output ->
                scaled.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            if (scaled !== bitmap) scaled.recycle()
            outFile.setReadable(true, false)
            exportedAny = true
        }
        return exportedAny
    }

    private fun Bitmap.toCenteredSquareBitmap(targetPx: Int): Bitmap {
        val output = Bitmap.createBitmap(targetPx, targetPx, Bitmap.Config.ARGB_8888)
        val scale = minOf(targetPx / width.toFloat(), targetPx / height.toFloat())
        val scaledWidth = (width * scale).roundToInt().coerceAtLeast(1)
        val scaledHeight = (height * scale).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(this, scaledWidth, scaledHeight, true)
        Canvas(output).drawBitmap(
            scaled,
            ((targetPx - scaledWidth) / 2f),
            ((targetPx - scaledHeight) / 2f),
            null,
        )
        if (scaled !== this) scaled.recycle()
        return output
    }

    private fun getDisplayName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun normalizeFileName(value: String): String? {
        val fileName = value.trim()
        if (fileName.isBlank() || fileName.contains('/') || fileName.contains('\\') || fileName.contains("..")) {
            return null
        }
        return if (fileName.endsWith(".png")) fileName else "$fileName.png"
    }

    private fun densityFolderForDpi(densityDpi: Int): String = when {
        densityDpi <= 160 -> "mdpi"
        densityDpi <= 240 -> "hdpi"
        densityDpi <= 320 -> "xhdpi"
        densityDpi <= 480 -> "xxhdpi"
        else -> "xxxhdpi"
    }

    private fun loadOriginalApplicationIcon(
        packageManager: PackageManager,
        info: ApplicationInfo,
    ): Drawable? {
        val iconResId = when {
            info.icon != 0 -> info.icon
            info.logo != 0 -> info.logo
            else -> 0
        }
        if (iconResId != 0) {
            runCatching {
                packageManager.getResourcesForApplication(info).getDrawable(iconResId, null)
            }.getOrNull()?.let { return it }
        }
        return runCatching { info.loadUnbadgedIcon(packageManager) }.getOrNull()
    }

    private fun loadLauncherVisiblePackages(packageManager: PackageManager): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return runCatching { packageManager.queryIntentActivities(intent, 0) }
            .getOrDefault(emptyList())
            .mapNotNullTo(linkedSetOf()) { it.activityInfo?.packageName ?: it.resolvePackageName }
    }

    private fun readDynamicIconInfo(sourceObject: JSONObject): DynamicIconInfo? {
        val dynamicObject = sourceObject.optJSONObject("dynamicIcon")
        val type = (dynamicObject?.optString("type") ?: sourceObject.optString("dynamicType"))
            .takeIf { it.isNotBlank() }
            ?: return null
        val clockObject = dynamicObject?.optJSONObject("clockConfig") ?: sourceObject.optJSONObject("clockConfig")
        return DynamicIconInfo(
            type = type,
            clockConfig = clockObject?.let {
                DynamicClockConfig(
                    hourLayerIndex = it.optInt("hourLayerIndex", -1),
                    minuteLayerIndex = it.optInt("minuteLayerIndex", -1),
                    secondLayerIndex = it.optInt("secondLayerIndex", -1),
                    defaultHour = it.optInt("defaultHour", 0),
                    defaultMinute = it.optInt("defaultMinute", 0),
                    defaultSecond = it.optInt("defaultSecond", 0),
                )
            },
            calendarPrefix = dynamicObject?.optString("calendarPrefix")?.takeIf { it.isNotBlank() }
                ?: sourceObject.optString("calendarPrefix").takeIf { it.isNotBlank() },
            calendarFallbackDrawable = dynamicObject?.optString("calendarFallbackDrawable")?.takeIf { it.isNotBlank() }
                ?: sourceObject.optString("calendarFallbackDrawable").takeIf { it.isNotBlank() },
        )
    }

    private fun writeDynamicIconInfo(dynamicIcon: DynamicIconInfo?): JSONObject? {
        dynamicIcon ?: return null
        return JSONObject().apply {
            put("type", dynamicIcon.type)
            dynamicIcon.calendarPrefix?.let { put("calendarPrefix", it) }
            dynamicIcon.calendarFallbackDrawable?.let { put("calendarFallbackDrawable", it) }
            dynamicIcon.clockConfig?.let { config ->
                put("clockConfig", JSONObject().apply {
                    put("hourLayerIndex", config.hourLayerIndex)
                    put("minuteLayerIndex", config.minuteLayerIndex)
                    put("secondLayerIndex", config.secondLayerIndex)
                    put("defaultHour", config.defaultHour)
                    put("defaultMinute", config.defaultMinute)
                    put("defaultSecond", config.defaultSecond)
                })
            }
        }
    }

    private fun readShapeAreaConfig(areaObject: JSONObject?): IconShapeAreaConfig? {
        if (areaObject == null) return null
        return IconShapeAreaConfig(
            stretchShape = areaObject.optBoolean("stretchShape", false),
            removeShape = areaObject.optBoolean("removeShape", false),
            scalePercent = areaObject.optDouble("scalePercent", PacksManager.DEFAULT_SHAPE_SCALE_PERCENT.toDouble()).toFloat(),
        ).normalized()
    }

    private fun writeShapeAreaConfig(config: IconShapeAreaConfig?): JSONObject? {
        val normalized = config?.normalized() ?: return null
        return JSONObject().apply {
            put("stretchShape", normalized.stretchShape)
            put("removeShape", normalized.removeShape)
            put("scalePercent", normalized.scalePercent)
        }
    }

    private fun findDrawableResourceNames(sourceDir: String): List<String> {
        return runCatching {
            ZipFile(sourceDir).use { zip ->
                zip.entries().asSequence()
                    .map { it.name }
                    .filter { path ->
                        (path.startsWith("res/drawable") || path.startsWith("res/mipmap")) &&
                            (path.endsWith(".png") || path.endsWith(".webp") || path.endsWith(".xml") || path.endsWith(".jpg"))
                    }
                    .map { path ->
                        path.substringAfterLast('/')
                            .substringBeforeLast('.')
                            .removeSuffix(".9")
                    }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .toList()
            }
        }.getOrDefault(emptyList())
    }

    internal fun ApplicationInfo.isSystemApplication(): Boolean {
        return (flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
            (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    }

    internal data class IconDensity(val folder: String, val dpi: Int)

    internal data class InstalledAppRecord(
        val label: String,
        val packageName: String,
        val icon: Drawable?,
        val isSystem: Boolean,
        val isLauncherVisible: Boolean,
    )
}
