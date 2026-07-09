package com.samfont.core.samsung

import com.samfont.core.shizuku.ShellResult
import com.samfont.core.shizuku.ShizukuBridge

data class AutoApplyResult(
    val applied: Boolean,
    val message: String,
    val log: String
)

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

    fun applyFontShell(fontName: String): AutoApplyResult {
        val log = StringBuilder()
        val support = hasMiscPolicy()
        log.appendLine(support.log)
        if (!support.applied) {
            return AutoApplyResult(false, "misc_policy not supported", log.toString())
        }

        val apply = ShizukuBridge.runShell(
            "/system/bin/service call misc_policy 5 i32 0 s16 ${ShizukuBridge.shellQuote(fontName)} i32 0",
            timeoutSeconds = 60
        )
        log.appendLine(formatResult(apply))
        if (!apply.success || apply.stdout.hasFailureMarker() || apply.stderr.hasFailureMarker()) {
            return AutoApplyResult(false, "Installed but not applied", log.toString())
        }

        repeat(6) { attempt ->
            Thread.sleep((attempt + 1) * 250L)
            val current = readCurrentFontRaw()
            val activeName = parseParcelString(current.stdout)
            log.appendLine(formatResult(current))
            log.appendLine("expectedName=$fontName")
            log.appendLine("activeName=${activeName ?: "null"}")
            if (fontNamesMatch(activeName, fontName)) {
                return AutoApplyResult(true, "Applied", log.toString())
            }
        }

        return AutoApplyResult(false, "Installed but not applied", log.toString())
    }

    fun readCurrentFontRaw(): ShellResult {
        return ShizukuBridge.runShell("/system/bin/service call misc_policy 6", timeoutSeconds = 60)
    }

    fun parseParcelString(raw: String): String? {
        val words = raw.lineSequence()
            .flatMap { line ->
                val payload = if (line.contains(':')) line.substringAfter(':') else line
                Regex("""\b[0-9a-fA-F]{8}\b""").findAll(payload).map { it.value.toLong(16) }
            }
            .toList()
        if (words.size < 3) return null
        val length = words.getOrNull(1)?.toInt() ?: return null
        if (length <= 0 || length > 512) return null

        val chars = StringBuilder()
        words.drop(2).forEach { word ->
            val low = (word and 0xffff).toInt()
            val high = ((word shr 16) and 0xffff).toInt()
            if (low != 0 && chars.length < length) chars.append(low.toChar())
            if (high != 0 && chars.length < length) chars.append(high.toChar())
        }
        return chars.toString().takeIf { it.isNotBlank() }
    }

    fun fontNamesMatch(active: String?, expected: String): Boolean {
        if (active == null) return false
        return normalize(active) == normalize(expected)
    }

    private fun normalize(value: String): String {
        return value.trim().lowercase().replace('-', '_')
    }

    private fun String.hasFailureMarker(): Boolean {
        return contains("fffffffe", ignoreCase = true) ||
            contains("Exception", ignoreCase = true) ||
            contains("error", ignoreCase = true)
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
