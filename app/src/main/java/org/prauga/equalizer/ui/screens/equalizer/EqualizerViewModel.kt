package org.prauga.equalizer.ui.screens.equalizer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.prauga.equalizer.audio.EngineStatus
import org.prauga.equalizer.data.PresetJson
import org.prauga.equalizer.di.AppContainer
import org.prauga.equalizer.domain.EqBands
import org.prauga.equalizer.domain.EqMode
import org.prauga.equalizer.domain.EqProfile
import org.prauga.equalizer.domain.Preset
import org.prauga.equalizer.dsp.DpBandFit
import org.prauga.equalizer.dsp.withTargetGain

/**
 * The editor's state: the curve, the selected band, the engine's status and the saved presets.
 *
 * The curve is restored from disk on construction and written back shortly after each edit, so the
 * app no longer forgets the equalizer on process death. Both screens share one instance, which is
 * why applying a preset on the presets tab immediately updates the curve on the other.
 */
class EqualizerViewModel(private val container: AppContainer) : ViewModel() {

    private val store = container.stateStore

    val status: StateFlow<EngineStatus> = container.engine.status

    private var mode: EqMode = container.preferences.mode

    private val _profile = MutableStateFlow(store.loadProfile(mode))
    val profile: StateFlow<EqProfile> = _profile.asStateFlow()

    private val _selectedBandId = MutableStateFlow(_profile.value.bands.first().id)
    val selectedBandId: StateFlow<Int?> = _selectedBandId.asStateFlow()

    private val _presets = MutableStateFlow(store.loadPresets())
    val presets: StateFlow<List<Preset>> = _presets.asStateFlow()

    /** The name of the preset the curve came from, or null once it has been edited by hand. */
    private val _activePresetName = MutableStateFlow<String?>(null)
    val activePresetName: StateFlow<String?> = _activePresetName.asStateFlow()

    private var saveJob: Job? = null

    init {
        // Hand the restored curve to the engine straight away, so a restored power state applies
        // it without waiting for an edit.
        container.equalizer.setProfile(_profile.value)
        viewModelScope.launch(Dispatchers.Default) {
            DpBandFit.fit(_profile.value, SAMPLE_RATE_HZ)
        }
    }

    fun setEnabled(enabled: Boolean) {
        store.savePowerOn(enabled)
        container.equalizer.setEnabled(enabled)
    }

    fun selectBand(id: Int) {
        _selectedBandId.value = id
    }

    /** Sets the heard gain at a band's frequency, which is where its handle sits. */
    fun setTargetGain(id: Int, targetDb: Double) {
        _activePresetName.value = null
        commit(_profile.value.withTargetGain(id, targetDb, SAMPLE_RATE_HZ))
    }

    fun setGain(id: Int, gainDb: Double) {
        _activePresetName.value = null
        update { current ->
            current.copy(
                bands = current.bands.map { band ->
                    if (band.id == id) {
                        band.copy(
                            gainDb = gainDb.coerceIn(EqProfile.MIN_GAIN_DB, EqProfile.MAX_GAIN_DB)
                        )
                    } else {
                        band
                    }
                }
            )
        }
    }

    fun setPreamp(preampDb: Double) {
        _activePresetName.value = null
        update {
            it.copy(
                preampDb = preampDb.coerceIn(EqProfile.MIN_PREAMP_DB, EqProfile.MAX_PREAMP_DB)
            )
        }
    }

    /** Applies a built-in curve and remembers which one it was. */
    fun applyBuiltIn(name: String, preampDb: Double, gainAt: (Double) -> Double) {
        applyCurve(preampDb, gainAt)
        _activePresetName.value = name
    }

