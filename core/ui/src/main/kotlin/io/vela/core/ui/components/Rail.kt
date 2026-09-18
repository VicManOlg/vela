package io.vela.core.ui.components

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import io.vela.core.ui.theme.VelaTheme

/**
 * Horizontal shelf with a heading. `focusRestorer` returns focus to the last focused child when
 * the user comes back to the row; padding gives scaled cards room so they never get clipped.
 */
@Composable
fun Rail(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    state: LazyListState = rememberLazyListState(),
    focusRequester: FocusRequester? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val dimens = VelaTheme.dimens
    val bleed = focusBleed()
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.screenPadding),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = VelaTheme.typography.headline, color = VelaTheme.colors.onBackground)
                if (subtitle != null) Text(subtitle, style = VelaTheme.typography.caption, color = VelaTheme.colors.muted)
            }
            trailing?.invoke()
        }
        Spacer(Modifier.height(10.dp))
        LazyRow(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .focusRestorer()
                .focusGroup(),
            contentPadding = PaddingValues(horizontal = dimens.screenPadding, vertical = bleed),
            horizontalArrangement = Arrangement.spacedBy(dimens.railSpacing),
            content = content,
        )
    }
}
