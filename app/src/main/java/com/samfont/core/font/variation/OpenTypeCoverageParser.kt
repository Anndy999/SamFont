package com.samfont.core.font.variation

import java.io.File
import java.io.RandomAccessFile

object OpenTypeCoverageParser {
    fun hasCodePoints(file: File, codePoints: List<Int>, ttcIndex: Int = 0): Boolean {
        return codePoints.all { hasCodePoint(file, it, ttcIndex) }
    }

    private fun hasCodePoint(file: File, codePoint: Int, ttcIndex: Int): Boolean {
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val fontOffset = resolveFontOffset(raf, ttcIndex)
                val cmapOffset = readTableDirectory(raf, fontOffset)["cmap"] ?: return false
                readCmap(raf, cmapOffset, codePoint)
            }
        }.getOrDefault(false)
    }

    private fun readCmap(raf: RandomAccessFile, cmapOffset: Long, codePoint: Int): Boolean {
        raf.seek(cmapOffset)
        raf.readUInt16()
        val count = raf.readUInt16()
        val subtables = mutableListOf<Long>()

        repeat(count) {
            raf.readUInt16()
            raf.readUInt16()
            subtables += cmapOffset + raf.readUInt32()
        }

        val formats = subtables.mapNotNull { offset ->
            raf.seek(offset)
            val format = raf.readUInt16()
            format to offset
        }

        formats.firstOrNull { it.first == 12 }?.let { (_, offset) ->
            if (readFormat12(raf, offset, codePoint)) return true
        }
        formats.firstOrNull { it.first == 4 }?.let { (_, offset) ->
            if (readFormat4(raf, offset, codePoint)) return true
        }

        return false
    }

    private fun readFormat12(raf: RandomAccessFile, offset: Long, codePoint: Int): Boolean {
        raf.seek(offset + 12)
        val groups = raf.readUInt32().toInt()
        repeat(groups) {
            val start = raf.readUInt32()
            val end = raf.readUInt32()
            raf.readUInt32()
            if (codePoint.toLong() in start..end) {
                return true
            }
        }
        return false
    }

    private fun readFormat4(raf: RandomAccessFile, offset: Long, codePoint: Int): Boolean {
        if (codePoint > 0xFFFF) return false
        raf.seek(offset + 6)
        val segCount = raf.readUInt16() / 2
        raf.skipBytes(6)
        val endCodes = IntArray(segCount) { raf.readUInt16() }
        raf.readUInt16()
        val startCodes = IntArray(segCount) { raf.readUInt16() }
        val idDeltas = IntArray(segCount) { raf.readUInt16() }
        val idRangeOffsetStart = raf.filePointer
        val idRangeOffsets = IntArray(segCount) { raf.readUInt16() }

        for (index in 0 until segCount) {
            if (codePoint in startCodes[index]..endCodes[index]) {
                if (idRangeOffsets[index] == 0) {
                    return ((codePoint + idDeltas[index]) and 0xFFFF) != 0
                }
                val glyphOffset = idRangeOffsetStart +
                    index * 2L +
                    idRangeOffsets[index] +
                    (codePoint - startCodes[index]) * 2L
                if (glyphOffset < raf.length()) {
                    raf.seek(glyphOffset)
                    return raf.readUInt16() != 0
                }
            }
        }
        return false
    }

    private fun resolveFontOffset(raf: RandomAccessFile, ttcIndex: Int): Long {
        raf.seek(0)
        val signature = raf.readTag()
        if (signature != "ttcf") return 0L
        raf.readUInt32()
        val fontCount = raf.readUInt32().toInt()
        val index = ttcIndex.coerceIn(0, (fontCount - 1).coerceAtLeast(0))
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

    private fun RandomAccessFile.readTag(): String {
        val bytes = ByteArray(4)
        readFully(bytes)
        return String(bytes, Charsets.US_ASCII)
    }

    private fun RandomAccessFile.readUInt16(): Int = readUnsignedShort()

    private fun RandomAccessFile.readUInt32(): Long = readInt().toLong() and 0xffffffffL
}
