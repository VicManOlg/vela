package io.vela.core.scraper.provider

import io.vela.core.catalog.PlatformCatalog
import io.vela.core.common.Outcome
import io.vela.core.common.TitleCleaner
import io.vela.core.model.ArtworkCandidate
import io.vela.core.model.ArtworkType
import io.vela.core.model.GameMetadata
import io.vela.core.model.MetadataMatch
import io.vela.core.model.MetadataProviderInfo
import io.vela.core.model.MetadataQuery
import io.vela.core.model.ScrapingSettings
import io.vela.core.scraper.MetadataProvider
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * libretro-thumbnails: no account, no rate limit, works with No-Intro/Redump named files.
 * Only artwork (box, screenshot, title screen), no descriptive metadata. The URL is derived from
 * the file name, so the downloader must tolerate 404s for names that do not match the database.
 */
@Singleton
class LibretroThumbnailsProvider @Inject constructor(
    private val platforms: PlatformCatalog,
) : MetadataProvider {

    override val info = MetadataProviderInfo(
        id = ID,
        name = "libretro thumbnails",
        description = "Box art, screenshots and title screens by No-Intro/Redump file name. No account needed.",
        requiresCredentials = false,
        supportsHashLookup = false,
        artworkTypes = setOf(ArtworkType.BOX_FRONT, ArtworkType.SCREENSHOT, ArtworkType.TITLE_SCREEN),
        website = "https://thumbnails.libretro.com",
    )

    override fun isAvailable(settings: ScrapingSettings) = true

    override suspend fun search(query: MetadataQuery, settings: ScrapingSettings): Outcome<List<MetadataMatch>> {
        val system = platforms[query.platformId]?.libretroName ?: return Outcome.success(emptyList())
        val stem = TitleCleaner.stem(query.fileName)
        // Exact No-Intro name first, cleaned title as a weaker fallback.
        val names = listOf(stem to 0.9f, query.title to 0.5f).distinctBy { it.first }
        val matches = names.map { (name, score) ->
            val safe = sanitize(name)
            MetadataMatch(
                providerId = ID,
                providerGameId = "$system/$safe",
                title = name,
                score = score,
                metadata = GameMetadata(),
                artwork = listOf(
                    ArtworkCandidate(ArtworkType.BOX_FRONT, url(system, "Named_Boxarts", safe)),
                    ArtworkCandidate(ArtworkType.SCREENSHOT, url(system, "Named_Snaps", safe)),
                    ArtworkCandidate(ArtworkType.TITLE_SCREEN, url(system, "Named_Titles", safe)),
                ),
            )
        }
        return Outcome.success(matches)
    }

    private fun url(system: String, folder: String, name: String): String =
        "$BASE/${encode(system)}/$folder/${encode(name)}.png"

    private fun encode(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    companion object {
        const val ID = "libretro"
        private const val BASE = "https://thumbnails.libretro.com"
        private val forbidden = Regex("""[&*/:`<>?\\|"]""")

        /** libretro replaces characters that are illegal in file names with underscores. */
        fun sanitize(name: String): String = name.replace(forbidden, "_")
    }
}
