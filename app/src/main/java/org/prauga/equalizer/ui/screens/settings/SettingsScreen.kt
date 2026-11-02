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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.prauga.pvot.coreui.PvotSectionHeader
import com.prauga.pvot.designsystem.components.PvotCard
import com.prauga.pvot.designsystem.components.PvotScreen
import com.prauga.pvot.designsystem.components.PvotScreenDefaults
import com.prauga.pvot.designsystem.motion.pvotReveal
import com.prauga.pvot.designsystem.theme.PvotSpacing
import org.prauga.equalizer.BuildConfig
import org.prauga.equalizer.R
import org.prauga.equalizer.audio.EngineStatus
import org.prauga.equalizer.di.AppContainer
import org.prauga.equalizer.ui.screens.equalizer.EqualizerViewModel

@Composable
fun SettingsScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val viewModel: EqualizerViewModel = viewModel(factory = EqualizerViewModel.factory(container))
    val status by viewModel.status.collectAsStateWithLifecycle()
    val preferences = container.preferences

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
                    subtitle = engineSubtitle(status),
                    value = engineValue(status)
                )
            }
        }
        item {
            PvotSectionHeader(
                title = stringResource(R.string.settings_section_appearance),
                modifier = Modifier
                    .fillMaxWidth()
                    .pvotReveal(3)
            )
        }
        item {
            PvotCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .pvotReveal(4),
                onClick = { preferences.updateLiquidGlass(!preferences.liquidGlass) }
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
                            text = stringResource(R.string.settings_glass_title),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.settings_glass_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = PvotSpacing.XS)
                        )
                    }
                    Switch(
                        checked = preferences.liquidGlass,
                        onCheckedChange = preferences::updateLiquidGlass
                    )
                }
            }
        }
        item {
            PvotSectionHeader(
                title = stringResource(R.string.settings_section_about),
                modifier = Modifier
                    .fillMaxWidth()
                    .pvotReveal(5)
            )
        }
        item {
            PvotCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .pvotReveal(6)
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
private fun engineValue(status: EngineStatus): String = when {
    status.failure != null -> stringResource(R.string.settings_engine_value_failed)
    !status.enabled -> stringResource(R.string.settings_engine_value_off)
    status.sessions == 0 -> stringResource(R.string.settings_engine_value_waiting)
    else -> pluralStringResource(
        R.plurals.settings_engine_sessions,
        status.sessions,
        status.sessions
    )
}

@Composable
private fun engineSubtitle(status: EngineStatus): String = when {
    status.failure != null -> status.failure.orEmpty()
    !status.enabled -> stringResource(R.string.settings_engine_subtitle_off)
    else -> pluralStringResource(
        R.plurals.settings_engine_bands,
        status.bandCount,
        status.bandCount
    )
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
