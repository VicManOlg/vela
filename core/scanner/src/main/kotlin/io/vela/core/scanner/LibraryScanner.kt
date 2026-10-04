package io.vela.core.scanner

import io.vela.core.common.ApplicationScope
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
import kotlinx.coroutines.Job
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
    private val scope: ApplicationScope,
) {
    private val mutex = Mutex()
    private val startLock = Any()
    private var job: Job? = null

    private val _progress = MutableStateFlow<ScanProgress>(ScanProgress.Idle)
    val progress: StateFlow<ScanProgress> = _progress

    val isRunning: Boolean get() = job?.isActive == true

    /** Starts a full scan in the background; a no-op if one is already running. */
    fun scanAllInBackground(purgeMissing: Boolean = false) = synchronized(startLock) {
        if (isRunning) return@synchronized
        job = scope.launch {
            try {
                scanAll(purgeMissing)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.e(e, "Scan failed")
            }
        }
    }

    fun cancel() = synchronized(startLock) {
        job?.cancel()
        job = null
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
                    errors += r.errors
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Timber.e(e, "Failed scanning %s", source.uri)
                    errors += "${source.displayName}: ${e.message}"
                }
            }
            reattachMoved(start)
            // Never purge after a pass in which any source could not be read: that is exactly when
            // "missing" games are only temporarily out of reach.
            if (purgeMissing && errors.isEmpty()) {
                db.gameDao().purgeMissingUnplayed()
                pruneOrphans()
            }
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
            reattachMoved(start)
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
        // Changed files and platform fixes are written in batches: one transaction instead of one
        // commit (and one fsync) per file.
        val pendingTouches = ArrayList<Touch>(128)
        val pendingPlatformFixes = ArrayList<Pair<Long, String>>(64)

        suspend fun flushUpdates() {
            if (pendingTouches.isEmpty() && pendingPlatformFixes.isEmpty()) return
            db.withTransaction {
                for (t in pendingTouches) db.gameDao().touchScanned(t.id, t.fileSize, t.lastModified, generation, t.platformId, t.fileName)
                for ((id, platformId) in pendingPlatformFixes) db.gameDao().setPlatform(id, platformId)
            }
            pendingTouches.clear()
            pendingPlatformFixes.clear()
        }
        var currentDir: List<String>? = null
        val dirBatch = ArrayList<Pair<ScannedFile, Platform>>()

        suspend fun flushDir() {
            if (dirBatch.isEmpty()) return
            val accepted = resolveMultiDisc(dirBatch, fs)
            for ((file, platform, disc, hidden) in accepted) {
                val sig = existing[file.location]
                if (sig == null) {
                    pendingInserts += newGame(file, platform, source, locationType, disc, hidden, generation)
                } else if (sig.fileSize != file.size || sig.lastModified != file.lastModified) {
                    pendingTouches += Touch(sig.id, file.size, file.lastModified, platform.id.value, file.name)
                    updated++
                } else {
                    if (sig.platformId != platform.id.value) {
                        pendingPlatformFixes += sig.id to platform.id.value
                        updated++
                    }
                    seenIds += sig.id
                }
            }
            skipped += dirBatch.size - accepted.size
            dirBatch.clear()
            if (pendingInserts.size >= 200) added += flushInserts(pendingInserts)
            if (pendingTouches.size + pendingPlatformFixes.size >= 200) flushUpdates()
            if (seenIds.size >= 500) { db.gameDao().markSeen(seenIds, generation); seenIds.clear() }
        }

        val unreadable = ArrayList<String>()
        fs.walk(source.uri, source.recursive, PlatformDetector.IGNORED_FOLDERS, onUnreadable = { unreadable += it }).collect { file ->
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
        added += flushInserts(pendingInserts)
        flushUpdates()
        if (seenIds.isNotEmpty()) db.gameDao().markSeen(seenIds, generation)

        // A folder that used to hold games and now yields nothing is almost always an unmounted
        // card, a revoked permission or a renamed root, not an empty library. Keep everything.
        if (seenFiles == 0 && existing.isNotEmpty()) {
            Timber.w("Source %s returned no files; keeping its %d games untouched", source.displayName, existing.size)
            return ScanResult(0, 0, 0, 0, 0, errors = listOf("${source.displayName}: folder unreadable, library kept as it was"))
        }

        // A subfolder that could not be listed (a card still mounting, a permission hiccup) would
        // make every game in it look deleted, and a purge would take their artwork with them.
        if (unreadable.isNotEmpty()) {
            Timber.w("Source %s: %d folders unreadable; nothing marked missing", source.displayName, unreadable.size)
            db.libraryDao().recordScan(source.id, generation, (existing.size + added).coerceAtLeast(0))
            return ScanResult(added, updated, 0, skipped, 0, errors = listOf("${source.displayName}: some folders could not be read, missing games kept"))
        }

        val removed = db.gameDao().markMissingForSource(source.id, generation)
        val count = existing.size + added - removed
        db.libraryDao().recordScan(source.id, generation, count.coerceAtLeast(0))
        Timber.i("Scanned %s: %d files, +%d ~%d -%d skipped %d", source.displayName, seenFiles, added, updated, removed, skipped)
        return ScanResult(added, updated, removed, skipped, 0)
    }

    /** @return how many rows were really inserted (duplicates across overlapping folders are ignored). */
    private suspend fun flushInserts(batch: MutableList<GameEntity>): Int {
        if (batch.isEmpty()) return 0
        val ids = db.withTransaction { db.gameDao().insertAllIgnore(batch.toList()) }
        batch.clear()
        return ids.count { it != -1L }
    }

    /**
     * Forgets a folder and its games. Under the scan lock, so a scan in progress cannot insert
     * games for a source that is being removed (they would never be cleaned up).
     */
    suspend fun removeSource(sourceId: Long) = mutex.withLock {
        withContext(dispatchers.io) {
            db.withTransaction {
                db.gameDao().deleteBySource(sourceId)
                db.libraryDao().deleteSource(sourceId)
            }
            pruneOrphans()
        }
    }

    /**
     * A game whose folder was moved or renamed shows up as a new file while the old one goes
     * missing. Keep the old row (artwork, play time, rating, collections) at the new location and
     * drop the fresh duplicate, which has nothing of its own yet.
     */
    private suspend fun reattachMoved(since: Long) {
        val moves = db.gameDao().movedGames(since)
        if (moves.isEmpty()) return
        val usedOld = HashSet<Long>()
        val usedNew = HashSet<Long>()
        db.withTransaction {
            for (move in moves) {
                if (move.oldId in usedOld || move.newId in usedNew) continue
                val fresh = db.gameDao().byId(move.newId) ?: continue
                db.gameDao().deleteRow(fresh.id)
                db.gameDao().relocate(move.oldId, fresh.locationType.name, fresh.locationValue, fresh.sourceId, fresh.lastModified, fresh.scanGeneration)
                usedOld += move.oldId
                usedNew += move.newId
            }
        }
        Timber.i("Reattached %d moved games", usedOld.size)
    }

    /**
     * Drops metadata, artwork rows, collection links and sessions whose game no longer exists,
     * then their files: rows first, so a failure never leaves artwork pointing at deleted files.
     */
    private suspend fun pruneOrphans() {
        val files = db.withTransaction { db.metadataDao().orphanArtworkPaths().also { db.gameDao().pruneOrphans() } }
        files.forEach { java.io.File(it).delete() }
    }

    private data class Accepted(val file: ScannedFile, val platform: Platform, val disc: Int?, val hidden: Boolean)

    /** A known file whose size or date changed; written to the database in batches. */
    private data class Touch(val id: Long, val fileSize: Long, val lastModified: Long, val platformId: String, val fileName: String)

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
        // A PS3 game folder is often named by its serial (NPUB30123); its PARAM.SFO knows the name.
        val sfoTitle = if (file.extension == PS3_FOLDER_EXTENSION && locationType == LocationType.FILE) ParamSfo.titleOfGameFolder(java.io.File(file.location)) else null
        val title = sfoTitle ?: TitleCleaner.clean(file.name)
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
