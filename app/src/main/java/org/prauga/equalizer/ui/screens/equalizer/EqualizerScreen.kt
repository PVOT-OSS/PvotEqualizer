package org.prauga.equalizer.ui.screens.equalizer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.prauga.pvot.designsystem.components.PvotBottomSheet
import com.prauga.pvot.designsystem.components.PvotCard
import com.prauga.pvot.designsystem.components.PvotScreen
import com.prauga.pvot.designsystem.components.PvotScreenDefaults
import com.prauga.pvot.designsystem.components.PvotSheetItem
import com.prauga.pvot.designsystem.motion.pvotReveal
import com.prauga.pvot.designsystem.theme.PvotSpacing
import java.util.Locale
import kotlin.math.exp
import kotlin.math.ln
import kotlinx.coroutines.delay
import org.prauga.equalizer.R
import org.prauga.equalizer.audio.EngineStatus
import org.prauga.equalizer.di.AppContainer
import org.prauga.equalizer.domain.EqMode
import org.prauga.equalizer.domain.EqProfile
import org.prauga.equalizer.dsp.ProfileResponse
import org.prauga.equalizer.ui.components.BUILT_IN_CURVES
import org.prauga.equalizer.ui.components.EqCurve
import org.prauga.equalizer.ui.components.EqSlider
import org.prauga.equalizer.ui.components.UndoBar
import org.prauga.equalizer.ui.components.ValueEntryDialog
import org.prauga.equalizer.ui.components.ValuePill

/** How much a tap on the stepper buttons moves the selected band. */
private const val STEP_DB = 0.1

/**
 * The equalizer screen: a header with the power switch, the curve, then the controls for the whole
 * curve and for the band currently selected on it.
 *
 * Bands sit at fixed frequencies, so the only thing to adjust is gain — dragged on the graph, nudged
 * with the slider, or stepped a tenth of a decibel at a time.
 */
