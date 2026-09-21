package io.vela.core.scraper

import com.google.common.truth.Truth.assertThat
import io.vela.core.scraper.provider.LibretroDatabase
import io.vela.core.scraper.provider.RdbReader
import io.vela.core.scraper.provider.TitleSimilarity
import io.vela.core.scraper.provider.WikipediaSummaries
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class MetadataSourcesTest {

    // ---- RDB -------------------------------------------------------------------------------

    private fun str(s: String): ByteArray {
        val b = s.toByteArray(Charsets.UTF_8)
        return if (b.size < 32) byteArrayOf((0xa0 or b.size).toByte()) + b else byteArrayOf(0xd9.toByte(), b.size.toByte()) + b
    }

    private fun u16(v: Int) = byteArrayOf(0xcd.toByte(), (v shr 8).toByte(), v.toByte())

    private fun rdb(vararg records: ByteArray): ByteArray {
        val body = ByteArrayOutputStream().apply { records.forEach { write(it) } }.toByteArray()
        val trailer = byteArrayOf(0x81.toByte()) + str("count") + byteArrayOf(records.size.toByte())
        val header = ByteArrayOutputStream().apply {
            write("RARCHDB\u0000".toByteArray(Charsets.US_ASCII))
            write(ByteBuffer.allocate(8).putLong(16L + body.size).array())
        }.toByteArray()
        return header + body + trailer
    }

    private fun record(vararg fields: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(0x80 or fields.size)
        fields.forEach { (k, v) -> out.write(str(k)); out.write(v) }
        return out.toByteArray()
    }

    @Test
    fun `reads records and maps them to entries`() {
        val bytes = rdb(
            record(
                "name" to str("Chrono Trigger (USA)"), "developer" to str("Square"), "releaseyear" to u16(1995),
                "releasemonth" to byteArrayOf(8), "genre" to str("Role-Playing"), "users" to byteArrayOf(1),
                "franchise" to str("Chrono"), "esrb_rating" to str("K-A"), "crc" to byteArrayOf(0xc4.toByte(), 2, 0x2d, 0x20.toByte()),
            ),
            record("crc" to byteArrayOf(0xc4.toByte(), 1, 0x01)),
            record("name" to str("Terranigma (Europe)"), "description" to str("Terranigma (Europe)"), "rumble" to byteArrayOf(0xc3.toByte())),
        )
        val records = RdbReader.parse(bytes)
        assertThat(records).hasSize(3)
        assertThat(records[0]["crc"]).isEqualTo("2d20")

        val entries = LibretroDatabase.toEntries(records)
        assertThat(entries).hasSize(2)
        val chrono = entries.first()
        assertThat(chrono.name).isEqualTo("Chrono Trigger (USA)")
        assertThat(chrono.developer).isEqualTo("Square")
        assertThat(chrono.releaseYear).isEqualTo(1995)
        assertThat(chrono.releaseMonth).isEqualTo(8)
        assertThat(chrono.players).isEqualTo(1)
        assertThat(chrono.franchise).isEqualTo("Chrono")
        assertThat(chrono.ageRating).isEqualTo("K-A")
        assertThat(chrono.factCount).isEqualTo(6)
        // A "description" equal to the name is libretro filler, not a description.
        assertThat(entries[1].description).isNull()
    }

    @Test
    fun `rejects files without the header`() {
        val thrown = runCatching { RdbReader.parse("not an rdb file at all".toByteArray()) }.exceptionOrNull()
        assertThat(thrown).isInstanceOf(IllegalArgumentException::class.java)
    }

    // ---- Title similarity ------------------------------------------------------------------

    @Test
    fun `similarity ignores accents, punctuation and filler words`() {
        assertThat(TitleSimilarity.score("Pokemon - HeartGold Version", "Pokémon HeartGold and SoulSilver")).isEqualTo(1f)
        assertThat(TitleSimilarity.score("The Legend of Zelda - A Link to the Past", "The Legend of Zelda: A Link to the Past")).isEqualTo(1f)
        assertThat(TitleSimilarity.score("Mario Kart 64", "Mario Kart 8 Deluxe")).isLessThan(0.7f)
        assertThat(TitleSimilarity.score("Mario", "List of every Mario game ever released on a Nintendo console")).isEqualTo(0f)
    }

    @Test
    fun `wikipedia picks the game article and skips lists, franchises and films`() {
        val page = WikipediaSummaries.pickPage(
            "Pokemon - HeartGold Version",
            listOf("List of Pokémon video games", "Pokémon (franchise)", "Pokémon HeartGold and SoulSilver", "Pokémon Gold and Silver"),
        )
        assertThat(page).isEqualTo("Pokémon HeartGold and SoulSilver")
        assertThat(WikipediaSummaries.pickPage("Super Metroid", listOf("Metroid (franchise)", "Metroid Prime"))).isNull()
        assertThat(WikipediaSummaries.pickPage("Doom", listOf("Doom (1993 video game)", "Doom (film)"))).isEqualTo("Doom (1993 video game)")
    }

    @Test
    fun `summaries must read like a game article`() {
        assertThat(WikipediaSummaries.looksLikeGame("Chrono Trigger is a 1995 role-playing video game developed by Square.", "1995 video game")).isTrue()
        assertThat(WikipediaSummaries.looksLikeGame("Doom is a 2005 American science fiction action film.", "2005 film")).isFalse()
    }
}
