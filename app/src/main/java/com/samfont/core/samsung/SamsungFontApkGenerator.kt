package com.samfont.core.samsung

import android.content.Context
import com.android.apksig.ApkVerifier
import com.samfont.core.font.FontFamilyModel
import com.samfont.core.font.FontRepository
import java.io.File
import java.io.FileOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

interface ApkSigner {
    fun sign(unsignedApk: File, signedApk: File)
}

class SamsungFontApkGenerator(
    private val context: Context,
    private val signer: ApkSigner = AndroidApkSigner()
) {
    fun generate(fontFamily: FontFamilyModel): BuiltFontPackage {
        val fontFileModel = fontFamily.files.firstOrNull()
            ?: error("字体文件不存在")
        val fontFile = File(fontFileModel.path)
        require(fontFile.exists()) { "字体文件不存在：${fontFile.absolutePath}" }
        require(FontRepository.isValidFontFile(fontFile)) { "不是有效字体文件：${fontFile.absolutePath}" }

        val hash = fontFileModel.sha256.ifBlank { FontRepository.sha256(fontFile) }
        val spec = SamsungFontPackageSpec.create(fontFamily.displayName)
        val outputDir = File(context.filesDir, "samsung-packages").apply { mkdirs() }
        val signedApk = File(outputDir, "samfont-generated-$hash.apk")
        val unsignedApk = File(outputDir, "samfont-generated-$hash-unsigned.apk")
        val alignedApk = File(outputDir, "samfont-generated-$hash-aligned.apk")
        signedApk.delete()
        unsignedApk.delete()
        alignedApk.delete()

        val template = copyTemplateApk(outputDir)
        val templatePath = template.absolutePath
        val templateSize = template.length()
        val xml = SamsungFontXmlBuilder.build(spec)
        rewriteTemplateApk(
            templateApk = template,
            outputApk = unsignedApk,
            fontFile = fontFile,
            spec = spec,
            xml = xml
        )
        val unsignedResourceCheck = ApkStructureVerifier.checkResourcesArsc(unsignedApk)
        val alignedResourceCheck = ZipAligner.align(unsignedApk, alignedApk)
        signer.sign(alignedApk, signedApk)
        val signedResourceCheck = ApkStructureVerifier.requireValidResourcesArsc(signedApk)
        validateGeneratedApk(
            signedApk = signedApk,
            sourceFont = fontFile,
            regularFontEntryName = spec.regularFontEntry,
            boldFontEntryName = spec.boldFontEntry,
            xmlEntryName = spec.xmlEntry
        )
        val unsignedSize = unsignedApk.length()
        val alignedSize = alignedApk.length()
        unsignedApk.delete()
        alignedApk.delete()
        template.delete()

        return BuiltFontPackage(
            apkFile = signedApk,
            userInputName = spec.userInputName,
            displayName = spec.displayName,
            assetBase = spec.assetBase,
            packageName = spec.packageName,
            xmlEntry = spec.xmlEntry,
            regularFontEntry = spec.regularFontEntry,
            boldFontEntry = spec.boldFontEntry,
            sourceFontSha256 = hash,
            log = buildString {
                appendLine("User input: ${spec.userInputName}")
                appendLine("Display name: ${spec.displayName}")
                appendLine("Asset base: ${spec.assetBase}")
                appendLine("Template APK path: $templatePath")
                appendLine("Template APK size: $templateSize")
                appendLine("Unsigned APK path: ${unsignedApk.absolutePath}")
                appendLine("Unsigned APK size: $unsignedSize")
                append(unsignedResourceCheck.toLog("resources.arsc before zipalign:"))
                appendLine("Aligned APK path: ${alignedApk.absolutePath}")
                appendLine("Aligned APK size: $alignedSize")
                append(alignedResourceCheck.toLog("resources.arsc before signing:"))
                appendLine("Signed APK path: ${signedApk.absolutePath}")
                appendLine("Signed APK size: ${signedApk.length()}")
                append(signedResourceCheck.toLog("resources.arsc after signing:"))
                appendLine("Signed APK verified: true")
                appendLine("Package name: ${spec.packageName}")
                appendLine("Font entry: ${spec.regularFontEntry}")
                appendLine("Bold entry: ${spec.boldFontEntry}")
                appendLine("XML entry: ${spec.xmlEntry}")
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
            regularFontEntryName: String,
            boldFontEntryName: String,
            xmlEntryName: String
        ) {
            require(signedApk.exists()) { "生成 APK 文件不存在" }
            require(signedApk.length() > 0) { "生成 APK 文件为空" }
            ZipFile(signedApk).use { zip ->
                require(zip.getEntry("AndroidManifest.xml") != null) { "生成 APK 缺少 AndroidManifest.xml" }
                require(zip.getEntry(xmlEntryName) != null) { "生成 APK 缺少字体 XML entry" }
                val fontEntry = zip.getEntry(regularFontEntryName)
                    ?: throw IllegalStateException("生成 APK 缺少字体文件 entry")
                val boldEntry = zip.getEntry(boldFontEntryName)
                    ?: throw IllegalStateException("生成 APK 缺少 Bold 字体文件 entry")
                if (fontEntry.size >= 0 && fontEntry.size != sourceFont.length()) {
                    throw IllegalStateException("生成 APK 字体文件大小不一致")
                }
                if (boldEntry.size >= 0 && boldEntry.size != sourceFont.length()) {
                    throw IllegalStateException("生成 APK Bold 字体文件大小不一致")
                }
            }
            ApkStructureVerifier.requireValidResourcesArsc(signedApk)
            val result = ApkVerifier.Builder(signedApk).build().verify()
            require(result.isVerified) { "生成 APK 签名验证失败" }
        }

        fun rewriteTemplateApk(
            templateApk: File,
            outputApk: File,
            fontFile: File,
            spec: SamsungFontPackageSpec,
            xml: String
        ) {
            ZipFile(templateApk).use { zip ->
                ZipOutputStream(FileOutputStream(outputApk)).use { output ->
                    val copied = mutableSetOf<String>()
                    zip.entries().asSequence().forEach { entry ->
                        val name = entry.name
                        if (entry.isDirectory || name.startsWith("META-INF/") ||
                            name.startsWith("assets/fonts/") ||
                            name.startsWith("assets/xml/")
                        ) {
                            return@forEach
                        }
                        copied += name
                        val rawBytes = zip.getInputStream(entry).use { it.readBytes() }
                        val bytes = when (name) {
                            "AndroidManifest.xml" -> patchManifestTemplate(rawBytes, spec)
                            "resources.arsc" -> patchArscTemplate(rawBytes, spec)
                            else -> rawBytes
                        }
                        output.putNextEntry(buildZipEntry(name, entry.time, bytes, entry.method == ZipEntry.STORED || name == "resources.arsc"))
                        output.write(bytes)
                        output.closeEntry()
                    }

                    if ("assets/fonts/" !in copied) {
                        output.putNextEntry(ZipEntry("assets/fonts/"))
                        output.closeEntry()
                    }
                    val fontBytes = fontFile.readBytes()
                    output.putNextEntry(buildZipEntry(spec.regularFontEntry, System.currentTimeMillis(), fontBytes, forceStored = false))
                    output.write(fontBytes)
                    output.closeEntry()

                    output.putNextEntry(buildZipEntry(spec.boldFontEntry, System.currentTimeMillis(), fontBytes, forceStored = false))
                    output.write(fontBytes)
                    output.closeEntry()

                    val xmlBytes = xml.toByteArray(Charsets.UTF_8)
                    output.putNextEntry(buildZipEntry(spec.xmlEntry, System.currentTimeMillis(), xmlBytes, forceStored = false))
                    output.write(xmlBytes)
                    output.closeEntry()
                }
            }
        }

        private fun buildZipEntry(
            name: String,
            time: Long,
            bytes: ByteArray,
            forceStored: Boolean
        ): ZipEntry {
            return ZipEntry(name).apply {
                this.time = time
                if (forceStored) {
                    method = ZipEntry.STORED
                    size = bytes.size.toLong()
                    compressedSize = bytes.size.toLong()
                    crc = crc32(bytes)
                } else {
                    method = ZipEntry.DEFLATED
                }
            }
        }

        private fun crc32(bytes: ByteArray): Long {
            val crc = CRC32()
            crc.update(bytes)
            return crc.value
        }

        private fun patchManifestTemplate(bytes: ByteArray, spec: SamsungFontPackageSpec): ByteArray {
            return BinaryStringPoolPatcher.patchBinaryXml(
                bytes,
                mapOf(
                    SamsungFontPackageSpec.PACKAGE_PLACEHOLDER to spec.packageName,
                    SamsungFontPackageSpec.DISPLAY_PLACEHOLDER to spec.displayName,
                    SamsungFontPackageSpec.XML_PATH_PLACEHOLDER to spec.xmlEntry
                )
            )
        }

        private fun patchArscTemplate(bytes: ByteArray, spec: SamsungFontPackageSpec): ByteArray {
            return BinaryStringPoolPatcher.patchResourceTable(
                bytes,
                mapOf(
                    SamsungFontPackageSpec.ARSC_DISPLAY_PLACEHOLDER to "@@${spec.displayName}",
                    SamsungFontPackageSpec.DISPLAY_PLACEHOLDER to spec.displayName
                )
            )
        }
    }
}
