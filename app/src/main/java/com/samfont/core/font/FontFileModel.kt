package com.samfont.core.font

data class FontFileModel(
    val path: String,
    val sha256: String,
    val fileType: String,
    val weight: Int,
    val italic: Boolean,
    val previewAvailable: Boolean = true,
    val ttcIndex: Int? = null
)
