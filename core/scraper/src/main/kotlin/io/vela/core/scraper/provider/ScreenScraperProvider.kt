package io.vela.core.scraper.provider

import io.vela.core.catalog.PlatformCatalog
import io.vela.core.common.Outcome
import io.vela.core.common.VelaError
import io.vela.core.model.ArtworkCandidate
import io.vela.core.model.ArtworkType
import io.vela.core.model.GameMetadata
import io.vela.core.model.MetadataMatch
import io.vela.core.model.MetadataProviderInfo
import io.vela.core.model.MetadataQuery
import io.vela.core.model.ScrapingSettings
import io.vela.core.scraper.BuildConfig
import io.vela.core.scraper.MetadataProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ScreenScraper.fr v2 `jeuInfos.php`. Requires the user's own account (ssid/sspassword) plus the
 * developer credentials compiled in from `secrets.properties`; without dev credentials the
 * provider reports itself unavailable. Matches by CRC when we have it, else by file name.
 */
@Singleton
class ScreenScraperProvider @Inject constructor(
    private val client: OkHttpClient,
    private val platforms: PlatformCatalog,
) : MetadataProvider {

    override val info = MetadataProviderInfo(
        id = ID,
        name = "ScreenScraper",
        description = "Full metadata, box art, logos, backgrounds, screenshots and videos. Needs a free screenscraper.fr account.",
        requiresCredentials = true,
        supportsHashLookup = true,
        artworkTypes = setOf(
            ArtworkType.BOX_FRONT, ArtworkType.BOX_BACK, ArtworkType.LOGO, ArtworkType.BACKGROUND,
            ArtworkType.SCREENSHOT, ArtworkType.TITLE_SCREEN, ArtworkType.MARQUEE, ArtworkType.VIDEO,
        ),
        website = "https://www.screenscraper.fr",
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun isAvailable(settings: ScrapingSettings): Boolean =
        BuildConfig.SCREENSCRAPER_DEV_ID.isNotBlank() && settings.screenScraperUser.isNotBlank()

    override suspend fun search(query: MetadataQuery, settings: ScrapingSettings): Outcome<List<MetadataMatch>> {
        val systemId = platforms[query.platformId]?.screenScraperId
            ?: return Outcome.success(emptyList())
        val url = "https://api.screenscraper.fr/api2/jeuInfos.php".toHttpUrl().newBuilder().apply {
            addQueryParameter("devid", BuildConfig.SCREENSCRAPER_DEV_ID)
            addQueryParameter("devpassword", BuildConfig.SCREENSCRAPER_DEV_PASSWORD)
            addQueryParameter("softname", "Vela")
            addQueryParameter("output", "json")
            addQueryParameter("ssid", settings.screenScraperUser)
            addQueryParameter("sspassword", settings.screenScraperPassword)
            addQueryParameter("systemeid", systemId.toString())
            addQueryParameter("romtype", "rom")
            addQueryParameter("romnom", query.fileName)
            query.fileSize?.let { addQueryParameter("romtaille", it.toString()) }
            query.crc32?.let { addQueryParameter("crc", it) }
        }.build()

        return withContext(Dispatchers.IO) {
            try {
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    when (response.code) {
                        200 -> Unit
                        401, 403 -> return@withContext Outcome.failure(VelaError.Unauthorized(ID))
                        404 -> return@withContext Outcome.success(emptyList())
                        429, 430, 431 -> return@withContext Outcome.failure(VelaError.RateLimited(ID, 60_000))
                        else -> return@withContext Outcome.failure(VelaError.Network("ScreenScraper HTTP ${response.code}"))
                    }
                    val body = response.body.string()
                    val root = json.parseToJsonElement(body).jsonObject
                    val jeu = root["response"]?.jsonObject?.get("jeu")?.jsonObject
                        ?: return@withContext Outcome.success(emptyList())
                    Outcome.success(listOf(parseGame(jeu, query, settings)))
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.w(e, "ScreenScraper request failed")
                Outcome.failure(VelaError.Network(e.message ?: "network error", e))
            }
        }
    }

    private fun parseGame(jeu: JsonObject, query: MetadataQuery, settings: ScrapingSettings): MetadataMatch {
        val regions = settings.preferredRegions + listOf("ss", "wor", "eu", "us", "jp")
        val lang = settings.language

        fun regional(key: String): String? = jeu[key]?.asArray()?.pickRegion(regions)
        fun localized(key: String): String? = jeu[key]?.asArray()?.pickLanguage(lang)

        val title = regional("noms") ?: query.title
        val genres = jeu["genres"]?.asArray()?.mapNotNull { g -> g.jsonObject["noms"]?.asArray()?.pickLanguage(lang) }.orEmpty()
        val rating = jeu["note"]?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull?.toFloatOrNull()?.let { it / 20f }
        val medias = jeu["medias"]?.asArray().orEmpty().mapNotNull { it as? JsonObject }
        val artwork = MEDIA_TYPES.mapNotNull { (ssType, type) ->
            val pick = medias.filter { it.str("type") == ssType }.let { list ->
                regions.firstNotNullOfOrNull { r -> list.firstOrNull { it.str("region") == r } } ?: list.firstOrNull()
            } ?: return@mapNotNull null
            val url = pick.str("url") ?: return@mapNotNull null
            ArtworkCandidate(type, url, region = pick.str("region"), format = pick.str("format"))
        }
        return MetadataMatch(
            providerId = ID,
            providerGameId = jeu.str("id") ?: "",
            title = title,
            score = if (query.crc32 != null) 1f else 0.8f,
            metadata = GameMetadata(
                title = title,
                description = localized("synopsis"),
                developer = jeu["developpeur"]?.jsonObject?.str("text"),
                publisher = jeu["editeur"]?.jsonObject?.str("text"),
                releaseDate = regional("dates"),
                genres = genres,
                players = jeu["joueurs"]?.jsonObject?.str("text"),
                rating = rating,
                region = query.preferredRegions.firstOrNull(),
                providerId = ID,
                providerGameId = jeu.str("id"),
                scrapedAt = System.currentTimeMillis(),
            ),
            artwork = artwork,
        )
    }

    private fun JsonElement.asArray(): JsonArray? = this as? JsonArray ?: (this as? JsonObject)?.let { JsonArray(listOf(it)) }
    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
    private fun JsonArray.pickRegion(regions: List<String>): String? {
        val objs = mapNotNull { it as? JsonObject }
        return regions.firstNotNullOfOrNull { r -> objs.firstOrNull { it.str("region") == r }?.str("text") } ?: objs.firstOrNull()?.str("text")
    }
    private fun JsonArray.pickLanguage(lang: String): String? {
        val objs = mapNotNull { it as? JsonObject }
        return objs.firstOrNull { it.str("langue") == lang }?.str("text")
            ?: objs.firstOrNull { it.str("langue") == "en" }?.str("text")
            ?: objs.firstOrNull()?.str("text")
    }

    companion object {
        const val ID = "screenscraper"
        private val MEDIA_TYPES = listOf(
            "box-2D" to ArtworkType.BOX_FRONT,
            "box-2D-back" to ArtworkType.BOX_BACK,
            "wheel-hd" to ArtworkType.LOGO,
            "wheel" to ArtworkType.LOGO,
            "fanart" to ArtworkType.BACKGROUND,
            "ss" to ArtworkType.SCREENSHOT,
            "sstitle" to ArtworkType.TITLE_SCREEN,
            "marquee" to ArtworkType.MARQUEE,
            "video-normalized" to ArtworkType.VIDEO,
            "video" to ArtworkType.VIDEO,
        )
    }
}
