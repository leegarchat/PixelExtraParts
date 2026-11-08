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

data class IconDashboardState(
    val iconPacks: List<IconPackInfo>,
    val installedApps: List<InstalledIconApp>,
    val iconMap: IconMapSnapshot
)

data class IconPackInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val versionCode: Long,
    val supportedIconCount: Int,
    val supportedPackageCount: Int,
    val appliedPackageCount: Int,
    val status: IconPackApplyStatus,
    val requiresUpdate: Boolean,
    val installed: Boolean = true
)

enum class IconPackApplyStatus {
    NOT_APPLIED,
    APPLIED_PARTIAL,
    APPLIED_FULL
}

data class IconPackEntry(
    val iconPackPackage: String,
    val appPackageName: String,
    val componentName: String?,
    val drawableName: String,
    val label: String,
    val installed: Boolean,
    val isSystem: Boolean,
    val dynamicIcon: DynamicIconInfo? = null
)

data class DynamicIconInfo(
    val type: String,
    val clockConfig: DynamicClockConfig? = null,
    val calendarPrefix: String? = null,
    val calendarFallbackDrawable: String? = null
)

data class DynamicClockConfig(
    val hourLayerIndex: Int = -1,
    val minuteLayerIndex: Int = -1,
    val secondLayerIndex: Int = -1,
    val defaultHour: Int = 0,
    val defaultMinute: Int = 0,
    val defaultSecond: Int = 0
)

data class IconPackPreviewItem(
    val iconPackPackage: String,
    val drawableName: String,
    val label: String,
    val appPackageName: String?
)

data class AppIconCandidate(
    val iconPackPackage: String,
    val iconPackLabel: String,
    val drawableName: String
)

data class InstalledIconApp(
    val label: String,
    val packageName: String,
    val icon: Drawable?,
    val isSystem: Boolean,
    val isLauncherVisible: Boolean,
    val appliedIconPackLabel: String?,
    val appliedIconFileName: String?,
    val shapeOverrides: AppIconShapeOverrides?
)

data class IconApplyResult(
    val requested: Int,
    val applied: Int,
    val failed: Int,
    val removed: Int = 0,
    val skipped: Int = 0
)

enum class IconApplyMode {
    ALL_PACK,
    SYSTEM_ONLY,
    USER_ONLY,
    INSTALLED_ONLY
}

data class IconApplyProgress(
    val taskId: String,
    val label: String,
    val total: Int,
    val processed: Int,
    val applied: Int,
    val failed: Int,
    val removed: Int,
    val skipped: Int,
    val completed: Boolean,
    val result: IconApplyResult? = null
) {
    val percent: Int
        get() = if (total <= 0) 0 else ((processed * 100) / total).coerceIn(0, 100)
}

data class IconMapSnapshot(
    val icons: Map<String, String>,
    val sources: Map<String, IconSource>,
    val packs: Map<String, IconPackVersion>,
    val shapeOverrides: Map<String, AppIconShapeOverrides> = emptyMap()
) {
    companion object {
        val EMPTY = IconMapSnapshot(emptyMap(), emptyMap(), emptyMap(), emptyMap())
    }
}

enum class AppIconShapeArea(val jsonKey: String) {
    SYSTEM("system"),
    NOTIFICATION("notification"),
    LAUNCHER("launcher")
}

data class IconShapeAreaConfig(
    val stretchShape: Boolean = false,
    val removeShape: Boolean = false,
    val scalePercent: Float = IconPackManager.DEFAULT_SHAPE_SCALE_PERCENT
) {
    fun normalized(): IconShapeAreaConfig {
        val remove = removeShape
        return copy(
            stretchShape = stretchShape && !remove,
            scalePercent = scalePercent.coerceIn(
                IconPackManager.MIN_SHAPE_SCALE_PERCENT,
                IconPackManager.MAX_SHAPE_SCALE_PERCENT
            )
        )
    }
}

data class AppIconShapeOverrides(
    val system: IconShapeAreaConfig? = null,
    val notification: IconShapeAreaConfig? = null,
    val launcher: IconShapeAreaConfig? = null
) {
    fun area(area: AppIconShapeArea): IconShapeAreaConfig? = when (area) {
        AppIconShapeArea.SYSTEM -> system
        AppIconShapeArea.NOTIFICATION -> notification
        AppIconShapeArea.LAUNCHER -> launcher
    }

    fun isEmpty(): Boolean = system == null && notification == null && launcher == null

    fun normalizedOrNull(): AppIconShapeOverrides? {
        val normalized = copy(
            system = system?.normalized(),
            notification = notification?.normalized(),
            launcher = launcher?.normalized()
        )
        return normalized.takeUnless { it.isEmpty() }
    }
}

