package io.vela.core.scraper.provider

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.vela.core.common.DispatcherProvider
import io.vela.core.common.TitleCleaner
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/** One game of a libretro `.rdb`: the No-Intro/Redump name plus whatever facts the database has. */
data class RdbEntry(
    val name: String,
    val developer: String? = null,
    val publisher: String? = null,
    val releaseYear: Int? = null,
    val releaseMonth: Int? = null,
    val genre: String? = null,
    val players: Int? = null,
    val franchise: String? = null,
    val ageRating: String? = null,
    val region: String? = null,
    val description: String? = null,
) {
    val factCount: Int
        get() = listOf(developer, publisher, releaseYear, genre, players, franchise, ageRating).count { it != null }
}

/** Looks a game up in libretro-database by file name, then by cleaned title. */
interface LibretroMetadataSource {
    suspend fun lookup(system: String, fileName: String, title: String, preferredRegions: List<String>): RdbEntry?
}

/**
 * libretro-database `.rdb` files (developer, publisher, year, genre, players, franchise, ESRB…),
 * downloaded once per system and parsed into memory; kept on disk for a month. Coverage varies a
 * lot by system, so callers must treat every field as optional.
 */
@Singleton
class LibretroDatabase @Inject constructor(
    @ApplicationContext context: Context,
    private val client: OkHttpClient,
    private val dispatchers: DispatcherProvider,
) : LibretroMetadataSource {

    private class Index(entries: List<RdbEntry>) {
        val byName: Map<String, RdbEntry> = entries.associateBy { it.name }
        val byKey: Map<String, List<RdbEntry>> = entries.groupBy { LibretroMatcher.key(it.name) }
    }

    private val dir = File(context.cacheDir, "libretro-rdb")
    private val memory = HashMap<String, Index>()
    private val failedAt = HashMap<String, Long>()
    private val mutex = Mutex()

    override suspend fun lookup(system: String, fileName: String, title: String, preferredRegions: List<String>): RdbEntry? {
        val index = index(system) ?: return null
        index.byName[TitleCleaner.stem(fileName)]?.let { return it }
        val candidates = index.byKey[LibretroMatcher.key(title)].orEmpty()
        if (candidates.isEmpty()) return null
        val ordered = LibretroMatcher.rank(title, fileName, LibretroNames(candidates.map { it.name }), preferredRegions, limit = candidates.size)
        val byName = candidates.associateBy { it.name }
        // The preferred-region entry wins unless a sibling knows more about the game.
        return ordered.mapNotNull { byName[it] }.firstOrNull { it.factCount > 0 } ?: byName[ordered.first()]
    }

    private suspend fun index(system: String): Index? = mutex.withLock {
        memory[system]?.let { return@withLock it }
        val lastFailure = failedAt[system]
        if (lastFailure != null && System.currentTimeMillis() - lastFailure < RETRY_MS) return@withLock null
        val loaded = withContext(dispatchers.io) { load(system) }
        if (loaded != null) memory[system] = loaded else failedAt[system] = System.currentTimeMillis()
        loaded
    }

    private fun load(system: String): Index? {
        val file = File(dir, system.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".rdb")
        val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < TTL_MS
        if (!fresh) {
            val ok = runCatching { download(system, file) }.onFailure { Timber.w(it, "libretro-database for %s unavailable", system) }.getOrDefault(false)
            if (!ok && !file.exists()) return null
        }
        return runCatching {
            val entries = toEntries(RdbReader.parse(file.readBytes()))
            Timber.i("libretro-database %s: %d entries", system, entries.size)
            Index(entries)
        }.onFailure {
            Timber.w(it, "libretro-database for %s unreadable, discarding", system)
            file.delete()
        }.getOrNull()
    }

    private fun download(system: String, target: File): Boolean {
        val url = "$BASE/${URLEncoder.encode(system, "UTF-8").replace("+", "%20")}.rdb"
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) {
                Timber.d("libretro-database %s -> HTTP %d", url, response.code)
                return false
            }
            dir.mkdirs()
            val tmp = File(target.path + ".part")
            tmp.outputStream().use { out -> response.body.byteStream().copyTo(out) }
            if (target.exists()) target.delete()
            return tmp.renameTo(target)
        }
    }

    companion object {
        private const val BASE = "https://raw.githubusercontent.com/libretro/libretro-database/master/rdb"
        private const val TTL_MS = 30L * 24 * 60 * 60 * 1000
        private const val RETRY_MS = 5L * 60 * 1000

        /** Maps raw RDB records to [RdbEntry], dropping records without a name. */
        fun toEntries(records: List<Map<String, Any?>>): List<RdbEntry> = records.mapNotNull { r ->
            val name = r["name"] as? String ?: return@mapNotNull null
            RdbEntry(
                name = name,
                developer = r.str("developer"),
                publisher = r.str("publisher"),
                releaseYear = r.int("releaseyear"),
                releaseMonth = r.int("releasemonth"),
                genre = r.str("genre"),
                players = r.int("users"),
                franchise = r.str("franchise"),
                ageRating = r.str("esrb_rating") ?: r.str("elspa_rating") ?: r.str("pegi_rating"),
                region = r.str("region"),
                description = r.str("description")?.takeIf { it != name },
            )
        }

        private fun Map<String, Any?>.str(key: String): String? = (this[key] as? String)?.trim()?.takeIf { it.isNotEmpty() }
        private fun Map<String, Any?>.int(key: String): Int? = (this[key] as? Number)?.toInt()?.takeIf { it > 0 }
    }
}
