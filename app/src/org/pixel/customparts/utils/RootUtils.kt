package org.pixel.customparts.utils

import android.util.Log
import java.io.DataOutputStream
import java.util.concurrent.TimeUnit

/**
 * Execute a shell command via su (root).
 * Used for privileged operations that require root access.
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