package org.prauga.equalizer.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.prauga.pvot.designsystem.theme.PvotSpacing
import org.prauga.equalizer.R

/**
 * Offers to take back the change that just happened.
 *
 * Destructive actions in this app are one tap and immediate, so a change the user did not mean has
 * to be recoverable without a confirmation dialog in front of every harmless action.
 */
@Composable
fun UndoBar(
    message: String,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = colors.surfaceVariant,
        contentColor = colors.onSurface
    ) {
        Row(
            modifier = Modifier.padding(start = PvotSpacing.L, end = PvotSpacing.S),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onUndo) {
                Text(
                    text = stringResource(R.string.action_undo),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.primary
                )
            }
        }
    }
}
