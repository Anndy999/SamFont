package com.samfont.core.font

data class FontFamilyModel(
    val id: String,
    val displayName: String,
    val files: List<FontFileModel>,
    val supportedWeights: List<Int>,
    val isVariableFont: Boolean
)
