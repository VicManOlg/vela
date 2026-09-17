package io.vela.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.vela.core.common.VelaJson
import io.vela.core.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for [AppSettings]. The whole object is stored as one JSON document so
 * adding a preference is a one-line model change with a default value - no migrations.
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<AppSettings> = dataStore.data
        .map { prefs -> prefs[KEY]?.let(::decode) ?: AppSettings() }
        .distinctUntilChanged()

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { prefs ->
            val current = prefs[KEY]?.let(::decode) ?: AppSettings()
            prefs[KEY] = VelaJson.encodeToString(AppSettings.serializer(), transform(current))
        }
    }

    private fun decode(json: String): AppSettings = try {
        VelaJson.decodeFromString(AppSettings.serializer(), json)
    } catch (e: Exception) {
        Timber.w(e, "Settings JSON unreadable, falling back to defaults")
        AppSettings()
    }

    private companion object {
        val KEY = stringPreferencesKey("app_settings_json")
    }
}

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "vela_settings")

@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {
    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> = context.settingsDataStore
}
