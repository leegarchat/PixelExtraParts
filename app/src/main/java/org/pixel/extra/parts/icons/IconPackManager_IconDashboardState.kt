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