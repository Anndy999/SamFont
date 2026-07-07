package com.samfont.shizuku

import java.io.File

class SamFontShizukuUserService : ISamFontShizukuService.Stub() {
    override fun whoami(): String {
        return System.getProperty("user.name") ?: "unknown"
    }

    override fun id(): String {
        return readProcStatusUidLine()
    }

    override fun listFontDirs(): String {
        val dirs = listOf(
            "/system/fonts",
            "/product/fonts",
            "/system/etc",
            "/product/etc",
            "/system/etc/fonts.xml",
            "/product/etc/fonts_customization.xml"
        )

        return dirs.joinToString(separator = "\n") { path ->
            val file = File(path)
            "$path exists=${file.exists()} dir=${file.isDirectory} file=${file.isFile}"
        }
    }

    override fun checkWritable(): String {
        val targets = listOf(
            "/system/fonts",
            "/product/fonts",
            "/data/local/tmp",
            "/sdcard"
        )

        return targets.joinToString(separator = "\n") { path ->
            val file = File(path)
            "$path canRead=${file.canRead()} canWrite=${file.canWrite()}"
        }
    }

    override fun readFontConfigCandidates(): String {
        val candidates = listOf(
            "/system/etc/fonts.xml",
            "/system/etc/font_fallback.xml",
            "/product/etc/fonts_customization.xml",
            "/product/etc/fonts.xml"
        )

        return candidates.joinToString(separator = "\n\n") { path ->
            val file = File(path)
            val preview = runCatching {
                if (file.exists() && file.isFile) {
                    file.bufferedReader().use { reader ->
                        reader.readLines().take(20).joinToString("\n")
                    }
                } else {
                    "<missing>"
                }
            }.getOrElse { "<read failed: ${it.message}>" }

            "### $path\n$preview"
        }
    }

    private fun readProcStatusUidLine(): String {
        return runCatching {
            File("/proc/self/status")
                .readLines()
                .filter { it.startsWith("Uid:") || it.startsWith("Gid:") || it.startsWith("Groups:") }
                .joinToString("\n")
        }.getOrElse { "读取 /proc/self/status 失败：${it.message}" }
    }
}
