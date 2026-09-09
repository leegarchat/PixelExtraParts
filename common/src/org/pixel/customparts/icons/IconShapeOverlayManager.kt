package org.pixel.customparts.icons

import android.content.Context
import android.content.om.FabricatedOverlay
import android.content.om.IOverlayManager
import android.content.om.OverlayIdentifier
import android.content.om.OverlayManager
import android.content.om.OverlayManagerTransaction
import android.content.pm.PackageManager
import android.content.res.Resources
import android.util.TypedValue
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.net.Uri
import android.os.ServiceManager
import android.os.UserHandle
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.util.PathParser
import android.util.Xml
import org.json.JSONObject
import org.pixel.customparts.R
import org.pixel.customparts.utils.ApkInstaller
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

object IconShapeOverlayManager {
    private const val TAG = "IconShapeOverlayManager"
    private const val TARGET_PACKAGE = "android"
    private const val CATEGORY = "android.theme.customization.adaptive_icon_shape"
    private const val CUSTOM_PACKAGE_PREFIX = "org.pixel.customparts.iconshape."
    private const val FABRICATED_OVERLAY_NAME = "icon_shape_custom"
    private const val FABRICATED_OVERLAY_ID =
        "org.pixel.customparts:$FABRICATED_OVERLAY_NAME"
    private const val FABRICATED_PREFS = "icon_shape_fabricated_overlay"
    private const val PREF_PATH = "path"
    private const val PREF_LABEL = "label"
    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

    data class ShapeOption(
        val id: String,
        val label: String,
        val packageName: String?,
        val pathData: String,
        val source: ShapeSource,
        val active: Boolean
    )

    enum class ShapeSource {
        DEFAULT,
        INSTALLED_OVERLAY,
        BUILTIN_PRESET,
        CUSTOM_OVERLAY
    }

    data class CustomParams(
        val mode: Int = MODE_SUPERELLIPSE,
        val sides: Int = 6,
        val roundness: Float = 4.5f,
        val inset: Float = 0f,
        val wave: Float = 0.12f,
        val rotation: Float = 0f,
        val scaleX: Float = 1f,
        val scaleY: Float = 1f,
        val offsetX: Float = 0f,
        val offsetY: Float = 0f
    )

    data class CompileResult(
        val success: Boolean,
        val packageName: String? = null,
        val apkPath: String? = null,
        val log: List<String> = emptyList(),
        val error: String? = null
    )

    const val MODE_SUPERELLIPSE = 0
    const val MODE_POLYGON = 1
    const val MODE_FLOWER = 2

