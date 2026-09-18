package io.vela.core.scraper

import com.google.common.truth.Truth.assertThat
import io.vela.core.catalog.PlatformCatalog
import io.vela.core.common.Outcome
import io.vela.core.model.ArtworkType
import io.vela.core.model.GameId
import io.vela.core.model.MetadataQuery
import io.vela.core.model.PlatformId
import io.vela.core.model.ScrapingSettings
import io.vela.core.scraper.provider.LibretroThumbnailsProvider
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LibretroThumbnailsProviderTest {

    private val provider = LibretroThumbnailsProvider(PlatformCatalog())

    @Test
    fun `sanitises characters libretro replaces with underscores`() {
        assertThat(LibretroThumbnailsProvider.sanitize("Legend of Zelda, The: A Link/Past & More?")).isEqualTo("Legend of Zelda, The_ A Link_Past _ More_")
    }

    @Test
    fun `builds exact-name and cleaned-title candidates for a known system`() = runTest {
        val query = MetadataQuery(GameId(1), PlatformId("snes"), title = "Chrono Trigger", fileName = "Chrono Trigger (USA).sfc")
        val result = provider.search(query, ScrapingSettings()) as Outcome.Success
        val matches = result.value
        assertThat(matches).hasSize(2)
        assertThat(matches.first().score).isGreaterThan(matches.last().score)
        val box = matches.first().artwork.first { it.type == ArtworkType.BOX_FRONT }
        assertThat(box.url).isEqualTo("https://thumbnails.libretro.com/Nintendo%20-%20Super%20Nintendo%20Entertainment%20System/Named_Boxarts/Chrono%20Trigger%20%28USA%29.png")
    }

    @Test
    fun `platforms without a libretro name yield nothing`() = runTest {
        val query = MetadataQuery(GameId(1), PlatformId("switch"), title = "Game", fileName = "Game.nsp")
        val result = provider.search(query, ScrapingSettings()) as Outcome.Success
        assertThat(result.value).isEmpty()
    }
}
