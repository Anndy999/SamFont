package com.samfont.core.font

import android.content.Context
import android.graphics.Typeface
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object FontRepository {
    val defaultWeights = listOf(100, 200, 300, 400, 500, 600, 700, 800, 900)

    private val supportedExtensions = setOf("ttf", "otf", "ttc")
    private val _fontFamilies = MutableStateFlow<List<FontFamilyModel>>(emptyList())
    val fontFamilies: StateFlow<List<FontFamilyModel>> = _fontFamilies.asStateFlow()

    fun reload(context: Context) {
        _fontFamilies.value = scanInstalledFonts(context)
    }

    fun scanInstalledFonts(context: Context): List<FontFamilyModel> {
        val fontDir = File(context.filesDir, "fonts")
        if (!fontDir.exists()) {
            return emptyList()
        }

        return fontDir
            .listFiles()
            ?.asSequence()
            ?.filter { it.isFile && isValidFontFile(it) }
            ?.sortedBy { it.name.lowercase() }
            ?.map { file ->
                val displayName = file.nameWithoutExtension.ifBlank { file.name }
                FontFamilyModel(
                    id = "${displayName}_${file.absolutePath.hashCode()}",
                    displayName = displayName,
                    files = listOf(
                        FontFileModel(
                            path = file.absolutePath,
                            weight = 400,
                            italic = false,
                            previewAvailable = canPreviewFont(file)
                        )
                    ),
                    supportedWeights = defaultWeights,
                    isVariableFont = false
                )
            }
            ?.toList()
            ?: emptyList()
    }

    fun isSupportedFontFile(name: String): Boolean {
        return findSupportedExtension(name) != null
    }

    fun findSupportedExtension(name: String?): String? {
        if (name.isNullOrBlank()) {
            return null
        }
        val extension = name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        return extension.takeIf { it in supportedExtensions }
    }

    fun extensionFromMimeType(mimeType: String?): String? {
        return when (mimeType?.lowercase()) {
            "font/ttf",
            "font/sfnt",
            "application/font-sfnt",
            "application/x-font-ttf",
            "application/x-font-truetype" -> "ttf"

            "font/otf",
            "application/x-font-otf",
            "application/x-font-opentype",
            "application/vnd.ms-opentype" -> "otf"

            "font/ttc",
            "font/collection",
            "application/x-font-ttc" -> "ttc"

            else -> null
        }
    }

    fun detectFontExtension(file: File): String? {
        val header = ByteArray(4)
        val readCount = runCatching {
            file.inputStream().use { input -> input.read(header) }
        }.getOrDefault(-1)

        if (readCount < 4) {
            return null
        }

        return when {
            header.contentEquals(byteArrayOf(0x00, 0x01, 0x00, 0x00)) -> "ttf"
            header.toAsciiString() == "true" -> "ttf"
            header.toAsciiString() == "OTTO" -> "otf"
            header.toAsciiString() == "ttcf" -> "ttc"
            else -> null
        }
    }

    fun isValidFontFile(file: File): Boolean {
        val extension = findSupportedExtension(file.name)
        val detectedExtension = detectFontExtension(file)
        return extension != null && detectedExtension != null
    }

    fun canPreviewFont(file: File): Boolean {
        return runCatching {
            // 这里只表示 Android Typeface 预览引擎能否加载，不作为字体库显示条件。
            Typeface.Builder(file).build()
        }.isSuccess
    }

    fun normalizeDetectedExtension(file: File): File {
        val detectedExtension = detectFontExtension(file) ?: return file
        val currentExtension = findSupportedExtension(file.name)

        if (currentExtension == detectedExtension) {
            return file
        }

        val normalizedFile = uniqueSiblingFile(
            directory = file.parentFile ?: return file,
            baseName = file.nameWithoutExtension.ifBlank { "imported-font" },
            extension = detectedExtension
        )

        return if (file.renameTo(normalizedFile)) {
            normalizedFile
        } else {
            file
        }
    }

    private fun uniqueSiblingFile(directory: File, baseName: String, extension: String): File {
        var candidate = File(directory, "$baseName.$extension")
        var index = 1

        while (candidate.exists()) {
            candidate = File(directory, "${baseName}_$index.$extension")
            index += 1
        }

        return candidate
    }

    private fun ByteArray.toAsciiString(): String {
        return String(this, Charsets.US_ASCII)
    }
}