    private val builtinPresets = listOf(
        BuiltinShape("ios", "iOS", "M24,0L76,0A24 24 0 0 1 100,24L100,76A24 24 0 0 1 76,100L24,100A24 24 0 0 1 0,76L0,24A24 24 0 0 1 24,0z"),
        BuiltinShape("rounded_rect", "Rounded rectangle", "M50,0L88,0 C94.4,0 100,5.4 100 12 L100,88 C100,94.6 94.6 100 88 100 L12,100 C5.4,100 0,94.6 0,88 L0 12 C0 5.4 5.4 0 12 0 L50,0 Z"),
        BuiltinShape("squircle", "Squircle", "M50,0 C10,0 0,10 0,50 0,90 10,100 50,100 90,100 100,90 100,50 100,10 90,0 50,0 Z"),
        BuiltinShape("square", "Square", "M50,0L100,0 100,100 0,100 0,0z"),
        BuiltinShape("hexagon", "Hexagon", "M 50,0 L 100,25, 100,75, 50,100, 0,75, 0,25 Z"),
        BuiltinShape("rounded_hexagon", "Rounded hexagon", "M4.8 33V67c0 5.8 3 11 8 13.7l29.4 17c4.9 2.7 11 2.7 15.9 0l29.4 -17c4.9 -2.7 8 -8 8 -13.7V33c0 -5.8 -3 -11 -8 -13.7l-29.4 -17c-4.9 -2.7 -11 -2.7 -15.9 0l-29.7 17C7.8 22.2 4.8 27.5 4.8 33z"),
        BuiltinShape("teardrop", "Teardrop", "M50,0 C77.6,0 100,22.4 100,50 L100,88 C100,94.6 94.6,100 88,100 L50,100 C22.4 100 0 77.6 0 50C0 22.4 22.4 0 50 0 Z"),
        BuiltinShape("tapered_rect", "Tapered rectangle", "M20,0 80,0 100,20 100,80 80,100 20,100 0,80 0,20 20,0 Z"),
        BuiltinShape("pebble", "Pebble", "M55,0 C25,0 0,25 0,50 0,78 28,100 55,100 85,100 100,85 100,58 100,30 86,0 55,0 Z"),
        BuiltinShape("flower", "Flower", "M50,0 C60.6,0 69.9,5.3 75.6,13.5 78.5,17.8 82.3,21.5 86.6,24.5 94.7,30.1 100,39.4 100,50 100,60.6 94.7,69.9 86.5,75.6 82.2,78.5 78.5,82.3 75.5,86.6 69.9,94.7 60.6,100 50,100 39.4,100 30.1,94.7 24.4,86.5 21.5,82.2 17.7,78.5 13.4,75.5 5.3,69.9 0,60.6 0,50 0,39.4 5.3,30.1 13.5,24.4 17.8,21.5 21.5,17.7 24.5,13.4 30.1,5.3 39.4,0 50,0 Z"),
        BuiltinShape("cloudy", "Cloudy", "M4,50 C2,45 0,39 0,33 C0,15 15,0 33,0 C39,0 45,2 50,4 C55,2 61,0 67,0 C85,0 100,15 100,33 C100,39 98,45 96,50 C98,55 100,61 100,66 C100,85 85,100 66,100 C61,100 55,98 50,96 C45,98 39,100 33,100 C15,100 0,85 0,66 C0,61 2,55 3,50 Z"),
        BuiltinShape("heart", "Heart", "M50,20 C45,0 30,0 25,0 20,0 0,5 0,34 0,72 40,97 50,100 60,97 100,72 100,34 100,5 80,0 75,0 70,0 55,0 50,20 Z"),
        BuiltinShape("leaf", "Leaf", "M-0.06,0.07h67.37C85.36,0.07,100,14.71,100,32.76v67.37H32.63c-18.06,0-32.69-14.64-32.69-32.69L-0.06,0.07z"),
        BuiltinShape("stretched", "Stretched", "M100,50 C100,77 77,100 50,100 L10,100 C4,100 0,96 0,90 L0,50 C0,22 22,0 50,0 L90,0 C96,0 100,4 100,10 L100,50 Z"),
        BuiltinShape("arch", "Arch", "M50 0C77.614 0 100 22.386 100 50C100 85.471 100 86.476 99.9 87.321 99.116 93.916 93.916 99.116 87.321 99.9 86.476 100 85.471 100 83.46 100H16.54C14.529 100 13.524 100 12.679 99.9 6.084 99.116 .884 93.916 .1 87.321 0 86.476 0 85.471 0 83.46L0 50C0 22.386 22.386 0 50 0Z")
    )

