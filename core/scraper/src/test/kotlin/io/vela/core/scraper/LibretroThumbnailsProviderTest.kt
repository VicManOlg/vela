package io.vela.core.scraper

import com.google.common.truth.Truth.assertThat
import io.vela.core.catalog.PlatformCatalog
import io.vela.core.common.Outcome
import io.vela.core.model.ArtworkType
import io.vela.core.model.GameId
import io.vela.core.model.MetadataQuery
import io.vela.core.model.PlatformId
import io.vela.core.model.ScrapingSettings
import io.vela.core.scraper.provider.DescriptionSource
import io.vela.core.scraper.provider.GameDescription
import io.vela.core.scraper.provider.LibretroMetadataSource
import io.vela.core.scraper.provider.LibretroNameSource
import io.vela.core.scraper.provider.LibretroNames
import io.vela.core.scraper.provider.LibretroThumbnailsProvider
import io.vela.core.scraper.provider.RdbEntry
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LibretroThumbnailsProviderTest {

    private class FakeNames(private val bySystem: Map<String, List<String>>) : LibretroNameSource {
        override suspend fun names(system: String): LibretroNames? = bySystem[system]?.let(::LibretroNames)
    }

    private class FakeDatabase(private val entries: Map<String, RdbEntry>) : LibretroMetadataSource {
        override suspend fun lookup(system: String, fileName: String, title: String, preferredRegions: List<String>): RdbEntry? = entries[title]
    }

    private class FakeDescriptions(private val text: String?) : DescriptionSource {
        var calls = 0
        override suspend fun describe(title: String, language: String): GameDescription? {
            calls++
            return text?.let { GameDescription(it, "https://en.m.wikipedia.org/wiki/X", title, "Wikipedia") }
        }
    }

    private val noFacts = FakeDatabase(emptyMap())
    private val noText = FakeDescriptions(null)

    private val offline = LibretroThumbnailsProvider(PlatformCatalog(), FakeNames(emptyMap()), noFacts, noText)

    private val dsNames = mapOf(
        "Nintendo - Nintendo DS" to listOf(
            "Pocket Monsters - HeartGold (Japan)",
            "Pokemon - HeartGold Version (Europe)",
            "Pokemon - HeartGold Version (USA)",
        ),
    )

    @Test
    fun `sanitises characters libretro replaces with underscores`() {
        assertThat(LibretroThumbnailsProvider.sanitize("Legend of Zelda, The: A Link/Past & More?")).isEqualTo("Legend of Zelda, The_ A Link_Past _ More_")
    }

    @Test
    fun `without a listing it guesses the exact name, region variants and the cleaned title`() = runTest {
        val query = MetadataQuery(GameId(1), PlatformId("snes"), title = "Chrono Trigger", fileName = "Chrono Trigger (USA).sfc", preferredRegions = listOf("eu", "us"))
        val result = offline.search(query, ScrapingSettings(wikipediaDescriptions = false)) as Outcome.Success
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
        val provider = LibretroThumbnailsProvider(PlatformCatalog(), FakeNames(dsNames), noFacts, noText)
        val query = MetadataQuery(
            GameId(1), PlatformId("nds"),
            title = "Pokemon - HeartGold Version", fileName = "Pokemon - HeartGold Version (axekin.com).nds",
            preferredRegions = listOf("eu", "us", "wor", "jp"),
        )
        val result = provider.search(query, ScrapingSettings()) as Outcome.Success
        assertThat(result.value.map { it.title }).containsExactly(
            "Pokemon - HeartGold Version (Europe)", "Pokemon - HeartGold Version (USA)",
        ).inOrder()
        val box = result.value.first().artwork.first { it.type == ArtworkType.BOX_FRONT }
        assertThat(box.url).isEqualTo("https://thumbnails.libretro.com/Nintendo%20-%20Nintendo%20DS/Named_Boxarts/Pokemon%20-%20HeartGold%20Version%20%28Europe%29.png")
    }

    @Test
    fun `facts from the database and the description travel with every artwork candidate`() = runTest {
        val db = FakeDatabase(
            mapOf(
                "Pokemon - HeartGold Version" to RdbEntry(
                    name = "Pokemon - HeartGold Version (USA)", developer = "Game Freak", publisher = "Nintendo",
                    releaseYear = 2010, releaseMonth = 3, genre = "Role-Playing", players = 2, franchise = "Pokemon", ageRating = "E",
                ),
            ),
        )
        val wiki = FakeDescriptions("HeartGold is a 2009 remake of Pokemon Gold.")
        val provider = LibretroThumbnailsProvider(PlatformCatalog(), FakeNames(dsNames), db, wiki)
        val query = MetadataQuery(GameId(1), PlatformId("nds"), title = "Pokemon - HeartGold Version", fileName = "Pokemon - HeartGold Version (USA).nds")
        val result = provider.search(query, ScrapingSettings()) as Outcome.Success

        val meta = result.value.first().metadata
        assertThat(meta.developer).isEqualTo("Game Freak")
        assertThat(meta.releaseDate).isEqualTo("2010-03")
        assertThat(meta.genres).containsExactly("Role-Playing")
        assertThat(meta.players).isEqualTo("2")
        assertThat(meta.franchise).isEqualTo("Pokemon")
        assertThat(meta.ageRating).isEqualTo("E")
        assertThat(meta.description).startsWith("HeartGold is a 2009 remake")
        assertThat(meta.sourceUrl).contains("wikipedia.org")
        assertThat(result.value.all { it.metadata == meta }).isTrue()
    }

    @Test
    fun `wikipedia is not consulted when the user disabled descriptions`() = runTest {
        val wiki = FakeDescriptions("text")
        val provider = LibretroThumbnailsProvider(PlatformCatalog(), FakeNames(dsNames), noFacts, wiki)
        val query = MetadataQuery(GameId(1), PlatformId("nds"), title = "Pokemon - HeartGold Version", fileName = "Pokemon - HeartGold Version (USA).nds")
        val result = provider.search(query, ScrapingSettings(wikipediaDescriptions = false)) as Outcome.Success
        assertThat(wiki.calls).isEqualTo(0)
        assertThat(result.value.first().metadata.description).isNull()
    }

    @Test
    fun `a game absent from the listing but known to the database still yields its facts`() = runTest {
        val db = FakeDatabase(mapOf("Unknown Game" to RdbEntry(name = "Unknown Game (USA)", developer = "Someone")))
        val provider = LibretroThumbnailsProvider(PlatformCatalog(), FakeNames(dsNames), db, noText)
        val query = MetadataQuery(GameId(1), PlatformId("nds"), title = "Unknown Game", fileName = "Unknown Game.nds")
        val result = provider.search(query, ScrapingSettings(wikipediaDescriptions = false)) as Outcome.Success
        assertThat(result.value).hasSize(1)
        assertThat(result.value.single().artwork).isEmpty()
        assertThat(result.value.single().metadata.developer).isEqualTo("Someone")
    }

    @Test
    fun `platforms without a libretro name yield nothing`() = runTest {
        val query = MetadataQuery(GameId(1), PlatformId("switch"), title = "Game", fileName = "Game.nsp")
        val result = offline.search(query, ScrapingSettings()) as Outcome.Success
        assertThat(result.value).isEmpty()
    }
}
