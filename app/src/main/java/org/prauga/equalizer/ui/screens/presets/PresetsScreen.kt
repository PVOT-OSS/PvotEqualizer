package org.prauga.equalizer.ui.screens.presets

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.prauga.pvot.coreui.PvotEmptyContent
import com.prauga.pvot.coreui.PvotSectionHeader
import com.prauga.pvot.designsystem.components.PvotBottomSheet
import com.prauga.pvot.designsystem.components.PvotCard
import com.prauga.pvot.designsystem.components.PvotDialog
import com.prauga.pvot.designsystem.components.PvotDialogAction
import com.prauga.pvot.designsystem.components.PvotDialogActionStyle
import com.prauga.pvot.designsystem.components.PvotScreen
import com.prauga.pvot.designsystem.components.PvotScreenDefaults
import com.prauga.pvot.designsystem.components.PvotSheetItem
import com.prauga.pvot.designsystem.components.textfield.PvotTextField
import com.prauga.pvot.designsystem.motion.pvotReveal
import com.prauga.pvot.designsystem.theme.PvotSpacing
import java.util.Locale
import org.prauga.equalizer.R
import org.prauga.equalizer.data.PresetJson
import org.prauga.equalizer.di.AppContainer
import org.prauga.equalizer.domain.EqProfile
import org.prauga.equalizer.domain.Preset
import org.prauga.equalizer.dsp.withTargetGain
import org.prauga.equalizer.ui.components.BUILT_IN_CURVES
import org.prauga.equalizer.ui.components.EqCurve
import org.prauga.equalizer.ui.components.EqSlider
import org.prauga.equalizer.ui.components.ValuePill
import org.prauga.equalizer.ui.screens.equalizer.EqualizerViewModel

/** What the naming dialog is currently doing. */
private data class NameTarget(val presetId: String?, val initial: String)

/**
 * Saved curves. Tapping one applies it; "Edit curve" opens it in the real editor at the band count
 * it was saved with, rather than shrinking the editor into a sheet.
 */