data class IconSource(
    val iconPackPackage: String,
    val iconPackLabel: String,
    val drawableName: String,
    val iconPackVersionCode: Long,
    val updatedAt: Long,
    val dynamicIcon: DynamicIconInfo? = null
)

data class IconPackVersion(
    val packageName: String,
    val label: String,
    val versionCode: Long,
    val appliedAt: Long
)

object IconPackManager {
    const val RELOAD_ACTION = "com.pixelparts.intent.action.RELOAD_ICONS"
    const val MIN_SHAPE_SCALE_PERCENT = 0f
    const val MAX_SHAPE_SCALE_PERCENT = 200f
    const val DEFAULT_SHAPE_SCALE_PERCENT = 72f
    private const val DYNAMIC_ICON_CALENDAR = "calendar"
    private const val DYNAMIC_ICON_CLOCK = "clock"

    private const val TAG = "IconPackManager"
    private const val ICON_DP = 48
    private const val CUSTOM_ICON_SOURCE_ID = "custom"
    private val iconRoot = File("/data/pixelparts/IconsManager")
    private val iconMapFile = File(iconRoot, "icon_map.json")
    private val ioLock = Any()
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    @Volatile private var activeProgress: IconApplyProgress? = null
    @Volatile private var dashboardCache: IconDashboardState? = null

    private val themeActions = listOf(
        "org.adw.launcher.THEMES",
        "com.teslacoilsw.launcher.THEME",
        "com.novalauncher.THEME"
    )

    private val densities = listOf(
        IconDensity("mdpi", 160),
        IconDensity("hdpi", 240),
        IconDensity("xhdpi", 320),
        IconDensity("xxhdpi", 480),
        IconDensity("xxxhdpi", 640)
    )

    fun getActiveProgress(): IconApplyProgress? = activeProgress

    fun getCachedDashboardState(): IconDashboardState? = dashboardCache

    fun clearDashboardCache() {
        dashboardCache = null
    }

    fun clearCompletedProgress() {
        if (activeProgress?.completed == true) {
            activeProgress = null
        }
    }

    fun requestIconReload(context: Context) {
        sendReloadBroadcast(context.applicationContext ?: context)
    }

    fun startApplyAll(context: Context, iconPackPackage: String, mode: IconApplyMode): Boolean {
        val appContext = context.applicationContext ?: context
        return startBackgroundOperation(appContext.getString(R.string.app_icons_progress_applying_pack)) { taskId ->
            val packageManager = appContext.packageManager
            val installedIndex = loadInstalledApplicationIndex(packageManager)
            val entries = filterEntriesForMode(
                parseIconPackEntries(appContext, iconPackPackage, installedIndex),
                mode
            )
            applyIconSelection(
                context = appContext,
                iconPackPackage = iconPackPackage,
                allEntries = entries,
                selectedPackages = entries.map { it.appPackageName }.toSet(),
                clearUnselected = false,
                progressTaskId = taskId
            )
        }
    }

    fun startApplyPartial(context: Context, iconPackPackage: String, selectedPackages: Set<String>): Boolean {
        val appContext = context.applicationContext ?: context
        return startBackgroundOperation(appContext.getString(R.string.app_icons_progress_applying_selected)) { taskId ->
            val packageManager = appContext.packageManager
            val installedIndex = loadInstalledApplicationIndex(packageManager)
            val packInstalled = runCatching { packageManager.getPackageInfo(iconPackPackage, 0) }.getOrNull() != null
            if (!packInstalled) {
                return@startBackgroundOperation reconcileStoredPackSelection(
                    appContext,
                    iconPackPackage,
                    selectedPackages,
                    taskId
                )
            }
            val entries = loadIconPackSelectionEntries(appContext, iconPackPackage, installedIndex, readIconMap())
            applyIconSelection(
                context = appContext,
                iconPackPackage = iconPackPackage,
                allEntries = entries,
                selectedPackages = selectedPackages,
                clearUnselected = true,
                progressTaskId = taskId
            )
        }
    }

    fun startApplySingle(
        context: Context,
        iconPackPackage: String,
        packageName: String,
        drawableName: String? = null
    ): Boolean {
        val appContext = context.applicationContext ?: context
        return startBackgroundOperation(appContext.getString(R.string.app_icons_progress_applying_app)) { taskId ->
            val packageManager = appContext.packageManager
            val installedIndex = loadInstalledApplicationIndex(packageManager)
            val entries = singleSelectionEntries(
                appContext,
                iconPackPackage,
                packageName,
                drawableName,
                installedIndex
            )
            applyIconSelection(
                context = appContext,
                iconPackPackage = iconPackPackage,
                allEntries = entries,
                selectedPackages = setOf(packageName),
                clearUnselected = false,
                progressTaskId = taskId
            )
        }
    }

