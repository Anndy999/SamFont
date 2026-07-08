package com.samfont.core.samsung

data class SamsungFontPackageSpec(
    val packageName: String,
    val displayName: String,
    val fontFileName: String,
    val fontXmlName: String,
    val versionCode: Long,
    val versionName: String
) {
    companion object {
        const val FIXED_PACKAGE_NAME = "com.monotype.android.font.samfont.generated"
        const val DEFAULT_XML_NAME = "samfont.xml"

        fun create(displayName: String, fontExtension: String): SamsungFontPackageSpec {
            val ext = fontExtension.lowercase().ifBlank { "ttf" }
            return SamsungFontPackageSpec(
                packageName = FIXED_PACKAGE_NAME,
                displayName = sanitizeDisplayName(displayName),
                fontFileName = "SamFont.$ext",
                fontXmlName = DEFAULT_XML_NAME,
                versionCode = 1L,
                versionName = "1.0"
            )
        }

        fun sanitizeDisplayName(name: String): String {
            return name
                .replace(Regex("[\\u0000-\\u001F<>\"'&]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(48)
                .ifBlank { "SamFont" }
        }
    }
}
