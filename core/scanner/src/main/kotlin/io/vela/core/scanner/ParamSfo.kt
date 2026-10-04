package io.vela.core.scanner

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Reads PARAM.SFO, the small key/value table every PS3 game carries (PS3_GAME/PARAM.SFO on a disc
 * dump, PARAM.SFO next to USRDIR/ on an installed game). Layout: "\0PSF" magic, then key-table
 * offset, data-table offset and entry count as little-endian u32 at 8, 12 and 16; 16-byte entries
 * from 20 (key offset u16, format u16, length u32, max length u32, data offset u32).
 */
internal object ParamSfo {

    /** The game's name for a PS3 game folder, or null when it has no readable PARAM.SFO. */
    fun titleOfGameFolder(folder: File): String? {
        val sfo = listOf(File(folder, "PS3_GAME/PARAM.SFO"), File(folder, "PARAM.SFO")).firstOrNull { it.isFile } ?: return null
        if (sfo.length() > MAX_BYTES) return null
        return runCatching { title(sfo.readBytes()) }.getOrNull()
    }

    fun title(bytes: ByteArray): String? = values(bytes)["TITLE"]
        ?.replace('\n', ' ')
        ?.replace("™", "")
        ?.replace("®", "")
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    fun values(bytes: ByteArray): Map<String, String> {
        if (bytes.size < 20 || bytes[0] != 0.toByte() || bytes[1] != 'P'.code.toByte() || bytes[2] != 'S'.code.toByte() || bytes[3] != 'F'.code.toByte()) return emptyMap()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val keyTable = buf.getInt(8)
        val dataTable = buf.getInt(12)
        val count = buf.getInt(16)
        if (count !in 0..256) return emptyMap()
        val out = LinkedHashMap<String, String>()
        for (i in 0 until count) {
            val entry = 20 + i * 16
            if (entry + 16 > bytes.size) break
            val keyOffset = buf.getShort(entry).toInt() and 0xFFFF
            val format = buf.getShort(entry + 2).toInt() and 0xFFFF
            val length = buf.getInt(entry + 4)
            val dataOffset = buf.getInt(entry + 12)
            val key = cString(bytes, keyTable + keyOffset) ?: continue
            val start = dataTable + dataOffset
            if (start < 0 || length < 0 || start + length > bytes.size) continue
            // 0x0204 and 0x0004 are UTF-8 strings; 0x0404 is an integer we have no use for.
            if (format == 0x0404) continue
            out[key] = String(bytes, start, length, Charsets.UTF_8).trimEnd('\u0000')
        }
        return out
    }

    private fun cString(bytes: ByteArray, at: Int): String? {
        if (at < 0 || at >= bytes.size) return null
        var end = at
        while (end < bytes.size && bytes[end] != 0.toByte()) end++
        return String(bytes, at, end - at, Charsets.US_ASCII)
    }

    private const val MAX_BYTES = 64 * 1024L
}
