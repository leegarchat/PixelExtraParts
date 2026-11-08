package org.pixel.extra.parts.icons

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pixel.extra.parts.R
import java.util.Locale

// Icon-pack apply pipeline: discovers installed packs, applies icon
// selections through the shared backend, and tracks every binding in
// icon_map.json. Firmware hooks consume the exported files; the UI polls
// getActiveProgress() for the live operation state.
data class IconDashboardState(
    val iconPacks: List<IconPackInfo>,
    val installedApps: List<InstalledIconApp>,
    val iconMap: IconMapSnapshot,
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
    val installed: Boolean = true,
)

enum class IconPackApplyStatus {
    NOT_APPLIED,
    APPLIED_PARTIAL,
    APPLIED_FULL,
}

data class IconPackEntry(
    val iconPackPackage: String,
    val appPackageName: String,
    val componentName: String?,
    val drawableName: String,
    val label: String,
    val installed: Boolean,
    val isSystem: Boolean,
    val dynamicIcon: DynamicIconInfo? = null,
)

data class DynamicIconInfo(
    val type: String,
    val clockConfig: DynamicClockConfig? = null,
    val calendarPrefix: String? = null,
    val calendarFallbackDrawable: String? = null,
)

data class DynamicClockConfig(
    val hourLayerIndex: Int = -1,
    val minuteLayerIndex: Int = -1,
    val secondLayerIndex: Int = -1,
    val defaultHour: Int = 0,
    val defaultMinute: Int = 0,
    val defaultSecond: Int = 0,
)

data class IconPackPreviewItem(
    val iconPackPackage: String,
    val drawableName: String,
    val label: String,
    val appPackageName: String?,
)

data class AppIconCandidate(
    val iconPackPackage: String,
    val iconPackLabel: String,
    val drawableName: String,
)

data class InstalledIconApp(
    val label: String,
    val packageName: String,
    val icon: Drawable?,
    val isSystem: Boolean,
    val isLauncherVisible: Boolean,
    val appliedIconPackLabel: String?,
    val appliedIconFileName: String?,
    val shapeOverrides: AppIconShapeOverrides?,
)

data class IconApplyResult(
    val requested: Int,
    val applied: Int,
    val failed: Int,
    val removed: Int = 0,
    val skipped: Int = 0,
)

enum class IconApplyMode {
    ALL_PACK,
    SYSTEM_ONLY,
    USER_ONLY,
    INSTALLED_ONLY,
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
    val result: IconApplyResult? = null,
) {
    val percent: Int
        get() = if (total <= 0) 0 else ((processed * 100) / total).coerceIn(0, 100)
}

data class IconMapSnapshot(
    val icons: Map<String, String>,
    val sources: Map<String, IconSource>,
    val packs: Map<String, IconPackVersion>,
    val shapeOverrides: Map<String, AppIconShapeOverrides> = emptyMap(),
) {
    companion object {
        val EMPTY = IconMapSnapshot(emptyMap(), emptyMap(), emptyMap(), emptyMap())
    }
}

enum class AppIconShapeArea(val jsonKey: String) {
    SYSTEM("system"),
    NOTIFICATION("notification"),
    LAUNCHER("launcher"),
}

data class IconShapeAreaConfig(
    val stretchShape: Boolean = false,
    val removeShape: Boolean = false,
    val scalePercent: Float = PacksManager.DEFAULT_SHAPE_SCALE_PERCENT,
) {
    fun normalized(): IconShapeAreaConfig {
        val remove = removeShape
        return copy(
            stretchShape = stretchShape && !remove,
            scalePercent = scalePercent.coerceIn(
                PacksManager.MIN_SHAPE_SCALE_PERCENT,
                PacksManager.MAX_SHAPE_SCALE_PERCENT,
            ),
        )
    }
}

data class AppIconShapeOverrides(
    val system: IconShapeAreaConfig? = null,
    val notification: IconShapeAreaConfig? = null,
    val launcher: IconShapeAreaConfig? = null,
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
            launcher = launcher?.normalized(),
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
    val dynamicIcon: DynamicIconInfo? = null,
)

data class IconPackVersion(
    val packageName: String,
    val label: String,
    val versionCode: Long,
    val appliedAt: Long,
)

