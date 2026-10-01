package io.vela.core.data.repository

import io.vela.core.model.AppSettings
import io.vela.core.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** The user's settings as feature modules see them; they depend on core:data, never on core:settings. */
@Singleton
class AppSettingsRepository @Inject constructor(private val store: SettingsRepository) {
    val settings: Flow<AppSettings> get() = store.settings

    /** Null only until the first read finishes; see [SettingsRepository.state]. */
    val state: StateFlow<AppSettings?> get() = store.state

    val loaded: AppSettings get() = store.loaded

    suspend fun current(): AppSettings = store.current()

    suspend fun update(transform: (AppSettings) -> AppSettings) = store.update(transform)
}
