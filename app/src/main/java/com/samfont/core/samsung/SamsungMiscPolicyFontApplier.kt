package com.samfont.core.samsung

import com.samfont.core.shizuku.ShellResult
import com.samfont.core.shizuku.ShizukuBridge

class SamsungMiscPolicyFontApplier {
    fun hasMiscPolicy(): AutoApplyResult {
        val result = ShizukuBridge.runShell("/system/bin/service list | /system/bin/grep misc_policy", timeoutSeconds = 60)
        return AutoApplyResult(
            applied = result.success && result.stdout.contains("misc_policy"),
            message = if (result.success && result.stdout.contains("misc_policy")) {
                "misc_policy supported"
            } else {
                "misc_policy not supported"
            },
            log = formatResult(result)
        )
    }

    fun readCurrentFontRaw(): ShellResult {
        return ShizukuBridge.runShell("/system/bin/service call misc_policy 6", timeoutSeconds = 60)
    }

    fun applyFont(displayName: String): ShellResult {
        // Samsung misc_policy 接收字体显示名，不是 packageName 或文件名。
        return ShizukuBridge.runShell(
            "/system/bin/service call misc_policy 5 i32 0 s16 ${ShizukuBridge.shellQuote(displayName)} i32 0",
            timeoutSeconds = 60
        )
    }

    fun applyAndVerify(displayName: String): AutoApplyResult {
        val log = StringBuilder()
        val support = hasMiscPolicy()
        log.appendLine(support.log)
        if (!support.applied) {
            return AutoApplyResult(
                applied = false,
                message = "misc_policy not supported",
                log = log.toString()
            )
        }

        val apply = applyFont(displayName)
        log.appendLine(formatResult(apply))
        if (!apply.success) {
            return AutoApplyResult(
                applied = false,
                message = "Installed but not applied: misc_policy apply failed",
                log = log.toString()
            )
        }

        Thread.sleep(800)
        val current = readCurrentFontRaw()
        log.appendLine(formatResult(current))
        val verified = current.success && current.stdout.contains(displayName)
        return AutoApplyResult(
            applied = verified,
            message = if (verified) {
                "Applied"
            } else {
                "Installed but not applied: Verification failed"
            },
            log = log.toString()
        )
    }

    private fun formatResult(result: ShellResult): String {
        return buildString {
            appendLine("$ ${result.command}")
            appendLine("exitCode=${result.exitCode}")
            if (result.stdout.isNotBlank()) appendLine("stdout:\n${result.stdout.trim()}")
            if (result.stderr.isNotBlank()) appendLine("stderr:\n${result.stderr.trim()}")
        }
    }
}
