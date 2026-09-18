package io.vela.core.scraper.store

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.vela.core.common.DispatcherProvider
import io.vela.core.database.dao.MetadataDao
import io.vela.core.database.entity.ArtworkEntity
import io.vela.core.model.ArtworkCandidate
import io.vela.core.model.ArtworkType
import io.vela.core.model.GameId
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads artwork once into app storage (`files/artwork/<gameId>/<type>.<ext>`) and records
 * it in the database. Files are the offline cache: once present, nothing is fetched again.
 */
@Singleton
class ArtworkStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
    private val metadataDao: MetadataDao,
    private val dispatchers: DispatcherProvider,
) {
    private val root: File get() = File(context.filesDir, "artwork")

    fun fileFor(gameId: GameId, type: ArtworkType, extension: String): File =
        File(File(root, gameId.value.toString()), "${type.name.lowercase()}.$extension")

    /** Downloads [candidate] unless artwork of that type already exists (or [overwrite]). */
    suspend fun download(gameId: GameId, candidate: ArtworkCandidate, providerId: String, overwrite: Boolean): Boolean =
        withContext(dispatchers.io) {
            val existing = metadataDao.artwork(gameId.value).firstOrNull { it.type == candidate.type.name }
            if (existing != null && !overwrite && File(existing.localPath).exists()) return@withContext true

            val ext = candidate.format?.lowercase()?.takeIf { it.length in 2..4 }
                ?: candidate.url.substringAfterLast('.', "").substringBefore('?').lowercase().takeIf { it.length in 2..4 }
                ?: if (candidate.type.isVideo) "mp4" else "png"
            val target = fileFor(gameId, candidate.type, ext)
            target.parentFile?.mkdirs()
            val tmp = File(target.path + ".part")
            try {
                client.newCall(Request.Builder().url(candidate.url).build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        Timber.d("Artwork %s -> HTTP %d", candidate.url, response.code)
                        return@withContext false
                    }
                    val body = response.body
                    if (body.contentLength() in 0..MIN_BYTES) return@withContext false
                    tmp.sink().buffer().use { sink -> sink.writeAll(body.source()) }
                }
                if (tmp.length() <= MIN_BYTES) { tmp.delete(); return@withContext false }
                if (target.exists()) target.delete()
                if (!tmp.renameTo(target)) { tmp.delete(); return@withContext false }
                metadataDao.upsertArtwork(
                    ArtworkEntity(
                        gameId = gameId.value, type = candidate.type.name, localPath = target.absolutePath,
                        sourceUrl = candidate.url, width = candidate.width, height = candidate.height,
                        providerId = providerId, updatedAt = System.currentTimeMillis(),
                    ),
                )
                true
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                tmp.delete()
                Timber.w(e, "Artwork download failed %s", candidate.url)
                false
            }
        }

    /** Stores a user-picked local image (custom artwork) by copying it into our folder. */
    suspend fun importLocal(gameId: GameId, type: ArtworkType, source: java.io.InputStream, extension: String) =
        withContext(dispatchers.io) {
            val target = fileFor(gameId, type, extension)
            target.parentFile?.mkdirs()
            source.use { input -> target.outputStream().use { input.copyTo(it) } }
            metadataDao.upsertArtwork(
                ArtworkEntity(gameId = gameId.value, type = type.name, localPath = target.absolutePath, providerId = "user", updatedAt = System.currentTimeMillis()),
            )
        }

    suspend fun delete(gameId: GameId, type: ArtworkType) = withContext(dispatchers.io) {
        metadataDao.artwork(gameId.value).filter { it.type == type.name }.forEach { File(it.localPath).delete() }
        metadataDao.deleteArtwork(gameId.value, type.name)
    }

    suspend fun deleteAll(gameId: GameId) = withContext(dispatchers.io) {
        File(root, gameId.value.toString()).deleteRecursively()
        metadataDao.deleteAllArtwork(gameId.value)
    }

    suspend fun totalBytes(): Long = withContext(dispatchers.io) {
        root.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
    }

    private companion object {
        /** Anything smaller is a placeholder or an error page, not artwork. */
        const val MIN_BYTES = 512L
    }
}
