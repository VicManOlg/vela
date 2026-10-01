package io.vela.core.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.vela.core.model.GameMenuEvent
import kotlinx.coroutines.flow.Flow
import io.vela.core.model.CollectionId
import io.vela.core.model.GameMenuActions
import io.vela.core.model.GameMenuState

/**
 * Delivers the menu's one-shot results (open a game's details, a game was hidden) while the
 * screen is started; they queue in between.
 */
@Composable
fun GameMenuEvents(events: Flow<GameMenuEvent>, onEvent: (GameMenuEvent) -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latest by rememberUpdatedState(onEvent)
    LaunchedEffect(events, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { events.collect { latest(it) } }
    }
}

/** Renders whichever dialog the shared game menu state asks for. */
@Composable
fun GameMenuHost(state: GameMenuState, actions: GameMenuActions) {
    when (state) {
        GameMenuState.Hidden -> Unit
        is GameMenuState.Context -> GameContextMenu(
            game = state.game,
            completion = state.completion,
            onAction = actions::onAction,
            onDismiss = actions::onDismiss,
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
                if (opt.id == "new") actions.onStartNewCollection() else actions.onToggleCollection(CollectionId(opt.id.toLong()))
            },
            onDismiss = actions::onDismiss,
        )
        is GameMenuState.NewCollection -> TextInputDialog(
            title = "New collection",
            initial = "",
            placeholder = "Zelda, Resident Evil, Pokémon…",
            confirmLabel = "Create",
            onConfirm = actions::onCreateCollection,
            onDismiss = actions::onDismiss,
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
            onSelect = { opt -> state.options.firstOrNull { it.id == opt.id }?.let { actions.onLaunchWith(it, false) } },
            onDismiss = actions::onDismiss,
        )
        is GameMenuState.Completion -> CompletionMenu(
            current = state.current,
            onSelect = actions::onSetCompletion,
            onDismiss = actions::onDismiss,
        )
        is GameMenuState.Rate -> RatingMenu(
            current = state.current,
            onSelect = actions::onRate,
            onDismiss = actions::onDismiss,
        )
        is GameMenuState.ConfirmHide -> ConfirmDialog(
            title = "Hide ${state.game.title}?",
            message = "It disappears from every list. You can show hidden games again from Settings > Library.",
            confirmLabel = "Hide",
            onConfirm = actions::onConfirmHide,
            onDismiss = actions::onDismiss,
            danger = true,
        )
    }
}