@Composable
fun PresetsScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val viewModel: EqualizerViewModel = viewModel(factory = EqualizerViewModel.factory(container))
    val presets by viewModel.presets.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var naming by remember { mutableStateOf<NameTarget?>(null) }
    var editing by remember { mutableStateOf<Preset?>(null) }
    var editingCurve by remember { mutableStateOf<Preset?>(null) }
    var importSheetVisible by remember { mutableStateOf(false) }
    var pasteDialogVisible by remember { mutableStateOf(false) }
    var pastedJson by remember { mutableStateOf("") }
    var importFailed by remember { mutableStateOf(false) }

    val openPreset = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        if (text == null || !viewModel.importPreset(text)) importFailed = true
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
                text = stringResource(R.string.presets_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .pvotReveal(0)
            )
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pvotReveal(1),
                horizontalArrangement = Arrangement.spacedBy(PvotSpacing.S)
            ) {
                ActionCard(
                    iconRes = R.drawable.ic_preset_add,
                    label = stringResource(R.string.presets_save),
                    onClick = { naming = NameTarget(presetId = null, initial = "") },
                    modifier = Modifier.weight(1f)
                )
                ActionCard(
                    iconRes = R.drawable.ic_download,
                    label = stringResource(R.string.presets_import),
                    onClick = { importSheetVisible = true },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            PvotSectionHeader(
                title = stringResource(R.string.presets_section_builtin),
                modifier = Modifier
                    .fillMaxWidth()
                    .pvotReveal(2)
            )
        }
        items(BUILT_IN_CURVES.size) { index ->
            val builtIn = BUILT_IN_CURVES[index]
            val name = stringResource(builtIn.nameRes)
            PresetRow(
                title = name,
                subtitle = stringResource(R.string.presets_subtitle, formatDb(builtIn.preampDb)),
                onClick = { viewModel.applyBuiltIn(name, builtIn.preampDb, builtIn.gainAt) },
                modifier = Modifier.pvotReveal(3 + index)
            )
        }
        item {
            PvotSectionHeader(
                title = stringResource(R.string.presets_section_saved),
                modifier = Modifier
                    .fillMaxWidth()
                    .pvotReveal(8)
            )
        }
        if (presets.isEmpty()) {
            item {
                PvotCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pvotReveal(9)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(PvotSpacing.L)
                    ) {
                        PvotEmptyContent(stringResource(R.string.presets_empty))
                    }
                }
            }
        }
        items(presets.size) { index ->
            val preset = presets[index]
            PresetRow(
                title = preset.name,
                subtitle = stringResource(R.string.presets_subtitle, formatDb(preset.preampDb)),
                onClick = { viewModel.applyPreset(preset) },
                trailing = {
                    IconButton(onClick = { editing = preset }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_more),
                            contentDescription = stringResource(R.string.cd_more_options),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                modifier = Modifier.pvotReveal(9 + index)
            )
        }
    }

    naming?.let { target ->
        var name by remember(target) { mutableStateOf(target.initial) }
        PvotDialog(
            visible = true,
            onDismissRequest = { naming = null },
            title = stringResource(
                if (target.presetId == null) R.string.presets_dialog_save_title
                else R.string.presets_dialog_rename_title
            ),
            content = {
                PvotTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(R.string.presets_name_label),
                    singleLine = true,
                    maxLength = 40
                )
            }
        ) {
            PvotDialogAction(
                label = stringResource(R.string.presets_action_cancel),
                onClick = { naming = null }
            )
            PvotDialogAction(
                label = stringResource(R.string.presets_action_save),
                onClick = {
                    if (target.presetId == null) {
                        viewModel.savePreset(name)
                    } else {
                        viewModel.renamePreset(target.presetId, name)
                    }
                    naming = null
                },
                style = PvotDialogActionStyle.Primary
            )
        }
    }

    if (importSheetVisible) {
        PvotBottomSheet(
            visible = importSheetVisible,
            onDismissRequest = { importSheetVisible = false },
            title = stringResource(R.string.presets_import_sheet_title)
        ) {
            PvotSheetItem(
                title = stringResource(R.string.presets_import_from_file),
                subtitle = stringResource(R.string.presets_import_from_file_subtitle),
                onClick = {
                    importSheetVisible = false
                    openPreset.launch(IMPORT_MIME_TYPES)
                }
            )
            PvotSheetItem(
                title = stringResource(R.string.presets_import_paste),
                subtitle = stringResource(R.string.presets_import_paste_subtitle),
                onClick = {
                    importSheetVisible = false
                    pastedJson = ""
                    pasteDialogVisible = true
                }
            )
        }
    }

    if (pasteDialogVisible) {
        PvotDialog(
            visible = true,
            onDismissRequest = { pasteDialogVisible = false },
            title = stringResource(R.string.presets_paste_title),
            content = {
                PvotTextField(
                    value = pastedJson,
                    onValueChange = { pastedJson = it },
                    label = stringResource(R.string.presets_paste_label),
                    placeholder = stringResource(R.string.presets_paste_hint),
                    singleLine = false,
                    minLines = 5,
                    maxLines = 8
                )
            }
        ) {
            PvotDialogAction(
                label = stringResource(R.string.presets_action_cancel),
                onClick = { pasteDialogVisible = false }
            )
            PvotDialogAction(
                label = stringResource(R.string.presets_import_action),
                onClick = {
                    pasteDialogVisible = false
                    if (!viewModel.importPreset(pastedJson)) importFailed = true
                },
                style = PvotDialogActionStyle.Primary
            )
        }
    }

    if (importFailed) {
        PvotDialog(
            visible = true,
            onDismissRequest = { importFailed = false },
            title = stringResource(R.string.presets_import_failed_title),
            message = stringResource(R.string.presets_import_failed_message)
        ) {
            PvotDialogAction(
                label = stringResource(R.string.presets_action_ok),
                onClick = { importFailed = false },
                style = PvotDialogActionStyle.Primary
            )
        }
    }

    editing?.let { preset ->
        // Captured as resources here: lint rightly refuses getString() inside a composable.
        val shareTitle = stringResource(R.string.presets_share)
        val shareSubject = stringResource(R.string.presets_share_subject, preset.name)
        PvotBottomSheet(
            visible = true,
            onDismissRequest = { editing = null },
            title = preset.name
        ) {
            PvotSheetItem(
                title = stringResource(R.string.presets_edit_curve),
                subtitle = stringResource(R.string.presets_edit_curve_subtitle),
                onClick = {
                    editingCurve = preset
                    editing = null
                }
            )
            PvotSheetItem(
                title = stringResource(R.string.presets_action_rename),
                onClick = {
                    naming = NameTarget(presetId = preset.id, initial = preset.name)
                    editing = null
                }
            )
            PvotSheetItem(
                title = stringResource(R.string.presets_share),
                onClick = {
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, shareSubject)
                        putExtra(Intent.EXTRA_TEXT, PresetJson.encodeToString(preset))
                    }
                    context.startActivity(Intent.createChooser(share, shareTitle))
                    editing = null
                }
            )
            PvotSheetItem(
                title = stringResource(R.string.presets_action_delete),
                onClick = {
                    viewModel.deletePreset(preset.id)
                    editing = null
                },
                destructive = true
            )
        }
    }

    editingCurve?.let { preset ->
        var editProfile by remember(preset.id) { mutableStateOf(preset.toProfile()) }
        var bandId by remember(preset.id) { mutableIntStateOf(editProfile.bands.first().id) }

        Dialog(
            onDismissRequest = { editingCurve = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(WindowInsets.statusBars.asPaddingValues())
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = PvotSpacing.L, vertical = PvotSpacing.M),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.presets_editor_title, preset.name),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = stringResource(R.string.presets_editor_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { editingCurve = null }) {
                            Text(stringResource(R.string.presets_action_cancel))
                        }
                        TextButton(
                            onClick = {
                                viewModel.updatePresetCurve(preset.id, editProfile)
                                editingCurve = null
                            }
                        ) {
                            Text(stringResource(R.string.presets_action_save))
                        }
                    }
                    EqCurve(
                        profile = editProfile,
                        selectedBandId = bandId,
                        onSelectBand = { bandId = it },
                        onTargetGain = { id, target ->
                            editProfile = editProfile.withTargetGain(id, target)
                        },
                        modifier = Modifier.padding(horizontal = PvotSpacing.L)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = PvotSpacing.L, vertical = PvotSpacing.M),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.eq_preamp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.weight(1f))
                        ValuePill(stringResource(R.string.eq_db_value, formatDb(editProfile.preampDb)))
                    }
                    EqSlider(
                        value = editProfile.preampDb.toFloat(),
                        onValueChange = {
                            editProfile = editProfile.copy(
                                preampDb = it.toDouble().coerceIn(
                                    EqProfile.MIN_PREAMP_DB,
                                    EqProfile.MAX_PREAMP_DB
                                )
                            )
                        },
                        valueRange = EqProfile.MIN_PREAMP_DB.toFloat()..
                            EqProfile.MAX_PREAMP_DB.toFloat(),
                        modifier = Modifier.padding(horizontal = PvotSpacing.L)
                    )
                }
            }
        }
    }
}

