package com.samfont.core.font

import com.samfont.core.font.variation.FontCompatibilityReport
import com.samfont.core.font.variation.FontVariationInfo

data class FontFamilyModel(
    val id: String,
    val displayName: String,
    val files: List<FontFileModel>,
    val supportedWeights: List<Int>,
    val isVariableFont: Boolean,
    val variationInfo: FontVariationInfo? = null,
    val compatibilityReport: FontCompatibilityReport? = null
)
