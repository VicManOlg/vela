package io.vela.core.data.repository

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.vela.core.catalog.ThemeCatalog
import io.vela.core.common.ApplicationScope
import io.vela.core.common.DispatcherProvider
import io.vela.core.common.VelaJson
import io.vela.core.model.ThemeSpec
import io.vela.core.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Every theme the user can pick: the bundled ones, JSON files dropped into
 * `Android/data/<app>/files/themes/` or imported through the file picker, and the "custom" theme
 * the in-app editor writes into settings. Re-reads the folder on [reload] and after an import.
 */
@Singleton
class ThemeRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val dispatchers: DispatcherProvider,
    scope: ApplicationScope,
) {
    private val folder: File get() = File(context.getExternalFilesDir(null) ?: context.filesDir, "themes")
    private val scope = scope
    // Filled off the main thread: this singleton is created during startup injection.
    private val fileThemes = MutableStateFlow<List<String>>(emptyList())

    init {
        reload()
    }

    val catalog: StateFlow<ThemeCatalog> = combine(
        fileThemes,
        settings.settings.map { it.customThemeJson }.distinctUntilChanged(),
    ) { files, custom -> ThemeCatalog(files + listOfNotNull(custom)) }
        .stateIn(scope, SharingStarted.Eagerly, ThemeCatalog(fileThemes.value))

    val themes: StateFlow<List<ThemeSpec>> = catalog.map { it.themes }
        .stateIn(scope, SharingStarted.Eagerly, catalog.value.themes)

    fun byId(id: String): ThemeSpec = catalog.value.byId(id)

    fun reload() {
        scope.launch(dispatchers.io) { fileThemes.value = readFolder() }
    }

    /** Copies a picked JSON file into the themes folder (named after its id) and reloads. */
    suspend fun import(uri: Uri): Result<ThemeSpec> = withContext(dispatchers.io) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: error("Could not read the file")
            val spec = VelaJson.decodeFromString(ThemeSpec.serializer(), text)
            require(spec.id.isNotBlank()) { "The theme has no id" }
            folder.mkdirs()
            File(folder, spec.id.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".json").writeText(text)
            fileThemes.value = readFolder()
            spec
        }.onFailure { Timber.w(it, "Theme import failed") }
    }

    /** Saves the editor's theme as "custom" and selects it. */
    suspend fun saveCustom(spec: ThemeSpec) {
        val custom = spec.copy(id = CUSTOM_ID, author = "You")
        val json = VelaJson.encodeToString(ThemeSpec.serializer(), custom)
        settings.update { it.copy(customThemeJson = json, themeId = CUSTOM_ID) }
    }

    suspend fun clearCustom(fallbackId: String = ThemeCatalog.DEFAULT_ID) {
        settings.update { it.copy(customThemeJson = null, themeId = if (it.themeId == CUSTOM_ID) fallbackId else it.themeId) }
    }

    private fun readFolder(): List<String> =
        folder.listFiles { f -> f.isFile && f.extension.equals("json", ignoreCase = true) }
            ?.sortedBy { it.name }
            ?.mapNotNull { f -> runCatching { f.readText() }.onFailure { Timber.w(it, "Unreadable theme %s", f) }.getOrNull() }
            .orEmpty()

    companion object {
        const val CUSTOM_ID = "custom"
    }
}
