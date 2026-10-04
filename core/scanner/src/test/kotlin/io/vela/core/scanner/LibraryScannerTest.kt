package io.vela.core.scanner

import io.vela.core.database.entity.ArtworkEntity
import android.content.Context
import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import io.vela.core.catalog.PlatformCatalog
import io.vela.core.common.ApplicationScope
import io.vela.core.common.DispatcherProvider
import io.vela.core.database.VelaDatabase
import io.vela.core.database.entity.LibrarySourceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class LibraryScannerTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: VelaDatabase
    private lateinit var scanner: LibraryScanner
    private lateinit var root: File

    private val dispatchers = object : DispatcherProvider {
        override val io = Dispatchers.Unconfined
        override val default = Dispatchers.Unconfined
        override val main = Dispatchers.Unconfined
    }

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, VelaDatabase::class.java).allowMainThreadQueries().build()
        val catalog = PlatformCatalog()
        scanner = LibraryScanner(db, FileSystemSourceFactory(context), PlatformDetector(catalog), dispatchers, ApplicationScope(dispatchers))
        root = tmp.newFolder("ROMs")
    }

    @After
    fun tearDown() = db.close()

    private fun file(path: String, content: String = "x"): File =
        File(root, path).apply { parentFile!!.mkdirs(); writeText(content) }

    private suspend fun addSource(platform: String? = null): Long =
        db.libraryDao().insertSource(LibrarySourceEntity(uri = root.absolutePath, displayName = "ROMs", access = "FILE", platformId = platform))

    @Test
    fun `detects platform from folder and skips junk`() = runTest {
        file("snes/Chrono Trigger (USA).sfc")
        file("snes/Chrono Trigger (USA).srm")
        file("Nintendo/Game Boy Advance/Metroid Fusion (Europe).gba")
        file("random/notes.txt")
        file("bios/scph1001.bin")
        addSource()

        val result = scanner.scanAll()

        assertThat(result.added).isEqualTo(2)
        val games = db.gameDao().observeRecentlyAdded(10).first()
        assertThat(games.map { it.title to it.platformId }).containsExactly("Chrono Trigger" to "snes", "Metroid Fusion" to "gba")
    }

    @Test
    fun `PS3 games kept as folders are one game each and are not walked into`() = runTest {
        // A disc dump and a game installed from a PKG; neither has an ISO.
        file("ps3/Demon's Souls [BLUS30443]/PS3_GAME/PARAM.SFO")
        file("ps3/Demon's Souls [BLUS30443]/PS3_GAME/USRDIR/EBOOT.BIN")
        file("ps3/Demon's Souls [BLUS30443]/PS3_DISC.SFB")
        file("ps3/NPUB30123/PARAM.SFO")
        file("ps3/NPUB30123/USRDIR/EBOOT.BIN")
        file("ps3/Folder of extras/readme.txt")
        addSource()

        scanner.scanAll()

        val games = db.gameDao().observeRecentlyAdded(10).first()
        assertThat(games.map { it.platformId }.distinct()).containsExactly("ps3")
        assertThat(games.map { it.title }).containsExactly("Demon's Souls", "NPUB30123")
        val stored = games.map { db.gameDao().byId(it.id)!! }
        assertThat(stored.map { it.extension }.distinct()).containsExactly(PS3_FOLDER_EXTENSION)
        // The game is the folder itself: that is the path aPS3e receives as game_dir.
        assertThat(stored.map { File(it.locationValue).name }).containsExactly("Demon's Souls [BLUS30443]", "NPUB30123")
    }

    @Test
    fun `a game GameNative installed is one Steam game named after its folder, not a DOS game`() = runTest {
        // The folder added is GameNative's common/ itself: no "Steam" folder name to go by.
        file("common/Darkwood/Darkwood.exe")
        file("common/Darkwood/UnityCrashHandler64.exe")
        file(
            "common/Darkwood/.DepotDownloader/depot.config",
            """{ "installedManifestIDs": { "228983": 1, "229002": 2, "274521": 3, "274524": 4 } }""",
        )
        addSource()

        scanner.scanAll()

        val games = db.gameDao().observeRecentlyAdded(10).first()
        assertThat(games.map { it.title to it.platformId }).containsExactly("Darkwood" to "steam")
        assertThat(db.gameDao().byId(games.single().id)!!.fileName).isEqualTo("274520.steamappid")
    }

    @Test
    fun `a PS3 folder named by its serial takes the name from PARAM SFO`() = runTest {
        File(root, "ps3/NPUB30123").mkdirs()
        File(root, "ps3/NPUB30123/USRDIR").mkdirs()
        File(root, "ps3/NPUB30123/PARAM.SFO").writeBytes(sfo("TITLE" to "Flower™", "TITLE_ID" to "NPUB30123"))
        addSource()

        scanner.scanAll()

        assertThat(db.gameDao().observeRecentlyAdded(10).first().map { it.title }).containsExactly("Flower")
    }

    /** A minimal PARAM.SFO: magic, header, one UTF-8 entry per pair. */
    private fun sfo(vararg pairs: Pair<String, String>): ByteArray {
        val keys = java.io.ByteArrayOutputStream()
        val data = java.io.ByteArrayOutputStream()
        val entries = java.nio.ByteBuffer.allocate(pairs.size * 16).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        for ((k, v) in pairs) {
            val value = v.toByteArray(Charsets.UTF_8) + 0
            entries.putShort(keys.size().toShort()).putShort(0x0204).putInt(value.size).putInt(value.size).putInt(data.size())
            keys.write(k.toByteArray(Charsets.US_ASCII) + 0)
            data.write(value)
        }
        val keyTable = 20 + pairs.size * 16
        val dataTable = keyTable + keys.size()
        val header = java.nio.ByteBuffer.allocate(20).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .put(byteArrayOf(0, 'P'.code.toByte(), 'S'.code.toByte(), 'F'.code.toByte())).putInt(0x101).putInt(keyTable).putInt(dataTable).putInt(pairs.size)
        return header.array() + entries.array() + keys.toByteArray() + data.toByteArray()
    }

    @Test
    fun `Vita pkg files are Vita games named without their title id, zRIF notes are not games`() = runTest {
        file("psvita/Gravity Rush [PCSF00024].pkg")
        file("psvita/Gravity Rush [PCSF00024].zrif.txt")
        file("psvita/LEEME.txt")
        // A PS3 pkg in the PS3 folder stays PS3: .pkg is no longer Vita's alone.
        file("ps3/How to survive [NPEB01387](axekin.com).pkg")
        addSource()

        scanner.scanAll()

        assertThat(db.gameDao().observeRecentlyAdded(10).first().map { it.title to it.platformId })
            .containsExactly("Gravity Rush" to "psvita", "How to survive" to "ps3")
    }

    @Test
    fun `a game whose folder was renamed keeps its row, artwork and play time`() = runTest {
        file("snes/Chrono Trigger (USA).sfc")
        addSource()
        scanner.scanAll()
        val id = db.gameDao().observeRecentlyAdded(10).first().single().id
        db.metadataDao().upsertArtwork(ArtworkEntity(gameId = id, type = "BOX_FRONT", localPath = "/art/ct.png", updatedAt = 1))
        db.gameDao().touchScanned(id, 1, 1, 1, "snes", "Chrono Trigger (USA).sfc") // what any later scan writes

        File(root, "snes").renameTo(File(root, "Super Nintendo"))
        scanner.scanAll(purgeMissing = true)

        val games = db.gameDao().observeRecentlyAdded(10).first()
        assertThat(games.map { it.id }).containsExactly(id)
        assertThat(games.single().boxArt).isEqualTo("/art/ct.png")
        assertThat(File(db.gameDao().byId(id)!!.locationValue).parentFile!!.name).isEqualTo("Super Nintendo")
    }

    @Test
    fun `a folder that cannot be read marks nothing missing and purges nothing`() = runTest {
        file("snes/Chrono Trigger (USA).sfc")
        file("gba/Metroid Fusion (Europe).gba")
        addSource()
        scanner.scanAll()
        val gba = File(root, "gba")
        gba.setReadable(false)
        // Some file systems (Windows) ignore the flag; the case only means something where it holds.
        org.junit.Assume.assumeTrue(gba.listFiles() == null)

        val result = scanner.scanAll(purgeMissing = true)

        assertThat(result.errors).isNotEmpty()
        assertThat(db.gameDao().observeRecentlyAdded(10).first().map { it.title }).containsExactly("Chrono Trigger", "Metroid Fusion")
        gba.setReadable(true)
    }

    @Test
    fun `second scan is incremental and detects removals`() = runTest {
        val a = file("gba/A.gba")
        file("gba/B.gba")
        addSource()
        assertThat(scanner.scanAll().added).isEqualTo(2)

        a.delete()
        file("gba/C.gba")
        val second = scanner.scanAll()

        assertThat(second.added).isEqualTo(1)
        assertThat(second.removed).isEqualTo(1)
        assertThat(second.updated).isEqualTo(0)
        assertThat(db.gameDao().observeTotalCount().first()).isEqualTo(2)
    }

    @Test
    fun `m3u playlist hides its discs and pinned source uses its platform`() = runTest {
        file("Final Fantasy VII (Disc 1).chd")
        file("Final Fantasy VII (Disc 2).chd")
        file("Final Fantasy VII.m3u", "Final Fantasy VII (Disc 1).chd\nFinal Fantasy VII (Disc 2).chd\n")
        file("Tekken 3.chd")
        addSource(platform = "psx")

        scanner.scanAll()

        val titles = db.gameDao().observeRecentlyAdded(10).first().map { it.title }
        assertThat(titles).containsExactly("Final Fantasy VII", "Tekken 3")
    }

    @Test
    fun `pinning a source to a system moves already scanned games without recreating them`() = runTest {
        file("roms/Game A.bin")
        file("roms/Game B.bin")
        val sourceId = addSource(platform = "megadrive")
        scanner.scanAll()
        val before = db.gameDao().observeRecentlyAdded(10).first()
        assertThat(before.map { it.platformId }.distinct()).containsExactly("megadrive")
        db.gameDao().setFavorite(before.first().id, true)

        val source = db.libraryDao().source(sourceId)!!
        db.libraryDao().updateSource(source.copy(platformId = "psx"))
        val result = scanner.scanSource(sourceId)

        assertThat(result.added).isEqualTo(0)
        assertThat(result.updated).isEqualTo(2)
        val after = db.gameDao().observeRecentlyAdded(10).first()
        assertThat(after.map { it.id }).containsExactlyElementsIn(before.map { it.id })
        assertThat(after.map { it.platformId }.distinct()).containsExactly("psx")
        assertThat(after.first { it.id == before.first().id }.favorite).isTrue()
    }

    @Test
    fun `multi disc without playlist keeps only first disc visible`() = runTest {
        file("psx/Metal Gear Solid (Disc 1).chd")
        file("psx/Metal Gear Solid (Disc 2).chd")
        addSource()

        scanner.scanAll()

        val visible = db.gameDao().observeRecentlyAdded(10).first()
        assertThat(visible.map { it.title }).containsExactly("Metal Gear Solid")
        assertThat(visible.single().discNumber).isEqualTo(1)
    }

    @Test
    fun `removing a source drops its games, their artwork rows and files`() = runTest {
        file("gba/A.gba")
        val source = addSource()
        scanner.scanAll()
        val gameId = db.gameDao().observeRecentlyAdded(10).first().single().id
        val art = tmp.newFile("a.png")
        db.metadataDao().upsertArtwork(io.vela.core.database.entity.ArtworkEntity(gameId = gameId, type = "BOX_FRONT", localPath = art.absolutePath, updatedAt = 1))

        scanner.removeSource(source)

        assertThat(db.gameDao().observeTotalCount().first()).isEqualTo(0)
        assertThat(db.libraryDao().sources()).isEmpty()
        assertThat(db.metadataDao().artworkCount()).isEqualTo(0)
        assertThat(art.exists()).isFalse()
    }
}
