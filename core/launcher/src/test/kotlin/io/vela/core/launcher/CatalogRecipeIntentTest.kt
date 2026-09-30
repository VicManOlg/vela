package io.vela.core.launcher

import android.content.Context
import android.content.Intent
import com.google.common.truth.Truth.assertThat
import io.vela.core.catalog.CatalogPlayers
import io.vela.core.catalog.EmulatorCatalog
import io.vela.core.model.Game
import io.vela.core.model.GameId
import io.vela.core.model.GameKind
import io.vela.core.model.GameLocation
import io.vela.core.model.PlatformId
import io.vela.core.model.ResolvedPlayer
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Intents built from translated catalogue recipes (real Daijishō data, nothing hand-written). */
@RunWith(RobolectricTestRunner::class)
class CatalogRecipeIntentTest {

    private lateinit var builder: LaunchIntentBuilder
    private val emulators = EmulatorCatalog()

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        builder = LaunchIntentBuilder(context)
    }

    private fun game(location: GameLocation, platform: String, fileName: String) = Game(
        id = GameId(1), platformId = PlatformId(platform), kind = GameKind.ROM, title = "T", sortTitle = "t",
        location = location, fileName = fileName, addedAt = 0,
    )

    @Test
    fun `content uri recipe sets data, type, category and grants`() {
        val def = CatalogPlayers.translate(emulators.emulator("psp.org.ppsspp.ppsspp")!!, PlatformId("psp"))!!
        val uri = "content://com.android.externalstorage.documents/tree/primary%3AROMs/document/primary%3AROMs%2Fpsp%2FGame.iso"
        val prepared = builder.build(game(GameLocation.Document(uri), "psp", "Game.iso"), ResolvedPlayer(def, "org.ppsspp.ppsspp", null), null)
        val i = prepared.intent

        assertThat(i.component!!.className).isEqualTo("org.ppsspp.ppsspp.PpssppActivity")
        assertThat(i.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(i.categories).containsExactly(Intent.CATEGORY_DEFAULT)
        assertThat(i.data.toString()).isEqualTo(uri)
        assertThat(i.type).isEqualTo("application/octet-stream")
        assertThat(i.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION).isNotEqualTo(0)
        assertThat(i.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK).isNotEqualTo(0)
        assertThat(i.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isNotEqualTo(0)
        assertThat(prepared.urisToGrant.map { it.toString() }).containsExactly(uri)
    }

    @Test
    fun `path recipe without action leaves the action unset and fills the extra`() {
        val def = CatalogPlayers.translate(emulators.emulator("atari5200.ra64.atari800")!!, PlatformId("atari5200"))!!
        val prepared = builder.build(game(GameLocation.File("/storage/emulated/0/ROMs/atari5200/Game.a52"), "atari5200", "Game.a52"), ResolvedPlayer(def, "com.retroarch.aarch64", null), null)
        val i = prepared.intent

        assertThat(i.action).isNull()
        assertThat(i.data).isNull()
        assertThat(i.getStringExtra("ROM")).isEqualTo("/storage/emulated/0/ROMs/atari5200/Game.a52")
        assertThat(i.getStringExtra("LIBRETRO")).isEqualTo("atari800")
        assertThat(prepared.urisToGrant).isEmpty()
    }

    @Test
    fun `path as data recipe sends the bare path like am start -d`() {
        val recipe = emulators.platforms.flatMap { it.emulators }.first { it.data == "{file.path}" }
        val platform = emulators.platforms.first { p -> p.emulators.any { it.id == recipe.id } }
        val def = CatalogPlayers.translate(recipe, PlatformId(platform.id))!!
        val path = "/storage/emulated/0/ROMs/${platform.id}/Game.bin"
        val prepared = builder.build(game(GameLocation.File(path), platform.id, "Game.bin"), ResolvedPlayer(def, recipe.packageName!!, null), null)

        assertThat(def.requiresFilePath).isTrue()
        assertThat(prepared.intent.data.toString()).isEqualTo(path)
        assertThat(prepared.intent.data!!.scheme).isNull()
    }
}
