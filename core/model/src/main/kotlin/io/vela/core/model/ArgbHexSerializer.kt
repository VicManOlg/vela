package io.vela.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** Serialises ARGB colours as `#AARRGGBB` (or `#RRGGBB`) strings so JSON catalogs stay readable. */
object ArgbHexSerializer : KSerializer<Long> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ArgbHex", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Long) {
        encoder.encodeString("#" + (value and 0xFFFFFFFFL).toString(16).uppercase().padStart(8, '0'))
    }

    override fun deserialize(decoder: Decoder): Long = parse(decoder.decodeString())

    fun parse(text: String): Long {
        val hex = text.trim().removePrefix("#").removePrefix("0x")
        val value = hex.toLong(16)
        return if (hex.length <= 6) value or 0xFF000000L else value
    }
}
