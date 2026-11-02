package org.prauga.equalizer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prauga.pvot.designsystem.theme.PvotShapes
import com.prauga.pvot.designsystem.theme.PvotSpacing

/**
 * A number in a small tinted box.
 *
 * Values are what the eye looks for on this screen, so they get their own surface and the accent
 * colour instead of trailing a label as plain text.
 */
@Composable
fun ValuePill(text: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .background(colors.primary.copy(alpha = 0.14f), PvotShapes.Small)
            .padding(horizontal = PvotSpacing.M, vertical = PvotSpacing.XS + 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = colors.primary,
            maxLines = 1
        )
    }
}
