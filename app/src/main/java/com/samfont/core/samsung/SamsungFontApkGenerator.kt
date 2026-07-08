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

data class SamsungFontGeneratedPackage(
    val apkFile: File,
    val spec: SamsungFontPackageSpec,
    val fontEntry: String,
    val xmlEntry: String,
    val sourceFontSha256: String,
    val reused: Boolean,
    val log: String
) {
    val apk: File
        get() = apkFile
    val packageName: String
        get() = spec.packageName
    val displayName: String
        get() = spec.displayName
}

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
        val spec = SamsungFontPackageSpec.create(fontFamily.displayName, fontFileModel.fileType, hash)
        val outputDir = File(context.filesDir, "samsung-packages").apply { mkdirs() }
        val signedApk = File(outputDir, "samfont-generated-$hash.apk")
        val unsignedApk = File(outputDir, "samfont-generated-$hash-unsigned.apk")
        val alignedApk = File(outputDir, "samfont-generated-$hash-aligned.apk")
        val fontEntryName = "assets/fonts/${spec.fontFileName}"
        val xmlEntryName = "assets/xml/${spec.fontXmlName}"
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
            packageName = spec.packageName,
            fontEntryName = fontEntryName,
            xmlEntryName = xmlEntryName,
            xml = xml
        )
        val unsignedResourceCheck = ApkStructureVerifier.checkResourcesArsc(unsignedApk)
        val alignedResourceCheck = ZipAligner.align(unsignedApk, alignedApk)
        signer.sign(alignedApk, signedApk)
        val signedResourceCheck = ApkStructureVerifier.requireValidResourcesArsc(signedApk)
        validateGeneratedApk(
            signedApk = signedApk,
            sourceFont = fontFile,
            fontEntryName = fontEntryName,
            xmlEntryName = xmlEntryName
        )
        val unsignedSize = unsignedApk.length()
        val alignedSize = alignedApk.length()
        unsignedApk.delete()
        alignedApk.delete()
        template.delete()

        return SamsungFontGeneratedPackage(
            apkFile = signedApk,
            spec = spec,
            fontEntry = fontEntryName,
            xmlEntry = xmlEntryName,
            sourceFontSha256 = hash,
            reused = false,
            log = buildString {
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
                appendLine("Display name: ${spec.displayName}")
                appendLine("Droid name: ${spec.droidName}")
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
            ApkStructureVerifier.requireValidResourcesArsc(signedApk)
            val result = ApkVerifier.Builder(signedApk).build().verify()
            require(result.isVerified) { "生成 APK 签名验证失败" }
        }

        fun rewriteTemplateApk(
            templateApk: File,
            outputApk: File,
            fontFile: File,
            packageName: String = SamsungFontPackageSpec.FIXED_PACKAGE_NAME,
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
                        val bytes = zip.getInputStream(entry).use { it.readBytes() }
                            .let { if (name == "AndroidManifest.xml") patchManifestPackageName(it, packageName) else it }
                        output.putNextEntry(buildZipEntry(name, entry.time, bytes, entry.method == ZipEntry.STORED || name == "resources.arsc"))
                        output.write(bytes)
                        output.closeEntry()
                    }

                    if ("assets/fonts/" !in copied) {
                        output.putNextEntry(ZipEntry("assets/fonts/"))
                        output.closeEntry()
                    }
                    val fontBytes = fontFile.readBytes()
                    output.putNextEntry(buildZipEntry(fontEntryName, System.currentTimeMillis(), fontBytes, forceStored = false))
                    output.write(fontBytes)
                    output.closeEntry()

                    val xmlBytes = xml.toByteArray(Charsets.UTF_8)
                    output.putNextEntry(buildZipEntry(xmlEntryName, System.currentTimeMillis(), xmlBytes, forceStored = false))
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

        private fun patchManifestPackageName(bytes: ByteArray, packageName: String): ByteArray {
            if (packageName == SamsungFontPackageSpec.FIXED_PACKAGE_NAME) return bytes
            require(packageName.length == SamsungFontPackageSpec.FIXED_PACKAGE_NAME.length) {
                "Generated package name must keep template package length."
            }
            val original = SamsungFontPackageSpec.FIXED_PACKAGE_NAME.toByteArray(Charsets.UTF_16LE)
            val replacement = packageName.toByteArray(Charsets.UTF_16LE)
            val index = bytes.indexOf(original)
            require(index >= 0) { "Template manifest package name not found." }
            return bytes.copyOf().apply {
                replacement.copyInto(this, destinationOffset = index)
            }
        }

        private fun ByteArray.indexOf(pattern: ByteArray): Int {
            if (pattern.isEmpty() || pattern.size > size) return -1
            for (index in 0..size - pattern.size) {
                var matched = true
                for (offset in pattern.indices) {
                    if (this[index + offset] != pattern[offset]) {
                        matched = false
                        break
                    }
                }
                if (matched) return index
            }
            return -1
        }
    }
}
