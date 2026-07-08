package com.samfont.core.samsung

import java.io.File
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * APK 安装器在 targetSdk 30+ 时要求 resources.arsc 必须未压缩并且 4 字节对齐。
 * 这里实现一个最小 zipalign：重新写 ZIP，并通过 Local File Header extra field
 * 给 STORED entry 增加 padding，保证数据区偏移满足 4 字节对齐。
 */
object ZipAligner {
    fun align(inputApk: File, outputApk: File, alignment: Int = 4): ApkResourceCheckResult {
        require(inputApk.exists()) { "Unsigned APK does not exist: ${inputApk.absolutePath}" }
        outputApk.parentFile?.mkdirs()
        outputApk.delete()

        ZipFile(inputApk).use { zip ->
            CountingOutputStream(FileOutputStream(outputApk)).use { counting ->
                ZipOutputStream(counting).use { output ->
                    zip.entries().asSequence().forEach { sourceEntry ->
                        val bytes = zip.getInputStream(sourceEntry).use { it.readBytes() }
                        val targetEntry = ZipEntry(sourceEntry.name).apply {
                            time = sourceEntry.time
                            comment = sourceEntry.comment
                        }

                        if (sourceEntry.isDirectory) {
                            output.putNextEntry(targetEntry)
                            output.closeEntry()
                            return@forEach
                        }

                        val mustStore = sourceEntry.method == ZipEntry.STORED ||
                            sourceEntry.name == "resources.arsc"
                        if (mustStore) {
                            targetEntry.method = ZipEntry.STORED
                            targetEntry.size = bytes.size.toLong()
                            targetEntry.compressedSize = bytes.size.toLong()
                            targetEntry.crc = crc32(bytes)
                            targetEntry.extra = alignmentExtra(
                                currentOffset = counting.bytesWritten,
                                entryName = sourceEntry.name,
                                alignment = alignment
                            )
                        } else {
                            targetEntry.method = ZipEntry.DEFLATED
                        }

                        output.putNextEntry(targetEntry)
                        output.write(bytes)
                        output.closeEntry()
                    }
                }
            }
        }

        return ApkStructureVerifier.requireValidResourcesArsc(outputApk)
    }

    private fun alignmentExtra(
        currentOffset: Long,
        entryName: String,
        alignment: Int
    ): ByteArray {
        val nameLength = entryName.toByteArray(Charsets.UTF_8).size
        val baseDataOffset = currentOffset + LOCAL_FILE_HEADER_SIZE + nameLength
        if (baseDataOffset % alignment == 0L) return ByteArray(0)

        for (payloadLength in 0 until alignment) {
            val extraLength = EXTRA_HEADER_SIZE + payloadLength
            if ((baseDataOffset + extraLength) % alignment == 0L) {
                return ByteArray(extraLength).apply {
                    // 自定义 extra field header id: 0x5346 ("FS")，长度为 little-endian。
                    this[0] = 0x46
                    this[1] = 0x53
                    this[2] = payloadLength.toByte()
                    this[3] = 0
                }
            }
        }
        return ByteArray(0)
    }

    private fun crc32(bytes: ByteArray): Long {
        val crc = CRC32()
        crc.update(bytes)
        return crc.value
    }

    private class CountingOutputStream(output: OutputStream) : FilterOutputStream(output) {
        var bytesWritten: Long = 0
            private set

        override fun write(b: Int) {
            out.write(b)
            bytesWritten += 1
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            bytesWritten += len.toLong()
        }
    }

    private const val LOCAL_FILE_HEADER_SIZE = 30
    private const val EXTRA_HEADER_SIZE = 4
}
