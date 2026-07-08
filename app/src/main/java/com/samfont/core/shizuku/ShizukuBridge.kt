package com.samfont.core.shizuku

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.ByteArrayOutputStream
import java.io.File
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

    fun runShell(command: String): ShellResult {
        return runRemoteProcess(command, null)
    }

    fun runShell(commands: List<String>): ShellResult {
        return runShell(commands.joinToString("\n"))
    }

    fun writeFileToRemoteTemp(localFile: File, remotePath: String): ShellResult {
        val write = runRemoteProcess("cat > ${shellQuote(remotePath)}", localFile)
        if (!write.success) return write
        val chmod = runShell("chmod 644 ${shellQuote(remotePath)}")
        return ShellResult(
            exitCode = chmod.exitCode,
            stdout = write.stdout + chmod.stdout,
            stderr = write.stderr + chmod.stderr,
            command = "write ${localFile.absolutePath} -> $remotePath\n${chmod.command}"
        )
    }

    private fun runRemoteProcess(command: String, stdinFile: File?): ShellResult {
        if (!pingBinder()) {
            return ShellResult(-1, "", "Shizuku 未运行", command)
        }
        if (!checkSelfPermission()) {
            return ShellResult(-1, "", "Shizuku 未授权", command)
        }

        return runCatching {
            val process = newRemoteProcess(arrayOf("sh", "-c", command))
            val stdout = ByteArrayOutputStream()
            val stderr = ByteArrayOutputStream()
            val outThread = Thread { process.inputStream.use { it.copyTo(stdout) } }
            val errThread = Thread { process.errorStream.use { it.copyTo(stderr) } }
            outThread.start()
            errThread.start()

            process.outputStream.use { output ->
                stdinFile?.inputStream()?.use { input -> input.copyTo(output) }
            }

            val finished = process.waitFor(60, TimeUnit.SECONDS)
            if (!finished) {
                process.destroy()
                return ShellResult(-1, stdout.toString(), "命令超时", command)
            }
            outThread.join(1_000)
            errThread.join(1_000)
            ShellResult(
                exitCode = process.exitValue(),
                stdout = stdout.toString(Charsets.UTF_8.name()),
                stderr = stderr.toString(Charsets.UTF_8.name()),
                command = command
            )
        }.getOrElse { throwable ->
            ShellResult(-1, "", throwable.message ?: throwable.toString(), command)
        }
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
