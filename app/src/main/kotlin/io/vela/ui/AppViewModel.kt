package io.vela.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.vela.core.catalog.ThemeCatalog
import io.vela.core.data.usecase.GameActions
import io.vela.core.data.usecase.UiMessage
import io.vela.core.model.AppSettings
import io.vela.core.model.ThemeSpec
import io.vela.core.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import io.vela.core.data.usecase.LaunchingGame

/** What the current screen wants painted behind everything. */
data class Backdrop(val artwork: String? = null, val accent: Long = 0xFF3D7BFF)

@HiltViewModel
class AppViewModel @Inject constructor(
    settings: SettingsRepository,
    private val themes: ThemeCatalog,
    private val actions: GameActions,
) : ViewModel() {

    val settings: StateFlow<AppSettings?> = settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val theme: StateFlow<ThemeSpec> = settings.settings.map { themes.byId(it.themeId) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, themes.default)

    val messages: SharedFlow<UiMessage> = actions.messages

    val launching: StateFlow<LaunchingGame?> = actions.launching
    fun clearLaunching() = actions.clearLaunching()

    val backdrop = MutableStateFlow(Backdrop())

    fun setBackdrop(artwork: String?, accent: Long) {
        val next = Backdrop(artwork, accent)
        if (backdrop.value != next) backdrop.value = next
    }
}
