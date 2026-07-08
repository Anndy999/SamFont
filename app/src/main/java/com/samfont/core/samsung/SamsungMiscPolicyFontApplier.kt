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

    fun applyFont(fontName: String): ShellResult {
        // misc_policy 实际读取/返回的是 Samsung 字体 XML 中的 droidname/internal name。
        return ShizukuBridge.runShell(
            "/system/bin/service call misc_policy 5 i32 0 s16 ${ShizukuBridge.shellQuote(fontName)} i32 0",
            timeoutSeconds = 60
        )
    }

    fun applyAndVerify(
        generatedPackage: SamsungFontGeneratedPackage,
        mode: SamsungFontApplyMode
    ): AutoApplyResult {
        val log = StringBuilder()
        log.appendLine("Font display name: ${generatedPackage.displayName}")
        log.appendLine("Font droid name: ${generatedPackage.spec.droidName}")
        log.appendLine("Font package name: ${generatedPackage.packageName}")
        log.appendLine("Font apply mode: ${mode.label}")
        val support = hasMiscPolicy()
        log.appendLine(support.log)
        if (!support.applied) {
            return AutoApplyResult(
                applied = false,
                message = "misc_policy not supported",
                log = log.toString()
            )
        }

        val candidates = applyCandidates(generatedPackage, mode)
        candidates.forEach { candidate ->
            log.appendLine("Trying misc_policy font value: $candidate")
            val apply = applyFont(candidate)
            log.appendLine(formatResult(apply))
            if (apply.success) {
                Thread.sleep(800)
                val current = readCurrentFontRaw()
                log.appendLine(formatResult(current))
                if (current.success && current.stdout.contains(candidate)) {
                    return AutoApplyResult(
                        applied = true,
                        message = "Applied",
                        log = log.toString()
                    )
                }
            }
        }

        return AutoApplyResult(
            applied = false,
            message = "Installed but not applied: Verification failed",
            log = log.toString()
        )
    }

    private fun applyCandidates(
        generatedPackage: SamsungFontGeneratedPackage,
        mode: SamsungFontApplyMode
    ): List<String> {
        val fileNameWithoutExtension = generatedPackage.spec.fontFileName.substringBeforeLast('.')
        val candidates = when (mode) {
            SamsungFontApplyMode.Auto -> listOf(
                generatedPackage.spec.droidName,
                generatedPackage.displayName,
                generatedPackage.packageName,
                fileNameWithoutExtension
            )
            SamsungFontApplyMode.DroidName -> listOf(generatedPackage.spec.droidName)
            SamsungFontApplyMode.DisplayName -> listOf(generatedPackage.displayName)
            SamsungFontApplyMode.PackageName -> listOf(generatedPackage.packageName)
            SamsungFontApplyMode.FileName -> listOf(fileNameWithoutExtension)
        }
        return candidates.map { it.trim() }.filter { it.isNotBlank() }.distinct()
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
