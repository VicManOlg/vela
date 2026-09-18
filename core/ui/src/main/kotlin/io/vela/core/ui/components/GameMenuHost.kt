package io.vela.core.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.Composable
import io.vela.core.model.CollectionId
import io.vela.core.model.CompletionStatus
import io.vela.core.model.GameMenuState
import io.vela.core.model.LaunchOption

/** Callbacks the host needs from the owning ViewModel. */
class GameMenuCallbacks(
    val onAction: (String) -> Unit,
    val onDismiss: () -> Unit,
    val onToggleCollection: (CollectionId) -> Unit,
    val onStartNewCollection: () -> Unit,
    val onCreateCollection: (String) -> Unit,
    val onLaunchWith: (LaunchOption, remember: Boolean) -> Unit,
    val onSetCompletion: (CompletionStatus) -> Unit,
    val onConfirmHide: () -> Unit,
)

/** Renders whichever dialog the shared game menu state asks for. */
@Composable
fun GameMenuHost(state: GameMenuState, callbacks: GameMenuCallbacks) {
    when (state) {
        GameMenuState.Hidden -> Unit
        is GameMenuState.Context -> GameContextMenu(
            game = state.game,
            completion = state.completion,
            onAction = { callbacks.onAction(it.name) },
            onDismiss = callbacks.onDismiss,
        )
        is GameMenuState.Collections -> VelaMenuDialog(
            title = "Collections",
            subtitle = state.game.title,
            options = state.collections.map { c ->
                MenuOption(
                    id = c.id.value.toString(),
                    label = c.name,
                    description = if (c.gameCount == 1) "1 game" else "${c.gameCount} games",
                    icon = if (c.id in state.memberOf) Icons.Rounded.Check else null,
                    selected = c.id in state.memberOf,
                )
            } + MenuOption("new", "New collection…", icon = Icons.Rounded.Add),
            onSelect = { opt ->
                if (opt.id == "new") callbacks.onStartNewCollection() else callbacks.onToggleCollection(CollectionId(opt.id.toLong()))
            },
            onDismiss = callbacks.onDismiss,
        )
        is GameMenuState.NewCollection -> TextInputDialog(
            title = "New collection",
            initial = "",
            placeholder = "Zelda, Resident Evil, Pokémon…",
            confirmLabel = "Create",
            onConfirm = callbacks.onCreateCollection,
            onDismiss = callbacks.onDismiss,
        )
        is GameMenuState.LaunchWith -> VelaMenuDialog(
            title = "Launch with",
            subtitle = "Hold the confirm button to make it the default for this game",
            options = state.options.map { o ->
                MenuOption(
                    id = o.id,
                    label = o.label,
                    description = if (o.installed) null else "Not installed",
                    selected = o.isCurrent,
                    enabled = o.installed,
                )
            },
            onSelect = { opt -> state.options.firstOrNull { it.id == opt.id }?.let { callbacks.onLaunchWith(it, false) } },
            onDismiss = callbacks.onDismiss,
        )
        is GameMenuState.Completion -> CompletionMenu(
            current = state.current,
            onSelect = callbacks.onSetCompletion,
            onDismiss = callbacks.onDismiss,
        )
        is GameMenuState.ConfirmHide -> ConfirmDialog(
            title = "Hide ${state.game.title}?",
            message = "It disappears from every list. You can show hidden games again from Settings > Library.",
            confirmLabel = "Hide",
            onConfirm = callbacks.onConfirmHide,
            onDismiss = callbacks.onDismiss,
            danger = true,
        )
    }
}
