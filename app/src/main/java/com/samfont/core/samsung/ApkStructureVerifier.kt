package com.samfont.core.samsung

import java.io.File
import java.io.RandomAccessFile
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

data class ApkResourceCheckResult(
    val hasResourcesArsc: Boolean,
    val compressionMethod: String,
    val isStored: Boolean,
    val dataOffset: Long,
    val dataOffsetMod4: Long,
    val isAligned4: Boolean
) {
    fun toLog(prefix: String): String = buildString {
        appendLine(prefix)
        appendLine("resources.arsc method=$compressionMethod")
        appendLine("resources.arsc dataOffset=$dataOffset")
        appendLine("resources.arsc dataOffset % 4=$dataOffsetMod4")
        appendLine("resources.arsc aligned4=$isAligned4")
    }
}

object ApkStructureVerifier {
    fun checkResourcesArsc(apkFile: File): ApkResourceCheckResult {
        ZipFile(apkFile).use { zip ->
            val entry = zip.getEntry("resources.arsc") ?: return ApkResourceCheckResult(
                hasResourcesArsc = false,
                compressionMethod = "MISSING",
                isStored = false,
                dataOffset = -1,
                dataOffsetMod4 = -1,
                isAligned4 = false
            )

            val dataOffset = findDataOffset(apkFile, "resources.arsc")
            val method = when (entry.method) {
                ZipEntry.STORED -> "STORED"
                ZipEntry.DEFLATED -> "DEFLATED"
                else -> entry.method.toString()
            }
            return ApkResourceCheckResult(
                hasResourcesArsc = true,
                compressionMethod = method,
                isStored = entry.method == ZipEntry.STORED,
                dataOffset = dataOffset,
                dataOffsetMod4 = if (dataOffset >= 0) dataOffset % 4 else -1,
                isAligned4 = dataOffset >= 0 && dataOffset % 4 == 0L
            )
        }
    }

    fun requireValidResourcesArsc(apkFile: File): ApkResourceCheckResult {
        val result = checkResourcesArsc(apkFile)
        require(result.hasResourcesArsc && result.isStored && result.isAligned4) {
            "Generated APK is invalid: resources.arsc must be uncompressed and 4-byte aligned."
        }
        return result
    }

    private fun findDataOffset(apkFile: File, targetName: String): Long {
        RandomAccessFile(apkFile, "r").use { raf ->
            val eocd = findEndOfCentralDirectory(raf)
            if (eocd < 0) return -1
            raf.seek(eocd + 10)
            val entryCount = readUInt16LE(raf)
            raf.seek(eocd + 16)
            val centralDirectoryOffset = readUInt32LE(raf)
            raf.seek(centralDirectoryOffset)

            repeat(entryCount) {
                val signature = readUInt32LE(raf)
                if (signature != CENTRAL_DIRECTORY_HEADER) return -1
                raf.skipBytes(6)
                raf.skipBytes(2)
                raf.skipBytes(2)
                raf.skipBytes(2)
                raf.skipBytes(4)
                raf.skipBytes(4)
                raf.skipBytes(4)
                val nameLength = readUInt16LE(raf)
                val extraLength = readUInt16LE(raf)
                val commentLength = readUInt16LE(raf)
                raf.skipBytes(8)
                val localHeaderOffset = readUInt32LE(raf)
                val nameBytes = ByteArray(nameLength)
                raf.readFully(nameBytes)
                val name = String(nameBytes, Charsets.UTF_8)
                raf.skipBytes(extraLength + commentLength)
                if (name == targetName) {
                    return readLocalDataOffset(raf, localHeaderOffset)
                }
            }
        }
        return -1
    }

    private fun readLocalDataOffset(raf: RandomAccessFile, localHeaderOffset: Long): Long {
        raf.seek(localHeaderOffset)
        val signature = readUInt32LE(raf)
        if (signature != LOCAL_FILE_HEADER) return -1
        raf.seek(localHeaderOffset + 26)
        val nameLength = readUInt16LE(raf)
        val extraLength = readUInt16LE(raf)
        return localHeaderOffset + 30 + nameLength + extraLength
    }

    private fun findEndOfCentralDirectory(raf: RandomAccessFile): Long {
        val maxCommentLength = 65_535
        val minEocdLength = 22
        val fileLength = raf.length()
        val searchStart = (fileLength - minEocdLength).coerceAtLeast(0)
        val searchEnd = (fileLength - minEocdLength - maxCommentLength).coerceAtLeast(0)
        var position = searchStart
        while (position >= searchEnd) {
            raf.seek(position)
            if (readUInt32LE(raf) == EOCD_HEADER) return position
            position--
        }
        return -1
    }

    private fun readUInt16LE(raf: RandomAccessFile): Int {
        val b0 = raf.read()
        val b1 = raf.read()
        if (b0 < 0 || b1 < 0) return -1
        return b0 or (b1 shl 8)
    }

    private fun readUInt32LE(raf: RandomAccessFile): Long {
        val b0 = raf.read()
        val b1 = raf.read()
        val b2 = raf.read()
        val b3 = raf.read()
        if (b0 < 0 || b1 < 0 || b2 < 0 || b3 < 0) return -1
        return (b0.toLong() and 0xff) or
            ((b1.toLong() and 0xff) shl 8) or
            ((b2.toLong() and 0xff) shl 16) or
            ((b3.toLong() and 0xff) shl 24)
    }

    private const val LOCAL_FILE_HEADER = 0x04034b50L
    private const val CENTRAL_DIRECTORY_HEADER = 0x02014b50L
    private const val EOCD_HEADER = 0x06054b50L
}
