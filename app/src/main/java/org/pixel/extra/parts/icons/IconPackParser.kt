package org.pixel.extra.parts.icons

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

// Shared icon-pack parsing helpers (extracted from IconPackManager to keep files small).
internal object IconPackParser {

    private const val TAG = "IconPackManager"

    private fun android.content.pm.ApplicationInfo.isSystemApplication(): Boolean {
        return (flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0 ||
            (flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    }

    internal val themeActions = listOf(
        "org.adw.launcher.THEMES",
        "com.teslacoilsw.launcher.THEME",
        "com.novalauncher.THEME"
    )

    internal fun discoverIconPackPackages(packageManager: PackageManager): List<String> {
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

    internal fun parseIconPackEntries(
        context: Context,
        iconPackPackage: String,
        installedIndex: Map<String, InstalledAppRecord>
    ): List<IconPackEntry> {
        val packContext = runCatching { IconPackBitmaps.createIconPackContext(context, iconPackPackage) }.getOrNull()
            ?: return emptyList()
        val entries = mutableListOf<IconPackEntry>()

        runCatching {
            openAppFilterParser(packContext)?.useParser { parser ->
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG) {
                        when (parser.name) {
                            "item" -> parseStaticIconEntry(
                                parser,
                                iconPackPackage,
                                installedIndex
                            )?.let { entries += it }

                            "calendar" -> parseCalendarIconEntry(
                                parser,
                                packContext,
                                iconPackPackage,
                                installedIndex
                            )?.let { entries += it }

                            "dynamic-clock", "dynamic_clock", "clock" -> parseDynamicClockIconEntry(
                                parser,
                                iconPackPackage,
                                installedIndex
                            )?.let { entries += it }
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
                    .thenBy { it.drawableName }
            )
    }

    internal fun parseStaticIconEntry(
        parser: XmlPullParser,
        iconPackPackage: String,
        installedIndex: Map<String, InstalledAppRecord>
    ): IconPackEntry? {
        val component = parser.getAttributeValue(null, "component")
        val drawable = parser.getAttributeValue(null, "drawable")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        val appPackage = parseComponentPackage(component)
        return buildIconPackEntry(iconPackPackage, appPackage, component, drawable, installedIndex)
    }

    internal fun parseCalendarIconEntry(
        parser: XmlPullParser,
        packContext: Context,
        iconPackPackage: String,
        installedIndex: Map<String, InstalledAppRecord>
    ): IconPackEntry? {
        val component = parser.getAttributeValue(null, "component")
        val appPackage = parseComponentPackage(component)
        val prefix = parser.getAttributeValue(null, "prefix")?.trim()?.takeIf { it.isNotBlank() }
        val fallbackDrawable = parser.getAttributeValue(null, "drawable")?.trim()?.takeIf { it.isNotBlank() }
        val drawable = resolveCalendarDrawableName(packContext, prefix, fallbackDrawable)
        return buildIconPackEntry(
            iconPackPackage,
            appPackage,
            component,
            drawable,
            installedIndex,
            DynamicIconInfo(
                type = IconPackManager.DYNAMIC_ICON_CALENDAR,
                calendarPrefix = prefix,
                calendarFallbackDrawable = fallbackDrawable
            )
        )
    }

    internal fun parseDynamicClockIconEntry(
        parser: XmlPullParser,
        iconPackPackage: String,
        installedIndex: Map<String, InstalledAppRecord>
    ): IconPackEntry? {
        val component = parser.getAttributeValue(null, "component")
        val drawable = parser.getAttributeValue(null, "drawable")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        val appPackage = parseComponentPackage(component)
        val clockConfig = DynamicClockConfig(
            hourLayerIndex = parser.intAttribute("hourLayerIndex", "hourLayer", "hour", defaultValue = -1),
            minuteLayerIndex = parser.intAttribute("minuteLayerIndex", "minuteLayer", "minute", defaultValue = -1),
            secondLayerIndex = parser.intAttribute("secondLayerIndex", "secondLayer", "second", defaultValue = -1),
            defaultHour = parser.intAttribute("defaultHour", defaultValue = 0),
            defaultMinute = parser.intAttribute("defaultMinute", defaultValue = 0),
            defaultSecond = parser.intAttribute("defaultSecond", defaultValue = 0)
        )
        return buildIconPackEntry(
            iconPackPackage,
            appPackage,
            component,
            drawable,
            installedIndex,
            DynamicIconInfo(IconPackManager.DYNAMIC_ICON_CLOCK, clockConfig)
        )
    }

    internal fun buildIconPackEntry(
        iconPackPackage: String,
        appPackage: String?,
        component: String?,
        drawable: String?,
        installedIndex: Map<String, InstalledAppRecord>,
        dynamicIcon: DynamicIconInfo? = null
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
            dynamicIcon = dynamicIcon
        )
    }

    internal fun singleSelectionEntries(
        context: Context,
        iconPackPackage: String,
        packageName: String,
        drawableName: String?,
        installedIndex: Map<String, InstalledAppRecord>
    ): List<IconPackEntry> {
        val parsedEntries = parseIconPackEntries(context, iconPackPackage, installedIndex)
            .filter { it.appPackageName == packageName && (drawableName == null || it.drawableName == drawableName) }
        if (parsedEntries.isNotEmpty() || drawableName.isNullOrBlank()) {
            return parsedEntries
        }

        val installedApp = installedIndex[packageName]
        return listOf(
            IconPackEntry(
                iconPackPackage = iconPackPackage,
                appPackageName = packageName,
                componentName = null,
                drawableName = drawableName,
                label = installedApp?.label ?: packageName,
                installed = installedApp != null,
                isSystem = installedApp?.isSystem == true
            )
        )
    }

    internal fun resolveCalendarDrawableName(
        context: Context,
        prefix: String?,
        fallbackDrawable: String?
    ): String? {
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
        return candidates.firstOrNull { drawableResourceExists(context, it) } ?: candidates.firstOrNull()
    }

    internal fun replaceTrailingCalendarNumber(value: String, day: String, dayPadded: String): String? {
        val extensionStart = value.lastIndexOf('.').takeIf { it > 0 } ?: value.length
        val numberStart = value.substring(0, extensionStart).indexOfLast { !it.isDigit() } + 1
        if (numberStart >= extensionStart) return null
        val replacement = if (extensionStart - numberStart >= 2) dayPadded else day
        return value.substring(0, numberStart) + replacement + value.substring(extensionStart)
    }

    internal fun drawableResourceExists(context: Context, drawableName: String): Boolean {
        val cleanName = drawableName.substringBeforeLast('.').trim()
        if (cleanName.isBlank()) return false
        val resources = context.resources
        return resources.getIdentifier(cleanName, "drawable", context.packageName) != 0 ||
            resources.getIdentifier(cleanName, "mipmap", context.packageName) != 0
    }

    internal fun XmlPullParser.intAttribute(
        vararg names: String,
        defaultValue: Int
    ): Int {
        return names.firstNotNullOfOrNull { name ->
            getAttributeValue(null, name)?.trim()?.toIntOrNull()
        } ?: defaultValue
    }

    internal fun openAppFilterParser(context: Context): ParserHolder? {
        runCatching {
            val input = context.assets.open("appfilter.xml")
            val parser = Xml.newPullParser()
            parser.setInput(input, null)
            return ParserHolder(parser, input)
        }

        val resId = context.resources.getIdentifier("appfilter", "xml", context.packageName)
        if (resId != 0) {
            runCatching {
                val parser = context.resources.getXml(resId)
                return ParserHolder(parser, parser)
            }
        }

        return null
    }

    internal fun parseComponentPackage(component: String?): String? {
        if (component.isNullOrBlank()) return null
        val body = component.substringAfter("ComponentInfo{", component).substringBefore("}")
        return body.substringBefore("/").takeIf { it.isNotBlank() }
    }

    internal fun filterEntriesForMode(entries: List<IconPackEntry>, mode: IconApplyMode): List<IconPackEntry> {
        return when (mode) {
            IconApplyMode.ALL_PACK -> entries
            IconApplyMode.SYSTEM_ONLY -> entries.filter { it.installed && it.isSystem }
            IconApplyMode.USER_ONLY -> entries.filter { it.installed && !it.isSystem }
            IconApplyMode.INSTALLED_ONLY -> entries.filter { it.installed }
        }
    }

    internal fun loadIconPackDrawable(
        context: Context,
        drawableName: String,
        dynamicIcon: DynamicIconInfo? = null
    ): Drawable? {
        val cleanName = drawableName.substringBeforeLast('.').trim()
        if (cleanName.isBlank()) return null
        val resources = context.resources
        val resId = resources.getIdentifier(cleanName, "drawable", context.packageName)
            .takeIf { it != 0 }
            ?: resources.getIdentifier(cleanName, "mipmap", context.packageName).takeIf { it != 0 }
            ?: return null
        val drawable = runCatching { resources.getDrawable(resId, context.theme) }.getOrNull()
            ?: return null
        return if (dynamicIcon?.type == IconPackManager.DYNAMIC_ICON_CLOCK && dynamicIcon.clockConfig != null) {
            DynamicClockDrawable(drawable, dynamicIcon.clockConfig)
        } else {
            drawable
        }
    }

    internal fun loadInstalledApplicationIndex(packageManager: PackageManager): Map<String, InstalledAppRecord> {
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
                    isLauncherVisible = launcherPackages.contains(packageName)
                )
            }.associateBy { it.packageName }
    }

    internal fun loadOriginalApplicationIcon(
        packageManager: PackageManager,
        info: ApplicationInfo
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

    internal fun loadLauncherVisiblePackages(packageManager: PackageManager): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return runCatching { packageManager.queryIntentActivities(intent, 0) }
            .getOrDefault(emptyList())
            .mapNotNullTo(linkedSetOf()) { it.activityInfo?.packageName ?: it.resolvePackageName }
    }

    internal class ParserHolder(
        val parser: XmlPullParser,
        private val closeable: AutoCloseable
    ) : AutoCloseable {
        override fun close() {
            closeable.close()
        }
    }

    internal inline fun ParserHolder.useParser(block: (XmlPullParser) -> Unit) {
        use { holder -> block(holder.parser) }
    }

    internal class DynamicClockDrawable(
        private val drawable: Drawable,
        private val config: DynamicClockConfig
    ) : Drawable() {
        private var alpha: Int = 255
        private var colorFilter: ColorFilter? = null

        override fun draw(canvas: Canvas) {
            val layerDrawable = drawable as? LayerDrawable
            if (layerDrawable == null) {
                drawable.bounds = bounds
                drawable.alpha = alpha
                drawable.colorFilter = colorFilter
                drawable.draw(canvas)
                return
            }

            layerDrawable.bounds = bounds
            val calendar = Calendar.getInstance()
            for (index in 0 until layerDrawable.numberOfLayers) {
                val child = layerDrawable.getDrawable(index) ?: continue
                val rotation = clockLayerRotation(index, calendar)
                child.alpha = alpha
                child.colorFilter = colorFilter
                val childBounds = child.bounds.takeUnless { it.isEmpty }
                if (childBounds == null) {
                    child.bounds = bounds
                }
                val save = canvas.save()
                if (rotation != 0f) {
                    canvas.rotate(rotation, bounds.exactCenterX(), bounds.exactCenterY())
                }
                child.draw(canvas)
                canvas.restoreToCount(save)
            }
        }

        private fun clockLayerRotation(index: Int, calendar: Calendar): Float {
            val hour = calendar.get(Calendar.HOUR)
            val minute = calendar.get(Calendar.MINUTE)
            val second = calendar.get(Calendar.SECOND)
            return when (index) {
                config.hourLayerIndex -> ((hour + minute / 60f) * 30f) -
                    ((config.defaultHour % 12 + config.defaultMinute / 60f) * 30f)
                config.minuteLayerIndex -> ((minute + second / 60f) * 6f) -
                    ((config.defaultMinute + config.defaultSecond / 60f) * 6f)
                config.secondLayerIndex -> (second * 6f) - (config.defaultSecond * 6f)
                else -> 0f
            }
        }

        override fun setAlpha(alpha: Int) {
            this.alpha = alpha.coerceIn(0, 255)
            invalidateSelf()
        }

        override fun getAlpha(): Int = alpha

        override fun setColorFilter(colorFilter: ColorFilter?) {
            this.colorFilter = colorFilter
            invalidateSelf()
        }

        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

        override fun getIntrinsicWidth(): Int = drawable.intrinsicWidth

        override fun getIntrinsicHeight(): Int = drawable.intrinsicHeight
    }
}
