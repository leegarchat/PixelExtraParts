package org.pixel.customparts.utils

import android.content.Context
import android.util.Log
import java.io.DataOutputStream
import java.util.concurrent.TimeUnit

object RootUtils {
    private const val TAG = "RootUtils"

    private val REQUIRED_PERMISSIONS = arrayOf(
        "android.permission.WRITE_SECURE_SETTINGS",
        "android.permission.STATUS_BAR",
        "android.permission.DEVICE_POWER",
        "android.permission.MONITOR_INPUT",
        "android.permission.MANAGE_ACTIVITY_TASKS",
        "android.permission.REAL_GET_TASKS",
        "android.permission.INTERACT_ACROSS_USERS_FULL",
        "android.permission.STATUS_BAR_SERVICE",
        "android.permission.INTERNAL_SYSTEM_WINDOW",
        "android.permission.INTERNET"
    )

    fun hasRootAccess(): Boolean {
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec("su")
            Thread { try { process?.inputStream?.readBytes() } catch (ignored: Exception) {} }.start()
            Thread { try { process?.errorStream?.readBytes() } catch (ignored: Exception) {} }.start()
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("exit\n")
            os.flush()
            os.close()
            val exitValue = process.waitFor()
            exitValue == 0
        } catch (e: Exception) {
            Log.e(TAG, "Root check failed", e)
            false
        } finally {
            try { process?.destroy() } catch (ignored: Exception) {}
        }
    }

    fun grantPermissions(context: Context) {
        val packageName = context.packageName
        val commands = StringBuilder()
        
        try {
            for (perm in REQUIRED_PERMISSIONS) {
                if (context.checkSelfPermission(perm) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Granting permission: $perm")
                    commands.append("pm grant $packageName $perm\n")
                }
            }

            if (commands.isNotEmpty()) {
                commands.append("exit\n")
                runSuCommand(commands.toString())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing permissions", e)
        }
    }

    private fun runSuCommand(command: String) {
        var process: Process? = null
        try {
            process = Runtime.getRuntime().exec("su")
            
            
            Thread { try { process?.inputStream?.readBytes() } catch (ignored: Exception) {} }.start()
            Thread { try { process?.errorStream?.readBytes() } catch (ignored: Exception) {} }.start()

            val os = DataOutputStream(process.outputStream)
            os.writeBytes(command)
            os.flush()
            os.close() 
            
            process.waitFor()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to run su command", e)
        } finally {
            try { process?.destroy() } catch (ignored: Exception) {}
        }
    }
}

/**
 * Execute a shell command via su (root).
 * Used by Xposed/root builds for operations that require root access.
 */
fun runRootCommand(command: String) {
    var process: Process? = null
    try {
        process = Runtime.getRuntime().exec("su")
        val os = DataOutputStream(process.outputStream)
        os.writeBytes("$command\n")
        os.writeBytes("exit\n")
        os.flush()
        os.close()
        process.waitFor()
    } catch (e: Exception) {
        Log.e("RootUtils", "runRootCommand failed: $command", e)
        try {
            Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
        } catch (ex: Exception) {
            Log.e("RootUtils", "Fallback sh -c also failed", ex)
        }
    } finally {
        try { process?.destroy() } catch (ignored: Exception) {}
    }
}

data class ShellCommandResult(
    val command: String,
    val exitCode: Int,
    val output: String,
    val error: String,
    val timedOut: Boolean = false
) {
    fun combinedOutput(): String {
        return buildString {
            if (output.isNotBlank()) append(output.trimEnd())
            if (error.isNotBlank()) {
                if (isNotEmpty()) append('\n')
                append(error.trimEnd())
            }
            if (isEmpty()) append("exitCode=$exitCode")
            if (timedOut) {
                if (isNotEmpty()) append('\n')
                append("Timed out")
            }
        }
    }
}

fun runShellCommandForResult(
    command: String,
    asRoot: Boolean = true,
    timeoutMs: Long = 30_000L
): ShellCommandResult {
    if (command.isBlank()) {
        return ShellCommandResult(command, -1, "", "Empty command")
    }
    return runCatching {
        executeShellCommand(command, asRoot, timeoutMs)
    }.getOrElse { error ->
        Log.e("RootUtils", "runShellCommandForResult failed: $command", error)
        if (asRoot) {
            runCatching { executeShellCommand(command, false, timeoutMs) }
                .getOrElse { fallbackError ->
                    ShellCommandResult(command, -1, "", error.message.orEmpty() + "\n" + fallbackError.message.orEmpty())
                }
        } else {
            ShellCommandResult(command, -1, "", error.message.orEmpty())
        }
    }
}

private fun executeShellCommand(command: String, asRoot: Boolean, timeoutMs: Long): ShellCommandResult {
    var process: Process? = null
    val stdout = StringBuilder()
    val stderr = StringBuilder()
    return try {
        val activeProcess = if (asRoot) Runtime.getRuntime().exec("su") else Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
        process = activeProcess
        val outThread = Thread { runCatching { activeProcess.inputStream.bufferedReader().use { stdout.append(it.readText()) } } }
        val errThread = Thread { runCatching { activeProcess.errorStream.bufferedReader().use { stderr.append(it.readText()) } } }
        outThread.start()
        errThread.start()

        if (asRoot) {
            DataOutputStream(activeProcess.outputStream).use { os ->
                os.writeBytes(command)
                os.writeBytes("\nexit\n")
                os.flush()
            }
        }

        val finished = activeProcess.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        if (!finished) {
            activeProcess.destroyForcibly()
        }
        outThread.join(500)
        errThread.join(500)
        ShellCommandResult(
            command = command,
            exitCode = if (finished) activeProcess.exitValue() else -1,
            output = stdout.toString(),
            error = stderr.toString(),
            timedOut = !finished
        )
    } finally {
        try { process?.destroy() } catch (ignored: Exception) {}
    }
}