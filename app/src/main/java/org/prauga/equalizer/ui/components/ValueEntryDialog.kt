package org.prauga.equalizer.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.prauga.pvot.designsystem.components.PvotDialog
import com.prauga.pvot.designsystem.components.PvotDialogAction
import com.prauga.pvot.designsystem.components.PvotDialogActionStyle
import com.prauga.pvot.designsystem.components.textfield.PvotTextField
import java.util.Locale
import org.prauga.equalizer.R

/**
 * Typing an exact gain, for when dragging to 97.3 dB is not the point.
 *
 * The commit button stays disabled until the text parses inside [range], so a half-typed number
 * cannot be applied by accident.
 */
@Composable
fun ValueEntryDialog(
    title: String,
    initial: Double,
    range: ClosedFloatingPointRange<Double>,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var text by remember { mutableStateOf(String.format(Locale.US, "%.1f", initial)) }
    val parsed = text.trim().replace(',', '.').toDoubleOrNull()
    val valid = parsed != null && parsed in range

    PvotDialog(
        visible = true,
        onDismissRequest = onDismiss,
        title = title,
        content = {
            PvotTextField(
                value = text,
                onValueChange = { text = it },
                label = stringResource(R.string.value_entry_label),
                supportingText = stringResource(
                    R.string.value_entry_range,
                    String.format(Locale.US, "%.1f", range.start),
                    String.format(Locale.US, "%.1f", range.endInclusive)
                ),
                isError = !valid,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        }
    ) {
        PvotDialogAction(
            label = stringResource(R.string.presets_action_cancel),
            onClick = onDismiss
        )
        PvotDialogAction(
            label = stringResource(R.string.value_entry_apply),
            onClick = { parsed?.let(onConfirm) },
            style = PvotDialogActionStyle.Primary,
            enabled = valid
        )
    }
}
