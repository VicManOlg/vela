package io.vela.feature.library

import io.vela.core.ui.components.rememberedItems
import io.vela.core.ui.components.rememberFocusMemory
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.vela.core.data.repository.CollectionRepository
import io.vela.core.model.CollectionId
import io.vela.core.model.GameCollection
import io.vela.core.ui.components.CollectionTile
import io.vela.core.ui.components.ConfirmDialog
import io.vela.core.ui.components.EmptyState
import io.vela.core.ui.components.MenuOption
import io.vela.core.ui.components.TextInputDialog
import io.vela.core.ui.components.VelaButton
import io.vela.core.ui.components.VelaMenuDialog
import io.vela.core.ui.components.focusBleed
import io.vela.core.ui.components.rememberAutoFocus
import androidx.compose.ui.focus.focusRequester
import io.vela.core.ui.input.GamepadButton
import io.vela.core.ui.input.GamepadHandler
import io.vela.core.ui.theme.VelaTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CollectionsViewModel @Inject constructor(private val repo: CollectionRepository) : ViewModel() {
    /** Null until the first database read: the empty state must not flash (and take the focus) meanwhile. */
    val collections: StateFlow<List<GameCollection>?> = repo.observeCollections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun create(name: String) = viewModelScope.launch { repo.create(name) }
    fun rename(id: CollectionId, name: String) = viewModelScope.launch { repo.rename(id, name) }
    fun delete(id: CollectionId) = viewModelScope.launch { repo.delete(id) }
}

/** Collections tab: manual collections as tiles; X creates, long-press edits. */
@Composable
fun CollectionsScreen(
    onOpenCollection: (CollectionId) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CollectionsViewModel = hiltViewModel(),
) {
    val loadedCollections by viewModel.collections.collectAsStateWithLifecycle()
    val collections = loadedCollections.orEmpty()
    val colors = VelaTheme.colors
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<GameCollection?>(null) }
    var renaming by remember { mutableStateOf<GameCollection?>(null) }
    var deleting by remember { mutableStateOf<GameCollection?>(null) }

    GamepadHandler { button ->
        if (button == GamepadButton.X) { creating = true; true } else false
    }

    Column(modifier.fillMaxSize()) {
        // The tab bar already names the section; one line counts what is here (the empty state
        // speaks for itself).
        if (collections.isNotEmpty()) {
            Text(
                if (collections.size == 1) "1 collection" else "${collections.size} collections",
                style = VelaTheme.typography.body,
                color = colors.muted,
                modifier = Modifier.padding(horizontal = VelaTheme.dimens.screenPadding).padding(top = 12.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        val memory = rememberFocusMemory()
        if (loadedCollections == null) {
            // Nothing yet: neither the empty state nor the grid.
        } else if (collections.isEmpty()) {
            val emptyFocus = rememberAutoFocus()
            EmptyState(
                title = "No collections yet",
                actionModifier = Modifier.focusRequester(emptyFocus),
                message = "Create one for a series, a mood or a weekend. Add games from any game menu.",
                actionLabel = "New collection",
                onAction = { creating = true },
            )
        } else {
            val autoFocus = rememberAutoFocus(memory = memory)
            LazyVerticalGrid(
                columns = GridCells.Adaptive(VelaTheme.dimens.cardWidth * 1.6f),
                modifier = Modifier.fillMaxSize().focusRequester(autoFocus).focusRestorer().focusGroup(),
                contentPadding = PaddingValues(start = VelaTheme.dimens.screenPadding, end = VelaTheme.dimens.screenPadding, top = focusBleed(), bottom = 90.dp),
                horizontalArrangement = Arrangement.spacedBy(VelaTheme.dimens.railSpacing),
                verticalArrangement = Arrangement.spacedBy(VelaTheme.dimens.railSpacing),
            ) {
                rememberedItems(memory, collections, key = { it.id.value }) { c ->
                    CollectionTile(
                        name = c.name,
                        count = c.gameCount,
                        coverArt = c.coverArt,
                        accent = c.accentColor?.let(::Color) ?: colors.accent,
                        onClick = { onOpenCollection(c.id) },
                        onLongPress = { editing = c },
                        width = null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item(key = "new") {
                    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                        VelaButton("New collection", { creating = true })
                    }
                }
            }
        }
    }

    if (creating) {
        TextInputDialog("New collection", "", "Zelda, Resident Evil, Pokémon…", "Create", onConfirm = { viewModel.create(it); creating = false }, onDismiss = { creating = false })
    }
    editing?.let { c ->
        VelaMenuDialog(
            title = c.name,
            options = listOf(
                MenuOption("rename", "Rename", icon = Icons.Rounded.Edit),
                MenuOption("delete", "Delete collection", icon = Icons.Rounded.Delete, danger = true),
            ),
            onSelect = { opt -> editing = null; if (opt.id == "rename") renaming = c else deleting = c },
            onDismiss = { editing = null },
        )
    }
    renaming?.let { c ->
        TextInputDialog("Rename collection", c.name, "Name", "Save", onConfirm = { viewModel.rename(c.id, it); renaming = null }, onDismiss = { renaming = null })
    }
    deleting?.let { c ->
        ConfirmDialog(
            title = "Delete ${c.name}?",
            message = "The games stay in your library; only the collection goes away.",
            confirmLabel = "Delete",
            onConfirm = { viewModel.delete(c.id); deleting = null },
            onDismiss = { deleting = null },
            danger = true,
        )
    }
}
