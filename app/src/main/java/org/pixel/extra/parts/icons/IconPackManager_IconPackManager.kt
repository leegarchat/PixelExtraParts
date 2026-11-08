package org.pixel.extra.parts.icons

import org.pixel.extra.parts.icons.IconPackBitmaps.versionCodeLong
import org.pixel.extra.parts.icons.IconPackBitmaps.createIconPackContext
import org.pixel.extra.parts.icons.IconPackBitmaps.toNormalizedIconBitmap
import org.pixel.extra.parts.icons.IconPackBitmaps.createIconPackContext
import org.pixel.extra.parts.icons.IconPackBitmaps.toNormalizedIconBitmap
import org.pixel.extra.parts.icons.IconPackParser.DynamicClockDrawable
import org.pixel.extra.parts.icons.IconPackParser.ParserHolder
import org.pixel.extra.parts.icons.IconPackParser.useParser
import org.pixel.extra.parts.icons.IconPackStore.ICON_DP

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


object IconPackManager {
    const val RELOAD_ACTION = "com.pixelparts.intent.action.RELOAD_ICONS"
    const val MIN_SHAPE_SCALE_PERCENT = 0f
    const val MAX_SHAPE_SCALE_PERCENT = 200f
    const val DEFAULT_SHAPE_SCALE_PERCENT = 72f
    internal const val DYNAMIC_ICON_CALENDAR = "calendar"
    internal const val DYNAMIC_ICON_CLOCK = "clock"

