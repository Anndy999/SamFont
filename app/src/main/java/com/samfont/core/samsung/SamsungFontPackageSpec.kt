package com.samfont.core.samsung

data class SamsungFontPackageSpec(
    val packageName: String,
    val displayName: String,
    val droidName: String,
    val fontFileName: String,
    val fontXmlName: String,
    val versionCode: Long,
    val versionName: String
) {
    companion object {
        const val FIXED_PACKAGE_NAME = "com.monotype.android.font.samfont.generated"
        const val DEFAULT_XML_NAME = "samfont.xml"
        private const val PACKAGE_PREFIX = "com.monotype.android.font.samfont."

        fun create(displayName: String, fontExtension: String, sourceHash: String = ""): SamsungFontPackageSpec {
            val ext = fontExtension.lowercase().ifBlank { "ttf" }
            val suffix = stableSuffix(sourceHash)
            return SamsungFontPackageSpec(
                packageName = PACKAGE_PREFIX + suffix,
                displayName = sanitizeDisplayName(displayName),
                droidName = "SamFont_$suffix",
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

        private fun stableSuffix(sourceHash: String): String {
            val clean = sourceHash.lowercase().filter { it in 'a'..'f' || it in '0'..'9' }
            return "f" + clean.take(8).padEnd(8, '0')
        }
    }
}
