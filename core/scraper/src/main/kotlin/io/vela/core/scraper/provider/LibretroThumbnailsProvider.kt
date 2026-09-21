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
 * Only artwork (box, screenshot, title screen), no descriptive metadata.
 *
 * Names come from the system's directory listing ([LibretroNameSource]) so files with extra tags,
 * a missing region or slightly different punctuation still match. When the listing cannot be
 * fetched the provider falls back to guessing the exact file name and the cleaned title with the
 * preferred region tags; the downloader tolerates the resulting 404s.
 */
@Singleton
class LibretroThumbnailsProvider @Inject constructor(
    private val platforms: PlatformCatalog,
    private val index: LibretroNameSource,
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
        val stem = sanitize(TitleCleaner.stem(query.fileName))
        val names = index.names(system)

        val candidates: List<Pair<String, Float>> = if (names != null) {
            LibretroMatcher.rank(query.title, query.fileName, names, query.preferredRegions)
                .mapIndexed { i, name -> name to if (name == stem) 1f else 0.9f - 0.05f * i }
        } else {
            val regionGuesses = query.preferredRegions
                .mapNotNull(LibretroMatcher::regionTag)
                .mapIndexed { i, tag -> sanitize("${query.title} ($tag)") to 0.7f - 0.05f * i }
            (listOf(stem to 0.9f) + regionGuesses + listOf(sanitize(query.title) to 0.5f)).distinctBy { it.first }
        }

        return Outcome.success(candidates.map { (name, score) -> match(system, name, score) })
    }

    private fun match(system: String, name: String, score: Float) = MetadataMatch(
        providerId = ID,
        providerGameId = "$system/$name",
        title = name,
        score = score,
        metadata = GameMetadata(),
        artwork = listOf(
            ArtworkCandidate(ArtworkType.BOX_FRONT, url(system, "Named_Boxarts", name)),
            ArtworkCandidate(ArtworkType.SCREENSHOT, url(system, "Named_Snaps", name)),
            ArtworkCandidate(ArtworkType.TITLE_SCREEN, url(system, "Named_Titles", name)),
        ),
    )

    private fun url(system: String, folder: String, name: String): String =
        "$BASE/${encode(system)}/$folder/${encode(name)}.png"

    companion object {
        const val ID = "libretro"
        internal const val BASE = "https://thumbnails.libretro.com"
        private val forbidden = Regex("""[&*/:`<>?\\|"]""")

        internal fun encode(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

        /** libretro replaces characters that are illegal in file names with underscores. */
        fun sanitize(name: String): String = name.replace(forbidden, "_")
    }
}
