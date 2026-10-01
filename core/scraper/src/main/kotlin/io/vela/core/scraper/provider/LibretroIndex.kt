package io.vela.core.scraper.provider

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.vela.core.common.DispatcherProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import io.vela.core.scraper.executeCancellable
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.net.URLDecoder
import javax.inject.Inject
import javax.inject.Singleton

/** Source of the file names present in a libretro-thumbnails system folder. */
interface LibretroNameSource {
    /** Names (without extension) in the system's `Named_Boxarts` folder, or null when the listing is unavailable. */
    suspend fun names(system: String): LibretroNames?
}

/**
 * Downloads the directory listing of `thumbnails.libretro.com/<system>/Named_Boxarts/` once per
 * system, keeps it in memory for the process and on disk for a week. A listing is ~2 MB of HTML
 * for the biggest systems, which is far cheaper than guessing file names with 404s.
 */
@Singleton
class LibretroIndex @Inject constructor(
    @ApplicationContext context: Context,
    private val client: OkHttpClient,
    private val dispatchers: DispatcherProvider,
) : LibretroNameSource {

    private val dir = File(context.cacheDir, "libretro-index")
    private val memory = HashMap<String, LibretroNames>()
    private val failedAt = HashMap<String, Long>()
    private val mutex = Mutex()

    override suspend fun names(system: String): LibretroNames? = mutex.withLock {
        memory[system]?.let { return@withLock it }
        val lastFailure = failedAt[system]
        if (lastFailure != null && System.currentTimeMillis() - lastFailure < RETRY_MS) return@withLock null
        val loaded = withContext(dispatchers.io) { load(system) }
        if (loaded != null) memory[system] = loaded else failedAt[system] = System.currentTimeMillis()
        loaded
    }

    private suspend fun load(system: String): LibretroNames? {
        val file = File(dir, system.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".txt")
        val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < TTL_MS
        if (fresh) readCached(file)?.let { return it }

        val downloaded = try {
            download(system)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.w(e, "libretro index for %s unavailable", system)
            null
        }
        if (downloaded != null) {
            runCatching {
                dir.mkdirs()
                val tmp = File(file.path + ".part")
                tmp.writeText(downloaded.joinToString("\n"))
                if (file.exists()) file.delete()
                tmp.renameTo(file)
            }.onFailure { Timber.w(it, "Could not cache libretro index for %s", system) }
            Timber.i("libretro index for %s: %d names", system, downloaded.size)
            return LibretroNames(downloaded)
        }
        // A stale copy beats nothing.
        return if (file.exists()) readCached(file) else null
    }

    private fun readCached(file: File): LibretroNames? =
        runCatching { LibretroNames(file.readLines().filter { it.isNotBlank() }) }
            .getOrNull()
            ?.takeIf { it.size > 0 }

    private suspend fun download(system: String): List<String>? {
        val url = "${LibretroThumbnailsProvider.BASE}/${LibretroThumbnailsProvider.encode(system)}/Named_Boxarts/"
        client.newCall(Request.Builder().url(url).build()).executeCancellable { response ->
            if (!response.isSuccessful) {
                Timber.d("libretro index %s -> HTTP %d", url, response.code)
                return null
            }
            return parseListing(response.body.string())
        }
    }

    companion object {
        private const val TTL_MS = 7L * 24 * 60 * 60 * 1000
        private const val RETRY_MS = 5L * 60 * 1000
        private val href = Regex("""href="([^"]+)\.png"""")

        /** File names (decoded, without `.png`) linked from an Apache-style directory listing. */
        fun parseListing(html: String): List<String> =
            href.findAll(html)
                .map { decode(it.groupValues[1]) }
                .filter { it.isNotBlank() && !it.contains('/') }
                .distinct()
                .toList()

        // URLDecoder turns '+' into a space; libretro encodes spaces as %20 and keeps '+' literal.
        private fun decode(s: String): String = URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")
    }
}
