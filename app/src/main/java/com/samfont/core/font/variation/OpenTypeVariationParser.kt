package com.samfont.core.font.variation

import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.Charset
import kotlin.math.min

object OpenTypeVariationParser {
    fun parse(file: File, ttcIndex: Int = 0): FontVariationInfo? {
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val fontOffset = resolveFontOffset(raf, ttcIndex)
                val tables = readTableDirectory(raf, fontOffset)
                val fvar = tables["fvar"] ?: return null
                val names = tables["name"]?.let { readNameTable(raf, it.offset) }.orEmpty()

                raf.seek(fvar.offset)
                val major = raf.readUInt16()
                val minor = raf.readUInt16()
                if (major != 1 || minor != 0) {
                    return null
                }

                val axesArrayOffset = raf.readUInt16()
                raf.readUInt16()
                val axisCount = raf.readUInt16()
                val axisSize = raf.readUInt16()
                val instanceCount = raf.readUInt16()
                val instanceSize = raf.readUInt16()

                if (axisCount == 0) {
                    return FontVariationInfo(emptyList(), emptyList())
                }

                val axes = mutableListOf<FontVariationAxis>()
                val axisStart = fvar.offset + axesArrayOffset
                repeat(axisCount) { index ->
                    raf.seek(axisStart + (index * axisSize))
                    val tag = raf.readTag()
                    val minValue = raf.readFixed()
                    val defaultValue = raf.readFixed()
                    val maxValue = raf.readFixed()
                    val flags = raf.readUInt16()
                    val axisNameId = raf.readUInt16()

                    axes += FontVariationAxis(
                        tag = tag,
                        minValue = minValue,
                        defaultValue = defaultValue,
                        maxValue = maxValue,
                        flags = flags,
                        axisNameId = axisNameId,
                        name = names[axisNameId]
                    )
                }

                val instances = mutableListOf<FontNamedInstance>()
                val instanceStart = axisStart + (axisCount * axisSize)
                repeat(instanceCount) { index ->
                    raf.seek(instanceStart + (index * instanceSize))
                    val nameId = raf.readUInt16()
                    raf.readUInt16()
                    val coordinates = axes.associate { axis ->
                        axis.tag to raf.readFixed()
                    }
                    instances += FontNamedInstance(
                        nameId = nameId,
                        name = names[nameId],
                        coordinates = coordinates
                    )
                }

                FontVariationInfo(axes, instances)
            }
        }.getOrNull()
    }

    private fun resolveFontOffset(raf: RandomAccessFile, ttcIndex: Int): Long {
        raf.seek(0)
        val signature = raf.readTag()
        if (signature != "ttcf") {
            return 0L
        }

        raf.readUInt32()
        val fontCount = raf.readUInt32().toInt()
        val index = ttcIndex.coerceIn(0, (fontCount - 1).coerceAtLeast(0))
        raf.seek(12L + index * 4L)
        return raf.readUInt32()
    }

    private fun readTableDirectory(raf: RandomAccessFile, offset: Long): Map<String, TableRecord> {
        raf.seek(offset + 4)
        val numTables = raf.readUInt16()
        raf.skipBytes(6)

        val tables = mutableMapOf<String, TableRecord>()
        repeat(numTables) {
            val tag = raf.readTag()
            raf.readUInt32()
            val tableOffset = raf.readUInt32()
            val length = raf.readUInt32()
            tables[tag] = TableRecord(tableOffset, length)
        }

        return tables
    }

    private fun readNameTable(raf: RandomAccessFile, offset: Long): Map<Int, String> {
        raf.seek(offset)
        raf.readUInt16()
        val count = raf.readUInt16()
        val stringOffset = raf.readUInt16()
        val names = mutableMapOf<Int, String>()

        repeat(count) {
            val platformId = raf.readUInt16()
            raf.readUInt16()
            raf.readUInt16()
            val nameId = raf.readUInt16()
            val length = raf.readUInt16()
            val itemOffset = raf.readUInt16()
            val pos = raf.filePointer
            val absolute = offset + stringOffset + itemOffset

            raf.seek(absolute)
            val bytes = ByteArray(min(length, 4096))
            raf.readFully(bytes)
            val charset = if (platformId == 0 || platformId == 3) {
                Charset.forName("UTF-16BE")
            } else {
                Charsets.UTF_8
            }
            val value = runCatching { String(bytes, charset).trim('\u0000', ' ') }.getOrNull()
            if (!value.isNullOrBlank() && nameId !in names) {
                names[nameId] = value
            }

            raf.seek(pos)
        }

        return names
    }

    private fun RandomAccessFile.readTag(): String {
        val bytes = ByteArray(4)
        readFully(bytes)
        return String(bytes, Charsets.US_ASCII)
    }

    private fun RandomAccessFile.readUInt16(): Int {
        return readUnsignedShort()
    }

    private fun RandomAccessFile.readUInt32(): Long {
        return readInt().toLong() and 0xffffffffL
    }

    private fun RandomAccessFile.readFixed(): Float {
        val raw = readInt()
        return raw / 65536f
    }

    private data class TableRecord(
        val offset: Long,
        val length: Long
    )
}
