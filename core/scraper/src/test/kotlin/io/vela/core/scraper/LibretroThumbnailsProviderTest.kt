package io.vela.core.scraper

import com.google.common.truth.Truth.assertThat
import io.vela.core.catalog.PlatformCatalog
import io.vela.core.common.Outcome
import io.vela.core.model.ArtworkType
import io.vela.core.model.GameId
import io.vela.core.model.MetadataQuery
import io.vela.core.model.PlatformId
import io.vela.core.model.ScrapingSettings
import io.vela.core.scraper.provider.LibretroNameSource
import io.vela.core.scraper.provider.LibretroNames
import io.vela.core.scraper.provider.LibretroThumbnailsProvider
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LibretroThumbnailsProviderTest {

    private class FakeNames(private val bySystem: Map<String, List<String>>) : LibretroNameSource {
        override suspend fun names(system: String): LibretroNames? = bySystem[system]?.let(::LibretroNames)
    }

    private val offline = LibretroThumbnailsProvider(PlatformCatalog(), FakeNames(emptyMap()))

    private val online = LibretroThumbnailsProvider(
        PlatformCatalog(),
        FakeNames(
            mapOf(
                "Nintendo - Nintendo DS" to listOf(
                    "Pocket Monsters - HeartGold (Japan)",
                    "Pokemon - HeartGold Version (Europe)",
                    "Pokemon - HeartGold Version (USA)",
                ),
            ),
        ),
    )

    @Test
    fun `sanitises characters libretro replaces with underscores`() {
        assertThat(LibretroThumbnailsProvider.sanitize("Legend of Zelda, The: A Link/Past & More?")).isEqualTo("Legend of Zelda, The_ A Link_Past _ More_")
    }

    @Test
    fun `without a listing it guesses the exact name, region variants and the cleaned title`() = runTest {
        val query = MetadataQuery(GameId(1), PlatformId("snes"), title = "Chrono Trigger", fileName = "Chrono Trigger (USA).sfc", preferredRegions = listOf("eu", "us"))
        val result = offline.search(query, ScrapingSettings()) as Outcome.Success
        val matches = result.value
        assertThat(matches.map { it.title }).containsExactly(
            "Chrono Trigger (USA)", "Chrono Trigger (Europe)", "Chrono Trigger",
        ).inOrder()
        assertThat(matches.map { it.score }).isInOrder(reverseOrder<Float>())
        val box = matches.first().artwork.first { it.type == ArtworkType.BOX_FRONT }
        assertThat(box.url).isEqualTo("https://thumbnails.libretro.com/Nintendo%20-%20Super%20Nintendo%20Entertainment%20System/Named_Boxarts/Chrono%20Trigger%20%28USA%29.png")
    }

    @Test
    fun `with a listing a file with a foreign tag matches the preferred region entry`() = runTest {
        val query = MetadataQuery(
            GameId(1), PlatformId("nds"),
            title = "Pokemon - HeartGold Version", fileName = "Pokemon - HeartGold Version (axekin.com).nds",
            preferredRegions = listOf("eu", "us", "wor", "jp"),
        )
        val result = online.search(query, ScrapingSettings()) as Outcome.Success
        val matches = result.value
        assertThat(matches.map { it.title }).containsExactly(
            "Pokemon - HeartGold Version (Europe)", "Pokemon - HeartGold Version (USA)",
        ).inOrder()
        val box = matches.first().artwork.first { it.type == ArtworkType.BOX_FRONT }
        assertThat(box.url).isEqualTo("https://thumbnails.libretro.com/Nintendo%20-%20Nintendo%20DS/Named_Boxarts/Pokemon%20-%20HeartGold%20Version%20%28Europe%29.png")
    }

    @Test
    fun `with a listing an unknown game yields nothing instead of guesses`() = runTest {
        val query = MetadataQuery(GameId(1), PlatformId("nds"), title = "Unknown Game", fileName = "Unknown Game.nds")
        val result = online.search(query, ScrapingSettings()) as Outcome.Success
        assertThat(result.value).isEmpty()
    }

    @Test
    fun `platforms without a libretro name yield nothing`() = runTest {
        val query = MetadataQuery(GameId(1), PlatformId("switch"), title = "Game", fileName = "Game.nsp")
        val result = offline.search(query, ScrapingSettings()) as Outcome.Success
        assertThat(result.value).isEmpty()
    }
}