object PacksManager {
    // Firmware reload contract: the action string stays verbatim so the
    // framework-side hooks keep reacting to it.
    const val RELOAD_ACTION = "com.pixelparts.intent.action.RELOAD_ICONS"
    const val MIN_SHAPE_SCALE_PERCENT = 0f
    const val MAX_SHAPE_SCALE_PERCENT = 200f
    const val DEFAULT_SHAPE_SCALE_PERCENT = 72f
    const val CUSTOM_ICON_SOURCE_ID = "custom"

    private const val TAG = "PacksManager"
    private val ioLock = Any()
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var activeProgress: IconApplyProgress? = null
    @Volatile private var dashboardCache: IconDashboardState? = null

    fun getActiveProgress(): IconApplyProgress? = activeProgress

    fun getCachedDashboardState(): IconDashboardState? = dashboardCache

    fun clearDashboardCache() {
        dashboardCache = null
    }

    fun clearCompletedProgress() {
        if (activeProgress?.completed == true) activeProgress = null
    }

    fun requestIconReload(context: Context) {
        PacksBackend.sendReloadBroadcast(context.applicationContext ?: context)
    }

    fun startApplyAll(context: Context, iconPackPackage: String, mode: IconApplyMode): Boolean {
        val appContext = context.applicationContext ?: context
        return startBackgroundOperation(appContext.getString(R.string.icons_progress_applying_pack)) { taskId ->
            val packageManager = appContext.packageManager
            val installedIndex = PacksBackend.loadInstalledApplicationIndex(packageManager)
            val entries = PacksBackend.filterEntriesForMode(
                PacksBackend.parseIconPackEntries(appContext, iconPackPackage, installedIndex),
                mode,
            )
            applyIconSelection(
                context = appContext,
                iconPackPackage = iconPackPackage,
                allEntries = entries,
                selectedPackages = entries.map { it.appPackageName }.toSet(),
                clearUnselected = false,
                progressTaskId = taskId,
            )
        }
    }

    fun startApplyPartial(context: Context, iconPackPackage: String, selectedPackages: Set<String>): Boolean {
        val appContext = context.applicationContext ?: context
        return startBackgroundOperation(appContext.getString(R.string.icons_progress_applying_selected)) { taskId ->
            val packageManager = appContext.packageManager
            val installedIndex = PacksBackend.loadInstalledApplicationIndex(packageManager)
            val packInstalled = runCatching { packageManager.getPackageInfo(iconPackPackage, 0) }.getOrNull() != null
            if (!packInstalled) {
                return@startBackgroundOperation reconcileStoredPackSelection(
                    appContext, iconPackPackage, selectedPackages, taskId,
                )
            }
            val entries = PacksBackend.loadIconPackSelectionEntries(
                appContext, iconPackPackage, installedIndex, PacksBackend.readIconMap(),
            )
            applyIconSelection(
                context = appContext,
                iconPackPackage = iconPackPackage,
                allEntries = entries,
                selectedPackages = selectedPackages,
                clearUnselected = true,
                progressTaskId = taskId,
            )
        }
    }

    fun startApplySingle(
        context: Context,
        iconPackPackage: String,
        packageName: String,
        drawableName: String? = null,
    ): Boolean {
        val appContext = context.applicationContext ?: context
        return startBackgroundOperation(appContext.getString(R.string.icons_progress_applying_app)) { taskId ->
            val packageManager = appContext.packageManager
            val installedIndex = PacksBackend.loadInstalledApplicationIndex(packageManager)
            val entries = PacksBackend.singleSelectionEntries(
                appContext, iconPackPackage, packageName, drawableName, installedIndex,
            )
            applyIconSelection(
                context = appContext,
                iconPackPackage = iconPackPackage,
                allEntries = entries,
                selectedPackages = setOf(packageName),
                clearUnselected = false,
                progressTaskId = taskId,
            )
        }
    }

