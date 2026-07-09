package com.samfont.core.samsung

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SamsungFontXmlBuilderTest {
    @Test
    fun displayNameIsEscaped() {
        val spec = SamsungFontPackageSpec.create("中文 <Font> & \"Name\"")

        val xml = SamsungFontXmlBuilder.build(spec)

        assertTrue(xml.contains("Font_Name"))
        assertTrue(xml.contains("<filename>font_name.ttf</filename>"))
        assertTrue(xml.contains("<filename>font_name-Bold.ttf</filename>"))
        assertTrue(xml.contains("<droidname>DroidSans.ttf</droidname>"))
        assertTrue(xml.contains("<droidname>DroidSans-Bold.ttf</droidname>"))
        assertFalse(xml.contains("displayname=\"中文 <Font>"))
    }

    @Test
    fun generatedPackageNameUsesAssetBase() {
        val spec = SamsungFontPackageSpec.create("HarmonyOS Sans Italic Medium")

        assertEquals("HarmonyOS_Sans_Italic_Medium", spec.displayName)
        assertEquals("harmonyos_sans_italic_medium", spec.assetBase)
        assertEquals("com.monotype.android.font.harmonyos_sans_italic_medium", spec.packageName)
    }
}
