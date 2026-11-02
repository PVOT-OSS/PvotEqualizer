package org.prauga.equalizer.ui.screens.equalizer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.StateFlow
import org.prauga.equalizer.audio.EngineStatus
import org.prauga.equalizer.di.AppContainer
import org.prauga.equalizer.domain.EqProfile

/**
 * The screen's window onto the engine. The engine is application-scoped, so this holds no audio
 * state of its own and survives a configuration change without re-attaching anything.
 */
class EqualizerViewModel(private val container: AppContainer) : ViewModel() {

    val status: StateFlow<EngineStatus> = container.engine.status

    fun setEnabled(enabled: Boolean) = container.equalizer.setEnabled(enabled)

    fun setProfile(profile: EqProfile) = container.equalizer.setProfile(profile)

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { EqualizerViewModel(container) }
        }
    }
}
