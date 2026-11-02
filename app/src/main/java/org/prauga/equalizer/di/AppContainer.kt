package org.prauga.equalizer.di

import android.content.Context
import org.prauga.equalizer.AppPreferences
import org.prauga.equalizer.audio.AudioEngine
import org.prauga.equalizer.data.EqStateStore
import org.prauga.equalizer.audio.DynamicsProcessingEngine
import org.prauga.equalizer.audio.EqController

/**
 * The app's dependencies, built once and held for the life of the process.
 *
 * Deliberately not Hilt: this is a single module, and the AGP 9.5 built-in Kotlin toolchain has not
 * been proven with KSP and Hilt yet. There is one place to swap this for a real container.
 */
class AppContainer(context: Context) {

    val preferences: AppPreferences = AppPreferences(context)

    val stateStore: EqStateStore = EqStateStore(context)

    val engine: AudioEngine = DynamicsProcessingEngine()

    val equalizer: EqController = EqController(context, engine)
}
