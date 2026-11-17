package org.prauga.equalizer.audio

import kotlinx.coroutines.flow.StateFlow
import org.prauga.equalizer.core.model.EqProfile

/**
 * What the engine is doing, for the screen to show honestly and for tests to assert on.
 *
 * @param enabled Whether the user has the equalizer on
 * @param bandCount Engine bands the current profile was fitted onto
 * @param sessions Audio sessions the effect is attached to right now
 * @param attachedPackages Apps whose audio is being equalized, as far as they told us
 * @param failure Why the engine could not start, if it could not
 * @param global Whether the effect is on the whole output mix (session 0) instead of per app
 * @param bypassed Whether the effect is attached but passing audio through untouched
 */
data class EngineStatus(
    val enabled: Boolean = false,
    val bandCount: Int = 0,
    val sessions: Int = 0,
    val attachedPackages: List<String> = emptyList(),
    val failure: String? = null,
    val global: Boolean = false,
    val bypassed: Boolean = false
)

/**
 * The boundary between the app and the sound.
 *
 * Everything is fire-and-forget and runs on the engine's own thread: callers are UI and broadcast
 * receivers, both of which must not touch an audio effect directly. [status] is the only thing to
 * read back.
 */
interface AudioEngine {

    val status: StateFlow<EngineStatus>

    /** Turns the equalizer on or off. Off releases every effect instance. */
    fun setEnabled(enabled: Boolean)

    /** Changes the curve; harmless when the engine is off or nothing is attached. */
    fun setProfile(profile: EqProfile)

    /** Attaches to an audio session, if the engine is on. */
    fun attach(sessionId: Int, packageName: String?)

    /**
     * Switches between one effect on the output mix (session 0, which reaches apps that never
     * announce a session but is deprecated and not honoured by every device) and one per announced
     * session. Never both, or announced sessions would be equalized twice.
     */
    fun setGlobal(global: Boolean)

    /**
     * Passes audio through untouched while keeping every effect attached, for an A/B comparison
     * that leaves the curve alone. Turning the equalizer off clears it.
     */
    fun setBypassed(bypassed: Boolean)

    /** Releases the effect for an audio session that has closed. */
    fun detach(sessionId: Int)

    /** Releases everything. */
    fun release()
}
