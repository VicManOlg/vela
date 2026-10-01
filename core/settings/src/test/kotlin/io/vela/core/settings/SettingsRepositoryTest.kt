package io.vela.core.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import io.vela.core.common.ApplicationScope
import io.vela.core.common.DispatcherProvider
import io.vela.core.model.AppSettings
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryTest {

    @get:Rule val folder = TemporaryFolder()

    @Test
    fun `a corrupt settings file falls back to defaults and stays writable`() = runTest {
        // DataStore replaces the file by renaming over it, which a Windows JVM refuses.
        assumeFalse(System.getProperty("os.name").startsWith("Windows"))
        val file = folder.newFile("vela_settings.preferences_pb").apply { writeBytes(byteArrayOf(0x7f, 0x01, 0x02, 0x03, 0x04)) }
        val store = PreferenceDataStoreFactory.create(corruptionHandler = settingsCorruptionHandler, scope = backgroundScope) { file }
        val dispatchers = object : DispatcherProvider {
            override val io = StandardTestDispatcher(testScheduler)
            override val default = io
            override val main = io
        }
        val repository = SettingsRepository(store, ApplicationScope(dispatchers))

        assertThat(repository.current()).isEqualTo(AppSettings())
        repository.update { it.copy(setupCompleted = true) }
        assertThat(repository.current().setupCompleted).isTrue()
    }
}
