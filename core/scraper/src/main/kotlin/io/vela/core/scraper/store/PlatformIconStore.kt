package io.vela.core.scraper.store

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.vela.core.catalog.PlatformCatalog
import io.vela.core.common.DispatcherProvider
import io.vela.core.model.PlatformId
import io.vela.core.model.ThemePlatformIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import io.vela.core.scraper.executeCancellable
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import timber.log.Timber
import java.io.File
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * System icons for platform tiles, one PNG per platform per icon set, kept under
 * `files/platform-icons/<set>/<platformId>.png`. Icons come from the RetroArch assets repository
 * (CC BY 4.0) by default; the theme decides the set and the URL template.
 */
@Singleton
class PlatformIconStore @Inject constructor(
    @ApplicationContext context: Context,
    private val client: OkHttpClient,
    private val platforms: PlatformCatalog,
    private val dispatchers: DispatcherProvider,
) {
    private val root = File(context.filesDir, "platform-icons")
    private val _icons = MutableStateFlow<Map<PlatformId, String>>(emptyMap())
    private val mutex = Mutex()
    private val missing = HashSet<String>()

    /** Platform id -> local PNG path for the set last passed to [sync]. */
    val icons: StateFlow<Map<PlatformId, String>> = _icons

    /**
     * Publishes the icons already on disk for [spec]'s set and, when [allowNetwork], downloads the
     * ones still missing. Tiles update as each icon lands. Icons the server does not have are
     * remembered per process; a network error stops the pass and leaves the rest for the next sync.
     */
    suspend fun sync(spec: ThemePlatformIcons, allowNetwork: Boolean) = mutex.withLock {
        withContext(dispatchers.io) {
            if (spec.set.isBlank() || spec.set == NONE) {
                _icons.value = emptyMap()
                return@withContext
            }
            val dir = File(root, spec.set.replace(Regex("[^A-Za-z0-9._-]"), "_"))
            publish(dir)
            if (!allowNetwork) return@withContext

            var downloaded = 0
            for (platform in platforms.platforms) {
                val name = platform.iconName ?: platform.libretroName ?: continue
                val target = File(dir, "${platform.id.value}.png")
                val key = "${spec.set}/${platform.id.value}"
                if (target.exists() || key in missing) continue
                // Not every set has every system: try the set's own name (and known aliases), then
                // the monochrome glyph, so a tile never stays blank because one set lacks a file.
                var result = Result.NOT_FOUND
                for ((set, candidate) in iconCandidates(spec.set, name)) {
                    result = downloadWithRetry(urlFor(spec.urlTemplate, set, candidate), target)
                    if (result != Result.NOT_FOUND) break
                }
                when (result) {
                    Result.DOWNLOADED -> {
                        downloaded++
                        publish(dir)
                    }
                    Result.NOT_FOUND -> missing += key
                    Result.NETWORK_ERROR -> {
                        // Offline or flaky link: leave the rest for the next sync instead of marking them missing.
                        Timber.i("Platform icons (%s): network unavailable, %d downloaded so far", spec.set, downloaded)
                        return@withContext
                    }
                }
            }
            if (downloaded > 0) Timber.i("Platform icons (%s): %d downloaded", spec.set, downloaded)
        }
    }

    private fun publish(dir: File) {
        // Only systems that still name a console icon: one that lost it (Steam used to borrow
        // DOS's) must not keep showing the file an older version downloaded.
        val withIcon = platforms.platforms.filter { (it.iconName ?: it.libretroName) != null }.mapTo(HashSet()) { it.id.value }
        val files = dir.listFiles { f -> f.isFile && f.extension == "png" && f.nameWithoutExtension in withIcon }.orEmpty()
        _icons.value = files.associate { PlatformId(it.nameWithoutExtension) to it.absolutePath }
    }

    private enum class Result { DOWNLOADED, NOT_FOUND, NETWORK_ERROR }

    /** DNS hiccups and dropped connections are common on handhelds: try a few times before giving up the pass. */
    private suspend fun downloadWithRetry(url: String, target: File): Result {
        var result = download(url, target)
        var attempt = 1
        while (result == Result.NETWORK_ERROR && attempt < MAX_ATTEMPTS) {
            delay(RETRY_DELAY_MS * attempt)
            result = download(url, target)
            attempt++
        }
        return result
    }

    private suspend fun download(url: String, target: File): Result {
        target.parentFile?.mkdirs()
        val tmp = File(target.path + ".part")
        return try {
            client.newCall(Request.Builder().url(url).build()).executeCancellable { response ->
                if (!response.isSuccessful) {
                    Timber.d("Platform icon %s -> HTTP %d", url, response.code)
                    return if (response.code in 500..599) Result.NETWORK_ERROR else Result.NOT_FOUND
                }
                tmp.sink().buffer().use { sink -> sink.writeAll(response.body.source()) }
            }
            when {
                tmp.length() == 0L -> { tmp.delete(); Result.NOT_FOUND }
                else -> {
                    if (target.exists()) target.delete()
                    if (tmp.renameTo(target)) Result.DOWNLOADED else { tmp.delete(); Result.NETWORK_ERROR }
                }
            }
        } catch (e: java.io.IOException) {
            tmp.delete()
            Timber.w("Platform icon download failed %s: %s", url, e.message)
            Result.NETWORK_ERROR
        }
    }

    companion object {
        const val NONE = "none"
        private const val MAX_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 1500L

        /** File names a set uses for systems whose RetroArch name differs from the libretro one. */
        private val ALIASES: Map<String, Map<String, String>> = mapOf(
            "systematic" to mapOf("Sony - PlayStation" to "Sony - PlayStation SCPH-100"),
        )

        /** (set, file name) pairs to try in order for one system. */
        fun iconCandidates(set: String, name: String): List<Pair<String, String>> = buildList {
            add(set to name)
            ALIASES[set]?.get(name)?.let { add(set to it) }
            if (set != "monochrome") add("monochrome" to name)
        }

        /** Fills `{set}` and `{name}` in [template], URL-encoding both. */
        fun urlFor(template: String, set: String, name: String): String =
            template.replace("{set}", encode(set)).replace("{name}", encode(name))

        private fun encode(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    }
}
