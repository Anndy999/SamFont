package com.samfont.core.samsung

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 用于 clean-room patch Android binary XML / resources.arsc 的字符串池。
 * SamFonts 的模板也是通过替换模板中的长占位字符串生成字体包。
 */
object BinaryStringPoolPatcher {
    fun patchBinaryXml(bytes: ByteArray, replacements: Map<String, String>): ByteArray {
        return patchContainer(bytes, 0, bytes.size, replacements)
    }

    fun patchResourceTable(bytes: ByteArray, replacements: Map<String, String>): ByteArray {
        return patchContainer(bytes, 0, bytes.size, replacements)
    }

    private fun patchContainer(
        bytes: ByteArray,
        start: Int,
        end: Int,
        replacements: Map<String, String>
    ): ByteArray {
        val type = readU16(bytes, start)
        val headerSize = readU16(bytes, start + 2)
        val size = readU32(bytes, start + 4)
        if (start + size > end || headerSize < 8) return bytes.copyOfRange(start, end)
        if (type == RES_STRING_POOL_TYPE) {
            return patchStringPool(bytes.copyOfRange(start, start + size), replacements)
        }

        val canContainChildren = type == RES_XML_TYPE || type == RES_TABLE_TYPE
        if (!canContainChildren) return bytes.copyOfRange(start, start + size)

        val output = ByteArrayOutputStream()
        val header = bytes.copyOfRange(start, start + headerSize)
        output.write(header)

        var cursor = start + headerSize
        while (cursor < start + size) {
            if (cursor + 8 > start + size) {
                output.write(bytes, cursor, start + size - cursor)
                cursor = start + size
                break
            }
            val childSize = readU32(bytes, cursor + 4)
            if (childSize <= 0 || cursor + childSize > start + size) {
                output.write(bytes, cursor, start + size - cursor)
                cursor = start + size
                break
            }
            val child = patchContainer(bytes, cursor, cursor + childSize, replacements)
            output.write(child)
            cursor += childSize
        }

        val patched = output.toByteArray()
        writeU32(patched, 4, patched.size)
        return patched
    }

    private fun patchStringPool(chunk: ByteArray, replacements: Map<String, String>): ByteArray {
        val stringCount = readU32(chunk, 8)
        val styleCount = readU32(chunk, 12)
        if (styleCount != 0) return chunk

        val flags = readU32(chunk, 16)
        val stringsStart = readU32(chunk, 20)
        val utf8 = flags and UTF8_FLAG != 0
        val offsetsStart = 28
        val strings = (0 until stringCount).map { index ->
            val offset = readU32(chunk, offsetsStart + index * 4)
            decodeString(chunk, stringsStart + offset, utf8)
        }
        val patchedStrings = strings.map { replacements[it] ?: it }
        if (patchedStrings == strings) return chunk

        val stringData = ByteArrayOutputStream()
        val newOffsets = mutableListOf<Int>()
        patchedStrings.forEach { value ->
            newOffsets += stringData.size()
            stringData.write(encodeString(value, utf8))
        }
        while (stringData.size() % 4 != 0) stringData.write(0)

        val headerSize = readU16(chunk, 2)
        val newSize = stringsStart + stringData.size()
        val output = ByteArray(newSize)
        chunk.copyInto(output, endIndex = headerSize)
        writeU32(output, 4, newSize)
        writeU32(output, 20, stringsStart)
        writeU32(output, 24, 0)
        newOffsets.forEachIndexed { index, offset ->
            writeU32(output, offsetsStart + index * 4, offset)
        }
        val data = stringData.toByteArray()
        data.copyInto(output, destinationOffset = stringsStart)
        return output
    }

    private fun decodeString(bytes: ByteArray, offset: Int, utf8: Boolean): String {
        return if (utf8) {
            val first = readLength8(bytes, offset)
            val second = readLength8(bytes, first.nextOffset)
            String(bytes, second.nextOffset, second.length, Charsets.UTF_8)
        } else {
            val length = readLength16(bytes, offset)
            String(bytes, length.nextOffset, length.length * 2, Charsets.UTF_16LE)
        }
    }

    private fun encodeString(value: String, utf8: Boolean): ByteArray {
        return if (utf8) {
            val utf16Length = value.length
            val utf8Bytes = value.toByteArray(Charsets.UTF_8)
            ByteArrayOutputStream().apply {
                writeLength8(utf16Length)
                writeLength8(utf8Bytes.size)
                write(utf8Bytes)
                write(0)
            }.toByteArray()
        } else {
            val utf16Bytes = value.toByteArray(Charsets.UTF_16LE)
            ByteArrayOutputStream().apply {
                writeLength16(value.length)
                write(utf16Bytes)
                write(0)
                write(0)
            }.toByteArray()
        }
    }

    private fun readLength8(bytes: ByteArray, offset: Int): LengthResult {
        val first = bytes[offset].toInt() and 0xff
        return if (first and 0x80 != 0) {
            val second = bytes[offset + 1].toInt() and 0xff
            LengthResult(((first and 0x7f) shl 8) or second, offset + 2)
        } else {
            LengthResult(first, offset + 1)
        }
    }

    private fun readLength16(bytes: ByteArray, offset: Int): LengthResult {
        val first = readU16(bytes, offset)
        return if (first and 0x8000 != 0) {
            val second = readU16(bytes, offset + 2)
            LengthResult(((first and 0x7fff) shl 16) or second, offset + 4)
        } else {
            LengthResult(first, offset + 2)
        }
    }

    private fun ByteArrayOutputStream.writeLength8(value: Int) {
        if (value > 0x7f) {
            write(((value shr 8) and 0x7f) or 0x80)
            write(value and 0xff)
        } else {
            write(value)
        }
    }

    private fun ByteArrayOutputStream.writeLength16(value: Int) {
        if (value > 0x7fff) {
            writeShort(((value shr 16) and 0x7fff) or 0x8000)
            writeShort(value and 0xffff)
        } else {
            writeShort(value)
        }
    }

    private fun ByteArrayOutputStream.writeShort(value: Int) {
        write(value and 0xff)
        write((value shr 8) and 0xff)
    }

    private fun readU16(bytes: ByteArray, offset: Int): Int {
        return ByteBuffer.wrap(bytes, offset, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xffff
    }

    private fun readU32(bytes: ByteArray, offset: Int): Int {
        return ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int
    }

    private fun writeU32(bytes: ByteArray, offset: Int, value: Int) {
        ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(value)
    }

    private data class LengthResult(val length: Int, val nextOffset: Int)

    private const val RES_STRING_POOL_TYPE = 0x0001
    private const val RES_XML_TYPE = 0x0003
    private const val RES_TABLE_TYPE = 0x0002
    private const val UTF8_FLAG = 0x00000100
}
