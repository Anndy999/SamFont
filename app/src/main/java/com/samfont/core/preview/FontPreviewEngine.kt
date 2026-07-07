package com.samfont.core.preview

import android.graphics.Typeface
import android.os.Build
import com.samfont.core.font.FontFileModel
import java.io.File

object FontPreviewEngine {
    fun loadTypeface(
        fontFile: FontFileModel,
        weight: Int,
        variationSettings: Map<String, Float> = emptyMap()
    ): Typeface? {
        val file = File(fontFile.path)
        if (!file.exists()) {
            return null
        }

        return runCatching {
            Typeface.Builder(file).apply {
                if (fontFile.ttcIndex != null) {
                    setTtcIndex(fontFile.ttcIndex)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    setWeight(weight)
                    setItalic(fontFile.italic)
                }
                val settings = buildVariationSettings(variationSettings)
                if (settings != null) {
                    setFontVariationSettings(settings)
                }
            }.build()
        }.recoverCatching {
            Typeface.Builder(file).build()
        }.getOrNull()
    }

    fun buildVariationSettings(settings: Map<String, Float>): String? {
        if (settings.isEmpty()) {
            return null
        }

        return settings.entries.joinToString(separator = ", ") { (tag, value) ->
            require(tag.length == 4 && tag.all { it.code in 0x20..0x7E }) {
                "非法 axis tag: $tag"
            }
            "'$tag' ${trimFloat(value)}"
        }
    }

    private fun trimFloat(value: Float): String {
        val intValue = value.toInt()
        return if (value == intValue.toFloat()) intValue.toString() else value.toString()
    }
}
