package io.vela.core.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import io.vela.core.ui.theme.VelaTheme
import io.vela.core.ui.theme.liveAccent

/** Highest star a game can get. Stored as 1..[MAX_RATING], null when the user has not rated it. */
const val MAX_RATING = 5

/** "★★★★" for the rating, null when there is none; used inside fact lines. */
fun ratingStars(rating: Int?): String? = rating?.takeIf { it > 0 }?.let { "★".repeat(it.coerceAtMost(MAX_RATING)) }

/** Label for menus: "Your rating: ★★★★" or "Rate this game". */
fun ratingLabel(rating: Int?): String = ratingStars(rating)?.let { "Your rating: $it" } ?: "Rate this game"

/**
 * Five stars the user can walk with the D-pad or tap. Moving focus previews the value, confirm
 * sets it, confirming the current value clears the rating. Read-only when [onRate] is null.
 */
@Composable
fun RatingStars(
    rating: Int?,
    onRate: ((Int?) -> Unit)?,
    modifier: Modifier = Modifier,
    label: String? = "Your rating",
) {
    val colors = VelaTheme.colors
    var preview by remember { mutableIntStateOf(0) }
    val shown = if (preview > 0) preview else (rating ?: 0)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (label != null) {
            Text(label, style = VelaTheme.typography.label, color = colors.muted)
            Spacer(Modifier.width(12.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            for (i in 1..MAX_RATING) {
                val filled = i <= shown
                val interaction = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .size(34.dp)
                        .onFocusChanged { if (it.isFocused) preview = i else if (preview == i) preview = 0 }
                        .then(
                            if (onRate != null) {
                                Modifier.velaFocusable(CircleShape, interaction, onClick = { onRate(if (rating == i) null else i) }, scaleOverride = 1.15f)
                            } else {
                                Modifier
                            },
                        )
                        .padding(4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (filled) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = "$i of $MAX_RATING",
                        tint = if (filled) VelaTheme.liveAccent else colors.muted.copy(alpha = 0.7f),
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
        }
        if (onRate != null) {
            Spacer(Modifier.width(10.dp))
            Text(
                when {
                    preview > 0 && preview == rating -> "Confirm to clear"
                    preview > 0 -> "$preview / $MAX_RATING"
                    rating != null -> "$rating / $MAX_RATING"
                    else -> "Not rated"
                },
                style = VelaTheme.typography.caption,
                color = colors.muted,
            )
        }
    }
}

/** Picker used from the context menu: five rows of stars plus "No rating". */
@Composable
fun RatingMenu(current: Int?, onSelect: (Int?) -> Unit, onDismiss: () -> Unit) {
    VelaMenuDialog(
        title = "Rate this game",
        options = (MAX_RATING downTo 1).map { n ->
            MenuOption(n.toString(), "★".repeat(n) + "☆".repeat(MAX_RATING - n), description = "$n / $MAX_RATING", selected = n == current)
        } + MenuOption("0", "No rating"),
        onSelect = { onSelect(it.id.toInt().takeIf { n -> n > 0 }) },
        onDismiss = onDismiss,
    )
}