    fun startImportPackageIcon(context: Context, packageName: String, uri: Uri): Boolean {
        val appContext = context.applicationContext ?: context
        runCatching {
            appContext.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return startBackgroundOperation(appContext.getString(R.string.icons_progress_importing_icon)) { taskId ->
            PacksBackend.importPackageIcon(appContext, packageName, uri, taskId) { progress ->
                updateProgress(progress)
            }
        }
    }

    fun startRemovePackageIcon(context: Context, packageName: String): Boolean {
        val appContext = context.applicationContext ?: context
        return startBackgroundOperation(appContext.getString(R.string.icons_progress_restoring_icon)) { taskId ->
            removePackageBindings(appContext, setOf(packageName), taskId)
        }
    }

    fun startRemoveIconPack(context: Context, iconPackPackage: String): Boolean {
        val appContext = context.applicationContext ?: context
        return startBackgroundOperation(appContext.getString(R.string.icons_progress_removing_pack)) { taskId ->
            val packages = PacksBackend.readIconMap().sources
                .filterValues { it.iconPackPackage == iconPackPackage }
                .keys
                .toSet()
            removePackageBindings(appContext, packages, taskId)
        }
    }

    fun startClearAllIcons(context: Context): Boolean {
        val appContext = context.applicationContext ?: context
        return startBackgroundOperation(appContext.getString(R.string.icons_progress_clearing_all)) { taskId ->
            PacksBackend.clearAllIconData(appContext, taskId) { progress -> updateProgress(progress) }
        }
    }

    suspend fun loadDashboardState(context: Context, forceRefresh: Boolean = false): IconDashboardState =
        withContext(Dispatchers.IO) {
            if (!forceRefresh) dashboardCache?.let { return@withContext it }
            val appContext = context.applicationContext ?: context
            val packageManager = appContext.packageManager
            val iconMap = PacksBackend.readIconMap()
            val installedIndex = PacksBackend.loadInstalledApplicationIndex(packageManager)
            val iconPacks = PacksBackend.loadIconPacks(appContext, packageManager, installedIndex, iconMap)
            val packLabels = iconPacks.associate { it.packageName to it.label }
            val installedApps = installedIndex.values.map { app ->
                val source = iconMap.sources[app.packageName]
                InstalledIconApp(
                    label = app.label,
                    packageName = app.packageName,
                    icon = app.icon,
                    isSystem = app.isSystem,
                    isLauncherVisible = app.isLauncherVisible,
                    appliedIconPackLabel = source?.let { packLabels[it.iconPackPackage] ?: it.iconPackLabel },
                    appliedIconFileName = iconMap.icons[app.packageName],
                    shapeOverrides = iconMap.shapeOverrides[app.packageName],
                )
            }.sortedWith(
                compareBy<InstalledIconApp> { it.label.lowercase(Locale.getDefault()) }
                    .thenBy { it.packageName },
            )
            IconDashboardState(iconPacks, installedApps, iconMap).also { dashboardCache = it }
        }

    suspend fun loadIconPackSelectionEntries(context: Context, iconPackPackage: String): List<IconPackEntry> =
        withContext(Dispatchers.IO) {
            val packageManager = context.packageManager
            val installedIndex = PacksBackend.loadInstalledApplicationIndex(packageManager)
            PacksBackend.loadIconPackSelectionEntries(context, iconPackPackage, installedIndex, PacksBackend.readIconMap())
        }

    suspend fun loadPreviewItems(context: Context, iconPackPackage: String): List<IconPackPreviewItem> =
        withContext(Dispatchers.IO) {
            val packageManager = context.packageManager
            val installedIndex = PacksBackend.loadInstalledApplicationIndex(packageManager)
            PacksBackend.loadPreviewItems(context, iconPackPackage, installedIndex)
        }

    suspend fun loadCandidatesForApp(context: Context, packageName: String): List<AppIconCandidate> =
        withContext(Dispatchers.IO) {
            val packageManager = context.packageManager
            val installedIndex = PacksBackend.loadInstalledApplicationIndex(packageManager)
            PacksBackend.loadCandidatesForApp(context, installedIndex, packageName)
        }

    suspend fun loadIconBitmap(context: Context, iconPackPackage: String, drawableName: String, sizePx: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                val packContext = context.createPackageContext(iconPackPackage, Context.CONTEXT_IGNORE_SECURITY)
                PacksBackend.loadIconPackDrawable(packContext, drawableName)?.toNormalizedPackBitmap(sizePx)
            }.getOrNull()
        }

