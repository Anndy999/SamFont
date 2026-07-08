package com.samfont.core.font

import android.content.Context
import android.graphics.Typeface
import com.samfont.core.font.variation.FontCompatibilityReport
import com.samfont.core.font.variation.FontVariationInfo
import com.samfont.core.font.variation.OpenTypeCoverageParser
import com.samfont.core.font.variation.OpenTypeMetadataParser
import com.samfont.core.font.variation.OpenTypeVariationParser
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object FontRepository {
    val defaultWeights = listOf(100, 200, 300, 400, 500, 600, 700, 800, 900)

    private val supportedExtensions = setOf("ttf", "otf", "ttc")
    private val _fontFamilies = MutableStateFlow<List<FontFamilyModel>>(emptyList())
    val fontFamilies: StateFlow<List<FontFamilyModel>> = _fontFamilies.asStateFlow()

    fun importedDir(context: Context): File = File(context.filesDir, "fonts/imported")
    fun installedDir(context: Context): File = File(context.filesDir, "fonts/installed")
    private fun stateDir(context: Context): File = File(context.filesDir, "fonts/state")
    fun appliedHashFile(context: Context): File = File(stateDir(context), "current-applied.sha256")

    fun reload(context: Context) {
        _fontFamilies.value = scanFonts(context)
    }

    fun scanFonts(context: Context): List<FontFamilyModel> {
        ensureDirs(context)
        val appliedHash = readAppliedHash(context)
        val installed = scanDir(installedDir(context), FontState.Installed, appliedHash)
        val imported = scanDir(importedDir(context), FontState.Imported, appliedHash)

        return (installed + imported)
            .distinctBy { it.id }
            .sortedWith(compareBy<FontFamilyModel> { it.state.ordinal }.thenBy { it.displayName.lowercase() })
    }

    fun installedFonts(context: Context): List<FontFamilyModel> {
        return scanFonts(context).filter { it.state == FontState.Installed || it.state == FontState.Applied }
    }

    fun importedFonts(context: Context): List<FontFamilyModel> {
        return scanFonts(context).filter { it.state == FontState.Imported || it.state == FontState.Available }
    }

    fun importFontFile(context: Context, source: File, displayName: String): ImportResult {
        ensureDirs(context)
        val detectedExtension = detectFontExtension(source)
            ?: return ImportResult.Failure("不是有效字体文件")
        val hash = sha256(source)

        findByHash(context, hash)?.let {
            return ImportResult.Success(buildFamilies(it, FontState.Imported, readAppliedHash(context)).first(), duplicate = true)
        }

        val target = uniqueFile(importedDir(context), sanitizeBaseName(displayName), detectedExtension)
        source.copyTo(target, overwrite = false)
        val normalized = normalizeDetectedExtension(target)

        if (!isValidFontFile(normalized)) {
            normalized.delete()
            return ImportResult.Failure("不是有效字体文件")
        }

        val family = buildFamilies(normalized, FontState.Imported, readAppliedHash(context)).first()
        return ImportResult.Success(family, duplicate = false)
    }

    fun installFont(context: Context, font: FontFamilyModel): InstallResult {
        ensureDirs(context)
        val source = font.files.firstOrNull()?.let { File(it.path) }
            ?: return InstallResult.Failure("字体文件不存在")
        if (!source.exists() || !isValidFontFile(source)) {
            return InstallResult.Failure("字体文件不可用")
        }

        val hash = font.files.first().sha256
        findInDir(installedDir(context), hash)?.let {
            val installed = buildFamilies(it, FontState.Installed, readAppliedHash(context)).first()
            return InstallResult.Success(installed, duplicate = true)
        }

        val target = uniqueFile(installedDir(context), sanitizeBaseName(font.displayName), font.fileType)
        source.copyTo(target, overwrite = false)
        val normalized = normalizeDetectedExtension(target)
        normalized.setReadable(true, false)
        val installed = buildFamilies(normalized, FontState.Installed, readAppliedHash(context)).first()
        return InstallResult.Success(installed, duplicate = false)
    }

    fun markApplied(context: Context, hash: String) {
        val file = appliedHashFile(context)
        file.parentFile?.mkdirs()
        val old = file.takeIf { it.exists() }?.readText()?.trim()
        if (old != hash) {
            atomicWrite(file, hash)
        }
    }

    fun readAppliedHash(context: Context): String? {
        return appliedHashFile(context).takeIf { it.exists() }?.readText()?.trim()?.ifBlank { null }
    }

    fun findInstalledFile(context: Context, hash: String): File? = findInDir(installedDir(context), hash)

    fun detectFontExtension(file: File): String? {
        val header = ByteArray(4)
        val readCount = runCatching { file.inputStream().use { it.read(header) } }.getOrDefault(-1)
        if (readCount < 4) return null

        return when {
            header.contentEquals(byteArrayOf(0x00, 0x01, 0x00, 0x00)) -> "ttf"
            header.toAsciiString() == "true" -> "ttf"
            header.toAsciiString() == "OTTO" -> "otf"
            header.toAsciiString() == "ttcf" -> "ttc"
            else -> null
        }
    }

    fun isValidFontFile(file: File): Boolean = detectFontExtension(file) != null

    fun canPreviewFont(file: File, ttcIndex: Int? = null): Boolean {
        return runCatching {
            Typeface.Builder(file).apply { ttcIndex?.let { setTtcIndex(it) } }.build()
        }.isSuccess
    }

    fun normalizeDetectedExtension(file: File): File {
        val detectedExtension = detectFontExtension(file) ?: return file
        val currentExtension = file.extension.lowercase().takeIf { it in supportedExtensions }
        if (currentExtension == detectedExtension) return file

        val normalized = uniqueFile(
            directory = file.parentFile ?: return file,
            baseName = file.nameWithoutExtension.ifBlank { "imported-font" },
            extension = detectedExtension
        )
        return if (file.renameTo(normalized)) normalized else file
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun ensureDirs(context: Context) {
        importedDir(context).mkdirs()
        installedDir(context).mkdirs()
        stateDir(context).mkdirs()
    }

    private fun scanDir(directory: File, state: FontState, appliedHash: String?): List<FontFamilyModel> {
        return directory
            .listFiles()
            ?.asSequence()
            ?.filter { it.isFile && isValidFontFile(it) }
            ?.flatMap { file -> buildFamilies(file, state, appliedHash).asSequence() }
            ?.toList()
            .orEmpty()
    }

    private fun buildFamilies(file: File, state: FontState, appliedHash: String?): List<FontFamilyModel> {
        val fileType = detectFontExtension(file) ?: "unknown"
        val faceCount = if (fileType == "ttc") OpenTypeMetadataParser.ttcFaceCount(file) else 1
        val hash = sha256(file)

        return (0 until faceCount).map { index ->
            val ttcIndex = if (fileType == "ttc") index else null
            val metadata = OpenTypeMetadataParser.parse(file, index)
            val variationInfo = OpenTypeVariationParser.parse(file, index)
            val previewAvailable = canPreviewFont(file, ttcIndex)
            val report = buildCompatibilityReport(file, fileType, variationInfo, previewAvailable, ttcIndex)
            val displayName = metadata.fullName
                ?: metadata.familyName
                ?: file.nameWithoutExtension.ifBlank { file.name }
            val actualState = when {
                !file.exists() -> FontState.Broken
                hash == appliedHash && state == FontState.Installed -> FontState.Applied
                else -> state
            }
            val id = if (ttcIndex != null) "$hash#$ttcIndex" else hash

            FontFamilyModel(
                id = id,
                displayName = displayName,
                files = listOf(
                    FontFileModel(
                        path = file.absolutePath,
                        sha256 = hash,
                        fileType = fileType,
                        weight = metadata.weight,
                        italic = metadata.subfamilyName?.contains("italic", ignoreCase = true) == true,
                        previewAvailable = previewAvailable,
                        ttcIndex = ttcIndex
                    )
                ),
                supportedWeights = defaultWeights,
                isVariableFont = variationInfo?.isVariable == true,
                state = actualState,
                fileType = fileType,
                previewAvailable = previewAvailable,
                variationInfo = variationInfo,
                compatibilityReport = report
            )
        }
    }

    private fun buildCompatibilityReport(
        file: File,
        fileType: String,
        variationInfo: FontVariationInfo?,
        previewAvailable: Boolean,
        ttcIndex: Int?
    ): FontCompatibilityReport {
        val index = ttcIndex ?: 0
        val cjk = OpenTypeCoverageParser.hasCodePoints(file, listOf('汉'.code, '字'.code, '高'.code), index)
        val simplified = OpenTypeCoverageParser.hasCodePoints(file, listOf('汉'.code, '简'.code, '体'.code), index)
        val traditional = OpenTypeCoverageParser.hasCodePoints(file, listOf('漢'.code, '繁'.code, '體'.code), index)

        return FontCompatibilityReport(
            fileType = fileType,
            isVariableFont = variationInfo?.isVariable == true,
            axes = variationInfo?.axes.orEmpty(),
            namedInstances = variationInfo?.namedInstances.orEmpty(),
            androidPreviewAvailable = previewAvailable,
            hasCjkCoverage = cjk,
            hasSimplifiedChineseCoverage = simplified,
            hasTraditionalChineseCoverage = traditional,
            isTtc = fileType == "ttc",
            suitableForSystemFont = isValidFontFile(file) && cjk
        )
    }

    private fun findByHash(context: Context, hash: String): File? {
        return findInDir(installedDir(context), hash) ?: findInDir(importedDir(context), hash)
    }

    private fun findInDir(directory: File, hash: String): File? {
        return directory.listFiles()?.firstOrNull { it.isFile && isValidFontFile(it) && sha256(it) == hash }
    }

    private fun uniqueFile(directory: File, baseName: String, extension: String): File {
        directory.mkdirs()
        var candidate = File(directory, "$baseName.$extension")
        var index = 1
        while (candidate.exists()) {
            candidate = File(directory, "${baseName}_$index.$extension")
            index += 1
        }
        return candidate
    }

    private fun sanitizeBaseName(name: String): String {
        return name
            .substringBeforeLast('.', name)
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
            .take(80)
            .ifBlank { "imported-font" }
    }

    private fun atomicWrite(file: File, content: String) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        FileOutputStream(tmp).use { output ->
            output.write(content.toByteArray())
            output.fd.sync()
        }
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
    }

    private fun ByteArray.toAsciiString(): String = String(this, Charsets.US_ASCII)

    sealed class ImportResult {
        data class Success(val font: FontFamilyModel, val duplicate: Boolean) : ImportResult()
        data class Failure(val message: String) : ImportResult()
    }

    sealed class InstallResult {
        data class Success(val font: FontFamilyModel, val duplicate: Boolean) : InstallResult()
        data class Failure(val message: String) : InstallResult()
    }
}
