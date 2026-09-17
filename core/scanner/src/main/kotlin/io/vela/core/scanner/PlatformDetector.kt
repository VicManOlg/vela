package io.vela.core.scanner

import io.vela.core.catalog.PlatformCatalog
import io.vela.core.model.Platform
import io.vela.core.model.PlatformId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides which platform a scanned file belongs to.
 *
 * Priority: source pinned to a platform > closest ancestor folder whose name matches a platform
 * alias > unambiguous extension. Files that match nothing, or match a platform that does not
 * accept their extension, are skipped.
 */
@Singleton
class PlatformDetector @Inject constructor(private val catalog: PlatformCatalog) {

    fun detect(file: ScannedFile, pinnedPlatform: PlatformId?): Platform? {
        val ext = file.extension
        if (ext.isEmpty() || ext in IGNORED_EXTENSIONS) return null

        pinnedPlatform?.let { id ->
            val platform = catalog[id] ?: return null
            return platform.takeIf { it.accepts(ext) }
        }

        // Closest folder wins: ROMs/Nintendo/SNES/game.sfc -> SNES, not "nintendo".
        for (folder in file.folderChain.asReversed()) {
            val platform = catalog.byFolderName(folder) ?: continue
            return if (platform.accepts(ext)) platform else null
        }

        val byExtension = catalog.byExtension(ext)
        return byExtension.singleOrNull()
    }

    companion object {
        /** Sidecar and media files that are never games. */
        val IGNORED_EXTENSIONS: Set<String> = setOf(
            "txt", "nfo", "dat", "xml", "json", "md", "cfg", "ini", "log", "srm", "sav", "sta", "state",
            "png", "jpg", "jpeg", "gif", "webp", "mp4", "webm", "mkv", "mp3", "ogg", "pdf",
            "auto", "ips", "bps", "ups", "xdelta", "db", "lpl", "rtc", "eep", "fla", "mpk", "sra",
            "ldb", "nv", "bak", "tmp", "part", "crdownload", "html", "htm", "css", "js", "apk",
        )

        /** Folder names skipped during recursion (case-insensitive). */
        val IGNORED_FOLDERS: Set<String> = setOf(
            "bios", "media", "images", "videos", "manuals", "downloaded_images", "downloaded_videos",
            "saves", "states", "screenshots", "cheats", "system", "shaders", "overlays", "thumbnails",
            "gamelists", "es-de", "emulationstation", "android", "lost.dir", "recycle.bin", "\$recycle.bin",
            "trash", ".trash", "node_modules",
        )
    }
}
