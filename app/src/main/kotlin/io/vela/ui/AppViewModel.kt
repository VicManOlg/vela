package io.vela.ui

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
import kotlinx.coroutines.flow.map
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

    val backdrop = MutableStateFlow(Backdrop())

    private var paletteJob: Job? = null

    fun setBackdrop(artwork: String?, accent: Long) {
        val current = backdrop.value
        if (current.artwork == artwork && current.accent == accent) return
        backdrop.value = Backdrop(artwork, accent, dynamicAccent = if (artwork == current.artwork) current.dynamicAccent else null)
        paletteJob?.cancel()
        if (artwork == null) return
        paletteJob = viewModelScope.launch {
            val colour = ArtworkPalette.dominant(artwork)
            val latest = backdrop.value
            if (latest.artwork == artwork) backdrop.value = latest.copy(dynamicAccent = colour)
        }
    }

    private companion object {
        const val TAB_KEY = "shellTab"
    }
}
