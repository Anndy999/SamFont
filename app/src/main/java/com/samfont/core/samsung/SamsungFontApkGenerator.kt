package com.samfont.core.samsung

import android.content.Context
import com.android.apksig.ApkVerifier
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
        val unsignedApk = File(outputDir, "samfont-generated-$hash-unsigned.apk")
        val fontEntryName = "assets/fonts/${spec.fontFileName}"
        val xmlEntryName = "assets/xml/${spec.fontXmlName}"
        signedApk.delete()
        unsignedApk.delete()

        val template = copyTemplateApk(outputDir)
        val templatePath = template.absolutePath
        val templateSize = template.length()
        val xml = SamsungFontXmlBuilder.build(spec)
        rewriteTemplateApk(
            templateApk = template,
            outputApk = unsignedApk,
            fontFile = fontFile,
            fontEntryName = fontEntryName,
            xmlEntryName = xmlEntryName,
            xml = xml
        )
        signer.sign(unsignedApk, signedApk)
        validateGeneratedApk(
            signedApk = signedApk,
            sourceFont = fontFile,
            fontEntryName = fontEntryName,
            xmlEntryName = xmlEntryName
        )
        unsignedApk.delete()
        template.delete()

        return SamsungFontGeneratedPackage(
            apk = signedApk,
            spec = spec,
            reused = false,
            log = buildString {
                appendLine("Template APK path: $templatePath")
                appendLine("Template APK size: $templateSize")
                appendLine("Signed APK path: ${signedApk.absolutePath}")
                appendLine("Signed APK size: ${signedApk.length()}")
                appendLine("Signed APK verified: true")
                appendLine("Package name: ${spec.packageName}")
                appendLine("Display name: ${spec.displayName}")
                appendLine("Font entry: $fontEntryName")
                appendLine("XML entry: $xmlEntryName")
                appendLine("Source font size: ${fontFile.length()}")
                appendLine("Font sha256: $hash")
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
        fun validateGeneratedApk(
            signedApk: File,
            sourceFont: File,
            fontEntryName: String,
            xmlEntryName: String
        ) {
            require(signedApk.exists()) { "生成 APK 文件不存在" }
            require(signedApk.length() > 0) { "生成 APK 文件为空" }
            ZipFile(signedApk).use { zip ->
                require(zip.getEntry("AndroidManifest.xml") != null) { "生成 APK 缺少 AndroidManifest.xml" }
                require(zip.getEntry(xmlEntryName) != null) { "生成 APK 缺少字体 XML entry" }
                val fontEntry = zip.getEntry(fontEntryName)
                    ?: throw IllegalStateException("生成 APK 缺少字体文件 entry")
                if (fontEntry.size >= 0 && fontEntry.size != sourceFont.length()) {
                    throw IllegalStateException("生成 APK 字体文件大小不一致")
                }
            }
            val result = ApkVerifier.Builder(signedApk).build().verify()
            require(result.isVerified) { "生成 APK 签名验证失败" }
        }

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
