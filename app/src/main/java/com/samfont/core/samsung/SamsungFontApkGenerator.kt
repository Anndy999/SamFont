package com.samfont.core.samsung

import android.content.Context
import com.samfont.core.font.FontFamilyModel
import com.samfont.core.font.FontRepository
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

data class SamsungFontGeneratedPackage(
    val apk: File,
    val spec: SamsungFontPackageSpec,
    val reused: Boolean,
    val log: String
)

interface ApkSigner {
    fun sign(unsignedApk: File, signedApk: File)
}

class SamsungFontApkGenerator(
    private val context: Context,
    private val signer: ApkSigner = AndroidApkSigner()
) {
    fun generate(fontFamily: FontFamilyModel): SamsungFontGeneratedPackage {
        val fontFileModel = fontFamily.files.firstOrNull()
            ?: error("字体文件不存在")
        val fontFile = File(fontFileModel.path)
        require(fontFile.exists()) { "字体文件不存在：${fontFile.absolutePath}" }
        require(FontRepository.isValidFontFile(fontFile)) { "不是有效字体文件：${fontFile.absolutePath}" }

        val hash = fontFileModel.sha256.ifBlank { FontRepository.sha256(fontFile) }
        val spec = SamsungFontPackageSpec.create(fontFamily.displayName, fontFileModel.fileType)
        val outputDir = File(context.filesDir, "samsung-packages").apply { mkdirs() }
        val signedApk = File(outputDir, "samfont-generated-$hash.apk")
        if (signedApk.exists() && signedApk.length() > 0) {
            return SamsungFontGeneratedPackage(
                apk = signedApk,
                spec = spec,
                reused = true,
                log = "Reused generated APK: ${signedApk.absolutePath}"
            )
        }

        val template = copyTemplateApk(outputDir)
        val unsignedApk = File(outputDir, "samfont-generated-$hash-unsigned.apk")
        val xml = SamsungFontXmlBuilder.build(spec)
        rewriteTemplateApk(
            templateApk = template,
            outputApk = unsignedApk,
            fontFile = fontFile,
            fontEntryName = "assets/fonts/${spec.fontFileName}",
            xmlEntryName = "assets/xml/${spec.fontXmlName}",
            xml = xml
        )
        signer.sign(unsignedApk, signedApk)
        unsignedApk.delete()
        template.delete()

        return SamsungFontGeneratedPackage(
            apk = signedApk,
            spec = spec,
            reused = false,
            log = buildString {
                appendLine("Generated APK: ${signedApk.absolutePath}")
                appendLine("Package: ${spec.packageName}")
                appendLine("Display name: ${spec.displayName}")
                appendLine("Font entry: assets/fonts/${spec.fontFileName}")
                appendLine("XML entry: assets/xml/${spec.fontXmlName}")
            }
        )
    }

    private fun copyTemplateApk(outputDir: File): File {
        val template = File(outputDir, "samsung-font-template.apk")
        context.assets.open("templates/samsung-font-template.apk").use { input ->
            FileOutputStream(template).use { output -> input.copyTo(output) }
        }
        return template
    }

    companion object {
        fun rewriteTemplateApk(
            templateApk: File,
            outputApk: File,
            fontFile: File,
            fontEntryName: String,
            xmlEntryName: String,
            xml: String
        ) {
            ZipFile(templateApk).use { zip ->
                ZipOutputStream(FileOutputStream(outputApk)).use { output ->
                    val copied = mutableSetOf<String>()
                    zip.entries().asSequence().forEach { entry ->
                        val name = entry.name
                        if (entry.isDirectory || name.startsWith("META-INF/") ||
                            name.startsWith("assets/fonts/") || name == xmlEntryName ||
                            name == "assets/xml/samfont.xml"
                        ) {
                            return@forEach
                        }
                        copied += name
                        output.putNextEntry(ZipEntry(name).apply { time = entry.time })
                        zip.getInputStream(entry).use { it.copyTo(output) }
                        output.closeEntry()
                    }

                    if ("assets/fonts/" !in copied) {
                        output.putNextEntry(ZipEntry("assets/fonts/"))
                        output.closeEntry()
                    }
                    output.putNextEntry(ZipEntry(fontEntryName))
                    fontFile.inputStream().use { it.copyTo(output) }
                    output.closeEntry()

                    output.putNextEntry(ZipEntry(xmlEntryName))
                    output.write(xml.toByteArray(Charsets.UTF_8))
                    output.closeEntry()
                }
            }
        }
    }
}
