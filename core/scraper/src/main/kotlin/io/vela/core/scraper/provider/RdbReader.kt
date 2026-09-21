package io.vela.core.scraper.provider

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Reads libretro-database `.rdb` files: a `RARCHDB\0` header, a big-endian offset to the trailer,
 * then a stream of MessagePack maps (one per game) followed by a `{count}` trailer. Only the
 * MessagePack subset RetroArch writes is supported; binary fields come back as lowercase hex.
 */
object RdbReader {

    private const val MAGIC = "RARCHDB"

    fun parse(bytes: ByteArray): List<Map<String, Any?>> {
        require(bytes.size >= 16 && String(bytes, 0, MAGIC.length, Charsets.US_ASCII) == MAGIC) { "Not an RDB file" }
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val trailer = buf.getLong(8).coerceAtMost(bytes.size.toLong())
        buf.position(16)
        val out = ArrayList<Map<String, Any?>>(4096)
        while (buf.position() < trailer && buf.hasRemaining()) {
            val value = readValue(buf)
            if (value is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                out += value as Map<String, Any?>
            }
        }
        return out
    }

    private fun readValue(buf: ByteBuffer): Any? {
        val b = buf.get().toInt() and 0xff
        return when {
            b <= 0x7f -> b
            b in 0x80..0x8f -> readMap(buf, b and 0x0f)
            b in 0x90..0x9f -> List(b and 0x0f) { readValue(buf) }
            b in 0xa0..0xbf -> readString(buf, b and 0x1f)
            b == 0xc0 -> null
            b == 0xc2 -> false
            b == 0xc3 -> true
            b == 0xc4 -> readHex(buf, u8(buf))
            b == 0xc5 -> readHex(buf, u16(buf))
            b == 0xc6 -> readHex(buf, u32(buf))
            b == 0xcc -> u8(buf)
            b == 0xcd -> u16(buf)
            b == 0xce -> buf.getInt().toLong() and 0xffffffffL
            b == 0xcf -> buf.getLong()
            b == 0xd0 -> buf.get().toInt()
            b == 0xd1 -> buf.getShort().toInt()
            b == 0xd2 -> buf.getInt()
            b == 0xd3 -> buf.getLong()
            b == 0xd9 -> readString(buf, u8(buf))
            b == 0xda -> readString(buf, u16(buf))
            b == 0xdb -> readString(buf, u32(buf))
            b == 0xdc -> List(u16(buf)) { readValue(buf) }
            b == 0xdd -> List(u32(buf)) { readValue(buf) }
            b == 0xde -> readMap(buf, u16(buf))
            b == 0xdf -> readMap(buf, u32(buf))
            b >= 0xe0 -> b - 0x100
            else -> throw IllegalArgumentException("Unsupported MessagePack byte 0x${b.toString(16)} at ${buf.position() - 1}")
        }
    }

    private fun readMap(buf: ByteBuffer, size: Int): Map<String, Any?> {
        val map = LinkedHashMap<String, Any?>(size * 2)
        repeat(size) {
            val key = readValue(buf).toString()
            map[key] = readValue(buf)
        }
        return map
    }

    private fun readString(buf: ByteBuffer, length: Int): String {
        val bytes = ByteArray(length)
        buf.get(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    private fun readHex(buf: ByteBuffer, length: Int): String {
        val bytes = ByteArray(length)
        buf.get(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun u8(buf: ByteBuffer): Int = buf.get().toInt() and 0xff
    private fun u16(buf: ByteBuffer): Int = buf.getShort().toInt() and 0xffff
    private fun u32(buf: ByteBuffer): Int {
        val v = buf.getInt().toLong() and 0xffffffffL
        require(v <= Int.MAX_VALUE) { "Length too large" }
        return v.toInt()
    }
}
