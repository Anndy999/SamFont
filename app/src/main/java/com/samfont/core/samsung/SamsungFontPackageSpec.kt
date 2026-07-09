package com.samfont.core.samsung

import java.io.File
import java.util.Locale

data class SamsungFontPackageSpec(
    val userInputName: String,
    val displayName: String,
    val assetBase: String,
    val packageName: String,
    val regularFontFileName: String,
    val boldFontFileName: String,
    val fontXmlName: String,
    val versionCode: Long,
    val versionName: String
) {
    val regularFontEntry: String
        get() = "assets/fonts/$regularFontFileName"
    val boldFontEntry: String
        get() = "assets/fonts/$boldFontFileName"
    val xmlEntry: String
        get() = "assets/xml/$fontXmlName"

    companion object {
        const val PACKAGE_PREFIX = "com.monotype.android.font."
        const val PACKAGE_PLACEHOLDER =
            "com.monotype.android.font.PLACEHOLDER_FONT_NAME_HI_I_SUCK_AT_MAKING_APPS_SPACE_SPACE"
        const val DISPLAY_PLACEHOLDER =
            "PLACEHOLDER_FONT_DISPLAY_NAME_LONG_STRING_HERE_SPACE_SPACE_SPACE"
        const val ARSC_DISPLAY_PLACEHOLDER =
            "@@PLACEHOLDER_FONT_DISPLAY_NAME_LONG_STRING_HERE_SPACE_SPACE_SPACE"
        const val XML_PATH_PLACEHOLDER =
            "assets/xml/PLACEHOLDER_FONT_XML_NAME_LONG_STRING_HERE_SPACE_SPACE.xml"

        fun create(rawName: String): SamsungFontPackageSpec {
            val displayName = sanitizeUserFontName(rawName)
            val assetBase = toSamsungAssetBase(displayName)
            val packageName = "$PACKAGE_PREFIX$assetBase"
            require(packageName.length <= PACKAGE_PLACEHOLDER.length) {
                "字体名过长，请缩短后重试。"
            }
            require(displayName.length <= DISPLAY_PLACEHOLDER.length) {
                "字体显示名过长，请缩短后重试。"
            }

            return SamsungFontPackageSpec(
                userInputName = rawName,
                displayName = displayName,
                assetBase = assetBase,
                packageName = packageName,
                regularFontFileName = "$assetBase.ttf",
                boldFontFileName = "$assetBase-Bold.ttf",
                fontXmlName = "$assetBase.xml",
                versionCode = 1L,
                versionName = "1.0"
            )
        }

        fun sanitizeUserFontName(raw: String): String {
            val sanitized = raw
                .trim()
                .replace(' ', '_')
                .replace(Regex("[^A-Za-z0-9_-]"), "_")
                .replace(Regex("_+"), "_")
                .trim('_', '-')
            return sanitized.ifBlank { "My_Font" }
        }

        fun toSamsungAssetBase(displayName: String): String {
            return displayName
                .replace(Regex("[^a-zA-Z0-9]"), "_")
                .lowercase(Locale.ROOT)
                .replace(Regex("_+"), "_")
                .trim('_')
                .take(58)
                .ifBlank { "my_font" }
        }
    }
}

data class BuiltFontPackage(
    val apkFile: File,
    val userInputName: String,
    val displayName: String,
    val assetBase: String,
    val packageName: String,
    val xmlEntry: String,
    val regularFontEntry: String,
    val boldFontEntry: String?,
    val sourceFontSha256: String,
    val log: String
) {
    val apk: File
        get() = apkFile
}
