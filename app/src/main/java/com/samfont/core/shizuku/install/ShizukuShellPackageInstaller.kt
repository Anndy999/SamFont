package com.samfont.core.shizuku.install

import com.samfont.core.font.FontRepository
import com.samfont.core.shizuku.ShizukuBridge
import com.samfont.core.shizuku.ShellResult
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ShizukuShellPackageInstaller : ShizukuPackageInstaller {
    override suspend fun installApk(apk: File, packageName: String): ShizukuInstallResult = withContext(Dispatchers.IO) {
        val log = StringBuilder()
        if (!apk.exists() || apk.length() <= 0) {
            return@withContext ShizukuInstallResult(false, packageName, "APK 文件不存在或为空", "apk=${apk.absolutePath}")
        }

        val hash = FontRepository.sha256(apk).take(16)
        val remotePath = "/data/local/tmp/samfont-$hash.apk"
        appendLog(log, ShizukuBridge.writeFileToRemoteTemp(apk, remotePath))

        if (log.containsFailureMarker()) {
            ShizukuBridge.runShell("rm -f ${ShizukuBridge.shellQuote(remotePath)}")
            return@withContext ShizukuInstallResult(false, packageName, "APK 写入 Shizuku 临时目录失败", log.toString())
        }

        val create = ShizukuBridge.runShell("pm install-create -r --user 0")
        appendLog(log, create)
        val sessionId = parseSessionId(create.stdout)
        if (!create.success || sessionId == null) {
            val fallback = fallbackInstall(packageName, remotePath, log)
            cleanup(remotePath, log)
            return@withContext fallback ?: ShizukuInstallResult(false, packageName, "Shizuku 安装 session 创建失败", log.toString())
        }

        val write = ShizukuBridge.runShell(
            "pm install-write -S ${apk.length()} $sessionId base.apk ${ShizukuBridge.shellQuote(remotePath)}"
        )
        appendLog(log, write)
        if (!write.success) {
            appendLog(log, ShizukuBridge.runShell("pm install-abandon $sessionId"))
            cleanup(remotePath, log)
            return@withContext ShizukuInstallResult(false, packageName, "APK 写入 session 失败", log.toString())
        }

        val commit = ShizukuBridge.runShell("pm install-commit $sessionId")
        appendLog(log, commit)
        if (!commit.success || !commit.stdout.contains("Success", ignoreCase = true)) {
            appendLog(log, ShizukuBridge.runShell("pm install-abandon $sessionId"))
            val fallback = fallbackInstall(packageName, remotePath, log)
            cleanup(remotePath, log)
            return@withContext fallback ?: ShizukuInstallResult(false, packageName, "APK commit 失败", log.toString())
        }

        cleanup(remotePath, log)
        val installed = isPackageInstalled(packageName)
        if (!installed) {
            return@withContext ShizukuInstallResult(false, packageName, "安装后 pm list packages 未发现目标包", log.toString())
        }

        ShizukuInstallResult(true, packageName, "字体包安装成功", log.toString())
    }

    override suspend fun isPackageInstalled(packageName: String): Boolean = withContext(Dispatchers.IO) {
        val result = ShizukuBridge.runShell("pm list packages ${ShizukuBridge.shellQuote(packageName)}")
        result.success && result.stdout.lineSequence().any { it.trim() == "package:$packageName" }
    }

    override suspend fun uninstallPackage(packageName: String): ShizukuInstallResult = withContext(Dispatchers.IO) {
        val result = ShizukuBridge.runShell("pm uninstall --user 0 ${ShizukuBridge.shellQuote(packageName)}")
        ShizukuInstallResult(
            success = result.success && result.stdout.contains("Success", ignoreCase = true),
            packageName = packageName,
            message = if (result.success) "字体包卸载命令已执行" else "字体包卸载失败",
            log = formatResult(result)
        )
    }

    private fun fallbackInstall(packageName: String, remotePath: String, log: StringBuilder): ShizukuInstallResult? {
        val fallback = ShizukuBridge.runShell("pm install -r --user 0 ${ShizukuBridge.shellQuote(remotePath)}")
        appendLog(log, fallback)
        return if (fallback.success && fallback.stdout.contains("Success", ignoreCase = true)) {
            ShizukuInstallResult(true, packageName, "字体包安装成功", log.toString())
        } else {
            null
        }
    }

    private fun cleanup(remotePath: String, log: StringBuilder) {
        appendLog(log, ShizukuBridge.runShell("rm -f ${ShizukuBridge.shellQuote(remotePath)}"))
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

    private fun StringBuilder.containsFailureMarker(): Boolean {
        return toString().contains("exitCode=-1") || toString().contains("exitCode=1")
    }

    companion object {
        fun parseSessionId(output: String): Int? {
            return Regex("""\[(\d+)]""").find(output)?.groupValues?.getOrNull(1)?.toIntOrNull()
        }
    }
}
