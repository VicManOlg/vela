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
 * The no-account provider, built from three public sources:
 * - libretro-thumbnails for box art, screenshots and title screens, matched through the
 *   system's directory listing ([LibretroNameSource]) so files with odd tags still hit;
 * - libretro-database for developer, publisher, year, genre, players, franchise and age rating;
 * - Wikipedia for the description, when the user keeps it enabled.
 * When the listing cannot be fetched the artwork falls back to guessing the exact file name and
 * the cleaned title with the preferred region tags; the downloader tolerates the resulting 404s.
 */
@Singleton
class LibretroThumbnailsProvider @Inject constructor(
    private val platforms: PlatformCatalog,
    private val index: LibretroNameSource,
    private val database: LibretroMetadataSource,
    private val descriptions: DescriptionSource,
) : MetadataProvider {

    override val info = MetadataProviderInfo(
        id = ID,
        name = "libretro + Wikipedia",
        description = "Box art and screenshots from libretro-thumbnails, facts from libretro-database, descriptions from Wikipedia. No account needed.",
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

        val metadata = metadataFor(system, query, settings)
        // Facts are about the game, not about one cover: attach them to every candidate so the
        // best artwork match and the metadata always travel together.
        val matches = candidates.map { (name, score) -> match(system, name, score, metadata) }
        return Outcome.success(
            if (matches.isEmpty() && metadata.hasFacts()) listOf(match(system, stem, 0.3f, metadata, withArtwork = false)) else matches,
        )
    }

    private suspend fun metadataFor(system: String, query: MetadataQuery, settings: ScrapingSettings): GameMetadata {
        val entry = database.lookup(system, query.fileName, query.title, query.preferredRegions)
        val description = if (settings.wikipediaDescriptions) descriptions.describe(query.title, query.language) else null
        return GameMetadata(
            description = description?.text ?: entry?.description,
            developer = entry?.developer,
            publisher = entry?.publisher,
            releaseDate = entry?.releaseYear?.let { y -> entry.releaseMonth?.let { m -> "%04d-%02d".format(y, m) } ?: y.toString() },
            genres = entry?.genre?.split('/', ',', '|')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
            players = entry?.players?.toString(),
            region = entry?.region,
            franchise = entry?.franchise,
            ageRating = entry?.ageRating,
            sourceUrl = description?.url,
            providerId = ID,
            providerGameId = entry?.name ?: description?.pageTitle,
        )
    }

    private fun GameMetadata.hasFacts() =
        !description.isNullOrBlank() || !developer.isNullOrBlank() || !publisher.isNullOrBlank() || !releaseDate.isNullOrBlank() || genres.isNotEmpty()

    private fun match(system: String, name: String, score: Float, metadata: GameMetadata, withArtwork: Boolean = true) = MetadataMatch(
        providerId = ID,
        providerGameId = "$system/$name",
        title = name,
        score = score,
        metadata = metadata,
        artwork = if (!withArtwork) emptyList() else listOf(
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
