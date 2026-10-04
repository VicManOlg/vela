package io.vela.ui

import kotlinx.coroutines.flow.flow
import io.vela.core.data.system.StorageAccess
import io.vela.core.data.repository.LibraryRepository
import kotlinx.coroutines.flow.asStateFlow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.vela.core.data.usecase.GameActions
import io.vela.core.data.usecase.UiMessage
import io.vela.core.model.AppSettings
import io.vela.core.model.ThemeSpec
import io.vela.core.model.applying
import io.vela.core.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import io.vela.core.data.usecase.LaunchingGame
import io.vela.core.data.repository.ThemeRepository
import kotlinx.coroutines.flow.combine
import io.vela.core.ui.image.ArtworkPalette
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal enum class ShellTab(val label: String) { HOME("Home"), LIBRARY("Library"), COLLECTIONS("Collections"), SEARCH("Search"), SETTINGS("Settings") }

/** What the current screen wants painted behind everything. */
data class Backdrop(val artwork: String? = null, val accent: Long = 0xFF3D7BFF, /** Vivid colour taken from [artwork], when it has one. */ val dynamicAccent: Long? = null)

@HiltViewModel
class AppViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    settings: SettingsRepository,
    private val themes: ThemeRepository,
    private val actions: GameActions,
    library: LibraryRepository,
    storage: StorageAccess,
) : ViewModel() {

    val settings: StateFlow<AppSettings?> = settings.state

    /** The Shell's tab. Here, not in the Shell, so the Home button can reset it while a detail screen is on top. */
    internal val tab: StateFlow<ShellTab> = savedState.getStateFlow(TAB_KEY, ShellTab.HOME)

    internal fun selectTab(tab: ShellTab) {
        savedState[TAB_KEY] = tab
    }

    /** The chosen theme with the user's Appearance tweaks on top; `gridColumns` predates the overrides and still counts. */
    val theme: StateFlow<ThemeSpec> = combine(settings.settings, themes.catalog) { s, catalog ->
        val overrides = if (s.appearance.gridColumns == null && s.gridColumns > 0) s.appearance.copy(gridColumns = s.gridColumns) else s.appearance
        catalog.byId(s.themeId).applying(overrides)
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, themes.catalog.value.default)

    val messages: Flow<UiMessage> = actions.messages

    val launching: StateFlow<LaunchingGame?> = actions.launching
    private val handoff = LaunchHandoff(viewModelScope, actions.launching, actions::clearLaunching)

    fun onUiPaused() = handoff.onPaused()
    fun onUiResumed() = handoff.onResumed()

    private val _backdrop = MutableStateFlow(Backdrop())
    val backdrop: StateFlow<Backdrop> = _backdrop.asStateFlow()

    private var paletteJob: Job? = null

    // For the `system` background: the user's own art per system, else the scene of the system's
    // most played game; and which system each game belongs to.
    private val userSystemArt: StateFlow<Map<String, String>> = flow { emit(storage.systemArt()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    private val systemScenes: StateFlow<Map<String, String>> = library.observeSystemScenes()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    private val gamePlatforms: StateFlow<Map<Long, String>> = library.observeGamePlatforms()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /**
     * Screens hand over the focused game's art. With the `system` background it stands for its
     * system's one image (game art lives in `artwork/<game id>/`), so moving between games of a
     * system keeps the same picture: nothing to decode, nothing to fade. Art that is not a game's
     * (a system tile's own image) passes through; a system without any image gets its colour.
     */
    private fun backdropArt(artwork: String?): String? {
        if (theme.value.background.mode != SYSTEM_BACKGROUND || artwork == null) return artwork
        val gameId = GAME_ART.find(artwork)?.groupValues?.get(1)?.toLongOrNull() ?: return artwork
        val platform = gamePlatforms.value[gameId] ?: return artwork
        return userSystemArt.value[platform.lowercase()] ?: systemScenes.value[platform]
    }

    fun setBackdrop(rawArtwork: String?, accent: Long) {
        val artwork = backdropArt(rawArtwork)
        val current = _backdrop.value
        if (current.artwork == artwork && current.accent == accent) return
        _backdrop.value = Backdrop(artwork, accent, dynamicAccent = if (artwork == current.artwork) current.dynamicAccent else null)
        paletteJob?.cancel()
        if (artwork == null) return
        paletteJob = viewModelScope.launch {
            val colour = ArtworkPalette.dominant(artwork)
            val latest = _backdrop.value
            if (latest.artwork == artwork) _backdrop.value = latest.copy(dynamicAccent = colour)
        }
    }

    private companion object {
        const val TAB_KEY = "shellTab"
        const val SYSTEM_BACKGROUND = "system"
        val GAME_ART = Regex("""/artwork/(\d+)/""")
    }
}
