package org.pixel.customparts.icons

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
import org.pixel.customparts.R
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Calendar
import java.util.Locale
import java.util.zip.ZipFile
import kotlin.math.roundToInt

internal const val TAG = "IconPackManager"

private val themeActions = listOf(
    "org.adw.launcher.THEMES",
    "com.teslacoilsw.launcher.THEME",
    "com.novalauncher.THEME"
)

internal fun loadIconPacks(
    context: Context,
    packageManager: PackageManager,
    installedIndex: Map<String, InstalledAppRecord>,
    iconMap: IconMapSnapshot
): List<IconPackInfo> {
    val discoveredPackages = discoverIconPackPackages(packageManager)
    val appliedPackPackages = iconMap.sources.values
        .map { it.iconPackPackage }
        .filterNot { it == IconPackManager.CUSTOM_ICON_SOURCE_ID }
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
                installed = false
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
        val versionCode = packageInfo.versionCodeLong()
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
            installed = true
        )
    }.sortedBy { it.label.lowercase(Locale.getDefault()) }
}


internal fun loadIconPackSelectionEntries(
    context: Context,
    iconPackPackage: String,
    installedIndex: Map<String, InstalledAppRecord>,
    iconMap: IconMapSnapshot
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
                dynamicIcon = source.dynamicIcon
            )
        }
    return (parsedEntries + storedEntries).sortedWith(
        compareBy<IconPackEntry> { it.label.lowercase(Locale.getDefault()) }
            .thenBy { it.appPackageName }
            .thenBy { it.drawableName }
    )
}


internal fun buildInstalledApps(
    installedIndex: Map<String, InstalledAppRecord>,
    iconMap: IconMapSnapshot,
    packLabels: Map<String, String>
): List<InstalledIconApp> {
    return installedIndex.values.map { app ->
        val source = iconMap.sources[app.packageName]
        InstalledIconApp(
            label = app.label,
            packageName = app.packageName,
            icon = app.icon,
            isSystem = app.isSystem,
            isLauncherVisible = app.isLauncherVisible,
            appliedIconPackLabel = source?.let { packLabels[it.iconPackPackage] ?: it.iconPackLabel },
            appliedIconFileName = iconMap.icons[app.packageName],
            shapeOverrides = iconMap.shapeOverrides[app.packageName]
        )
    }.sortedWith(
        compareBy<InstalledIconApp> { it.label.lowercase(Locale.getDefault()) }
            .thenBy { it.packageName }
    )
}


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
    val packContext = runCatching { createIconPackContext(context, iconPackPackage) }.getOrNull()
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


private fun parseStaticIconEntry(
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


private fun parseCalendarIconEntry(
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


private fun parseDynamicClockIconEntry(
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


private fun buildIconPackEntry(
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


private fun resolveCalendarDrawableName(
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


private fun replaceTrailingCalendarNumber(value: String, day: String, dayPadded: String): String? {
    val extensionStart = value.lastIndexOf('.').takeIf { it > 0 } ?: value.length
    val numberStart = value.substring(0, extensionStart).indexOfLast { !it.isDigit() } + 1
    if (numberStart >= extensionStart) return null
    val replacement = if (extensionStart - numberStart >= 2) dayPadded else day
    return value.substring(0, numberStart) + replacement + value.substring(extensionStart)
}


private fun drawableResourceExists(context: Context, drawableName: String): Boolean {
    val cleanName = drawableName.substringBeforeLast('.').trim()
    if (cleanName.isBlank()) return false
    val resources = context.resources
    return resources.getIdentifier(cleanName, "drawable", context.packageName) != 0 ||
        resources.getIdentifier(cleanName, "mipmap", context.packageName) != 0
}


private fun XmlPullParser.intAttribute(
    vararg names: String,
    defaultValue: Int
): Int {
    return names.firstNotNullOfOrNull { name ->
        getAttributeValue(null, name)?.trim()?.toIntOrNull()
    } ?: defaultValue
}


private fun openAppFilterParser(context: Context): ParserHolder? {
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


private fun parseComponentPackage(component: String?): String? {
    if (component.isNullOrBlank()) return null
    val body = component.substringAfter("ComponentInfo{", component).substringBefore("}")
    return body.substringBefore("/").takeIf { it.isNotBlank() }
}


internal data class InstalledAppRecord(
    val label: String,
    val packageName: String,
    val icon: Drawable?,
    val isSystem: Boolean,
    val isLauncherVisible: Boolean
)


private class ParserHolder(
    val parser: XmlPullParser,
    private val closeable: AutoCloseable
) : AutoCloseable {
    override fun close() {
        closeable.close()
    }
}


private inline fun ParserHolder.useParser(block: (XmlPullParser) -> Unit) {
    use { holder -> block(holder.parser) }
}