    fun loadOptions(context: Context): List<ShapeOption> {
        val overlayManager = overlayManager() ?: return builtinOnlyOptions(context)
        val allOverlays = runCatching {
            overlayManager.getOverlayInfosForTarget(TARGET_PACKAGE, UserHandle.USER_SYSTEM)
        }.getOrElse {
            Log.e(TAG, "Unable to read overlay infos", it)
            emptyList()
        }
        val overlays = allOverlays.filter {
            it.category == CATEGORY && !it.isFabricated &&
                !it.packageName.startsWith(CUSTOM_PACKAGE_PREFIX)
        }
        val fabricatedOverlay = allOverlays.firstOrNull {
            it.isFabricated && it.packageName == context.packageName &&
                it.overlayName == FABRICATED_OVERLAY_NAME
        }

        val activePackage = overlays.firstOrNull { it.isEnabled }?.packageName
        val fabricatedActive = fabricatedOverlay?.isEnabled == true
        val options = mutableListOf<ShapeOption>()
        val seenMasks = LinkedHashSet<String>()
        val defaultMask = readMask(context, TARGET_PACKAGE).ifEmpty { generatePath(CustomParams()) }
        options += ShapeOption(
            id = "default",
            label = context.getString(R.string.icon_shape_default),
            packageName = TARGET_PACKAGE,
            pathData = defaultMask,
            source = ShapeSource.DEFAULT,
            active = activePackage == null && !fabricatedActive
        )
        seenMasks += normalizeMask(defaultMask)

        overlays.sortedBy { it.priority }.forEach { info ->
            val mask = readMask(context, info.packageName)
            if (mask.isBlank()) return@forEach
            val source = if (info.packageName.startsWith(CUSTOM_PACKAGE_PREFIX)) {
                ShapeSource.CUSTOM_OVERLAY
            } else {
                ShapeSource.INSTALLED_OVERLAY
            }
            options += ShapeOption(
                id = info.packageName,
                label = loadLabel(context, info.packageName),
                packageName = info.packageName,
                pathData = mask,
                source = source,
                active = info.packageName == activePackage
            )
            seenMasks += normalizeMask(mask)
        }

        val fabricatedPath = context.getSharedPreferences(FABRICATED_PREFS, Context.MODE_PRIVATE)
            .getString(PREF_PATH, null)
        if (fabricatedOverlay != null && !fabricatedPath.isNullOrBlank()) {
            options += ShapeOption(
                id = FABRICATED_OVERLAY_ID,
                label = context.getSharedPreferences(FABRICATED_PREFS, Context.MODE_PRIVATE)
                    .getString(PREF_LABEL, "PixelParts shape") ?: "PixelParts shape",
                packageName = FABRICATED_OVERLAY_ID,
                pathData = fabricatedPath,
                source = ShapeSource.CUSTOM_OVERLAY,
                active = fabricatedActive
            )
            seenMasks += normalizeMask(fabricatedPath)
        }

        builtinPresets.forEach { preset ->
            if (seenMasks.add(normalizeMask(preset.pathData))) {
                options += ShapeOption(
                    id = "builtin:${preset.id}",
                    label = preset.label,
                    packageName = null,
                    pathData = preset.pathData,
                    source = ShapeSource.BUILTIN_PRESET,
                    active = false
                )
            }
        }
        return options
    }

    fun applyOption(context: Context, option: ShapeOption): Boolean {
        return when {
            option.packageName == TARGET_PACKAGE -> applyOverlay(context, TARGET_PACKAGE)
            option.packageName == FABRICATED_OVERLAY_ID -> applyFabricatedOverlay(context)
            option.packageName != null -> applyOverlay(context, option.packageName)
            else -> {
                val result = compileAndInstallCustom(context, option.label, option.pathData)
                result.success && applyFabricatedOverlay(context)
            }
        }
    }

    fun applyOverlay(context: Context, packageName: String): Boolean {
        val overlayManager = overlayManager() ?: return false
        return try {
            if (packageName == TARGET_PACKAGE) {
                val disabled = overlayManager.getOverlayInfosForTarget(TARGET_PACKAGE, UserHandle.USER_SYSTEM)
                    .filter { it.category == CATEGORY && !it.isFabricated && it.isEnabled }
                    .all { overlayManager.setEnabled(it.packageName, false, UserHandle.USER_SYSTEM) }
                if (!disabled) return false
                if (!setFabricatedEnabled(context, false)) return false
                if (!writeThemeCustomization(context, null)) return false
            } else {
                if (!setFabricatedEnabled(context, false)) return false
                if (!overlayManager.setEnabledExclusiveInCategory(packageName, UserHandle.USER_SYSTEM)) {
                    return false
                }
                val info = overlayManager.getOverlayInfo(packageName, UserHandle.USER_SYSTEM)
                if (info == null || !info.isEnabled) return false
                if (!writeThemeCustomization(context, packageName)) return false
            }
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Unable to apply icon shape overlay $packageName", t)
            false
        }
    }

