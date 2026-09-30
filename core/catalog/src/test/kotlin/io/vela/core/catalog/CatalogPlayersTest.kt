package io.vela.core.catalog

import com.google.common.truth.Truth.assertThat
import io.vela.core.model.ExtraType
import io.vela.core.model.PlatformId
import io.vela.core.model.PlayerId
import io.vela.core.model.RomDelivery
import org.junit.Test

/** Translation of Daijishō recipes into Vela players. Every recipe used here is real catalogue data. */
class CatalogPlayersTest {

    private val emulators = EmulatorCatalog()
    private val platforms = PlatformCatalog()
    private val players = PlayerCatalog()
    private val catalog = CatalogPlayers(emulators, platforms, players)

    @Test
    fun `content uri recipe keeps action, category, mime type and flags`() {
        val recipe = emulators.emulator("psp.org.ppsspp.ppsspp")!!
        val def = CatalogPlayers.translate(recipe, PlatformId("psp"))!!
        assertThat(def.id).isEqualTo(PlayerId("catalog.psp.org.ppsspp.ppsspp"))
        assertThat(def.packages).containsExactly("org.ppsspp.ppsspp")
        assertThat(def.activity).isEqualTo("org.ppsspp.ppsspp.PpssppActivity")
        assertThat(def.action).isEqualTo("android.intent.action.VIEW")
        assertThat(def.categories).containsExactly("android.intent.category.DEFAULT")
        assertThat(def.delivery).isEqualTo(RomDelivery.CONTENT_URI_DATA)
        assertThat(def.mimeType).isEqualTo("application/octet-stream")
        assertThat(def.flags).containsAtLeast("FLAG_ACTIVITY_CLEAR_TASK", "FLAG_ACTIVITY_CLEAR_TOP", "FLAG_ACTIVITY_NO_HISTORY", "FLAG_ACTIVITY_NEW_TASK")
        assertThat(def.requiresFilePath).isFalse()
        assertThat(def.notes).contains("psp.org.ppsspp.ppsspp")
    }

    @Test
    fun `path extras become rom path extras and require a file path`() {
        val recipe = emulators.emulator("atari5200.ra64.atari800")!!
        val def = CatalogPlayers.translate(recipe, PlatformId("atari5200"))!!
        assertThat(def.action).isNull()
        assertThat(def.delivery).isEqualTo(RomDelivery.EXTRAS_ONLY)
        assertThat(def.requiresFilePath).isTrue()
        assertThat(def.extras.map { it.key to it.value }).containsExactly(
            "ROM" to "{rom.path}",
            "LIBRETRO" to "atari800",
            "CONFIGFILE" to "/storage/emulated/0/Android/data/com.retroarch.aarch64/files/retroarch.cfg",
        ).inOrder()
        assertThat(def.extras.all { it.type == ExtraType.STRING }).isTrue()
        assertThat(def.name).isEqualTo("RetroArch 64 - atari800")
    }

    @Test
    fun `boolean extras keep their type`() {
        val recipe = emulators.emulator("psx.com.github.stenzek.duckstation")!!
        val def = CatalogPlayers.translate(recipe, PlatformId("psx"))!!
        assertThat(def.extras.first { it.key == "resumeState" }.type).isEqualTo(ExtraType.BOOLEAN)
        assertThat(def.extras.first { it.key == "bootPath" }.value).isEqualTo("{rom.path}")
    }

    @Test
    fun `recipes that need Daijishou tags are not translated`() {
        val recipe = emulators.emulator("xbox360.emu.x360mobile.com")!!
        assertThat(recipe.placeholders).contains("{tags.x360url}")
        assertThat(CatalogPlayers.translate(recipe, PlatformId("xbox360"))).isNull()
    }

    @Test
    fun `players json wins over the catalogue for the same package`() {
        val psp = catalog.forPlatform(PlatformId("psp"))
        assertThat(psp.none { "org.ppsspp.ppsspp" in it.packages || "org.ppsspp.ppssppgold" in it.packages }).isTrue()
        val nds = catalog.forPlatform(PlatformId("nds"))
        assertThat(nds.none { "me.magnum.melonds" in it.packages || "com.dsemu.drastic" in it.packages }).isTrue()
        assertThat(nds.map { it.id.value }).contains("catalog.nds.com.hydra.noods")
    }

    @Test
    fun `vela platform ids map to Daijishou ids where they differ`() {
        assertThat(catalog.forPlatform(PlatformId("megadrive")).map { it.id.value }).contains("catalog.genesis.com.explusalpha.MdEmu")
        assertThat(catalog.forPlatform(PlatformId("pcengine")).map { it.id.value }).contains("catalog.tg16.com.PceEmu")
        // Arcade maps to mame + fbneo, but every recipe there is RetroArch or MAME4droid, which players.json covers.
        assertThat(platforms.require(PlatformId("arcade")).catalogIds).containsExactly("mame", "fbneo").inOrder()
        assertThat(catalog.forPlatform(PlatformId("arcade"))).isEmpty()
        assertThat(catalog.forPlatform(PlatformId("android"))).isEmpty()
    }

    @Test
    fun `every translated player has a package, an absolute activity and the platform`() {
        platforms.platforms.forEach { p ->
            catalog.forPlatform(p.id).forEach { def ->
                assertThat(def.id.value).startsWith(CatalogPlayers.PREFIX)
                assertThat(def.packages).hasSize(1)
                assertThat(def.activity).contains(".")
                assertThat(def.activity!!.startsWith(".")).isFalse()
                assertThat(def.platforms).containsExactly(p.id)
                assertThat(def.flags).contains("FLAG_ACTIVITY_NEW_TASK")
            }
        }
        assertThat(catalog[PlayerId("catalog.nds.com.hydra.noods")]).isNotNull()
        assertThat(catalog[PlayerId("melonds")]).isNull()
    }
}
