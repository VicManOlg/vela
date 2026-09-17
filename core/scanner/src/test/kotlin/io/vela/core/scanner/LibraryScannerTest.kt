package io.vela.core.scanner

import android.content.Context
import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import io.vela.core.catalog.PlatformCatalog
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
        scanner = LibraryScanner(db, FileSystemSourceFactory(context), PlatformDetector(catalog), dispatchers)
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
    fun `multi disc without playlist keeps only first disc visible`() = runTest {
        file("psx/Metal Gear Solid (Disc 1).chd")
        file("psx/Metal Gear Solid (Disc 2).chd")
        addSource()

        scanner.scanAll()

        val visible = db.gameDao().observeRecentlyAdded(10).first()
        assertThat(visible.map { it.title }).containsExactly("Metal Gear Solid")
        assertThat(visible.single().discNumber).isEqualTo(1)
    }
}
