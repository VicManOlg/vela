package io.vela.core.data.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import io.vela.core.common.DispatcherProvider
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Storage permissions and the ROM folders Setup and Settings offer, so ViewModels need no Context. */
@Singleton
class StorageAccess @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) {
    fun hasAllFilesAccess(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

    fun allFilesAccessIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))

    /** Common ROM locations that exist and are readable right now, on internal storage and SD cards. */
    suspend fun suggestedFolders(): List<File> = withContext(dispatchers.io) {
        val internal = File(Environment.getExternalStorageDirectory().path)
        val cards = File("/storage").listFiles()?.filter { it.isDirectory && it.name != "emulated" && it.name != "self" }.orEmpty()
        (INTERNAL_NAMES.map { File(internal, it) } + cards.flatMap { card -> CARD_NAMES.map { File(card, it) } + card })
            .filter { it.isDirectory && it.canRead() }
            .distinctBy { it.absolutePath.lowercase() }
    }

    /**
     * Keeps read access to a folder picked with the system picker across reboots.
     * @return a short display name for it.
     */
    fun persistTree(uri: Uri): String {
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        return runCatching { DocumentsContract.getTreeDocumentId(uri).substringAfter(':').ifEmpty { "Storage" } }.getOrDefault("Folder")
    }

    /**
     * Optional art the user supplies per system: `Android/data/<app>/files/system-art/<platform id>.png`
     * (jpg/webp too; `all`, `favorites` and `android` name the smart shelves).
     * @return lower-case name -> absolute path.
     */
    suspend fun systemArt(): Map<String, String> = withContext(dispatchers.io) {
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "system-art")
        dir.listFiles { f -> f.isFile && f.extension.lowercase() in ART_EXTENSIONS }.orEmpty()
            .associate { it.nameWithoutExtension.lowercase() to it.absolutePath }
    }

    private companion object {
        val INTERNAL_NAMES = listOf("ROMs", "Roms", "roms", "Games", "Emulation/roms", "Emulation", "RetroArch/roms", "Download")
        val CARD_NAMES = listOf("ROMs", "Roms", "roms", "Games")
        val ART_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp")
    }
}
