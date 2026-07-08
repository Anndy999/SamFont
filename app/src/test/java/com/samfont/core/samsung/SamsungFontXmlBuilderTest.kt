package com.samfont.core.samsung

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SamsungFontXmlBuilderTest {
    @Test
    fun displayNameIsEscaped() {
        val spec = SamsungFontPackageSpec(
            packageName = SamsungFontPackageSpec.FIXED_PACKAGE_NAME,
            displayName = "中文 <Font> & \"Name\"",
            droidName = "SamFont_test",
            fontFileName = "SamFont.ttf",
            fontXmlName = "samfont.xml",
            versionCode = 1,
            versionName = "1.0"
        )

        val xml = SamsungFontXmlBuilder.build(spec)

        assertTrue(xml.contains("中文 &lt;Font&gt; &amp; &quot;Name&quot;"))
        assertTrue(xml.contains("<filename>SamFont.ttf</filename>"))
        assertTrue(xml.contains("<droidname>SamFont_test</droidname>"))
        assertFalse(xml.contains("displayname=\"中文 <Font>"))
    }

    @Test
    fun generatedPackageNameKeepsTemplateLengthAndDroidName() {
        val spec = SamsungFontPackageSpec.create("HarmonyOS Sans", "ttf", "62ae1dd2ce8f")

        assertEquals(SamsungFontPackageSpec.FIXED_PACKAGE_NAME.length, spec.packageName.length)
        assertTrue(spec.packageName.endsWith(".f62ae1dd2"))
        assertEquals("SamFont_f62ae1dd2", spec.droidName)
    }
}
