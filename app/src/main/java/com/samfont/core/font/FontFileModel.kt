package com.samfont.core.font

data class FontFileModel(
    val path: String,
    val weight: Int,
    val italic: Boolean,
    val ttcIndex: Int? = null
)
