package io.vela.core.database

import android.content.Context
import androidx.room.Room
import org.robolectric.RuntimeEnvironment
import com.google.common.truth.Truth.assertThat
import io.vela.core.database.entity.ArtworkEntity
import io.vela.core.database.entity.GameEntity
import io.vela.core.database.entity.GameMetadataEntity
import io.vela.core.database.entity.LocationType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GameDaoTest {

    private lateinit var db: VelaDatabase

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, VelaDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private fun game(title: String, platform: String = "snes", path: String = "/roms/$platform/$title.sfc") = GameEntity(
        platformId = platform, kind = "ROM", title = title, sortTitle = title.lowercase(),
        locationType = LocationType.FILE, locationValue = path, fileName = "$title.sfc",
        addedAt = 1L, duplicateKey = "$platform:${title.lowercase()}",
    )

    @Test
    fun `insert is idempotent per location`() = runTest {
        val first = db.gameDao().insertIgnore(game("Chrono Trigger"))
        val second = db.gameDao().insertIgnore(game("Chrono Trigger"))
        assertThat(first).isGreaterThan(0)
        assertThat(second).isEqualTo(-1)
        assertThat(db.gameDao().observeTotalCount().first()).isEqualTo(1)
    }

    @Test
    fun `summary view joins metadata title and box art`() = runTest {
        val id = db.gameDao().insertIgnore(game("chrono trigger (usa)"))
        db.metadataDao().upsertMetadata(GameMetadataEntity(gameId = id, title = "Chrono Trigger", genres = "RPG"))
        db.metadataDao().upsertArtwork(ArtworkEntity(gameId = id, type = "BOX_FRONT", localPath = "/art/ct.png", updatedAt = 1))
        val summary = db.gameDao().observeSummary(id).first()!!
        assertThat(summary.title).isEqualTo("Chrono Trigger")
        assertThat(summary.boxArt).isEqualTo("/art/ct.png")
        assertThat(summary.genres).isEqualTo("RPG")
    }

    @Test
    fun `fts search finds partial titles`() = runTest {
        db.gameDao().insertIgnore(game("Super Metroid"))
        db.gameDao().insertIgnore(game("Super Mario World"))
        db.gameDao().insertIgnore(game("Zelda"))
        val hits = db.gameDao().searchTitles("super*", 10)
        assertThat(hits.map { it.title }).containsExactly("Super Metroid", "Super Mario World")
    }

    @Test
    fun `recently played and favorites`() = runTest {
        val a = db.gameDao().insertIgnore(game("A"))
        val b = db.gameDao().insertIgnore(game("B"))
        db.gameDao().recordLaunch(a, 100)
        db.gameDao().recordLaunch(b, 200)
        db.gameDao().setFavorite(a, true)
        assertThat(db.gameDao().observeRecentlyPlayed(10).first().map { it.title }).containsExactly("B", "A").inOrder()
        assertThat(db.gameDao().observeFavorites(10).first().map { it.title }).containsExactly("A")
    }

    @Test
    fun `missing files are marked not present but kept`() = runTest {
        db.libraryDao().insertSource(io.vela.core.database.entity.LibrarySourceEntity(id = 1, uri = "file:///roms", displayName = "roms", access = "FILE"))
        val keep = db.gameDao().insertIgnore(game("Keep").copy(sourceId = 1))
        val gone = db.gameDao().insertIgnore(game("Gone").copy(sourceId = 1))
        db.gameDao().markSeen(listOf(keep), generation = 2)
        val missing = db.gameDao().markMissingForSource(1, generation = 2)
        assertThat(missing).isEqualTo(1)
        assertThat(db.gameDao().byId(gone)!!.present).isFalse()
        assertThat(db.gameDao().observeTotalCount().first()).isEqualTo(1)
    }
}
