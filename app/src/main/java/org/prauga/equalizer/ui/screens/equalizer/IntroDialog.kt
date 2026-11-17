package org.prauga.equalizer.ui.screens.equalizer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.prauga.pvot.designsystem.components.PvotDialog
import com.prauga.pvot.designsystem.components.PvotDialogAction
import com.prauga.pvot.designsystem.components.PvotDialogActionStyle
import com.prauga.pvot.designsystem.theme.PvotSpacing
import org.prauga.equalizer.R

/** Shown once: what the equalizer reaches, how to turn it on, and where presets live. */
@Composable
fun IntroDialog(onDismiss: () -> Unit) {
    PvotDialog(
        visible = true,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.intro_title),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(PvotSpacing.M)) {
                listOf(R.string.intro_sessions, R.string.intro_power, R.string.intro_presets)
                    .forEach { text ->
                        Text(
                            text = stringResource(text),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
            }
        }
    ) {
        PvotDialogAction(
            label = stringResource(R.string.intro_ok),
            onClick = onDismiss,
            style = PvotDialogActionStyle.Primary
        )
    }
}
