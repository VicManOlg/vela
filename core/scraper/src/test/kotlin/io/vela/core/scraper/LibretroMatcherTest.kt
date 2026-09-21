package io.vela.core.scraper

import com.google.common.truth.Truth.assertThat
import io.vela.core.scraper.provider.LibretroMatcher
import io.vela.core.scraper.provider.LibretroNames
import org.junit.Test

class LibretroMatcherTest {

    private val ds = LibretroNames(
        listOf(
            "Pocket Monsters - HeartGold (Japan)",
            "Pokemon - Edicion Oro HeartGold (Spain)",
            "Pokemon - HeartGold SoulSilver (USA)",
            "Pokemon - HeartGold Version (Europe)",
            "Pokemon - HeartGold Version (USA)",
            "Pokemon - HeartGold Version (USA) (Demo) (Kiosk)",
            "Dr. Mario Express (USA)",
            "Legend of Zelda, The - Phantom Hourglass (USA)",
        ),
    )

    @Test
    fun `key ignores tags, punctuation, case and articles`() {
        assertThat(LibretroMatcher.key("Legend of Zelda, The - Phantom Hourglass (USA) (Rev 1)"))
            .isEqualTo(LibretroMatcher.key("The Legend of Zelda: Phantom Hourglass"))
        assertThat(LibretroMatcher.key("Dr. Mario Express (USA)")).isEqualTo("dr mario express")
    }

    @Test
    fun `file with unknown tag matches by title and follows preferred regions`() {
        val ranked = LibretroMatcher.rank(
            title = "Pokemon - HeartGold Version",
            fileName = "Pokemon - HeartGold Version (axekin.com).nds",
            names = ds,
            preferredRegions = listOf("eu", "us", "wor", "jp"),
        )
        assertThat(ranked).containsExactly(
            "Pokemon - HeartGold Version (Europe)",
            "Pokemon - HeartGold Version (USA)",
            "Pokemon - HeartGold Version (USA) (Demo) (Kiosk)",
        ).inOrder()
    }

    @Test
    fun `the file's own region tag beats the preferred regions`() {
        val ranked = LibretroMatcher.rank(
            title = "Pokemon - HeartGold Version",
            fileName = "Pokemon HeartGold Version (USA) [b].nds",
            names = ds,
            preferredRegions = listOf("eu", "us"),
        )
        assertThat(ranked.first()).isEqualTo("Pokemon - HeartGold Version (USA)")
    }

    @Test
    fun `an exact file name is always first`() {
        val ranked = LibretroMatcher.rank(
            title = "Pokemon - HeartGold Version",
            fileName = "Pokemon - HeartGold Version (USA).nds",
            names = ds,
            preferredRegions = listOf("eu"),
        )
        assertThat(ranked.first()).isEqualTo("Pokemon - HeartGold Version (USA)")
    }

    @Test
    fun `titles with articles and colons still match`() {
        val ranked = LibretroMatcher.rank(
            title = "The Legend of Zelda: Phantom Hourglass",
            fileName = "The Legend of Zelda - Phantom Hourglass.nds",
            names = ds,
            preferredRegions = listOf("eu", "us"),
        )
        assertThat(ranked).containsExactly("Legend of Zelda, The - Phantom Hourglass (USA)")
    }

    @Test
    fun `unknown games yield nothing`() {
        val ranked = LibretroMatcher.rank("Totally Unknown Game", "Totally Unknown Game.nds", ds, listOf("eu"))
        assertThat(ranked).isEmpty()
    }
}
