package com.samfont.core.samsung

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SamsungFontXmlBuilderTest {
    @Test
    fun displayNameIsEscaped() {
        val spec = SamsungFontPackageSpec(
            packageName = SamsungFontPackageSpec.FIXED_PACKAGE_NAME,
            displayName = "中文 <Font> & \"Name\"",
            fontFileName = "SamFont.ttf",
            fontXmlName = "samfont.xml",
            versionCode = 1,
            versionName = "1.0"
        )

        val xml = SamsungFontXmlBuilder.build(spec)

        assertTrue(xml.contains("中文 &lt;Font&gt; &amp; &quot;Name&quot;"))
        assertTrue(xml.contains("<file>SamFont.ttf</file>"))
        assertFalse(xml.contains("displayname=\"中文 <Font>"))
    }
}
