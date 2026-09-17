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

/** Abstraction over java.io.File and SAF so the scanner is storage-agnostic. */
interface FileSystemSource {
    val access: SourceAccess

    /** Emits every regular file under [rootUri]; directories in [ignoredFolders] are skipped. */
    fun walk(rootUri: String, recursive: Boolean, ignoredFolders: Set<String>): Flow<ScannedFile>

    /** Reads a small text file (m3u playlists) or null if unreadable. */
    fun readText(location: String, maxBytes: Int = 64 * 1024): String?

    fun exists(location: String): Boolean
}

/** Fast path: direct filesystem access ("All files access" or app-private folders). */
class FileTreeSource : FileSystemSource {
    override val access = SourceAccess.FILE

    override fun walk(rootUri: String, recursive: Boolean, ignoredFolders: Set<String>): Flow<ScannedFile> = flow {
        val root = rootUri.toFileOrNull() ?: return@flow
        val queue = ArrayDeque<Pair<File, List<String>>>()
        queue.add(root to emptyList())
        while (queue.isNotEmpty()) {
            val (dir, chain) = queue.removeFirst()
            val children = dir.listFiles() ?: continue
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

    override fun walk(rootUri: String, recursive: Boolean, ignoredFolders: Set<String>): Flow<ScannedFile> = flow {
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
            } ?: continue
            cursor.use { c ->
                while (c.moveToNext()) {
                    val childId = c.getString(0)
                    val name = c.getString(1) ?: continue
                    if (name.startsWith('.')) continue
                    val mime = c.getString(2)
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (recursive && name.lowercase() !in ignoredFolders) queue.add(childId to chain + name)
                    } else {
                        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                        emit(ScannedFile(name, uri.toString(), c.getLong(3), c.getLong(4), chain))
                    }
                }
            }
        }
    }

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
