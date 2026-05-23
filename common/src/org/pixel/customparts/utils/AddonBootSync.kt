package org.pixel.customparts.utils

import android.content.Context
import android.provider.Settings
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipFile

object AddonBootSync {
    private const val TAG = "AddonBootSync"
    private const val ADDON_DIR = "/data/pixelparts/addons"
    private const val SYSTEM_ADDON_DIR = "/system_ext/etc/pixelparts/addons"
    private const val ADDON_PREFIX = "pixel_addon_"
    private const val INJECT_PREFIX = "pixel_extra_parts_inject_package_"
    private const val IGNORED_PACKAGE = "android"

    private val builtinWhitelist = setOf(
        "com.google.android.apps.nexuslauncher",
        "com.google.android.apps.pixel.launcher",
        "com.android.launcher3",
        "com.android.systemui"
    )

    fun sync(context: Context) {
        try {
            val descriptors = scanDescriptors()
            val enabledTargets = linkedSetOf<String>()

            descriptors.values.forEach { descriptor ->
                val id = descriptor.optString("id", descriptor.optString("entryClass", ""))
                if (id.isBlank()) return@forEach

                val enabled = ensureEnabledDefault(context, id, descriptor.optBoolean("enabled", true))
                if (!enabled) return@forEach

                enabledTargets += effectiveTargets(context, id, descriptor)
            }

            enabledTargets
                .filter { it != IGNORED_PACKAGE && it !in builtinWhitelist }
                .forEach { packageName ->
                    Settings.Global.putInt(context.contentResolver, INJECT_PREFIX + packageName, 1)
                }

            Log.d(TAG, "Boot sync indexed ${descriptors.size} addon(s), whitelisted ${enabledTargets.size} target(s)")
        } catch (t: Throwable) {
            Log.e(TAG, "Boot addon sync failed", t)
        }
    }

    private fun ensureEnabledDefault(context: Context, id: String, defaultEnabled: Boolean): Boolean {
        val key = ADDON_PREFIX + id + "_enabled"
        val raw = Settings.Global.getString(context.contentResolver, key)
        if (raw == null) {
            Settings.Global.putInt(context.contentResolver, key, if (defaultEnabled) 1 else 0)
            return defaultEnabled
        }
        return raw.toIntOrNull()?.let { it != 0 } ?: defaultEnabled
    }

    private fun effectiveTargets(context: Context, id: String, descriptor: JSONObject): Set<String> {
        val scopeMode = try {
            Settings.Global.getInt(context.contentResolver, ADDON_PREFIX + id + "_scope_mode", 0)
        } catch (_: Throwable) { 0 }

        val targets = linkedSetOf<String>()
        if (scopeMode == 0 || scopeMode == 2) {
            targets += stringArray(descriptor.optJSONArray("targetPackages"))
        }
        if (scopeMode == 1 || scopeMode == 2) {
            Settings.Global.getString(context.contentResolver, ADDON_PREFIX + id + "_packages")
                ?.split(',')
                ?.map { it.trim() }
                ?.filterTo(targets) { it.isNotEmpty() }
        }
        targets.remove(IGNORED_PACKAGE)
        return targets
    }

    private data class DescriptorCandidate(
        val descriptor: JSONObject,
        val isSystem: Boolean,
        val version: String
    )

    private fun scanDescriptors(): Map<String, JSONObject> {
        val descriptors = linkedMapOf<String, DescriptorCandidate>()
        listOf(SYSTEM_ADDON_DIR, ADDON_DIR).forEach { dirPath ->
            val isSystemDir = dirPath == SYSTEM_ADDON_DIR
            val dir = File(dirPath)
            if (!dir.isDirectory) return@forEach
            dir.listFiles { file -> file.isFile && file.name.endsWith(".jar") }?.forEach { jar ->
                val descriptor = readDescriptor(jar) ?: return@forEach
                val id = descriptor.optString("id", descriptor.optString("entryClass", jar.nameWithoutExtension))
                if (id.isBlank()) return@forEach

                val candidate = DescriptorCandidate(
                    descriptor = descriptor,
                    isSystem = isSystemDir,
                    version = descriptor.optString("version", "1.0")
                )
                val existing = descriptors[id]
                if (existing == null || shouldPrefer(candidate, existing)) {
                    descriptors[id] = candidate
                }
            }
        }
        return descriptors.mapValues { it.value.descriptor }
    }

    private fun shouldPrefer(candidate: DescriptorCandidate, existing: DescriptorCandidate): Boolean {
        val versionCompare = compareVersions(candidate.version, existing.version)
        if (versionCompare != 0) return versionCompare > 0
        return !candidate.isSystem && existing.isSystem
    }

    private fun compareVersions(left: String, right: String): Int {
        val leftParts = left.split('.', '-', '_').map { it.toIntOrNull() ?: 0 }
        val rightParts = right.split('.', '-', '_').map { it.toIntOrNull() ?: 0 }
        val count = maxOf(leftParts.size, rightParts.size)
        for (index in 0 until count) {
            val leftPart = leftParts.getOrElse(index) { 0 }
            val rightPart = rightParts.getOrElse(index) { 0 }
            if (leftPart != rightPart) return leftPart.compareTo(rightPart)
        }
        return 0
    }

    private fun readDescriptor(jarFile: File): JSONObject? {
        val external = File(jarFile.absolutePath + ".json")
        if (external.exists()) {
            runCatching { return JSONObject(external.readText(Charsets.UTF_8)) }
        }
        return runCatching {
            ZipFile(jarFile).use { zip ->
                val entry = zip.getEntry("META-INF/addon.json") ?: return null
                JSONObject(zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() })
            }
        }.getOrNull()
    }

    private fun stringArray(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            array.optString(index).trim().takeIf { it.isNotEmpty() }
        }
    }
}