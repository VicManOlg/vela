package io.vela.core.scraper.store

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.vela.core.catalog.PlatformCatalog
import io.vela.core.common.DispatcherProvider
import io.vela.core.model.PlatformId
import io.vela.core.model.ThemePlatformIcons
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
     * ones still missing. Tiles update as each icon lands. Failures are remembered per process so
     * a missing icon is requested once per run.
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
                if (download(urlFor(spec.urlTemplate, spec.set, name), target)) {
                    downloaded++
                    publish(dir)
                } else {
                    missing += key
                }
            }
            if (downloaded > 0) Timber.i("Platform icons (%s): %d downloaded", spec.set, downloaded)
        }
    }

    private fun publish(dir: File) {
        val files = dir.listFiles { f -> f.isFile && f.extension == "png" }.orEmpty()
        _icons.value = files.associate { PlatformId(it.nameWithoutExtension) to it.absolutePath }
    }

    private fun download(url: String, target: File): Boolean {
        target.parentFile?.mkdirs()
        val tmp = File(target.path + ".part")
        return try {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.d("Platform icon %s -> HTTP %d", url, response.code)
                    return false
                }
                tmp.sink().buffer().use { sink -> sink.writeAll(response.body.source()) }
            }
            if (tmp.length() == 0L) {
                tmp.delete()
                false
            } else {
                if (target.exists()) target.delete()
                tmp.renameTo(target)
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            tmp.delete()
            Timber.w(e, "Platform icon download failed %s", url)
            false
        }
    }

    companion object {
        const val NONE = "none"

        /** Fills `{set}` and `{name}` in [template], URL-encoding both. */
        fun urlFor(template: String, set: String, name: String): String =
            template.replace("{set}", encode(set)).replace("{name}", encode(name))

        private fun encode(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    }
}
