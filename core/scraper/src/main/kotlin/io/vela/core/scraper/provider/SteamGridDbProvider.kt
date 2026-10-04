package io.vela.core.scraper.provider

import io.vela.core.common.Outcome
import io.vela.core.common.VelaError
import io.vela.core.model.ArtworkCandidate
import io.vela.core.model.ArtworkType
import io.vela.core.model.GameMetadata
import io.vela.core.model.MetadataMatch
import io.vela.core.model.MetadataProviderInfo
import io.vela.core.model.MetadataQuery
import io.vela.core.model.ScrapingSettings
import io.vela.core.scraper.MetadataProvider
import io.vela.core.common.DispatcherProvider
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import io.vela.core.scraper.executeCancellable
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SteamGridDB: community logos, hero backgrounds, vertical grids and icons. Needs the user's own
 * API key (free at steamgriddb.com/profile/preferences/api). The game is found by title; the
 * first static, non-humour, non-NSFW asset of each type is offered.
 */
@Singleton
class SteamGridDbProvider @Inject constructor(
    private val client: OkHttpClient,
    private val dispatchers: DispatcherProvider,
) : MetadataProvider {

    override val info = MetadataProviderInfo(
        id = ID,
        name = "SteamGridDB",
        description = "Game logos, hero backgrounds and alternative covers. Needs a free API key from steamgriddb.com.",
        requiresCredentials = true,
        supportsHashLookup = false,
        artworkTypes = setOf(ArtworkType.LOGO, ArtworkType.BACKGROUND, ArtworkType.BOX_FRONT, ArtworkType.ICON),
        website = "https://www.steamgriddb.com",
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun isAvailable(settings: ScrapingSettings): Boolean = keyFor(settings).isNotBlank()

    /** The key from Settings, else the one compiled in from secrets.properties (personal builds). */
    private fun keyFor(settings: ScrapingSettings): String = settings.steamGridDbApiKey.trim().ifBlank { io.vela.core.scraper.BuildConfig.STEAMGRIDDB_API_KEY.trim() }

    override suspend fun search(query: MetadataQuery, settings: ScrapingSettings): Outcome<List<MetadataMatch>> = withContext(dispatchers.io) {
        val key = keyFor(settings)
        try {
            // A Steam game (GameNative) is a file named after its app id: look the game up by that
            // id, which also gives its real name, instead of searching for "1145360".
            val steamAppId = query.fileName.substringBeforeLast('.').takeIf { query.platformId.value == STEAM_PLATFORM && it.isNotEmpty() && it.all(Char::isDigit) }
            val picked = if (steamAppId != null) {
                val game = when (val r = call("$BASE/games/steam/$steamAppId", key)) {
                    is Outcome.Failure -> return@withContext r
                    is Outcome.Success -> r.value["data"] as? JsonObject
                } ?: return@withContext Outcome.success(emptyList())
                val id = game.int("id") ?: return@withContext Outcome.success(emptyList())
                Candidate(id, game.str("name") ?: query.title, verified = true)
            } else {
                val results = when (val r = call("$BASE/search/autocomplete/${encode(query.title)}", key)) {
                    is Outcome.Failure -> return@withContext r
                    is Outcome.Success -> r.value.data()
                }
                val games = results.mapNotNull { it as? JsonObject }
                    .mapNotNull { g -> g.int("id")?.let { id -> Candidate(id, g.str("name") ?: return@mapNotNull null, g["verified"]?.jsonPrimitive?.booleanOrNull == true) } }
                TitleSimilarity.best(query.title, games.sortedByDescending { it.verified }, threshold = 0.6f) { it.name }
                    ?: return@withContext Outcome.success(emptyList())
            }

            val artwork = ArrayList<ArtworkCandidate>(4)
            for ((path, type, extra) in ASSETS) {
                when (val r = call("$BASE/$path/game/${picked.id}?types=static$extra", key)) {
                    is Outcome.Failure -> if (r.error is VelaError.Unauthorized || r.error is VelaError.RateLimited) return@withContext r else continue
                    is Outcome.Success -> firstUsable(r.value.data(), type)?.let(artwork::add)
                }
            }
            Outcome.success(
                listOf(
                    MetadataMatch(
                        providerId = ID,
                        providerGameId = picked.id.toString(),
                        title = picked.name,
                        score = if (steamAppId != null) 1f else TitleSimilarity.score(query.title, picked.name),
                        // The id lookup is exact, so its name can replace the file's number.
                        metadata = if (steamAppId != null) GameMetadata(title = picked.name) else GameMetadata(),
                        artwork = artwork,
                    ),
                ),
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.w(e, "SteamGridDB request failed")
            Outcome.failure(VelaError.Network(e.message ?: "network error", e))
        }
    }

    private data class Candidate(val id: Int, val name: String, val verified: Boolean)

    private suspend fun call(url: String, key: String): Outcome<JsonObject> =
        client.newCall(Request.Builder().url(url).header("Authorization", "Bearer $key").build()).executeCancellable { response ->
            when (response.code) {
                200 -> Outcome.success(json.parseToJsonElement(response.body.string()).jsonObject)
                401, 403 -> Outcome.failure(VelaError.Unauthorized(ID))
                404 -> Outcome.success(JsonObject(emptyMap()))
                429 -> Outcome.failure(VelaError.RateLimited(ID, 60_000))
                else -> Outcome.failure(VelaError.Network("SteamGridDB HTTP ${response.code}"))
            }
        }

    private fun JsonObject.data(): List<Any?> = this["data"]?.jsonArray?.toList().orEmpty()

    private fun firstUsable(items: List<Any?>, type: ArtworkType): ArtworkCandidate? =
        items.mapNotNull { it as? JsonObject }
            .firstOrNull { it["nsfw"]?.jsonPrimitive?.booleanOrNull != true && it["humor"]?.jsonPrimitive?.booleanOrNull != true && it.str("url") != null }
            ?.let { ArtworkCandidate(type, it.str("url")!!, width = it.int("width"), height = it.int("height"), format = it.str("mime")?.substringAfter('/')) }

    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
    private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull
    private fun encode(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    companion object {
        const val ID = "steamgriddb"
        private const val STEAM_PLATFORM = "steam"
        private const val BASE = "https://www.steamgriddb.com/api/v2"
        private val ASSETS = listOf(
            Triple("logos", ArtworkType.LOGO, ""),
            Triple("heroes", ArtworkType.BACKGROUND, ""),
            Triple("grids", ArtworkType.BOX_FRONT, "&dimensions=600x900,342x482"),
            Triple("icons", ArtworkType.ICON, ""),
        )
    }
}
