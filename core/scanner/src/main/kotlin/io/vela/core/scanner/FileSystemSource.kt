package io.vela.core.scanner

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import io.vela.core.model.SourceAccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import java.io.File
import java.io.InputStream
import java.util.ArrayDeque
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** One file found while walking a library source. */
data class ScannedFile(
    val name: String,
    /** Absolute path (FILE access) or document URI string (DOCUMENT access). */
    val location: String,
    val size: Long,
    val lastModified: Long,
    /** Folder names between the source root and the file, top-most first. */
    val folderChain: List<String>,
) {
    val extension: String get() = name.substringAfterLast('.', "").lowercase()
    val parentFolder: String? get() = folderChain.lastOrNull()
}

/**
 * Extension given to a PS3 game kept as a folder, so the folder goes through platform detection
 * and player choice like a file ("Demon's Souls [BLUS30443].ps3dir").
 */
const val PS3_FOLDER_EXTENSION = "ps3dir"

/**
 * A PS3 game stored as a folder: a disc dump (PS3_GAME/ with PARAM.SFO inside) or an installed
 * game (PARAM.SFO next to USRDIR/). [names] are the folder's direct children.
 */
internal fun isPs3FolderGame(names: Collection<String>): Boolean {
    val upper = names.mapTo(HashSet()) { it.uppercase() }
    return "PS3_GAME" in upper || ("PARAM.SFO" in upper && "USRDIR" in upper)
}

/** Extension of a Steam game: a file (or a GameNative install folder) named after its app id. */
const val STEAM_APP_EXTENSION = "steamappid"

/** Where GameNative's downloader records the depots of an installed game. */
internal const val GAMENATIVE_DEPOT_DIR = ".DepotDownloader"
internal const val GAMENATIVE_DEPOT_CONFIG = "depot.config"

/**
 * The Steam app id of a game GameNative installed, from its depot.config: Steam numbers a game's
 * own depots right after its app id (Darkwood 274520 -> depots 274521, 274524), and every game
 * also carries the Steamworks redistributables (app 228980, depots 228981..229999). So the app
 * id is the lowest depot outside that range, minus one. Null when the file says nothing usable.
 */
internal fun steamAppIdFromDepotConfig(text: String): Long? {
    val depots = Regex(""""(\d+)"\s*:""").findAll(text).mapNotNull { it.groupValues[1].toLongOrNull() }
        .filter { it !in 228_981L..229_999L }
        .toList()
    return depots.minOrNull()?.minus(1)?.takeIf { it > 0 }
}

/** Abstraction over java.io.File and SAF so the scanner is storage-agnostic. */
interface FileSystemSource {
    val access: SourceAccess

    /**
     * Emits every regular file under [rootUri]; directories in [ignoredFolders] are skipped. A PS3
     * game kept as a folder is emitted once, as the folder, and not walked into. A directory that
     * cannot be listed is reported to [onUnreadable]: its games are out of reach, not gone.
     */
    fun walk(rootUri: String, recursive: Boolean, ignoredFolders: Set<String>, onUnreadable: (String) -> Unit = {}): Flow<ScannedFile>

    /** Reads a small text file (m3u playlists) or null if unreadable. */
    fun readText(location: String, maxBytes: Int = 64 * 1024): String?

    fun exists(location: String): Boolean
}

/** Fast path: direct filesystem access ("All files access" or app-private folders). */
class FileTreeSource : FileSystemSource {
    override val access = SourceAccess.FILE

    override fun walk(rootUri: String, recursive: Boolean, ignoredFolders: Set<String>, onUnreadable: (String) -> Unit): Flow<ScannedFile> = flow {
        val root = rootUri.toFileOrNull() ?: return@flow
        val queue = ArrayDeque<Pair<File, List<String>>>()
        queue.add(root to emptyList())
        while (queue.isNotEmpty()) {
            val (dir, chain) = queue.removeFirst()
            val children = dir.listFiles() ?: run { onUnreadable(dir.path); null } ?: continue
            if (chain.isNotEmpty() && isPs3FolderGame(children.map { it.name })) {
                emit(ScannedFile("${dir.name}.$PS3_FOLDER_EXTENSION", dir.absolutePath, 0L, dir.lastModified(), chain.dropLast(1)))
                continue
            }
            // A game GameNative installed: one Steam game, never its .exe files (they are not DOS games).
            if (children.any { it.name == GAMENATIVE_DEPOT_DIR }) {
                val appId = runCatching { File(dir, "$GAMENATIVE_DEPOT_DIR/$GAMENATIVE_DEPOT_CONFIG").readText() }.getOrNull()?.let(::steamAppIdFromDepotConfig)
                if (appId != null) emit(ScannedFile("$appId.$STEAM_APP_EXTENSION", dir.absolutePath, 0L, dir.lastModified(), chain.dropLast(1)))
                continue
            }
            for (child in children) {
                val name = child.name
                if (name.startsWith('.')) continue
                if (child.isDirectory) {
                    if (recursive && name.lowercase() !in ignoredFolders) queue.add(child to chain + name)
                } else if (child.isFile) {
                    emit(ScannedFile(name, child.absolutePath, child.length(), child.lastModified(), chain))
                }
            }
        }
    }

