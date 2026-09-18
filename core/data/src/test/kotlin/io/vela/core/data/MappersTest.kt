package io.vela.core.data

import com.google.common.truth.Truth.assertThat
import io.vela.core.data.mapper.toDomain
import io.vela.core.data.mapper.toEntity
import io.vela.core.database.entity.ArtworkEntity
import io.vela.core.database.entity.GameEntity
import io.vela.core.database.entity.GameMetadataEntity
import io.vela.core.database.entity.LocationType
import io.vela.core.model.ArtworkType
import io.vela.core.model.GameLocation
import io.vela.core.model.PlatformId
import io.vela.core.model.PlatformSettings
import io.vela.core.model.PlayerId
import org.junit.Test

class MappersTest {

    @Test
    fun `game entity maps location, metadata and artwork`() {
        val entity = GameEntity(
            id = 7, platformId = "psx", kind = "ROM", title = "t", sortTitle = "t", locationType = LocationType.DOCUMENT,
            locationValue = "content://x", fileName = "t.chd", addedAt = 1, duplicateKey = "psx:t",
        )
        val game = entity.toDomain(
            GameMetadataEntity(gameId = 7, title = "Tekken 3", genres = "Fighting|Arcade", releaseDate = "1998-03-26"),
            listOf(ArtworkEntity(7, "BOX_FRONT", "/a/box.png", updatedAt = 1)),
        )
        assertThat(game.location).isEqualTo(GameLocation.Document("content://x"))
        assertThat(game.displayTitle).isEqualTo("Tekken 3")
        assertThat(game.metadata!!.genres).containsExactly("Fighting", "Arcade").inOrder()
        assertThat(game.metadata!!.releaseYear).isEqualTo(1998)
        assertThat(game.artwork[ArtworkType.BOX_FRONT]).isEqualTo("/a/box.png")
    }

    @Test
    fun `platform settings round-trip through json overrides`() {
        val settings = PlatformSettings(PlatformId("snes"), playerId = PlayerId("retroarch"), coreId = "bsnes", launchOverrides = mapOf("core.path" to "/x.so"))
        val back = settings.toEntity().toDomain()
        assertThat(back).isEqualTo(settings)
    }
}
