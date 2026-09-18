package io.vela.core.launcher

import android.content.Context
import android.content.Intent
import com.google.common.truth.Truth.assertThat
import io.vela.core.catalog.PlayerCatalog
import io.vela.core.model.CoreOption
import io.vela.core.model.Game
import io.vela.core.model.GameId
import io.vela.core.model.GameKind
import io.vela.core.model.GameLocation
import io.vela.core.model.PlatformId
import io.vela.core.model.PlayerId
import io.vela.core.model.ResolvedPlayer
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class LaunchIntentBuilderTest {

    private lateinit var builder: LaunchIntentBuilder
    private val players = PlayerCatalog()

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
    fun `retroarch gets path, core and config extras`() {
        val def = players[PlayerId("retroarch")]!!
        val core = CoreOption("snes9x", "Snes9x", "snes9x_libretro_android.so")
        val g = game(GameLocation.Document("content://com.android.externalstorage.documents/tree/primary%3AROMs/document/primary%3AROMs%2Fsnes%2FGame.sfc"), "snes", "Game.sfc")

        val prepared = builder.build(g, ResolvedPlayer(def, "com.retroarch.aarch64", core), null)
        val i = prepared.intent

        assertThat(i.component!!.className).isEqualTo("com.retroarch.browser.retroactivity.RetroActivityFuture")
        assertThat(i.getStringExtra("ROM")).endsWith("/ROMs/snes/Game.sfc")
        assertThat(i.getStringExtra("LIBRETRO")).isEqualTo("/data/data/com.retroarch.aarch64/cores/snes9x_libretro_android.so")
        assertThat(i.getStringExtra("CONFIGFILE")).contains("com.retroarch.aarch64/files/retroarch.cfg")
        assertThat(i.hasExtra("QUITFOCUS")).isTrue()
        assertThat(i.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isNotEqualTo(0)
    }

    @Test
    fun `content uri players get data, mime type and read grant`() {
        val def = players[PlayerId("azahar")]!!
        val uri = "content://com.android.externalstorage.documents/tree/primary%3AROMs/document/primary%3AROMs%2F3ds%2FGame.3ds"
        val g = game(GameLocation.Document(uri), "n3ds", "Game.3ds")

        val prepared = builder.build(g, ResolvedPlayer(def, "org.azahar_emu.azahar", null), null)
        val i = prepared.intent

        assertThat(i.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(i.data.toString()).isEqualTo(uri)
        assertThat(i.type).isEqualTo("application/octet-stream")
        assertThat(i.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION).isNotEqualTo(0)
        assertThat(prepared.urisToGrant).hasSize(1)
    }

    @Test
    fun `string extras holding content uris are granted through clipData`() {
        val def = players[PlayerId("nethersx2")]!!
        val uri = "content://com.android.externalstorage.documents/tree/primary%3AROMs/document/primary%3AROMs%2Fps2%2FGame.chd"
        val g = game(GameLocation.Document(uri), "ps2", "Game.chd")

        val prepared = builder.build(g, ResolvedPlayer(def, "xyz.aethersx2.android", null), null)

        assertThat(prepared.intent.getStringExtra("bootPath")).isEqualTo(uri)
        assertThat(prepared.intent.clipData!!.getItemAt(0).uri.toString()).isEqualTo(uri)
        assertThat(prepared.urisToGrant.map { it.toString() }).containsExactly(uri)
    }

    @Test
    fun `package placeholder expands in action and activity`() {
        val def = players[PlayerId("melonds")]!!
        val uri = "content://com.android.externalstorage.documents/tree/primary%3AROMs/document/primary%3AROMs%2Fnds%2FGame.nds"
        val g = game(GameLocation.Document(uri), "nds", "Game.nds")

        val prepared = builder.build(g, ResolvedPlayer(def, "me.magnum.melonds", null), null)

        assertThat(prepared.intent.action).isEqualTo("me.magnum.melonds.LAUNCH_ROM")
        assertThat(prepared.intent.component!!.packageName).isEqualTo("me.magnum.melonds")
        assertThat(prepared.intent.data.toString()).isEqualTo(uri)
    }

    @Test
    fun `document path is derived for external storage documents`() {
        val primary = builder.documentPath("content://com.android.externalstorage.documents/tree/primary%3AROMs/document/primary%3AROMs%2Fsnes%2FGame.sfc")
        assertThat(primary).endsWith("/ROMs/snes/Game.sfc")
        val sd = builder.documentPath("content://com.android.externalstorage.documents/tree/1234-ABCD%3AROMs/document/1234-ABCD%3AROMs%2Fx.gba")
        assertThat(sd).isEqualTo("/storage/1234-ABCD/ROMs/x.gba")
        assertThat(builder.documentPath("content://other.provider/document/1")).isNull()
    }

    @Test
    fun `string array extras split on commas`() {
        val def = players[PlayerId("vita3k")]!!
        val g = game(GameLocation.File("/roms/vita/PCSE00001.psvita"), "psvita", "PCSE00001.psvita")
        val prepared = builder.build(g, ResolvedPlayer(def, "org.vita3k.emulator", null), null)
        assertThat(prepared.intent.getStringArrayExtra("AppStartParameters")!!.toList()).containsExactly("-r", "PCSE00001").inOrder()
    }
}