@Composable
fun EqualizerScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val viewModel: EqualizerViewModel = viewModel(factory = EqualizerViewModel.factory(container))
    val status by viewModel.status.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val selectedBandId by viewModel.selectedBandId.collectAsStateWithLifecycle()
    val presets by viewModel.presets.collectAsStateWithLifecycle()
    val activePresetName by viewModel.activePresetName.collectAsStateWithLifecycle()
    var presetPickerVisible by remember { mutableStateOf(false) }

    val preferences = container.preferences
    val mode = if (preferences.advancedMode) EqMode.ADVANCED else EqMode.SIMPLE
    val selectedBand = profile.bands.firstOrNull { it.id == selectedBandId } ?: profile.bands.first()
    // What is heard at the selected band's frequency, which is where its handle sits.
    val heardDb = remember(profile, selectedBand.frequencyHz) {
        ProfileResponse(profile, 48_000.0).dbAt(selectedBand.frequencyHz)
    }

    val undoAction by viewModel.undoAction.collectAsStateWithLifecycle()
    LaunchedEffect(undoAction) {
        if (undoAction != null) {
            delay(6_000)
            viewModel.dismissUndo()
        }
    }

    // A tall phone gets a taller plot; a short one keeps the controls above the fold.
    val curveHeight = with(LocalConfiguration.current) {
        (screenHeightDp * 0.36f).dp.coerceIn(230.dp, 380.dp)
    }

    // The curve already includes the preamp, so its peak above 0 dB is what would clip.
    val peakDb = remember(profile) {
        val response = ProfileResponse(profile, 48_000.0)
        var peak = -60.0
        for (index in 0..199) {
            val frequency = 20.0 * exp(ln(1_000.0) * index / 199.0)
            peak = maxOf(peak, response.dbAt(frequency))
        }
        peak
    }
    val clippingDb = peakDb.coerceAtLeast(0.0)

    var valueTarget by remember { mutableStateOf<ValueTarget?>(null) }

    // Changing mode swaps the band set; each mode keeps its own saved curve.
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

    Box(modifier = modifier.fillMaxSize()) {
    PvotScreen(
        modifier = Modifier.fillMaxSize(),
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
                    .pvotReveal(1),
                shape = CardShape
            ) {
                EqCurve(
                    profile = profile,
                    selectedBandId = selectedBand.id,
                    onSelectBand = viewModel::selectBand,
                    onTargetGain = viewModel::setTargetGain,
                    onResetBand = viewModel::resetBand,
                    modifier = Modifier.padding(
                        horizontal = PvotSpacing.XS,
                        vertical = PvotSpacing.M
                    ),
                    height = curveHeight
                )
                if (clippingDb > 0.05) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = PvotSpacing.L)
                            .padding(bottom = PvotSpacing.M),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(
                                R.string.eq_clipping,
                                formatDb(clippingDb)
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            onClick = {
                                viewModel.setPreamp(profile.preampDb - clippingDb)
                            }
                        ) {
                            Text(
                                text = stringResource(R.string.eq_auto_preamp),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        item {
            PresetDropdown(
                name = activePresetName,
                onClick = { presetPickerVisible = true },
                modifier = Modifier.pvotReveal(2)
            )
        }

        item {
            PvotCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .pvotReveal(3),
                shape = CardShape,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PvotSpacing.L, vertical = PvotSpacing.M)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.eq_preamp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.weight(1f))
                        ValuePill(
                            text = stringResource(
                                R.string.eq_db_value,
                                formatDb(profile.preampDb)
                            ),
                            onClick = { valueTarget = ValueTarget(null) }
                        )
                        TextButton(
                            onClick = { viewModel.setPreamp(0.0) },
                            modifier = Modifier.padding(start = PvotSpacing.XS)
                        ) {
                            Text(
                                text = stringResource(R.string.eq_reset),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    EqSlider(
                        value = profile.preampDb.toFloat(),
                        onValueChange = { viewModel.setPreamp(it.toDouble()) },
                        valueRange = EqProfile.MIN_PREAMP_DB.toFloat()..
                            EqProfile.MAX_PREAMP_DB.toFloat()
                    )
                }
            }
        }

        // The selected band's own controls: precise adjustment, and the path a screen reader uses,
        // since a canvas cannot expose each handle as a separate control.
        item {
            PvotCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .pvotReveal(4),
                shape = CardShape,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)
                ),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PvotSpacing.L, vertical = PvotSpacing.M)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        )
                        Text(
                            text = formatFrequency(selectedBand.frequencyHz),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .padding(start = PvotSpacing.S + 2.dp)
                                .weight(1f)
                        )
                        ValuePill(
                            text = stringResource(R.string.eq_db_value, formatDb(heardDb)),
                            onClick = { valueTarget = ValueTarget(selectedBand.id) }
                        )
                    }
                    EqSlider(
                        value = heardDb.toFloat(),
                        onValueChange = {
                            viewModel.setTargetGain(selectedBand.id, it.toDouble())
                        },
                        valueRange = EqProfile.MIN_GAIN_DB.toFloat()..
                            EqProfile.MAX_GAIN_DB.toFloat()
                    )
                }
            }
        }
    }

        if (undoAction != null) {
            UndoBar(
                message = stringResource(undoMessage(undoAction ?: return@Box)),
                onUndo = viewModel::undo,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = PvotSpacing.L)
                    .padding(bottom = 108.dp)
                    .fillMaxWidth()
            )
        }
    }

    valueTarget?.let { target ->
        val isPreamp = target.bandId == null
        ValueEntryDialog(
            title = if (isPreamp) {
                stringResource(R.string.value_entry_preamp)
            } else {
                stringResource(R.string.value_entry_band, formatFrequency(selectedBand.frequencyHz))
            },
            initial = if (isPreamp) profile.preampDb else heardDb,
            range = if (isPreamp) {
                EqProfile.MIN_PREAMP_DB..EqProfile.MAX_PREAMP_DB
            } else {
                EqProfile.MIN_GAIN_DB..EqProfile.MAX_GAIN_DB
            },
            onDismiss = { valueTarget = null },
            onConfirm = { value ->
                if (isPreamp) {
                    viewModel.setPreamp(value)
                } else {
                    target.bandId?.let { viewModel.setTargetGain(it, value) }
                }
                valueTarget = null
            }
        )
    }

    if (presetPickerVisible) {
        PvotBottomSheet(
            visible = presetPickerVisible,
            onDismissRequest = { presetPickerVisible = false },
            title = stringResource(R.string.eq_preset)
        ) {
            for (curve in BUILT_IN_CURVES) {
                val name = stringResource(curve.nameRes)
                PvotSheetItem(
                    title = name,
                    subtitle = stringResource(R.string.presets_section_builtin),
                    onClick = {
                        viewModel.applyBuiltIn(name, curve.preampDb, curve.gainAt)
                        presetPickerVisible = false
                    }
                )
            }
            for (preset in presets) {
                PvotSheetItem(
                    title = preset.name,
                    subtitle = stringResource(
                        R.string.presets_subtitle,
                        String.format(Locale.US, "%+.1f", preset.preampDb)
                    ),
                    onClick = {
                        viewModel.applyPreset(preset)
                        presetPickerVisible = false
                    }
                )
            }
        }
    }
}

private fun formatDb(db: Double): String {
    val normalized = if (kotlin.math.abs(db) < 0.05) 0.0 else db
    return String.format(Locale.US, "%+.1f", normalized)
}

/** Whole decibels, for axis labels: -12, 0, +12. */
private val CardShape = RoundedCornerShape(12.dp)

/** Either the preamp (no band) or one band's gain, for the exact-entry dialog. */
private data class ValueTarget(val bandId: Int?)

private fun undoMessage(action: EqualizerViewModel.UndoAction): Int = when (action) {
    EqualizerViewModel.UndoAction.PRESET_DELETED -> R.string.undo_preset_deleted
    EqualizerViewModel.UndoAction.CURVE_RESET -> R.string.undo_curve_reset
    EqualizerViewModel.UndoAction.PRESET_APPLIED -> R.string.undo_preset_applied
    EqualizerViewModel.UndoAction.BAND_RESET -> R.string.undo_band_reset
}

@Composable
private fun PresetDropdown(name: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        color = colors.onSurface.copy(alpha = 0.06f),
        contentColor = colors.onSurface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PvotSpacing.L, vertical = PvotSpacing.L),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_presets),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = colors.onSurfaceVariant
            )
            Text(
                text = name ?: stringResource(R.string.eq_preset_custom),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface,
                modifier = Modifier
                    .padding(start = PvotSpacing.M)
                    .weight(1f)
            )
            Icon(
                painter = painterResource(R.drawable.ic_chevron_down),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = colors.onSurfaceVariant
            )
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