    fun deleteCustomOverlay(context: Context, option: ShapeOption): Boolean {
        if (option.source != ShapeSource.CUSTOM_OVERLAY) return false
        if (option.packageName == FABRICATED_OVERLAY_ID) {
            return unregisterFabricatedOverlay(context)
        }
        val packageName = option.packageName ?: return false
        return try {
            val overlayDisabled = if (option.active) {
                applyOverlay(context, TARGET_PACKAGE)
            } else {
                overlayManager()?.setEnabled(packageName, false, UserHandle.USER_SYSTEM) == true
            }
            if (!overlayDisabled) return false
            ApkInstaller.uninstall(context, packageName)
        } catch (t: Throwable) {
            Log.e(TAG, "Unable to delete custom icon shape $packageName", t)
            false
        }
    }

    fun compileAndInstallCustom(
        context: Context,
        label: String,
        pathData: String,
        logCallback: ((String) -> Unit)? = null,
        installLogCallback: ((String) -> Unit)? = null
    ): CompileResult {
        val normalizedPath = pathData.trim().trim('"')
        if (!isValidPath(normalizedPath)) {
            return CompileResult(false, error = "Invalid path data")
        }
        return registerFabricatedOverlay(
            context, label, normalizedPath, logCallback,
            installLogCallback
        )
    }

