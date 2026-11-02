package org.prauga.equalizer.ui.screens.equalizer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.prauga.pvot.designsystem.components.PvotCard
import com.prauga.pvot.designsystem.components.PvotScreen
import com.prauga.pvot.designsystem.components.PvotScreenDefaults
import com.prauga.pvot.designsystem.components.slider.PvotSlider
import com.prauga.pvot.designsystem.motion.pvotReveal
import com.prauga.pvot.designsystem.theme.PvotSpacing
import java.util.Locale
import kotlinx.coroutines.delay
import org.prauga.equalizer.R
import org.prauga.equalizer.audio.EngineStatus
import org.prauga.equalizer.di.AppContainer
import org.prauga.equalizer.domain.EqMode
import org.prauga.equalizer.domain.EqProfile
import org.prauga.equalizer.dsp.ProfileResponse
import org.prauga.equalizer.ui.components.EqCurve

/**
 * The equalizer screen: a header with the power switch, and one control that is both the curve and
 * the faders.
 *
 * Bands sit at fixed frequencies, so the only thing to adjust is gain — dragged on the graph, or
 * nudged with the slider below it when a finger needs help.
 */
@Composable
fun EqualizerScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val viewModel: EqualizerViewModel = viewModel(factory = EqualizerViewModel.factory(container))
    val status by viewModel.status.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val selectedBandId by viewModel.selectedBandId.collectAsStateWithLifecycle()

    val preferences = container.preferences
    val mode = if (preferences.advancedMode) EqMode.ADVANCED else EqMode.SIMPLE
    val selectedBand = profile.bands.firstOrNull { it.id == selectedBandId } ?: profile.bands.first()
    // What is heard at the selected band's frequency, which is where its handle sits.
    val heardDb = remember(profile, selectedBand.frequencyHz) {
        ProfileResponse(profile, 48_000.0).dbAt(selectedBand.frequencyHz)
    }

    // Changing mode swaps the band set; the gains of shared frequencies are carried over.
    LaunchedEffect(mode) {
        viewModel.syncBands(mode)
    }

    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* The service runs either way; without the grant its notification is just hidden. */ }

    // Something audible may be playing while no app has announced a session. Say that, rather than
    // claiming there is "no audio session yet" while music is plainly coming out of the speaker.
    var musicPlaying by remember { mutableStateOf(false) }
    LaunchedEffect(status.enabled, status.sessions) {
        if (!status.enabled || status.sessions > 0) {
            musicPlaying = false
            return@LaunchedEffect
        }
        val audio = context.getSystemService(AudioManager::class.java)
        while (true) {
            musicPlaying = audio?.isMusicActive() == true
            delay(2_000)
        }
    }

    // Ask only once the switch has actually changed. Prompting from inside the toggle gesture can
    // have the dialog steal the tap, and the release land back on the switch as a second click.
    LaunchedEffect(status.enabled) {
        if (status.enabled && needsNotificationPermission(context)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    PvotScreen(
        modifier = modifier,
        contentPadding = PvotScreenDefaults.contentPadding(
            top = PvotScreenDefaults.TopPadding +
                WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        )
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pvotReveal(0),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PvotSpacing.L)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.eq_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = statusText(status, musicPlaying),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = PvotSpacing.XS)
                    )
                }
                Switch(checked = status.enabled, onCheckedChange = viewModel::setEnabled)
            }
        }

        item {
            PvotCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .pvotReveal(1)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PvotSpacing.XS, vertical = PvotSpacing.M)
                ) {
                    EqCurve(
                        profile = profile,
                        selectedBandId = selectedBand.id,
                        onSelectBand = viewModel::selectBand,
                        onGainChange = viewModel::setTargetGain
                    )
                    // The graph runs almost to the card's edge for width; the preamp controls below
                    // it are not part of the graph, so they get the card's normal inset back.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = PvotSpacing.M)
                    ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = PvotSpacing.S),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.eq_preamp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = stringResource(
                                R.string.eq_db_value,
                                String.format(Locale.US, "%+.1f", profile.preampDb)
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        TextButton(
                            onClick = viewModel::resetToFlat,
                            modifier = Modifier.padding(start = PvotSpacing.M)
                        ) {
                            Text(stringResource(R.string.eq_reset))
                        }
                    }
                    PvotSlider(
                        value = profile.preampDb.toFloat(),
                        onValueChange = { viewModel.setPreamp(it.toDouble()) },
                        valueRange = EqProfile.MIN_PREAMP_DB.toFloat()..
                            EqProfile.MAX_PREAMP_DB.toFloat()
                    )
                    }
                }
            }
        }

        // The selected band's own slider: precise adjustment, and the path a screen reader uses,
        // since a canvas cannot expose each handle as a separate control.
        item {
            PvotCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .pvotReveal(2)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(PvotSpacing.L)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatFrequency(selectedBand.frequencyHz),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(
                                R.string.eq_db_value,
                                String.format(Locale.US, "%+.1f", heardDb)
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    PvotSlider(
                        value = heardDb.toFloat(),
                        onValueChange = { viewModel.setTargetGain(selectedBand.id, it.toDouble()) },
                        valueRange = EqProfile.MIN_GAIN_DB.toFloat()..
                            EqProfile.MAX_GAIN_DB.toFloat()
                    )
                }
            }
        }
    }
}

private fun formatFrequency(hz: Double): String {
    val kilo = hz >= 1_000.0
    val value = if (kilo) hz / 1_000.0 else hz
    val number = if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        String.format(Locale.US, "%.1f", value)
    }
    return if (kilo) "$number kHz" else "$number Hz"
}

@Composable
private fun statusText(status: EngineStatus, musicPlaying: Boolean): String = when {
    status.failure != null -> stringResource(R.string.eq_power_failed, status.failure.orEmpty())
    !status.enabled -> stringResource(R.string.eq_power_off)
    status.attachedPackages.isNotEmpty() ->
        stringResource(R.string.eq_power_connected, status.attachedPackages.joinToString())
    musicPlaying -> stringResource(R.string.eq_power_unshared)
    else -> stringResource(R.string.eq_power_waiting)
}

private fun needsNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
