package io.vela.core.launcher

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import com.google.common.truth.Truth.assertThat
import io.vela.core.catalog.CatalogPlayers
import io.vela.core.catalog.EmulatorCatalog
import io.vela.core.catalog.PlatformCatalog
import io.vela.core.catalog.PlayerCatalog
import io.vela.core.model.Game
import io.vela.core.model.GameId
import io.vela.core.model.GameKind
import io.vela.core.model.GameLocation
import io.vela.core.model.PlatformId
import io.vela.core.model.PlayerId
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * Catalogue recipes reach the resolver only when installed. The package/activity used here are
 * read from the catalogue itself, so nothing in this test is made up.
 */
@RunWith(RobolectricTestRunner::class)
class PlayerResolverTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val emulators = EmulatorCatalog()
    private val platforms = PlatformCatalog()
    private val players = PlayerCatalog()
    private lateinit var resolver: PlayerResolver

    @Before
    fun setUp() {
        resolver = PlayerResolver(
            players, platforms, InstalledPackages(context),
            CatalogPlayers(emulators, platforms, players), EmulatorAvailability(context, emulators),
        )
    }

    private fun install(packageName: String, vararg activities: String) {
        val info = PackageInfo().apply {
            this.packageName = packageName
            applicationInfo = ApplicationInfo().apply { this.packageName = packageName }
            this.activities = activities.map { name -> ActivityInfo().apply { this.name = name; this.packageName = packageName } }.toTypedArray()
        }
        shadowOf(context.packageManager).installPackage(info)
    }

    private fun game(platform: String) = Game(
        id = GameId(1), platformId = PlatformId(platform), kind = GameKind.ROM, title = "T", sortTitle = "t",
        location = GameLocation.File("/storage/emulated/0/ROMs/$platform/Game.bin"), fileName = "Game.bin", addedAt = 0,
    )

    @Test
    fun `nothing installed resolves to not installed, never to a catalogue player`() {
        val r = resolver.resolve(game("nds"), null)
        assertThat(r).isInstanceOf(PlayerResolver.Resolution.NotInstalled::class.java)
        assertThat(resolver.optionsFor(platforms.require(PlatformId("nds"))).none { it.first.id.value.startsWith("catalog.") }).isTrue()
    }

    @Test
    fun `an installed catalogue emulator is offered and resolved when no players json emulator is present`() {
        val recipe = emulators.emulator("nds.com.hydra.noods")!!
        install(recipe.packageName!!, recipe.activity!!)

        val r = resolver.resolve(game("nds"), null)
        assertThat(r).isInstanceOf(PlayerResolver.Resolution.Ready::class.java)
        val ready = r as PlayerResolver.Resolution.Ready
        assertThat(ready.player.definition.id).isEqualTo(PlayerId("catalog.nds.com.hydra.noods"))
        assertThat(ready.player.installedPackage).isEqualTo(recipe.packageName)
        assertThat(resolver.optionsFor(platforms.require(PlatformId("nds"))).map { it.first.id.value }).contains("catalog.nds.com.hydra.noods")
        assertThat(resolver.installedCatalogPlayers().map { it.id.value }).contains("catalog.nds.com.hydra.noods")
    }

    @Test
    fun `a players json emulator wins over a catalogue one`() {
        val melon = players[PlayerId("melonds")]!!
        install(melon.packages.first(), melon.activity!!)
        val recipe = emulators.emulator("nds.com.hydra.noods")!!
        install(recipe.packageName!!, recipe.activity!!)

        val r = resolver.resolve(game("nds"), null) as PlayerResolver.Resolution.Ready
        assertThat(r.player.definition.id).isEqualTo(PlayerId("melonds"))
    }

    @Test
    fun `a catalogue emulator whose activity is gone is skipped`() {
        val recipe = emulators.emulator("nds.com.hydra.noods")!!
        install(recipe.packageName!!, "com.example.SomethingElse")
        assertThat(resolver.resolve(game("nds"), null)).isInstanceOf(PlayerResolver.Resolution.NotInstalled::class.java)
    }

    @Test
    fun `an explicit catalogue player id is honoured`() {
        val recipe = emulators.emulator("nds.com.hydra.noods")!!
        install(recipe.packageName!!, recipe.activity!!)
        val g = game("nds").copy(playerOverride = PlayerId("catalog.nds.com.hydra.noods"))
        val r = resolver.resolve(g, null) as PlayerResolver.Resolution.Ready
        assertThat(r.player.definition.id.value).isEqualTo("catalog.nds.com.hydra.noods")
    }
}
