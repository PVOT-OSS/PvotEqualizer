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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import com.prauga.pvot.coreui.PvotEmptyContent
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
import org.prauga.equalizer.ui.components.FitPreview

/**
 * The EQ screen: the power switch and, for now, a debug view of the fit.
 *
 * The curve editor replaces the bands card in the next phase.
 */
@Composable
fun EqualizerScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val viewModel: EqualizerViewModel = viewModel(factory = EqualizerViewModel.factory(container))
    val status by viewModel.status.collectAsStateWithLifecycle()

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
                            text = statusText(status, musicPlaying),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = PvotSpacing.XS)
                        )
                    }
                    Switch(
                        checked = status.enabled,
                        onCheckedChange = viewModel::setEnabled
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
        // Temporary: lets the fit be judged by ear and by eye before the curve editor exists.
        if (BuildConfig.DEBUG) {
            item {
                PvotCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pvotReveal(4)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(PvotSpacing.L)
                    ) {
                        Text(
                            text = stringResource(R.string.preview_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        FitPreview(
                            onSendProfile = viewModel::setProfile,
                            modifier = Modifier.padding(top = PvotSpacing.S)
                        )
                    }
                }
            }
        }
    }
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
