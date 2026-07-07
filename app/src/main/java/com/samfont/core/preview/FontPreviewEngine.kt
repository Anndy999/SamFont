package com.samfont.core.preview

import android.graphics.Typeface
import android.os.Build
import com.samfont.core.font.FontFileModel
import java.io.File

object FontPreviewEngine {
    fun loadTypeface(fontFile: FontFileModel, weight: Int): Typeface? {
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
            }.build()
        }.getOrNull()
    }
}
