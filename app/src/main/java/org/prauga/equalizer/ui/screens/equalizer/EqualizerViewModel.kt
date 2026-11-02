package org.prauga.equalizer.ui.screens.equalizer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.prauga.equalizer.audio.EngineStatus
import org.prauga.equalizer.di.AppContainer
import org.prauga.equalizer.domain.EqBands
import org.prauga.equalizer.domain.EqMode
import org.prauga.equalizer.domain.EqProfile
import org.prauga.equalizer.dsp.DpBandFit
import org.prauga.equalizer.dsp.ProfileResponse

/**
 * The editor's state: the profile being shaped, which band is selected, and the engine's status.
 *
 * Bands sit at fixed frequencies, so the only edit is a band's gain. Every edit is committed to the
 * engine as it happens, so the drawn curve and the applied one cannot drift apart.
 */
class EqualizerViewModel(private val container: AppContainer) : ViewModel() {

    val status: StateFlow<EngineStatus> = container.engine.status

    private val _profile = MutableStateFlow(EqProfile.flat())
    val profile: StateFlow<EqProfile> = _profile.asStateFlow()

    private val _selectedBandId = MutableStateFlow(EqProfile.flat().bands.first().id)
    val selectedBandId: StateFlow<Int?> = _selectedBandId.asStateFlow()

    init {
        // The first fit in a fresh process costs about 10 ms; pay it before the first drag does.
        viewModelScope.launch(Dispatchers.Default) {
            DpBandFit.fit(_profile.value, SAMPLE_RATE_HZ)
        }
    }

    fun setEnabled(enabled: Boolean) = container.equalizer.setEnabled(enabled)

    fun selectBand(id: Int) {
        _selectedBandId.value = id
    }

    /**
     * Sets the *heard* gain at a band's frequency, which is what its handle sits at.
     *
     * The curve at a band's centre is that band's gain plus whatever its neighbours contribute
     * there, so the stored band gain has to give that contribution back. A bell's response at its
     * own centre is exactly its gain, so the neighbours' share is independent of the value being set
     * and can be subtracted once.
     */
    fun setTargetGain(id: Int, targetDb: Double) {
        val current = _profile.value
        val band = current.bands.firstOrNull { it.id == id } ?: return
        val heard = ProfileResponse(current, SAMPLE_RATE_HZ).dbAt(band.frequencyHz)
        val neighbours = heard - band.gainDb
        setGain(id, targetDb - neighbours)
    }

    /** Sets the band gain itself. */
    fun setGain(id: Int, gainDb: Double) {
        val current = _profile.value
        commit(
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
        )
    }

    fun setPreamp(preampDb: Double) {
        commit(
            _profile.value.copy(
                preampDb = preampDb.coerceIn(EqProfile.MIN_PREAMP_DB, EqProfile.MAX_PREAMP_DB)
            )
        )
    }

    /**
     * Rebuilds the bands when the mode changes. The two modes use different grids, so a band inherits
     * the gain of the nearest band that existed before, when one is close enough to be the same
     * intent. Nothing else survives a mode change, which is the cost of fixed frequencies.
     */
    fun syncBands(mode: EqMode) {
        val current = _profile.value
        val rebuilt = EqBands.profile(mode, current.preampDb).let { fresh ->
            fresh.copy(
                bands = fresh.bands.map { band ->
                    val nearest = current.bands.minByOrNull {
                        abs(it.frequencyHz - band.frequencyHz)
                    }
                    val closeEnough = nearest != null &&
                        nearest.frequencyHz / band.frequencyHz in 0.9..1.1
                    if (closeEnough) band.copy(gainDb = nearest.gainDb) else band
                }
            )
        }
        if (rebuilt.bands.map { it.frequencyHz } == current.bands.map { it.frequencyHz }) return

        if (rebuilt.bands.none { it.id == _selectedBandId.value }) {
            _selectedBandId.value = rebuilt.bands.first().id
        }
        commit(rebuilt)
    }

    fun resetToFlat() {
        val flat = EqBands.profile(
            mode = if (_profile.value.bands.size == EqBands.ALL.size) EqMode.ADVANCED else EqMode.SIMPLE
        )
        _selectedBandId.value = flat.bands.first().id
        commit(flat)
    }

    private fun commit(profile: EqProfile) {
        _profile.value = profile
        container.equalizer.setProfile(profile)
    }

    companion object {
        private const val SAMPLE_RATE_HZ = 48_000.0

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { EqualizerViewModel(container) }
        }
    }
}
