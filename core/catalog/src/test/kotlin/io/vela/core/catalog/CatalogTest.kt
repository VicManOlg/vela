package io.vela.core.catalog

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.vela.core.model.PlatformId
import io.vela.core.model.PlayerId
import org.junit.Test

class CatalogTest {

    private val platforms = PlatformCatalog()
    private val players = PlayerCatalog()

    @Test
    fun `platform catalog loads and has unique ids`() {
        assertThat(platforms.platforms).isNotEmpty()
        assertThat(platforms.platforms.map { it.id }.toSet()).hasSize(platforms.platforms.size)
    }

    @Test
    fun `folder aliases resolve`() {
        assertThat(platforms.byFolderName("SNES")?.id).isEqualTo(PlatformId("snes"))
        assertThat(platforms.byFolderName("Super Nintendo")?.id).isEqualTo(PlatformId("snes"))
        assertThat(platforms.byFolderName("01 - PSX")?.id).isEqualTo(PlatformId("psx"))
        assertThat(platforms.byFolderName("random")).isNull()
    }

    @Test
    fun `unambiguous extensions map to a single platform`() {
        assertThat(platforms.byExtension("sfc").map { it.id.value }).containsExactly("snes")
        assertThat(platforms.byExtension("gba").map { it.id.value }).containsExactly("gba")
        assertThat(platforms.isUnambiguousExtension("iso")).isFalse()
    }

    @Test
    fun `every default player exists in the player catalog`() {
        platforms.platforms.flatMap { it.defaultPlayers }.forEach { id ->
            assertWithMessage("player $id").that(players[id]).isNotNull()
        }
    }

    @Test
    fun `retroarch is offered for platforms it has cores for`() {
        val snes = platforms.require(PlatformId("snes"))
        val offered = players.forPlatform(snes.id, snes.defaultPlayers).map { it.id }
        assertThat(offered).contains(PlayerId("retroarch"))
        val ps2 = platforms.require(PlatformId("ps2"))
        assertThat(players.forPlatform(ps2.id, ps2.defaultPlayers).map { it.id.value }).containsAtLeast("nethersx2", "play").inOrder()
    }

    @Test
    fun `themes load`() {
        val themes = ThemeCatalog()
        assertThat(themes.themes.map { it.id }).containsAtLeast("vela-night", "vela-ember", "vela-mono")
        assertThat(themes.default.id).isEqualTo(ThemeCatalog.DEFAULT_ID)
    }
}
