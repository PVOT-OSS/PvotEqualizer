package org.prauga.equalizer.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.prauga.pvot.coreui.PvotSectionHeader
import com.prauga.pvot.designsystem.components.PvotCard
import com.prauga.pvot.designsystem.components.PvotScreen
import com.prauga.pvot.designsystem.components.PvotScreenDefaults
import com.prauga.pvot.designsystem.motion.pvotReveal
import com.prauga.pvot.designsystem.theme.PvotSpacing
import org.prauga.equalizer.BuildConfig
import org.prauga.equalizer.R

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    PvotScreen(
        modifier = modifier,
        contentPadding = PvotScreenDefaults.contentPadding(
            top = PvotScreenDefaults.TopPadding +
                WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        )
    ) {
        item {
            Text(
                text = stringResource(R.string.tab_settings),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .pvotReveal(0)
            )
        }
        item {
            PvotSectionHeader(
                title = stringResource(R.string.settings_section_engine),
                modifier = Modifier
                    .fillMaxWidth()
                    .pvotReveal(1)
            )
        }
        item {
            PvotCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .pvotReveal(2)
            ) {
                SettingsRow(
                    title = stringResource(R.string.settings_engine_title),
                    subtitle = stringResource(R.string.settings_engine_subtitle),
                    value = stringResource(R.string.settings_engine_value)
                )
            }
        }
        item {
            PvotSectionHeader(
                title = stringResource(R.string.settings_section_about),
                modifier = Modifier
                    .fillMaxWidth()
                    .pvotReveal(3)
            )
        }
        item {
            PvotCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .pvotReveal(4)
            ) {
                SettingsRow(
                    title = stringResource(R.string.settings_version_title),
                    value = BuildConfig.VERSION_NAME
                )
            }
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(PvotSpacing.L),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PvotSpacing.L)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = PvotSpacing.XS)
                )
            }
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
