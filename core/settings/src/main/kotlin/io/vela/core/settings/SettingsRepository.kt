package io.vela.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.catch
import java.io.IOException
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
        .catch { e ->
            if (e is IOException) { Timber.e(e, "Settings store unreadable, using defaults"); emit(emptyPreferences()) } else throw e
        }
        .map { prefs -> prefs[KEY]?.let(::decodeOrNull) ?: AppSettings() }
        .distinctUntilChanged()

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { prefs ->
            val raw = prefs[KEY]
            val decoded = raw?.let(::decodeOrNull)
            if (raw != null && decoded == null) {
                // Never overwrite a document we could not read: keep it aside for recovery.
                Timber.w("Settings JSON unreadable; keeping a copy under %s", BROKEN_KEY.name)
                prefs[BROKEN_KEY] = raw
            }
            prefs[KEY] = VelaJson.encodeToString(AppSettings.serializer(), transform(decoded ?: AppSettings()))
        }
    }

    private fun decodeOrNull(json: String): AppSettings? = try {
        VelaJson.decodeFromString(AppSettings.serializer(), json)
    } catch (e: Exception) {
        Timber.w(e, "Settings JSON unreadable, falling back to defaults")
        null
    }

    private companion object {
        val KEY = stringPreferencesKey("app_settings_json")
        val BROKEN_KEY = stringPreferencesKey("app_settings_json_broken")
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
