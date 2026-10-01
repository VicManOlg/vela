package io.vela.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// Lazy-list and lazy-grid `items` that tag every item for a FocusMemory, so a list gets its
// focused item back after it left composition (a Shell tab switch, a screen on top). Same
// signatures as Compose's, with the memory first.

fun <T> LazyListScope.rememberedItems(
    memory: FocusMemory,
    items: List<T>,
    key: (T) -> Any,
    itemContent: @Composable LazyItemScope.(T) -> Unit,
) = items(items, key) { item ->
    val scope = this
    Box(Modifier.rememberedFocus(memory, key(item))) { scope.itemContent(item) }
}

fun <T> LazyListScope.rememberedItemsIndexed(
    memory: FocusMemory,
    items: List<T>,
    key: (Int, T) -> Any,
    itemContent: @Composable LazyItemScope.(Int, T) -> Unit,
) = itemsIndexed(items, key) { index, item ->
    val scope = this
    Box(Modifier.rememberedFocus(memory, key(index, item))) { scope.itemContent(index, item) }
}

/** For paged lists: [key] is the PagingItems `itemKey`. */
fun LazyListScope.rememberedItems(
    memory: FocusMemory,
    count: Int,
    key: (Int) -> Any,
    itemContent: @Composable LazyItemScope.(Int) -> Unit,
) = items(count, key) { index ->
    val scope = this
    Box(Modifier.rememberedFocus(memory, key(index))) { scope.itemContent(index) }
}

fun <T> LazyGridScope.rememberedItems(
    memory: FocusMemory,
    items: List<T>,
    key: (T) -> Any,
    itemContent: @Composable LazyGridItemScope.(T) -> Unit,
) = items(items, key) { item ->
    val scope = this
    Box(Modifier.rememberedFocus(memory, key(item))) { scope.itemContent(item) }
}

fun <T> LazyGridScope.rememberedItemsIndexed(
    memory: FocusMemory,
    items: List<T>,
    key: (Int, T) -> Any,
    itemContent: @Composable LazyGridItemScope.(Int, T) -> Unit,
) = itemsIndexed(items, key) { index, item ->
    val scope = this
    Box(Modifier.rememberedFocus(memory, key(index, item))) { scope.itemContent(index, item) }
}

/** For paged grids: [key] is the PagingItems `itemKey`. */
fun LazyGridScope.rememberedItems(
    memory: FocusMemory,
    count: Int,
    key: (Int) -> Any,
    itemContent: @Composable LazyGridItemScope.(Int) -> Unit,
) = items(count, key) { index ->
    val scope = this
    Box(Modifier.rememberedFocus(memory, key(index))) { scope.itemContent(index) }
}
