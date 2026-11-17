package org.prauga.equalizer.audio

import android.content.Context
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.prauga.equalizer.AppPreferences
import org.prauga.equalizer.core.model.EqProfile

/**
 * Turns "the user wants the equalizer on" into the two things that means: a running service holding
 * the session receiver, and an enabled engine.
 *
 * Callers are UI only; everything here is safe to call from the main thread.
 */
@Singleton
class EqController @Inject constructor(
    @ApplicationContext context: Context,
    val engine: AudioEngine,
    private val preferences: AppPreferences
) {

    private val appContext = context.applicationContext

    init {
        engine.setGlobal(preferences.globalMode)
    }

    fun setEnabled(enabled: Boolean) {
        if (enabled) {
            ContextCompat.startForegroundService(appContext, EqService.intent(appContext))
            engine.setEnabled(true)
        } else {
            engine.setEnabled(false)
            appContext.stopService(EqService.intent(appContext))
        }
    }

    fun setProfile(profile: EqProfile) = engine.setProfile(profile)

    fun setGlobal(global: Boolean) {
        preferences.updateGlobalMode(global)
        engine.setGlobal(global)
    }
}
