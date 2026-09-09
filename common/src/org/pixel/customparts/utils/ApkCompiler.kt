package org.pixel.customparts.utils

import android.content.Context
import kotlin.coroutines.cancellation.CancellationException
import java.io.File

/** Shared aapt2/signing pipeline for generated animation and overlay APKs. */
object ApkCompiler {

    private const val TAG = "ApkCompiler"
    private const val FRAMEWORK_RES_PATH = "/system/framework/framework-res.apk"

    data class Request(
        val packageName: String,
        val workspaceName: String,
        val outputFileName: String,
        val manifestXml: String,
        val resourceDirectory: String,
        val resourceLog: String,
        val autoAddOverlay: Boolean = false,
        val writeResources: (File) -> Unit
    )

    data class Result(
        val success: Boolean,
        val packageName: String? = null,
        val apkPath: String? = null,
        val log: List<String> = emptyList(),
        val error: String? = null
    )

    private data class CommandResult(
        val exitCode: Int,
        val output: String
    )

    fun compile(
        context: Context,
        request: Request,
        logCallback: ((String) -> Unit)? = null,
        isCancelled: (() -> Boolean)? = null
    ): Result {
        val log = mutableListOf<String>()
        fun emit(message: String) {
            log += message
            logCallback?.invoke(message)
        }
        fun checkCancelled() {
            if (isCancelled?.invoke() == true) throw CancellationException()
        }

        return try {
            checkCancelled()
            emit("Package: ${request.packageName}")

            val workDir = File(context.cacheDir, request.workspaceName)
            if (workDir.exists()) workDir.deleteRecursively()
            if (!workDir.mkdirs()) {
                throw IllegalStateException("Unable to create workspace: ${workDir.absolutePath}")
            }

            val resourceDir = File(workDir, "res/${request.resourceDirectory}")
            if (!resourceDir.mkdirs()) {
                throw IllegalStateException("Unable to create resource directory")
            }
            request.writeResources(resourceDir)
            checkCancelled()
            emit(request.resourceLog)

            val manifest = File(workDir, "AndroidManifest.xml")
            manifest.writeText(request.manifestXml)
            emit("Generated AndroidManifest.xml")

            val aapt2 = getAapt2(context)
            checkCancelled()
            emit("aapt2 binary: ${aapt2.absolutePath}")

            val frameworkRes = File(FRAMEWORK_RES_PATH)
            if (!frameworkRes.exists()) {
                return Result(false, packageName = request.packageName, log = log,
                    error = "framework-res.apk not found")
            }

            val compiledZip = File(workDir, "compiled.zip")
            val compileResult = runCommand(
                arrayOf(
                    aapt2.absolutePath,
                    "compile",
                    "--dir", File(workDir, "res").absolutePath,
                    "-o", compiledZip.absolutePath
                ),
                workDir,
                isCancelled
            )
            if (compileResult.output.isNotBlank()) {
                emit("compile output: ${compileResult.output}")
            }
            if (compileResult.exitCode != 0 || !compiledZip.exists()) {
                return Result(false, packageName = request.packageName, log = log,
                    error = "aapt2 compile failed: ${compileResult.output}")
            }

            val unsignedApk = File(workDir, "unsigned.apk")
            val linkCommand = mutableListOf(
                aapt2.absolutePath,
                "link",
                "-I", frameworkRes.absolutePath,
                "--manifest", manifest.absolutePath
            )
            if (request.autoAddOverlay) {
                linkCommand += listOf(
                    "--auto-add-overlay",
                    "--keep-raw-values",
                    "--no-resource-deduping",
                    "--no-resource-removal"
                )
            }
            linkCommand += listOf(
                "--min-sdk-version", "33",
                "--target-sdk-version", "35",
                "-o", unsignedApk.absolutePath,
                compiledZip.absolutePath
            )

            val linkResult = runCommand(linkCommand.toTypedArray(), workDir, isCancelled)
            if (linkResult.output.isNotBlank()) {
                emit("link output: ${linkResult.output}")
            }
            if (linkResult.exitCode != 0 || !unsignedApk.exists()) {
                return Result(false, packageName = request.packageName, log = log,
                    error = "aapt2 link failed: ${linkResult.output}")
            }

            val signedApk = File(workDir, request.outputFileName)
            checkCancelled()
            emit("Signing APK...")
            if (!AnimThemeSigner.sign(context, unsignedApk, signedApk)) {
                return Result(false, packageName = request.packageName, log = log,
                    error = "APK signing failed")
            }
            emit("Signed APK: ${signedApk.absolutePath} (${signedApk.length()} bytes)")
            Result(
                success = true,
                packageName = request.packageName,
                apkPath = signedApk.absolutePath,
                log = log
            )
        } catch (t: Throwable) {
            if (t is CancellationException || isCancelled?.invoke() == true) {
                throw CancellationException()
            }
            emit("ERROR: ${t.message}")
            android.util.Log.e(TAG, "APK compilation failed", t)
            Result(false, packageName = request.packageName, log = log, error = t.message)
        }
    }

    private fun getAapt2(context: Context): File {
        val candidates = mutableListOf<File>()
        // Privileged system_ext app: prefer on-device aapt2 copies first.
        candidates += File("/system/bin/aapt2_pixelparts")
        candidates += File("/system_ext/bin/aapt2_pixelparts")
        candidates += File("/system/bin/aapt2")
        candidates += File("/system_ext/bin/aapt2")
        candidates += File("/system_ext/lib64/libaapt2.so")
        candidates += File("/system/lib64/libaapt2.so")
        candidates += File(context.applicationInfo.nativeLibraryDir, "libaapt2.so")

        for (candidate in candidates) {
            if (!candidate.exists()) continue
            if (candidate.canExecute()) return candidate
            try {
                Runtime.getRuntime().exec(arrayOf(candidate.absolutePath, "version")).waitFor()
                return candidate
            } catch (_: Exception) {
                // Try the next candidate.
            }
        }
        throw IllegalStateException(
            "aapt2 not found: ${candidates.joinToString { it.absolutePath }}"
        )
    }

    private fun runCommand(
        cmd: Array<String>,
        workDir: File,
        isCancelled: (() -> Boolean)?
    ): CommandResult {
        val process = ProcessBuilder(*cmd)
            .directory(workDir)
            .redirectErrorStream(true)
            .start()
        process.outputStream.close()
        val output = StringBuilder()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        try {
            process.inputStream.use { input ->
                while (process.isAlive) {
                    if (isCancelled?.invoke() == true) {
                        process.destroyForcibly()
                        throw CancellationException()
                    }
                    while (input.available() > 0) {
                        val count = input.read(buffer)
                        if (count <= 0) break
                        output.append(String(buffer, 0, count, Charsets.UTF_8))
                    }
                    Thread.sleep(50)
                }
                while (input.available() > 0) {
                    val count = input.read(buffer)
                    if (count <= 0) break
                    output.append(String(buffer, 0, count, Charsets.UTF_8))
                }
            }
        } catch (_: InterruptedException) {
            process.destroyForcibly()
            throw CancellationException()
        }

        return CommandResult(process.waitFor(), output.toString())
    }
}
