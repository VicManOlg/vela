package io.vela.core.data

import com.google.common.truth.Truth.assertThat
import io.vela.core.common.DispatcherProvider
import io.vela.core.data.system.StorageAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class StorageAccessTest {

    private val context = RuntimeEnvironment.getApplication()
    private val dispatchers = object : DispatcherProvider {
        override val io = Dispatchers.Unconfined
        override val default = Dispatchers.Unconfined
        override val main = Dispatchers.Unconfined
    }

    @Test
    fun `system art is keyed by lower-case name and skips other files`() = runTest {
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "system-art").apply { mkdirs() }
        File(dir, "SNES.png").writeText("x")
        File(dir, "favorites.webp").writeText("x")
        File(dir, "notes.txt").writeText("x")

        val art = StorageAccess(context, dispatchers).systemArt()

        assertThat(art.keys).containsExactly("snes", "favorites")
        assertThat(art["snes"]).endsWith("SNES.png")
    }
}
