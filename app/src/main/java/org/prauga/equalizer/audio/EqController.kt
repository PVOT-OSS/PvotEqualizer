package org.prauga.equalizer.audio

import android.content.Context
import androidx.core.content.ContextCompat
import org.prauga.equalizer.domain.EqProfile

/**
 * Turns "the user wants the equalizer on" into the two things that means: a running service holding
 * the session receiver, and an enabled engine.
 *
 * Callers are UI only; everything here is safe to call from the main thread.
 */
class EqController(context: Context, val engine: AudioEngine) {

    private val appContext = context.applicationContext

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
}