    fun startImportPackageIcon(context: Context, packageName: String, uri: Uri): Boolean {
        val appContext = context.applicationContext ?: context
        runCatching {
            appContext.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return startBackgroundOperation(appContext.getString(R.string.app_icons_progress_importing_icon)) { taskId ->
            importPackageIcon(appContext, packageName, uri, taskId)
        }
    }

    fun startRemovePackageIcon(context: Context, packageName: String): Boolean {
        val appContext = context.applicationContext ?: context
        return startBackgroundOperation(appContext.getString(R.string.app_icons_progress_restoring_icon)) { taskId ->
            removePackageBindings(appContext, setOf(packageName), taskId)
        }
    }

    fun startRemoveIconPack(context: Context, iconPackPackage: String): Boolean {
        val appContext = context.applicationContext ?: context
        return startBackgroundOperation(appContext.getString(R.string.app_icons_progress_removing_pack)) { taskId ->
            val packages = readIconMap().sources
                .filterValues { it.iconPackPackage == iconPackPackage }
                .keys
                .toSet()
            removePackageBindings(appContext, packages, taskId)
        }
    }

    fun startClearAllIcons(context: Context): Boolean {
        val appContext = context.applicationContext ?: context
        return startBackgroundOperation(appContext.getString(R.string.app_icons_progress_clearing_all)) { taskId ->
            clearAllIconData(appContext, taskId)
        }
    }

    suspend fun loadDashboardState(context: Context, forceRefresh: Boolean = false): IconDashboardState = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            dashboardCache?.let { return@withContext it }
        }
        val appContext = context.applicationContext ?: context
        val packageManager = appContext.packageManager
        val iconMap = readIconMap()
        val installedIndex = loadInstalledApplicationIndex(packageManager)
        val iconPacks = loadIconPacks(appContext, packageManager, installedIndex, iconMap)
        val packLabels = iconPacks.associate { it.packageName to it.label }
        val installedApps = buildInstalledApps(installedIndex, iconMap, packLabels)
        IconDashboardState(iconPacks, installedApps, iconMap).also { dashboardCache = it }
    }

    suspend fun loadIconPackEntries(context: Context, iconPackPackage: String): List<IconPackEntry> =
        withContext(Dispatchers.IO) {
            val packageManager = context.packageManager
            parseIconPackEntries(context, iconPackPackage, loadInstalledApplicationIndex(packageManager))
        }

    suspend fun loadIconPackSelectionEntries(context: Context, iconPackPackage: String): List<IconPackEntry> =
        withContext(Dispatchers.IO) {
            val packageManager = context.packageManager
            val installedIndex = loadInstalledApplicationIndex(packageManager)
            loadIconPackSelectionEntries(context, iconPackPackage, installedIndex, readIconMap())
        }

    suspend fun loadPreviewItems(context: Context, iconPackPackage: String): List<IconPackPreviewItem> =
        withContext(Dispatchers.IO) {
            val packageManager = context.packageManager
            val installedIndex = loadInstalledApplicationIndex(packageManager)
            val parsedEntries = parseIconPackEntries(context, iconPackPackage, installedIndex)
            val packageInfo = runCatching { packageManager.getPackageInfo(iconPackPackage, 0) }.getOrNull()
            val items = linkedMapOf<String, IconPackPreviewItem>()

            parsedEntries.forEach { entry ->
                items.putIfAbsent(
                    entry.drawableName,
                    IconPackPreviewItem(
                        iconPackPackage = iconPackPackage,
                        drawableName = entry.drawableName,
                        label = entry.label,
                        appPackageName = entry.appPackageName
                    )
                )
            }

            packageInfo?.applicationInfo?.sourceDir
                ?.let { findDrawableResourceNames(it) }
                ?.forEach { drawableName ->
                    items.putIfAbsent(
                        drawableName,
                        IconPackPreviewItem(
                            iconPackPackage = iconPackPackage,
                            drawableName = drawableName,
                            label = drawableName,
                            appPackageName = null
                        )
                    )
                }

            items.values.sortedWith(
                compareBy<IconPackPreviewItem> { it.label.lowercase(Locale.getDefault()) }
                    .thenBy { it.drawableName }
            )
        }

    suspend fun loadCandidatesForApp(context: Context, packageName: String): List<AppIconCandidate> =
        withContext(Dispatchers.IO) {
            val packageManager = context.packageManager
            val installedIndex = loadInstalledApplicationIndex(packageManager)
            discoverIconPackPackages(packageManager).flatMap { iconPackPackage ->
                val packageInfo = runCatching { packageManager.getPackageInfo(iconPackPackage, 0) }.getOrNull()
                val label = packageInfo?.applicationInfo?.loadLabel(packageManager)?.toString()
                    ?.takeIf { it.isNotBlank() } ?: iconPackPackage
                parseIconPackEntries(context, iconPackPackage, installedIndex)
                    .filter { it.appPackageName == packageName }
                    .distinctBy { it.drawableName }
                    .map {
                        AppIconCandidate(
                            iconPackPackage = iconPackPackage,
                            iconPackLabel = label,
                            drawableName = it.drawableName
                        )
                    }
            }.sortedWith(
                compareBy<AppIconCandidate> { it.iconPackLabel.lowercase(Locale.getDefault()) }
                    .thenBy { it.drawableName }
            )
        }

    suspend fun applyAll(
        context: Context,
        iconPackPackage: String,
        mode: IconApplyMode = IconApplyMode.INSTALLED_ONLY
    ): IconApplyResult = withContext(Dispatchers.IO) {
        val packageManager = context.packageManager
        val installedIndex = loadInstalledApplicationIndex(packageManager)
        val entries = filterEntriesForMode(parseIconPackEntries(context, iconPackPackage, installedIndex), mode)
        applyIconSelection(
            context = context,
            iconPackPackage = iconPackPackage,
            allEntries = entries,
            selectedPackages = entries.map { it.appPackageName }.toSet(),
            clearUnselected = false,
            progressTaskId = null
        )
    }

    suspend fun applyPartial(
        context: Context,
        iconPackPackage: String,
        selectedPackages: Set<String>
    ): IconApplyResult = withContext(Dispatchers.IO) {
        val packageManager = context.packageManager
        val installedIndex = loadInstalledApplicationIndex(packageManager)
        val entries = parseIconPackEntries(context, iconPackPackage, installedIndex)
        applyIconSelection(
            context = context,
            iconPackPackage = iconPackPackage,
            allEntries = entries,
            selectedPackages = selectedPackages,
            clearUnselected = true,
            progressTaskId = null
        )
    }

    suspend fun applySingle(
        context: Context,
        iconPackPackage: String,
        packageName: String,
        drawableName: String? = null
    ): IconApplyResult =
        withContext(Dispatchers.IO) {
            val packageManager = context.packageManager
            val installedIndex = loadInstalledApplicationIndex(packageManager)
            val entries = singleSelectionEntries(
                context,
                iconPackPackage,
                packageName,
                drawableName,
                installedIndex
            )
            applyIconSelection(
                context = context,
                iconPackPackage = iconPackPackage,
                allEntries = entries,
                selectedPackages = setOf(packageName),
                clearUnselected = false,
                progressTaskId = null
            )
        }

    suspend fun importPackageIcon(context: Context, packageName: String, uri: Uri): IconApplyResult =
        withContext(Dispatchers.IO) { importPackageIcon(context, packageName, uri, null) }

    fun savePackageShapeOverrides(
        context: Context,
        packageName: String,
        overrides: AppIconShapeOverrides?
    ) {
        synchronized(ioLock) {
            val current = readIconMap()
            val shapeOverrides = current.shapeOverrides.toMutableMap()
            val normalized = overrides?.normalizedOrNull()
            if (normalized == null) {
                shapeOverrides.remove(packageName)
            } else {
                shapeOverrides[packageName] = normalized
            }
            writeIconMap(current.copy(shapeOverrides = shapeOverrides.toSortedMap()))
        }
        sendReloadBroadcast(context.applicationContext ?: context)
    }

    suspend fun removePackageIcon(context: Context, packageName: String): IconApplyResult =
        withContext(Dispatchers.IO) { removePackageBindings(context, setOf(packageName), null) }

    suspend fun removeIconPack(context: Context, iconPackPackage: String): IconApplyResult =
        withContext(Dispatchers.IO) {
            val packages = readIconMap().sources
                .filterValues { it.iconPackPackage == iconPackPackage }
                .keys
                .toSet()
            removePackageBindings(context, packages, null)
        }

    suspend fun loadIconBitmap(
        context: Context,
        iconPackPackage: String,
        drawableName: String,
        sizePx: Int
    ): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val packContext = createIconPackContext(context, iconPackPackage)
            loadIconPackDrawable(packContext, drawableName)?.toNormalizedIconBitmap(sizePx)
        }.getOrNull()
    }

    suspend fun loadAppliedIconBitmap(context: Context, packageName: String, sizePx: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            val fileName = readIconMap().icons[packageName] ?: return@withContext null
            val iconFile = findIconFile(fileName, context.resources.displayMetrics.densityDpi)
                ?: return@withContext null
            BitmapFactory.decodeFile(iconFile.absolutePath)?.let { bitmap ->
                if (bitmap.width == sizePx && bitmap.height == sizePx) {
                    bitmap
                } else {
                    Bitmap.createScaledBitmap(bitmap, sizePx.coerceAtLeast(1), sizePx.coerceAtLeast(1), true)
                }
            }
        }

    private fun loadIconPacks(
        context: Context,
        packageManager: PackageManager,
        installedIndex: Map<String, InstalledAppRecord>,
        iconMap: IconMapSnapshot
    ): List<IconPackInfo> {
        val discoveredPackages = discoverIconPackPackages(packageManager)
        val appliedPackPackages = iconMap.sources.values
            .map { it.iconPackPackage }
            .filterNot { it == CUSTOM_ICON_SOURCE_ID }
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

    private fun loadIconPackSelectionEntries(
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

    private fun buildInstalledApps(
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

    private fun discoverIconPackPackages(packageManager: PackageManager): List<String> {
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

    private fun parseIconPackEntries(
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
                type = DYNAMIC_ICON_CALENDAR,
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
            DynamicIconInfo(DYNAMIC_ICON_CLOCK, clockConfig)
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

    private fun singleSelectionEntries(
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

    private fun applyIconSelection(
        context: Context,
        iconPackPackage: String,
        allEntries: List<IconPackEntry>,
        selectedPackages: Set<String>,
        clearUnselected: Boolean,
        progressTaskId: String?
    ): IconApplyResult {
        if (allEntries.isEmpty() && selectedPackages.isEmpty()) {
            return IconApplyResult(0, 0, 0)
        }

        val packageManager = context.packageManager
        val packageInfo = runCatching { packageManager.getPackageInfo(iconPackPackage, 0) }.getOrNull()
            ?: return IconApplyResult(selectedPackages.size, 0, selectedPackages.size)
        val appInfo = packageInfo.applicationInfo
            ?: return IconApplyResult(selectedPackages.size, 0, selectedPackages.size)
        val packLabel = appInfo.loadLabel(packageManager)?.toString()?.takeIf { it.isNotBlank() }
            ?: iconPackPackage
        val versionCode = packageInfo.versionCodeLong()
        val selectedEntries = allEntries
            .filter { it.appPackageName in selectedPackages }
            .distinctBy { it.appPackageName }

        return synchronized(ioLock) {
            val current = readIconMap()
            val icons = current.icons.toMutableMap()
            val sources = current.sources.toMutableMap()
            val packs = current.packs.toMutableMap()
            val shapeOverrides = current.shapeOverrides.toMutableMap()
            var changed = false
            val removals = if (clearUnselected) {
                val allTargetPackages = allEntries.map { it.appPackageName }.toSet()
                allTargetPackages.filterNot { it in selectedPackages }
                    .filter { sources[it]?.iconPackPackage == iconPackPackage }
            } else {
                emptyList()
            }
            val totalWork = removals.size + selectedEntries.size
            var processed = 0
            var removed = 0
            var skipped = 0
            var applied = 0
            var failed = 0

            updateProgress(progressTaskId, totalWork, processed, applied, failed, removed, skipped)

            removals.forEach { packageName ->
                val fileName = icons.remove(packageName)
                sources.remove(packageName)
                shapeOverrides.remove(packageName)
                if (fileName != null) {
                    deleteIconFiles(fileName)
                }
                changed = true
                removed++
                processed++
                updateProgress(progressTaskId, totalWork, processed, applied, failed, removed, skipped)
            }

            ensureStorageDirs()

            selectedEntries.forEach { entry ->
                val fileName = fileNameForPackage(entry.appPackageName)
                val currentSource = sources[entry.appPackageName]
                val alreadyCurrent = icons[entry.appPackageName] == fileName &&
                    currentSource?.iconPackPackage == iconPackPackage &&
                    currentSource.drawableName == entry.drawableName &&
                    currentSource.dynamicIcon == entry.dynamicIcon &&
                    hasExportedIcon(fileName)
                var entryApplied = false
                if (alreadyCurrent) {
                    skipped++
                } else {
                    val exported = runCatching {
                        exportIconForAllDensities(context, iconPackPackage, entry, fileName)
                    }.getOrDefault(false)
                    if (exported) {
                        applied++
                        entryApplied = true
                    } else {
                        failed++
                    }
                }

                if (alreadyCurrent || entryApplied) {
                    icons[entry.appPackageName] = fileName
                    sources[entry.appPackageName] = IconSource(
                        iconPackPackage = iconPackPackage,
                        iconPackLabel = packLabel,
                        drawableName = entry.drawableName,
                        iconPackVersionCode = versionCode,
                        updatedAt = System.currentTimeMillis(),
                        dynamicIcon = entry.dynamicIcon
                    )
                    if (!alreadyCurrent) changed = true
                }
                processed++
                updateProgress(progressTaskId, totalWork, processed, applied, failed, removed, skipped)
            }

            if (applied > 0 || changed) {
                packs[iconPackPackage] = IconPackVersion(
                    packageName = iconPackPackage,
                    label = packLabel,
                    versionCode = versionCode,
                    appliedAt = System.currentTimeMillis()
                )
                writeIconMap(IconMapSnapshot(
                    icons.toSortedMap(),
                    sources.toSortedMap(),
                    packs.toSortedMap(),
                    shapeOverrides.toSortedMap()
                ))
                sendReloadBroadcast(context)
            }

            IconApplyResult(
                requested = selectedEntries.size,
                applied = applied,
                failed = failed,
                removed = removed,
                skipped = skipped
            )
        }
    }

    private fun removePackageBindings(
        context: Context,
        packageNames: Set<String>,
        progressTaskId: String?
    ): IconApplyResult {
        return synchronized(ioLock) {
            val current = readIconMap()
            val icons = current.icons.toMutableMap()
            val sources = current.sources.toMutableMap()
            val packs = current.packs.toMutableMap()
            val shapeOverrides = current.shapeOverrides.toMutableMap()
            val targets = packageNames.filter {
                icons.containsKey(it) || sources.containsKey(it) || shapeOverrides.containsKey(it)
            }
            var processed = 0
            var removed = 0
            updateProgress(progressTaskId, targets.size, processed, 0, 0, removed, 0)

            targets.forEach { packageName ->
                val fileName = icons.remove(packageName)
                sources.remove(packageName)
                shapeOverrides.remove(packageName)
                if (fileName != null) {
                    deleteIconFiles(fileName)
                }
                removed++
                processed++
                updateProgress(progressTaskId, targets.size, processed, 0, 0, removed, 0)
            }

            val activePacks = sources.values.map { it.iconPackPackage }.toSet()
            packs.keys.filterNot { it in activePacks }.forEach { packs.remove(it) }

            if (removed > 0) {
                writeIconMap(IconMapSnapshot(
                    icons.toSortedMap(),
                    sources.toSortedMap(),
                    packs.toSortedMap(),
                    shapeOverrides.toSortedMap()
                ))
                sendReloadBroadcast(context)
            }

            IconApplyResult(
                requested = packageNames.size,
                applied = 0,
                failed = 0,
                removed = removed,
                skipped = 0
            )
        }
    }

    private fun reconcileStoredPackSelection(
        context: Context,
        iconPackPackage: String,
        selectedPackages: Set<String>,
        progressTaskId: String?
    ): IconApplyResult {
        val currentPackages = readIconMap().sources
            .filterValues { it.iconPackPackage == iconPackPackage }
            .keys
            .toSet()
        val removals = currentPackages - selectedPackages
        val result = removePackageBindings(context, removals, progressTaskId)
        return result.copy(requested = currentPackages.size, skipped = currentPackages.size - removals.size)
    }

    private fun clearAllIconData(context: Context, progressTaskId: String?): IconApplyResult {
        return synchronized(ioLock) {
            val current = readIconMap()
            val packageNames = (current.icons.keys + current.sources.keys + current.shapeOverrides.keys).toSet()
            var processed = 0
            var removed = 0
            updateProgress(progressTaskId, packageNames.size, processed, 0, 0, removed, 0)

            packageNames.forEach { _ ->
                removed++
                processed++
                updateProgress(progressTaskId, packageNames.size, processed, 0, 0, removed, 0)
            }

            densities.forEach { density ->
                File(iconRoot, density.folder).listFiles()?.forEach { file ->
                    if (file.isFile) file.delete()
                }
            }
            if (iconMapFile.exists()) {
                iconMapFile.delete()
            }
            writeIconMap(IconMapSnapshot.EMPTY)
            sendReloadBroadcast(context)

            IconApplyResult(
                requested = packageNames.size,
                applied = 0,
                failed = 0,
                removed = removed,
                skipped = 0
            )
        }
    }

    private fun filterEntriesForMode(entries: List<IconPackEntry>, mode: IconApplyMode): List<IconPackEntry> {
        return when (mode) {
            IconApplyMode.ALL_PACK -> entries
            IconApplyMode.SYSTEM_ONLY -> entries.filter { it.installed && it.isSystem }
            IconApplyMode.USER_ONLY -> entries.filter { it.installed && !it.isSystem }
            IconApplyMode.INSTALLED_ONLY -> entries.filter { it.installed }
        }
    }

    private fun startBackgroundOperation(
        initialLabel: String,
        block: (String) -> IconApplyResult
    ): Boolean {
        val taskId = "icon_task_" + System.currentTimeMillis()
        synchronized(ioLock) {
            if (activeProgress?.completed == false) return false
            activeProgress = IconApplyProgress(
                taskId = taskId,
                label = initialLabel,
                total = 0,
                processed = 0,
                applied = 0,
                failed = 0,
                removed = 0,
                skipped = 0,
                completed = false
            )
        }
        managerScope.launch {
            val result = runCatching { block(taskId) }
                .getOrElse { IconApplyResult(0, 0, 1) }
            completeProgress(taskId, result)
        }
        return true
    }

    private fun updateProgress(
        taskId: String?,
        total: Int,
        processed: Int,
        applied: Int,
        failed: Int,
        removed: Int,
        skipped: Int
    ) {
        if (taskId == null) return
        val current = activeProgress ?: return
        if (current.taskId != taskId) return
        activeProgress = current.copy(
            total = total,
            processed = processed,
            applied = applied,
            failed = failed,
            removed = removed,
            skipped = skipped
        )
    }

    private fun completeProgress(taskId: String, result: IconApplyResult) {
        val current = activeProgress ?: return
        if (current.taskId != taskId) return
        clearDashboardCache()
        val total = current.total.coerceAtLeast(result.requested + result.removed)
        activeProgress = current.copy(
            total = total,
            processed = total,
            applied = result.applied,
            failed = result.failed,
            removed = result.removed,
            skipped = result.skipped,
            completed = true,
            result = result
        )
    }

    private fun exportIconForAllDensities(
        context: Context,
        iconPackPackage: String,
        entry: IconPackEntry,
        fileName: String
    ): Boolean {
        val packContext = createIconPackContext(context, iconPackPackage)
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

    private fun importPackageIcon(
        context: Context,
        packageName: String,
        uri: Uri,
        progressTaskId: String?
    ): IconApplyResult {
        return synchronized(ioLock) {
            updateProgress(progressTaskId, 1, 0, 0, 0, 0, 0)
            val bitmap = decodeBitmapFromUri(context, uri)
            if (bitmap == null) {
                updateProgress(progressTaskId, 1, 1, 0, 1, 0, 0)
                return@synchronized IconApplyResult(requested = 1, applied = 0, failed = 1)
            }

            ensureStorageDirs()
            val current = readIconMap()
            val icons = current.icons.toMutableMap()
            val sources = current.sources.toMutableMap()
            val packs = current.packs.toMutableMap()
            val fileName = fileNameForPackage(packageName)
            val exported = exportBitmapForAllDensities(bitmap, fileName)
            if (!exported) {
                updateProgress(progressTaskId, 1, 1, 0, 1, 0, 0)
                return@synchronized IconApplyResult(requested = 1, applied = 0, failed = 1)
            }

            val now = System.currentTimeMillis()
            icons[packageName] = fileName
            sources[packageName] = IconSource(
                iconPackPackage = CUSTOM_ICON_SOURCE_ID,
                iconPackLabel = context.getString(R.string.app_icons_custom_icon_source),
                drawableName = getDisplayName(context, uri) ?: uri.lastPathSegment.orEmpty().ifBlank { "custom" },
                iconPackVersionCode = 0L,
                updatedAt = now
            )
            packs[CUSTOM_ICON_SOURCE_ID] = IconPackVersion(
                packageName = CUSTOM_ICON_SOURCE_ID,
                label = context.getString(R.string.app_icons_custom_icon_source),
                versionCode = 0L,
                appliedAt = now
            )
            writeIconMap(current.copy(
                icons = icons.toSortedMap(),
                sources = sources.toSortedMap(),
                packs = packs.toSortedMap()
            ))
            sendReloadBroadcast(context)
            updateProgress(progressTaskId, 1, 1, 1, 0, 0, 0)
            IconApplyResult(requested = 1, applied = 1, failed = 0)
        }
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
            if (scaled !== bitmap) {
                scaled.recycle()
            }
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
            null
        )
        if (scaled !== this) {
            scaled.recycle()
        }
        return output
    }

    private fun Drawable.toNormalizedIconBitmap(targetPx: Int): Bitmap {
        val raw = toBitmap(
            width = targetPx.coerceAtLeast(1),
            height = targetPx.coerceAtLeast(1),
            config = Bitmap.Config.ARGB_8888
        )
        val normalized = raw.normalizeVisibleIconArea(targetPx.coerceAtLeast(1))
        if (normalized !== raw) raw.recycle()
        return normalized
    }

    private fun Bitmap.normalizeVisibleIconArea(targetPx: Int): Bitmap {
        val bounds = visibleAlphaBounds() ?: return this
        val visibleWidth = bounds.width().coerceAtLeast(1)
        val visibleHeight = bounds.height().coerceAtLeast(1)
        val minExpected = targetPx * 0.70f
        if (visibleWidth >= minExpected || visibleHeight >= minExpected) {
            return this
        }

        val output = Bitmap.createBitmap(targetPx, targetPx, Bitmap.Config.ARGB_8888)
        val targetVisiblePx = (targetPx * 0.88f).roundToInt().coerceIn(1, targetPx)
        val scale = minOf(
            targetVisiblePx / visibleWidth.toFloat(),
            targetVisiblePx / visibleHeight.toFloat()
        )
        val scaledWidth = (visibleWidth * scale).roundToInt().coerceAtLeast(1)
        val scaledHeight = (visibleHeight * scale).roundToInt().coerceAtLeast(1)
        val dst = RectF(
            ((targetPx - scaledWidth) / 2f),
            ((targetPx - scaledHeight) / 2f),
            ((targetPx + scaledWidth) / 2f),
            ((targetPx + scaledHeight) / 2f)
        )
        Canvas(output).drawBitmap(this, bounds, dst, iconPaint)
        return output
    }

    private fun Bitmap.visibleAlphaBounds(): Rect? {
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

    private fun getDisplayName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun hasExportedIcon(fileName: String): Boolean {
        return densities.any { density -> File(File(iconRoot, density.folder), fileName).isFile }
    }

    private fun deleteIconFiles(fileName: String) {
        normalizeFileName(fileName)?.let { safeName ->
            densities.forEach { density ->
                File(File(iconRoot, density.folder), safeName).delete()
            }
        }
    }

    private fun loadIconPackDrawable(
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
        return if (dynamicIcon?.type == DYNAMIC_ICON_CLOCK && dynamicIcon.clockConfig != null) {
            DynamicClockDrawable(drawable, dynamicIcon.clockConfig)
        } else {
            drawable
        }
    }

    private fun loadInstalledApplicationIndex(packageManager: PackageManager): Map<String, InstalledAppRecord> {
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

    private fun loadOriginalApplicationIcon(
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

    private fun loadLauncherVisiblePackages(packageManager: PackageManager): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return runCatching { packageManager.queryIntentActivities(intent, 0) }
            .getOrDefault(emptyList())
            .mapNotNullTo(linkedSetOf()) { it.activityInfo?.packageName ?: it.resolvePackageName }
    }

    private fun readIconMap(): IconMapSnapshot {
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

    private fun writeIconMap(snapshot: IconMapSnapshot) {
        clearDashboardCache()
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

    private fun readShapeAreaConfig(areaObject: JSONObject?): IconShapeAreaConfig? {
        if (areaObject == null) return null
        return IconShapeAreaConfig(
            stretchShape = areaObject.optBoolean("stretchShape", false),
            removeShape = areaObject.optBoolean("removeShape", false),
            scalePercent = areaObject.optDouble("scalePercent", DEFAULT_SHAPE_SCALE_PERCENT.toDouble()).toFloat()
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
                    defaultSecond = it.optInt("defaultSecond", 0)
                )
            },
            calendarPrefix = dynamicObject?.optString("calendarPrefix")?.takeIf { it.isNotBlank() }
                ?: sourceObject.optString("calendarPrefix").takeIf { it.isNotBlank() },
            calendarFallbackDrawable = dynamicObject?.optString("calendarFallbackDrawable")?.takeIf { it.isNotBlank() }
                ?: sourceObject.optString("calendarFallbackDrawable").takeIf { it.isNotBlank() }
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

    private fun ensureStorageDirs() {
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

    private fun findIconFile(fileName: String, densityDpi: Int): File? {
        val currentFolder = densityFolderForDpi(densityDpi)
        val current = File(File(iconRoot, currentFolder), fileName)
        if (current.isFile) return current
        densities.forEach { density ->
            val fallback = File(File(iconRoot, density.folder), fileName)
            if (fallback.isFile) return fallback
        }
        return null
    }

    private fun densityFolderForDpi(densityDpi: Int): String = when {
        densityDpi <= 160 -> "mdpi"
        densityDpi <= 240 -> "hdpi"
        densityDpi <= 320 -> "xhdpi"
        densityDpi <= 480 -> "xxhdpi"
        else -> "xxxhdpi"
    }

    private fun sendReloadBroadcast(context: Context) {
        context.sendBroadcast(
            Intent(RELOAD_ACTION).addFlags(Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND)
        )
    }

    private fun createIconPackContext(context: Context, iconPackPackage: String): Context {
        return context.createPackageContext(iconPackPackage, Context.CONTEXT_IGNORE_SECURITY)
    }

    private fun fileNameForPackage(packageName: String): String {
        return packageName.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".png"
    }

    private fun normalizeFileName(value: String): String? {
        val fileName = value.trim()
        if (fileName.isBlank() || fileName.contains('/') || fileName.contains('\\') || fileName.contains("..")) {
            return null
        }
        return if (fileName.endsWith(".png")) fileName else "$fileName.png"
    }

    private fun PackageInfo.versionCodeLong(): Long {
        @Suppress("DEPRECATION")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else versionCode.toLong()
    }

    private fun ApplicationInfo.isSystemApplication(): Boolean {
        return (flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
            (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    }

    private data class IconDensity(val folder: String, val dpi: Int)

    private data class InstalledAppRecord(
        val label: String,
        val packageName: String,
        val icon: Drawable?,
        val isSystem: Boolean,
        val isLauncherVisible: Boolean
    )

    private class DynamicClockDrawable(
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
}