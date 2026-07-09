package com.samfont.core.samsung

import android.content.Context
import android.content.pm.PackageManager
import com.samfont.core.shizuku.ShizukuBridge

data class SamsungInstalledFontItem(
    val name: String,
    val displayName: String,
    val packageName: String,
    val regularAsset: String,
    val boldAsset: String?
)

data class SamsungFontScanResult(
    val fonts: List<SamsungInstalledFontItem>,
    val log: String
)

class SamsungInstalledFontScanner(
    private val context: Context
) {
    fun fetchInstalledSamsungFonts(): SamsungFontScanResult {
        val log = StringBuilder()
        val packageNames = discoverPackageNames(log)
        val fonts = packageNames.flatMap { packageName ->
            scanPackage(packageName, log)
        }.distinctBy { it.packageName + ":" + it.name }
        return SamsungFontScanResult(fonts, log.toString())
    }

    private fun discoverPackageNames(log: StringBuilder): List<String> {
        val fromPackageManager = runCatching {
            context.packageManager.getInstalledPackages(0)
                .map { it.packageName }
        }.getOrElse {
            log.appendLine("PackageManager scan failed: ${it.message}")
            emptyList()
        }

        val fromShell = ShizukuBridge.runShell("/system/bin/pm list packages", timeoutSeconds = 60)
        log.appendLine(formatShell(fromShell))
        val shellPackages = fromShell.stdout
            .lineSequence()
            .mapNotNull { it.trim().removePrefix("package:").takeIf { value -> value.isNotBlank() } }
            .toList()

        return (fromPackageManager + shellPackages)
            .distinct()
            .filter { packageName ->
                packageName.startsWith(SamsungFontPackageSpec.PACKAGE_PREFIX) ||
                    packageName.contains("applemint", ignoreCase = true)
            }
            .sorted()
    }

    private fun scanPackage(packageName: String, log: StringBuilder): List<SamsungInstalledFontItem> {
        val packageContext = runCatching {
            context.createPackageContext(packageName, Context.CONTEXT_IGNORE_SECURITY)
        }.getOrElse {
            log.appendLine("createPackageContext failed for $packageName: ${it.message}")
            return emptyList()
        }

        val assets = packageContext.assets
        val fontPaths = listOf("fonts", "assets", "")
            .flatMap { dir ->
                runCatching {
                    assets.list(dir)
                        .orEmpty()
                        .filter { it.endsWith(".ttf", true) || it.endsWith(".otf", true) }
                        .map { if (dir.isBlank()) it else "$dir/$it" }
                }.getOrElse {
                    log.appendLine("assets list failed package=$packageName dir=$dir: ${it.message}")
                    emptyList()
                }
            }
            .distinct()

        val regular = fontPaths.filterNot { path ->
            val base = path.substringAfterLast('/').substringBeforeLast('.')
            base.endsWith("-Bold", ignoreCase = true) || base.endsWith("_Bold", ignoreCase = true)
        }

        return regular.map { regularPath ->
            val baseName = regularPath.substringAfterLast('/').substringBeforeLast('.')
            val boldPath = fontPaths.firstOrNull { path ->
                val boldBase = path.substringAfterLast('/').substringBeforeLast('.')
                boldBase.equals("$baseName-Bold", ignoreCase = true) ||
                    boldBase.equals("${baseName}_Bold", ignoreCase = true)
            }
            val item = SamsungInstalledFontItem(
                name = baseName,
                displayName = baseName.replace('_', ' '),
                packageName = packageName,
                regularAsset = regularPath,
                boldAsset = boldPath
            )
            log.appendLine(
                "found installed Samsung font: name=${item.name} package=${item.packageName} regular=${item.regularAsset} bold=${item.boldAsset}"
            )
            item
        }
    }

    private fun formatShell(result: com.samfont.core.shizuku.ShellResult): String {
        return buildString {
            appendLine("$ ${result.command}")
            appendLine("exitCode=${result.exitCode}")
            if (result.stdout.isNotBlank()) appendLine("stdout:\n${result.stdout.trim()}")
            if (result.stderr.isNotBlank()) appendLine("stderr:\n${result.stderr.trim()}")
        }
    }
}