/** A card that reads as an action: icon and label, centred in a third of the row. */
@Composable
private fun ActionCard(
    @DrawableRes iconRes: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    PvotCard(modifier = modifier, onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PvotSpacing.M, vertical = PvotSpacing.L),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.size(PvotSpacing.S))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun PresetRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    PvotCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PvotSpacing.L),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PvotSpacing.S)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = PvotSpacing.XS)
                )
            }
            trailing?.invoke()
        }
    }
}

/**
 * One decimal with an explicit sign. Values a rounding error away from zero print as 0.0 rather
 * than -0.0, which reads as a bug on a control the user just reset.
 */
private fun formatDb(db: Double): String {
    val normalized = if (kotlin.math.abs(db) < 0.05) 0.0 else db
    return String.format(Locale.US, "%+.1f", normalized)
}

private val IMPORT_MIME_TYPES =
    arrayOf("application/json", "text/plain", "application/octet-stream")

/** A curve that ships with the app. Gain is a function of frequency so it fits either band grid. */
private data class BuiltIn(
    val nameRes: Int,
    val preampDb: Double,
    val gainAt: (Double) -> Double
)

private val BUILT_INS = listOf(
    BuiltIn(R.string.preset_flat, 0.0) { 0.0 },
    BuiltIn(R.string.preset_bass, -3.0) { frequency ->
        when {
            frequency <= 60.0 -> 6.0
            frequency < 300.0 -> 6.0 * (300.0 - frequency) / 240.0
            else -> 0.0
        }
    },
    BuiltIn(R.string.preset_vocal, -2.0) { frequency ->
        when {
            frequency < 150.0 -> -3.0
            frequency < 800.0 -> -1.0
            frequency < 3_000.0 -> 4.0
            frequency < 6_000.0 -> 2.0
            frequency < 10_000.0 -> 0.0
            else -> -2.0
        }
    },
    BuiltIn(R.string.preset_treble, -2.0) { frequency ->
        when {
            frequency < 2_000.0 -> 0.0
            frequency < 8_000.0 -> 6.0 * (frequency - 2_000.0) / 6_000.0
            else -> 6.0
        }
    }
)
