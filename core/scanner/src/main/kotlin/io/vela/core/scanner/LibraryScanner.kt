package io.vela.core.scanner

import io.vela.core.common.DispatcherProvider
import io.vela.core.common.TitleCleaner
import io.vela.core.database.VelaDatabase
import io.vela.core.database.dao.FileSignature
import io.vela.core.database.entity.GameEntity
import io.vela.core.database.entity.LibrarySourceEntity
import io.vela.core.database.entity.LocationType
import io.vela.core.model.GameKind
import io.vela.core.model.Platform
import io.vela.core.model.PlatformId
import io.vela.core.model.ScanProgress
import io.vela.core.model.ScanResult
import io.vela.core.model.SourceAccess
import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Incremental library scanner. Walks every enabled source, diffs (location, size, mtime) against
 * the database and only writes what changed. Files that vanish are flagged `present = 0` rather
 * than deleted so favourites and play stats survive an unplugged SD card.
 */
@Singleton
class LibraryScanner @Inject constructor(
    private val db: VelaDatabase,
    private val sources: FileSystemSourceFactory,
    private val detector: PlatformDetector,
    private val dispatchers: DispatcherProvider,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)
    private val mutex = Mutex()
    private var job: Job? = null

    private val _progress = MutableStateFlow<ScanProgress>(ScanProgress.Idle)
    val progress: StateFlow<ScanProgress> = _progress

    val isRunning: Boolean get() = job?.isActive == true

    /** Starts a full scan in the background; a no-op if one is already running. */
    fun scanAllInBackground(purgeMissing: Boolean = false) {
        if (isRunning) return
        job = scope.launch { runCatching { scanAll(purgeMissing) }.onFailure { Timber.e(it, "Scan failed") } }
    }

    fun cancel() {
        job?.cancel()
        _progress.value = ScanProgress.Idle
    }

    suspend fun scanAll(purgeMissing: Boolean = false): ScanResult = mutex.withLock {
        withContext(dispatchers.io) {
            val start = System.currentTimeMillis()
            var total = ScanResult(0, 0, 0, 0, 0)
            val errors = mutableListOf<String>()
            val enabled = db.libraryDao().sources().filter { it.enabled }
            for (source in enabled) {
                ensureActive()
                try {
                    val r = scanSourceInternal(source)
                    total = total.copy(
                        added = total.added + r.added, updated = total.updated + r.updated,
                        removed = total.removed + r.removed, skipped = total.skipped + r.skipped,
                    )
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Timber.e(e, "Failed scanning %s", source.uri)
                    errors += "${source.displayName}: ${e.message}"
                }
            }
            if (purgeMissing) db.gameDao().purgeMissingUnplayed()
            val result = total.copy(durationMs = System.currentTimeMillis() - start, errors = errors)
            _progress.value = ScanProgress.Finished(result)
            Timber.i("Scan finished: %s", result)
            result
        }
    }

    suspend fun scanSource(sourceId: Long): ScanResult = mutex.withLock {
        withContext(dispatchers.io) {
            val source = db.libraryDao().source(sourceId) ?: return@withContext ScanResult(0, 0, 0, 0, 0)
            val start = System.currentTimeMillis()
            val r = scanSourceInternal(source)
            r.copy(durationMs = System.currentTimeMillis() - start).also { _progress.value = ScanProgress.Finished(it) }
        }
    }

    private suspend fun scanSourceInternal(source: LibrarySourceEntity): ScanResult {
        val access = SourceAccess.valueOf(source.access)
        val fs = sources.forAccess(access)
        val pinned = source.platformId?.let(::PlatformId)
        val generation = System.currentTimeMillis()
        val existing: MutableMap<String, FileSignature> =
            db.gameDao().fileSignaturesForSource(source.id).associateByTo(HashMap()) { it.locationValue }
        val locationType = if (access == SourceAccess.FILE) LocationType.FILE else LocationType.DOCUMENT

        var seenFiles = 0
        var added = 0
        var updated = 0
        var skipped = 0
        val seenIds = ArrayList<Long>(512)
        val pendingInserts = ArrayList<GameEntity>(256)
        var currentDir: List<String>? = null
        val dirBatch = ArrayList<Pair<ScannedFile, Platform>>()

        suspend fun flushDir() {
            if (dirBatch.isEmpty()) return
            val accepted = resolveMultiDisc(dirBatch, fs)
            for ((file, platform, disc, hidden) in accepted) {
                val sig = existing[file.location]
                if (sig == null) {
                    pendingInserts += newGame(file, platform, source, locationType, disc, hidden, generation)
                    added++
                } else if (sig.fileSize != file.size || sig.lastModified != file.lastModified) {
                    db.gameDao().touchScanned(sig.id, file.size, file.lastModified, generation, platform.id.value, file.name)
                    updated++
                } else {
                    seenIds += sig.id
                }
            }
            skipped += dirBatch.size - accepted.size
            dirBatch.clear()
            if (pendingInserts.size >= 200) flushInserts(pendingInserts)
            if (seenIds.size >= 500) { db.gameDao().markSeen(seenIds, generation); seenIds.clear() }
        }

        fs.walk(source.uri, source.recursive, PlatformDetector.IGNORED_FOLDERS).collect { file ->
            seenFiles++
            if (file.folderChain != currentDir) {
                flushDir()
                currentDir = file.folderChain
            }
            val platform = detector.detect(file, pinned)
            if (platform == null) skipped++ else dirBatch += file to platform
            if (seenFiles % 50 == 0) {
                _progress.value = ScanProgress.Running(source.displayName, seenFiles, added + updated + seenIds.size)
            }
        }
        flushDir()
        flushInserts(pendingInserts)
        if (seenIds.isNotEmpty()) db.gameDao().markSeen(seenIds, generation)

        val removed = db.gameDao().markMissingForSource(source.id, generation)
        val count = existing.size + added - removed
        db.libraryDao().recordScan(source.id, generation, count.coerceAtLeast(0))
        Timber.i("Scanned %s: %d files, +%d ~%d -%d skipped %d", source.displayName, seenFiles, added, updated, removed, skipped)
        return ScanResult(added, updated, removed, skipped, 0)
    }

    private suspend fun flushInserts(batch: MutableList<GameEntity>) {
        if (batch.isEmpty()) return
        db.withTransaction { db.gameDao().insertAllIgnore(batch.toList()) }
        batch.clear()
    }

    private data class Accepted(val file: ScannedFile, val platform: Platform, val disc: Int?, val hidden: Boolean)

    /**
     * Within one directory: files referenced by an .m3u playlist are dropped (the playlist is the
     * game) and, without playlists, discs after the first are hidden so a set shows once.
     */
    private fun resolveMultiDisc(batch: List<Pair<ScannedFile, Platform>>, fs: FileSystemSource): List<Accepted> {
        val playlists = batch.filter { it.first.extension == "m3u" }
        val referenced = HashSet<String>()
        for ((m3u, _) in playlists) {
            fs.readText(m3u.location)?.lineSequence()
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() && !it.startsWith('#') }
                ?.forEach { referenced += it.substringAfterLast('/').substringAfterLast('\\').lowercase() }
        }
        val firstDiscSeen = HashSet<String>()
        return batch.mapNotNull { (file, platform) ->
            if (file.extension != "m3u" && file.name.lowercase() in referenced) return@mapNotNull null
            val disc = TitleCleaner.discNumber(file.name)
            val key = "${platform.id}:${TitleCleaner.sortKey(TitleCleaner.clean(file.name))}"
            val hidden = disc != null && disc > 1 && !firstDiscSeen.add(key)
            if (disc == 1 || disc == null) firstDiscSeen += key
            Accepted(file, platform, disc, hidden)
        }
    }

    private fun newGame(
        file: ScannedFile,
        platform: Platform,
        source: LibrarySourceEntity,
        locationType: LocationType,
        disc: Int?,
        hidden: Boolean,
        generation: Long,
    ): GameEntity {
        val title = TitleCleaner.clean(file.name)
        val sortTitle = TitleCleaner.sortKey(title)
        return GameEntity(
            platformId = platform.id.value,
            kind = GameKind.ROM.name,
            title = title,
            sortTitle = sortTitle,
            locationType = locationType,
            locationValue = file.location,
            fileName = file.name,
            fileSize = file.size,
            lastModified = file.lastModified,
            extension = file.extension,
            hidden = hidden,
            addedAt = generation,
            sourceId = source.id,
            region = TitleCleaner.region(file.name),
            duplicateKey = "${platform.id}:$sortTitle",
            discNumber = disc,
            present = true,
            scanGeneration = generation,
        )
    }
}
