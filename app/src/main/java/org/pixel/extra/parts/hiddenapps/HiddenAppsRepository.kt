package org.pixel.extra.parts.hiddenapps

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import android.os.UserHandle
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.io.DataOutputStream
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// UI model for one row of the hidden-apps list.
data class HiddenAppEntry(
    val label: String,
    val packageName: String,
    val icon: ImageBitmap?,
    val isSystem: Boolean,
)

data class HiddenAppsLoadResult(
    val apps: List<HiddenAppEntry>,
    val hidden: Set<String>,
)

// Platform bridge for launcher hidden state. Primary path is the PackageManager
// hidden-setting API (hidden method, reached via reflection so the public-SDK
// stand still compiles); root `pm hide/unhide` is the fallback. Hidden packages
// no longer resolve as launchable, so the list is the union of launchable
// packages and packages currently flagged hidden.
object HiddenAppsRepository {

    suspend fun loadAll(context: Context): HiddenAppsLoadResult = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val user = Process.myUserHandle()
        val launchable = launchablePackages(pm)
        val installed = runCatching {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES)
        }.getOrDefault(emptyList())
        val hidden = installed.mapNotNull { info ->
            if (isHiddenReflect(pm, info.packageName, user) == true) info.packageName else null
        }.toSet()
        val apps = (launchable + hidden).distinct().mapNotNull { packageName ->
            runCatching {
                @Suppress("DEPRECATION")
                val info = pm.getApplicationInfo(packageName, 0)
                val label = info.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: packageName
                val icon = runCatching {
                    info.loadIcon(pm).toBitmap(96, 96).asImageBitmap()
                }.getOrNull()
                HiddenAppEntry(label, packageName, icon, info.isSystemApp())
            }.getOrNull()
        }.sortedWith(
            compareByDescending<HiddenAppEntry> { hidden.contains(it.packageName) }
                .thenBy { it.label.lowercase(Locale.getDefault()) }
                .thenBy { it.packageName },
        )
        HiddenAppsLoadResult(apps, hidden)
    }

    suspend fun setHidden(context: Context, packageName: String, hidden: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val user = Process.myUserHandle()
            if (setHiddenReflect(pm, packageName, hidden, user) &&
                isHiddenReflect(pm, packageName, user) == hidden
            ) {
                return@withContext true
            }
            // Root fallback; trust the su exit code when reflection is unavailable.
            // No public user-id getter exists in the SDK stubs; derive it from
            // the uid the same way UserHandle does (uid / PER_USER_RANGE).
            val suOk = runSuPm(hidden, Process.myUid() / 100000, packageName)
            val verified = isHiddenReflect(pm, packageName, user)
            suOk && (verified == hidden || verified == null)
        }

    suspend fun restartLauncher(): Boolean = withContext(Dispatchers.IO) {
        runSu("am force-stop ${HiddenAppsKeys.LAUNCHER_PACKAGE}")
    }

    @Suppress("DEPRECATION")
    private fun launchablePackages(pm: PackageManager): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return runCatching {
            pm.queryIntentActivities(intent, 0)
                .mapNotNull { it.activityInfo?.packageName }
                .toSet()
        }.getOrDefault(emptySet())
    }

    private fun isHiddenReflect(pm: PackageManager, packageName: String, user: UserHandle): Boolean? =
        runCatching {
            val method = PackageManager::class.java.getMethod(
                "getApplicationHiddenSettingAsUser",
                String::class.java,
                UserHandle::class.java,
            )
            method.invoke(pm, packageName, user) as Boolean
        }.getOrNull()

    private fun setHiddenReflect(
        pm: PackageManager,
        packageName: String,
        hidden: Boolean,
        user: UserHandle,
    ): Boolean = runCatching {
        val method = PackageManager::class.java.getMethod(
            "setApplicationHiddenSettingAsUser",
            String::class.java,
            Boolean::class.javaPrimitiveType,
            UserHandle::class.java,
        )
        method.invoke(pm, packageName, hidden, user) as Boolean
    }.getOrDefault(false)

    private fun runSuPm(hidden: Boolean, userId: Int, packageName: String): Boolean {
        val verb = if (hidden) HiddenAppsKeys.PM_HIDE else HiddenAppsKeys.PM_UNHIDE
        return runSu("pm $verb --user $userId $packageName")
    }

    private fun runSu(vararg lines: String): Boolean = runCatching {
        val process = Runtime.getRuntime().exec("su")
        DataOutputStream(process.outputStream).use { out ->
            lines.forEach { out.writeBytes("$it\n") }
            out.writeBytes("exit\n")
            out.flush()
        }
        process.waitFor() == 0
    }.getOrDefault(false)

    private fun ApplicationInfo.isSystemApp(): Boolean =
        (flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
            (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
}