    private const val TAG = "IconPackManager"
    private const val CUSTOM_ICON_SOURCE_ID = "custom"

    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    @Volatile private var activeProgress: IconApplyProgress? = null
    @Volatile private var dashboardCache: IconDashboardState? = null



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
        IconPackBitmaps.sendReloadBroadcast(context.applicationContext ?: context)
    }

    fun startApplyAll(context: Context, iconPackPackage: String, mode: IconApplyMode): Boolean {
        val appContext = context.applicationContext ?: context
        return startBackgroundOperation(appContext.getString(R.string.app_icons_progress_applying_pack)) { taskId ->
            val packageManager = appContext.packageManager
            val installedIndex = IconPackParser.loadInstalledApplicationIndex(packageManager)
            val entries = IconPackParser.filterEntriesForMode(
                IconPackParser.parseIconPackEntries(appContext, iconPackPackage, installedIndex),
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
            val installedIndex = IconPackParser.loadInstalledApplicationIndex(packageManager)
            val packInstalled = runCatching { packageManager.getPackageInfo(iconPackPackage, 0) }.getOrNull() != null
            if (!packInstalled) {
                return@startBackgroundOperation reconcileStoredPackSelection(
                    appContext,
                    iconPackPackage,
                    selectedPackages,
                    taskId
                )
            }
            val entries = loadIconPackSelectionEntries(appContext, iconPackPackage, installedIndex, IconPackStore.readIconMap())
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
            val installedIndex = IconPackParser.loadInstalledApplicationIndex(packageManager)
            val entries = IconPackParser.singleSelectionEntries(
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
            val packages = IconPackStore.readIconMap().sources
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
        val iconMap = IconPackStore.readIconMap()
        val installedIndex = IconPackParser.loadInstalledApplicationIndex(packageManager)
        val iconPacks = loadIconPacks(appContext, packageManager, installedIndex, iconMap)
        val packLabels = iconPacks.associate { it.packageName to it.label }
        val installedApps = buildInstalledApps(installedIndex, iconMap, packLabels)
        IconDashboardState(iconPacks, installedApps, iconMap).also { dashboardCache = it }
    }

    suspend fun loadIconPackEntries(context: Context, iconPackPackage: String): List<IconPackEntry> =
        withContext(Dispatchers.IO) {
            val packageManager = context.packageManager
            IconPackParser.parseIconPackEntries(context, iconPackPackage, IconPackParser.loadInstalledApplicationIndex(packageManager))
        }

    suspend fun loadIconPackSelectionEntries(context: Context, iconPackPackage: String): List<IconPackEntry> =
        withContext(Dispatchers.IO) {
            val packageManager = context.packageManager
            val installedIndex = IconPackParser.loadInstalledApplicationIndex(packageManager)
            loadIconPackSelectionEntries(context, iconPackPackage, installedIndex, IconPackStore.readIconMap())
        }

    suspend fun loadPreviewItems(context: Context, iconPackPackage: String): List<IconPackPreviewItem> =
        withContext(Dispatchers.IO) {
            val packageManager = context.packageManager
            val installedIndex = IconPackParser.loadInstalledApplicationIndex(packageManager)
            val parsedEntries = IconPackParser.parseIconPackEntries(context, iconPackPackage, installedIndex)
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
                ?.let { IconPackStore.findDrawableResourceNames(it) }
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
            val installedIndex = IconPackParser.loadInstalledApplicationIndex(packageManager)
            IconPackParser.discoverIconPackPackages(packageManager).flatMap { iconPackPackage ->
                val packageInfo = runCatching { packageManager.getPackageInfo(iconPackPackage, 0) }.getOrNull()
                val label = packageInfo?.applicationInfo?.loadLabel(packageManager)?.toString()
                    ?.takeIf { it.isNotBlank() } ?: iconPackPackage
                IconPackParser.parseIconPackEntries(context, iconPackPackage, installedIndex)
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
        val installedIndex = IconPackParser.loadInstalledApplicationIndex(packageManager)
        val entries = IconPackParser.filterEntriesForMode(IconPackParser.parseIconPackEntries(context, iconPackPackage, installedIndex), mode)
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
        val installedIndex = IconPackParser.loadInstalledApplicationIndex(packageManager)
        val entries = IconPackParser.parseIconPackEntries(context, iconPackPackage, installedIndex)
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
            val installedIndex = IconPackParser.loadInstalledApplicationIndex(packageManager)
            val entries = IconPackParser.singleSelectionEntries(
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
        synchronized(IconPackStore.ioLock) {
            val current = IconPackStore.readIconMap()
            val shapeOverrides = current.shapeOverrides.toMutableMap()
            val normalized = overrides?.normalizedOrNull()
            if (normalized == null) {
                shapeOverrides.remove(packageName)
            } else {
                shapeOverrides[packageName] = normalized
            }
            IconPackStore.writeIconMap(current.copy(shapeOverrides = shapeOverrides.toSortedMap()))
        }
        IconPackBitmaps.sendReloadBroadcast(context.applicationContext ?: context)
    }

    suspend fun removePackageIcon(context: Context, packageName: String): IconApplyResult =
        withContext(Dispatchers.IO) { removePackageBindings(context, setOf(packageName), null) }

    suspend fun removeIconPack(context: Context, iconPackPackage: String): IconApplyResult =
        withContext(Dispatchers.IO) {
            val packages = IconPackStore.readIconMap().sources
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
            IconPackParser.loadIconPackDrawable(packContext, drawableName)?.toNormalizedIconBitmap(sizePx)
        }.getOrNull()
    }

    suspend fun loadAppliedIconBitmap(context: Context, packageName: String, sizePx: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            val fileName = IconPackStore.readIconMap().icons[packageName] ?: return@withContext null
            val iconFile = IconPackStore.findIconFile(fileName, context.resources.displayMetrics.densityDpi)
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
        val discoveredPackages = IconPackParser.discoverIconPackPackages(packageManager)
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
            val entries = IconPackParser.parseIconPackEntries(context, packageName, installedIndex)
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
        val parsedEntries = IconPackParser.parseIconPackEntries(context, iconPackPackage, installedIndex)
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

        return synchronized(IconPackStore.ioLock) {
            val current = IconPackStore.readIconMap()
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
                    IconPackStore.deleteIconFiles(fileName)
                }
                changed = true
                removed++
                processed++
                updateProgress(progressTaskId, totalWork, processed, applied, failed, removed, skipped)
            }

            IconPackStore.ensureStorageDirs()

            selectedEntries.forEach { entry ->
                val fileName = IconPackStore.fileNameForPackage(entry.appPackageName)
                val currentSource = sources[entry.appPackageName]
                val alreadyCurrent = icons[entry.appPackageName] == fileName &&
                    currentSource?.iconPackPackage == iconPackPackage &&
                    currentSource.drawableName == entry.drawableName &&
                    currentSource.dynamicIcon == entry.dynamicIcon &&
                    IconPackStore.hasExportedIcon(fileName)
                var entryApplied = false
                if (alreadyCurrent) {
                    skipped++
                } else {
                    val exported = runCatching {
                        IconPackStore.exportIconForAllDensities(context, iconPackPackage, entry, fileName)
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
                IconPackStore.writeIconMap(IconMapSnapshot(
                    icons.toSortedMap(),
                    sources.toSortedMap(),
                    packs.toSortedMap(),
                    shapeOverrides.toSortedMap()
                ))
                IconPackBitmaps.sendReloadBroadcast(context)
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
        return synchronized(IconPackStore.ioLock) {
            val current = IconPackStore.readIconMap()
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
                    IconPackStore.deleteIconFiles(fileName)
                }
                removed++
                processed++
                updateProgress(progressTaskId, targets.size, processed, 0, 0, removed, 0)
            }

            val activePacks = sources.values.map { it.iconPackPackage }.toSet()
            packs.keys.filterNot { it in activePacks }.forEach { packs.remove(it) }

            if (removed > 0) {
                IconPackStore.writeIconMap(IconMapSnapshot(
                    icons.toSortedMap(),
                    sources.toSortedMap(),
                    packs.toSortedMap(),
                    shapeOverrides.toSortedMap()
                ))
                IconPackBitmaps.sendReloadBroadcast(context)
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
        val currentPackages = IconPackStore.readIconMap().sources
            .filterValues { it.iconPackPackage == iconPackPackage }
            .keys
            .toSet()
        val removals = currentPackages - selectedPackages
        val result = removePackageBindings(context, removals, progressTaskId)
        return result.copy(requested = currentPackages.size, skipped = currentPackages.size - removals.size)
    }

    private fun clearAllIconData(context: Context, progressTaskId: String?): IconApplyResult {
        return synchronized(IconPackStore.ioLock) {
            val current = IconPackStore.readIconMap()
            val packageNames = (current.icons.keys + current.sources.keys + current.shapeOverrides.keys).toSet()
            var processed = 0
            var removed = 0
            updateProgress(progressTaskId, packageNames.size, processed, 0, 0, removed, 0)

            packageNames.forEach { _ ->
                removed++
                processed++
                updateProgress(progressTaskId, packageNames.size, processed, 0, 0, removed, 0)
            }

            IconPackStore.densities.forEach { density ->
                File(IconPackStore.iconRoot, density.folder).listFiles()?.forEach { file ->
                    if (file.isFile) file.delete()
                }
            }
            if (IconPackStore.iconMapFile.exists()) {
                IconPackStore.iconMapFile.delete()
            }
            IconPackStore.writeIconMap(IconMapSnapshot.EMPTY)
            IconPackBitmaps.sendReloadBroadcast(context)

            IconApplyResult(
                requested = packageNames.size,
                applied = 0,
                failed = 0,
                removed = removed,
                skipped = 0
            )
        }
    }


    private fun startBackgroundOperation(
        initialLabel: String,
        block: (String) -> IconApplyResult
    ): Boolean {
        val taskId = "icon_task_" + System.currentTimeMillis()
        synchronized(IconPackStore.ioLock) {
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


    private fun importPackageIcon(
        context: Context,
        packageName: String,
        uri: Uri,
        progressTaskId: String?
    ): IconApplyResult {
        return synchronized(IconPackStore.ioLock) {
            updateProgress(progressTaskId, 1, 0, 0, 0, 0, 0)
            val bitmap = IconPackStore.decodeBitmapFromUri(context, uri)
            if (bitmap == null) {
                updateProgress(progressTaskId, 1, 1, 0, 1, 0, 0)
                return@synchronized IconApplyResult(requested = 1, applied = 0, failed = 1)
            }

            IconPackStore.ensureStorageDirs()
            val current = IconPackStore.readIconMap()
            val icons = current.icons.toMutableMap()
            val sources = current.sources.toMutableMap()
            val packs = current.packs.toMutableMap()
            val fileName = IconPackStore.fileNameForPackage(packageName)
            val exported = IconPackStore.exportBitmapForAllDensities(bitmap, fileName)
            if (!exported) {
                updateProgress(progressTaskId, 1, 1, 0, 1, 0, 0)
                return@synchronized IconApplyResult(requested = 1, applied = 0, failed = 1)
            }

            val now = System.currentTimeMillis()
            icons[packageName] = fileName
            sources[packageName] = IconSource(
                iconPackPackage = CUSTOM_ICON_SOURCE_ID,
                iconPackLabel = context.getString(R.string.app_icons_custom_icon_source),
                drawableName = IconPackStore.getDisplayName(context, uri) ?: uri.lastPathSegment.orEmpty().ifBlank { "custom" },
                iconPackVersionCode = 0L,
                updatedAt = now
            )
            packs[CUSTOM_ICON_SOURCE_ID] = IconPackVersion(
                packageName = CUSTOM_ICON_SOURCE_ID,
                label = context.getString(R.string.app_icons_custom_icon_source),
                versionCode = 0L,
                appliedAt = now
            )
            IconPackStore.writeIconMap(current.copy(
                icons = icons.toSortedMap(),
                sources = sources.toSortedMap(),
                packs = packs.toSortedMap()
            ))
            IconPackBitmaps.sendReloadBroadcast(context)
            updateProgress(progressTaskId, 1, 1, 1, 0, 0, 0)
            IconApplyResult(requested = 1, applied = 1, failed = 0)
        }
    }


    private fun ApplicationInfo.isSystemApplication(): Boolean {
        return (flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
            (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    }





}