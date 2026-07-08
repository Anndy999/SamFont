package com.samfont.core.shizuku

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

object ShizukuBridge {
    const val REQUEST_CODE = 6201

    fun check(): ShizukuStatus {
        return runCatching {
            if (!pingBinder()) {
                return ShizukuStatus(
                    available = false,
                    permissionGranted = false,
                    uid = null,
                    source = ShizukuSource.NOT_RUNNING,
                    message = "Shizuku 未运行"
                )
            }

            val permissionGranted = checkSelfPermission()
            val uid = getShizukuUid()
            val source = when (uid) {
                1000 -> ShizukuSource.SYSTEM_UID
                0 -> ShizukuSource.ROOT
                2000 -> ShizukuSource.ADB_SHELL
                null -> ShizukuSource.UNKNOWN
                else -> ShizukuSource.UNKNOWN
            }
            val message = when {
                !permissionGranted -> "Shizuku 可用，但未授权"
                source == ShizukuSource.SYSTEM_UID -> "Shizuku UID1000 模式"
                source == ShizukuSource.ROOT -> "Shizuku ROOT 模式"
                source == ShizukuSource.ADB_SHELL -> "Shizuku ADB shell 模式"
                else -> "Shizuku UID: ${uid ?: "未知"}"
            }

            ShizukuStatus(
                available = true,
                permissionGranted = permissionGranted,
                uid = uid,
                source = if (permissionGranted) source else ShizukuSource.DENIED,
                message = message
            )
        }.getOrElse { throwable ->
            ShizukuStatus(
                available = false,
                permissionGranted = false,
                uid = null,
                source = ShizukuSource.UNAVAILABLE,
                message = throwable.message ?: "Shizuku API 不可用"
            )
        }
    }

    fun pingBinder(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun checkSelfPermission(): Boolean {
        return runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
    }

    fun getShizukuUid(): Int? = runCatching { Shizuku.getUid() }.getOrNull()

    fun requestPermission() {
        if (pingBinder()) {
            Shizuku.requestPermission(REQUEST_CODE)
        }
    }

    fun runShell(command: String, timeoutSeconds: Long = 60): ShellResult {
        return runRemoteProcess(command, null, timeoutSeconds)
    }

    fun runShell(commands: List<String>, timeoutSeconds: Long = 60): ShellResult {
        return runShell(commands.joinToString("\n"), timeoutSeconds)
    }

    fun runShellWithInput(
        command: String,
        inputFile: File,
        timeoutSeconds: Long = 300
    ): ShellResult {
        return runRemoteProcess(command, inputFile, timeoutSeconds)
    }

    private fun runRemoteProcess(
        command: String,
        stdinFile: File?,
        timeoutSeconds: Long
    ): ShellResult {
        if (!pingBinder()) {
            return ShellResult(-1, "", "Shizuku 未运行", command)
        }
        if (!checkSelfPermission()) {
            return ShellResult(-1, "", "Shizuku 未授权", command)
        }

        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val stdinErrors = StringBuilder()
        val process = runCatching {
            newRemoteProcess(arrayOf("/system/bin/sh", "-c", command))
        }.getOrElse { throwable ->
            return ShellResult(-1, "", throwable.stackTraceToString(), command)
        }

        return runCatching {
            val outThread = Thread { process.inputStream.use { it.copyTo(stdout) } }
            val errThread = Thread { process.errorStream.use { it.copyTo(stderr) } }
            outThread.start()
            errThread.start()

            try {
                process.outputStream.use { output ->
                    stdinFile?.inputStream()?.use { input -> input.copyTo(output) }
                }
            } catch (exception: IOException) {
                stdinErrors.appendLine("stdin write failed: ${exception.message}")
                stdinErrors.appendLine(exception.stackTraceToString())
            } catch (exception: IllegalArgumentException) {
                stdinErrors.appendLine("stdin write failed: ${exception.message}")
                stdinErrors.appendLine(exception.stackTraceToString())
            } catch (exception: IllegalThreadStateException) {
                stdinErrors.appendLine("stdin write failed: ${exception.message}")
                stdinErrors.appendLine(exception.stackTraceToString())
            }

            val waitResult = WaitResult()
            val waitThread = Thread {
                runCatching { process.waitFor() }
                    .onSuccess { waitResult.exitCode = it }
                    .onFailure {
                        waitResult.error = it
                        waitResult.exitCode = -1
                    }
                waitResult.finished = true
            }
            waitThread.start()
            waitThread.join(TimeUnit.SECONDS.toMillis(timeoutSeconds))

            if (!waitResult.finished) {
                process.destroy()
                waitThread.join(3_000)
                outThread.join(3_000)
                errThread.join(3_000)
                return ShellResult(
                    exitCode = -1,
                    stdout = stdout.toString(Charsets.UTF_8.name()),
                    stderr = buildString {
                        appendLine("timeout after ${timeoutSeconds}s")
                        if (stdinErrors.isNotBlank()) append(stdinErrors)
                        val processStderr = stderr.toString(Charsets.UTF_8.name())
                        if (processStderr.isNotBlank()) append(processStderr)
                    },
                    command = command
                )
            }
            outThread.join(3_000)
            errThread.join(3_000)
            waitResult.error?.let {
                stdinErrors.appendLine("waitFor failed: ${it.message}")
                stdinErrors.appendLine(it.stackTraceToString())
            }
            ShellResult(
                exitCode = waitResult.exitCode,
                stdout = stdout.toString(Charsets.UTF_8.name()),
                stderr = buildString {
                    if (stdinErrors.isNotBlank()) append(stdinErrors)
                    val processStderr = stderr.toString(Charsets.UTF_8.name())
                    if (processStderr.isNotBlank()) append(processStderr)
                },
                command = command
            )
        }.getOrElse { throwable ->
            ShellResult(
                exitCode = -1,
                stdout = stdout.toString(Charsets.UTF_8.name()),
                stderr = buildString {
                    if (stdinErrors.isNotBlank()) append(stdinErrors)
                    append(throwable.stackTraceToString())
                },
                command = command
            )
        }
    }

    private class WaitResult {
        @Volatile
        var finished: Boolean = false
        @Volatile
        var exitCode: Int = -1
        @Volatile
        var error: Throwable? = null
    }

    private fun newRemoteProcess(command: Array<String>): Process {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        method.isAccessible = true
        return method.invoke(null, command, null, null) as Process
    }

    fun shellQuote(value: String): String {
        return "'" + value.replace("'", "'\"'\"'") + "'"
    }
}