    suspend fun loadAppliedIconBitmap(context: Context, packageName: String, sizePx: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            val fileName = PacksBackend.readIconMap().icons[packageName] ?: return@withContext null
            val iconFile = PacksBackend.findIconFile(fileName, context.resources.displayMetrics.densityDpi)
                ?: return@withContext null
            BitmapFactory.decodeFile(iconFile.absolutePath)?.let { bitmap ->
                if (bitmap.width == sizePx && bitmap.height == sizePx) {
                    bitmap
                } else {
                    Bitmap.createScaledBitmap(bitmap, sizePx.coerceAtLeast(1), sizePx.coerceAtLeast(1), true)
                }
            }
        }

    fun savePackageShapeOverrides(context: Context, packageName: String, overrides: AppIconShapeOverrides?) {
        PacksBackend.savePackageShapeOverrides(context.applicationContext ?: context, packageName, overrides)
        dashboardCache = null
    }

    private fun applyIconSelection(
        context: Context,
        iconPackPackage: String,
        allEntries: List<IconPackEntry>,
        selectedPackages: Set<String>,
        clearUnselected: Boolean,
        progressTaskId: String?,
    ): IconApplyResult {
        if (allEntries.isEmpty() && selectedPackages.isEmpty()) return IconApplyResult(0, 0, 0)
        val packageManager = context.packageManager
        val packageInfo = runCatching { packageManager.getPackageInfo(iconPackPackage, 0) }.getOrNull()
            ?: return IconApplyResult(selectedPackages.size, 0, selectedPackages.size)
        val appInfo = packageInfo.applicationInfo
            ?: return IconApplyResult(selectedPackages.size, 0, selectedPackages.size)
        val packLabel = appInfo.loadLabel(packageManager)?.toString()?.takeIf { it.isNotBlank() }
            ?: iconPackPackage
        val versionCode = packageInfo.versionCodeLongCompat()
        val selectedEntries = allEntries
            .filter { it.appPackageName in selectedPackages }
            .distinctBy { it.appPackageName }
        return synchronized(ioLock) {
            dashboardCache = null
            val current = PacksBackend.readIconMapLocked()
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
            updateProgress(taskId = progressTaskId, total = totalWork, processed = processed,
                applied = applied, failed = failed, removed = removed, skipped = skipped)
            removals.forEach { packageName ->
                val fileName = icons.remove(packageName)
                sources.remove(packageName)
                shapeOverrides.remove(packageName)
                if (fileName != null) PacksBackend.deleteIconFiles(fileName)
                changed = true
                removed++
                processed++
                updateProgress(taskId = progressTaskId, total = totalWork, processed = processed,
                    applied = applied, failed = failed, removed = removed, skipped = skipped)
            }
            PacksBackend.ensureStorageDirs()
            selectedEntries.forEach { entry ->
                val fileName = PacksBackend.fileNameForPackage(entry.appPackageName)
                val currentSource = sources[entry.appPackageName]
                val alreadyCurrent = icons[entry.appPackageName] == fileName &&
                    currentSource?.iconPackPackage == iconPackPackage &&
                    currentSource.drawableName == entry.drawableName &&
                    currentSource.dynamicIcon == entry.dynamicIcon &&
                    PacksBackend.hasExportedIcon(fileName)
                var entryApplied = false
                if (alreadyCurrent) {
                    skipped++
                } else {
                    val exported = runCatching {
                        PacksBackend.exportIconForAllDensities(context, iconPackPackage, entry, fileName)
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
                        dynamicIcon = entry.dynamicIcon,
                    )
                    if (!alreadyCurrent) changed = true
                }
                processed++
                updateProgress(taskId = progressTaskId, total = totalWork, processed = processed,
                    applied = applied, failed = failed, removed = removed, skipped = skipped)
            }
            if (applied > 0 || changed) {
                packs[iconPackPackage] = IconPackVersion(
                    packageName = iconPackPackage,
                    label = packLabel,
                    versionCode = versionCode,
                    appliedAt = System.currentTimeMillis(),
                )
                PacksBackend.writeIconMapLocked(
                    IconMapSnapshot(
                        icons.toSortedMap(), sources.toSortedMap(),
                        packs.toSortedMap(), shapeOverrides.toSortedMap(),
                    ),
                )
                PacksBackend.sendReloadBroadcast(context)
            }
            IconApplyResult(selectedEntries.size, applied, failed, removed, skipped)
        }
    }

