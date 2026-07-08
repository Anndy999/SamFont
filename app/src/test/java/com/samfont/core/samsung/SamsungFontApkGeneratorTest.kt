package com.samfont.core.samsung

import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
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

    @Test
    fun zipAlignKeepsResourcesArscStoredAndAligned() {
        val tempDir = createTempDir(prefix = "samfont-align-test")
        val template = File(tempDir, "template.apk")
        val unsigned = File(tempDir, "unsigned.apk")
        val aligned = File(tempDir, "aligned.apk")
        val font = File(tempDir, "source.ttf")

        createZipWithResources(template)
        font.writeBytes(byteArrayOf(0x00, 0x01, 0x00, 0x00, 1, 2, 3, 4))

        SamsungFontApkGenerator.rewriteTemplateApk(
            templateApk = template,
            outputApk = unsigned,
            fontFile = font,
            fontEntryName = "assets/fonts/SamFont.ttf",
            xmlEntryName = "assets/xml/samfont.xml",
            xml = SamsungFontXmlBuilder.build(SamsungFontPackageSpec.create("测试字体", "ttf"))
        )

        ZipAligner.align(unsigned, aligned)
        val check = ApkStructureVerifier.checkResourcesArsc(aligned)
        assertTrue(check.hasResourcesArsc)
        assertTrue(check.isStored)
        assertEquals(0, check.dataOffsetMod4)
        assertTrue(check.isAligned4)
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

    private fun createZipWithResources(file: File) {
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zip.write("manifest".toByteArray())
            zip.closeEntry()

            val resources = "resources".toByteArray()
            val crc = CRC32().apply { update(resources) }.value
            zip.putNextEntry(ZipEntry("resources.arsc").apply {
                method = ZipEntry.STORED
                size = resources.size.toLong()
                compressedSize = resources.size.toLong()
                this.crc = crc
            })
            zip.write(resources)
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("assets/xml/samfont.xml"))
            zip.write("old".toByteArray())
            zip.closeEntry()
        }
    }
}
