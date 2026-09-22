package io.vela.core.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.vela.core.model.CompletionStatus
import io.vela.core.model.GameKind
import io.vela.core.model.GameSummary
import io.vela.core.model.Platform

fun Platform.color(): Color = Color(accentColor)

/** Actions of the shared game context menu (X button / long press). */
enum class GameMenuAction { PLAY, DETAILS, FAVORITE, COLLECTIONS, LAUNCH_WITH, COMPLETION, RATE, REFRESH_METADATA, HIDE }

/**
 * The same menu on every screen: play, details, favourite, collections, launch with, status,
 * metadata refresh, hide. Callers pass the game summary and get an action back.
 */
@Composable
fun GameContextMenu(
    game: GameSummary,
    completion: CompletionStatus?,
    onAction: (GameMenuAction) -> Unit,
    onDismiss: () -> Unit,
    showDetails: Boolean = true,
) {
    val isRom = game.kind == GameKind.ROM
    val options = buildList {
        add(MenuOption(GameMenuAction.PLAY.name, "Play", icon = Icons.Rounded.PlayArrow))
        if (showDetails) add(MenuOption(GameMenuAction.DETAILS.name, "Details", icon = Icons.Rounded.Info))
        add(
            MenuOption(
                GameMenuAction.FAVORITE.name,
                if (game.favorite) "Remove from favorites" else "Add to favorites",
                icon = if (game.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            ),
        )
        add(MenuOption(GameMenuAction.COLLECTIONS.name, "Collections…", icon = Icons.Outlined.CollectionsBookmark))
        if (isRom) add(MenuOption(GameMenuAction.LAUNCH_WITH.name, "Launch with…", icon = Icons.Rounded.SportsEsports))
        add(
            MenuOption(
                GameMenuAction.COMPLETION.name,
                "Status: " + completionLabel(completion ?: CompletionStatus.NONE),
                icon = Icons.Rounded.Flag,
            ),
        )
        add(MenuOption(GameMenuAction.RATE.name, ratingLabel(game.userRating), icon = if (game.userRating != null) Icons.Rounded.Star else Icons.Rounded.StarBorder))
        if (isRom) add(MenuOption(GameMenuAction.REFRESH_METADATA.name, "Refresh metadata", icon = Icons.Rounded.Refresh))
        add(MenuOption(GameMenuAction.HIDE.name, "Hide", icon = Icons.Rounded.VisibilityOff, danger = true))
    }
    VelaMenuDialog(
        title = game.title,
        options = options,
        onSelect = { onAction(GameMenuAction.valueOf(it.id)) },
        onDismiss = onDismiss,
    )
}

fun completionLabel(status: CompletionStatus): String = when (status) {
    CompletionStatus.NONE -> "Not set"
    CompletionStatus.BACKLOG -> "Backlog"
    CompletionStatus.PLAYING -> "Playing"
    CompletionStatus.COMPLETED -> "Completed"
    CompletionStatus.ABANDONED -> "Abandoned"
}

@Composable
fun CompletionMenu(current: CompletionStatus, onSelect: (CompletionStatus) -> Unit, onDismiss: () -> Unit) {
    VelaMenuDialog(
        title = "Status",
        options = CompletionStatus.entries.map { MenuOption(it.name, completionLabel(it), selected = it == current) },
        onSelect = { onSelect(CompletionStatus.valueOf(it.id)) },
        onDismiss = onDismiss,
    )
}

/** Human play time: "2h 15m", "40m", "<1m". */
fun formatPlayTime(ms: Long): String {
    val minutes = ms / 60_000
    return when {
        minutes < 1 -> "<1m"
        minutes < 60 -> "${minutes}m"
        else -> "${minutes / 60}h ${minutes % 60}m"
    }
}

/** Relative "last played" text. */
fun formatLastPlayed(at: Long?, now: Long = System.currentTimeMillis()): String? {
    if (at == null) return null
    val diff = now - at
    val minutes = diff / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 2 -> "Just now"
        minutes < 60 -> "$minutes min ago"
        hours < 24 -> if (hours == 1L) "1 hour ago" else "$hours hours ago"
        days == 1L -> "Yesterday"
        days < 30 -> "$days days ago"
        days < 365 -> "${days / 30} months ago"
        else -> "${days / 365} years ago"
    }
}
