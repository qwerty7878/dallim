package com.dallim.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimShapes
import com.dallim.ui.theme.Spacing

/**
 * Custom card per docs/04-ui-guide.md §6 — never use Material3's default `Card`. 20dp round,
 * a very soft shadow (not Compose's default elevation, which reads "cheap" — see guide §4),
 * and no nested cards inside it.
 */
@Composable
fun DallimCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 12.dp,
                shape = DallimShapes.CardCorner,
                ambientColor = Color.Black.copy(alpha = 0.06f),
                spotColor = Color.Black.copy(alpha = 0.06f),
            )
            .clip(DallimShapes.CardCorner)
            .background(DallimColors.Surface)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(Spacing.md),
        content = content,
    )
}
