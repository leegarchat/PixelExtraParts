package org.pixel.extra.parts.icons

import org.pixel.extra.parts.icons.IconPackBitmaps.toCenteredSquareBitmap
import org.pixel.extra.parts.icons.IconPackBitmaps.toNormalizedIconBitmap

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
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
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pixel.extra.parts.R
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Calendar
import java.util.Locale
import java.util.zip.ZipFile
import kotlin.math.roundToInt

// Shared storage helpers for icon packs (extracted from IconPackManager to keep files small).

internal data class IconDensity(val folder: String, val dpi: Int)

internal data class InstalledAppRecord(
    val label: String,
    val packageName: String,
    val icon: android.graphics.drawable.Drawable?,
    val isSystem: Boolean,
    val isLauncherVisible: Boolean
)
internal object IconPackStore {

    private const val TAG = "IconPackManager"

    internal const val ICON_DP = 48


    internal val iconRoot = File("/data/pixelparts/IconsManager")
    internal val iconMapFile = File(iconRoot, "icon_map.json")
    internal val ioLock = Any()

    internal val densities = listOf(
        IconDensity("mdpi", 160),
        IconDensity("hdpi", 240),
        IconDensity("xhdpi", 320),
        IconDensity("xxhdpi", 480),
        IconDensity("xxxhdpi", 640)
    )

    internal fun exportIconForAllDensities(
        context: Context,
        iconPackPackage: String,
        entry: IconPackEntry,
        fileName: String
    ): Boolean {
        val packContext = IconPackBitmaps.createIconPackContext(context, iconPackPackage)
        var exportedAny = false
        densities.forEach { density ->
            val drawable = IconPackParser.loadIconPackDrawable(packContext, entry.drawableName, entry.dynamicIcon) ?: return@forEach
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

    internal fun decodeBitmapFromUri(context: Context, uri: Uri): Bitmap? {
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

    internal fun exportBitmapForAllDensities(bitmap: Bitmap, fileName: String): Boolean {
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
            if (scaled !== bitmap) {
                scaled.recycle()
            }
            outFile.setReadable(true, false)
            exportedAny = true
        }
        return exportedAny
    }

    internal fun getDisplayName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    internal fun hasExportedIcon(fileName: String): Boolean {
        return densities.any { density -> File(File(iconRoot, density.folder), fileName).isFile }
    }

    internal fun deleteIconFiles(fileName: String) {
        normalizeFileName(fileName)?.let { safeName ->
            densities.forEach { density ->
                File(File(iconRoot, density.folder), safeName).delete()
            }
        }
    }

    internal fun readIconMap(): IconMapSnapshot {
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
                        dynamicIcon = readDynamicIconInfo(source)
                    )
                }

                val packs = mutableMapOf<String, IconPackVersion>()
                root.optJSONObject("packs")?.keys()?.forEach { packageName ->
                    val pack = root.optJSONObject("packs")?.optJSONObject(packageName) ?: return@forEach
                    packs[packageName] = IconPackVersion(
                        packageName = packageName,
                        label = pack.optString("label", packageName),
                        versionCode = pack.optLong("versionCode", 0L),
                        appliedAt = pack.optLong("appliedAt", 0L)
                    )
                }

                val shapeOverrides = mutableMapOf<String, AppIconShapeOverrides>()
                root.optJSONObject("shapeOverrides")?.keys()?.forEach { packageName ->
                    val appObject = root.optJSONObject("shapeOverrides")?.optJSONObject(packageName) ?: return@forEach
                    val overrides = AppIconShapeOverrides(
                        system = readShapeAreaConfig(appObject.optJSONObject(AppIconShapeArea.SYSTEM.jsonKey)),
                        notification = readShapeAreaConfig(appObject.optJSONObject(AppIconShapeArea.NOTIFICATION.jsonKey)),
                        launcher = readShapeAreaConfig(appObject.optJSONObject(AppIconShapeArea.LAUNCHER.jsonKey))
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

    internal fun writeIconMap(snapshot: IconMapSnapshot) {
        IconPackManager.clearDashboardCache()
        ensureStorageDirs()
        val root = JSONObject()
        root.put("version", 2)
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
                writeShapeAreaConfig(normalized.notification)?.let { appObject.put(AppIconShapeArea.NOTIFICATION.jsonKey, it) }
                writeShapeAreaConfig(normalized.launcher)?.let { appObject.put(AppIconShapeArea.LAUNCHER.jsonKey, it) }
                if (appObject.length() > 0) {
                    shapeOverrides.put(packageName, appObject)
                }
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

    internal fun readShapeAreaConfig(areaObject: JSONObject?): IconShapeAreaConfig? {
        if (areaObject == null) return null
        return IconShapeAreaConfig(
            stretchShape = areaObject.optBoolean("stretchShape", false),
            removeShape = areaObject.optBoolean("removeShape", false),
            scalePercent = areaObject.optDouble("scalePercent", IconPackManager.DEFAULT_SHAPE_SCALE_PERCENT.toDouble()).toFloat()
        ).normalized()
    }

    internal fun writeShapeAreaConfig(config: IconShapeAreaConfig?): JSONObject? {
        val normalized = config?.normalized() ?: return null
        return JSONObject().apply {
            put("stretchShape", normalized.stretchShape)
            put("removeShape", normalized.removeShape)
            put("scalePercent", normalized.scalePercent)
        }
    }

    internal fun readDynamicIconInfo(sourceObject: JSONObject): DynamicIconInfo? {
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
                    defaultSecond = it.optInt("defaultSecond", 0)
                )
            },
            calendarPrefix = dynamicObject?.optString("calendarPrefix")?.takeIf { it.isNotBlank() }
                ?: sourceObject.optString("calendarPrefix").takeIf { it.isNotBlank() },
            calendarFallbackDrawable = dynamicObject?.optString("calendarFallbackDrawable")?.takeIf { it.isNotBlank() }
                ?: sourceObject.optString("calendarFallbackDrawable").takeIf { it.isNotBlank() }
        )
    }

    internal fun writeDynamicIconInfo(dynamicIcon: DynamicIconInfo?): JSONObject? {
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

    internal fun ensureStorageDirs() {
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

    internal fun findDrawableResourceNames(sourceDir: String): List<String> {
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

    internal fun findIconFile(fileName: String, densityDpi: Int): File? {
        val currentFolder = densityFolderForDpi(densityDpi)
        val current = File(File(iconRoot, currentFolder), fileName)
        if (current.isFile) return current
        densities.forEach { density ->
            val fallback = File(File(iconRoot, density.folder), fileName)
            if (fallback.isFile) return fallback
        }
        return null
    }

    internal fun densityFolderForDpi(densityDpi: Int): String = when {
        densityDpi <= 160 -> "mdpi"
        densityDpi <= 240 -> "hdpi"
        densityDpi <= 320 -> "xhdpi"
        densityDpi <= 480 -> "xxhdpi"
        else -> "xxxhdpi"
    }

    internal fun fileNameForPackage(packageName: String): String {
        return packageName.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".png"
    }

    internal fun normalizeFileName(value: String): String? {
        val fileName = value.trim()
        if (fileName.isBlank() || fileName.contains('/') || fileName.contains('\\') || fileName.contains("..")) {
            return null
        }
        return if (fileName.endsWith(".png")) fileName else "$fileName.png"
    }

}