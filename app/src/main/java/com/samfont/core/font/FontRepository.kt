package com.samfont.core.font

import android.content.Context
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
            ?.filter { it.isFile && isSupportedFontFile(it.name) }
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
                            italic = false
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
        val extension = name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        return extension in supportedExtensions
    }
}
