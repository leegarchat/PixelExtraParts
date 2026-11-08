package org.pixel.customparts.utils

import android.content.Context
import android.net.Uri
import kotlin.coroutines.cancellation.CancellationException
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

fun filterThemeNameInput(value: String): String {
    return value.filter { character ->
        character in 'a'..'z' || character in 'A'..'Z' ||
            character in '0'..'9' || character == ' '
    }
}

fun normalizeThemeName(name: String): String {
    return name.lowercase(Locale.US)
        .replace(Regex("\\s+"), "_")
        .replace(Regex("[^a-z0-9_]"), "_")
        .replace(Regex("_+"), "_")
        .trimStart('_')
        .trimEnd('_')
        .ifEmpty { "custom" }
}

object AnimThemeCompiler {

    private const val THEME_PACKAGE_PREFIX = "org.pixel.customparts.anim."
    private const val ROTATION_THEME_PACKAGE_PREFIX = "org.pixel.customparts.rotation."

    data class CompileResult(
        val success: Boolean,
        val apkPath: String? = null,
        val packageName: String? = null,
        val log: List<String> = emptyList(),
        val error: String? = null
    )

    fun interface LogCallback {
        fun onLog(line: String)
    }

    fun compile(
        context: Context,
        styleName: String,
        openEnterUri: Uri,
        openExitUri: Uri,
        closeEnterUri: Uri?,
        closeExitUri: Uri?,
        logCallback: LogCallback? = null,
        isCancelled: (() -> Boolean)? = null
    ): CompileResult {
        return compileInternal(context, styleName, THEME_PACKAGE_PREFIX, "anim_compile", logCallback, isCancelled) { resDir ->
            copyUriToFile(context, openEnterUri, File(resDir, "custom_open_enter.xml"), isCancelled)
            copyUriToFile(context, openExitUri, File(resDir, "custom_open_exit.xml"), isCancelled)
            if (closeEnterUri != null) {
                copyUriToFile(context, closeEnterUri, File(resDir, "custom_close_enter.xml"), isCancelled)
            } else {
                copyUriToFile(context, openExitUri, File(resDir, "custom_close_enter.xml"), isCancelled)
            }
            if (closeExitUri != null) {
                copyUriToFile(context, closeExitUri, File(resDir, "custom_close_exit.xml"), isCancelled)
            } else {
                copyUriToFile(context, openEnterUri, File(resDir, "custom_close_exit.xml"), isCancelled)
            }
        }
    }

    fun compileFromXml(
        context: Context,
        styleName: String,
        openEnterXml: String,
        openExitXml: String,
        closeEnterXml: String?,
        closeExitXml: String?,
        logCallback: LogCallback? = null,
        isCancelled: (() -> Boolean)? = null
    ): CompileResult {
        return compileInternal(context, styleName, THEME_PACKAGE_PREFIX, "anim_compile", logCallback, isCancelled) { resDir ->
            File(resDir, "custom_open_enter.xml").writeText(openEnterXml)
            File(resDir, "custom_open_exit.xml").writeText(openExitXml)
            File(resDir, "custom_close_enter.xml").writeText(
                closeEnterXml ?: openExitXml
            )
            File(resDir, "custom_close_exit.xml").writeText(
                closeExitXml ?: openEnterXml
            )
        }
    }

    fun compileRotation(
        context: Context,
        styleName: String,
        exitUri: Uri,
        enterUri: Uri,
        logCallback: LogCallback? = null,
        isCancelled: (() -> Boolean)? = null
    ): CompileResult {
        return compileInternal(context, styleName, ROTATION_THEME_PACKAGE_PREFIX, "rotation_compile", logCallback, isCancelled) { resDir ->
            copyUriToFile(context, exitUri, File(resDir, "custom_rotate_exit.xml"), isCancelled)
            copyUriToFile(context, enterUri, File(resDir, "custom_rotate_enter.xml"), isCancelled)
        }
    }

    fun compileRotationFromXml(
        context: Context,
        styleName: String,
        exitXml: String,
        enterXml: String,
        logCallback: LogCallback? = null,
        isCancelled: (() -> Boolean)? = null
    ): CompileResult {
        return compileInternal(context, styleName, ROTATION_THEME_PACKAGE_PREFIX, "rotation_compile", logCallback, isCancelled) { resDir ->
            File(resDir, "custom_rotate_exit.xml").writeText(exitXml)
            File(resDir, "custom_rotate_enter.xml").writeText(enterXml)
        }
    }

    private fun compileInternal(
        context: Context,
        styleName: String,
        packagePrefix: String,
        workspacePrefix: String,
        logCallback: LogCallback?,
        isCancelled: (() -> Boolean)?,
        writeAnims: (resDir: File) -> Unit
    ): CompileResult {
        val safeName = normalizeThemeName(styleName)
        val packageName = packagePrefix + safeName
        val result = ApkCompiler.compile(
            context,
            ApkCompiler.Request(
                packageName = packageName,
                workspaceName = "${workspacePrefix}_$safeName",
                outputFileName = "$safeName.apk",
                manifestXml = generateManifest(packageName),
                resourceDirectory = "anim",
                resourceLog = "Animation files written",
                writeResources = writeAnims
            ),
            logCallback = { logCallback?.onLog(it) },
            isCancelled = isCancelled
        )
        return CompileResult(
            success = result.success,
            apkPath = result.apkPath,
            packageName = result.packageName,
            log = result.log,
            error = result.error
        )
    }

    // Чистый, нативный API с легальным обходом Play Protect через рефлексию
    fun install(
        context: Context,
        apkPath: String,
        packageName: String,
        logCallback: LogCallback? = null,
        isCancelled: (() -> Boolean)? = null
    ): Boolean {
        return ApkInstaller.install(
            context,
            apkPath,
            packageName,
            { line -> logCallback?.onLog(line) },
            isCancelled
        )
    }

    // Возвращаем надежное удаление через нативный PackageInstaller API
    fun uninstall(context: Context, packageName: String, logCallback: LogCallback? = null): Boolean {
        return ApkInstaller.uninstall(context, packageName) { line -> logCallback?.onLog(line) }
    }

    private fun generateManifest(packageName: String): String = """
        <?xml version="1.0" encoding="utf-8"?>
        <manifest xmlns:android="http://schemas.android.com/apk/res/android"
            package="$packageName"
            android:versionCode="1"
            android:versionName="1.0">
            <application android:hasCode="false" />
        </manifest>
    """.trimIndent()

    private fun copyUriToFile(
        context: Context,
        uri: Uri,
        dest: File,
        isCancelled: (() -> Boolean)?
    ) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(dest).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    if (isCancelled?.invoke() == true) throw CancellationException()
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
            }
        } ?: throw RuntimeException("Cannot open: $uri")
    }

}
