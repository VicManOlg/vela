package io.vela.core.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TitleCleanerTest {

    @Test
    fun `strips region tags and dump markers`() {
        assertThat(TitleCleaner.clean("Super Mario World (USA) [!].sfc")).isEqualTo("Super Mario World")
        assertThat(TitleCleaner.clean("Chrono Trigger (USA) (Rev 1).zip")).isEqualTo("Chrono Trigger")
    }

    @Test
    fun `moves trailing article to the front`() {
        assertThat(TitleCleaner.clean("Legend of Zelda, The - A Link to the Past (Europe).sfc"))
            .isEqualTo("The Legend of Zelda - A Link to the Past")
    }

    @Test
    fun `removes disc markers and underscores`() {
        assertThat(TitleCleaner.clean("Final_Fantasy_VII (Disc 1).chd")).isEqualTo("Final Fantasy VII")
        assertThat(TitleCleaner.discNumber("Final Fantasy VII (Disc 2).chd")).isEqualTo(2)
    }

    @Test
    fun `sort key drops leading article and punctuation`() {
        assertThat(TitleCleaner.sortKey("The Legend of Zelda: Ocarina of Time")).isEqualTo("legend of zelda ocarina of time")
    }

    @Test
    fun `detects region`() {
        assertThat(TitleCleaner.region("Game (Europe).gba")).isEqualTo("eu")
        assertThat(TitleCleaner.region("Game (USA, Europe).gba")).isEqualTo("eu")
        assertThat(TitleCleaner.region("Game (Japan).gba")).isEqualTo("jp")
        assertThat(TitleCleaner.region("Game.gba")).isNull()
    }

    @Test
    fun `stem and extension`() {
        assertThat(TitleCleaner.stem("/roms/snes/Game.Name.sfc")).isEqualTo("Game.Name")
        assertThat(TitleCleaner.extension("Game.SFC")).isEqualTo("sfc")
        assertThat(TitleCleaner.extension("noext")).isEmpty()
    }
}
