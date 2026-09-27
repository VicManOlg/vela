package io.vela.core.launcher

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import com.google.common.truth.Truth.assertThat
import io.vela.core.model.PlatformId
import io.vela.core.model.PlayerDefinition
import io.vela.core.model.PlayerId
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class InstalledPackagesTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    private fun install(packageName: String, vararg activities: String) {
        val info = PackageInfo().apply {
            this.packageName = packageName
            applicationInfo = ApplicationInfo().apply { this.packageName = packageName }
            this.activities = activities.map { name ->
                ActivityInfo().apply { this.name = name; this.packageName = packageName }
            }.toTypedArray()
        }
        shadowOf(context.packageManager).installPackage(info)
    }

    private val eden = PlayerDefinition(
        id = PlayerId("eden"), name = "Eden",
        packages = listOf("dev.eden.eden_emulator"),
        activity = "org.yuzu.yuzu_emu.activities.EmulationActivity",
        platforms = listOf(PlatformId("switch")),
    )

    @Test
    fun `known package wins`() {
        install("dev.eden.eden_emulator", "org.yuzu.yuzu_emu.activities.EmulationActivity")
        install("com.example.edenfork", "org.yuzu.yuzu_emu.activities.EmulationActivity")
        assertThat(InstalledPackages(context).installedPackage(eden)).isEqualTo("dev.eden.eden_emulator")
    }

    @Test
    fun `a renamed build exposing the same emulation activity is found`() {
        install("com.example.edenoptimized", "org.yuzu.yuzu_emu.activities.EmulationActivity", "com.example.Other")
        install("com.example.unrelated", "com.example.unrelated.MainActivity")
        assertThat(InstalledPackages(context).installedPackage(eden)).isEqualTo("com.example.edenoptimized")
    }

    @Test
    fun `no match when nothing exposes the activity`() {
        install("com.example.unrelated", "com.example.unrelated.MainActivity")
        assertThat(InstalledPackages(context).installedPackage(eden)).isNull()
    }

    @Test
    fun `templated activities are not searched`() {
        val pizza = eden.copy(id = PlayerId("pizza"), packages = listOf("it.dbtecno.pizzaboygba"), activity = "{package}.MainActivity")
        install("com.example.whatever", "com.example.whatever.MainActivity")
        assertThat(InstalledPackages(context).installedPackage(pizza)).isNull()
    }
}