    private fun removePackageBindings(
        context: Context,
        packageNames: Set<String>,
        progressTaskId: String?,
    ): IconApplyResult {
        return synchronized(ioLock) {
            dashboardCache = null
            val current = PacksBackend.readIconMapLocked()
            val icons = current.icons.toMutableMap()
            val sources = current.sources.toMutableMap()
            val packs = current.packs.toMutableMap()
            val shapeOverrides = current.shapeOverrides.toMutableMap()
            val targets = packageNames.filter {
                icons.containsKey(it) || sources.containsKey(it) || shapeOverrides.containsKey(it)
            }
            var processed = 0
            var removed = 0
            updateProgress(taskId = progressTaskId, total = targets.size, processed = processed,
                applied = 0, failed = 0, removed = removed, skipped = 0)
            targets.forEach { packageName ->
                val fileName = icons.remove(packageName)
                sources.remove(packageName)
                shapeOverrides.remove(packageName)
                if (fileName != null) PacksBackend.deleteIconFiles(fileName)
                removed++
                processed++
                updateProgress(taskId = progressTaskId, total = targets.size, processed = processed,
                    applied = 0, failed = 0, removed = removed, skipped = 0)
            }
            val activePacks = sources.values.map { it.iconPackPackage }.toSet()
            packs.keys.filterNot { it in activePacks }.forEach { packs.remove(it) }
            if (removed > 0) {
                PacksBackend.writeIconMapLocked(
                    IconMapSnapshot(
                        icons.toSortedMap(), sources.toSortedMap(),
                        packs.toSortedMap(), shapeOverrides.toSortedMap(),
                    ),
                )
                PacksBackend.sendReloadBroadcast(context)
            }
            IconApplyResult(packageNames.size, 0, 0, removed, 0)
        }
    }

    private fun reconcileStoredPackSelection(
        context: Context,
        iconPackPackage: String,
        selectedPackages: Set<String>,
        progressTaskId: String?,
    ): IconApplyResult {
        val currentPackages = PacksBackend.readIconMap().sources
            .filterValues { it.iconPackPackage == iconPackPackage }
            .keys
            .toSet()
        val removals = currentPackages - selectedPackages
        val result = removePackageBindings(context, removals, progressTaskId)
        return result.copy(requested = currentPackages.size, skipped = currentPackages.size - removals.size)
    }

    private fun startBackgroundOperation(
        initialLabel: String,
        block: (String) -> IconApplyResult,
    ): Boolean {
        val taskId = "icon_task_" + System.currentTimeMillis()
        synchronized(ioLock) {
            if (activeProgress?.completed == false) return false
            activeProgress = IconApplyProgress(
                taskId = taskId, label = initialLabel, total = 0, processed = 0,
                applied = 0, failed = 0, removed = 0, skipped = 0, completed = false,
            )
        }
        managerScope.launch {
            val result = runCatching { block(taskId) }.getOrElse { IconApplyResult(0, 0, 1) }
            completeProgress(taskId, result)
        }
        return true
    }

    private fun updateProgress(
        taskId: String?, total: Int, processed: Int, applied: Int,
        failed: Int, removed: Int, skipped: Int,
    ) {
        updateProgress(
            IconApplyProgress(
                taskId = taskId.orEmpty(), label = "", total = total, processed = processed,
                applied = applied, failed = failed, removed = removed, skipped = skipped,
                completed = false,
            ),
        )
    }

    private fun updateProgress(progress: IconApplyProgress) {
        val current = activeProgress ?: return
        if (progress.taskId.isNotEmpty() && current.taskId != progress.taskId) return
        activeProgress = current.copy(
            total = progress.total, processed = progress.processed, applied = progress.applied,
            failed = progress.failed, removed = progress.removed, skipped = progress.skipped,
        )
    }

    private fun completeProgress(taskId: String, result: IconApplyResult) {
        val current = activeProgress ?: return
        if (current.taskId != taskId) return
        dashboardCache = null
        val total = current.total.coerceAtLeast(result.requested + result.removed)
        activeProgress = current.copy(
            total = total, processed = total, applied = result.applied,
            failed = result.failed, removed = result.removed, skipped = result.skipped,
            completed = true, result = result,
        )
    }

    private fun android.content.pm.PackageInfo.versionCodeLongCompat(): Long {
        @Suppress("DEPRECATION")
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            longVersionCode
        } else {
            versionCode.toLong()
        }
    }
}