    /** Applies a whole curve in one step. */
    fun applyCurve(preampDb: Double, gainAt: (Double) -> Double) {
        update { current ->
            current.copy(
                preampDb = preampDb.coerceIn(EqProfile.MIN_PREAMP_DB, EqProfile.MAX_PREAMP_DB),
                bands = current.bands.map { band ->
                    band.copy(
                        gainDb = gainAt(band.frequencyHz)
                            .coerceIn(EqProfile.MIN_GAIN_DB, EqProfile.MAX_GAIN_DB)
                    )
                }
            )
        }
    }

    /**
     * Switches the band grid. Each mode keeps its own saved curve, so switching back restores what
     * was there rather than starting from flat.
     */
    fun syncBands(mode: EqMode) {
        if (mode == this.mode) return
        this.mode = mode
        val restored = store.loadProfile(mode)
        if (restored.bands.none { it.id == _selectedBandId.value }) {
            _selectedBandId.value = restored.bands.first().id
        }
        commit(restored)
    }

    fun resetToFlat() {
        _activePresetName.value = null
        val flat = EqBands.profile(mode)
        _selectedBandId.value = flat.bands.first().id
        commit(flat)
    }

    fun applyPreset(preset: Preset) {
        switchTo(preset.mode)
        applyPresetToGrid(preset)
        _activePresetName.value = preset.name
    }

    /** Replaces a preset's curve with an edited copy, leaving the live equalizer alone. */
    fun updatePresetCurve(id: String, profile: EqProfile) {
        val existing = _presets.value.firstOrNull { it.id == id } ?: return
        persist(
            _presets.value.map { preset ->
                if (preset.id != id) {
                    preset
                } else {
                    existing.copy(
                        preampDb = profile.preampDb,
                        gains = profile.bands.filter { it.gainDb != 0.0 }
                            .associate { it.frequencyHz to it.gainDb }
                    )
                }
            }
        )
    }

    /** Imports a preset from shared text. Returns false when the text is not a preset. */
    fun importPreset(text: String): Boolean {
        val preset = PresetJson.decodeFromString(text) ?: return false
        persist(_presets.value + preset)
        return true
    }

    private fun switchTo(mode: EqMode) {
        if (mode == this.mode) return
        this.mode = mode
        container.preferences.updateAdvancedMode(mode == EqMode.ADVANCED)
    }

    private fun applyPresetToGrid(preset: Preset) {
        val base = preset.toProfile()
        this.mode = preset.mode
        if (base.bands.none { it.id == _selectedBandId.value }) {
            _selectedBandId.value = base.bands.first().id
        }
        commit(base)
    }

    /** Saves the current curve as a new preset. Blank names are ignored. */
    fun savePreset(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        persist(_presets.value + Preset.from(trimmed, _profile.value, mode))
    }

    fun renamePreset(id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        persist(_presets.value.map { if (it.id == id) it.copy(name = trimmed) else it })
    }

    fun deletePreset(id: String) {
        persist(_presets.value.filterNot { it.id == id })
    }

    /** Saves a built-in curve as an editable preset, so it can be tweaked and kept. */
    fun saveBuiltInAsPreset(name: String, preampDb: Double, gainAt: (Double) -> Double) {
        val gains = _profile.value.bands.map { it.frequencyHz to gainAt(it.frequencyHz) }
        persist(
            _presets.value + Preset(
                id = UUID.randomUUID().toString(),
                name = name,
                mode = mode,
                preampDb = preampDb,
                gains = gains.filter { it.second != 0.0 }.toMap()
            )
        )
    }

    private fun persist(presets: List<Preset>) {
        _presets.value = presets
        store.savePresets(presets)
    }

    private fun update(transform: (EqProfile) -> EqProfile) = commit(transform(_profile.value))

    private fun commit(profile: EqProfile) {
        _profile.value = profile
        container.equalizer.setProfile(profile)
        // A drag fires this many times a second; write once the finger has settled.
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            store.saveProfile(profile, mode)
        }
    }

    companion object {
        private const val SAMPLE_RATE_HZ = 48_000.0
        private const val SAVE_DEBOUNCE_MS = 400L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { EqualizerViewModel(container) }
        }
    }
}
