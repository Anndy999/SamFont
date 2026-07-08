package com.samfont.core.samsung

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.samfont.core.shizuku.ShizukuBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class VerificationResult(
    val installed: Boolean,
    val visibleToSamsung: Boolean?,
    val currentlyApplied: Boolean?,
    val message: String,
    val log: String
)

class SamsungFontVerifier {
    suspend fun verifyPackageInstalled(packageName: String): VerificationResult = withContext(Dispatchers.IO) {
        val log = StringBuilder()
        val list = ShizukuBridge.runShell("pm list packages ${ShizukuBridge.shellQuote(packageName)}")
        append(log, list.command, list.exitCode, list.stdout, list.stderr)
        val path = ShizukuBridge.runShell("pm path ${ShizukuBridge.shellQuote(packageName)}")
        append(log, path.command, path.exitCode, path.stdout, path.stderr)
        val dumpsys = ShizukuBridge.runShell("dumpsys package ${ShizukuBridge.shellQuote(packageName)} | head -n 80")
        append(log, dumpsys.command, dumpsys.exitCode, dumpsys.stdout, dumpsys.stderr)

        val installed = list.success && list.stdout.lineSequence().any { it.trim() == "package:$packageName" } &&
            path.success && path.stdout.lineSequence().any { it.trim().startsWith("package:/data/app/") }

        VerificationResult(
            installed = installed,
            visibleToSamsung = null,
            currentlyApplied = null,
            message = if (installed) {
                "字体包已安装，请到 Samsung 系统字体设置中选择该字体"
            } else {
                "字体包安装后验证失败"
            },
            log = log.toString()
        )
    }

    fun openSamsungFontSettingsIntent(context: Context): Intent {
        val samsungIntent = Intent("com.samsung.settings.FONT_STYLE_SETTINGS").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (samsungIntent.resolveActivity(context.packageManager) != null) {
            samsungIntent
        } else {
            Intent(Settings.ACTION_DISPLAY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    private fun append(log: StringBuilder, command: String, exitCode: Int, stdout: String, stderr: String) {
        log.appendLine("$ $command")
        log.appendLine("exitCode=$exitCode")
        if (stdout.isNotBlank()) log.appendLine("stdout:\n${stdout.trim()}")
        if (stderr.isNotBlank()) log.appendLine("stderr:\n${stderr.trim()}")
    }
}
