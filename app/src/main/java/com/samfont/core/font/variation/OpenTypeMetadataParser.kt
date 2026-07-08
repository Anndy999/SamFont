package com.samfont.core.font.variation

import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.Charset
import kotlin.math.min

data class OpenTypeMetadata(
    val familyName: String?,
    val subfamilyName: String?,
    val fullName: String?,
    val weight: Int,
    val ttcFaceCount: Int
)

object OpenTypeMetadataParser {
    fun parse(file: File, ttcIndex: Int = 0): OpenTypeMetadata {
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val faceCount = readTtcFaceCount(raf)
                val fontOffset = resolveFontOffset(raf, ttcIndex)
                val tables = readTableDirectory(raf, fontOffset)
                val names = tables["name"]?.let { readNameTable(raf, it) }.orEmpty()
                val weight = tables["OS/2"]?.let { readOs2Weight(raf, it) } ?: 400

                OpenTypeMetadata(
                    familyName = names[1],
                    subfamilyName = names[2],
                    fullName = names[4] ?: names[1],
                    weight = weight,
                    ttcFaceCount = faceCount
                )
            }
        }.getOrElse {
            OpenTypeMetadata(
                familyName = null,
                subfamilyName = null,
                fullName = null,
                weight = 400,
                ttcFaceCount = 1
            )
        }
    }

    fun ttcFaceCount(file: File): Int {
        return runCatching {
            RandomAccessFile(file, "r").use { readTtcFaceCount(it) }
        }.getOrDefault(1)
    }

    private fun readTtcFaceCount(raf: RandomAccessFile): Int {
        raf.seek(0)
        if (raf.readTag() != "ttcf") return 1
        raf.readUInt32()
        return raf.readUInt32().toInt().coerceAtLeast(1)
    }

    private fun resolveFontOffset(raf: RandomAccessFile, ttcIndex: Int): Long {
        raf.seek(0)
        if (raf.readTag() != "ttcf") return 0L
        raf.readUInt32()
        val count = raf.readUInt32().toInt()
        val index = ttcIndex.coerceIn(0, (count - 1).coerceAtLeast(0))
        raf.seek(12L + index * 4L)
        return raf.readUInt32()
    }

    private fun readTableDirectory(raf: RandomAccessFile, offset: Long): Map<String, Long> {
        raf.seek(offset + 4)
        val numTables = raf.readUInt16()
        raf.skipBytes(6)
        val tables = mutableMapOf<String, Long>()

        repeat(numTables) {
            val tag = raf.readTag()
            raf.readUInt32()
            val tableOffset = raf.readUInt32()
            raf.readUInt32()
            tables[tag] = tableOffset
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
            raf.seek(offset + stringOffset + itemOffset)

            val bytes = ByteArray(min(length, 4096))
            raf.readFully(bytes)
            val charset = if (platformId == 0 || platformId == 3) Charset.forName("UTF-16BE") else Charsets.UTF_8
            val value = runCatching { String(bytes, charset).trim('\u0000', ' ') }.getOrNull()
            if (!value.isNullOrBlank() && nameId !in names) {
                names[nameId] = value
            }
            raf.seek(pos)
        }

        return names
    }

    private fun readOs2Weight(raf: RandomAccessFile, offset: Long): Int {
        raf.seek(offset + 4)
        return raf.readUInt16().coerceIn(1, 1000)
    }

    private fun RandomAccessFile.readTag(): String {
        val bytes = ByteArray(4)
        readFully(bytes)
        return String(bytes, Charsets.US_ASCII)
    }

    private fun RandomAccessFile.readUInt16(): Int = readUnsignedShort()

    private fun RandomAccessFile.readUInt32(): Long = readInt().toLong() and 0xffffffffL
}
