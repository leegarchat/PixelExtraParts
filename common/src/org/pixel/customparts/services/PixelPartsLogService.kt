package org.pixel.customparts.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import org.pixel.customparts.utils.PixelPartsLogController
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class PixelPartsLogService : Service() {
    private val workers = mutableMapOf<LogStream, CommandLogWorker>()
    private var sessionDirectory: File? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        appendServiceLog("Pixel Extra Parts logging service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!PixelPartsLogController.isServiceEnabled(this)) {
            stopAllWorkers()
            stopSelf()
            return START_NOT_STICKY
        }

        syncWorkers()
        return START_STICKY
    }

    override fun onDestroy() {
        stopAllWorkers()
        appendServiceLog("Pixel Extra Parts logging service destroyed")
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun syncWorkers() {
        updateWorker(
            stream = LogStream.LOGCAT,
            enabled = PixelPartsLogController.isLogcatEnabled(this),
            fileName = "logcat.log",
            command = listOf("logcat", "-b", "main", "-b", "system", "-b", "events", "-v", "threadtime")
        )
        updateWorker(
            stream = LogStream.DMESG,
            enabled = PixelPartsLogController.isDmesgEnabled(this),
            fileName = "dmesg.log",
            command = listOf("dmesg", "-w")
        )
        updateWorker(
            stream = LogStream.APP_CRASHES,
            enabled = PixelPartsLogController.isCrashesEnabled(this),
            fileName = "app-crashes.log",
            command = listOf(
                "logcat",
                "-b", "crash",
                "-b", "main",
                "-b", "system",
                "-b", "events",
                "-v", "threadtime",
                "AndroidRuntime:E",
                "ActivityManager:W",
                "ActivityTaskManager:W",
                "am_crash:I",
                "am_anr:I",
                "wm_crash:I",
                "DEBUG:E",
                "libc:F",
                "Process:E",
                "Pine:E",
                "PineEnhances:E",
                "PineXposed:E",
                "Xposed:E",
                "LSPosed:E",
                "PixelParts:E",
                "*:S"
            )
        )

        if (workers.isEmpty()) {
            appendServiceLog("Logging service is running with no active log streams")
        }
    }

    private fun updateWorker(stream: LogStream, enabled: Boolean, fileName: String, command: List<String>) {
        val existingWorker = workers[stream]
        if (enabled) {
            if (existingWorker == null) {
                val worker = CommandLogWorker(
                    service = this,
                    stream = stream,
                    fileName = fileName,
                    command = command
                )
                workers[stream] = worker
                worker.start()
                appendServiceLog("Started ${stream.displayName}")
            }
        } else if (existingWorker != null) {
            existingWorker.stop()
            workers.remove(stream)
            appendServiceLog("Stopped ${stream.displayName}")
        }
    }

    private fun stopAllWorkers() {
        workers.values.forEach { it.stop() }
        workers.clear()
    }

    fun outputFile(fileName: String): File {
        val directory = getOrCreateSessionDirectory()
        if (!directory.exists()) {
            directory.mkdirs()
        }
        return File(directory, fileName)
    }

    fun appendServiceLog(message: String) {
        runCatching {
            val output = outputFile("service.log")
            output.parentFile?.mkdirs()
            output.appendText("${timestamp()} $message\n")
        }.onFailure { throwable ->
            Log.w(TAG, "Failed to write service log", throwable)
        }
    }

    private fun getOrCreateSessionDirectory(): File {
        sessionDirectory?.let { return it }

        val sessionName = SimpleDateFormat(SESSION_DIR_PATTERN, Locale.US).format(Date())
        val requestedDirectory = File(PixelPartsLogController.LOG_ROOT_PATH, sessionName)
        sessionDirectory = if (requestedDirectory.mkdirs() || requestedDirectory.isDirectory) {
            requestedDirectory
        } else {
            val fallbackRoot = getExternalFilesDir(null) ?: filesDir
            File(fallbackRoot, "PixelExtraPartsLogs/$sessionName").apply { mkdirs() }
        }
        return sessionDirectory!!
    }

    private enum class LogStream(val displayName: String) {
        LOGCAT("logcat"),
        DMESG("dmesg"),
        APP_CRASHES("app crash log")
    }

    private class CommandLogWorker(
        private val service: PixelPartsLogService,
        private val stream: LogStream,
        private val fileName: String,
        private val command: List<String>
    ) {
        @Volatile private var running = false
        @Volatile private var process: Process? = null
        private var thread: Thread? = null

        fun start() {
            if (running) return
            running = true
            thread = Thread(::runLoop, "PixelParts-${stream.name}").apply { start() }
        }

        fun stop() {
            running = false
            process?.destroy()
            runCatching { process?.waitFor(1, TimeUnit.SECONDS) }
            process?.destroyForcibly()
            thread?.interrupt()
            thread = null
        }

        private fun runLoop() {
            val output = service.outputFile(fileName)
            writeHeader(output)

            while (running) {
                runCatching { runCommand(output) }
                    .onFailure { throwable ->
                        appendLine(output, "${timestamp()} ${stream.displayName} failed: ${throwable.message}")
                    }

                if (running) {
                    SystemClock.sleep(RESTART_DELAY_MS)
                    appendLine(output, "${timestamp()} restarting ${stream.displayName}")
                }
            }

            appendLine(output, "${timestamp()} stopped ${stream.displayName}")
        }

        private fun runCommand(output: File) {
            output.parentFile?.mkdirs()
            val processBuilder = ProcessBuilder(command).redirectErrorStream(true)
            val activeProcess = processBuilder.start()
            process = activeProcess

            FileOutputStream(output, true).bufferedWriter().use { writer ->
                activeProcess.inputStream.bufferedReader().use { reader ->
                    var lineCount = 0
                    while (running) {
                        val line = reader.readLine() ?: break
                        writer.write(line)
                        writer.newLine()
                        lineCount++
                        if (lineCount % FLUSH_LINE_COUNT == 0) {
                            writer.flush()
                        }
                    }
                }
                writer.flush()
            }

            val exitCode = runCatching { activeProcess.waitFor() }.getOrDefault(-1)
            process = null
            if (running) {
                appendLine(output, "${timestamp()} ${stream.displayName} exited with code $exitCode")
            }
        }

        private fun writeHeader(output: File) {
            appendLine(output, "")
            appendLine(output, "=== ${stream.displayName} started at ${timestamp()} ===")
            appendLine(output, "Command: ${command.joinToString(" ")}")
        }

        private fun appendLine(output: File, line: String) {
            runCatching {
                output.parentFile?.mkdirs()
                output.appendText("$line\n")
            }.onFailure { throwable ->
                Log.w(TAG, "Failed to append ${stream.displayName} output", throwable)
            }
        }
    }

    companion object {
        @Volatile var isRunning: Boolean = false
            private set

        private const val TAG = "PixelPartsLogService"
        private const val SESSION_DIR_PATTERN = "yyyy-MM-dd_HH-mm-ss"
        private const val RESTART_DELAY_MS = 5000L
        private const val FLUSH_LINE_COUNT = 20

        private fun timestamp(): String {
            return SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        }
    }
}