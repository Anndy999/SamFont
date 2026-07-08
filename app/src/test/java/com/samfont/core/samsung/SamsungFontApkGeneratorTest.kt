package com.samfont.core.samsung

import java.io.File
import java.util.zip.ZipFile
import org.junit.Assert.assertTrue
import org.junit.Test

class SamsungFontApkGeneratorTest {
    @Test
    fun rewriteTemplateAddsFontAndXmlEntries() {
        val tempDir = createTempDir(prefix = "samfont-apk-test")
        val template = File(tempDir, "template.apk")
        val font = File(tempDir, "source.ttf")
        val output = File(tempDir, "generated.apk")

        createZip(template, mapOf("AndroidManifest.xml" to "manifest", "assets/xml/samfont.xml" to "old"))
        font.writeBytes(byteArrayOf(0x00, 0x01, 0x00, 0x00, 1, 2, 3, 4))

        SamsungFontApkGenerator.rewriteTemplateApk(
            templateApk = template,
            outputApk = output,
            fontFile = font,
            fontEntryName = "assets/fonts/SamFont.ttf",
            xmlEntryName = "assets/xml/samfont.xml",
            xml = SamsungFontXmlBuilder.build(SamsungFontPackageSpec.create("测试字体", "ttf"))
        )

        assertTrue(output.exists())
        assertTrue(output.length() > 0)
        ZipFile(output).use { zip ->
            assertTrue(zip.getEntry("assets/fonts/SamFont.ttf") != null)
            assertTrue(zip.getEntry("assets/xml/samfont.xml") != null)
        }
    }

    private fun createZip(file: File, entries: Map<String, String>) {
        java.util.zip.ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(java.util.zip.ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
    }
}
