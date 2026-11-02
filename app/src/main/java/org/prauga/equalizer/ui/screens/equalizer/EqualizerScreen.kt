package org.prauga.equalizer.ui.screens.equalizer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.prauga.pvot.coreui.PvotEmptyContent
import com.prauga.pvot.coreui.PvotSectionHeader
import com.prauga.pvot.designsystem.components.PvotCard
import com.prauga.pvot.designsystem.components.PvotScreen
import com.prauga.pvot.designsystem.components.PvotScreenDefaults
import com.prauga.pvot.designsystem.motion.pvotReveal
import com.prauga.pvot.designsystem.theme.PvotSpacing
import org.prauga.equalizer.R

/**
 * The EQ screen: the curve, its bands and the power switch.
 *
 * For now it only draws the frame the engine will fill in: the switch is inert because nothing
 * is attached to an audio session yet.
 */
@Composable
fun EqualizerScreen(modifier: Modifier = Modifier) {
    PvotScreen(
        modifier = modifier,
        contentPadding = PvotScreenDefaults.contentPadding(
            top = PvotScreenDefaults.TopPadding +
                WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        )
    ) {
        item {
            Text(
                text = stringResource(R.string.eq_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .pvotReveal(0)
            )
        }
        item {
            PvotCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .pvotReveal(1)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(PvotSpacing.L),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PvotSpacing.L)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.eq_power_title),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.eq_power_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = PvotSpacing.XS)
                        )
                    }
                    Switch(
                        checked = false,
                        onCheckedChange = null,
                        enabled = false
                    )
                }
            }
        }
        item {
            PvotSectionHeader(
                title = stringResource(R.string.eq_bands_section),
                modifier = Modifier
                    .fillMaxWidth()
                    .pvotReveal(2)
            )
        }
        item {
            PvotCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .pvotReveal(3)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(PvotSpacing.L)
                ) {
                    Text(
                        text = stringResource(R.string.eq_bands_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    PvotEmptyContent(
                        message = stringResource(R.string.eq_bands_empty_message),
                        modifier = Modifier.padding(top = PvotSpacing.XS)
                    )
                }
            }
        }
    }
}