    override fun readText(location: String, maxBytes: Int): String? = runCatching {
        val file = File(location)
        if (file.length() > maxBytes) return null
        file.readText()
    }.getOrNull()

    override fun exists(location: String): Boolean = File(location).exists()

    companion object {
        fun String.toFileOrNull(): File? = when {
            startsWith("file://") -> File(Uri.parse(this).path ?: return null)
            startsWith("content://") -> null
            else -> File(this)
        }
    }
}

/**
 * SAF path. Uses DocumentsContract child queries with a narrow projection - one query per
 * directory - which is an order of magnitude faster than DocumentFile.listFiles().
 */
class DocumentTreeSource(private val context: Context) : FileSystemSource {
    override val access = SourceAccess.DOCUMENT_TREE

    private val projection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
    )

    override fun walk(rootUri: String, recursive: Boolean, ignoredFolders: Set<String>, onUnreadable: (String) -> Unit): Flow<ScannedFile> = flow {
        val treeUri = Uri.parse(rootUri)
        val rootDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return@flow
        val queue = ArrayDeque<Pair<String, List<String>>>()
        queue.add(rootDocId to emptyList())
        val resolver = context.contentResolver
        while (queue.isNotEmpty()) {
            val (docId, chain) = queue.removeFirst()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            val cursor = try {
                resolver.query(childrenUri, projection, null, null, null)
            } catch (e: Exception) {
                Timber.w(e, "SAF query failed for %s", childrenUri)
                null
            } ?: run { onUnreadable(childrenUri.toString()); null } ?: continue
            var depotDirId: String? = null
            val rows = cursor.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        val name = c.getString(1) ?: continue
                        if (name == GAMENATIVE_DEPOT_DIR) depotDirId = c.getString(0)
                        if (name.startsWith('.')) continue
                        add(SafRow(c.getString(0), name, c.getString(2), c.getLong(3), c.getLong(4)))
                    }
                }
            }
            val depots = depotDirId
            if (depots != null) {
                val appId = depotConfigText(treeUri, depots)?.let(::steamAppIdFromDepotConfig)
                val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                if (appId != null) emit(ScannedFile("$appId.$STEAM_APP_EXTENSION", uri.toString(), 0L, 0L, chain.dropLast(1)))
                continue
            }
            if (chain.isNotEmpty() && isPs3FolderGame(rows.map { it.name })) {
                val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                emit(ScannedFile("${chain.last()}.$PS3_FOLDER_EXTENSION", uri.toString(), 0L, 0L, chain.dropLast(1)))
                continue
            }
            for (row in rows) {
                if (row.mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    if (recursive && row.name.lowercase() !in ignoredFolders) queue.add(row.id to chain + row.name)
                } else {
                    val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, row.id)
                    emit(ScannedFile(row.name, uri.toString(), row.size, row.modified, chain))
                }
            }
        }
    }

    private class SafRow(val id: String, val name: String, val mime: String?, val size: Long, val modified: Long)

    /** depot.config inside a GameNative .DepotDownloader folder, or null. */
    private fun depotConfigText(treeUri: Uri, depotDirId: String): String? = runCatching {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, depotDirId)
        val configId = context.contentResolver.query(children, projection, null, null, null)?.use { c ->
            var found: String? = null
            while (c.moveToNext()) if (c.getString(1) == GAMENATIVE_DEPOT_CONFIG) found = c.getString(0)
            found
        } ?: return@runCatching null
        readText(DocumentsContract.buildDocumentUriUsingTree(treeUri, configId).toString())
    }.getOrNull()

    override fun readText(location: String, maxBytes: Int): String? = runCatching {
        context.contentResolver.openInputStream(Uri.parse(location))?.use { it.readBounded(maxBytes) }
    }.getOrNull()

    override fun exists(location: String): Boolean = runCatching {
        context.contentResolver.query(Uri.parse(location), arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)
            ?.use { it.count > 0 } ?: false
    }.getOrDefault(false)

    private fun InputStream.readBounded(max: Int): String? {
        val bytes = readNBytes(max + 1)
        return if (bytes.size > max) null else bytes.decodeToString()
    }
}

/** Picks the right source implementation for a library URI. */
@Singleton
class FileSystemSourceFactory @Inject constructor(@ApplicationContext private val context: Context) {
    private val fileSource = FileTreeSource()
    private val documentSource by lazy { DocumentTreeSource(context) }

    fun forAccess(access: SourceAccess): FileSystemSource = when (access) {
        SourceAccess.FILE -> fileSource
        SourceAccess.DOCUMENT_TREE -> documentSource
    }

    fun forLocation(location: String): FileSystemSource =
        if (location.startsWith("content://")) documentSource else fileSource
}
