package io.vela.core.data

import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import io.vela.core.common.DispatcherProvider
import io.vela.core.data.repository.GameQuery
import io.vela.core.data.repository.GameRepository
import io.vela.core.database.VelaDatabase
import io.vela.core.database.entity.GameEntity
import io.vela.core.database.entity.LocationType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class GameQueryTest {

    private val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), VelaDatabase::class.java).allowMainThreadQueries().build()
    private val dispatchers = object : DispatcherProvider {
        override val io = Dispatchers.Unconfined
        override val default = Dispatchers.Unconfined
        override val main = Dispatchers.Unconfined
    }
    private val repository = GameRepository(db.gameDao(), db.metadataDao(), dispatchers)

    @After
    fun tearDown() = db.close()

    private suspend fun add(title: String, key: String, playCount: Int = 0, hidden: Boolean = false) = db.gameDao().insertIgnore(
        GameEntity(
            platformId = "snes", kind = "ROM", title = title, sortTitle = title.lowercase(),
            locationType = LocationType.FILE, locationValue = "/roms/$title", fileName = title,
            addedAt = 1L, duplicateKey = key, playCount = playCount, hidden = hidden,
        ),
    )

    @Test
    fun `one row per duplicate key, preferring the played and visible copy`() = runTest {
        add("Chrono Trigger (Europe)", "snes:chrono trigger")
        add("Chrono Trigger (USA)", "snes:chrono trigger", playCount = 3)
        add("Chrono Trigger (Japan)", "snes:chrono trigger", playCount = 9, hidden = true)
        add("Earthbound", "snes:earthbound")

        val titles = repository.observeGames(GameQuery()).first().map { it.title }

        assertThat(titles).containsExactly("Chrono Trigger (USA)", "Earthbound")
    }
}
