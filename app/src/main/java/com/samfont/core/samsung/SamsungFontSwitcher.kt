package com.samfont.core.samsung

import com.samfont.core.shizuku.ShellResult
import com.samfont.core.shizuku.ShizukuBridge

data class AutoApplyResult(
    val applied: Boolean,
    val message: String,
    val log: String
)

class SamsungFontSwitcher {
    fun tryApplyInstalledFont(packageName: String): AutoApplyResult {
        val log = StringBuilder()
        val system = ShizukuBridge.runShell("/system/bin/settings list system", timeoutSeconds = 60)
        appendLog(log, system)
        val secure = ShizukuBridge.runShell("/system/bin/settings list secure", timeoutSeconds = 60)
        appendLog(log, secure)
        val global = ShizukuBridge.runShell("/system/bin/settings list global", timeoutSeconds = 60)
        appendLog(log, global)

        val candidates = listOf(system, secure, global)
            .flatMap { it.stdout.lineSequence().toList() }
            .map { it.trim() }
            .filter { line -> fontKeywords.any { keyword -> line.contains(keyword, ignoreCase = true) } }

        log.appendLine("Font switch candidate keys:")
        if (candidates.isEmpty()) {
            log.appendLine("(none)")
        } else {
            candidates.forEach { log.appendLine(it) }
        }
        log.appendLine("Target package: $packageName")

        return AutoApplyResult(
            applied = false,
            message = "字体包已安装，但未识别到当前系统字体切换 key。",
            log = log.toString()
        )
    }

    private fun appendLog(log: StringBuilder, result: ShellResult) {
        log.appendLine("$ ${result.command}")
        log.appendLine("exitCode=${result.exitCode}")
        if (result.stdout.isNotBlank()) log.appendLine("stdout:\n${result.stdout.trim()}")
        if (result.stderr.isNotBlank()) log.appendLine("stderr:\n${result.stderr.trim()}")
    }

    private companion object {
        val fontKeywords = listOf(
            "font",
            "flip",
            "flipfont",
            "typeface",
            "monotype",
            "theme",
            "sans",
            "droid"
        )
    }
}