    fun readCustomPath(context: Context, uri: Uri): String {
        val text = context.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        } ?: return ""
        return parseImportedMask(text) ?: text.trim().trim('"')
    }

    fun isValidPath(pathData: String): Boolean {
        return try {
            !TextUtils.isEmpty(pathData) && PathParser.createPathFromPathData(pathData) != null
        } catch (_: Throwable) {
            false
        }
    }

    fun generatePath(params: CustomParams): String {
        return when (params.mode) {
            MODE_POLYGON -> generatePolygonPath(params.sides.coerceIn(3, 12), params)
            MODE_FLOWER -> generateFlowerPath(params.sides.coerceIn(3, 12), params)
            else -> generateSuperellipsePath(params)
        }
    }

    private fun registerFabricatedOverlay(
        context: Context,
        label: String,
        pathData: String,
        logCallback: ((String) -> Unit)? = null,
        installLogCallback: ((String) -> Unit)? = null
    ): CompileResult {
        val log = mutableListOf<String>()
        fun emit(message: String) {
            log += message
            logCallback?.invoke(message)
        }

        return try {
            val manager = fabricatedOverlayManager(context)
                ?: return CompileResult(false, log = log, error = "OverlayManager unavailable")
            installLogCallback?.invoke("Registering fabricated overlay")
            if (!setFabricatedEnabled(context, false)) {
                return CompileResult(false, log = log, error = "Unable to disable old fabricated overlay")
            }

            val overlay = FabricatedOverlay.Builder(
                context.packageName,
                FABRICATED_OVERLAY_NAME,
                TARGET_PACKAGE
            ).setResourceValue(
                "android:string/config_icon_mask",
                TypedValue.TYPE_STRING,
                pathData
            ).setResourceValue(
                "android:bool/config_useRoundIcon",
                TypedValue.TYPE_INT_BOOLEAN,
                1
            ).build()
            val transaction = OverlayManagerTransaction.Builder()
            transaction.registerFabricatedOverlay(overlay)
            manager.commit(transaction.build())

            val info = manager.getOverlayInfo(
                OverlayIdentifier(context.packageName, FABRICATED_OVERLAY_NAME),
                UserHandle.SYSTEM
            ) ?: return CompileResult(false, log = log, error = "Fabricated overlay was not registered")

            context.getSharedPreferences(FABRICATED_PREFS, Context.MODE_PRIVATE).edit()
                .putString(PREF_PATH, pathData)
                .putString(PREF_LABEL, label.ifBlank { "PixelParts shape" })
                .apply()
            emit("Fabricated overlay registered: $FABRICATED_OVERLAY_ID")
            CompileResult(true, packageName = FABRICATED_OVERLAY_ID, log = log)
        } catch (t: Throwable) {
            Log.e(TAG, "Unable to register fabricated icon shape overlay", t)
            CompileResult(false, log = log, error = t.message ?: "Fabricated overlay failed")
        }
    }

    private fun applyFabricatedOverlay(context: Context): Boolean {
        return try {
            val manager = overlayManager() ?: return false
            val disabled = manager.getOverlayInfosForTarget(TARGET_PACKAGE, UserHandle.USER_SYSTEM)
                .filter { it.category == CATEGORY && !it.isFabricated && it.isEnabled }
                .all { manager.setEnabled(it.packageName, false, UserHandle.USER_SYSTEM) }
            if (!disabled || !setFabricatedEnabled(context, true)) return false
            writeThemeCustomization(context, null)
        } catch (t: Throwable) {
            Log.e(TAG, "Unable to apply fabricated icon shape overlay", t)
            false
        }
    }

    private fun unregisterFabricatedOverlay(context: Context): Boolean {
        return try {
            val manager = fabricatedOverlayManager(context) ?: return false
            val identifier = OverlayIdentifier(context.packageName, FABRICATED_OVERLAY_NAME)
            if (manager.getOverlayInfo(identifier, UserHandle.SYSTEM) == null) return false
            setFabricatedEnabled(context, false)
            val transaction = OverlayManagerTransaction.Builder()
            transaction.unregisterFabricatedOverlay(identifier)
            manager.commit(transaction.build())
            context.getSharedPreferences(FABRICATED_PREFS, Context.MODE_PRIVATE).edit().clear().apply()
            manager.getOverlayInfo(identifier, UserHandle.SYSTEM) == null
        } catch (t: Throwable) {
            Log.e(TAG, "Unable to unregister fabricated icon shape overlay", t)
            false
        }
    }

    private fun setFabricatedEnabled(context: Context, enabled: Boolean): Boolean {
        val manager = fabricatedOverlayManager(context) ?: return false
        val identifier = OverlayIdentifier(context.packageName, FABRICATED_OVERLAY_NAME)
        val current = runCatching {
            manager.getOverlayInfo(identifier, UserHandle.SYSTEM)
        }.getOrNull() ?: return !enabled
        if (current.isEnabled() == enabled) return true

        val transaction = OverlayManagerTransaction.Builder()
        transaction.setEnabled(identifier, enabled, UserHandle.USER_SYSTEM)
        manager.commit(transaction.build())
        return manager.getOverlayInfo(identifier, UserHandle.SYSTEM)?.isEnabled() == enabled
    }

    private fun fabricatedOverlayManager(context: Context): OverlayManager? {
        return context.getSystemService(OverlayManager::class.java)
    }

    private fun builtinOnlyOptions(context: Context): List<ShapeOption> {
        val defaultMask = readMask(context, TARGET_PACKAGE).ifEmpty { generatePath(CustomParams()) }
        return listOf(
            ShapeOption("default", context.getString(R.string.icon_shape_default), TARGET_PACKAGE, defaultMask, ShapeSource.DEFAULT, true)
        ) + builtinPresets.map {
            ShapeOption("builtin:${it.id}", it.label, null, it.pathData, ShapeSource.BUILTIN_PRESET, false)
        }
    }

    private fun overlayManager(): IOverlayManager? {
        return IOverlayManager.Stub.asInterface(ServiceManager.getService(Context.OVERLAY_SERVICE))
    }

    private fun readMask(context: Context, packageName: String): String {
        return try {
            val resources = if (packageName == TARGET_PACKAGE) {
                Resources.getSystem()
            } else {
                context.packageManager.getResourcesForApplication(packageName)
            }
            val id = resources.getIdentifier("config_icon_mask", "string", packageName)
            if (id != 0) resources.getString(id).trim() else ""
        } catch (_: PackageManager.NameNotFoundException) {
            ""
        } catch (_: Resources.NotFoundException) {
            ""
        }
    }

    private fun loadLabel(context: Context, packageName: String): String {
        return try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            info.loadLabel(context.packageManager).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName.substringAfterLast('.')
        }
    }

    private fun writeThemeCustomization(context: Context, packageName: String?): Boolean {
        val raw = Settings.Secure.getStringForUser(
            context.contentResolver,
            Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES,
            UserHandle.USER_CURRENT
        )
        val json = if (raw.isNullOrBlank()) JSONObject() else JSONObject(raw)
        if (packageName == null) {
            json.remove(CATEGORY)
        } else {
            json.put(CATEGORY, packageName)
        }
        return Settings.Secure.putStringForUser(
            context.contentResolver,
            Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES,
            json.toString(),
            UserHandle.USER_CURRENT
        )
    }

    private fun parseImportedMask(text: String): String? {
        val trimmed = text.trim()
        if (!trimmed.startsWith("<")) return null

        return runCatching {
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(trimmed))

            val paths = mutableListOf<Path>()
            var rootName = ""
            var viewport: RectF? = null

            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG) continue
                val tag = parser.name ?: continue
                if (rootName.isEmpty()) rootName = tag.lowercase(Locale.US)

                when (tag.lowercase(Locale.US)) {
                    "string" -> {
                        if (attr(parser, "name") == "config_icon_mask") {
                            return@runCatching parser.nextText().trim().trim('"').takeIf(::isValidPath)
                        }
                    }
                    "svg" -> viewport = parseSvgViewport(parser)
                    "vector" -> viewport = parseVectorViewport(parser)
                    "path" -> parsePathTag(parser)?.let(paths::add)
                    "rect" -> parseRectTag(parser)?.let(paths::add)
                    "circle" -> parseCircleTag(parser)?.let(paths::add)
                    "ellipse" -> parseEllipseTag(parser)?.let(paths::add)
                    "polygon", "polyline" -> parsePointsTag(parser, close = tag.equals("polygon", true))?.let(paths::add)
                }
            }

            if (paths.isEmpty()) {
                Regex(
                    "<string[^>]+name\\s*=\\s*\"config_icon_mask\"[^>]*>(.*?)</string>",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
                ).find(trimmed)?.groupValues?.getOrNull(1)?.trim()?.trim('"')?.takeIf(::isValidPath)
            } else {
                normalizeImportedPaths(paths, viewport)
            }
        }.getOrNull()
    }

    private fun attr(parser: XmlPullParser, name: String): String? {
        parser.getAttributeValue(ANDROID_NS, name)?.let { return it }
        parser.getAttributeValue(null, name)?.let { return it }
        for (index in 0 until parser.attributeCount) {
            if (parser.getAttributeName(index).substringAfter(':') == name) {
                return parser.getAttributeValue(index)
            }
        }
        return null
    }

    private fun parseSvgViewport(parser: XmlPullParser): RectF? {
        parseViewBox(attr(parser, "viewBox"))?.let { return it }
        val width = parseFloatValue(attr(parser, "width")) ?: return null
        val height = parseFloatValue(attr(parser, "height")) ?: return null
        return RectF(0f, 0f, width, height)
    }

    private fun parseVectorViewport(parser: XmlPullParser): RectF? {
        val width = parseFloatValue(attr(parser, "viewportWidth")) ?: return null
        val height = parseFloatValue(attr(parser, "viewportHeight")) ?: return null
        return RectF(0f, 0f, width, height)
    }

    private fun parseViewBox(value: String?): RectF? {
        val numbers = parseFloatList(value)
        if (numbers.size < 4) return null
        val width = numbers[2]
        val height = numbers[3]
        if (width <= 0f || height <= 0f) return null
        return RectF(numbers[0], numbers[1], numbers[0] + width, numbers[1] + height)
    }

    private fun parsePathTag(parser: XmlPullParser): Path? {
        val pathData = attr(parser, "pathData") ?: attr(parser, "d") ?: return null
        return runCatching { PathParser.createPathFromPathData(pathData) }.getOrNull()
    }

    private fun parseRectTag(parser: XmlPullParser): Path? {
        val x = parseFloatValue(attr(parser, "x")) ?: 0f
        val y = parseFloatValue(attr(parser, "y")) ?: 0f
        val width = parseFloatValue(attr(parser, "width")) ?: return null
        val height = parseFloatValue(attr(parser, "height")) ?: return null
        if (width <= 0f || height <= 0f) return null
        val rx = parseFloatValue(attr(parser, "rx")) ?: 0f
        val ry = parseFloatValue(attr(parser, "ry")) ?: rx
        return Path().apply {
            if (rx > 0f || ry > 0f) {
                addRoundRect(RectF(x, y, x + width, y + height), rx, ry, Path.Direction.CW)
            } else {
                addRect(x, y, x + width, y + height, Path.Direction.CW)
            }
        }
    }

    private fun parseCircleTag(parser: XmlPullParser): Path? {
        val cx = parseFloatValue(attr(parser, "cx")) ?: 0f
        val cy = parseFloatValue(attr(parser, "cy")) ?: 0f
        val radius = parseFloatValue(attr(parser, "r")) ?: return null
        if (radius <= 0f) return null
        return Path().apply { addCircle(cx, cy, radius, Path.Direction.CW) }
    }

    private fun parseEllipseTag(parser: XmlPullParser): Path? {
        val cx = parseFloatValue(attr(parser, "cx")) ?: 0f
        val cy = parseFloatValue(attr(parser, "cy")) ?: 0f
        val rx = parseFloatValue(attr(parser, "rx")) ?: return null
        val ry = parseFloatValue(attr(parser, "ry")) ?: return null
        if (rx <= 0f || ry <= 0f) return null
        return Path().apply { addOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), Path.Direction.CW) }
    }

    private fun parsePointsTag(parser: XmlPullParser, close: Boolean): Path? {
        val numbers = parseFloatList(attr(parser, "points"))
        if (numbers.size < 4) return null
        return Path().apply {
            moveTo(numbers[0], numbers[1])
            var index = 2
            while (index + 1 < numbers.size) {
                lineTo(numbers[index], numbers[index + 1])
                index += 2
            }
            if (close) close()
        }
    }

    private fun normalizeImportedPaths(paths: List<Path>, viewport: RectF?): String? {
        val sourceBounds = viewport ?: combinedBounds(paths) ?: return null
        if (sourceBounds.width() <= 0f || sourceBounds.height() <= 0f) return null

        val matrix = Matrix().apply {
            setTranslate(-sourceBounds.left, -sourceBounds.top)
            postScale(100f / sourceBounds.width(), 100f / sourceBounds.height())
        }
        val result = paths.joinToString(separator = "") { original ->
            Path(original).apply { transform(matrix) }.toPathData()
        }.trim()
        return result.takeIf(::isValidPath)
    }

    private fun combinedBounds(paths: List<Path>): RectF? {
        val result = RectF()
        var hasBounds = false
        paths.forEach { path ->
            val bounds = RectF()
            path.computeBounds(bounds, true)
            if (!bounds.isEmpty) {
                if (hasBounds) result.union(bounds) else result.set(bounds)
                hasBounds = true
            }
        }
        return result.takeIf { hasBounds }
    }

    private fun Path.toPathData(): String {
        val builder = StringBuilder()
        val measure = PathMeasure(this, false)
        val pos = FloatArray(2)
        do {
            val length = measure.length
            if (length <= 0f) continue
            val steps = max(12, ceil(length / 2f).toInt())
            for (step in 0..steps) {
                measure.getPosTan(length * step / steps, pos, null)
                builder.append(if (step == 0) "M" else "L")
                builder.append(format(pos[0].toDouble())).append(',').append(format(pos[1].toDouble()))
            }
            builder.append('Z')
        } while (measure.nextContour())
        return builder.toString()
    }

    private fun parseFloatValue(value: String?): Float? =
        Regex("[-+]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?")
            .find(value.orEmpty())
            ?.value
            ?.toFloatOrNull()

    private fun parseFloatList(value: String?): List<Float> =
        Regex("[-+]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?")
            .findAll(value.orEmpty())
            .mapNotNull { it.value.toFloatOrNull() }
            .toList()

    private fun generateSuperellipsePath(params: CustomParams): String {
        val n = params.roundness.coerceIn(1.5f, 8f).toDouble()
        val radius = (50f - params.inset.coerceIn(0f, 24f)).toDouble()
        val points = (0 until 96).map { index ->
            val angle = 2.0 * PI * index / 96.0
            val c = cos(angle)
            val s = sin(angle)
            val x = 50.0 + sign(c) * abs(c).pow(2.0 / n) * radius
            val y = 50.0 + sign(s) * abs(s).pow(2.0 / n) * radius
            x to y
        }
        return pointsToPath(transformPoints(points, params))
    }

    private fun generatePolygonPath(sides: Int, params: CustomParams): String {
        val radius = 50.0 - params.inset.coerceIn(0f, 24f)
        val angleOffset = Math.toRadians(-90.0)
        val points = (0 until sides).map { index ->
            val angle = 2.0 * PI * index / sides + angleOffset
            50.0 + cos(angle) * radius to 50.0 + sin(angle) * radius
        }
        return pointsToPath(transformPoints(points, params))
    }

    private fun generateFlowerPath(sides: Int, params: CustomParams): String {
        val baseRadius = 43.0 - params.inset.coerceIn(0f, 18f)
        val amount = params.wave.coerceIn(0f, 0.35f)
        val angleOffset = Math.toRadians(-90.0)
        val points = (0 until 120).map { index ->
            val angle = 2.0 * PI * index / 120.0 + angleOffset
            val radius = baseRadius * (1.0 + amount * cos(sides * angle))
            50.0 + cos(angle) * radius to 50.0 + sin(angle) * radius
        }
        return pointsToPath(transformPoints(points, params))
    }

    private fun transformPoints(
        points: List<Pair<Double, Double>>,
        params: CustomParams
    ): List<Pair<Double, Double>> {
        val rotation = Math.toRadians(params.rotation.toDouble())
        val cosRotation = cos(rotation)
        val sinRotation = sin(rotation)
        val scaleX = params.scaleX.coerceIn(0.4f, 1.6f).toDouble()
        val scaleY = params.scaleY.coerceIn(0.4f, 1.6f).toDouble()
        val offsetX = params.offsetX.coerceIn(-35f, 35f).toDouble()
        val offsetY = params.offsetY.coerceIn(-35f, 35f).toDouble()
        return points.map { (x, y) ->
            val centeredX = (x - 50.0) * scaleX
            val centeredY = (y - 50.0) * scaleY
            val rotatedX = centeredX * cosRotation - centeredY * sinRotation
            val rotatedY = centeredX * sinRotation + centeredY * cosRotation
            50.0 + rotatedX + offsetX to 50.0 + rotatedY + offsetY
        }
    }

    private fun pointsToPath(points: List<Pair<Double, Double>>): String {
        if (points.isEmpty()) return ""
        val builder = StringBuilder()
        points.forEachIndexed { index, point ->
            builder.append(if (index == 0) "M" else "L")
            builder.append(format(point.first)).append(',').append(format(point.second))
        }
        builder.append('Z')
        return builder.toString()
    }

    private fun format(value: Double): String {
        val clamped = min(100.0, max(0.0, value))
        return String.format(Locale.US, "%.2f", clamped).trimEnd('0').trimEnd('.')
    }

    private fun normalizeMask(pathData: String): String =
        pathData.lowercase(Locale.US).replace(Regex("\\s+"), "")

    private fun sanitizeName(name: String): String {
        return name.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9_]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .ifEmpty { "custom" }
    }

    private fun xmlEscape(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private data class BuiltinShape(
        val id: String,
        val label: String,
        val pathData: String
    )

}
