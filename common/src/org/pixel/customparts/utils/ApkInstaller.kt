package org.pixel.customparts.utils

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.util.Log
import kotlin.coroutines.cancellation.CancellationException
import org.pixel.customparts.AppConfig
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object ApkInstaller {

    private const val TAG = "ApkInstaller"
    // Hidden PackageManager flag; the system build is allowed to request this explicitly.
    private const val INSTALL_DISABLE_VERIFICATION = 0x00080000

    fun interface LogCallback {
        fun onLog(line: String)
    }

    fun install(
        context: Context,
        apkPath: String,
        packageName: String,
        logCallback: LogCallback? = null,
        isCancelled: (() -> Boolean)? = null
    ): Boolean {
        val log = fun(msg: String) {
            logCallback?.onLog(msg)
            Log.d(TAG, msg)
        }
        fun checkCancelled() {
            if (isCancelled?.invoke() == true) throw CancellationException()
        }
        val apkFile = File(apkPath)
        if (!apkFile.exists()) {
            log("APK file not found: $apkPath")
            return false
        }

        var session: PackageInstaller.Session? = null
        var handlerThread: android.os.HandlerThread? = null
        var receiver: BroadcastReceiver? = null
        var receiverRegistered = false
        try {
            checkCancelled()
            log("Installing via native PackageInstaller API...")

            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setSize(apkFile.length())
                setAppPackageName(packageName)
                setPackageSource(PackageInstaller.PACKAGE_SOURCE_OTHER)
                if (android.os.Build.VERSION.SDK_INT >= 31) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
            }

            try {
                val installFlagsField = params.javaClass.getDeclaredField("installFlags")
                installFlagsField.isAccessible = true
                var flags = installFlagsField.getInt(params)
                flags = flags or INSTALL_DISABLE_VERIFICATION
                installFlagsField.setInt(params, flags)
                log("Requested INSTALL_DISABLE_VERIFICATION for system install")
            } catch (e: Exception) {
                log("Failed to request verification bypass: ${e.message}")
                return if (AppConfig.NEEDS_ROOT_ACCESS) {
                    fallbackSuInstall(apkPath, log)
                } else {
                    false
                }
            }

            try {
                val forceQueryableMethod = params.javaClass.getMethod("setForceQueryable")
                forceQueryableMethod.invoke(params)
                log("Marked APK as force-queryable")
            } catch (e: Exception) {
                log("Notice: Failed to mark APK force-queryable: ${e.message}")
            }

            val sessionId = packageInstaller.createSession(params)
            session = packageInstaller.openSession(sessionId)

            apkFile.inputStream().use { input ->
                session!!.openWrite("package", 0, apkFile.length()).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        checkCancelled()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                    session!!.fsync(output)
                }
            }

            val countDownLatch = CountDownLatch(1)
            var installSuccess = false
            var installMsg = ""

            val action = "org.pixel.customparts.INSTALL_COMMIT_${System.currentTimeMillis()}"
            val installReceiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                    val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "No message"
                    log("Broadcast received! Status: $status, Msg: $msg")

                    if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                        log("Install requires user action; confirmation UI will not be launched")
                        installMsg = msg
                        installSuccess = false
                        countDownLatch.countDown()
                        return
                    }

                    installMsg = msg
                    installSuccess = (status == PackageInstaller.STATUS_SUCCESS)
                    countDownLatch.countDown()
                }
            }
            receiver = installReceiver

            handlerThread = android.os.HandlerThread("InstallReceiverThread").apply { start() }
            val handler = android.os.Handler(handlerThread!!.looper)

            context.registerReceiver(
                installReceiver,
                IntentFilter(action),
                null,
                handler,
                Context.RECEIVER_EXPORTED
            )
            receiverRegistered = true

            val intent = Intent(action).setPackage(context.packageName)
            intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                sessionId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            checkCancelled()
            session!!.commit(pendingIntent.intentSender)
            val installDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60)
            while (!countDownLatch.await(100, TimeUnit.MILLISECONDS)) {
                checkCancelled()
                if (System.nanoTime() >= installDeadline) break
            }

            if (installSuccess) {
                log("Install successful!")
                return true
            } else {
                log("API install failed. Status: $installMsg")
            }

            return if (AppConfig.NEEDS_ROOT_ACCESS) {
                fallbackSuInstall(apkPath, log)
            } else {
                false
            }
        } catch (e: CancellationException) {
            log("Install cancelled")
            runCatching { session?.abandon() }
            throw e
        } catch (e: Exception) {
            if (isCancelled?.invoke() == true) {
                runCatching { session?.abandon() }
                throw CancellationException()
            }
            log("Install exception: ${e.message}")
            return if (AppConfig.NEEDS_ROOT_ACCESS) {
                fallbackSuInstall(apkPath, log)
            } else {
                false
            }
        } finally {
            receiver?.let { registeredReceiver ->
                if (receiverRegistered) {
                    runCatching { context.unregisterReceiver(registeredReceiver) }
                }
            }
            handlerThread?.quitSafely()
            session?.close()
        }
    }

    fun uninstall(context: Context, packageName: String, logCallback: LogCallback? = null): Boolean {
        val log = fun(msg: String) {
            logCallback?.onLog(msg)
            Log.d(TAG, msg)
        }
        try {
            log("Uninstalling via native PackageInstaller API: $packageName")

            val packageInstaller = context.packageManager.packageInstaller
            val countDownLatch = CountDownLatch(1)
            var uninstallSuccess = false
            var uninstallMsg = ""

            val action = "org.pixel.customparts.UNINSTALL_COMMIT_${System.currentTimeMillis()}"
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                    val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "No message"
                    log("Uninstall broadcast received! Status: $status, Msg: $msg")

                    if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                        log("Uninstall requires user action; confirmation UI will not be launched")
                        uninstallMsg = msg
                        uninstallSuccess = false
                        countDownLatch.countDown()
                        return
                    }

                    uninstallMsg = msg
                    uninstallSuccess = (status == PackageInstaller.STATUS_SUCCESS)
                    countDownLatch.countDown()
                }
            }

            val handlerThread = android.os.HandlerThread("UninstallReceiverThread").apply { start() }
            val handler = android.os.Handler(handlerThread.looper)

            context.registerReceiver(
                receiver,
                IntentFilter(action),
                null,
                handler,
                Context.RECEIVER_EXPORTED
            )

            val intent = Intent(action).setPackage(context.packageName)
            intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                1001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            packageInstaller.uninstall(packageName, pendingIntent.intentSender)
            countDownLatch.await(60, TimeUnit.SECONDS)

            context.unregisterReceiver(receiver)
            handlerThread.quitSafely()

            if (uninstallSuccess) {
                log("Uninstall successful (via API).")
                return true
            } else {
                log("API uninstall failed. Status: $uninstallMsg")
            }

            if (AppConfig.NEEDS_ROOT_ACCESS) {
                try {
                    val suResult = runCommand(arrayOf("su", "-c", "cmd package uninstall $packageName"))
                    if (suResult.first.contains("Success")) {
                        log("SU uninstall successful.")
                        return true
                    }
                    log("SU uninstall failed. Output: ${suResult.first}")
                } catch (e: Exception) {
                    log("SU fallback not available: ${e.message}")
                }
            }

            return false
        } catch (e: Exception) {
            log("Uninstall exception: ${e.message}")
            return false
        }
    }

    private fun fallbackSuInstall(apkPath: String, log: (String) -> Unit): Boolean {
        log("Trying fallback to su...")
        try {
            val suResult = runCommand(arrayOf(
                "su", "-c", "cmd package install --skip-verification --force-queryable -r -d -t ${shellQuote(apkPath)}"
            ))
            if (suResult.first.contains("Success")) {
                log("SU install successful.")
                return true
            }
            log("SU install failed. Output: ${suResult.first}")
        } catch (e: Exception) {
            log("SU fallback not available: ${e.message}")
        }
        return false
    }

    private fun shellQuote(value: String): String {
        return "'${value.replace("'", "'\\''")}'"
    }

    private fun runCommand(cmd: Array<String>, workDir: File? = null): Pair<String, String> {
        val process = ProcessBuilder(*cmd)
            .directory(workDir)
            .redirectErrorStream(true)
            .start()

        process.outputStream.close()

        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        return Pair(output.trim(), "")
    }
}
