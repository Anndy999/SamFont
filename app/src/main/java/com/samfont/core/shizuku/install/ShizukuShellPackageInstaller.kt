package com.samfont.core.shizuku.install

import com.samfont.core.shizuku.ShellResult
import com.samfont.core.shizuku.ShizukuBridge
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ShizukuShellPackageInstaller : ShizukuPackageInstaller {
    override suspend fun installApk(apk: File, packageName: String): ShizukuInstallResult = withContext(Dispatchers.IO) {
        val log = StringBuilder()
        if (!apk.exists() || apk.length() <= 0) {
            return@withContext ShizukuInstallResult(false, packageName, "APK 文件不存在或为空", "apk=${apk.absolutePath}")
        }

        val firstAttempt = installWithSession(apk, packageName, log)
        val retryAttempt = if (!firstAttempt.success && shouldRetryAfterSignatureMismatch(firstAttempt.log)) {
            appendLog(log, ShizukuBridge.runShell("/system/bin/pm uninstall --user 0 ${ShizukuBridge.shellQuote(packageName)}", timeoutSeconds = 300))
            installWithSession(apk, packageName, log)
        } else {
            firstAttempt
        }

        if (!retryAttempt.success) {
            appendLog(
                log,
                ShizukuBridge.runShellWithInput(
                    command = "/system/bin/pm install -r --user 0 -S ${apk.length()} -",
                    inputFile = apk,
                    timeoutSeconds = 300
                )
            )
        }

        return@withContext if (verifyInstalled(packageName, log)) {
            ShizukuInstallResult(true, packageName, "字体包安装成功", log.toString())
        } else {
            ShizukuInstallResult(false, packageName, retryAttempt.message, log.toString())
        }
    }

    override suspend fun isPackageInstalled(packageName: String): Boolean = withContext(Dispatchers.IO) {
        val log = StringBuilder()
        verifyInstalled(packageName, log)
    }

    override suspend fun uninstallPackage(packageName: String): ShizukuInstallResult = withContext(Dispatchers.IO) {
        val result = ShizukuBridge.runShell("/system/bin/pm uninstall --user 0 ${ShizukuBridge.shellQuote(packageName)}", timeoutSeconds = 300)
        ShizukuInstallResult(
            success = result.success && hasSuccess(result),
            packageName = packageName,
            message = if (result.success) "字体包卸载命令已执行" else "字体包卸载失败",
            log = formatResult(result)
        )
    }

    private fun installWithSession(
        apk: File,
        packageName: String,
        log: StringBuilder
    ): ShizukuInstallResult {
        val create = ShizukuBridge.runShell("/system/bin/pm install-create -r --user 0", timeoutSeconds = 300)
        appendLog(log, create)
        val sessionId = parseSessionId(create.stdout + "\n" + create.stderr)
        if (!create.success || sessionId == null) {
            return ShizukuInstallResult(false, packageName, "Shizuku 安装 session 创建失败", log.toString())
        }

        val write = ShizukuBridge.runShellWithInput(
            command = "/system/bin/pm install-write -S ${apk.length()} $sessionId base.apk -",
            inputFile = apk,
            timeoutSeconds = 300
        )
        appendLog(log, write)
        if (!write.success) {
            appendLog(log, ShizukuBridge.runShell("/system/bin/pm install-abandon $sessionId", timeoutSeconds = 60))
            return ShizukuInstallResult(false, packageName, "APK 写入 session 失败", log.toString())
        }

        val commit = ShizukuBridge.runShell("/system/bin/pm install-commit $sessionId", timeoutSeconds = 300)
        appendLog(log, commit)
        if (!commit.success || !hasSuccess(commit)) {
            appendLog(log, ShizukuBridge.runShell("/system/bin/pm install-abandon $sessionId", timeoutSeconds = 60))
            return ShizukuInstallResult(false, packageName, "APK commit 失败", log.toString())
        }

        return ShizukuInstallResult(true, packageName, "字体包安装成功", log.toString())
    }

    private fun verifyInstalled(packageName: String, log: StringBuilder): Boolean {
        val list = ShizukuBridge.runShell("/system/bin/pm list packages ${ShizukuBridge.shellQuote(packageName)}", timeoutSeconds = 60)
        appendLog(log, list)
        val path = ShizukuBridge.runShell("/system/bin/pm path ${ShizukuBridge.shellQuote(packageName)}", timeoutSeconds = 60)
        appendLog(log, path)

        return list.success &&
            path.success &&
            list.stdout.lineSequence().any { it.trim() == "package:$packageName" } &&
            path.stdout.lineSequence().any { it.trim().startsWith("package:/data/app/") }
    }

    private fun appendLog(log: StringBuilder, result: ShellResult) {
        log.appendLine(formatResult(result))
    }

    private fun formatResult(result: ShellResult): String {
        return buildString {
            appendLine("$ ${result.command}")
            appendLine("exitCode=${result.exitCode}")
            if (result.stdout.isNotBlank()) appendLine("stdout:\n${result.stdout.trim()}")
            if (result.stderr.isNotBlank()) appendLine("stderr:\n${result.stderr.trim()}")
        }
    }

    private fun hasSuccess(result: ShellResult): Boolean {
        return result.stdout.contains("Success", ignoreCase = true) ||
            result.stderr.contains("Success", ignoreCase = true)
    }

    companion object {
        fun parseSessionId(output: String): Int? {
            return Regex("""\[(\d+)]""").find(output)?.groupValues?.getOrNull(1)?.toIntOrNull()
        }

        fun shouldRetryAfterSignatureMismatch(log: String): Boolean {
            return log.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE", ignoreCase = true) ||
                log.contains("signatures do not match", ignoreCase = true) ||
                log.contains("UPDATE_INCOMPATIBLE", ignoreCase = true) ||
                log.contains("existing package", ignoreCase = true)
        }
    }
}
