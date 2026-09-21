package io.vela.core.scraper.provider

import io.vela.core.common.DispatcherProvider
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

data class GameDescription(val text: String, val url: String?, val pageTitle: String, val source: String)

/** Free-text description of a game from a public source; null when nothing trustworthy is found. */
interface DescriptionSource {
    suspend fun describe(title: String, language: String): GameDescription?
}

/**
 * Wikipedia article lead as the game description (CC BY-SA). A title search picks the article
 * whose name overlaps the game's title; the summary must look like a game article, otherwise
 * nothing is returned rather than a film or a band with the same name.
 */
@Singleton
class WikipediaSummaries @Inject constructor(
    private val client: OkHttpClient,
    private val dispatchers: DispatcherProvider,
) : DescriptionSource {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun describe(title: String, language: String): GameDescription? = withContext(dispatchers.io) {
        val lang = language.lowercase().takeIf { it.matches(Regex("[a-z]{2,3}")) } ?: "en"
        try {
            val searchUrl = "https://$lang.wikipedia.org/w/api.php?action=query&list=search&format=json&srlimit=6&srprop=&srsearch=${encode("$title video game")}"
            val search = get(searchUrl) ?: return@withContext null
            val candidates = json.parseToJsonElement(search).jsonObject["query"]?.jsonObject?.get("search")?.jsonArray
                ?.mapNotNull { it.jsonObject["title"]?.jsonPrimitive?.contentOrNull }
                .orEmpty()
            val page = pickPage(title, candidates) ?: return@withContext null
            val summary = get("https://$lang.wikipedia.org/api/rest_v1/page/summary/${encode(page.replace(' ', '_'))}") ?: return@withContext null
            val obj = json.parseToJsonElement(summary).jsonObject
            if (obj["type"]?.jsonPrimitive?.contentOrNull != "standard") return@withContext null
            val extract = obj["extract"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val shortDescription = obj["description"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (extract.length < 40 || !looksLikeGame(extract, shortDescription)) return@withContext null
            val url = obj["content_urls"]?.jsonObject?.get("mobile")?.jsonObject?.get("page")?.jsonPrimitive?.contentOrNull
            GameDescription(extract, url, page, "Wikipedia")
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.d(e, "Wikipedia lookup failed for %s", title)
            null
        }
    }

    private fun get(url: String): String? =
        client.newCall(Request.Builder().url(url).header("Accept", "application/json").build()).execute().use { response ->
            if (!response.isSuccessful) {
                Timber.d("Wikipedia %s -> HTTP %d", url, response.code)
                null
            } else {
                response.body.string()
            }
        }

    private fun encode(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    companion object {
        private val notAGame = listOf("(franchise)", "(series)", "(character)", "(film)", "(tv series)", "(album)", "(song)", "(company)", "(disambiguation)")

        /** The article whose title overlaps the game title most; list/franchise/film pages are skipped. */
        fun pickPage(title: String, candidates: List<String>): String? {
            val usable = candidates.filter { c -> notAGame.none { c.lowercase().endsWith(it) } && !c.startsWith("List of") }
            return TitleSimilarity.best(title, usable, threshold = 0.7f) { it.substringBefore(" (") }
        }

        fun looksLikeGame(extract: String, shortDescription: String): Boolean {
            val head = extract.take(320).lowercase()
            return shortDescription.contains("game", ignoreCase = true) || "video game" in head || " game" in head || "videojuego" in head
        }
    }
}
