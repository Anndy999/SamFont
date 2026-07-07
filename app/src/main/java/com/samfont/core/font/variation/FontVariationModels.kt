package com.samfont.core.font.variation

data class FontVariationAxis(
    val tag: String,
    val minValue: Float,
    val defaultValue: Float,
    val maxValue: Float,
    val flags: Int,
    val axisNameId: Int,
    val name: String?
) {
    val hidden: Boolean
        get() = flags and 0x0001 != 0

    val standard: Boolean
        get() = tag in setOf("wght", "wdth", "opsz", "slnt", "ital")
}

data class FontNamedInstance(
    val nameId: Int,
    val name: String?,
    val coordinates: Map<String, Float>
)

data class FontVariationInfo(
    val axes: List<FontVariationAxis>,
    val namedInstances: List<FontNamedInstance>
) {
    val isVariable: Boolean
        get() = axes.isNotEmpty()
}

data class FontCompatibilityReport(
    val fileType: String,
    val isVariableFont: Boolean,
    val axes: List<FontVariationAxis>,
    val namedInstances: List<FontNamedInstance>,
    val androidPreviewAvailable: Boolean,
    val hasCjkCoverage: Boolean,
    val hasSimplifiedChineseCoverage: Boolean,
    val hasTraditionalChineseCoverage: Boolean,
    val isTtc: Boolean,
    val suitableForSystemFont: Boolean
)
